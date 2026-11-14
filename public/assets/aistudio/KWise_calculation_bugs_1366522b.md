# KWise – Calculation Bug Report

**Scope:** money, stock, udhari, supplier-ledger and report math in `KWise-main` (Android / Kotlin / Room).
**Method:** static code reading. The app was not built or run. Two numeric examples (#9 and #1) were re-checked with a small script.
**Coverage:** about 76K lines. Calculation paths were prioritised, so this list is not exhaustive.

Paths below are relative to `app/src/main/java/com/example/`.

| # | Severity | Area | Short description |
|---|----------|------|-------------------|
| 1 | High | Reports | Returns subtracted twice |
| 2 | High | Reports | Invented 22% expense and double-counted freight |
| 3 | High | Reports | Fake cash/UPI split |
| 4 | High | Purchases | Deleting an invoice does not reverse stock or cost |
| 5 | High | Bills | Edit/Return allowed on cancelled bills |
| 6 | High | Udhari | Overpayments silently lost |
| 7 | High | Purchases | Default purchase rate is the selling price |
| 8 | High | Sales | No stock check, no transaction |
| 9 | Medium | Returns | Refund uses rounded average rate |
| 10 | Medium | Bills | Bill numbers inconsistent |
| 11 | Medium | Suppliers | Name-case collision drops supplier data |
| 12 | Medium | Suppliers | Statement balance ≠ summary balance |
| 13 | Medium | Suppliers | `.toLong()` truncates paise |
| 14 | Medium | Analytics | `== BigDecimal.ZERO` scale bug |
| 15 | Low | Validation | Negative/zero amounts accepted |
| 16 | Low | Purchases | Average cost rounded to 2 decimals |
| 17 | Low | Purchases | Handling-share rounding drift |
| 18 | Low | Budget split | Inconsistent minimum rate |
| 19 | Low | Budget split | Leftover absorption can exceed budget |
| 20 | Low | Display | "kg kg" duplicated suffix |
| 21 | Low | Stability | Possible divide-by-zero on `kgPerBag` |

---

## High severity

### 1. Reports subtract returns twice
**Where:** `ui/reports/ReportsScreen.kt` (~L270–295)

`returnSaleItems()` overwrites `quantityKg` with the remaining kg and `totalSalePrice` with the new total, and also stores the cumulative `returnedKg`. The report then does `netKg = quantityKg - returnedKg`, so returned kg is removed a second time.

*Example:* sell 100 kg, return 20 kg. Stored `quantityKg` is 80, but the report uses 60 kg.

*Effect:* revenue, COGS, gross profit and total kg are understated for every sale with a return. `ShopAnalytics.computeProductProfits` uses `quantityKg` directly (correct), so the two screens disagree.

**Fix:** treat `quantityKg` and `totalSalePrice` as already net. Use them directly and drop the `returnedKg` subtraction. Alternatively, store the original quantity in a separate field and derive net from it consistently.

### 2. Invented 22% expense and double-counted freight
**Where:** `ui/reports/ReportsScreen.kt` (~L300–315)

`otherExpenses = totalHandlingExpenses × 0.22` is not backed by any data. Separately, ladhan, utaar and gaadi kiraya are already apportioned into each item's landed cost (`recordPurchaseInvoice`), so they are already inside COGS. Deducting them again as expenses counts freight twice.

**Fix:** remove the 0.22 multiplier. Use real expenses from the cashbook. Count handling charges once: either in landed cost or as an expense, not both.

### 3. Fake cash/UPI split
**Where:** `ui/reports/ReportsScreen.kt` (~L750–775)

Cash is `totalPaidAtSale × 0.66`, UPI is the remainder, and the fallback percentages are hard-coded (47 / 24). Sales and payments do not store a payment mode.

**Fix:** add a `paymentMode` field to sales and payments (with a Room migration) and aggregate real values. Until then, hide the split or label it "not tracked".

### 4. Deleting a purchase invoice does not reverse stock or cost
**Where:** `data/repository/CattleFeedRepository.kt` – `softDeletePurchaseInvoice()`

It only flags the invoice as deleted. The stock added, the moving-average cost update and the `PurchaseEntry` rows created by `recordPurchaseInvoice` all remain, so inventory and purchase reports stay inflated.

**Fix:** inside one transaction, parse the invoice's `itemsJson`, subtract each item's `totalKg` from stock, soft-delete the matching `PurchaseEntry` rows and recompute average cost. Block the deletion if that stock has already been sold.

### 5. Edit and Return are allowed on cancelled bills
**Where:** `CattleFeedRepository.kt` – `editSaleBill()`, `returnSaleItems()`, `addLineItemToExistingBill()`; `data/dao/SaleDao.kt` – `getSaleById()`

`cancelSaleBill` restores stock. Nothing stops a later edit or return on the same sale, which then adjusts stock again. Editing also sets `billStatus = "EDITED"`, which revives a cancelled bill. `getSaleById` does not filter `isDeleted`.

**Fix:** at the top of each function, return or throw if `billStatus == "CANCELLED"` or `isDeleted`.

### 6. Overpayments are silently lost
**Where:** `CattleFeedRepository.kt` – `recordPayment()`, `recalculateCustomerUdhari()`, `editSaleBill()`, `recordMultiItemSale()`

- `recalculateCustomerUdhari` clamps the net balance with `.max(0)`, so a payment larger than the outstanding udhari vanishes. There is no advance or credit balance.
- `editSaleBill` caps `paid` at the new total.
- `recordMultiItemSale` caps each sale's paid amount, but `BillData.paidAmount` shows the uncapped figure, so the printed bill and the stored sales disagree.

**Fix:** either reject payments above the outstanding amount, or keep a negative balance as customer advance and clamp only for display. Make `BillData.paidAmount` equal the capped sum.

### 7. Default purchase rate is the selling price
**Where:** `CattleFeedRepository.kt` – `recordPurchaseInvoice()` (~L945–960)

When no custom rate is supplied, `ratePerBag` falls back to `product.bagSellingPrice` and `ratePerKg` to `sellingPricePerKg`. Purchase cost then equals the selling price, which inflates average cost and erases margin. Supplying only a custom per-kg rate still prices whole bags at the selling price.

**Fix:** require an explicit rate, or fall back to `averagePurchaseCostPerKg`. Reject items with no rate.

### 8. No stock check and no transaction on sales
**Where:** `CattleFeedRepository.kt` – `recordMultiItemSale()`

- Stock is deducted with no check that enough exists, so it can go negative. `recordPurchase` and `recordPurchaseInvoice` then use `max(0)` for valuation but the real (negative) stock for the new total.
- The doc comment says "single transaction", but there is no `db.withTransaction`. A failure part-way leaves stock deducted with no sale saved.
- A product listed on two lines is checked per line, not in total.

**Fix:** wrap the function in `db.withTransaction { … }`. Sum quantity per product and validate it against stock (or gate on an explicit "allow negative stock" setting).

---

## Medium severity

### 9. Return refund uses a rounded average rate
**Where:** `CattleFeedRepository.kt` – `returnSaleItems()` (`newTotal = remainingKg × pricePerKgAtSale`)

`pricePerKgAtSale` is the line total divided by kg, rounded to 2 decimals, and it mixes bag and loose rates.

*Example (verified):* 2 bags at ₹1333 (50 kg each) plus 3 kg loose at ₹27.50 gives ₹2748.50 over 103 kg, so ₹26.68/kg.

- Returning the 3 loose kg leaves ₹2668.00. The correct figure is ₹2666.00.
- Returning one bag leaves ₹1414.04. The correct figure is ₹1415.50.

**Fix:** at minimum, compute proportionally from the stored total: `totalSalePrice × remainingKg / quantityKg`, rounding once at the end. A fully correct result needs per-component data (bags, loose kg and their rates) on `SaleEntry`, or a user-entered refund amount.

### 10. Bill numbers are inconsistent
**Where:** `CattleFeedRepository.kt` – `recordMultiItemSale()` (`billNumber = currentTimeMillis % 1000000`); `model/SaleEntry.kt` – `groupSalesIntoBills()`

The number shown at checkout never matches the 1001+index number in the Bills list. The list numbers also change if a sale is soft-deleted or a filtered list is passed in.

**Fix:** store a sequential `billNumber` on the sale at creation (counter in settings, inside the same transaction) and show that everywhere.

### 11. Supplier name-case collision drops data
**Where:** `ui/purchases/PurchasesScreen.kt` (~L120–150) versus `CattleFeedRepository.supplierSummaries`

The repository groups suppliers by exact trimmed name. The screen re-keys by lowercase name, so "Sharma Traders" and "sharma traders" overwrite each other and one supplier's invoices and payments disappear from totals. The DAO queries (`wholesalerName = :name`) are case-sensitive, so ledgers miss rows too.

**Fix:** use one normalisation (trim and lowercase, or a supplier ID) in the repository, UI and DAO (`COLLATE NOCASE`).

### 12. Supplier statement balance differs from the summary balance
**Where:** `ui/purchases/SupplierPayStatementScreens.kt` (~L1303–1350) and `PurchasesScreen.kt`

- The statement clamps the running balance at 0 after each payment, so an advance paid before an invoice is lost. The summary clamps only at the end.
- The opening balance is added only in the UI. `CattleFeedRepository.supplierSummaries` ignores it, and the UI folds it into "Total purchases".

**Fix:** compute supplier balance in one repository function that includes the opening balance. Allow a negative running balance (advance) and clamp only when displaying.

### 13. `.toLong()` truncates paise
**Where:** `ui/purchases/SupplierPayStatementScreens.kt` (~L1911–1966, L2125–2143)

`BigDecimal.toLong()` truncates. ₹1,999.99 shows as 1,999, and each row is truncated separately, so the rows do not add up to the totals (screen and PDF).

**Fix:** use `setScale(2, HALF_UP)` with a number format that shows 2 decimals, or `CurrencyUtils.formatInr(x, includeDecimals = true)`.

### 14. `== BigDecimal.ZERO` ignores value equality
**Where:** `util/ShopAnalytics.kt:207`

`currentUdhari == BigDecimal.ZERO` uses `equals`, which also compares scale. `recalculateCustomerUdhari` stores `0.00`, so the check never matches and the "fully paid, minimum score 90" rule never fires.

**Fix:** use `currentUdhari.signum() == 0` or `compareTo(BigDecimal.ZERO) == 0`.

---

## Low severity

### 15. Negative or zero amounts are accepted
**Where:** `recordPayment`, `recordMultiItemSale`, `editSaleBill`, `recordSupplierPayment`; `ui/billing/BillingScreen.kt` (`executeSaleBill`)

No check that amounts are positive. A negative payment raises udhari. A negative loose kg alongside bags reduces the quantity deducted from stock while billing the bags in full.

**Fix:** validate in the repository (`require(bags >= 0)`, `looseKg >= 0`, `paid >= 0`, `amount > 0`) rather than relying on UI filters.

### 16. Average cost is rounded to 2 decimals
**Where:** `recordPurchase()`, `recordPurchaseInvoice()`

The new average is stored at `MONEY_SCALE`, and `recordPurchase` also rounds the unit price to 4 decimals before multiplying back, so valuation drifts slightly from the real total cost.

**Fix:** store average cost at 4 decimals and use `totalCost` directly for the new valuation.

### 17. Handling-share rounding drift
**Where:** `recordPurchaseInvoice()`

Each item's share of ladhan, utaar and gaadi kiraya is rounded separately, so the sum of `PurchaseEntry` totals can differ from the invoice total by a few paise.

**Fix:** give the remainder to the last item.

### 18. Inconsistent minimum rate in Budget Split
**Where:** `util/BudgetSplit.kt`

WEIGHT mode clamps the rate to at least ₹1, while the other modes clamp to ₹0.01. A product priced under ₹1/kg is billed differently by mode.

**Fix:** use one minimum, or reject a rate ≤ 0 with a visible message.

### 19. Leftover absorption can exceed the budget
**Where:** `util/BudgetSplit.kt` – `applyLeftoverToRow()`

The new kg is rounded half-up to 3 decimals and `actualAmount` is set to the target amount instead of `kg × rate`. The displayed amount can differ from kg × rate by a fraction of a rupee and slightly exceed the budget.

**Fix:** round kg down, then recompute `actualAmount = kg × rate`.

### 20. Duplicated "kg" suffix
**Where:** about 47 call sites, for example `ui/bills/BillsScreen.kt:1431`, `ui/purchases/InvoicesTabView.kt:296`, audit-log strings in `CattleFeedRepository`

`CurrencyUtils.formatWeight()` already returns "50 kg", and callers append " kg" again, giving "50 kg kg".

**Fix:** remove the extra suffix at the call sites, or add a number-only helper.

### 21. Possible divide-by-zero on `kgPerBag`
**Where:** many sites, for example `ShopAnalytics.kt`, `BillsScreen.kt`, `LedgerScreen.kt`, `FirestoreSyncEngine.kt`

`divide(kgPerBag, …)` throws `ArithmeticException` if a product has bag weight 0. I did not verify whether the product form blocks zero.

**Fix:** enforce `kgPerBag > 0` in the form and the `Product` model, and use a safe helper for bag counts.

---

## Suggested fix order

1. #1, #2, #3: reports show wrong profit today.
2. #5, #6, #8: stock and udhari integrity.
3. #4, #7: purchase and inventory valuation.
4. #9–#14: consistency fixes.
5. #15–#21: hardening and cleanup.
