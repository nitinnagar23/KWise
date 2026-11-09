package com.example.util

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * Split modes supported by the Calculator's Budget Split feature.
 */
enum class BudgetSplitMode {
    EQUAL,
    RATIO,
    AMOUNT,
    WEIGHT
}

/**
 * Input row for [splitBudget].
 *
 * @param productId Unique product ID.
 * @param productName Display name of the loose commodity.
 * @param ratePerKg Effective loose selling rate per kg (must be > 0).
 * @param availableStockKg Current available stock in kg (used for stock warning).
 * @param ratioWeight Weight for [BudgetSplitMode.RATIO] (e.g. 1, 3, 4). Defaults to 1.
 * @param requestedAmount In [BudgetSplitMode.AMOUNT] or when [isLocked] is true, the user-entered ₹ amount.
 * @param requestedKg In [BudgetSplitMode.WEIGHT] or when [isLocked] is true, the user-entered weight in kg.
 * @param isLocked When true, this row's amount/kg is frozen and unlocked rows rebalance the remaining budget.
 */
data class BudgetSplitItemInput(
    val productId: String,
    val productName: String,
    val ratePerKg: BigDecimal,
    val availableStockKg: BigDecimal = BigDecimal("999999"),
    val ratioWeight: BigDecimal = BigDecimal.ONE,
    val requestedAmount: BigDecimal? = null,
    val requestedKg: BigDecimal? = null,
    val isLocked: Boolean = false
)

/**
 * Calculated output row from [splitBudget].
 */
data class BudgetSplitRowResult(
    val productId: String,
    val productName: String,
    val ratePerKg: BigDecimal,
    val targetAmount: BigDecimal,
    val allocatedKg: BigDecimal,
    val actualAmount: BigDecimal,
    val availableStockKg: BigDecimal,
    val isLocked: Boolean,
    val exceedsStock: Boolean
)

/**
 * Overall result of [splitBudget].
 */
data class BudgetSplitResult(
    val budget: BigDecimal,
    val stepKg: BigDecimal,
    val mode: BudgetSplitMode,
    val rows: List<BudgetSplitRowResult>,
    val totalActualAmount: BigDecimal,
    val totalWeightKg: BigDecimal,
    val leftoverRupees: BigDecimal,
    val isOverBudget: Boolean,
    val hasStockWarning: Boolean
)

/**
 * Rounds [weightKg] DOWN (floor for non-negative numbers) to the nearest multiple of [stepKg].
 * Default step is 0.050 kg (50 g).
 */
fun roundDownToStepKg(
    weightKg: BigDecimal,
    stepKg: BigDecimal = BigDecimal("0.050")
): BigDecimal {
    if (weightKg <= BigDecimal.ZERO) return BigDecimal.ZERO.setScale(3, RoundingMode.DOWN)
    if (stepKg <= BigDecimal.ZERO) return weightKg.setScale(3, RoundingMode.DOWN)
    val steps = weightKg.divideToIntegralValue(stepKg)
    return steps.multiply(stepKg).setScale(3, RoundingMode.DOWN)
}

/**
 * Pure, unit-tested function that splits [budget] across [items] using [mode] and rounds
 * each item's weight DOWN to [stepKg] (default 50 g = 0.050 kg).
 *
 * Rules:
 * - Locked rows (`isLocked == true`) keep their frozen amount/kg, and unlocked rows rebalance
 *   the remaining budget (`budget - lockedTotal`).
 * - EQUAL: splits remaining budget equally across unlocked items (whole rupees, with any
 *   leftover rupee from integer division going to the last unlocked item).
 * - RATIO: splits remaining budget proportionally to `ratioWeight` across unlocked items,
 *   with any rounding remainder going to the last unlocked item.
 * - AMOUNT: uses `requestedAmount` for each unlocked row except the last unlocked row,
 *   which auto-fills so the sum of target amounts equals [budget].
 * - WEIGHT: uses `requestedKg` directly for each row (`actualAmount = allocatedKg * ratePerKg`)
 *   and flags `isOverBudget = totalActualAmount > budget`.
 * - For EQUAL, RATIO, and AMOUNT: `rawKg = targetAmount / ratePerKg`, rounded DOWN to [stepKg],
 *   and `actualAmount = allocatedKg * ratePerKg` (rounded to 2 decimal places).
 *   `leftoverRupees = budget - totalActualAmount`.
 */
