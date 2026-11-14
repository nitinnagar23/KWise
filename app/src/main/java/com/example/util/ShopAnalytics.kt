package com.example.util

import com.example.model.Customer
import com.example.model.PaymentEntry
import com.example.model.Product
import com.example.model.SaleEntry
import com.example.model.SupplierPayment
import java.math.BigDecimal
import java.math.RoundingMode
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class ReorderSuggestion(
    val product: Product,
    val soldLast30DaysKg: BigDecimal,
    val avgDailySalesKg: BigDecimal,
    val daysOfStockLeft: Int,
    val suggestedOrderKg: BigDecimal,
    val suggestedBags: Int,
    val isUrgent: Boolean
)

data class CustomerInsight(
    val customer: Customer,
    val totalPurchased: BigDecimal,
    val totalPaid: BigDecimal,
    val currentUdhari: BigDecimal,
    val creditLimit: BigDecimal,
    val isOverCreditLimit: Boolean,
    val isOverdue: Boolean,
    val overdueDays: Int,
    val trustScore: Int, // 0..100
    val trustBadge: String, // "Excellent", "Good", "Watch", "Defaulter"
    val orderCount: Int
)

data class VillageUdhariSummary(
    val villageName: String,
    val customerCount: Int,
    val totalUdhari: BigDecimal,
    val overdueCount: Int
)

data class ProductProfitSummary(
    val productId: String,
    val feedName: String,
    val totalSoldKg: BigDecimal,
    val totalRevenue: BigDecimal,
    val totalCost: BigDecimal,
    val grossProfit: BigDecimal,
    val marginPercent: BigDecimal
)

data class DailyProfitSummary(
    val dayStartMillis: Long,
    val dateLabel: String,
    val totalRevenue: BigDecimal,
    val totalCost: BigDecimal,
    val grossProfit: BigDecimal,
    val saleCount: Int
)

object ShopAnalytics {

