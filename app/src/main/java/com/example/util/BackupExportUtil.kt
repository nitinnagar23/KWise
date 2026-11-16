package com.example.util

import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import androidx.core.content.FileProvider
import com.example.model.Customer
import com.example.model.PaymentEntry
import com.example.model.Product
import com.example.model.PurchaseInvoice
import com.example.model.SaleEntry
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.math.BigDecimal
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object BackupExportUtil {

    private const val STALE_EXPORT_MAX_AGE_MS = 60L * 60L * 1000L // 1 hour

    /**
     * Purges exported CSV/PDF/JSON/statement files older than [maxAgeMs] from cache directories (S-8).
     */
    fun purgeStaleExports(context: Context, maxAgeMs: Long = STALE_EXPORT_MAX_AGE_MS) {
        val cutoff = System.currentTimeMillis() - maxAgeMs
        listOf("bills", "statements", "templates").forEach { sub ->
            val dir = File(context.cacheDir, sub)
            if (dir.exists() && dir.isDirectory) {
                dir.listFiles()?.forEach { f ->
                    if (f.isFile && f.lastModified() < cutoff) {
                        runCatching { f.delete() }
                    }
                }
            }
        }
    }

    private fun ensureExportDir(context: Context): File {
        purgeStaleExports(context)
        val dir = File(context.cacheDir, "bills")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Sanitizes a cell value against CSV/Excel formula injection (S-7) and escapes double quotes.
     */
    internal fun sanitizeCsvCell(raw: String): String {
        val trimmedStart = raw.trimStart()
        val neutralized = if (trimmedStart.isNotEmpty() &&
            (trimmedStart[0] == '=' || trimmedStart[0] == '+' || trimmedStart[0] == '-' ||
                trimmedStart[0] == '@' || trimmedStart[0] == '\t' || trimmedStart[0] == '\r')
        ) {
            "'$raw"
        } else {
            raw
        }
        return neutralized.replace("\"", "\"\"")
    }

    /**
     * Generates and shares a GST-Ready Accountant CSV (Excel-compatible) report.
     * Note: [SaleEntry.quantityKg] and [SaleEntry.totalSalePrice] are already net of any returns
     * (see [com.example.data.repository.CattleFeedRepository.returnSaleItems]), so we use them
     * directly without subtracting [SaleEntry.returnedKg] a second time (C-1, C-5).
     */
    fun exportGstReadyCsv(
        context: Context,
        shopName: String,
        periodLabel: String,
        sales: List<SaleEntry>,
        purchases: List<PurchaseInvoice>,
        products: Map<String, Product>,
        customers: Map<String, Customer>
    ) {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val file = File(ensureExportDir(context), "KhataWise_GST_Report_$fileStamp.csv")

        val activeSales = sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" }
        var totalRevenue = BigDecimal.ZERO
        var totalCogs = BigDecimal.ZERO
        var totalGrossMargin = BigDecimal.ZERO

        val sb = StringBuilder()
        sb.appendLine("KHATAWISE - GST & ACCOUNTANT MONTHLY REPORT")
        sb.appendLine("Shop Name,\"${sanitizeCsvCell(shopName)}\"")
        sb.appendLine("Report Period,\"${sanitizeCsvCell(periodLabel)}\"")
        sb.appendLine("Generated On,\"${dateFormat.format(Date())}\"")
        sb.appendLine()

        sb.appendLine("Date,Bill ID,Customer Name,Village,Product,HSN/Category,Net Weight (kg),Returned Weight (kg),Selling Rate/kg (INR),Cost Rate/kg (INR),Taxable Sale Value (INR),COGS (INR),Gross Profit (INR),Paid Amount (INR),Udhari Due (INR),Bill Status")

        activeSales.sortedByDescending { it.date }.forEach { sale ->
            val netKg = sale.quantityKg.max(BigDecimal.ZERO)
            val revenue = sale.totalSalePrice.max(BigDecimal.ZERO)
            val cogs = sale.costPerKgAtSale.multiply(netKg).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val margin = revenue.subtract(cogs)

            totalRevenue = totalRevenue.add(revenue)
            totalCogs = totalCogs.add(cogs)
            totalGrossMargin = totalGrossMargin.add(margin)

            val cust = customers[sale.customerId]
            val prod = products[sale.productId]
            val custName = sanitizeCsvCell(cust?.name ?: "Walk-in")
            val village = sanitizeCsvCell(cust?.village ?: "")
            val prodName = sanitizeCsvCell(prod?.feedName ?: "Cattle Feed")
            val billIdLabel = sanitizeCsvCell(sale.billNumber.ifBlank { sale.id.take(8).uppercase() })
            val statusLabel = sanitizeCsvCell(sale.billStatus)

            sb.appendLine(
                "\"${dateFormat.format(Date(sale.date))}\"," +
                    "\"$billIdLabel\"," +
                    "\"$custName\"," +
                    "\"$village\"," +
                    "\"$prodName\"," +
                    "\"2309 (Cattle Feed)\"," +
                    "${netKg.toPlainString()}," +
                    "${sale.returnedKg.toPlainString()}," +
                    "${sale.pricePerKgAtSale.toPlainString()}," +
                    "${sale.costPerKgAtSale.toPlainString()}," +
                    "${revenue.toPlainString()}," +
                    "${cogs.toPlainString()}," +
                    "${margin.toPlainString()}," +
                    "${sale.paidAmount.toPlainString()}," +
                    "${sale.udhariAmount.toPlainString()}," +
                    "\"$statusLabel\""
            )
        }

        val totalHandlingExpenses = purchases.filter { !it.isDeleted }.fold(BigDecimal.ZERO) { acc, inv ->
            acc.add(inv.ladhan).add(inv.utaar).add(inv.gaadiKiraya)
        }
        val netProfitAfterExpenses = totalGrossMargin.subtract(totalHandlingExpenses)

        sb.appendLine()
        sb.appendLine("SUMMARY METRICS,Amount (INR)")
        sb.appendLine("Gross Sales Revenue,${totalRevenue.toPlainString()}")
        sb.appendLine("Cost of Goods Sold (COGS),${totalCogs.toPlainString()}")
        sb.appendLine("Gross Trading Margin,${totalGrossMargin.toPlainString()}")
        sb.appendLine("Freight & Handling Expenses (Ladhan + Utaar + Gaadi Kiraya),${totalHandlingExpenses.toPlainString()}")
        sb.appendLine("Expense-Adjusted Net Profit,${netProfitAfterExpenses.toPlainString()}")

        file.writeText(sb.toString())
        shareFile(context, file, "text/csv", "Share GST CSV/Excel Report")
    }

    /**
     * Generates and shares a multi-page GST-Ready PDF Report for accountants.
     */
    fun exportGstReadyPdf(
        context: Context,
        shopName: String,
        periodLabel: String,
        sales: List<SaleEntry>,
        purchases: List<PurchaseInvoice>,
        products: Map<String, Product>,
        customers: Map<String, Customer>
    ) {
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val file = File(ensureExportDir(context), "KhataWise_GST_Report_$fileStamp.pdf")

        val activeSales = sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" }
        var totalRevenue = BigDecimal.ZERO
        var totalCogs = BigDecimal.ZERO
        var totalGrossMargin = BigDecimal.ZERO

        activeSales.forEach { sale ->
            val netKg = sale.quantityKg.max(BigDecimal.ZERO)
            val revenue = sale.totalSalePrice.max(BigDecimal.ZERO)
            val cogs = sale.costPerKgAtSale.multiply(netKg).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            totalRevenue = totalRevenue.add(revenue)
            totalCogs = totalCogs.add(cogs)
            totalGrossMargin = totalGrossMargin.add(revenue.subtract(cogs))
        }

        val totalHandlingExpenses = purchases.filter { !it.isDeleted }.fold(BigDecimal.ZERO) { acc, inv ->
            acc.add(inv.ladhan).add(inv.utaar).add(inv.gaadiKiraya)
        }
        val netProfit = totalGrossMargin.subtract(totalHandlingExpenses)

        val pdfDocument = PdfDocument()
        val titlePaint = Paint().apply {
            color = Color.rgb(16, 185, 129)
            textSize = 18f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val headerPaint = Paint().apply {
            color = Color.BLACK
            textSize = 12f
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        }
        val bodyPaint = Paint().apply {
            color = Color.DKGRAY
            textSize = 10f
        }
        val linePaint = Paint().apply {
            color = Color.LTGRAY
            strokeWidth = 1f
        }

        var pageNumber = 1
        var currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
        var canvas: Canvas = currentPage.canvas

        fun drawTableHeader(targetCanvas: Canvas, startY: Float): Float {
            var hy = startY
            targetCanvas.drawText("Date", 32f, hy, headerPaint)
            targetCanvas.drawText("Customer", 110f, hy, headerPaint)
            targetCanvas.drawText("Product", 230f, hy, headerPaint)
            targetCanvas.drawText("Qty(kg)", 360f, hy, headerPaint)
            targetCanvas.drawText("Sale", 425f, hy, headerPaint)
            targetCanvas.drawText("Margin", 500f, hy, headerPaint)
            hy += 8f
            targetCanvas.drawLine(32f, hy, 563f, hy, linePaint)
            return hy + 14f
        }

        var y = 40f
        canvas.drawText("$shopName — GST & Profit Statement", 32f, y, titlePaint)
        y += 20f
        val dateStr = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date())
        canvas.drawText("Period: $periodLabel   |   Generated: $dateStr   |   HSN: 2309 (Cattle Feed)", 32f, y, bodyPaint)
        y += 14f
        canvas.drawLine(32f, y, 563f, y, linePaint)
        y += 22f

        canvas.drawText("1. FINANCIAL & PROFITABILITY SUMMARY", 32f, y, headerPaint)
        y += 18f
        canvas.drawText("Gross Sales Revenue: ${CurrencyUtils.formatInr(totalRevenue)}", 40f, y, bodyPaint)
        canvas.drawText("Cost of Goods Sold (COGS): ${CurrencyUtils.formatInr(totalCogs)}", 300f, y, bodyPaint)
        y += 16f
        canvas.drawText("Gross Trading Profit: ${CurrencyUtils.formatInr(totalGrossMargin)}", 40f, y, bodyPaint)
        canvas.drawText("Handling & Freight Expenses: ${CurrencyUtils.formatInr(totalHandlingExpenses)}", 300f, y, bodyPaint)
        y += 18f
        canvas.drawText("Expense-Adjusted Net Profit: ${CurrencyUtils.formatInr(netProfit)}", 40f, y, headerPaint)
        y += 14f
        canvas.drawLine(32f, y, 563f, y, linePaint)
        y += 22f

        canvas.drawText("2. SALES REGISTER (${activeSales.size} Entries)", 32f, y, headerPaint)
        y += 18f
        y = drawTableHeader(canvas, y)

        val shortDate = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        activeSales.sortedByDescending { it.date }.forEach { sale ->
            if (y > 790f) {
                pdfDocument.finishPage(currentPage)
                pageNumber++
                currentPage = pdfDocument.startPage(PdfDocument.PageInfo.Builder(595, 842, pageNumber).create())
                canvas = currentPage.canvas
                canvas.drawText("$shopName — Sales Register (Page $pageNumber)", 32f, 36f, headerPaint)
                y = drawTableHeader(canvas, 58f)
            }
            val netKg = sale.quantityKg.max(BigDecimal.ZERO)
            val rev = sale.totalSalePrice.max(BigDecimal.ZERO)
            val cogs = sale.costPerKgAtSale.multiply(netKg).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val margin = rev.subtract(cogs)
            val custName = (customers[sale.customerId]?.name ?: "Walk-in").take(16)
            val prodName = (products[sale.productId]?.feedName ?: "Feed").take(18)

            canvas.drawText(shortDate.format(Date(sale.date)), 32f, y, bodyPaint)
            canvas.drawText(custName, 110f, y, bodyPaint)
            canvas.drawText(prodName, 230f, y, bodyPaint)
            canvas.drawText(netKg.stripTrailingZeros().toPlainString(), 360f, y, bodyPaint)
            canvas.drawText(CurrencyUtils.formatInr(rev), 425f, y, bodyPaint)
            canvas.drawText(CurrencyUtils.formatInr(margin), 500f, y, bodyPaint)
            y += 15f
        }

        pdfDocument.finishPage(currentPage)
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        shareFile(context, file, "application/pdf", "Share GST PDF Report")
    }

    /**
     * Creates a complete JSON backup of shop data (customers, products, sales, and payments)
     * and opens the share chooser (Google Drive, WhatsApp, etc.).
     */
    fun exportFullBackupJson(
        context: Context,
        shopName: String,
        customers: List<Customer>,
        products: List<Product>,
        sales: List<SaleEntry>,
        payments: List<PaymentEntry>
    ) {
        val fileStamp = SimpleDateFormat("yyyyMMdd_HHmm", Locale.getDefault()).format(Date())
        val file = File(ensureExportDir(context), "KhataWise_Drive_Backup_$fileStamp.json")

        val root = JSONObject().apply {
            put("app", "KhataWise")
            put("version", 2)
            put("shopName", shopName)
            put("exportedAt", System.currentTimeMillis())
            put("customersCount", customers.size)
            put("productsCount", products.size)
            put("salesCount", sales.size)
            put("paymentsCount", payments.size)

            val custArr = JSONArray()
            customers.forEach { c ->
                custArr.put(
                    JSONObject().apply {
                        put("id", c.id)
                        put("name", c.name)
                        put("fatherName", c.fatherName)
                        put("phone", c.phone)
                        put("phonesJson", c.phonesJson)
                        put("village", c.village)
                        put("totalUdhari", c.totalUdhari.toPlainString())
                        put("creditLimit", c.creditLimit.toPlainString())
                        put("promiseDate", c.promiseDate ?: 0L)
                        put("createdAt", c.createdAt)
                        put("updatedAt", c.updatedAt)
                        put("isDeleted", c.isDeleted)
                    }
                )
            }
            put("customers", custArr)

            val prodArr = JSONArray()
            products.forEach { p ->
                prodArr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("feedName", p.feedName)
                        put("baseUnit", p.baseUnit)
                        put("kgPerBag", p.kgPerBag.toPlainString())
                        put("stockKg", p.currentStockKg.toPlainString())
                        put("sellingPricePerKg", p.sellingPricePerKg.toPlainString())
                        put("looseSellingPricePerKg", p.looseSellingPricePerKg.toPlainString())
                        put("costPerKg", p.averagePurchaseCostPerKg.toPlainString())
                        put("isLooseOnly", p.isLooseOnly)
                        put("createdAt", p.createdAt)
                        put("updatedAt", p.updatedAt)
                        put("isDeleted", p.isDeleted)
                    }
                )
            }
            put("products", prodArr)

            val salesArr = JSONArray()
            sales.forEach { s ->
                salesArr.put(
                    JSONObject().apply {
                        put("id", s.id)
                        put("billNumber", s.billNumber)
                        put("date", s.date)
                        put("customerId", s.customerId)
                        put("productId", s.productId)
                        put("quantityKg", s.quantityKg.toPlainString())
                        put("returnedKg", s.returnedKg.toPlainString())
                        put("pricePerKgAtSale", s.pricePerKgAtSale.toPlainString())
                        put("costPerKgAtSale", s.costPerKgAtSale.toPlainString())
                        put("totalSalePrice", s.totalSalePrice.toPlainString())
                        put("paidAmount", s.paidAmount.toPlainString())
                        put("udhariAmount", s.udhariAmount.toPlainString())
                        put("paymentStatus", s.paymentStatus.name)
                        put("paymentMode", s.paymentMode)
                        put("billStatus", s.billStatus)
                        put("soldByUserId", s.soldByUserId)
                        put("createdAt", s.createdAt)
                        put("updatedAt", s.updatedAt)
                        put("isDeleted", s.isDeleted)
                    }
                )
            }
            put("sales", salesArr)

            val paymentsArr = JSONArray()
            payments.forEach { p ->
                paymentsArr.put(
                    JSONObject().apply {
                        put("id", p.id)
                        put("date", p.date)
                        put("customerId", p.customerId)
                        put("amount", p.amount.toPlainString())
                        put("linkedSaleId", p.linkedSaleId ?: "")
                        put("note", p.note)
                        put("paymentMode", p.paymentMode)
                        put("collectedByUserId", p.collectedByUserId)
                        put("createdAt", p.createdAt)
                        put("updatedAt", p.updatedAt)
                        put("isDeleted", p.isDeleted)
                    }
                )
            }
            put("payments", paymentsArr)
        }

        file.writeText(root.toString(2))
        shareFile(context, file, "application/json", "Backup KhataWise Data to Google Drive")
    }

    private fun shareFile(context: Context, file: File, mimeType: String, chooserTitle: String) {
        val uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mimeType
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, file.nameWithoutExtension)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        val chooser = Intent.createChooser(intent, chooserTitle).apply {
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
        context.startActivity(chooser)
    }
}