fun splitBudget(
    budget: BigDecimal,
    items: List<BudgetSplitItemInput>,
    mode: BudgetSplitMode,
    stepKg: BigDecimal = BigDecimal("0.050")
): BudgetSplitResult {
    val cleanBudget = budget.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
    val effectiveStep = if (stepKg > BigDecimal.ZERO) stepKg else BigDecimal("0.050")

    if (items.isEmpty()) {
        return BudgetSplitResult(
            budget = cleanBudget,
            stepKg = effectiveStep,
            mode = mode,
            rows = emptyList(),
            totalActualAmount = BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP),
            totalWeightKg = BigDecimal.ZERO.setScale(3, RoundingMode.DOWN),
            leftoverRupees = cleanBudget,
            isOverBudget = false,
            hasStockWarning = false
        )
    }

    // 1. Handle WEIGHT mode first (user specifies kg per item)
    if (mode == BudgetSplitMode.WEIGHT) {
        val rows = items.map { item ->
            val rate = item.ratePerKg.max(BigDecimal.ONE)
            val rawKg = (item.requestedKg ?: BigDecimal.ZERO).max(BigDecimal.ZERO)
            val roundedKg = roundDownToStepKg(rawKg, effectiveStep)
            val actualAmt = roundedKg.multiply(rate).setScale(2, RoundingMode.HALF_UP)
            val exceeds = roundedKg > item.availableStockKg
            BudgetSplitRowResult(
                productId = item.productId,
                productName = item.productName,
                ratePerKg = rate,
                targetAmount = actualAmt,
                allocatedKg = roundedKg,
                actualAmount = actualAmt,
                availableStockKg = item.availableStockKg,
                isLocked = item.isLocked,
                exceedsStock = exceeds
            )
        }
        val totalActual = rows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.actualAmount) }
            .setScale(2, RoundingMode.HALF_UP)
        val totalKg = rows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.allocatedKg) }
            .setScale(3, RoundingMode.DOWN)
        val leftover = cleanBudget.subtract(totalActual).setScale(2, RoundingMode.HALF_UP)
        return BudgetSplitResult(
            budget = cleanBudget,
            stepKg = effectiveStep,
            mode = mode,
            rows = rows,
            totalActualAmount = totalActual,
            totalWeightKg = totalKg,
            leftoverRupees = leftover,
            isOverBudget = totalActual > cleanBudget,
            hasStockWarning = rows.any { it.exceedsStock }
        )
    }

    // 2. For EQUAL, RATIO, AMOUNT: compute frozen amounts for locked rows first
    val targetAmounts = MutableList(items.size) { BigDecimal.ZERO }
    var lockedSum = BigDecimal.ZERO
    val unlockedIndices = mutableListOf<Int>()

    items.forEachIndexed { idx, item ->
        val rate = item.ratePerKg.max(BigDecimal("0.01"))
        if (item.isLocked) {
            val frozenAmt = when {
                item.requestedAmount != null -> item.requestedAmount.max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)
                item.requestedKg != null -> item.requestedKg.max(BigDecimal.ZERO).multiply(rate).setScale(2, RoundingMode.HALF_UP)
                else -> BigDecimal.ZERO.setScale(2, RoundingMode.HALF_UP)
            }
            targetAmounts[idx] = frozenAmt
            lockedSum = lockedSum.add(frozenAmt)
        } else {
            unlockedIndices.add(idx)
        }
    }

    val remainingBudget = cleanBudget.subtract(lockedSum).max(BigDecimal.ZERO).setScale(2, RoundingMode.HALF_UP)

    if (unlockedIndices.isNotEmpty()) {
        when (mode) {
            BudgetSplitMode.EQUAL -> {
                val count = BigDecimal(unlockedIndices.size)
                // Split in whole rupees first so leftover rupee goes to the last unlocked item
                val perItemWholeRupees = remainingBudget.divideToIntegralValue(count)
                    .setScale(2, RoundingMode.DOWN)
                var allocatedSoFar = BigDecimal.ZERO
                unlockedIndices.forEachIndexed { pos, itemIdx ->
                    if (pos == unlockedIndices.lastIndex) {
                        targetAmounts[itemIdx] = remainingBudget.subtract(allocatedSoFar).max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP)
                    } else {
                        targetAmounts[itemIdx] = perItemWholeRupees
                        allocatedSoFar = allocatedSoFar.add(perItemWholeRupees)
                    }
                }
            }

            BudgetSplitMode.RATIO -> {
                val totalRatio = unlockedIndices.fold(BigDecimal.ZERO) { acc, idx ->
                    acc.add(items[idx].ratioWeight.max(BigDecimal.ZERO))
                }
                if (totalRatio <= BigDecimal.ZERO) {
                    val count = BigDecimal(unlockedIndices.size)
                    val baseShare = remainingBudget.divideToIntegralValue(count).setScale(2, RoundingMode.DOWN)
                    var allocated = BigDecimal.ZERO
                    unlockedIndices.forEachIndexed { pos, itemIdx ->
                        if (pos == unlockedIndices.lastIndex) {
                            targetAmounts[itemIdx] = remainingBudget.subtract(allocated).max(BigDecimal.ZERO)
                        } else {
                            targetAmounts[itemIdx] = baseShare
                            allocated = allocated.add(baseShare)
                        }
                    }
                } else {
                    var allocated = BigDecimal.ZERO
                    unlockedIndices.forEachIndexed { pos, itemIdx ->
                        if (pos == unlockedIndices.lastIndex) {
                            targetAmounts[itemIdx] = remainingBudget.subtract(allocated).max(BigDecimal.ZERO)
                                .setScale(2, RoundingMode.HALF_UP)
                        } else {
                            val ratio = items[itemIdx].ratioWeight.max(BigDecimal.ZERO)
                            val share = remainingBudget.multiply(ratio)
                                .divide(totalRatio, 2, RoundingMode.DOWN)
                            targetAmounts[itemIdx] = share
                            allocated = allocated.add(share)
                        }
                    }
                }
            }

            BudgetSplitMode.AMOUNT -> {
                var typedUnlockedSum = BigDecimal.ZERO
                unlockedIndices.forEachIndexed { pos, itemIdx ->
                    if (pos == unlockedIndices.lastIndex && unlockedIndices.size > 1) {
                        val autoFill = remainingBudget.subtract(typedUnlockedSum).max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP)
                        targetAmounts[itemIdx] = autoFill
                    } else {
                        val req = (items[itemIdx].requestedAmount ?: BigDecimal.ZERO)
                            .max(BigDecimal.ZERO)
                            .setScale(2, RoundingMode.HALF_UP)
                        targetAmounts[itemIdx] = req
                        typedUnlockedSum = typedUnlockedSum.add(req)
                    }
                }
            }

            BudgetSplitMode.WEIGHT -> Unit
        }
    }

    // 3. Convert targetAmounts into step-rounded kg and actual ₹ amounts
    val rows = items.mapIndexed { idx, item ->
        val rate = item.ratePerKg.max(BigDecimal("0.01"))
        val targetAmt = targetAmounts[idx]
        val rawKg = if (item.isLocked && item.requestedKg != null && item.requestedAmount == null) {
            item.requestedKg.max(BigDecimal.ZERO)
        } else {
            targetAmt.divide(rate, 6, RoundingMode.DOWN)
        }
        val roundedKg = roundDownToStepKg(rawKg, effectiveStep)
        val actualAmt = roundedKg.multiply(rate).setScale(2, RoundingMode.HALF_UP)
        val exceeds = roundedKg > item.availableStockKg

        BudgetSplitRowResult(
            productId = item.productId,
            productName = item.productName,
            ratePerKg = rate,
            targetAmount = targetAmt,
            allocatedKg = roundedKg,
            actualAmount = actualAmt,
            availableStockKg = item.availableStockKg,
            isLocked = item.isLocked,
            exceedsStock = exceeds
        )
    }

    val totalActual = rows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.actualAmount) }
        .setScale(2, RoundingMode.HALF_UP)
    val totalKg = rows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.allocatedKg) }
        .setScale(3, RoundingMode.DOWN)
    val leftover = cleanBudget.subtract(totalActual).setScale(2, RoundingMode.HALF_UP)

    return BudgetSplitResult(
        budget = cleanBudget,
        stepKg = effectiveStep,
        mode = mode,
        rows = rows,
        totalActualAmount = totalActual,
        totalWeightKg = totalKg,
        leftoverRupees = leftover,
        isOverBudget = totalActual > cleanBudget,
        hasStockWarning = rows.any { it.exceedsStock }
    )
}

