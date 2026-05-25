package com.burhan2855.genelsiparisformu.util

import android.content.Context
import android.net.Uri
import com.burhan2855.genelsiparisformu.data.Customer
import com.burhan2855.genelsiparisformu.data.Order
import com.burhan2855.genelsiparisformu.data.OrderItem
import com.burhan2855.genelsiparisformu.data.Product
import org.apache.poi.ss.usermodel.WorkbookFactory
import java.io.File

object ExcelHelper {
    // Basit CSV tabanlı rapor üretici (uygulama içinde paylaşım için yeterli)
    fun createReportExcel(context: Context, orders: List<Order>, itemMap: Map<String, List<OrderItem>>): File? {
        return try {
            val file = File(context.cacheDir, "report_${System.currentTimeMillis()}.xlsx")
            val sb = StringBuilder()
            sb.append("OrderId,Customer,Total\n")
            orders.forEach { o ->
                sb.append("${o.id},${o.customerName},${o.totalAmount}\n")
            }
            file.writeText(sb.toString())
            file
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }

    fun readProductsFromExcel(context: Context, uri: Uri): List<Product> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val workbook = WorkbookFactory.create(input)
                val sheet = workbook.getSheetAt(0)
                val products = mutableListOf<Product>()
                val rows = sheet.iterator()
                if (rows.hasNext()) rows.next() // header
                while (rows.hasNext()) {
                    val row = rows.next()
                    val code = row.getCell(0)?.stringCellValue ?: continue
                    val name = row.getCell(1)?.stringCellValue ?: ""
                    val stockQuantity = row.getCell(2)?.numericCellValue ?: 0.0
                    val unit = row.getCell(3)?.stringCellValue ?: "adet"
                    val price = row.getCell(4)?.numericCellValue ?: 0.0
                    val taxBuy = row.getCell(5)?.stringCellValue ?: "0%"
                    val taxSell = row.getCell(6)?.stringCellValue ?: "0%"
                    val currency = row.getCell(7)?.stringCellValue ?: "TL"
                    products.add(Product(code, name, stockQuantity, unit, price, taxBuy, taxSell, currency))
                }
                workbook.close()
                products
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }

    fun readCustomersFromExcel(context: Context, uri: Uri): List<Customer> {
        return try {
            context.contentResolver.openInputStream(uri)?.use { input ->
                val workbook = WorkbookFactory.create(input)
                val sheet = workbook.getSheetAt(0)
                val customers = mutableListOf<Customer>()
                val rows = sheet.iterator()
                if (rows.hasNext()) rows.next()
                while (rows.hasNext()) {
                    val row = rows.next()
                    val code = row.getCell(0)?.stringCellValue ?: continue
                    val name = row.getCell(1)?.stringCellValue ?: ""
                    val phone = row.getCell(2)?.stringCellValue ?: ""
                    val mobilePhone = row.getCell(3)?.stringCellValue ?: ""
                    val district = row.getCell(4)?.stringCellValue ?: ""
                    val city = row.getCell(5)?.stringCellValue ?: ""
                    val fullAddress = row.getCell(6)?.stringCellValue ?: ""
                    val balance = row.getCell(7)?.stringCellValue ?: ""
                    val baStatus = row.getCell(8)?.stringCellValue ?: ""
                    val taxOffice = row.getCell(9)?.stringCellValue ?: ""
                    val taxNumber = row.getCell(10)?.stringCellValue ?: ""
                    val idNumber = row.getCell(11)?.stringCellValue ?: ""
                    customers.add(
                        Customer(
                            code = code,
                            name = name,
                            phone = phone,
                            mobilePhone = mobilePhone,
                            district = district,
                            city = city,
                            fullAddress = fullAddress,
                            balance = balance,
                            baStatus = baStatus,
                            taxOffice = taxOffice,
                            taxNumber = taxNumber,
                            idNumber = idNumber
                        )
                    )
                }
                workbook.close()
                customers
            } ?: emptyList()
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}
