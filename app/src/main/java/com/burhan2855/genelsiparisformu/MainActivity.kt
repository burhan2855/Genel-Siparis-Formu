package com.burhan2855.genelsiparisformu

import android.graphics.Bitmap
import android.graphics.pdf.PdfRenderer
import android.os.Bundle
import android.os.ParcelFileDescriptor
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.*
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.lifecycle.viewmodel.compose.viewModel
import com.burhan2855.genelsiparisformu.data.Customer
import com.burhan2855.genelsiparisformu.data.Order
import com.burhan2855.genelsiparisformu.data.OrderItem
import com.burhan2855.genelsiparisformu.data.Product
import com.burhan2855.genelsiparisformu.ui.OrderViewModel
import com.burhan2855.genelsiparisformu.ui.theme.GenelSiparisFormuTheme
import com.burhan2855.genelsiparisformu.util.ExcelHelper
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInClient
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            GenelSiparisFormuTheme {
                MainApp()
            }
        }
    }
}

enum class AppTab(val titleRes: Int, val icon: ImageVector) {
    SIPARIS(R.string.tab_order, Icons.Default.Edit),
    CARILER(R.string.tab_customers, Icons.Default.Groups),
    STOKLAR(R.string.tab_stocks, Icons.Default.Inventory),
    GECMIS(R.string.tab_history, Icons.Default.History),
    RAPORLAR(R.string.tab_reports, Icons.Default.BarChart),
    AYARLAR(R.string.tab_settings, Icons.Default.Settings)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainApp(viewModel: OrderViewModel = viewModel()) {
    val currentUser by viewModel.currentUser.collectAsState()
    
    if (currentUser == null) {
        LoginScreen(viewModel)
    } else {
        MainContent(viewModel)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainContent(viewModel: OrderViewModel) {
    val context = LocalContext.current
    var selectedTab by remember { mutableStateOf(AppTab.SIPARIS) }
    val cartItems by viewModel.cartItems.collectAsState()
    val previewPdfFile by viewModel.previewPdfFile.collectAsState()
    var showCart by remember { mutableStateOf(false) }

    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    Scaffold(
        bottomBar = {
            NavigationBar {
                AppTab.entries.forEach { tab ->
                    NavigationBarItem(
                        selected = selectedTab == tab,
                        onClick = {
                            selectedTab = tab
                            if (tab != AppTab.SIPARIS) showCart = false
                        },
                        icon = {
                            if (tab == AppTab.SIPARIS && cartItems.isNotEmpty()) {
                                BadgedBox(badge = {
                                    Badge { Text(cartItems.values.sumOf { it.first }.toString()) }
                                }) {
                                    Icon(tab.icon, contentDescription = stringResource(tab.titleRes))
                                }
                            } else {
                                Icon(tab.icon, contentDescription = stringResource(tab.titleRes))
                            }
                        },
                        label = { Text(stringResource(tab.titleRes)) }
                    )
                }
            }
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
        ) {
            when (selectedTab) {
                AppTab.SIPARIS -> OrderTabScreen(
                    viewModel = viewModel,
                    showCart = showCart,
                    onToggleCart = { showCart = it },
                    googleSignInClient = googleSignInClient
                )
                AppTab.CARILER -> CustomersTabScreen(viewModel = viewModel)
                AppTab.STOKLAR -> StocksTabScreen(viewModel = viewModel)
                AppTab.GECMIS -> HistoryTabScreen(viewModel = viewModel)
                AppTab.RAPORLAR -> ReportsTabScreen(viewModel = viewModel)
                AppTab.AYARLAR -> SettingsTabScreen(viewModel = viewModel, googleSignInClient = googleSignInClient)
            }
        }
    }

    // PDF Önizleme Dialogu
    if (previewPdfFile != null) {
        PdfPreviewDialog(
            file = previewPdfFile!!,
            onDismiss = { viewModel.clearPreview() },
            onShare = { 
                viewModel.sharePdfFile(context, previewPdfFile!!)
                viewModel.clearPreview()
            }
        )
    }
}

@Composable
fun LoginScreen(viewModel: OrderViewModel) {
    var email by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var isRegisterMode by remember { mutableStateOf(false) }
    var isLoading by remember { mutableStateOf(false) }
    val context = LocalContext.current

    // Google Sign-In Configuration
    val gso = GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
        .requestIdToken(context.getString(R.string.default_web_client_id))
        .requestEmail()
        .build()
    val googleSignInClient = GoogleSignIn.getClient(context, gso)

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)!!
            val credential = GoogleAuthProvider.getCredential(account.idToken, null)
            isLoading = true
            viewModel.loginWithGoogle(credential) { success, error ->
                isLoading = false
                if (!success) Toast.makeText(context, error ?: "Google Giriş Hatası", Toast.LENGTH_LONG).show()
            }
        } catch (e: ApiException) {
            Toast.makeText(context, "Google Giriş İptal Edildi", Toast.LENGTH_SHORT).show()
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.primaryContainer),
        contentAlignment = Alignment.Center
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .padding(32.dp),
            elevation = CardDefaults.cardElevation(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Text(
                    stringResource(R.string.app_name),
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(if (isRegisterMode) stringResource(R.string.register_title) else stringResource(R.string.login_title), fontSize = 14.sp)

                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text(stringResource(R.string.email)) },
                    leadingIcon = { Icon(Icons.Default.Email, null) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                OutlinedTextField(
                    value = password,
                    onValueChange = { password = it },
                    label = { Text(stringResource(R.string.password)) },
                    leadingIcon = { Icon(Icons.Default.Lock, null) },
                    visualTransformation = PasswordVisualTransformation(),
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )

                if (isLoading) {
                    CircularProgressIndicator()
                } else {
                    Button(
                        onClick = {
                            if (email.isNotBlank() && password.isNotBlank()) {
                                isLoading = true
                                if (isRegisterMode) {
                                    viewModel.register(email, password, "INDIVIDUAL") { success, error ->
                                        isLoading = false
                                        if (!success) Toast.makeText(context, error ?: "Kayıt hatası", Toast.LENGTH_LONG).show()
                                    }
                                } else {
                                    viewModel.login(email, password) { success, error ->
                                        isLoading = false
                                        if (!success) Toast.makeText(context, error ?: "Giriş hatası", Toast.LENGTH_LONG).show()
                                    }
                                }
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isRegisterMode) stringResource(R.string.register_btn) else stringResource(R.string.login_btn))
                    }

                    if (!isRegisterMode) {
                        OutlinedButton(
                            onClick = { googleSignInLauncher.launch(googleSignInClient.signInIntent) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Default.AccountCircle, null)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(stringResource(R.string.google_login))
                        }
                    }

                    TextButton(onClick = { isRegisterMode = !isRegisterMode }) {
                        Text(if (isRegisterMode) "Zaten hesabım var, Giriş Yap" else "Henüz hesabınız yok mu? Kayıt Olun")
                    }
                }
            }
        }
    }
}

@Composable
fun PdfPreviewDialog(
    file: File,
    onDismiss: () -> Unit,
    onShare: () -> Unit
) {
    var bitmaps by remember { mutableStateOf<List<Bitmap>>(emptyList()) }
    var errorMessage by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(file) {
        withContext(Dispatchers.IO) {
            try {
                if (!file.exists()) {
                    errorMessage = "PDF dosyası bulunamadı."
                    return@withContext
                }
                val parcelFileDescriptor = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                val renderer = PdfRenderer(parcelFileDescriptor)
                val tempBitmaps = mutableListOf<Bitmap>()
                for (i in 0 until renderer.pageCount) {
                    val page = renderer.openPage(i)
                    // Ölçeklendirme yaparak hafıza kullanımını optimize et
                    val width = (page.width * 2) // 2x ölçek
                    val height = (page.height * 2)
                    val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                    page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                    tempBitmaps.add(bitmap)
                    page.close()
                }
                bitmaps = tempBitmaps
                renderer.close()
                parcelFileDescriptor.close()
            } catch (e: Exception) {
                e.printStackTrace()
                errorMessage = "PDF yüklenirken hata oluştu: ${e.localizedMessage}"
            }
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = Color.DarkGray
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surface)
                        .padding(8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                    Text("PDF Önizleme", fontWeight = FontWeight.Bold)
                    IconButton(onClick = onShare) {
                        Icon(Icons.Default.Share, contentDescription = "Paylaş", tint = MaterialTheme.colorScheme.primary)
                    }
                }

                // Content
                Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                    if (errorMessage != null) {
                        Text(
                            text = errorMessage!!,
                            color = Color.White,
                            modifier = Modifier.align(Alignment.Center).padding(16.dp),
                            textAlign = TextAlign.Center
                        )
                    } else if (bitmaps.isEmpty()) {
                        CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                    } else {
                        LazyColumn(
                            modifier = Modifier.fillMaxSize(),
                            contentPadding = PaddingValues(16.dp),
                            verticalArrangement = Arrangement.spacedBy(16.dp)
                        ) {
                            items(bitmaps) { bitmap ->
                                Card(
                                    modifier = Modifier.fillMaxWidth(),
                                    elevation = CardDefaults.cardElevation(4.dp)
                                ) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = null,
                                        modifier = Modifier.fillMaxWidth(),
                                        contentScale = ContentScale.FillWidth
                                    )
                                }
                            }
                        }
                    }
                }

                // Bottom Action
                Button(
                    onClick = onShare,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Icon(Icons.Default.Share, null)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Siparişi WhatsApp / E-Posta ile Paylaş")
                }
            }
        }
    }
}