/**
 * Absorbs the leftover rupees into [targetProductId] by computing exact extra kg (rounded to 0.001 kg)
 * so the customer gets the full ₹ budget value on that item.
 */
fun applyLeftoverToRow(
    result: BudgetSplitResult,
    targetProductId: String
): BudgetSplitResult {
    if (result.leftoverRupees <= BigDecimal.ZERO) return result
    val updatedRows = result.rows.map { row ->
        if (row.productId == targetProductId && row.ratePerKg > BigDecimal.ZERO) {
            val newActualAmount = row.actualAmount.add(result.leftoverRupees).setScale(2, RoundingMode.HALF_UP)
            val newKg = newActualAmount.divide(row.ratePerKg, 3, RoundingMode.HALF_UP)
            row.copy(
                targetAmount = newActualAmount,
                allocatedKg = newKg,
                actualAmount = newActualAmount,
                exceedsStock = newKg > row.availableStockKg
            )
        } else {
            row
        }
    }
    val totalActual = updatedRows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.actualAmount) }
        .setScale(2, RoundingMode.HALF_UP)
    val totalKg = updatedRows.fold(BigDecimal.ZERO) { acc, r -> acc.add(r.allocatedKg) }
        .setScale(3, RoundingMode.DOWN)
    val leftover = result.budget.subtract(totalActual).setScale(2, RoundingMode.HALF_UP)
    return result.copy(
        rows = updatedRows,
        totalActualAmount = totalActual,
        totalWeightKg = totalKg,
        leftoverRupees = leftover,
        isOverBudget = totalActual > result.budget,
        hasStockWarning = updatedRows.any { it.exceedsStock }
    )
}
