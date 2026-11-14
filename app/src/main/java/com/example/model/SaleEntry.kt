package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.UUID
import kotlin.math.abs

@Entity(tableName = "sales")
data class SaleEntry(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val customerId: String,
    val productId: String,
    val quantityKg: BigDecimal,
    val pricePerKgAtSale: BigDecimal,
    val costPerKgAtSale: BigDecimal,
    val totalSalePrice: BigDecimal,
    val paidAmount: BigDecimal,
    val udhariAmount: BigDecimal,
    val paymentStatus: PaymentStatus,
    val soldByUserId: String,
    val returnedKg: BigDecimal = BigDecimal.ZERO,
    val billStatus: String = "ACTIVE", // ACTIVE, EDITED, RETURNED, CANCELLED
    val billNumber: String = "",
    val paymentMode: String = "CASH",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)

/**
 * Represents a consolidated customer sale bill grouping all line items (`SaleEntry` rows)
 * recorded in a single checkout for the same customer.
 */
data class GroupedSaleBill(
    val primarySale: SaleEntry,
    val items: List<SaleEntry>,
    val billNumber: String = ""
) {
    val id: String get() = primarySale.id
    val date: Long get() = primarySale.date
    val customerId: String get() = primarySale.customerId
    val soldByUserId: String get() = primarySale.soldByUserId

    val totalSalePrice: BigDecimal = items.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.totalSalePrice) }
    val paidAmount: BigDecimal = items.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.paidAmount) }
    val udhariAmount: BigDecimal = totalSalePrice.subtract(paidAmount).max(BigDecimal.ZERO)
    val returnedKg: BigDecimal = items.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.returnedKg) }
    val totalQuantityKg: BigDecimal = items.fold(BigDecimal.ZERO) { acc, s -> acc.add(s.quantityKg) }

    val isCancelled: Boolean = items.isNotEmpty() && items.all { it.billStatus == "CANCELLED" }
    val isReturned: Boolean = !isCancelled && items.any { it.billStatus == "RETURNED" || it.returnedKg > BigDecimal.ZERO }
    val isEdited: Boolean = items.any { it.billStatus == "EDITED" }

    val billStatus: String = when {
        isCancelled -> "CANCELLED"
        isReturned -> "RETURNED"
        isEdited -> "EDITED"
        else -> primarySale.billStatus
    }

    val paymentStatus: PaymentStatus = when {
        paidAmount >= totalSalePrice && totalSalePrice > BigDecimal.ZERO -> PaymentStatus.PAID
        paidAmount > BigDecimal.ZERO -> PaymentStatus.PARTIAL
        else -> PaymentStatus.UNPAID
    }

    val extraItemCount: Int get() = (items.size - 1).coerceAtLeast(0)

    fun toSummarySaleEntry(): SaleEntry = primarySale.copy(
        quantityKg = totalQuantityKg,
        totalSalePrice = totalSalePrice,
        paidAmount = paidAmount,
        udhariAmount = udhariAmount,
        paymentStatus = paymentStatus,
        returnedKg = returnedKg,
        billStatus = billStatus
    )
}

/**
 * Groups individual `SaleEntry` line items that were created in the same checkout
 * (same `billNumber`, or same `customerId` and `date` within 2.5 seconds for legacy rows)
 * into consolidated `GroupedSaleBill` objects.
 */
fun groupSalesIntoBills(
    sales: List<SaleEntry>,
    descending: Boolean = true
): List<GroupedSaleBill> {
    if (sales.isEmpty()) return emptyList()

    val rawGroups = mutableListOf<List<SaleEntry>>()
    val byCustomer = sales.filter { !it.isDeleted }.groupBy { it.customerId }

    for ((_, custSales) in byCustomer) {
        val sortedAsc = custSales.sortedWith(compareBy<SaleEntry> { it.date }.thenBy { it.id })
        var currentBatch = mutableListOf<SaleEntry>()
        for (s in sortedAsc) {
            if (currentBatch.isEmpty()) {
                currentBatch.add(s)
            } else {
                val anchor = currentBatch.first()
                val sameBill = if (anchor.billNumber.isNotBlank() || s.billNumber.isNotBlank()) {
                    anchor.billNumber.isNotBlank() && anchor.billNumber == s.billNumber
                } else {
                    abs(s.date - anchor.date) <= 2500L
                }
                if (sameBill) {
                    currentBatch.add(s)
                } else {
                    rawGroups.add(currentBatch)
                    currentBatch = mutableListOf(s)
                }
            }
        }
        if (currentBatch.isNotEmpty()) {
            rawGroups.add(currentBatch)
        }
    }

    val orderedAsc = rawGroups.sortedWith(
        compareBy<List<SaleEntry>> { it.first().date }.thenBy { it.first().id }
    )

    val numberedBills = orderedAsc.mapIndexed { index, batch ->
        val primary = batch.first()
        val storedCode = batch.firstNotNullOfOrNull { it.billNumber.takeIf { bn -> bn.isNotBlank() } }
        val code = storedCode ?: (1001 + index).toString()
        GroupedSaleBill(
            primarySale = primary,
            items = batch,
            billNumber = code
        )
    }

    return if (descending) numberedBills.asReversed() else numberedBills
}

/**
 * Builds a lookup map from any `SaleEntry.id` to its consolidated bill's 4-digit bill number.
 */
fun buildConsolidatedBillNoMap(sales: List<SaleEntry>): Map<String, String> {
    val billsAsc = groupSalesIntoBills(sales, descending = false)
    val map = HashMap<String, String>(sales.size)
    for (bill in billsAsc) {
        for (item in bill.items) {
            map[item.id] = bill.billNumber
        }
    }
    return map
}
