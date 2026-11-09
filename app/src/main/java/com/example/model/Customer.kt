package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import org.json.JSONArray
import org.json.JSONObject
import java.math.BigDecimal
import java.util.UUID

data class TaggedMobileNumber(
    val number: String,
    val tag: String = "Self"
)

@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val name: String,
    val fatherName: String = "",
    val phone: String = "",
    val phonesJson: String = "",
    val village: String = "",
    val photoDriveUrl: String? = null,
    val totalUdhari: BigDecimal = BigDecimal.ZERO,
    val creditLimit: BigDecimal = BigDecimal.ZERO,
    val promiseDate: Long? = null,
    val createdByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING
) {
    private fun parseMetaObject(): JSONObject? {
        val trimmed = phonesJson.trim()
        if (trimmed.startsWith("{")) {
            return try {
                JSONObject(trimmed)
            } catch (_: Throwable) {
                null
            }
        }
        return null
    }

    fun getTaggedPhones(): List<TaggedMobileNumber> {
        val trimmed = phonesJson.trim()
        if (trimmed.isNotBlank()) {
            try {
                val arr = when {
                    trimmed.startsWith("{") -> JSONObject(trimmed).optJSONArray("phones")
                    trimmed.startsWith("[") -> JSONArray(trimmed)
                    else -> null
                }
                if (arr != null) {
                    val list = mutableListOf<TaggedMobileNumber>()
                    for (i in 0 until arr.length()) {
                        val obj = arr.getJSONObject(i)
                        val num = obj.optString("number", "").trim()
                        val tag = obj.optString("tag", "Self").ifBlank { "Self" }
                        if (num.isNotBlank()) {
                            list.add(TaggedMobileNumber(num, tag))
                        }
                    }
                    if (list.isNotEmpty()) return list
                }
            } catch (_: Throwable) {
            }
        }
        return if (phone.isNotBlank()) {
            listOf(TaggedMobileNumber(phone.trim(), "Self"))
        } else {
            emptyList()
        }
    }

    fun getAutoBlockAtLimit(): Boolean {
        return parseMetaObject()?.optBoolean("autoBlockAtLimit", true) ?: true
    }

    fun getBlockUdhariNow(): Boolean {
        return parseMetaObject()?.optBoolean("blockUdhariNow", false) ?: false
    }

    fun getReminderChannel(): String {
        return parseMetaObject()?.optString("reminderChannel", "WhatsApp")?.ifBlank { "WhatsApp" } ?: "WhatsApp"
    }

    fun getNotes(): String {
        return parseMetaObject()?.optString("notes", "") ?: ""
    }

    fun getPromisedAmount(): BigDecimal? {
        val raw = parseMetaObject()?.optString("promisedAmount", "")?.trim().orEmpty()
        return raw.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO }
    }

    fun getOpeningUdhari(): BigDecimal {
        val raw = parseMetaObject()?.optString("openingUdhari", "")?.trim().orEmpty()
        return raw.toBigDecimalOrNull()?.takeIf { it > BigDecimal.ZERO } ?: BigDecimal.ZERO
    }

    fun getInitials(): String {
        val parts = name.trim().split(Regex("\\s+")).filter { it.isNotBlank() }
        return when {
            parts.size >= 2 -> "${parts.first().first().uppercaseChar()}${parts.last().first().uppercaseChar()}"
            parts.size == 1 -> parts.first().take(2).uppercase()
            else -> "CU"
        }
    }

    companion object {
        fun serializeTaggedPhones(phones: List<TaggedMobileNumber>): String {
            val valid = phones.filter { it.number.isNotBlank() }
            if (valid.isEmpty()) return ""
            val arr = JSONArray()
            for (item in valid) {
                val obj = JSONObject()
                obj.put("number", item.number.trim())
                obj.put("tag", item.tag.ifBlank { "Self" })
                arr.put(obj)
            }
            return arr.toString()
        }

        fun serializeCustomerMeta(
            phones: List<TaggedMobileNumber>,
            autoBlockAtLimit: Boolean = true,
            blockUdhariNow: Boolean = false,
            reminderChannel: String = "WhatsApp",
            notes: String = "",
            promisedAmount: BigDecimal? = null,
            openingUdhari: BigDecimal = BigDecimal.ZERO
        ): String {
            val root = JSONObject()
            val arr = JSONArray()
            for (item in phones.filter { it.number.isNotBlank() }) {
                val obj = JSONObject()
                obj.put("number", item.number.trim())
                obj.put("tag", item.tag.ifBlank { "Self" })
                arr.put(obj)
            }
            root.put("phones", arr)
            root.put("autoBlockAtLimit", autoBlockAtLimit)
            root.put("blockUdhariNow", blockUdhariNow)
            root.put("reminderChannel", reminderChannel.ifBlank { "WhatsApp" })
            if (notes.isNotBlank()) {
                root.put("notes", notes.trim())
            }
            if (promisedAmount != null && promisedAmount > BigDecimal.ZERO) {
                root.put("promisedAmount", promisedAmount.stripTrailingZeros().toPlainString())
            }
            if (openingUdhari > BigDecimal.ZERO) {
                root.put("openingUdhari", openingUdhari.stripTrailingZeros().toPlainString())
            }
            return root.toString()
        }
    }
}
