package com.example.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import java.math.BigDecimal
import java.util.UUID

@Entity(tableName = "supplier_payments")
data class SupplierPayment(
    @PrimaryKey
    val id: String = UUID.randomUUID().toString(),
    val date: Long = System.currentTimeMillis(),
    val wholesalerName: String,
    val amount: BigDecimal,
    val paymentMode: String = "CASH", // CASH, UPI, BANK_TRANSFER, CHEQUE
    val note: String = "",
    val recordedByUserId: String = "",
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val isDeleted: Boolean = false,
    val syncStatus: SyncStatus = SyncStatus.PENDING
)

data class SupplierKhataSummary(
    val wholesalerName: String,
    val wholesalerPhone: String = "",
    val totalPurchases: BigDecimal = BigDecimal.ZERO,
    val totalPaid: BigDecimal = BigDecimal.ZERO,
    val balancePayable: BigDecimal = BigDecimal.ZERO,
    val invoiceCount: Int = 0,
    val lastTransactionDate: Long = 0L
)

sealed class SupplierLedgerEntry(val date: Long) {
    data class InvoiceEntry(
        val invoice: PurchaseInvoice
    ) : SupplierLedgerEntry(invoice.date)

    data class PaymentEntry(
        val payment: SupplierPayment
    ) : SupplierLedgerEntry(payment.date)
}
