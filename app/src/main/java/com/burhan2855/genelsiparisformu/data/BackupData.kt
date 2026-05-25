package com.burhan2855.genelsiparisformu.data

data class BackupData(
    val products: List<Product>,
    val customers: List<Customer>,
    val orders: List<Order>,
    val orderItems: List<OrderItem>,
    val backupDate: Long = System.currentTimeMillis(),
    val appVersion: String = "1.0"
)
