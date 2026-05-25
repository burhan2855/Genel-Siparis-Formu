package com.burhan2855.genelsiparisformu.data

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.util.UUID

@Entity(tableName = "products")
data class Product(
    @PrimaryKey val code: String,
    val name: String = "",
    val stockQuantity: Double = 0.0,
    val unit: String = "adet",
    val price: Double = 0.0,
    val taxBuy: String = "0%",
    val taxSell: String = "0%",
    val currency: String = "TL",
    val ownerUid: String = ""
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey val code: String,
    val name: String = "",
    val phone: String = "",
    val mobilePhone: String = "",
    val district: String = "",
    val city: String = "",
    val fullAddress: String = "",
    val balance: String = "",
    val baStatus: String = "",
    val taxOffice: String = "",
    val taxNumber: String = "",
    val idNumber: String = "",
    val ownerUid: String = ""
)

@Entity(tableName = "orders")
data class Order(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val customerCode: String = "",
    val customerName: String = "",
    val customerPhone: String = "",
    val customerDistrict: String = "",
    val customerCity: String = "",
    val customerFullAddress: String = "",
    val totalAmount: Double = 0.0,
    val deliveryDate: Long = 0L,
    val paymentTerm: String = "",
    val isTaxIncluded: Boolean = false,
    val ownerUid: String = "",
    val adminUid: String = "",
    val salesRepEmail: String = "",
    val status: String = "Pending"
)

@Entity(tableName = "order_items")
data class OrderItem(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val orderId: String = "",
    val productCode: String = "",
    val productName: String = "",
    val quantity: Double = 0.0,
    val shippedQuantity: Double = 0.0,
    val unitPrice: Double = 0.0,
    val currency: String = "TL",
    val unit: String = "adet",
    val taxRate: Double = 0.0
)

data class UserProfile(
    val uid: String = "",
    val email: String = "",
    val role: String = "INDIVIDUAL",
    val adminUid: String = ""
)
