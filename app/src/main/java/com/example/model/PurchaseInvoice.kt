package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

data class PurchaseInvoiceItem(
    val productId: String,
    val feedName: String,
    val bags: Int = 0,
    val looseKg: BigDecimal = BigDecimal.ZERO,
    val totalKg: BigDecimal = BigDecimal.ZERO,
    val ratePerBag: BigDecimal? = null,
    val ratePerKg: BigDecimal = BigDecimal.ZERO,
    val itemCost: BigDecimal = BigDecimal.ZERO
)

@Entity(tableName = "purchase_invoices")
data class PurchaseInvoice(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val invoiceNumber: String = "",
    val date: Long = System.currentTimeMillis(),
    val wholesalerName: String,
    val wholesalerPhone: String = "",
    val itemsJson: String = "[]",
    val ladhan: BigDecimal = BigDecimal.ZERO, // लदान (Loading charges - optional)
    val utaar: BigDecimal = BigDecimal.ZERO,  // उतराई (Unloading charges - optional)
    val gaadiKiraya: BigDecimal = BigDecimal.ZERO, // गाड़ी किराया (Freight / transport - optional)
    val goodsSubtotal: BigDecimal = BigDecimal.ZERO,
    val totalInvoiceCost: BigDecimal = BigDecimal.ZERO,
    val paidAmount: BigDecimal = BigDecimal.ZERO,
    val balanceAmount: BigDecimal = BigDecimal.ZERO,
    val paymentStatus: PaymentStatus = PaymentStatus.PAID,
    val notes: String = "",
    val recordedByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING
) {
    fun getItems(): List<PurchaseInvoiceItem> = deserializeItems(itemsJson)

    companion object {
        fun serializeItems(items: List<PurchaseInvoiceItem>): String {
            val arr = JSONArray()
            for (item in items) {
                val obj = JSONObject()
                obj.put("productId", item.productId)
                obj.put("feedName", item.feedName)
                obj.put("bags", item.bags)
                obj.put("looseKg", item.looseKg.toPlainString())
                obj.put("totalKg", item.totalKg.toPlainString())
                if (item.ratePerBag != null) {
                    obj.put("ratePerBag", item.ratePerBag.toPlainString())
                }
                obj.put("ratePerKg", item.ratePerKg.toPlainString())
                obj.put("itemCost", item.itemCost.toPlainString())
                arr.put(obj)
            }
            return arr.toString()
        }

        fun deserializeItems(itemsJson: String): List<PurchaseInvoiceItem> {
            return try {
                val arr = JSONArray(itemsJson)
                val list = mutableListOf<PurchaseInvoiceItem>()
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    list.add(
                        PurchaseInvoiceItem(
                            productId = obj.optString("productId"),
                            feedName = obj.optString("feedName"),
                            bags = obj.optInt("bags", 0),
                            looseKg = BigDecimal(obj.optString("looseKg", "0")),
                            totalKg = BigDecimal(obj.optString("totalKg", "0")),
                            ratePerBag = if (obj.has("ratePerBag") && !obj.isNull("ratePerBag")) {
                                BigDecimal(obj.getString("ratePerBag"))
                            } else null,
                            ratePerKg = BigDecimal(obj.optString("ratePerKg", "0")),
                            itemCost = BigDecimal(obj.optString("itemCost", "0"))
                        )
                    )
                }
                list
            } catch (e: Exception) {
                emptyList()
            }
        }
    }
}