// ==========================================
// 1. SİPARİŞ OLUŞTURMA SEKME EKRANI
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun OrderTabScreen(
    viewModel: OrderViewModel,
    showCart: Boolean,
    onToggleCart: (Boolean) -> Unit,
    googleSignInClient: GoogleSignInClient
) {
    val products by viewModel.products.collectAsState()
    val cartItems by viewModel.cartItems.collectAsState()
    val customers by viewModel.customers.collectAsState()
    
    var selectedCustomer by remember { mutableStateOf<Customer?>(null) }
    var searchQuery by remember { mutableStateOf("") }
    var showCustomerSelectDialog by remember { mutableStateOf(false) }

    val filteredProducts = products.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.code.contains(searchQuery, ignoreCase = true)
    }

    if (showCart) {
        CartScreen(
            viewModel = viewModel,
            selectedCustomer = selectedCustomer,
            onBack = { onToggleCart(false) },
            onConfirmSuccess = {
                onToggleCart(false)
                selectedCustomer = null
            }
        )
    } else {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(stringResource(R.string.tab_order)) },
                    actions = {
                        BadgedBox(
                            badge = {
                                if (cartItems.isNotEmpty()) {
                                    Badge { Text(cartItems.values.sumOf { it.first }.toString()) }
                                }
                            },
                            modifier = Modifier.padding(end = 16.dp)
                        ) {
                            IconButton(onClick = { onToggleCart(true) }) {
                                Icon(Icons.Default.ShoppingCart, contentDescription = "Sepet")
                            }
                        }
                        IconButton(onClick = { 
                            viewModel.logout()
                            googleSignInClient.signOut()
                        }) {
                            Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = "Çıkış Yap")
                        }
                    }
                )
            },
            floatingActionButton = {
                if (cartItems.isNotEmpty()) {
                    ExtendedFloatingActionButton(
                        onClick = { onToggleCart(true) },
                        icon = { Icon(Icons.Default.ShoppingCart, null) },
                        text = { Text("Sepete Git (${cartItems.values.sumOf { it.first }} Kalem)") }
                    )
                }
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp)
            ) {
                // Müşteri Seçim Alanı
                Card(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp)
                        .clickable { showCustomerSelectDialog = true },
                    colors = CardDefaults.cardColors(
                        containerColor = if (selectedCustomer != null) MaterialTheme.colorScheme.primaryContainer 
                                         else MaterialTheme.colorScheme.surfaceVariant
                    )
                ) {
                    Row(
                        modifier = Modifier.padding(16.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = null,
                            tint = if (selectedCustomer != null) MaterialTheme.colorScheme.primary 
                                   else MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.width(16.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = selectedCustomer?.name ?: "Lütfen Müşteri Seçin",
                                fontWeight = FontWeight.Bold,
                                fontSize = 16.sp
                            )
                            if (selectedCustomer != null) {
                                Text(
                                    text = "Kod: ${selectedCustomer?.code} | Tel: ${selectedCustomer?.phone}",
                                    fontSize = 12.sp
                                )
                            }
                        }
                        Icon(Icons.Default.Search, contentDescription = "Müşteri Ara")
                    }
                }

                // Ürün Arama Çubuğu
                OutlinedTextField(
                    value = searchQuery,
                    onValueChange = { searchQuery = it },
                    label = { Text("Ürün veya Kod Ara") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                    singleLine = true
                )

                if (filteredProducts.isEmpty()) {
                    Box(
                        modifier = Modifier.fillMaxSize().weight(1f),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (products.isEmpty()) "Henüz stok tanımlanmamış.\nLütfen Stoklar sekmesinden ekleme yapın." 
                                   else "Aranan kriterlere uygun ürün bulunamadı.",
                            textAlign = TextAlign.Center,
                            color = MaterialTheme.colorScheme.secondary
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(filteredProducts) { product ->
                            val cartPair = cartItems[product]
                            val qty = cartPair?.first ?: 0
                            
                            Card(
                                modifier = Modifier.fillMaxWidth(),
                                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(16.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(product.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                        Text("Kod: ${product.code} | Stok: ${product.stockQuantity} ${product.unit}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                        if (product.price > 0) {
                                            Text("${product.price} ${product.currency}", fontSize = 14.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Medium)
                                        }
                                    }
                                    if (qty > 0) {
                                        Badge(containerColor = MaterialTheme.colorScheme.primaryContainer) {
                                            Text("$qty Adet", modifier = Modifier.padding(4.dp))
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    IconButton(
                                        onClick = { viewModel.addToCart(product) }
                                    ) {
                                        Icon(Icons.Default.AddCircle, contentDescription = "Ekle", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(36.dp))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MÜŞTERİ SEÇİM DIALOGU
    if (showCustomerSelectDialog) {
        var customerSearch by remember { mutableStateOf("") }
        val filteredCustomers = customers.filter {
            it.name.contains(customerSearch, ignoreCase = true) || it.code.contains(customerSearch, ignoreCase = true)
        }

        Dialog(onDismissRequest = { showCustomerSelectDialog = false }) {
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.8f)
                    .padding(16.dp),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text("Cari Kart Seç", fontWeight = FontWeight.Bold, fontSize = 18.sp, modifier = Modifier.padding(bottom = 8.dp))
                    OutlinedTextField(
                        value = customerSearch,
                        onValueChange = { customerSearch = it },
                        label = { Text("Cari Kod veya Ünvan Ara") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    if (filteredCustomers.isEmpty()) {
                        Box(modifier = Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                            Text("Cari Kart bulunamadı.")
                        }
                    } else {
                        LazyColumn(modifier = Modifier.weight(1f)) {
                            items(filteredCustomers) { customer ->
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable {
                                            selectedCustomer = customer
                                            showCustomerSelectDialog = false
                                        }
                                        .padding(vertical = 12.dp, horizontal = 8.dp)
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(customer.name, fontWeight = FontWeight.SemiBold)
                                        val loc = listOf(customer.district, customer.city).filter { it.isNotEmpty() }.joinToString(" / ")
                                        val phones = listOf(customer.phone, customer.mobilePhone).filter { it.isNotEmpty() }.joinToString(" - ")
                                        val meta = listOf("Kod: ${customer.code}", if (phones.isNotEmpty()) "Tel: $phones" else "", loc).filter { it.isNotEmpty() }.joinToString(" | ")
                                        Text(meta, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                        if (customer.balance.isNotEmpty()) {
                                            Text("Bakiye: ${customer.balance} ${customer.baStatus}", fontSize = 11.sp, color = MaterialTheme.colorScheme.primary)
                                        }
                                    }
                                }
                                HorizontalDivider()
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { showCustomerSelectDialog = false },
                        modifier = Modifier.align(Alignment.End)
                    ) {
                        Text("Kapat")
                    }
                }
            }
        }
    }
}

// ==========================================
// SEPET EKRANI VE SİPARİŞ DETAY FORMU
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CartScreen(
    viewModel: OrderViewModel,
    selectedCustomer: Customer?,
    onBack: () -> Unit,
    onConfirmSuccess: () -> Unit
) {
    val cartItems by viewModel.cartItems.collectAsState()
    val context = LocalContext.current
    
    var paymentTerm by remember { mutableStateOf("") }
    var deliveryDateMillis by remember { mutableLongStateOf(0L) }
    
    var showDatePicker by remember { mutableStateOf(false) }
    var editingProductPrice by remember { mutableStateOf<Product?>(null) }
    var customPriceInput by remember { mutableStateOf("") }
    
    var editingProductQty by remember { mutableStateOf<Product?>(null) }
    var customQtyInput by remember { mutableStateOf("") }
    var isTaxIncluded by remember { mutableStateOf(true) }

    val formattedTerminDate = if (deliveryDateMillis > 0) {
        val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        sdf.format(Date(deliveryDateMillis))
    } else {
        "Seçilmedi"
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sipariş Sepeti") },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Geri")
                    }
                }
            )
        },
        bottomBar = {
            // Toplam ve Onay Butonu Sabit Alt Kısımda
            if (cartItems.isNotEmpty()) {
                val totalRaw = cartItems.entries.sumOf { it.value.first * it.value.second }
                val currency = cartItems.keys.firstOrNull()?.currency ?: "TL"
                
                // KDV ve Ara Toplam Hesaplama
                var araToplam = 0.0
                var kdvToplam = 0.0
                
                cartItems.forEach { (product, pair) ->
                    val taxRate = product.taxSell.replace("%", "").toDoubleOrNull() ?: 0.0
                    val lineTotal = pair.first * pair.second
                    if (isTaxIncluded) {
                        val lineKdv = lineTotal - (lineTotal / (1 + taxRate / 100.0))
                        kdvToplam += lineKdv
                        araToplam += (lineTotal - lineKdv)
                    } else {
                        val lineKdv = lineTotal * (taxRate / 100.0)
                        araToplam += lineTotal
                        kdvToplam += lineKdv
                    }
                }
                
                val genelToplam = araToplam + kdvToplam
                
                Surface(tonalElevation = 8.dp, shadowElevation = 16.dp) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("Ara Toplam:", fontSize = 14.sp)
                            Text("${String.format("%.2f", araToplam)} $currency", fontSize = 14.sp)
                        }
                        Row(modifier = Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.SpaceBetween) {
                            Text("KDV Toplam:", fontSize = 14.sp)
                            Text("${String.format("%.2f", kdvToplam)} $currency", fontSize = 14.sp)
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Genel Toplam:", fontWeight = FontWeight.Bold, fontSize = 16.sp)
                            Text("${String.format("%.2f", genelToplam)} $currency", fontWeight = FontWeight.ExtraBold, fontSize = 22.sp, color = MaterialTheme.colorScheme.primary)
                        }
                        Button(
                            onClick = {
                                if (selectedCustomer == null) {
                                    Toast.makeText(context, "Lütfen önce müşteri seçin!", Toast.LENGTH_SHORT).show()
                                    return@Button
                                }
                                viewModel.confirmOrder(
                                    customerCode = selectedCustomer.code,
                                    customerName = selectedCustomer.name,
                                    customerPhone = listOf(selectedCustomer.phone, selectedCustomer.mobilePhone).filter { it.isNotEmpty() }.joinToString(" / "),
                                    customerDistrict = selectedCustomer.district,
                                    customerCity = selectedCustomer.city,
                                    customerFullAddress = selectedCustomer.fullAddress,
                                    deliveryDate = deliveryDateMillis,
                                    paymentTerm = paymentTerm,
                                    isTaxIncluded = isTaxIncluded,
                                    context = context,
                                    showPreview = true
                                )
                                Toast.makeText(context, "Sipariş Kaydedildi ve PDF Oluşturuldu!", Toast.LENGTH_SHORT).show()
                                onConfirmSuccess()
                            },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = selectedCustomer != null
                        ) {
                            Text("Siparişi Onayla ve Paylaş (PDF)")
                        }
                    }
                }
            }
        }
    ) { padding ->
        if (cartItems.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Sepetiniz boş.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize(),
                contentPadding = PaddingValues(16.dp)
            ) {
                // 1. Seçili Cari Gösterimi
                item {
                    Card(
                        modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)
                    ) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text("Cari Ünvan:", fontWeight = FontWeight.Bold, fontSize = 12.sp)
                            Text(selectedCustomer?.name ?: "Müşteri Seçilmedi!", fontWeight = FontWeight.ExtraBold, fontSize = 16.sp)
                            if (selectedCustomer != null) {
                                Text("Kod: ${selectedCustomer.code} | Tel: ${selectedCustomer.phone}", fontSize = 12.sp)
                            }
                        }
                    }
                }

                // KDV Durumu Seçimi (Switch)
                item {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("KDV Durumu", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Text(
                                text = if (isTaxIncluded) "Fiyatlar KDV Dahildir" else "Fiyatlar KDV Hariçtir",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.secondary
                            )
                        }
                        Switch(
                            checked = isTaxIncluded,
                            onCheckedChange = { isTaxIncluded = it }
                        )
                    }
                }

                // 2. Ürün Başlığı
                item {
                    Text("Sipariş Edilen Ürünler", fontWeight = FontWeight.Bold, fontSize = 14.sp, modifier = Modifier.padding(bottom = 8.dp))
                }

                // 3. Sepet Ürün Listesi
                items(cartItems.keys.toList()) { product ->
                    val pair = cartItems[product] ?: Pair(1, product.price)
                    val qty = pair.first
                    val price = pair.second

                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(product.name, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                modifier = Modifier.clickable {
                                    editingProductPrice = product
                                    customPriceInput = price.toString()
                                }
                            ) {
                                Text(
                                    text = if (price > 0) "$price ${product.currency}" else "Fiyat Girin",
                                    color = MaterialTheme.colorScheme.primary,
                                    fontWeight = FontWeight.Medium,
                                    fontSize = 13.sp
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Icon(Icons.Default.Edit, contentDescription = "Fiyat Düzenle", modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                            }
                        }
                        
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(
                                onClick = {
                                    editingProductQty = product
                                    customQtyInput = qty.toString()
                                },
                                shape = MaterialTheme.shapes.small,
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.7f),
                                modifier = Modifier.width(100.dp)
                            ) {
                                Text(
                                    text = "$qty ${product.unit}",
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 12.dp),
                                    textAlign = TextAlign.Center,
                                    fontWeight = FontWeight.ExtraBold,
                                    color = MaterialTheme.colorScheme.onPrimaryContainer,
                                    fontSize = 15.sp
                                )
                            }
                            
                            IconButton(onClick = { viewModel.removeFromCart(product) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                    HorizontalDivider(thickness = 0.5.dp, color = MaterialTheme.colorScheme.outlineVariant)
                }

                // 4. Sipariş Parametreleri (Vade, Termin)
                item {
                    Card(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 16.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            Text("Sipariş Şartları", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            OutlinedTextField(
                                value = paymentTerm,
                                onValueChange = { paymentTerm = it },
                                label = { Text("Vade") },
                                modifier = Modifier.fillMaxWidth(),
                                singleLine = true
                            )
                            
                            Row(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.spacedBy(4.dp)
                            ) {
                                listOf("Peşin", "15 Gün", "30 Gün", "60 Gün").forEach { option ->
                                    AssistChip(
                                        onClick = { paymentTerm = option },
                                        label = { Text(option, fontSize = 11.sp) }
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column {
                                    Text("Termin Tarihi:", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    Text(formattedTerminDate, fontWeight = FontWeight.Bold, fontSize = 13.sp)
                                }
                                Button(
                                    onClick = { showDatePicker = true },
                                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                                    modifier = Modifier.height(36.dp)
                                ) {
                                    Icon(Icons.Default.DateRange, null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Tarih Seç", fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
                
                // Sayfanın en altına boşluk bırak (Alt bar kapatmasın diye)
                item { Spacer(modifier = Modifier.height(100.dp)) }
            }
        }
    }

    // TARİH SEÇİCİ DIALOGU
    if (showDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    deliveryDateMillis = datePickerState.selectedDateMillis ?: 0L
                    showDatePicker = false
                }) {
                    Text("Tamam")
                }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) {
                    Text("İptal")
                }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    // FİYAT DÜZENLEME DIALOGU
    if (editingProductPrice != null) {
        AlertDialog(
            onDismissRequest = { editingProductPrice = null },
            title = { Text("Birim Fiyat Düzenle") },
            text = {
                Column {
                    Text(editingProductPrice?.name ?: "", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customPriceInput,
                        onValueChange = { customPriceInput = it },
                        label = { Text("Yeni Fiyat") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val price = customPriceInput.toDoubleOrNull()
                    if (price != null && editingProductPrice != null) {
                        viewModel.updateCartItemPrice(editingProductPrice!!, price)
                    }
                    editingProductPrice = null
                }) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProductPrice = null }) {
                    Text("İptal")
                }
            }
        )
    }

    // MİKTAR DÜZENLEME DIALOGU
    if (editingProductQty != null) {
        AlertDialog(
            onDismissRequest = { editingProductQty = null },
            title = { Text("Miktar Düzenle") },
            text = {
                Column {
                    Text(editingProductQty?.name ?: "", fontWeight = FontWeight.Bold)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = customQtyInput,
                        onValueChange = { customQtyInput = it },
                        label = { Text("Yeni Miktar") },
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val qty = customQtyInput.toIntOrNull()
                    if (qty != null && editingProductQty != null) {
                        viewModel.updateCartItemQuantity(editingProductQty!!, qty)
                    }
                    editingProductQty = null
                }) {
                    Text("Kaydet")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProductQty = null }) {
                    Text("İptal")
                }
            }
        )
    }
}

// ==========================================
// 2. CARİ KARTLAR SEKME EKRANI
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CustomersTabScreen(viewModel: OrderViewModel) {
    val customers by viewModel.customers.collectAsState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingCustomer by remember { mutableStateOf<Customer?>(null) }

    val filteredCustomers = customers.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.code.contains(searchQuery, ignoreCase = true)
    }

    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val imported = ExcelHelper.readCustomersFromExcel(context, it)
            if (imported.isNotEmpty()) {
                viewModel.importCustomersFromExcel(imported)
                Toast.makeText(context, "${imported.size} Cari Kart içeri aktarıldı.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Hata: Excel okunamadı veya biçim hatalı.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cari Kart Yönetimi") },
                actions = {
                    IconButton(onClick = { 
                        excelPickerLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Excel'den Cari Aktar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAddDialog = true }) {
                Icon(Icons.Default.Add, contentDescription = "Cari Ekle")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                label = { Text("Cari Kart Ara") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true
            )

            if (filteredCustomers.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (customers.isEmpty()) "Henüz cari kart bulunmuyor.\nExcel'den aktarabilir veya manuel ekleyebilirsiniz." 
                               else "Aranan kriterlere uygun cari kart bulunamadı.",
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(filteredCustomers) { customer ->
                        Card(modifier = Modifier.fillMaxWidth()) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(customer.name, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                                    Text("Cari Kodu: ${customer.code}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    val phones = listOf(customer.phone, customer.mobilePhone).filter { it.isNotEmpty() }.joinToString(" - ")
                                    if (phones.isNotEmpty()) {
                                        Text("Tel: $phones", fontSize = 12.sp)
                                    }
                                    val location = listOf(customer.district, customer.city).filter { it.isNotEmpty() }.joinToString(" / ")
                                    if (location.isNotEmpty()) {
                                        Text(location, fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    if (customer.fullAddress.isNotEmpty()) {
                                        Text("Adres: ${customer.fullAddress}", fontSize = 12.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    if (customer.taxOffice.isNotEmpty() || customer.taxNumber.isNotEmpty() || customer.idNumber.isNotEmpty()) {
                                        val taxInfo = listOf(customer.taxOffice, customer.taxNumber, customer.idNumber).filter { it.isNotEmpty() }.joinToString(" | ")
                                        Text("Vergi/TC: $taxInfo", fontSize = 11.sp, color = MaterialTheme.colorScheme.secondary)
                                    }
                                    if (customer.balance.isNotEmpty()) {
                                        Text(
                                            text = "Bakiye: ${customer.balance} ${customer.baStatus}",
                                            fontSize = 12.sp,
                                            fontWeight = FontWeight.SemiBold,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                Row {
                                    IconButton(onClick = { editingCustomer = customer }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = MaterialTheme.colorScheme.primary)
                                    }
                                    IconButton(onClick = { viewModel.deleteCustomer(customer) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = MaterialTheme.colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MANUEL CARİ KART EKLEME / DÜZENLEME DIALOGU
    if (showAddDialog || editingCustomer != null) {
        var code by remember(editingCustomer) { mutableStateOf(editingCustomer?.code ?: "") }
        var name by remember(editingCustomer) { mutableStateOf(editingCustomer?.name ?: "") }
        var phone by remember(editingCustomer) { mutableStateOf(editingCustomer?.phone ?: "") }
        var mobilePhone by remember(editingCustomer) { mutableStateOf(editingCustomer?.mobilePhone ?: "") }
        var district by remember(editingCustomer) { mutableStateOf(editingCustomer?.district ?: "") }
        var city by remember(editingCustomer) { mutableStateOf(editingCustomer?.city ?: "") }
        var balance by remember(editingCustomer) { mutableStateOf(editingCustomer?.balance ?: "") }
        var baStatus by remember(editingCustomer) { mutableStateOf(editingCustomer?.baStatus ?: "") }
        var fullAddress by remember(editingCustomer) { mutableStateOf(editingCustomer?.fullAddress ?: "") }
        var taxOffice by remember(editingCustomer) { mutableStateOf(editingCustomer?.taxOffice ?: "") }
        var taxNumber by remember(editingCustomer) { mutableStateOf(editingCustomer?.taxNumber ?: "") }
        var idNumber by remember(editingCustomer) { mutableStateOf(editingCustomer?.idNumber ?: "") }

        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                editingCustomer = null
            },
            title = { Text(if (editingCustomer != null) "Cari Kart Düzenle" else "Yeni Cari Kart Ekle") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    item {
                        OutlinedTextField(
                            value = code, 
                            onValueChange = { code = it }, 
                            label = { Text("Cari Kod (Zorunlu)") }, 
                            singleLine = true, 
                            modifier = Modifier.fillMaxWidth(),
                            enabled = editingCustomer == null // Anahtar alan olduğu için düzenleme sırasında kapalı
                        )
                    }
                    item {
                        OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Ünvan / Adı (Zorunlu)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = phone, onValueChange = { phone = it }, label = { Text("Telefon") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        OutlinedTextField(value = mobilePhone, onValueChange = { mobilePhone = it }, label = { Text("Cep Tel") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = taxOffice, onValueChange = { taxOffice = it }, label = { Text("Vergi Dairesi") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = taxNumber, onValueChange = { taxNumber = it }, label = { Text("Vergi No") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    item {
                        OutlinedTextField(value = idNumber, onValueChange = { idNumber = it }, label = { Text("TC Kimlik No") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = district, onValueChange = { district = it }, label = { Text("İlçe") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = city, onValueChange = { city = it }, label = { Text("Şehir") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                    item {
                        OutlinedTextField(value = fullAddress, onValueChange = { fullAddress = it }, label = { Text("Açık Adres") }, modifier = Modifier.fillMaxWidth())
                    }
                    item {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedTextField(value = balance, onValueChange = { balance = it }, label = { Text("Bakiye") }, singleLine = true, modifier = Modifier.weight(1f))
                            OutlinedTextField(value = baStatus, onValueChange = { baStatus = it }, label = { Text("B/A") }, singleLine = true, modifier = Modifier.weight(1f))
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        if (code.isNotBlank() && name.isNotBlank()) {
                            val customer = Customer(
                                code = code, name = name, phone = phone,
                                mobilePhone = mobilePhone, district = district, 
                                city = city, balance = balance, baStatus = baStatus,
                                fullAddress = fullAddress, taxOffice = taxOffice,
                                taxNumber = taxNumber, idNumber = idNumber
                            )
                            // Eğer düzenleme modundaysak, orijinal ownerUid'yi aktar
                            val finalCustomer = if (editingCustomer != null) {
                                customer.copy(ownerUid = editingCustomer!!.ownerUid)
                            } else {
                                customer
                            }
                            viewModel.addCustomer(finalCustomer)
                            showAddDialog = false
                            editingCustomer = null
                        } else {
                            Toast.makeText(context, "Kod ve Ünvan alanları zorunludur!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(if (editingCustomer != null) "Güncelle" else "Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddDialog = false
                    editingCustomer = null
                }) {
                    Text("İptal")
                }
            }
        )
    }
}

// ==========================================
// 3. STOKLAR SEKME EKRANI
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun StocksTabScreen(viewModel: OrderViewModel) {
    val products by viewModel.products.collectAsState()
    val context = LocalContext.current
    var searchQuery by remember { mutableStateOf("") }
    var showAddDialog by remember { mutableStateOf(false) }
    var editingProduct by remember { mutableStateOf<Product?>(null) }

    val filteredProducts = products.filter {
        it.name.contains(searchQuery, ignoreCase = true) || it.code.contains(searchQuery, ignoreCase = true)
    }

    val excelPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            val imported = ExcelHelper.readProductsFromExcel(context, it)
            if (imported.isNotEmpty()) {
                viewModel.importProductsFromExcel(imported)
                Toast.makeText(context, "${imported.size} Ürün içeri aktarıldı.", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Hata: Excel okunamadı veya biçim hatalı.", Toast.LENGTH_LONG).show()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Stok Yönetimi") },
                actions = {
                    IconButton(onClick = { /* Çoklu Seçim */ }) {
                        Icon(Icons.Default.Rule, contentDescription = "Seç")
                    }
                    IconButton(onClick = { 
                        excelPickerLauncher.launch(arrayOf("application/vnd.ms-excel", "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                    }) {
                        Icon(Icons.Default.FileUpload, contentDescription = "Excel'den Ürün Aktar")
                    }
                }
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showAddDialog = true },
                containerColor = Color(0xFFDDE2F1),
                contentColor = Color(0xFF1E2E5D)
            ) {
                Icon(Icons.Default.Add, contentDescription = "Stok Ekle")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .padding(padding)
                .fillMaxSize()
                .padding(horizontal = 16.dp)
        ) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Stok Kartı Ara") },
                leadingIcon = { Icon(Icons.Default.Search, null) },
                modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                singleLine = true,
                shape = MaterialTheme.shapes.medium
            )

            if (filteredProducts.isEmpty()) {
                Box(modifier = Modifier.fillMaxSize().weight(1f), contentAlignment = Alignment.Center) {
                    Text(
                        text = if (products.isEmpty()) "Henüz stok tanımlanmamış.\nExcel'den aktarabilir veya manuel ekleyebilirsiniz." 
                               else "Aranan kriterlere uygun stok bulunamadı.",
                        textAlign = TextAlign.Center
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                    contentPadding = PaddingValues(bottom = 80.dp)
                ) {
                    items(filteredProducts) { product ->
                        Card(
                            modifier = Modifier.fillMaxWidth(),
                            colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF0)),
                            shape = MaterialTheme.shapes.large
                        ) {
                            Row(
                                modifier = Modifier.padding(16.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = product.name.uppercase(),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 16.sp,
                                        color = Color.DarkGray
                                    )
                                    Text(
                                        text = "Kart Kodu: ${product.code}",
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Fiili Stok: ${String.format("%.2f", product.stockQuantity)} ${product.unit}",
                                        fontSize = 14.sp,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = Color.Black
                                    )
                                    Text(
                                        text = "KDV Alış: %${product.taxBuy}  KDV Satış: %${product.taxSell}",
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = "Fiyat: ${String.format("%.2f", product.price)} ${product.currency}",
                                        fontSize = 15.sp,
                                        color = Color(0xFF3F51B5),
                                        fontWeight = FontWeight.Bold
                                    )
                                }
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(onClick = { editingProduct = product }) {
                                        Icon(Icons.Default.Edit, contentDescription = "Düzenle", tint = Color(0xFF3F51B5))
                                    }
                                    IconButton(onClick = { viewModel.deleteProduct(product) }) {
                                        Icon(Icons.Default.Delete, contentDescription = "Sil", tint = Color(0xFFB3261E))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // MANUEL STOK EKLEME / DÜZENLEME DIALOGU
    if (showAddDialog || editingProduct != null) {
        var code by remember(editingProduct) { mutableStateOf(editingProduct?.code ?: "") }
        var name by remember(editingProduct) { mutableStateOf(editingProduct?.name ?: "") }
        var stock by remember(editingProduct) { mutableStateOf(editingProduct?.stockQuantity?.toString() ?: "") }
        var unit by remember(editingProduct) { mutableStateOf(editingProduct?.unit ?: "") }
        var price by remember(editingProduct) { mutableStateOf(editingProduct?.price?.toString() ?: "") }
        var taxBuy by remember(editingProduct) { mutableStateOf(editingProduct?.taxBuy ?: "") }
        var taxSell by remember(editingProduct) { mutableStateOf(editingProduct?.taxSell ?: "") }
        var currency by remember(editingProduct) { mutableStateOf(editingProduct?.currency ?: "TL") }

        AlertDialog(
            onDismissRequest = { 
                showAddDialog = false
                editingProduct = null
            },
            title = { Text(if (editingProduct != null) "Stok Kartı Düzenle" else "Yeni Stok Kartı Ekle") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = code, 
                        onValueChange = { code = it }, 
                        label = { Text("Kart Kodu (Zorunlu)") }, 
                        singleLine = true,
                        enabled = editingProduct == null
                    )
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Açıklama (Zorunlu)") }, singleLine = true)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = stock, onValueChange = { stock = it }, label = { Text("Fiili Stok") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = unit, onValueChange = { unit = it }, label = { Text("Ana Birim") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = price, onValueChange = { price = it }, label = { Text("Birim Fiyatı") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = currency, onValueChange = { currency = it }, label = { Text("Para Birimi") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(value = taxBuy, onValueChange = { taxBuy = it }, label = { Text("KDV Alış") }, singleLine = true, modifier = Modifier.weight(1f))
                        OutlinedTextField(value = taxSell, onValueChange = { taxSell = it }, label = { Text("KDV Satış") }, singleLine = true, modifier = Modifier.weight(1f))
                    }
                }
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        val stockDouble = stock.replace(",", ".").toDoubleOrNull() ?: 0.0
                        val priceDouble = price.replace(",", ".").toDoubleOrNull() ?: 0.0
                        if (code.isNotBlank() && name.isNotBlank()) {
                            val product = Product(code, name, stockDouble, unit, priceDouble, taxBuy, taxSell, currency)
                            // Eğer düzenleme modundaysak, orijinal ownerUid'yi aktar
                            val finalProduct = if (editingProduct != null) {
                                product.copy(ownerUid = editingProduct!!.ownerUid)
                            } else {
                                product
                            }
                            viewModel.addProduct(finalProduct)
                            showAddDialog = false
                            editingProduct = null
                        } else {
                            Toast.makeText(context, "Kod ve Ad alanları zorunludur!", Toast.LENGTH_SHORT).show()
                        }
                    }
                ) {
                    Text(if (editingProduct != null) "Güncelle" else "Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { 
                    showAddDialog = false
                    editingProduct = null
                }) {
                    Text("İptal")
                }
            }
        )
    }
}

// ==========================================
// 4. RAPORLAR SEKME EKRANI
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ReportsTabScreen(viewModel: OrderViewModel) {
    val orders by viewModel.orders.collectAsState()
    var searchQuery by remember { mutableStateOf("") }
    var startDate by remember { mutableLongStateOf(0L) }
    var endDate by remember { mutableLongStateOf(0L) }
    var orientation by remember { mutableStateOf("Portrait") }
    
    var showStartDatePicker by remember { mutableStateOf(false) }
    var showEndDatePicker by remember { mutableStateOf(false) }
    
    val sdf = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
    
    if (showStartDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showStartDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    startDate = datePickerState.selectedDateMillis ?: 0L
                    showStartDatePicker = false
                }) { Text("Tamam") }
            }
        ) { DatePicker(state = datePickerState) }
    }
    
    if (showEndDatePicker) {
        val datePickerState = rememberDatePickerState()
        DatePickerDialog(
            onDismissRequest = { showEndDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    endDate = datePickerState.selectedDateMillis ?: 0L
                    showEndDatePicker = false
                }) { Text("Tamam") }
            }
        ) { DatePicker(state = datePickerState) }
    }

    val filteredOrders = orders.filter {
        (it.customerName.contains(searchQuery, ignoreCase = true) || it.customerCode.contains(searchQuery, ignoreCase = true)) &&
        (startDate == 0L || it.date >= startDate) &&
        (endDate == 0L || it.date <= endDate + 86400000L)
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        Text("Order Reports", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)

        // Search Bar
        OutlinedTextField(
            value = searchQuery,
            onValueChange = { searchQuery = it },
            modifier = Modifier.fillMaxWidth(),
            placeholder = { Text("Customer / Code Filter") },
            leadingIcon = { Icon(Icons.Default.Search, null) },
            shape = MaterialTheme.shapes.medium
        )

        // Date Selectors
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(
                onClick = { showStartDatePicker = true },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(if (startDate == 0L) "Start Date" else sdf.format(Date(startDate)))
            }
            OutlinedButton(
                onClick = { showEndDatePicker = true },
                modifier = Modifier.weight(1f),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Text(if (endDate == 0L) "End Date" else sdf.format(Date(endDate)))
            }
        }

        // Page Orientation
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Text("Page Orientation", fontWeight = FontWeight.SemiBold)
            Spacer(modifier = Modifier.weight(1f))
            
            FilterChip(
                selected = orientation == "Portrait",
                onClick = { orientation = "Portrait" },
                label = { Text("Portrait") },
                leadingIcon = if (orientation == "Portrait") {
                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                } else null
            )
            
            FilterChip(
                selected = orientation == "Landscape",
                onClick = { orientation = "Landscape" },
                label = { Text("Landscape") },
                leadingIcon = if (orientation == "Landscape") {
                    { Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp)) }
                } else null
            )
        }

        // Action Buttons
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            val context = LocalContext.current
            Button(
                onClick = { viewModel.exportReportPdf(context, filteredOrders) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFE91E63)),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Default.PictureAsPdf, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("PDF Report")
            }
            Button(
                onClick = { viewModel.exportReportExcel(context, filteredOrders) },
                modifier = Modifier.weight(1f),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Default.TableChart, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Excel Report")
            }
        }

        // Summary Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFF5C72A4)),
            shape = MaterialTheme.shapes.large
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text("Filtered Summary", color = Color.White, fontWeight = FontWeight.Bold)
                Spacer(modifier = Modifier.height(8.dp))
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Total Orders", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Text("${filteredOrders.size} Adet", color = Color.White, fontWeight = FontWeight.Bold)
                }
                Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Grand Total:", color = Color.White.copy(alpha = 0.8f), fontSize = 14.sp)
                    Text("${filteredOrders.sumOf { it.totalAmount }} TL", color = Color.White, fontWeight = FontWeight.ExtraBold, fontSize = 18.sp)
                }
            }
        }

        // Results
        Text("Results (${filteredOrders.size})", fontWeight = FontWeight.Bold)

        filteredOrders.forEach { order ->
            var orderItems by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
            LaunchedEffect(order.id) {
                orderItems = viewModel.getOrderItems(order.id)
            }
            
            val totalQty = orderItems.sumOf { it.quantity }
            val shippedQty = orderItems.sumOf { it.shippedQuantity }
            val remainingQty = totalQty - shippedQty
            
            val totalAmount = order.totalAmount
            val shippedAmount = orderItems.sumOf { it.shippedQuantity * it.unitPrice }
            val remainingAmount = totalAmount - shippedAmount

            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF0)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(order.customerName, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(
                        "Miktar: $totalQty / Sevk: $shippedQty / Kalan: $remainingQty",
                        fontSize = 12.sp,
                        color = Color.DarkGray
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Toplam: $totalAmount TL", fontWeight = FontWeight.Bold, fontSize = 13.sp)
                        Text("Sevk T: $shippedAmount TL", color = Color(0xFF4CAF50), fontSize = 12.sp)
                        Text("Kalan T: $remainingAmount TL", color = Color(0xFFD32F2F), fontSize = 12.sp)
                    }
                    
                    val progress = if (totalQty > 0) (shippedQty / totalQty).toFloat() else 0f
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth().padding(top = 12.dp),
                        color = Color(0xFF1E2E5D),
                        trackColor = Color.White
                    )
                }
            }
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }
}

// ==========================================
// 5. SİPARİŞ GEÇMİŞİ SEKME EKRANI
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryTabScreen(viewModel: OrderViewModel) {
    val orders by viewModel.orders.collectAsState()
    val userProfile by viewModel.userProfile.collectAsState()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var editingOrderItems by remember { mutableStateOf<Pair<Order, List<OrderItem>>?>(null) }
    
    val sdf = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale.getDefault())

    Scaffold(
        topBar = {
            TopAppBar(title = { Text("Sipariş Yönetimi", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold) })
        }
    ) { padding ->
        if (orders.isEmpty()) {
            Box(modifier = Modifier.padding(padding).fillMaxSize(), contentAlignment = Alignment.Center) {
                Text("Kayıtlı sipariş bulunamadı.")
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .padding(padding)
                    .fillMaxSize()
                    .padding(horizontal = 16.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                items(orders) { order ->
                    var orderItems by remember { mutableStateOf<List<OrderItem>>(emptyList()) }
                    LaunchedEffect(order.id) {
                        orderItems = viewModel.getOrderItems(order.id)
                    }

                    val totalQty = orderItems.sumOf { it.quantity }
                    val shippedQty = orderItems.sumOf { it.shippedQuantity }
                    val remainingQty = totalQty - shippedQty
                    val shippingPercent = if (totalQty > 0) (shippedQty / totalQty * 100).toInt() else 0

                    val statusText = when(order.status) {
                        "Completed" -> "Tamamlandı"
                        "Shipping" -> "Sevk Ediliyor"
                        "Approved" -> "Onaylandı"
                        else -> "Onay Bekliyor"
                    }
                    val statusColor = when(order.status) {
                        "Completed" -> Color(0xFF4CAF50)
                        "Shipping" -> Color(0xFF2196F3)
                        "Approved" -> Color(0xFF4CAF50)
                        else -> Color(0xFFD32F2F)
                    }

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF0)),
                        elevation = CardDefaults.cardElevation(2.dp),
                        shape = MaterialTheme.shapes.large
                    ) {
                        Column(modifier = Modifier.padding(16.dp)) {
                            // Header Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column {
                                    Text(
                                        text = "Sipariş: ${order.id.take(8).uppercase()}",
                                        fontWeight = FontWeight.Bold,
                                        color = Color(0xFFD32F2F),
                                        fontSize = 18.sp
                                    )
                                    Text(
                                        text = sdf.format(Date(order.date)),
                                        fontSize = 12.sp,
                                        color = Color.Gray
                                    )
                                }
                                Text(
                                    text = statusText,
                                    fontWeight = FontWeight.Bold,
                                    fontSize = 13.sp,
                                    color = statusColor
                                )
                            }
                            
                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Customer Name
                            Text(
                                text = order.customerName.uppercase(),
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 16.sp,
                                color = Color.Black
                            )
                            
                            Spacer(modifier = Modifier.height(8.dp))
                            
                            // Details Row
                            Row(modifier = Modifier.fillMaxWidth()) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Cari: ${order.customerCode} | Toplam: ${order.totalAmount} TL",
                                        fontSize = 13.sp,
                                        color = Color.DarkGray
                                    )
                                }
                                Column(horizontalAlignment = Alignment.End) {
                                    Text(
                                        text = "Sevkiyat: %$shippingPercent",
                                        fontSize = 13.sp,
                                        color = Color(0xFF3F51B5),
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = "Kalan: $remainingQty",
                                        fontSize = 13.sp,
                                        color = Color(0xFFD32F2F),
                                        fontWeight = FontWeight.Medium
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(12.dp))
                            
                            // Sales Rep
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Default.Person, null, modifier = Modifier.size(16.dp), tint = Color.DarkGray)
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(
                                    text = "Satış Elemanı: ${order.salesRepEmail}",
                                    fontSize = 12.sp,
                                    color = Color.DarkGray
                                )
                            }

                            Spacer(modifier = Modifier.height(16.dp))
                            
                            // Actions Row
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                if (userProfile?.role == "ADMIN" && order.status == "Pending") {
                                    Button(
                                        onClick = { viewModel.approveOrder(order) },
                                        modifier = Modifier.height(40.dp),
                                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF4CAF50)),
                                        contentPadding = PaddingValues(horizontal = 16.dp)
                                    ) {
                                        Icon(Icons.Default.Check, null, modifier = Modifier.size(18.dp), tint = Color.White)
                                        Spacer(modifier = Modifier.width(8.dp))
                                        Text("Onayla", fontSize = 13.sp, color = Color.White)
                                    }
                                }

                                Button(
                                    onClick = { 
                                        scope.launch {
                                            val items = viewModel.getOrderItems(order.id)
                                            editingOrderItems = Pair(order, items)
                                        }
                                    },
                                    modifier = Modifier.height(40.dp),
                                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFFCED2DA)),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    Icon(Icons.Default.LocalShipping, null, modifier = Modifier.size(18.dp), tint = Color.DarkGray)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Sevk Et", fontSize = 13.sp, color = Color.DarkGray)
                                }

                                OutlinedButton(
                                    onClick = { viewModel.shareOrderPdfFromHistory(context, order) },
                                    modifier = Modifier.height(40.dp),
                                    border = BorderStroke(1.dp, Color.Black),
                                    contentPadding = PaddingValues(horizontal = 16.dp)
                                ) {
                                    Icon(Icons.Default.PictureAsPdf, null, modifier = Modifier.size(18.dp), tint = Color.Black)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("PDF", fontSize = 13.sp, color = Color.Black)
                                }

                                Spacer(modifier = Modifier.weight(1f))

                                IconButton(onClick = { /* Düzenle */ }) {
                                    Icon(Icons.Default.Edit, "Düzenle", tint = Color(0xFF3F51B5), modifier = Modifier.size(24.dp))
                                }

                                IconButton(onClick = { viewModel.deleteOrder(order) }) {
                                    Icon(Icons.Default.Delete, "Sil", tint = Color(0xFFD32F2F), modifier = Modifier.size(24.dp))
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // SEVKİYAT DÜZENLEME DIALOGU
    if (editingOrderItems != null) {
        val currentOrder = editingOrderItems!!.first
        val items = editingOrderItems!!.second
        var editingItemByQty by remember { mutableStateOf<OrderItem?>(null) }
        var customQtyInput by remember { mutableStateOf("") }

        Dialog(
            onDismissRequest = { editingOrderItems = null },
            properties = DialogProperties(usePlatformDefaultWidth = false)
        ) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { editingOrderItems = null }) {
                            Icon(Icons.Default.Close, null)
                        }
                        Text("Sevkiyat Yönetimi", fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    }
                    
                    Card(modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp), colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer)) {
                        Column(modifier = Modifier.padding(12.dp)) {
                            Text(currentOrder.customerName, fontWeight = FontWeight.Bold)
                            Text("Sipariş No: ${currentOrder.id.take(8).uppercase()}", fontSize = 12.sp)
                        }
                    }

                    LazyColumn(modifier = Modifier.weight(1f)) {
                        items(items) { item ->
                            val remaining = item.quantity - item.shippedQuantity
                            val progress = if (item.quantity > 0) (item.shippedQuantity / item.quantity).toFloat() else 0f
                            
                            Card(
                                modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp),
                                border = BorderStroke(1.dp, if (remaining <= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.outlineVariant)
                            ) {
                                Column(modifier = Modifier.padding(12.dp)) {
                                    Text(item.productName, fontWeight = FontWeight.SemiBold)
                                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                                        Text("Sipariş: ${item.quantity}", fontSize = 12.sp)
                                        Text("Sevk: ${item.shippedQuantity}", fontSize = 12.sp, color = MaterialTheme.colorScheme.primary, fontWeight = FontWeight.Bold)
                                        Text("Kalan: $remaining", fontSize = 12.sp, color = if (remaining > 0) Color.Red else Color.Gray)
                                    }
                                    
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier.fillMaxWidth().padding(vertical = 8.dp),
                                        color = if (remaining <= 0) Color(0xFF4CAF50) else MaterialTheme.colorScheme.primary
                                    )

                                    Button(
                                        onClick = { 
                                            editingItemByQty = item
                                            customQtyInput = ""
                                        },
                                        modifier = Modifier.align(Alignment.End).height(32.dp),
                                        contentPadding = PaddingValues(horizontal = 12.dp)
                                    ) {
                                        Text("Miktar Gir", fontSize = 11.sp)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        if (editingItemByQty != null) {
            AlertDialog(
                onDismissRequest = { editingItemByQty = null },
                title = { Text("Sevk Edilen Miktar") },
                text = {
                    Column {
                        Text(editingItemByQty?.productName ?: "", fontWeight = FontWeight.Bold)
                        Text("Toplam Sipariş: ${editingItemByQty?.quantity}", fontSize = 12.sp)
                        Spacer(modifier = Modifier.height(8.dp))
                        OutlinedTextField(
                            value = customQtyInput,
                            onValueChange = { customQtyInput = it },
                            label = { Text("Sevk Edilen Toplam Miktar") },
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            singleLine = true
                        )
                    }
                },
                confirmButton = {
                    TextButton(onClick = {
                        val qty = customQtyInput.replace(",", ".").toDoubleOrNull()
                        if (qty != null && editingItemByQty != null) {
                            // capture current item to avoid race where state is cleared before coroutine runs
                            val itemToUpdate = editingItemByQty!!
                            scope.launch {
                                // await the viewModel update to ensure DB is updated before fetching
                                viewModel.updateOrderItemShipping(itemToUpdate, qty)
                                val updatedItems = viewModel.getOrderItems(currentOrder.id)
                                editingOrderItems = Pair(currentOrder, updatedItems)
                            }
                        }
                        editingItemByQty = null
                    }) {
                        Text("Kaydet")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { editingItemByQty = null }) {
                        Text("Iptal")
                    }
                }
            )
        }
    }
}

// ==========================================
// 6. AYARLAR VE YEDEKLEME SEKME EKRANI
// ==========================================
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsTabScreen(viewModel: OrderViewModel, googleSignInClient: GoogleSignInClient) {
    val context = LocalContext.current
    val userProfile by viewModel.userProfile.collectAsState()
    val teamMembers by viewModel.teamMembers.collectAsState()
    val currentUser by viewModel.currentUser.collectAsState()
    
    var showAddMemberDialog by remember { mutableStateOf(false) }
    var memberEmail by remember { mutableStateOf("") }

    val logoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.GetContent()
    ) { uri ->
        // Logo yükleme mantığı buraya gelecek
    }

    val backupPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri?.let {
            viewModel.importBackup(
                context = context,
                uri = it,
                onSuccess = { Toast.makeText(context, "Yedek başarıyla yüklendi!", Toast.LENGTH_SHORT).show() },
                onError = { error -> Toast.makeText(context, error, Toast.LENGTH_LONG).show() }
            )
        }
    }

    var showUserGuide by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(24.dp)
    ) {
        // --- TEAM MANAGEMENT ---
        Column {
            Text(stringResource(R.string.team_management), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFF5C72A4)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    val isAdmin = userProfile?.role == "ADMIN"
                    Text(
                        stringResource(R.string.current_role, userProfile?.role ?: "INDIVIDUAL"),
                        fontWeight = FontWeight.Bold,
                        color = Color.White,
                        fontSize = 18.sp
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Text(
                        if (isAdmin) "Tüm personelleri yönetebilir ve verileri görebilirsiniz."
                        else stringResource(R.string.individual_desc),
                        fontSize = 14.sp,
                        color = Color.White.copy(alpha = 0.9f)
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { 
                            if (isAdmin) viewModel.makeMeIndividual() 
                            else viewModel.makeMeAdmin() 
                        },
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF1E2E5D)),
                        contentPadding = PaddingValues(horizontal = 24.dp, vertical = 12.dp)
                    ) {
                        Text(
                            if (isAdmin) "Bireysel Moda Geç" 
                            else stringResource(R.string.make_me_admin), 
                            color = Color.White
                        )
                    }
                }
            }
        }

        if (userProfile?.role == "ADMIN") {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(stringResource(R.string.staff_list, teamMembers.size), fontWeight = FontWeight.Bold)
                    TextButton(onClick = { showAddMemberDialog = true }) {
                        Icon(Icons.Default.Add, null)
                        Text(stringResource(R.string.staff_add))
                    }
                }
                if (teamMembers.isEmpty()) {
                    Box(modifier = Modifier.fillMaxWidth().padding(16.dp), contentAlignment = Alignment.Center) {
                        Text(stringResource(R.string.no_team_members), fontSize = 12.sp, color = Color.Gray)
                    }
                } else {
                    teamMembers.forEach { member ->
                        Card(modifier = Modifier.fillMaxWidth().padding(vertical = 4.dp)) {
                            Row(
                                modifier = Modifier.padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Default.Person, null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(modifier = Modifier.width(12.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(member.email, fontWeight = FontWeight.SemiBold)
                                    Text("Role: Sales Rep", fontSize = 11.sp)
                                }
                                IconButton(onClick = { viewModel.removeTeamMember(member.uid) }) {
                                    Icon(Icons.Default.PersonRemove, null, tint = MaterialTheme.colorScheme.error)
                                }
                            }
                        }
                    }
                }
            }
        }

        // --- COMPANY LOGO ---
        Column {
            Text(stringResource(R.string.company_logo), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF0)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(
                        stringResource(R.string.upload_logo_desc),
                        fontSize = 14.sp,
                        modifier = Modifier.fillMaxWidth(),
                        textAlign = TextAlign.Start
                    )
                    Spacer(modifier = Modifier.height(16.dp))
                    OutlinedButton(
                        onClick = { logoPickerLauncher.launch("image/*") },
                        modifier = Modifier.fillMaxWidth(),
                        shape = MaterialTheme.shapes.medium,
                        border = BorderStroke(1.dp, Color.Gray)
                    ) {
                        Icon(Icons.Default.AddPhotoAlternate, null, tint = Color.DarkGray)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.upload_logo_btn), color = Color.DarkGray)
                    }
                }
            }
        }

        // --- DATA BACKUP ---
        Column {
            Text(stringResource(R.string.data_backup), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Text(
                stringResource(R.string.export_desc),
                fontSize = 14.sp,
                color = Color.Gray
            )
            Spacer(modifier = Modifier.height(16.dp))
            Button(
                onClick = { viewModel.exportBackup(context) },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF2B427D)),
                shape = MaterialTheme.shapes.extraLarge
            ) {
                Icon(Icons.Default.CloudUpload, null)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.backup_now))
            }
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { backupPickerLauncher.launch(arrayOf("application/json", "application/octet-stream")) },
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.extraLarge,
                border = BorderStroke(1.dp, Color.Gray)
            ) {
                Icon(Icons.Default.Restore, null, tint = Color.DarkGray)
                Spacer(modifier = Modifier.width(8.dp))
                Text(stringResource(R.string.restore_backup), color = Color.DarkGray)
            }
        }

        // --- ABOUT APP ---
        Column {
            Text(stringResource(R.string.about_app), fontWeight = FontWeight.Bold, fontSize = 20.sp)
            Spacer(modifier = Modifier.height(8.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE8EAF0)),
                shape = MaterialTheme.shapes.large
            ) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(stringResource(R.string.app_name), fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text(stringResource(R.string.version, "1.0.0"), fontSize = 14.sp)
                    Spacer(modifier = Modifier.height(16.dp))
                    Button(
                        onClick = { showUserGuide = true },
                        modifier = Modifier.fillMaxWidth(),
                        colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F4759)),
                        shape = MaterialTheme.shapes.medium
                    ) {
                        Icon(Icons.AutoMirrored.Filled.Help, null)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(stringResource(R.string.user_guide))
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                    Text(stringResource(R.string.logged_in_as), fontSize = 12.sp, color = Color.Gray)
                    Text(currentUser?.email ?: "brhayd55@gmail.com", fontWeight = FontWeight.Bold)
                }
            }
        }

        // --- LOGOUT ---
        TextButton(
            onClick = { 
                viewModel.logout()
                googleSignInClient.signOut()
            },
            modifier = Modifier.align(Alignment.CenterHorizontally),
            colors = ButtonDefaults.textButtonColors(contentColor = Color(0xFFB3261E))
        ) {
            Icon(Icons.AutoMirrored.Filled.Logout, null)
            Spacer(modifier = Modifier.width(8.dp))
            Text(stringResource(R.string.logout), fontWeight = FontWeight.Bold)
        }
        
        Spacer(modifier = Modifier.height(32.dp))
    }

    // PERSONEL EKLEME DIALOGU
    if (showAddMemberDialog) {
        AlertDialog(
            onDismissRequest = { showAddMemberDialog = false },
            title = { Text("Personel Ekle") },
            text = {
                Column {
                    Text("Eklemek istediğiniz personelin uygulamaya kayıt olduğu e-posta adresini girin.", fontSize = 12.sp)
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = memberEmail,
                        onValueChange = { memberEmail = it },
                        label = { Text("Personel E-Posta") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.addTeamMember(
                        email = memberEmail,
                        onSuccess = {
                            showAddMemberDialog = false
                            memberEmail = ""
                            Toast.makeText(context, "Personel başarıyla eklendi.", Toast.LENGTH_SHORT).show()
                        },
                        onError = { error ->
                            Toast.makeText(context, error, Toast.LENGTH_LONG).show()
                        }
                    )
                }) {
                    Text("Ekle")
                }
            },
            dismissButton = {
                TextButton(onClick = { showAddMemberDialog = false }) {
                    Text("İptal")
                }
            }
        )
    }

    if (showUserGuide) {
        UserGuideDialog(onDismiss = { showUserGuide = false })
    }
}

