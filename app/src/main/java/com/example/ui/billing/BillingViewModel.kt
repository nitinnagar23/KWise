package com.example.ui.billing

import androidx.compose.runtime.mutableStateListOf
import androidx.lifecycle.ViewModel
import com.example.model.BillData
import com.example.model.Customer
import com.example.model.Permission
import com.example.model.Product
import com.example.model.Role
import com.example.model.can
import com.example.util.CurrencyUtils
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.math.BigDecimal
import java.math.RoundingMode

class BillingViewModel : ViewModel() {
    private val _selectedCustomer = MutableStateFlow<Customer?>(null)
    val selectedCustomer: StateFlow<Customer?> = _selectedCustomer.asStateFlow()

    private val _isWalkInCashCustomer = MutableStateFlow(false)
    val isWalkInCashCustomer: StateFlow<Boolean> = _isWalkInCashCustomer.asStateFlow()

    private val _customerSearchQuery = MutableStateFlow("")
    val customerSearchQuery: StateFlow<String> = _customerSearchQuery.asStateFlow()

    private val _paidAmountStr = MutableStateFlow("")
    val paidAmountStr: StateFlow<String> = _paidAmountStr.asStateFlow()

    private val _selectedPayMode = MutableStateFlow("CUSTOM") // FULL, HALF, UDHARI, CUSTOM
    val selectedPayMode: StateFlow<String> = _selectedPayMode.asStateFlow()

    private val _promiseDateMillis = MutableStateFlow<Long?>(null)
    val promiseDateMillis: StateFlow<Long?> = _promiseDateMillis.asStateFlow()

    private val _customBillDateMillis = MutableStateFlow<Long?>(null)
    val customBillDateMillis: StateFlow<Long?> = _customBillDateMillis.asStateFlow()

    private val _generatedBill = MutableStateFlow<BillData?>(null)
    val generatedBill: StateFlow<BillData?> = _generatedBill.asStateFlow()

    val cartLines = mutableStateListOf<BillCartLine>()

    fun selectCustomer(customer: Customer?) {
        _selectedCustomer.value = customer
        if (customer != null) {
            _isWalkInCashCustomer.value = false
        }
    }

    fun toggleWalkInCash(isWalkIn: Boolean) {
        _isWalkInCashCustomer.value = isWalkIn
        if (isWalkIn) {
            _selectedCustomer.value = null
            _selectedPayMode.value = "FULL"
        }
    }

    fun setCustomerSearchQuery(query: String) {
        _customerSearchQuery.value = query
    }

    fun setPayMode(mode: String, grandTotal: BigDecimal) {
        _selectedPayMode.value = mode
        when (mode) {
            "FULL" -> _paidAmountStr.value = grandTotal.stripTrailingZeros().toPlainString()
            "HALF" -> {
                val half = grandTotal.divide(BigDecimal("2"), 0, RoundingMode.HALF_UP)
                _paidAmountStr.value = half.toPlainString()
            }
            "UDHARI" -> _paidAmountStr.value = "0"
        }
    }

    fun setCustomPaidAmount(amountStr: String) {
        _selectedPayMode.value = "CUSTOM"
        _paidAmountStr.value = amountStr
    }

    fun setPromiseDate(millis: Long?) {
        _promiseDateMillis.value = millis
    }

    fun setCustomBillDateMillis(millis: Long?) {
        _customBillDateMillis.value = millis
    }

    fun setGeneratedBill(bill: BillData?) {
        _generatedBill.value = bill
    }

    fun addOrIncrementProduct(prod: Product) {
        val existingIdx = cartLines.indexOfFirst { it.productId == prod.id }
        if (existingIdx >= 0) {
            val current = cartLines[existingIdx]
            if (prod.isLooseOnly) {
                val nextKg = (current.looseKgStr.toBigDecimalOrNull() ?: BigDecimal.ZERO).add(BigDecimal("5"))
                cartLines[existingIdx] = current.copy(looseKgStr = nextKg.stripTrailingZeros().toPlainString())
            } else {
                cartLines[existingIdx] = current.copy(bagsCount = current.bagsCount + 1)
            }
        } else {
            cartLines.add(
                BillCartLine(
                    productId = prod.id,
                    bagsCount = if (prod.isLooseOnly) 0 else 1,
                    looseKgStr = if (prod.isLooseOnly) "5" else ""
                )
            )
        }
    }

    fun calculateCartLines(
        activeProducts: List<Product>,
        actorRole: Role?
    ): List<Triple<BillCartLine, Product, Pair<BigDecimal, BigDecimal>>> {
        val allowOverride = can(actorRole, Permission.OVERRIDE_BILL_RATE)
        return cartLines.mapNotNull { line ->
            val prod = activeProducts.find { it.id == line.productId } ?: return@mapNotNull null
            val effectiveBags = if (prod.isLooseOnly) 0 else line.bagsCount.coerceAtLeast(0)
            val looseKg = line.looseKgStr.toBigDecimalOrNull() ?: BigDecimal.ZERO
            val totalKg = prod.kgPerBag.multiply(BigDecimal(effectiveBags)).add(looseKg)

            val bagRate = if (allowOverride) {
                line.customBagRateStr.toBigDecimalOrNull() ?: prod.bagSellingPrice
            } else {
                prod.bagSellingPrice
            }
            val looseRate = if (allowOverride) {
                line.customLooseRateStr.toBigDecimalOrNull() ?: prod.effectiveLooseRatePerKg
            } else {
                prod.effectiveLooseRatePerKg
            }

            val bagSub = if (effectiveBags > 0) bagRate.multiply(BigDecimal(effectiveBags)) else BigDecimal.ZERO
            val looseSub = if (looseKg > BigDecimal.ZERO) looseRate.multiply(looseKg) else BigDecimal.ZERO
            val lineTotal = bagSub.add(looseSub).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            Triple(line, prod, Pair(totalKg, lineTotal))
        }
    }
}
