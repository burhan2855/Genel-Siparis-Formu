package com.burhan2855.genelsiparisformu.data

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update

@Dao
interface OrderDao {
    // Products
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProducts(products: List<Product>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertProduct(product: Product)

    @Query("SELECT * FROM products")
    suspend fun getAllProducts(): List<Product>

    @Query("DELETE FROM products WHERE code = :code")
    suspend fun deleteProductByCode(code: String)

    @Delete
    suspend fun deleteProduct(product: Product)

    // Customers
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomers(customers: List<Customer>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertCustomer(customer: Customer)

    @Query("SELECT * FROM customers")
    suspend fun getAllCustomers(): List<Customer>

    @Query("DELETE FROM customers WHERE code = :code")
    suspend fun deleteCustomerByCode(code: String)

    @Delete
    suspend fun deleteCustomer(customer: Customer)

    // Orders
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrder(order: Order)

    @Query("SELECT * FROM orders")
    suspend fun getAllOrders(): List<Order>

    @Update
    suspend fun updateOrder(order: Order)

    @Delete
    suspend fun deleteOrder(order: Order)

    @Query("DELETE FROM orders WHERE id = :id")
    suspend fun deleteOrderById(id: String)

    @Query("DELETE FROM orders")
    suspend fun deleteAllOrders()

    // Order Items
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrderItem(items: List<OrderItem>)

    @Query("SELECT * FROM order_items WHERE orderId = :orderId")
    suspend fun getOrderItems(orderId: String): List<OrderItem>

    @Query("DELETE FROM order_items WHERE orderId = :orderId")
    suspend fun deleteOrderItems(orderId: String)

    @Query("SELECT * FROM order_items")
    suspend fun getAllOrderItems(): List<OrderItem>

    @Update
    suspend fun updateOrderItem(item: OrderItem)
}