@Composable
fun UserGuideDialog(onDismiss: () -> Unit) {
    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier.fillMaxSize(),
            color = MaterialTheme.colorScheme.surface
        ) {
            Column(modifier = Modifier.fillMaxSize()) {
                // Header
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Kullanım Kılavuzu",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Default.Close, contentDescription = "Kapat")
                    }
                }

                Column(
                    modifier = Modifier
                        .weight(1f)
                        .verticalScroll(rememberScrollState())
                        .padding(horizontal = 16.dp)
                ) {
                    GuideSection(
                        title = "1. Genel Kullanım",
                        content = "Uygulama; Sipariş girişi, Cari (Müşteri) yönetimi, Stok takibi ve Sevkiyat kontrolü aşamalarından oluşur. Siparişlerinizi oluşturduktan sonra 'Geçmiş' sekmesinden onaylayabilir ve PDF olarak paylaşabilirsiniz."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GuideSection(
                        title = "2. Excel ile Veri Aktarımı",
                        content = "Stok ve Cari kartlarınızı tek tek eklemek yerine Excel üzerinden topluca yükleyebilirsiniz. Dosyanızın ilk satırı başlık olmalı, veriler 2. satırdan başlamalıdır."
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TemplateSection(
                        title = "Stok Aktarım Şablonu (Excel)",
                        items = listOf(
                            "A" to "Kart Kodu",
                            "B" to "Açıklama",
                            "C" to "Fiili Stok",
                            "D" to "Ana Birim",
                            "E" to "Fiyat",
                            "F" to "KDV Alış",
                            "G" to "KDV Satış"
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    TemplateSection(
                        title = "Cari Aktarım Şablonu (Excel)",
                        items = listOf(
                            "A" to "Cari Kodu",
                            "B" to "Ünvan",
                            "C" to "Bakiye (TL)",
                            "D" to "B/A",
                            "E" to "Telefon 1",
                            "F" to "Cep Tel",
                            "G" to "Adres 2",
                            "H" to "İlçe",
                            "I" to "Şehir",
                            "J" to "Vergi Dairesi",
                            "K" to "Vergi Numarası",
                            "L" to "TC Kimlik No"
                        )
                    )

                    Spacer(modifier = Modifier.height(16.dp))

                    GuideSection(
                        title = "3. Sevkiyat Takibi",
                        content = "Onaylanan siparişler sevk edilebilir duruma geçer. 'Sevk Et' butonu ile parçalı sevkiyat yapabilirsiniz. Miktar girildiğinde sistem 'Kalan' miktarı otomatik hesaplar ve sipariş tamamlandığında durumunu günceller."
                    )
                    
                    Spacer(modifier = Modifier.height(24.dp))
                }

                // Bottom Button
                Button(
                    onClick = onDismiss,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF3F51B5))
                ) {
                    Text("Anladım", color = Color.White)
                }
            }
        }
    }
}

@Composable
fun GuideSection(title: String, content: String) {
    Column {
        Text(
            text = title,
            color = Color(0xFF3F51B5),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Text(
            text = content,
            fontSize = 14.sp,
            lineHeight = 20.sp,
            color = Color.DarkGray
        )
    }
}

@Composable
fun TemplateSection(title: String, items: List<Pair<String, String>>) {
    Column {
        Text(
            text = title,
            color = Color(0xFF3F51B5),
            fontWeight = FontWeight.Bold,
            fontSize = 16.sp
        )
        Spacer(modifier = Modifier.height(8.dp))
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F2F5)),
            shape = MaterialTheme.shapes.medium
        ) {
            Column(modifier = Modifier.padding(8.dp)) {
                items.forEach { (col, desc) ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 4.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = col,
                            fontWeight = FontWeight.Bold,
                            fontSize = 14.sp,
                            modifier = Modifier.width(40.dp)
                        )
                        Text(
                            text = desc,
                            fontSize = 14.sp,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    if (items.last() != (col to desc)) {
                        HorizontalDivider(color = Color.LightGray, thickness = 0.5.dp)
                    }
                }
            }
        }
    }
}
