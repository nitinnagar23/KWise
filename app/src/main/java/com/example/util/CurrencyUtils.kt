package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode
import java.text.DecimalFormat
import java.text.DecimalFormatSymbols
import java.util.Locale

object CurrencyUtils {
    val MONEY_SCALE = 2
    val WEIGHT_SCALE = 2
    val ROUNDING = RoundingMode.HALF_UP

    /**
     * Formats BigDecimal amount into Indian Rupee format with ₹ symbol
     * Example: 100000 -> ₹1,00,000
     * Example: 1550.50 -> ₹1,550.50
     */
    fun formatInr(amount: BigDecimal?, includeDecimals: Boolean = false): String {
        if (amount == null) return "₹0"
        val scaled = amount.setScale(if (includeDecimals) 2 else 0, ROUNDING)
        return "₹" + formatIndianNumber(scaled, includeDecimals)
    }

    fun formatInr(amount: Double, includeDecimals: Boolean = false): String {
        return formatInr(BigDecimal(amount.toString()), includeDecimals)
    }

    fun formatInr(amount: Long): String {
        return formatInr(BigDecimal(amount), false)
    }

    /**
     * Formats Indian numbering system: 1,00,00,000 (Lakhs and Crores)
     */
    private fun formatIndianNumber(value: BigDecimal, includeDecimals: Boolean): String {
        val isNegative = value.signum() < 0
        val absVal = value.abs()
        val plainStr = absVal.setScale(if (includeDecimals) 2 else 0, ROUNDING).toPlainString()

        val parts = plainStr.split(".")
        val integerPart = parts[0]
        val decimalPart = if (parts.size > 1 && includeDecimals) ".${parts[1]}" else ""

        if (integerPart.length <= 3) {
            val res = integerPart + decimalPart
            return if (isNegative) "-$res" else res
        }

        // Last 3 digits
        val lastThree = integerPart.substring(integerPart.length - 3)
        var remaining = integerPart.substring(0, integerPart.length - 3)
        val chunks = mutableListOf<String>()

        while (remaining.isNotEmpty()) {
            if (remaining.length <= 2) {
                chunks.add(0, remaining)
                break
            } else {
                val chunk = remaining.substring(remaining.length - 2)
                chunks.add(0, chunk)
                remaining = remaining.substring(0, remaining.length - 2)
            }
        }

        val formattedInt = chunks.joinToString(",") + "," + lastThree
        val result = formattedInt + decimalPart
        return if (isNegative) "-$result" else result
    }

    fun formatWeight(kg: BigDecimal?): String {
        if (kg == null) return "0"
        val scaled = kg.setScale(2, ROUNDING).stripTrailingZeros()
        return if (scaled.compareTo(BigDecimal.ZERO) == 0) "0" else scaled.toPlainString()
    }

    fun formatWeightKg(kg: BigDecimal?): String {
        return "${formatWeight(kg)} kg"
    }

    fun parseBigDecimal(str: String?): BigDecimal {
        if (str.isNullOrBlank()) return BigDecimal.ZERO
        return try {
            BigDecimal(str.trim().replace(",", "")).setScale(4, ROUNDING)
        } catch (e: Exception) {
            BigDecimal.ZERO
        }
    }
}