    /**
     * Dynamically computes the start of today (00:00:00.000) for the given timestamp.
     * Calling this with System.currentTimeMillis() ensures midnight rollovers are respected.
     */
    fun getStartOfTodayMillis(nowMillis: Long = System.currentTimeMillis()): Long {
        return Calendar.getInstance().apply {
            timeInMillis = nowMillis
            set(Calendar.HOUR_OF_DAY, 0)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    /**
     * Computes low-stock and velocity-based reorder suggestions for all products.
     */
    fun computeReorderSuggestions(
        products: List<Product>,
        sales: List<SaleEntry>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<ReorderSuggestion> {
        val thirtyDaysAgo = nowMillis - 30L * 24L * 60L * 60L * 1000L
        val activeRecentSales = sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" && it.date >= thirtyDaysAgo }

        val soldByProduct = mutableMapOf<String, BigDecimal>()
        for (sale in activeRecentSales) {
            val current = soldByProduct[sale.productId] ?: BigDecimal.ZERO
            soldByProduct[sale.productId] = current.add(sale.quantityKg)
        }

        return products.filter { !it.isDeleted }.mapNotNull { product ->
            val sold30d = soldByProduct[product.id] ?: BigDecimal.ZERO
            val avgDaily = if (sold30d > BigDecimal.ZERO) {
                sold30d.divide(BigDecimal("30"), 2, RoundingMode.HALF_UP)
            } else {
                BigDecimal.ZERO
            }

            val stock = product.currentStockKg.max(BigDecimal.ZERO)
            val daysLeft = when {
                stock <= BigDecimal.ZERO -> 0
                avgDaily > BigDecimal("0.5") -> stock.divide(avgDaily, 0, RoundingMode.FLOOR).toInt()
                else -> 999
            }

            val lowStockThreshold = if (product.isLooseOnly) BigDecimal("100") else product.kgPerBag.multiply(BigDecimal("5"))
            val needsReorder = stock <= lowStockThreshold || daysLeft <= 7

            if (!needsReorder) return@mapNotNull null

            // Target 15 days of stock or at least 10 bags (or 250kg for loose)
            val velocityTargetKg = avgDaily.multiply(BigDecimal("15"))
            val minTargetKg = if (product.isLooseOnly) BigDecimal("250") else product.kgPerBag.multiply(BigDecimal("10"))
            val targetStockKg = velocityTargetKg.max(minTargetKg)
            val deficitKg = targetStockKg.subtract(stock).max(
                if (product.isLooseOnly) BigDecimal("100") else product.kgPerBag.multiply(BigDecimal("5"))
            ).setScale(0, RoundingMode.CEILING)

            val suggestedBags = if (!product.isLooseOnly && product.kgPerBag > BigDecimal.ONE) {
                deficitKg.divide(product.kgPerBag, 0, RoundingMode.CEILING).toInt().coerceAtLeast(1)
            } else 0

            val finalOrderKg = if (suggestedBags > 0) {
                product.kgPerBag.multiply(BigDecimal(suggestedBags))
            } else {
                deficitKg
            }

            ReorderSuggestion(
                product = product,
                soldLast30DaysKg = sold30d,
                avgDailySalesKg = avgDaily,
                daysOfStockLeft = daysLeft,
                suggestedOrderKg = finalOrderKg,
                suggestedBags = suggestedBags,
                isUrgent = stock <= BigDecimal.ZERO || daysLeft <= 3 || stock < lowStockThreshold.divide(BigDecimal("2"), 0, RoundingMode.HALF_UP)
            )
        }.sortedWith(compareByDescending<ReorderSuggestion> { it.isUrgent }.thenBy { it.daysOfStockLeft })
    }

    /**
     * Computes customer insights including trust score (0-100), repayment history, credit limit check, and overdue status.
     */
    fun computeCustomerInsights(
        customers: List<Customer>,
        sales: List<SaleEntry>,
        payments: List<PaymentEntry>,
        nowMillis: Long = System.currentTimeMillis()
    ): List<CustomerInsight> {
        val activeSales = sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" }
        val activePayments = payments.filter { !it.isDeleted }

        val salesByCust = activeSales.groupBy { it.customerId }
        val paymentsByCust = activePayments.groupBy { it.customerId }
        val startOfToday = getStartOfTodayMillis(nowMillis)

        return customers.filter { !it.isDeleted }.map { customer ->
            val custSales = salesByCust[customer.id].orEmpty()
            val custPayments = paymentsByCust[customer.id].orEmpty()

            var totalPurchased = BigDecimal.ZERO
            var paidAtSale = BigDecimal.ZERO
            for (s in custSales) {
                totalPurchased = totalPurchased.add(s.totalSalePrice)
                paidAtSale = paidAtSale.add(s.paidAmount)
            }

            var standalonePaid = BigDecimal.ZERO
            for (p in custPayments) {
                standalonePaid = standalonePaid.add(p.amount)
            }

            val totalPaid = paidAtSale.add(standalonePaid)
            val currentUdhari = customer.totalUdhari.max(BigDecimal.ZERO)
            val limit = if (customer.creditLimit > BigDecimal.ZERO) customer.creditLimit else BigDecimal("10000")
            val isOverLimit = currentUdhari > limit

            val promise = customer.promiseDate
            val isOverdue = currentUdhari > BigDecimal.ZERO && promise != null && promise < startOfToday
            val overdueDays = if (isOverdue && promise != null) {
                ((startOfToday - promise) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
            } else 0

            // Calculate Trust Score (0..100)
            var score = 75 // Base score for new customer
            if (totalPurchased > BigDecimal.ZERO) {
                val repaymentRatio = totalPaid.divide(totalPurchased, 4, RoundingMode.HALF_UP)
                    .multiply(BigDecimal("100")).toInt().coerceIn(0, 100)
                score = 40 + (repaymentRatio * 50) / 100 // 40..90 based on repayment ratio
                if (custSales.size >= 5 && repaymentRatio >= 85) {
                    score += 10
                }
            }
            if (isOverLimit) {
                score -= 15
            }
            if (isOverdue) {
                score -= (overdueDays * 2).coerceIn(10, 40)
            }
            if (currentUdhari.compareTo(BigDecimal.ZERO) <= 0 && totalPurchased > BigDecimal.ZERO) {
                score = maxOf(score, 90)
            }
            val finalScore = score.coerceIn(5, 100)

            val badge = when {
                finalScore >= 85 -> "Excellent"
                finalScore >= 65 -> "Good"
                finalScore >= 45 -> "Watch"
                else -> "Defaulter"
            }

            CustomerInsight(
                customer = customer,
                totalPurchased = totalPurchased,
                totalPaid = totalPaid,
                currentUdhari = currentUdhari,
                creditLimit = limit,
                isOverCreditLimit = isOverLimit,
                isOverdue = isOverdue,
                overdueDays = overdueDays,
                trustScore = finalScore,
                trustBadge = badge,
                orderCount = custSales.size
            )
        }
    }

    /**
     * Computes village-wise outstanding udhari summary.
     */
    fun computeVillageSummaries(insights: List<CustomerInsight>): List<VillageUdhariSummary> {
        return insights
            .filter { it.currentUdhari > BigDecimal.ZERO }
            .groupBy { it.customer.village.trim().ifBlank { "Unspecified / Local" } }
            .map { (village, list) ->
                VillageUdhariSummary(
                    villageName = village,
                    customerCount = list.size,
                    totalUdhari = list.fold(BigDecimal.ZERO) { acc, item -> acc.add(item.currentUdhari) },
                    overdueCount = list.count { it.isOverdue }
                )
            }
            .sortedByDescending { it.totalUdhari }
    }

    /**
     * Computes per-product profit and margin for a given list of sales.
     */
    fun computeProductProfits(
        sales: List<SaleEntry>,
        productsMap: Map<String, Product>
    ): List<ProductProfitSummary> {
        val activeSales = sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" && it.quantityKg > BigDecimal.ZERO }
        return activeSales.groupBy { it.productId }.map { (productId, prodSales) ->
            val feedName = productsMap[productId]?.feedName ?: "Cattle Feed"
            var totalKg = BigDecimal.ZERO
            var totalRev = BigDecimal.ZERO
            var totalCost = BigDecimal.ZERO

            for (s in prodSales) {
                totalKg = totalKg.add(s.quantityKg)
                totalRev = totalRev.add(s.totalSalePrice)
                val itemCost = s.costPerKgAtSale.multiply(s.quantityKg).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
                totalCost = totalCost.add(itemCost)
            }

            val profit = totalRev.subtract(totalCost).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            val marginPct = if (totalRev > BigDecimal.ZERO) {
                profit.multiply(BigDecimal("100")).divide(totalRev, 1, RoundingMode.HALF_UP)
            } else BigDecimal.ZERO

            ProductProfitSummary(
                productId = productId,
                feedName = feedName,
                totalSoldKg = totalKg,
                totalRevenue = totalRev,
                totalCost = totalCost,
                grossProfit = profit,
                marginPercent = marginPct
            )
        }.sortedByDescending { it.grossProfit }
    }

    /**
     * Computes day-by-day profit breakdown for the given sales.
     */
    fun computeDailyProfits(sales: List<SaleEntry>): List<DailyProfitSummary> {
        val activeSales = sales.filter { !it.isDeleted && it.billStatus != "CANCELLED" && it.quantityKg > BigDecimal.ZERO }
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())

        return activeSales.groupBy { sale ->
            getStartOfTodayMillis(sale.date)
        }.map { (dayStart, daySales) ->
            var rev = BigDecimal.ZERO
            var cost = BigDecimal.ZERO
            for (s in daySales) {
                rev = rev.add(s.totalSalePrice)
                cost = cost.add(s.costPerKgAtSale.multiply(s.quantityKg))
            }
            val profit = rev.subtract(cost).setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING)
            DailyProfitSummary(
                dayStartMillis = dayStart,
                dateLabel = fmt.format(Date(dayStart)),
                totalRevenue = rev.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING),
                totalCost = cost.setScale(CurrencyUtils.MONEY_SCALE, CurrencyUtils.ROUNDING),
                grossProfit = profit,
                saleCount = com.example.model.groupSalesIntoBills(daySales).size
            )
        }.sortedByDescending { it.dayStartMillis }
    }

    /**
     * Builds a smart Hindi + English WhatsApp Udhari reminder message for a customer.
     */
    fun buildSmartHindiUdhariReminder(
        customer: Customer,
        shopName: String,
        shopUpiId: String = "",
        nowMillis: Long = System.currentTimeMillis()
    ): String {
        val fmt = SimpleDateFormat("dd MMM yyyy", Locale.getDefault())
        val amountStr = CurrencyUtils.formatInr(customer.totalUdhari)
        val storeTitle = shopName.ifBlank { "KhataWise Store" }
        val startOfToday = getStartOfTodayMillis(nowMillis)
        val promiseDate = customer.promiseDate

        val promiseLineHindi = when {
            promiseDate != null && promiseDate < startOfToday -> {
                val days = ((startOfToday - promiseDate) / (24L * 60L * 60L * 1000L)).toInt().coerceAtLeast(1)
                "📅 आपकी भुगतान तिथि *${fmt.format(Date(promiseDate))}* ($days दिन पहले) बीत चुकी है।"
            }
            promiseDate != null -> {
                "📅 निर्धारित भुगतान तिथि: *${fmt.format(Date(promiseDate))}*"
            }
            else -> "📅 कृपया समय पर बकाया राशि जमा कर दें।"
        }

        val upiLine = if (shopUpiId.isNotBlank()) {
            "\n💳 *UPI से भुगतान करें:* $shopUpiId"
        } else ""

        return """
            🙏 *नमस्ते ${customer.name} जी,*
            *$storeTitle* की ओर से विनम्र स्मरण:
            
            💰 *कुल बकाया उधारी (Udhari Due):* $amountStr
            $promiseLineHindi$upiLine
            
            कृपया जल्द से जल्द भुगतान कर सहयोग करें। धन्यवाद! 🙏
        """.trimIndent()
    }
}
