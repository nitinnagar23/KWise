package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.net.Uri
import androidx.core.content.FileProvider
import com.example.model.BillData
import com.example.model.PurchaseInvoice
import com.example.model.SupplierKhataSummary
import com.example.model.SupplierLedgerEntry
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BillGenerator {
    private val dateFormat = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault())
    private val shortDateFormat = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

    fun generatePurchaseInvoiceWhatsAppText(invoice: PurchaseInvoice): String {
        val dateStr = dateFormat.format(Date(invoice.date))
        val sb = StringBuilder()
        sb.appendLine("📦 *KHATAWISE - PURCHASE INVOICE / खरीद बिल* 📦")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📄 *Invoice No:* ${if (invoice.invoiceNumber.isNotBlank()) invoice.invoiceNumber else "N/A"}")
        sb.appendLine("📅 *Date:* $dateStr")
        sb.appendLine("🏭 *Wholesaler/Supplier:* ${invoice.wholesalerName}")
        if (invoice.wholesalerPhone.isNotBlank()) {
            sb.appendLine("📞 *Phone:* ${invoice.wholesalerPhone}")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("*ITEMS RECEIVED (आवक माल):*")
        val items = invoice.getItems()
        items.forEachIndexed { index, item ->
            val qtyDesc = if (item.bags > 0 && item.looseKg > BigDecimal.ZERO) {
                "${item.bags} bags + ${item.looseKg} kg (${item.totalKg} kg)"
            } else if (item.bags > 0) {
                "${item.bags} bags (${item.totalKg} kg)"
            } else {
                "${item.totalKg} kg"
            }
            val rateDesc = if (item.ratePerBag != null && item.bags > 0) {
                "${CurrencyUtils.formatInr(item.ratePerBag)}/bag"
            } else {
                "${CurrencyUtils.formatInr(item.ratePerKg, true)}/kg"
            }
            sb.appendLine("${index + 1}. *${item.feedName}*")
            sb.appendLine("   $qtyDesc @ $rateDesc = *${CurrencyUtils.formatInr(item.itemCost)}*")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("💰 *Goods Subtotal:* ${CurrencyUtils.formatInr(invoice.goodsSubtotal)}")

        val hasExtraCharges = invoice.ladhan > BigDecimal.ZERO || invoice.utaar > BigDecimal.ZERO || invoice.gaadiKiraya > BigDecimal.ZERO
        if (hasExtraCharges) {
            sb.appendLine("🚚 *CHARGES & HANDLING:*")
            if (invoice.ladhan > BigDecimal.ZERO) {
                sb.appendLine("   • Ladhan (लदान): ${CurrencyUtils.formatInr(invoice.ladhan)}")
            }
            if (invoice.utaar > BigDecimal.ZERO) {
                sb.appendLine("   • Utaar (उतराई): ${CurrencyUtils.formatInr(invoice.utaar)}")
            }
            if (invoice.gaadiKiraya > BigDecimal.ZERO) {
                sb.appendLine("   • Gaadi Kiraya (गाड़ी भाड़ा): ${CurrencyUtils.formatInr(invoice.gaadiKiraya)}")
            }
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("🏷️ *GRAND TOTAL:* *${CurrencyUtils.formatInr(invoice.totalInvoiceCost)}*")
        sb.appendLine("💵 *Paid Amount:* ${CurrencyUtils.formatInr(invoice.paidAmount)}")
        if (invoice.balanceAmount > BigDecimal.ZERO) {
            sb.appendLine("⚠️ *Pending Balance (देना बाकी):* *${CurrencyUtils.formatInr(invoice.balanceAmount)}*")
        } else {
            sb.appendLine("✅ *Payment Status:* FULLY PAID (पूर्ण चुकता)")
        }
        if (invoice.notes.isNotBlank()) {
            sb.appendLine("📝 *Note:* ${invoice.notes}")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Verified Purchase Inward Record 🙏")
        return sb.toString()
    }

    fun sharePurchaseInvoice(context: Context, invoice: PurchaseInvoice) {
        val text = generatePurchaseInvoiceWhatsAppText(invoice)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Purchase Invoice - ${invoice.wholesalerName}")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Purchase Invoice via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun generateSupplierKhataWhatsAppText(
        summary: SupplierKhataSummary,
        entries: List<SupplierLedgerEntry>
    ): String {
        val sb = StringBuilder()
        sb.appendLine("📑 *SUPPLIER KHATA STATEMENT / हिसाब-किताब* 📑")
        sb.appendLine("🌾 *KhataWise Store*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("🏭 *Wholesaler:* ${summary.wholesalerName}")
        if (summary.wholesalerPhone.isNotBlank()) {
            sb.appendLine("📞 *Phone:* ${summary.wholesalerPhone}")
        }
        sb.appendLine("📊 *Total Purchases (कुल खरीद):* ${CurrencyUtils.formatInr(summary.totalPurchases)}")
        sb.appendLine("💵 *Total Paid (कुल चुकाया):* ${CurrencyUtils.formatInr(summary.totalPaid)}")
        if (summary.balancePayable > BigDecimal.ZERO) {
            sb.appendLine("⚠️ *NET BALANCE PAYABLE (देना शेष):* *${CurrencyUtils.formatInr(summary.balancePayable)}*")
        } else {
            sb.appendLine("✅ *STATUS:* ALL DUES CLEARED (खाता चुकता)")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("*RECENT TRANSACTIONS / लेन-देन:*")

        val recentEntries = entries.take(10)
        if (recentEntries.isEmpty()) {
            sb.appendLine("No transaction history.")
        } else {
            recentEntries.forEach { entry ->
                when (entry) {
                    is SupplierLedgerEntry.InvoiceEntry -> {
                        val inv = entry.invoice
                        val dStr = shortDateFormat.format(Date(inv.date))
                        val invNum = if (inv.invoiceNumber.isNotBlank()) " #${inv.invoiceNumber}" else ""
                        sb.appendLine("📥 [$dStr] Invoice$invNum: +${CurrencyUtils.formatInr(inv.totalInvoiceCost)} (Paid: ${CurrencyUtils.formatInr(inv.paidAmount)})")
                    }
                    is SupplierLedgerEntry.PaymentEntry -> {
                        val pay = entry.payment
                        val dStr = shortDateFormat.format(Date(pay.date))
                        val modeStr = if (pay.paymentMode.isNotBlank()) " [${pay.paymentMode}]" else ""
                        val noteStr = if (pay.note.isNotBlank()) " - ${pay.note}" else ""
                        sb.appendLine("📤 [$dStr] Payment: -${CurrencyUtils.formatInr(pay.amount)}$modeStr$noteStr")
                    }
                }
            }
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Statement generated on ${dateFormat.format(Date())}")
        return sb.toString()
    }

    fun shareSupplierKhata(
        context: Context,
        summary: SupplierKhataSummary,
        entries: List<SupplierLedgerEntry>
    ) {
        val text = generateSupplierKhataWhatsAppText(summary, entries)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "Supplier Khata - ${summary.wholesalerName}")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Supplier Khata via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    fun generateWhatsAppText(bill: BillData): String {
        val dateStr = dateFormat.format(Date(bill.date))
        val sb = StringBuilder()
        sb.appendLine("🌾 *KHATAWISE - CATTLE FEED & GRAIN STORE* 🌾")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📄 *Bill No:* ${bill.billNumber}")
        sb.appendLine("📅 *Date:* $dateStr")
        sb.appendLine("👤 *Customer:* ${bill.customerName}")
        if (bill.customerVillage.isNotBlank()) {
            sb.appendLine("📍 *Village:* ${bill.customerVillage}")
        }
        if (bill.customerPhone.isNotBlank()) {
            sb.appendLine("📞 *Phone:* ${bill.customerPhone}")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("*ITEMS:*")
        bill.items.forEachIndexed { index, item ->
            val qtyDesc = if (item.bags > 0 && item.looseKg.signum() > 0) {
                "${item.bags} bags + ${item.looseKg} kg (${item.totalKg} kg)"
            } else if (item.bags > 0) {
                "${item.bags} bags (${item.totalKg} kg)"
            } else {
                "${item.totalKg} kg"
            }
            sb.appendLine("${index + 1}. *${item.feedName}*")
            sb.appendLine("   $qtyDesc @ ${CurrencyUtils.formatInr(item.pricePerKg, true)}/kg = *${CurrencyUtils.formatInr(item.itemTotal)}*")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("💰 *Bill Total:* ${CurrencyUtils.formatInr(bill.subtotal)}")
        sb.appendLine("💵 *Paid Now:* ${CurrencyUtils.formatInr(bill.paidAmount)}")
        if (bill.udhariAmount.signum() > 0) {
            sb.appendLine("⚠️ *New Udhari:* ${CurrencyUtils.formatInr(bill.udhariAmount)}")
        }
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("📊 *TOTAL OUTSTANDING UDHARI:* *${CurrencyUtils.formatInr(bill.newTotalUdhari)}*")
        sb.appendLine("━━━━━━━━━━━━━━━━━━━━━")
        sb.appendLine("Billed by: ${bill.staffName}")
        sb.appendLine("Thank you for your business! 🙏")
        return sb.toString()
    }

    fun shareTextBill(context: Context, bill: BillData) {
        val text = generateWhatsAppText(bill)
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_SUBJECT, "KhataWise Bill #${bill.billNumber}")
            putExtra(Intent.EXTRA_TEXT, text)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Bill via")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    /**
     * Renders a high-resolution, crisp thermal/invoice receipt bitmap and shares it via FileProvider.
     */
    fun sharePictureBill(context: Context, bill: BillData) {
        val bitmap = renderBillBitmap(bill)
        val imageUri = saveBitmapToCache(context, bitmap, "receipt_${bill.billNumber}.png")

        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, imageUri)
            putExtra(Intent.EXTRA_TEXT, "KhataWise Bill #${bill.billNumber} for ${bill.customerName}")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        val chooser = Intent.createChooser(intent, "Share Picture Bill")
        chooser.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        context.startActivity(chooser)
    }

    private fun renderBillBitmap(bill: BillData): Bitmap {
        val width = 720
        // Calculate dynamic height
        val baseHeight = 620
        val itemsHeight = bill.items.size * 70
        val height = baseHeight + itemsHeight

        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)

        // Draw clean white background with soft shadow border
        canvas.drawColor(Color.parseColor("#F4F6F8"))

        val cardPaint = Paint().apply {
            color = Color.WHITE
            isAntiAlias = true
        }
        val cardRect = RectF(24f, 24f, (width - 24).toFloat(), (height - 24).toFloat())
        canvas.drawRoundRect(cardRect, 20f, 20f, cardPaint)

        // Header Banner (Deep Forest Green)
        val headerPaint = Paint().apply {
            color = Color.parseColor("#1B4332")
            isAntiAlias = true
        }
        val headerRect = RectF(24f, 24f, (width - 24).toFloat(), 150f)
        canvas.drawRoundRect(headerRect, 20f, 20f, headerPaint)
        // fill bottom corners of header
        canvas.drawRect(24f, 130f, (width - 24).toFloat(), 150f, headerPaint)

        val titlePaint = Paint().apply {
            color = Color.WHITE
            textSize = 34f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("KHATAWISE", (width / 2).toFloat(), 80f, titlePaint)

        val subTitlePaint = Paint().apply {
            color = Color.parseColor("#E9C46A")
            textSize = 20f
            typeface = Typeface.DEFAULT_BOLD
            isAntiAlias = true
            textAlign = Paint.Align.CENTER
        }
        canvas.drawText("CATTLE FEED & GRAIN STORE", (width / 2).toFloat(), 115f, subTitlePaint)

        // Bill Meta
        val textPaint = Paint().apply {
            color = Color.parseColor("#2B2D42")
            textSize = 22f
            isAntiAlias = true
        }
        val boldPaint = Paint().apply {
            color = Color.parseColor("#1B4332")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val rightPaint = Paint().apply {
            color = Color.parseColor("#2B2D42")
            textSize = 22f
            isAntiAlias = true
            textAlign = Paint.Align.RIGHT
        }
        val rightBoldPaint = Paint().apply {
            color = Color.parseColor("#1B4332")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            textAlign = Paint.Align.RIGHT
            isAntiAlias = true
        }

        var y = 190f
        canvas.drawText("Bill No: #${bill.billNumber}", 48f, y, boldPaint)
        val dateStr = SimpleDateFormat("dd/MM/yyyy hh:mm a", Locale.getDefault()).format(Date(bill.date))
        canvas.drawText(dateStr, (width - 48).toFloat(), y, rightPaint)

        y += 36f
        canvas.drawText("Customer: ${bill.customerName}", 48f, y, boldPaint)
        if (bill.customerVillage.isNotBlank()) {
            canvas.drawText("Village: ${bill.customerVillage}", (width - 48).toFloat(), y, rightPaint)
        }

        y += 28f
        // Divider line
        val linePaint = Paint().apply {
            color = Color.parseColor("#E0E0E0")
            strokeWidth = 2f
        }
        canvas.drawLine(48f, y, (width - 48).toFloat(), y, linePaint)

        // Table Header
        y += 34f
        val thPaint = Paint().apply {
            color = Color.parseColor("#6C757D")
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        canvas.drawText("FEED ITEM", 48f, y, thPaint)
        canvas.drawText("QTY / WEIGHT", 360f, y, thPaint)
        val thRight = Paint(thPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("AMOUNT", (width - 48).toFloat(), y, thRight)

        y += 16f
        canvas.drawLine(48f, y, (width - 48).toFloat(), y, linePaint)

        // Items
        bill.items.forEach { item ->
            y += 36f
            canvas.drawText(item.feedName, 48f, y, textPaint)

            val qtyDesc = if (item.bags > 0 && item.looseKg.signum() > 0) {
                "${item.bags} bags + ${item.looseKg} kg"
            } else if (item.bags > 0) {
                "${item.bags} bags (${item.totalKg} kg)"
            } else {
                "${item.totalKg} kg"
            }
            canvas.drawText(qtyDesc, 360f, y, textPaint)
            canvas.drawText(CurrencyUtils.formatInr(item.itemTotal), (width - 48).toFloat(), y, rightBoldPaint)

            y += 24f
            val rateText = "@ ${CurrencyUtils.formatInr(item.pricePerKg, true)} / kg"
            val subRatePaint = Paint().apply {
                color = Color.parseColor("#6C757D")
                textSize = 17f
                isAntiAlias = true
            }
            canvas.drawText(rateText, 48f, y, subRatePaint)
        }

        y += 24f
        canvas.drawLine(48f, y, (width - 48).toFloat(), y, linePaint)

        // Financial Summary
        y += 40f
        canvas.drawText("Total Bill Amount:", 48f, y, textPaint)
        canvas.drawText(CurrencyUtils.formatInr(bill.subtotal), (width - 48).toFloat(), y, rightBoldPaint)

        y += 34f
        val greenPaint = Paint().apply {
            color = Color.parseColor("#2D6A4F")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val greenRightPaint = Paint(greenPaint).apply { textAlign = Paint.Align.RIGHT }
        canvas.drawText("Paid Now (Cash/UPI):", 48f, y, greenPaint)
        canvas.drawText(CurrencyUtils.formatInr(bill.paidAmount), (width - 48).toFloat(), y, greenRightPaint)

        if (bill.udhariAmount.signum() > 0) {
            y += 34f
            val redPaint = Paint().apply {
                color = Color.parseColor("#D90429")
                textSize = 22f
                typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
                isAntiAlias = true
            }
            val redRightPaint = Paint(redPaint).apply { textAlign = Paint.Align.RIGHT }
            canvas.drawText("Added to Udhari (Credit):", 48f, y, redPaint)
            canvas.drawText(CurrencyUtils.formatInr(bill.udhariAmount), (width - 48).toFloat(), y, redRightPaint)
        }

        // Total Outstanding Box
        y += 40f
        val udhariBoxPaint = Paint().apply {
            color = Color.parseColor("#FFF3CD")
            isAntiAlias = true
        }
        val udhariBoxStroke = Paint().apply {
            color = Color.parseColor("#FFEBAA")
            style = Paint.Style.STROKE
            strokeWidth = 2f
            isAntiAlias = true
        }
        val boxRect = RectF(48f, y, (width - 48).toFloat(), y + 64f)
        canvas.drawRoundRect(boxRect, 12f, 12f, udhariBoxPaint)
        canvas.drawRoundRect(boxRect, 12f, 12f, udhariBoxStroke)

        val alertTextPaint = Paint().apply {
            color = Color.parseColor("#856404")
            textSize = 22f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            isAntiAlias = true
        }
        val alertValPaint = Paint(alertTextPaint).apply {
            textAlign = Paint.Align.RIGHT
            color = Color.parseColor("#D90429")
        }
        canvas.drawText("Total Outstanding Udhari:", 64f, y + 40f, alertTextPaint)
        canvas.drawText(CurrencyUtils.formatInr(bill.newTotalUdhari), (width - 64).toFloat(), y + 40f, alertValPaint)

        // Footer
        y += 90f
        val footerPaint = Paint().apply {
            color = Color.parseColor("#6C757D")
            textSize = 18f
            textAlign = Paint.Align.CENTER
            isAntiAlias = true
        }
        canvas.drawText("Served by: ${bill.staffName}  •  Thank You!", (width / 2).toFloat(), y, footerPaint)

        return bitmap
    }

    private fun saveBitmapToCache(context: Context, bitmap: Bitmap, fileName: String): Uri {
        val cachePath = File(context.cacheDir, "images")
        cachePath.mkdirs()
        val file = File(cachePath, fileName)
        val fos = FileOutputStream(file)
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, fos)
        fos.flush()
        fos.close()
        val authority = "${context.packageName}.fileprovider"
        return FileProvider.getUriForFile(context, authority, file)
    }
}
