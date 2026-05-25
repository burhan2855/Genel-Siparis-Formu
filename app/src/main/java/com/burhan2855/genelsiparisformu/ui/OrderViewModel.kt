package com.burhan2855.genelsiparisformu.ui

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.burhan2855.genelsiparisformu.data.*
import com.burhan2855.genelsiparisformu.util.PdfHelper
import com.burhan2855.genelsiparisformu.util.ExcelHelper
import com.google.firebase.auth.AuthCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ktx.toObject
import com.google.gson.Gson
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.io.InputStreamReader

class OrderViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val orderDao = db.orderDao()
    private val auth = FirebaseAuth.getInstance()
    private val firestore = FirebaseFirestore.getInstance()

    private val _currentUser = MutableStateFlow(auth.currentUser)
    val currentUser: StateFlow<com.google.firebase.auth.FirebaseUser?> = _currentUser.asStateFlow()

    private val _userProfile = MutableStateFlow<UserProfile?>(null)
    val userProfile: StateFlow<UserProfile?> = _userProfile.asStateFlow()

    private val _teamMembers = MutableStateFlow<List<UserProfile>>(emptyList())
    val teamMembers: StateFlow<List<UserProfile>> = _teamMembers.asStateFlow()

    private val _products = MutableStateFlow<List<Product>>(emptyList())
    val products: StateFlow<List<Product>> = _products.asStateFlow()

    private val _customers = MutableStateFlow<List<Customer>>(emptyList())
    val customers: StateFlow<List<Customer>> = _customers.asStateFlow()

    private val _orders = MutableStateFlow<List<Order>>(emptyList())
    val orders: StateFlow<List<Order>> = _orders.asStateFlow()

    // Map: Product -> Pair(Quantity, CustomPrice)
    private val _cartItems = MutableStateFlow<Map<Product, Pair<Int, Double>>>(emptyMap())
    val cartItems: StateFlow<Map<Product, Pair<Int, Double>>> = _cartItems.asStateFlow()

    private val _previewPdfFile = MutableStateFlow<File?>(null)
    val previewPdfFile: StateFlow<File?> = _previewPdfFile.asStateFlow()

    init {
        loadProducts()
        loadCustomers()
        loadOrders()
        loadUserProfile()
    }

    private fun loadUserProfile() {
        val user = auth.currentUser ?: return
        // Profil bilgisini canlı (SnapshotListener) dinle
        firestore.collection("users").document(user.uid)
            .addSnapshotListener { snapshot, _ ->
                val profile = snapshot?.toObject<UserProfile>()
                if (profile != null) {
                    _userProfile.value = profile
                    observeCloudChanges() // Rol veya Admin değişirse dinleyicileri yenile
                    if (profile.role == "ADMIN") {
                        loadTeamMembers(user.uid)
                    }
                }
            }
    }

    private fun loadTeamMembers(adminUid: String) {
        firestore.collection("users")
            .whereEqualTo("adminUid", adminUid)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    _teamMembers.value = it.documents.mapNotNull { doc -> doc.toObject<UserProfile>() }
                }
            }
    }

    fun makeMeAdmin() {
        val user = auth.currentUser ?: return
        firestore.collection("users").document(user.uid).update("role", "ADMIN")
        loadUserProfile()
    }

    fun makeMeIndividual() {
        val user = auth.currentUser ?: return
        firestore.collection("users").document(user.uid).update("role", "INDIVIDUAL")
        loadUserProfile()
    }

    fun addTeamMember(email: String, onSuccess: () -> Unit, onError: (String) -> Unit) {
        val admin = _userProfile.value ?: return
        // Önce böyle bir kullanıcı var mı kontrol et (basitlik için Firestore'dan bakıyoruz)
        firestore.collection("users").whereEqualTo("email", email).get()
            .addOnSuccessListener { snapshot ->
                if (snapshot.isEmpty) {
                    // Kullanıcı henüz kayıt olmamış, bir 'davet' oluşturabiliriz
                    // Şimdilik sadece kayıtlı kullanıcıları ekleme mantığı kuralım
                    onError("Bu e-posta ile kayıtlı bir kullanıcı bulunamadı. Lütfen personelin önce kayıt olmasını isteyin.")
                } else {
                    val userDoc = snapshot.documents.first()
                    val userProfile = userDoc.toObject<UserProfile>()
                    if (userProfile?.role == "SALES") {
                        onError("Bu kullanıcı zaten başka bir ekibe bağlı.")
                    } else {
                        firestore.collection("users").document(userDoc.id).update(
                            "role", "SALES",
                            "adminUid", admin.uid
                        ).addOnSuccessListener { onSuccess() }
                    }
                }
            }
    }

    fun removeTeamMember(memberUid: String) {
        firestore.collection("users").document(memberUid).update(
            "role", "INDIVIDUAL",
            "adminUid", ""
        )
    }

    fun login(email: String, pass: String, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    _currentUser.value = auth.currentUser
                    loadUserProfile()
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    fun loginWithGoogle(credential: AuthCredential, onResult: (Boolean, String?) -> Unit) {
        auth.signInWithCredential(credential)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser!!
                    _currentUser.value = user
                    // Google ile girişte eğer kullanıcı ilk defa geliyorsa varsayılan profil oluştur
                    firestore.collection("users").document(user.uid).get()
                        .addOnSuccessListener { doc ->
                            if (!doc.exists()) {
                                val profile = UserProfile(uid = user.uid, email = user.email ?: "", role = "INDIVIDUAL")
                                firestore.collection("users").document(user.uid).set(profile)
                                _userProfile.value = profile
                            } else {
                                _userProfile.value = doc.toObject<UserProfile>()
                            }
                            observeCloudChanges()
                        }
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    fun logout() {
        auth.signOut()
        _currentUser.value = null
        _userProfile.value = null
    }

    fun register(email: String, pass: String, role: String, onResult: (Boolean, String?) -> Unit) {
        auth.createUserWithEmailAndPassword(email, pass)
            .addOnCompleteListener { task ->
                if (task.isSuccessful) {
                    val user = auth.currentUser!!
                    val profile = UserProfile(uid = user.uid, email = email, role = role)
                    firestore.collection("users").document(user.uid).set(profile)
                    _currentUser.value = user
                    _userProfile.value = profile
                    observeCloudChanges()
                    onResult(true, null)
                } else {
                    onResult(false, task.exception?.message)
                }
            }
    }

    private fun observeCloudChanges() {
        val user = auth.currentUser ?: return
        val profile = _userProfile.value ?: return
        
        // Eğer SALES ise ADMIN'in verilerini dinle, yoksa kendi verilerini
        val dataOwnerUid = if (profile.role == "SALES") profile.adminUid else user.uid

        if (dataOwnerUid.isEmpty()) return

        // Listen for remote product updates
        firestore.collection("products")
            .whereEqualTo("ownerUid", dataOwnerUid)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    val cloudProducts = it.documents.mapNotNull { doc -> doc.toObject<Product>() }
                    if (cloudProducts.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            orderDao.insertProducts(cloudProducts)
                            loadProducts()
                        }
                    }
                }
            }

        // Listen for remote customer updates
        firestore.collection("customers")
            .whereEqualTo("ownerUid", dataOwnerUid)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    val cloudCustomers = it.documents.mapNotNull { doc -> doc.toObject<Customer>() }
                    if (cloudCustomers.isNotEmpty()) {
                        viewModelScope.launch(Dispatchers.IO) {
                            orderDao.insertCustomers(cloudCustomers)
                            loadCustomers()
                        }
                    }
                }
            }
            
        // Listen for all orders related to this admin/individual
        firestore.collection("orders")
            .whereEqualTo("ownerUid", dataOwnerUid)
            .addSnapshotListener { snapshot, _ ->
                snapshot?.let {
                    viewModelScope.launch(Dispatchers.IO) {
                        // Önce yerel veritabanındaki tüm bulut kökenli siparişleri temizleyip baştan doldurabiliriz 
                        // veya daha verimli bir fark kontrolü (diff) yapabiliriz.
                        // En güvenli yöntem: Bulutta olmayanları silmek.
                        val cloudIds = it.documents.map { doc -> doc.id }
                        val localOrders = orderDao.getAllOrders()
                        localOrders.forEach { local ->
                            if (!cloudIds.contains(local.id) && local.ownerUid == dataOwnerUid) {
                                orderDao.deleteOrder(local)
                                orderDao.deleteOrderItems(local.id)
                            }
                        }

                        it.documents.forEach { doc ->
                            val order = doc.toObject<Order>()
                            if (order != null) {
                                orderDao.insertOrder(order)
                                val itemsList = doc.get("items") as? List<Map<String, Any>>
                                itemsList?.let { maps ->
                                    val orderItems = maps.map { map ->
                                        OrderItem(
                                            orderId = order.id,
                                            productCode = map["productCode"] as? String ?: "",
                                            productName = map["productName"] as? String ?: "",
                                            quantity = (map["quantity"] as? Number)?.toDouble() ?: 0.0,
                                            shippedQuantity = (map["shippedQuantity"] as? Number)?.toDouble() ?: 0.0,
                                            unitPrice = (map["unitPrice"] as? Number)?.toDouble() ?: 0.0,
                                            currency = map["currency"] as? String ?: "",
                                            unit = map["unit"] as? String ?: "",
                                            taxRate = (map["taxRate"] as? Number)?.toDouble() ?: 0.0
                                        )
                                    }
                                    orderDao.deleteOrderItems(order.id)
                                    orderDao.insertOrderItem(orderItems)
                                }
                            }
                        }
                        loadOrders()
                    }
                }
            }
    }

    fun loadProducts() {
        viewModelScope.launch {
            _products.value = orderDao.getAllProducts()
        }
    }

    fun loadCustomers() {
        viewModelScope.launch {
            _customers.value = orderDao.getAllCustomers()
        }
    }

    fun loadOrders() {
        viewModelScope.launch {
            _orders.value = orderDao.getAllOrders()
        }
    }

    fun importProductsFromExcel(products: List<Product>) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val productsWithUid = products.map { it.copy(ownerUid = user.uid) }
                orderDao.insertProducts(productsWithUid)

                // Buluta toplu aktarım
                productsWithUid.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { product ->
                        val docRef = firestore.collection("products").document("${user.uid}_${product.code}")
                        batch.set(docRef, product)
                    }
                    batch.commit()
                }
            }
            loadProducts()
        }
    }

    fun addProduct(product: Product) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            // Düzenleme yapılıyorsa mevcut ownerUid'yi koru, yoksa yeni kullanıcı ID'sini ata
            val currentOwnerUid = product.ownerUid.ifBlank { user.uid }
            val productWithUid = product.copy(ownerUid = currentOwnerUid)
            
            orderDao.insertProduct(productWithUid)
            loadProducts()
            firestore.collection("products").document("${currentOwnerUid}_${product.code}").set(productWithUid)
        }
    }

    fun deleteProduct(product: Product) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            orderDao.deleteProduct(product)
            loadProducts()
            firestore.collection("products").document("${user.uid}_${product.code}").delete()
        }
    }

    fun importCustomersFromExcel(customers: List<Customer>) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val customersWithUid = customers.map { it.copy(ownerUid = user.uid) }
                orderDao.insertCustomers(customersWithUid)

                // Buluta toplu aktarım
                customersWithUid.chunked(500).forEach { chunk ->
                    val batch = firestore.batch()
                    chunk.forEach { customer ->
                        val docRef = firestore.collection("customers").document("${user.uid}_${customer.code}")
                        batch.set(docRef, customer)
                    }
                    batch.commit()
                }
            }
            loadCustomers()
        }
    }

    fun addCustomer(customer: Customer) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            // Düzenleme yapılıyorsa mevcut ownerUid'yi koru, yoksa yeni kullanıcı ID'sini ata
            val currentOwnerUid = customer.ownerUid.ifBlank { user.uid }
            val customerWithUid = customer.copy(ownerUid = currentOwnerUid)
            
            orderDao.insertCustomer(customerWithUid)
            loadCustomers()
            firestore.collection("customers").document("${currentOwnerUid}_${customer.code}").set(customerWithUid)
        }
    }

    fun deleteCustomer(customer: Customer) {
        val user = auth.currentUser ?: return
        viewModelScope.launch {
            orderDao.deleteCustomer(customer)
            loadCustomers()
            firestore.collection("customers").document("${user.uid}_${customer.code}").delete()
        }
    }

    fun addToCart(product: Product, customPrice: Double = product.price) {
        val currentCart = _cartItems.value.toMutableMap()
        val currentPair = currentCart[product]
        if (currentPair != null) {
            currentCart[product] = Pair(currentPair.first + 1, currentPair.second)
        } else {
            currentCart[product] = Pair(1, customPrice)
        }
        _cartItems.value = currentCart
    }

    fun updateCartItemPrice(product: Product, price: Double) {
        val currentCart = _cartItems.value.toMutableMap()
        val currentPair = currentCart[product]
        if (currentPair != null) {
            currentCart[product] = Pair(currentPair.first, price)
            _cartItems.value = currentCart
        }
    }

    fun updateCartItemQuantity(product: Product, quantity: Int) {
        val currentCart = _cartItems.value.toMutableMap()
        val currentPair = currentCart[product]
        if (currentPair != null) {
            if (quantity > 0) {
                currentCart[product] = Pair(quantity, currentPair.second)
            } else {
                currentCart.remove(product)
            }
            _cartItems.value = currentCart
        }
    }

    fun removeFromCart(product: Product) {
        val currentCart = _cartItems.value.toMutableMap()
        val currentPair = currentCart[product] ?: return
        if (currentPair.first > 1) {
            currentCart[product] = Pair(currentPair.first - 1, currentPair.second)
        } else {
            currentCart.remove(product)
        }
        _cartItems.value = currentCart
    }

    fun clearCart() {
        _cartItems.value = emptyMap()
    }

    fun confirmOrder(
        customerCode: String,
        customerName: String,
        customerPhone: String,
        customerDistrict: String,
        customerCity: String,
        customerFullAddress: String,
        deliveryDate: Long,
        paymentTerm: String,
        isTaxIncluded: Boolean,
        context: Context,
        showPreview: Boolean = true
    ) {
        viewModelScope.launch {
            val items = _cartItems.value
            if (items.isEmpty()) return@launch

            val user = auth.currentUser ?: return@launch
            val profile = _userProfile.value ?: return@launch
            
            val file = withContext(Dispatchers.IO) {
                // Eğer SALES ise siparişi Admin'in UID'sine bağla, yoksa kendi UID'sine
                val dataOwnerUid = if (profile.role == "SALES") profile.adminUid else user.uid

                val totalAmountWithoutTax = items.values.sumOf { it.first * it.second }
                val totalTax = items.entries.sumOf { (product, pair) ->
                    val taxRate = product.taxSell.replace("%", "").toDoubleOrNull() ?: 0.0
                    val lineTotal = pair.first * pair.second
                    if (isTaxIncluded) {
                        lineTotal - (lineTotal / (1 + taxRate / 100.0))
                    } else {
                        lineTotal * (taxRate / 100.0)
                    }
                }

                val finalTotal = if (isTaxIncluded) totalAmountWithoutTax else totalAmountWithoutTax + totalTax

                val order = Order(
                    customerCode = customerCode,
                    customerName = customerName,
                    customerPhone = customerPhone,
                    customerDistrict = customerDistrict,
                    customerCity = customerCity,
                    customerFullAddress = customerFullAddress,
                    totalAmount = finalTotal,
                    deliveryDate = deliveryDate,
                    paymentTerm = paymentTerm,
                    isTaxIncluded = isTaxIncluded,
                    ownerUid = dataOwnerUid, // Admin'in görebilmesi için anahtar alan
                    adminUid = if (profile.role == "SALES") profile.adminUid else "",
                    salesRepEmail = user.email ?: ""
                )

                val orderItems = items.map { (product, pair) ->
                    val tax = product.taxSell.replace("%", "").toDoubleOrNull() ?: 0.0
                    OrderItem(
                        orderId = order.id,
                        productCode = product.code,
                        productName = product.name,
                        quantity = pair.first.toDouble(),
                        unitPrice = pair.second,
                        currency = product.currency,
                        unit = product.unit,
                        taxRate = tax
                    )
                }

                orderDao.insertOrder(order)
                orderDao.insertOrderItem(orderItems)
                
                // Stoktan düşme işlemi
                items.forEach { (product, pair) ->
                    val updatedProduct = product.copy(stockQuantity = product.stockQuantity - pair.first)
                    orderDao.insertProduct(updatedProduct) // Local DB update
                    
                    // Firestore update
                    val productDataOwnerUid = if (profile.role == "SALES") profile.adminUid else user.uid
                    firestore.collection("products")
                        .document("${productDataOwnerUid}_${product.code}")
                        .update("stockQuantity", updatedProduct.stockQuantity)
                }
                
                // Sync to Firestore
                val orderMap = hashMapOf(
                    "id" to order.id,
                    "customerName" to order.customerName,
                    "customerCode" to order.customerCode,
                    "totalAmount" to order.totalAmount,
                    "date" to order.date,
                    "salesRepEmail" to user.email,
                    "ownerUid" to dataOwnerUid, // Admin bu alan sayesinde siparişi görecek
                    "status" to "Pending"
                )
                firestore.collection("orders").document(order.id).set(orderMap)
                
                PdfHelper.createOrderPdf(context, order, orderItems)
            }
            
            loadProducts()
            loadOrders() // Yerel listeyi yenile

            if (showPreview && file != null) {
                _previewPdfFile.value = file
            } else if (file != null) {
                sharePdfFile(context, file)
            }
            
            clearCart()
        }
    }

    fun clearPreview() {
        _previewPdfFile.value = null
    }

    fun shareOrderPdfFromHistory(context: Context, order: Order) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val items = orderDao.getOrderItems(order.id)
                PdfHelper.createOrderPdf(context, order, items)
            }
            if (file != null) {
                _previewPdfFile.value = file
            }
        }
    }

    fun exportReportPdf(context: Context, filteredOrders: List<Order>) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                val itemMap = mutableMapOf<String, List<OrderItem>>()
                filteredOrders.forEach { order ->
                    itemMap[order.id] = orderDao.getOrderItems(order.id)
                }
                PdfHelper.createReportPdf(context, filteredOrders, itemMap)
            }
            if (file != null) {
                _previewPdfFile.value = file
            }
        }
    }

    fun deleteOrder(order: Order) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                // Önce sipariş kalemlerini al ki stokları iade edebilelim
                val items = orderDao.getOrderItems(order.id)
                val user = auth.currentUser
                val profile = _userProfile.value
                
                if (user != null && profile != null) {
                    val dataOwnerUid = if (profile.role == "SALES") profile.adminUid else user.uid
                    
                    // Her bir kalem için stoğu geri yükle
                    items.forEach { item ->
                        // Mevcut ürünü bul (Local DB'den)
                        val currentProducts = _products.value
                        val product = currentProducts.find { it.code == item.productCode }
                        
                        if (product != null) {
                            val restoredStock = product.stockQuantity + item.quantity
                            val updatedProduct = product.copy(stockQuantity = restoredStock)
                            
                            // Local DB Güncelle
                            orderDao.insertProduct(updatedProduct)
                            
                            // Firestore Güncelle
                            firestore.collection("products")
                                .document("${dataOwnerUid}_${product.code}")
                                .update("stockQuantity", restoredStock)
                        }
                    }
                }

                // Siparişi ve kalemlerini sil
                orderDao.deleteOrder(order)
                orderDao.deleteOrderItems(order.id)
                firestore.collection("orders").document(order.id).delete()
            }
            loadProducts() // Listeyi yenile
            loadOrders()
        }
    }

    fun approveOrder(order: Order) {
        viewModelScope.launch {
            withContext(Dispatchers.IO) {
                val updatedOrder = order.copy(status = "Approved")
                orderDao.updateOrder(updatedOrder)
                
                // Firestore senkronize et
                firestore.collection("orders").document(order.id).update("status", "Approved")
            }
            loadOrders()
        }
    }

    suspend fun updateOrderItemShipping(item: OrderItem, newShipped: Double) {
        withContext(Dispatchers.IO) {
            val updatedItem = item.copy(shippedQuantity = newShipped)
            orderDao.updateOrderItem(updatedItem)

            // Tüm kalemler sevk edildiyse sipariş durumunu güncelle
            val allItems = orderDao.getOrderItems(item.orderId)
            val allShipped = allItems.all {
                if (it.id == item.id) newShipped >= it.quantity
                else it.shippedQuantity >= it.quantity
            }

            val order = orders.value.find { it.id == item.orderId }
            if (order != null) {
                val newStatus = if (allShipped) "Completed" else "Shipping"
                val updatedOrder = order.copy(status = newStatus)
                orderDao.updateOrder(updatedOrder)

                // Firestore senkronize et
                firestore.collection("orders").document(order.id).update(
                    "status", newStatus,
                    "items", allItems.map { if (it.id == item.id) updatedItem else it }
                )
            }
        }
        loadOrders()
    }

    suspend fun getOrderItems(orderId: String): List<OrderItem> {
        return orderDao.getOrderItems(orderId)
    }

    fun exportReportExcel(context: Context, filteredOrders: List<Order>) {
        viewModelScope.launch {
            try {
                val file = withContext(Dispatchers.IO) {
                    val itemMap = mutableMapOf<String, List<OrderItem>>()
                    filteredOrders.forEach { order ->
                        itemMap[order.id] = orderDao.getOrderItems(order.id)
                    }
                    ExcelHelper.createReportExcel(context, filteredOrders, itemMap)
                }
                if (file != null && file.exists()) {
                    shareFile(context, file, "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", "Raporu Paylaş")
                } else {
                    withContext(Dispatchers.Main) {
                        android.widget.Toast.makeText(context, "Excel dosyası oluşturulamadı.", android.widget.Toast.LENGTH_SHORT).show()
                    }
                }
            } catch (e: Exception) {
                e.printStackTrace()
                withContext(Dispatchers.Main) {
                    android.widget.Toast.makeText(context, "Excel Hatası: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        // Eğer context bir Activity değilse, chooser intent'e NEW_TASK flag ekleyelim
        val chooser = Intent.createChooser(intent, chooserTitle)
        if (context !is android.app.Activity) chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        try {
            context.startActivity(chooser)
        } catch (e: Exception) {
            e.printStackTrace()
            android.widget.Toast.makeText(context, "Paylaşım başlatılamadı: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    fun sharePdfFile(context: Context, file: File) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "Sipariş Formu")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Siparişi Paylaş"))
    }

    fun shareOrderPdf(context: Context, order: Order, items: List<OrderItem>) {
        viewModelScope.launch {
            val file = withContext(Dispatchers.IO) {
                PdfHelper.createOrderPdf(context, order, items)
            }
            if (file != null) {
                val uri = FileProvider.getUriForFile(
                    context,
                    "${context.packageName}.fileprovider",
                    file
                )

                val intent = Intent(Intent.ACTION_SEND).apply {
                    type = "application/pdf"
                    putExtra(Intent.EXTRA_STREAM, uri)
                    putExtra(Intent.EXTRA_SUBJECT, "Sipariş Formu - ${order.customerName}")
                    addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                }
                context.startActivity(Intent.createChooser(intent, "Siparişi Paylaş"))
            }
        }
    }

    fun exportBackup(context: Context) {
        viewModelScope.launch {
            val backup = BackupData(
                products = orderDao.getAllProducts(),
                customers = orderDao.getAllCustomers(),
                orders = orderDao.getAllOrders(),
                orderItems = orderDao.getAllOrderItems()
            )
            val json = Gson().toJson(backup)
            val file = File(context.cacheDir, "GenelSiparis_Yedek_${System.currentTimeMillis()}.json")
            
            withContext(Dispatchers.IO) {
                file.writeText(json)
            }

            val uri = FileProvider.getUriForFile(
                context,
                "${context.packageName}.fileprovider",
                file
            )

            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/json"
                putExtra(Intent.EXTRA_STREAM, uri)
                putExtra(Intent.EXTRA_SUBJECT, "Uygulama Yedek Dosyası")
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            context.startActivity(Intent.createChooser(intent, "Yedeği Paylaş / Sakla"))
        }
    }

    fun importBackup(context: Context, uri: Uri, onSuccess: () -> Unit, onError: (String) -> Unit) {
        viewModelScope.launch {
            try {
                val success = withContext(Dispatchers.IO) {
                    val inputStream = context.contentResolver.openInputStream(uri)
                    val reader = InputStreamReader(inputStream)
                    val backup = Gson().fromJson(reader, BackupData::class.java)
                    reader.close()

                    if (backup != null) {
                        orderDao.insertProducts(backup.products)
                        orderDao.insertCustomers(backup.customers)
                        
                        // Orders and items might need sequential insertion or just replace
                        backup.orders.forEach { orderDao.insertOrder(it) }
                        orderDao.insertOrderItem(backup.orderItems)
                        true
                    } else {
                        false
                    }
                }

                if (success) {
                    loadProducts()
                    loadCustomers()
                    loadOrders()
                    onSuccess()
                } else {
                    onError("Yedek dosyası boş veya geçersiz.")
                }
            } catch (e: Exception) {
                onError("Hata: ${e.message}")
            }
        }
    }
}
