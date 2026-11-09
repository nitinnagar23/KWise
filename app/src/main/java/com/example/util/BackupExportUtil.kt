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

    private fun ensureExportDir(context: Context): File {
        val dir = File(context.cacheDir, "bills")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    /**
     * Generates and shares a GST-Ready Accountant CSV (Excel-compatible) report.
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
        sb.appendLine("Shop Name,\"${shopName.replace("\"", "\"\"")}\"")
        sb.appendLine("Report Period,\"$periodLabel\"")
        sb.appendLine("Generated On,\"${dateFormat.format(Date())}\"")
        sb.appendLine()

        sb.appendLine("Date,Bill ID,Customer Name,Village,Product,HSN/Category,Net Weight (kg),Selling Rate/kg (INR),Cost Rate/kg (INR),Taxable Sale Value (INR),COGS (INR),Gross Profit (INR),Paid Amount (INR),Udhari Due (INR),Bill Status")

        activeSales.sortedByDescending { it.date }.forEach { sale ->
            val netKg = sale.quantityKg.subtract(sale.returnedKg).max(BigDecimal.ZERO)
            val revenue = if (sale.quantityKg > BigDecimal.ZERO && sale.returnedKg > BigDecimal.ZERO) {
                sale.pricePerKgAtSale.multiply(netKg)
            } else {
                sale.totalSalePrice
            }
            val cogs = sale.costPerKgAtSale.multiply(netKg)
            val margin = revenue.subtract(cogs)

            totalRevenue = totalRevenue.add(revenue)
            totalCogs = totalCogs.add(cogs)
            totalGrossMargin = totalGrossMargin.add(margin)

            val cust = customers[sale.customerId]
            val prod = products[sale.productId]
            val custName = (cust?.name ?: "Walk-in").replace("\"", "\"\"")
            val village = (cust?.village ?: "").replace("\"", "\"\"")
            val prodName = (prod?.feedName ?: "Cattle Feed").replace("\"", "\"\"")

            sb.appendLine(
                "\"${dateFormat.format(Date(sale.date))}\"," +
                    "\"${sale.id.take(8).uppercase()}\"," +
                    "\"$custName\"," +
                    "\"$village\"," +
                    "\"$prodName\"," +
                    "\"2309 (Cattle Feed)\"," +
                    "${netKg.toPlainString()}," +
                    "${sale.pricePerKgAtSale.toPlainString()}," +
                    "${sale.costPerKgAtSale.toPlainString()}," +
                    "${revenue.toPlainString()}," +
                    "${cogs.toPlainString()}," +
                    "${margin.toPlainString()}," +
                    "${sale.paidAmount.toPlainString()}," +
                    "${sale.udhariAmount.toPlainString()}," +
                    "\"${sale.billStatus}\""
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
     * Generates and shares a formatted GST-Ready PDF Report for accountants.
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
            val netKg = sale.quantityKg.subtract(sale.returnedKg).max(BigDecimal.ZERO)
            val revenue = if (sale.quantityKg > BigDecimal.ZERO && sale.returnedKg > BigDecimal.ZERO) {
                sale.pricePerKgAtSale.multiply(netKg)
            } else {
                sale.totalSalePrice
            }
            val cogs = sale.costPerKgAtSale.multiply(netKg)
            totalRevenue = totalRevenue.add(revenue)
            totalCogs = totalCogs.add(cogs)
            totalGrossMargin = totalGrossMargin.add(revenue.subtract(cogs))
        }

        val totalHandlingExpenses = purchases.filter { !it.isDeleted }.fold(BigDecimal.ZERO) { acc, inv ->
            acc.add(inv.ladhan).add(inv.utaar).add(inv.gaadiKiraya)
        }
        val netProfit = totalGrossMargin.subtract(totalHandlingExpenses)

        val pdfDocument = PdfDocument()
        val pageInfo = PdfDocument.PageInfo.Builder(595, 842, 1).create()
        val page = pdfDocument.startPage(pageInfo)
        val canvas: Canvas = page.canvas

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

        canvas.drawText("2. RECENT SALES REGISTER (${activeSales.size} Bills)", 32f, y, headerPaint)
        y += 18f
        canvas.drawText("Date", 32f, y, headerPaint)
        canvas.drawText("Customer", 110f, y, headerPaint)
        canvas.drawText("Product", 230f, y, headerPaint)
        canvas.drawText("Qty(kg)", 360f, y, headerPaint)
        canvas.drawText("Sale", 425f, y, headerPaint)
        canvas.drawText("Margin", 500f, y, headerPaint)
        y += 8f
        canvas.drawLine(32f, y, 563f, y, linePaint)
        y += 14f

        val shortDate = SimpleDateFormat("dd/MM/yy", Locale.getDefault())
        activeSales.sortedByDescending { it.date }.take(28).forEach { sale ->
            if (y > 790f) return@forEach
            val netKg = sale.quantityKg.subtract(sale.returnedKg).max(BigDecimal.ZERO)
            val rev = sale.pricePerKgAtSale.multiply(netKg)
            val margin = rev.subtract(sale.costPerKgAtSale.multiply(netKg))
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

        pdfDocument.finishPage(page)
        FileOutputStream(file).use { out ->
            pdfDocument.writeTo(out)
        }
        pdfDocument.close()

        shareFile(context, file, "application/pdf", "Share GST PDF Report")
    }

    /**
     * Creates a full JSON backup of shop data and opens the share chooser (Google Drive, WhatsApp, etc.).
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
                        put("phone", c.phone)
                        put("village", c.village)
                        put("totalUdhari", c.totalUdhari.toPlainString())
                        put("creditLimit", c.creditLimit.toPlainString())
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
                        put("stockKg", p.currentStockKg.toPlainString())
                        put("sellingPricePerKg", p.sellingPricePerKg.toPlainString())
                        put("costPerKg", p.averagePurchaseCostPerKg.toPlainString())
                    }
                )
            }
            put("products", prodArr)
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
