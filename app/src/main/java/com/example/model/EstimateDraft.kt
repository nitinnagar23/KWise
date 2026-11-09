package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

data class EstimateDraftLineItem(
    val productId: String,
    val productName: String,
    val bags: Int,
    val looseKg: String,
    val customBagPrice: String = "",
    val customLoosePrice: String = ""
)

data class EstimateDraftMetadata(
    val mode: String = "ITEMS",
    val customerId: String = "",
    val customerName: String = "",
    val lockRates: Boolean = true,
    val items: List<EstimateDraftLineItem> = emptyList()
)

@Entity(tableName = "estimate_drafts")
data class EstimateDraft(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val title: String,
    val itemsJson: String,
    val totalWeightKg: BigDecimal = BigDecimal.ZERO,
    val totalAmount: BigDecimal = BigDecimal.ZERO,
    val createdByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
) {
    fun parseItems(): List<EstimateDraftLineItem> {
        return try {
            val trimmed = itemsJson.trim()
            val arr = if (trimmed.startsWith("{")) {
                JSONObject(trimmed).optJSONArray("items") ?: JSONArray()
            } else {
                JSONArray(trimmed)
            }
            val result = mutableListOf<EstimateDraftLineItem>()
            for (i in 0 until arr.length()) {
                val obj = arr.optJSONObject(i) ?: continue
                result.add(
                    EstimateDraftLineItem(
                        productId = obj.optString("productId", ""),
                        productName = obj.optString("productName", ""),
                        bags = obj.optInt("bags", 0),
                        looseKg = obj.optString("looseKg", "0"),
                        customBagPrice = obj.optString("customBagPrice", ""),
                        customLoosePrice = obj.optString("customLoosePrice", "")
                    )
                )
            }
            result
        } catch (_: Exception) {
            emptyList()
        }
    }

    fun getDraftMode(): String {
        return try {
            val trimmed = itemsJson.trim()
            if (trimmed.startsWith("{")) {
                val mode = JSONObject(trimmed).optString("mode", "")
                if (mode.isNotBlank()) return mode
            }
            if (title.contains("budget", ignoreCase = true)) "BUDGET" else "ITEMS"
        } catch (_: Exception) {
            if (title.contains("budget", ignoreCase = true)) "BUDGET" else "ITEMS"
        }
    }

    fun getCustomerId(): String {
        return try {
            val trimmed = itemsJson.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).optString("customerId", "")
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun getCustomerName(): String {
        return try {
            val trimmed = itemsJson.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).optString("customerName", "")
            } else ""
        } catch (_: Exception) {
            ""
        }
    }

    fun isRatesLocked(): Boolean {
        return try {
            val trimmed = itemsJson.trim()
            if (trimmed.startsWith("{")) {
                JSONObject(trimmed).optBoolean("lockRates", true)
            } else true
        } catch (_: Exception) {
            true
        }
    }

    fun parseMetadata(): EstimateDraftMetadata {
        return EstimateDraftMetadata(
            mode = getDraftMode(),
            customerId = getCustomerId(),
            customerName = getCustomerName(),
            lockRates = isRatesLocked(),
            items = parseItems()
        )
    }

    companion object {
        fun serializeItems(
            items: List<EstimateDraftLineItem>,
            mode: String = "ITEMS",
            customerId: String = "",
            customerName: String = "",
            lockRates: Boolean = true
        ): String {
            val arr = JSONArray()
            items.forEach { item ->
                val obj = JSONObject().apply {
                    put("productId", item.productId)
                    put("productName", item.productName)
                    put("bags", item.bags)
                    put("looseKg", item.looseKg)
                    put("customBagPrice", item.customBagPrice)
                    put("customLoosePrice", item.customLoosePrice)
                }
                arr.put(obj)
            }
            val root = JSONObject().apply {
                put("mode", mode)
                put("customerId", customerId)
                put("customerName", customerName)
                put("lockRates", lockRates)
                put("items", arr)
            }
            return root.toString()
        }
    }
}
