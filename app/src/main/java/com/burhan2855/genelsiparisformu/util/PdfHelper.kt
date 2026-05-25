package com.burhan2855.genelsiparisformu.util

import android.content.Context
import com.burhan2855.genelsiparisformu.data.Order
import com.burhan2855.genelsiparisformu.data.OrderItem
import java.io.File

object PdfHelper {
    fun createOrderPdf(context: Context, order: Order, items: List<OrderItem>): File? {
        return try {
            val file = File(context.cacheDir, "order_${order.id}.pdf")
            file.writeText("Sipariş PDF - ${order.customerName}\nKalem sayısı: ${items.size}")
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun createReportPdf(context: Context, orders: List<Order>, itemMap: Map<String, List<OrderItem>>): File? {
        return try {
            val file = File(context.cacheDir, "report_${System.currentTimeMillis()}.pdf")
            file.writeText("Rapor - sipariş sayısı: ${orders.size}")
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}
