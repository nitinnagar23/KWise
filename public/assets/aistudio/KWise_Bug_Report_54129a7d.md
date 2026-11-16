# KhataWise (KWise) – Bug Report

**Scope:** Security, Performance, Calculation, Firebase
**Reviewed:** Android app (Kotlin / Compose / Room / Firestore), `firestore.rules`, build + CI config
**Method:** Static code review only. Nothing was compiled or run, so confirm each item on a device or the Firebase emulator before fixing.

Paths are relative to `app/src/main/java/com/example/` unless noted.

---

## Summary

| Category | Critical | High | Medium | Low | Total |
|---|---|---|---|---|---|
| Security | 1 | 3 | 4 | 3 | 11 |
| Performance | 0 | 3 | 3 | 2 | 8 |
| Calculation | 0 | 4 | 4 | 3 | 11 |
| Firebase | 0 | 6 | 5 | 2 | 13 |
| Other (crash / data-loss) | 0 | 1 | 1 | 0 | 2 |

**Fix first**

1. **S-1 / F-1** – Firestore rules let any staff member edit balances, stock, and ledger entries.
2. **F-2** – Local deletions get overwritten by the next sync (`getSaleById` hides deleted rows).
3. **F-3** – Stock and balances sync as absolute values, so concurrent devices lose updates.
4. **P-1** – A full re-read of every collection runs after every sale or payment.
5. **C-1** – GST/PDF reports subtract returned quantity twice.
6. **O-1** – Every CSV/PDF/JSON export crashes (FileProvider path mismatch).
7. **O-2** – Destructive DB fallback can wipe unsynced data.

---

# 1. Security

### S-1 · CRITICAL · Client-only role checks; Firestore rules allow any staff to tamper with money data
- **Where:** `firestore.rules` lines 138–174; `model/Permissions.kt`
- **Problem:** Role enforcement (`requirePermission`, `OVERRIDE_BILL_RATE`, `EDIT_DELETE_BILL`) exists only in the app. The rules let any approved staff member, including the lowest role, do all of this:
  - Create or update any `transactions` doc (amount, type, `customerId`, `isDeleted`).
  - Create or update any `bills` doc.
  - Change a customer's `currentBalance` / `totalUdhari`. Only `creditLimit` and `isDeleted` are guarded.
  - Set `currentStockKg` / `stockBags` on inventory. Only price and name fields are guarded.
- **Impact:** A modified client or a direct Firestore call can zero a customer's udhari, hide sales, or inflate stock. The audit log is local-only, so nothing records it.
- **Fix:**
  - Make `transactions` and `bills` append-only for non-admins: `allow update` only for admin/manager, and restrict which keys may change.
  - Move balance and stock changes behind a Cloud Function or Firestore transaction. Alternatively, derive balances from transactions and drop the stored `totalUdhari`.

### S-2 · HIGH · Permission checks fail open when the actor is null
- **Where:** `data/repository/CattleFeedRepository.kt` lines 503 (`recordMultiItemSale`), 756 (`recordPayment`), 800 (`recordPurchase`)
- **Problem:** Each of these does `if (effectiveRole != null) requirePermission(...)`. If the user lookup returns null (stale `soldByUserId`, wiped local users table), the check is skipped. The `OVERRIDE_BILL_RATE` check in `recordMultiItemSale` is skipped too.
- **Fix:** Deny when the role is null (`requirePermission(role ?: throw SecurityException(...))`).

### S-3 · HIGH · 4-digit PIN with client-only lockout and PIN-only login across all users
- **Where:** `util/SecurityUtils.kt`; `ui/auth/PinUnlockScreenView.kt` lines 124–160
- **Problem:**
  - A 4-digit PIN has 10,000 combinations. The hash sits in an unencrypted Room DB, so anyone with a rooted device or a DB copy can brute-force it offline in seconds.
  - Lockout state is in plain SharedPreferences and can be cleared.
  - `verifyPinNow` tries the entered PIN against every active user and logs in as the first match. Two users with the same PIN are indistinguishable, and failed-attempt counting is per "primary target" only.
  - Failed attempts never decay over time (4 failures stay forever until a success).
- **Fix:**
  - Require the selected user, and verify only that user's hash.
  - Use a Keystore-backed secret (or an Android-Keystore-wrapped key) in the hash.
  - Allow a longer PIN (6+).
  - Add time-based decay.

### S-4 · HIGH · Pairing code is a numeric 8-digit value with client-side-only brute-force protection
- **Where:** `data/repository/PairingRepository.kt` lines 81, 38–60, 254, 359; `firestore.rules` lines 209–216
- **Problem:**
  - Codes are `%08d` of a `SecureRandom` int, so the space is 10^8.
  - Rate limiting lives in SharedPreferences on the attacker's own device.
  - Any authenticated user, including anonymous auth, may `get` any pairing doc. That returns `adminUid`, store name, address, and phone.
  - `recordLookupOutcome(true)` resets the failure counter, so a known-valid code interleaved with guesses defeats the lockout.
- **Mitigation:** New staff land as `PENDING` and need admin approval.
- **Fix:**
  - Use longer alphanumeric codes.
  - Move claim/verify into a Cloud Function with server-side rate limiting.
  - Require `request.auth.token.firebase.sign_in_provider != 'anonymous'` in the rules.
  - Do not expose store phone/address before claim.

### S-5 · MEDIUM · No App Check despite the dependency
- **Where:** `app/build.gradle.kts` (`firebase.appcheck.recaptcha`, `appcheck.debug`); `KhataWiseApp.kt`
- **Problem:** The libraries are on the classpath, but nothing installs a provider, so App Check is not active. The API key in `google-services.json.base64` is committed, so anyone can call Firestore with it.
- **Fix:** Initialize Play Integrity App Check and enforce it in the Firebase console. Restrict the API key to the package name and SHA-1.

### S-6 · MEDIUM · Revoking staff access can silently fail
- **Where:** `ui/staff/StaffManagementScreen.kt` lines 413, 430, 463, 490
- **Problem:** The remote `staff` doc deletion or update is wrapped in `catch (_: Exception) {}`, and the success toast is shown anyway. If the device is offline, a removed staff member keeps access (the local row is deleted but the Firestore doc remains approved). Nothing retries.
- **Fix:** Surface the error, queue the revocation for retry, and show success only after the write is acknowledged.

### S-7 · MEDIUM · CSV formula injection in exports
- **Where:** `util/BackupExportUtil.kt` (customer, village and product names written only quote-escaped)
- **Problem:** A staff-created customer named `=HYPERLINK(...)` or `+cmd|...` runs as a formula when the accountant opens the CSV in Excel.
- **Fix:** Prefix cells starting with `= + - @` (or tab/CR) with `'`.

### S-8 · MEDIUM · Exported financial data left in cache indefinitely
- **Where:** `util/BackupExportUtil.kt` line 28 (`cacheDir/bills`); `CustomerStatementAndRepaymentScreens.kt` (`cacheDir/statements`)
- **Problem:** CSV/PDF/JSON with customer names, phones, and balances are written to the cache dir and never deleted.
- **Fix:** Delete the file after the share intent returns, or purge old files at startup.

### S-9 · LOW · Google sign-in without a nonce
- **Where:** `auth/GoogleAuthManager.kt` line 143
- **Problem:** `GetSignInWithGoogleOption` is built without `setNonce`, so ID-token replay protection is absent.

### S-10 · LOW · Release build keeps almost all app code unobfuscated
- **Where:** `app/proguard-rules.pro`
- **Problem:** `-keep class com.example.model.** / data.** / sync.** / auth.** / util.** { *; }` disables obfuscation and shrinking for all business logic and security code.
- **Fix:** Keep only what reflection needs (Room entities, Firestore models).

### S-11 · LOW · CI and signing hygiene
- **Where:** `.github/workflows/build-apk.yml` lines 7, 21; `app/build.gradle.kts` lines 66–68
- **Problem:**
  - The workflow has `contents: write` and also runs on `pull_request`.
  - The debug keystore password is hard-coded (`android`). That is acceptable for debug, but the debug APK is published as a build artifact.
- **Fix:** Scope `contents: write` to the release job only.

---

# 2. Performance

### P-1 · HIGH · Every write triggers a full re-download of all collections
- **Where:** `data/repository/CattleFeedRepository.kt` `triggerAutoSync()`; `sync/FirestoreSyncEngine.kt` lines 727, 765, 860, 933, 1006, 1155
- **Problem:**
  - After every sale, payment, purchase, or edit, `syncNow()` runs.
  - `syncNow()` calls `.get().await()` on the full `staff`, `customers`, `inventory`, `transactions`, and `suppliers` collections, with no `updatedAt` filter.
  - The realtime listeners already stream the same documents, so everything is read twice.
- **Impact:** Firestore read cost and latency grow with total history (O(N) reads per sale).
- **Fix:**
  - Rely on the listeners for pulls.
  - Do incremental pulls with `whereGreaterThan("updatedAt", lastSync)`.
  - Make `syncNow` push-only for pending rows.

### P-2 · HIGH · Overlapping syncs are dropped
- **Where:** `sync/FirestoreSyncEngine.kt` line 691 (`syncMutex.tryLock()`)
- **Problem:** If a sync is running when a new sale is saved, `triggerSync()` returns immediately and the new row stays `PENDING` until the next trigger (up to the 6-hour periodic worker). That is a data-loss window if the device is lost or wiped.
- **Fix:** Queue a follow-up sync, or loop until there are no pending rows.

### P-3 · HIGH · Pushes are one network round-trip per document
- **Where:** `sync/FirestoreSyncEngine.kt` (push phase, roughly lines 1330–1580)
- **Problem:** Each pending customer/sale/payment/product is written with its own `set().await()`. After an offline period this is slow, and a failure mid-way leaves partial state (a sale uploaded without its stock update).
- **Fix:** Use `WriteBatch` (up to 500 ops) grouped by related documents.

### P-4 · MEDIUM · PBKDF2 runs on the main thread, for every user
- **Where:** `ui/auth/PinUnlockScreenView.kt` line 127 (`scope.launch` with no dispatcher switch), line 152 (loop)
- **Problem:** Each `verifyPin` does 120,000 PBKDF2 iterations. With several users the loop can freeze the UI or cause an ANR on low-end phones.
- **Fix:** Wrap in `withContext(Dispatchers.Default)` and verify only the selected user (see S-3).

### P-5 · MEDIUM · Whole tables held in memory and recomputed on every change
- **Where:** `ui/viewmodel/MainViewModel.kt` (all `allSales`, `allPayments`, `allCustomers`, …); `util/ShopAnalytics.kt` `computeCustomerInsights`
- **Problem:** Every Room invalidation re-emits full lists, and `customerInsights` re-groups all sales/payments. It also recomputes every 60 s when `_nowMillis` ticks.
- **Fix:** Use SQL aggregates (`SUM`, `GROUP BY`) and paged queries. Tick `nowMillis` only at midnight.

### P-6 · MEDIUM · No query limits on listeners; full history on every new device
- **Where:** `sync/FirestoreSyncEngine.kt` lines 235, 310, 385
- **Problem:** The `transactions` listener has no date bound, so a newly paired device downloads all-time history.
- **Fix:** Window the initial pull (e.g., last 90 days) and fetch older data on demand.

### P-7 · LOW · Security-rule `get()` calls add reads per operation
- **Where:** `firestore.rules` lines 13–37
- **Problem:** `isApprovedActiveStaff` calls `exists()` and `getStaffDoc()` several times. Each staff operation costs an extra billed read and counts toward the 10-`get()` limit.
- **Fix:** Cache the staff role in a custom auth claim.

### P-8 · LOW · Stale pairing docs are never cleaned up
- **Where:** `data/repository/PairingRepository.kt`
- **Problem:** Expired or USED `pairings/{code}` docs accumulate. Only admin-initiated deletes are allowed.
- **Fix:** Add a Firestore TTL policy on `expiresAt`.

---

# 3. Calculation

### C-1 · HIGH · Returned quantity is subtracted twice in GST/PDF reports
- **Where:** `util/BackupExportUtil.kt` lines 64, 140, 218
- **Problem:** `returnSaleItems` already reduces `quantityKg` and `totalSalePrice` to the net amounts and accumulates `returnedKg`. The exports then compute `netKg = quantityKg − returnedKg`, which counts the return again.
- **Impact:** Revenue, COGS, and margin are understated for any bill with a return. Example: sell 10 kg, return 4 kg → stored `quantityKg` = 6, export shows 2.
- **Fix:** Use `quantityKg` and `totalSalePrice` directly.

### C-2 · HIGH · Cancelling a paid bill drops the customer's payment from the ledger
- **Where:** `data/repository/CattleFeedRepository.kt` `cancelSaleBill` (line 1657) and `recalculateCustomerUdhari`
- **Problem:** Cancel sets `billStatus="CANCELLED"` and `udhariAmount=0` but keeps `paidAmount`. The recalculation skips cancelled sales entirely, so cash the customer already paid on that bill is neither credited as advance nor recorded as a refund.
- **Fix:** On cancel, either create a refund `PaymentEntry` or count `paidAmount` of cancelled bills as advance credit. Confirm the intended policy first.

### C-3 · HIGH · Bill numbers are per-device and collide across devices
- **Where:** `data/repository/CattleFeedRepository.kt` line 557; `AppSettings.nextBillNumber` is local only
- **Problem:** Admin and staff devices each count from 1001. The same `billNumber` can exist on two devices for the same customer. `cancelSaleBill` (line 1670) and `groupSalesIntoBills` treat the same number as the same bill, so cancelling one bill can cancel another device's bill.
- **Fix:** Use a server-allocated counter (Firestore transaction) or a device-prefixed number. Group by an explicit `billId`.

### C-4 · HIGH · `updateCustomerUdhari` rewrites `updatedAt`, which blocks later remote edits
- **Where:** `data/dao/CustomerDao.kt` line 40–46; `sync/FirestoreSyncEngine.kt` lines 255, 878
- **Problem:** The default `updatedAt = System.currentTimeMillis()` runs even when a remote pull recalculates the balance with `markPending = false`. The local row stays `SYNCED` but now has a newer `updatedAt`. Later, an older-stamped edit from another device (e.g., a name change) fails the `updatedAt > existing.updatedAt` check and is ignored forever.
- **Fix:** Do not touch `updatedAt` when `markPending == false`.

### C-5 · MEDIUM · Per-kg price is rounded to 2 decimals, then used for profit and exports
- **Where:** `data/repository/CattleFeedRepository.kt` (`effectivePricePerKg`, in `recordMultiItemSale`)
- **Problem:** Totals are exact, but `pricePerKgAtSale = total / kg` is rounded to 2 dp. Anything that recomputes `price × qty` (the export fallback at `BackupExportUtil.kt` line 65, PDF line 219) drifts by paise per bill.
- **Fix:** Use `totalSalePrice` for revenue, or store the rate with 4 dp.

### C-6 · MEDIUM · Overpayment is attached to the last line item
- **Where:** `recordMultiItemSale`, paid-amount allocation
- **Problem:** The last line takes all remaining paid money, so its `paidAmount` can exceed its `totalSalePrice`. Per-line reports and per-line returns are distorted, though customer totals still balance.

### C-7 · MEDIUM · Opening advance (negative opening balance) is ignored
- **Where:** `model/Customer.kt` line 98 (`getOpeningUdhari` uses `takeIf { it > ZERO }`)
- **Problem:** A negative opening balance (customer has advance credit) is silently treated as 0.

### C-8 · MEDIUM · Customer insight limit disagrees with billing rule
- **Where:** `util/ShopAnalytics.kt` line 182 vs `model/Permissions.kt` (`isCustomerCreditBlocked`)
- **Problem:** Billing treats `creditLimit == 0` as "no limit"; insights treat it as ₹10,000 and mark the customer over-limit with a trust-score penalty. The sync pull also defaults a missing limit to 10,000.

### C-9 · LOW · Credit limit is only enforced in the UI
- **Where:** `ui/billing/BillingScreen.kt` line 210; `ui/estimate/EstimateCalculatorScreen.kt` line 1519; no check in `recordMultiItemSale`
- **Problem:** Other entry points (voice bill, estimate → bill) or direct repository calls bypass the limit.

### C-10 · LOW · Money stored as `Double` in Firestore
- **Where:** `sync/FirestoreSyncEngine.kt` push/pull (`toDouble()` / `BigDecimal.valueOf(double)`)
- **Problem:** Binary floating point can introduce off-by-paise differences between devices. Store paise as integers or amounts as strings.

### C-11 · LOW · Purchase invoice numbers can repeat
- **Where:** `CattleFeedRepository.kt` line 1016 (`"PUR-${now % 1000000}"`)
- **Problem:** The six digits repeat roughly every 16 minutes, so two auto-numbered invoices can share a number.

---

# 4. Firebase

### F-1 · HIGH · Rules do not constrain ledger documents or field sets
- **Where:** `firestore.rules` lines 59–93, 150–174
- **Problem:**
  - No `keys().hasOnly([...])` on customers, transactions, inventory, or bills, so arbitrary extra fields can be written (document bloat, hidden data).
  - Validators check only types. `creditLimit` and amounts have no upper bound.
  - Transactions and bills can be edited after creation, with no immutability or audit trail.
- **Fix:** Add field whitelists and bounds, and make ledger entries immutable (see S-1).

### F-2 · HIGH · Local deletions are overwritten by the next sync
- **Where:** `data/dao/SaleDao.kt` line 30 (`getSaleById` has `AND isDeleted = 0`); `sync/FirestoreSyncEngine.kt` lines ~410 and ~1010
- **Problem:** When a sale is soft-deleted locally (still `PENDING`), the pull calls `getSaleById`, gets `null`, treats it as new, and inserts the remote copy with `REPLACE`. This resurrects the sale and loses the pending delete. Customer balances are then recalculated wrongly.
- **Fix:** Look up by id without the `isDeleted` filter in the sync code.

### F-3 · HIGH · Last-writer-wins on absolute stock and balance values
- **Where:** `sync/FirestoreSyncEngine.kt` (push of `currentStockKg`, `totalUdhari`; pull compares `updatedAt`)
- **Problem:** Two devices selling the same product offline each push their own absolute stock. The later write overwrites the earlier, and stock goes wrong. Comparison uses device clocks, so a skewed clock wins.
- **Fix:** Sync stock movements (sales/purchases) and compute stock from them, or use `FieldValue.increment` inside a transaction. Use `FieldValue.serverTimestamp()` for ordering.

### F-4 · HIGH · Realtime listeners die silently and never restart
- **Where:** `sync/FirestoreSyncEngine.kt` lines 237, 312, 387 (`if (error != null ...) return`)
- **Problem:** On a permission-denied or other error the listener is dead, but `activeListenerUserId` stays set, so `startRealtimeListeners` returns early forever. Sync stops until the app restarts, and nothing is logged.
- **Fix:** Log the error, clear the listener, and re-register with backoff.

### F-5 · HIGH · Removed documents are never propagated
- **Where:** same listeners (`for (change in snapshots.documentChanges)`)
- **Problem:** `DocumentChange.Type.REMOVED` is not handled, and an empty snapshot returns early. Hard deletes by admin/manager (allowed by rules) never reach other devices.

### F-6 · HIGH · Store profile overwritten on "create store"
- **Where:** `ui/auth/LoginScreen.kt` line 1208
- **Problem:** `db.collection("users").document(uid).set(storeData)` has no `SetOptions.merge()`. If the owner re-runs onboarding (new device or reinstall), existing fields (address, GSTIN, UPI, thresholds) are wiped.
- **Fix:** Use `merge()`, or read first and only create if missing.

### F-7 · MEDIUM · Staff-screen Firestore listener is never removed
- **Where:** `ui/staff/StaffManagementScreen.kt` line 146 (inside `LaunchedEffect`, no `awaitClose` / `DisposableEffect`)
- **Problem:** Each recomposition keyed on the user adds another listener that stays alive after leaving the screen. This duplicates reads and leaks the context.

### F-8 · MEDIUM · Staff device wipes local data when not yet approved
- **Where:** `sync/FirestoreSyncEngine.kt` lines 765–783 and 562
- **Problem:** If the active staff doc is missing or not yet approved, `clearAllLocalDataAndSignOut()` runs. This deletes all local tables, including unsynced sales or payments on that device. The `isStaffDevice` detection (`activeUserId != "admin"`, role checks) is also fragile.
- **Fix:** Preserve pending rows, or block sign-out until they are pushed.

### F-9 · MEDIUM · Admin Firestore calls fall back to a bogus path
- **Where:** `ui/staff/StaffManagementScreen.kt` lines 460–490 (`googleUser?.uid ?: currentUser?.id ?: "admin"`)
- **Problem:** If both are null, writes target `users/admin/staff/...`, which is the wrong store and is rejected by the rules.

### F-10 · MEDIUM · Audit logs and bill edits are not synced
- **Where:** `BillAuditLog` / `billAuditLogDao` have no sync path or rules
- **Problem:** Edit/cancel/return history exists only on the device where it happened. The admin cannot see staff edits, and the history is lost if the device is wiped.

### F-11 · MEDIUM · Client clock skew breaks pairing
- **Where:** `data/repository/PairingRepository.kt` (expiry set from `System.currentTimeMillis()`); `firestore.rules` lines 102–104
- **Problem:** The rules compare `expiresAt` against `request.time` and require it within a 15-minute window. A device clock off by more than a few minutes makes the code invalid immediately or rejected on create.
- **Fix:** Write `expiresAt` from a server timestamp or from `request.time` in a function.

### F-12 · LOW · Database ID and rules deployment coupling
- **Where:** `res/values/firebase_applet_config.xml`; `firebase.json`
- **Problem:** The app uses a named database (`ai-studio-android-docubuil-…`). Rules are deployed only to that database. If a `(default)` database exists in the project, it keeps its own (possibly open) rules. Verify in the console.

### F-13 · LOW · Rules vs sync mismatch for suppliers
- **Where:** `firestore.rules` line 177 vs `sync/FirestoreSyncEngine.kt` line 1155
- **Problem:** `suppliers` is admin/manager only, so staff devices always get permission-denied on this pull. The error is swallowed, which is fine, but it adds a failed request on every sync.

---

# 5. Other (crash / data-loss)

### O-1 · HIGH · Every file export/share crashes (FileProvider paths)
- **Where:** `res/xml/file_paths.xml` (only `cache/images/` and `files/receipts/`); exports write to `cacheDir/bills`, `cacheDir/statements`, `cacheDir/templates` (`BackupExportUtil.kt` line 28; `CustomerStatementAndRepaymentScreens.kt` lines 1253, 1331; `SupplierPayStatementScreens.kt` line 2051; `CustomerEditAndImportScreens.kt` lines 2224, 2247)
- **Problem:** `FileProvider.getUriForFile` throws `IllegalArgumentException: Failed to find configured root` for any file outside the declared paths. GST CSV/PDF, JSON backup, statements, and templates all fail.
- **Fix:** Add `<cache-path name="bills" path="bills/"/>`, `statements/`, `templates/` (or a single `<cache-path path="."/>`).

### O-2 · MEDIUM · Destructive DB fallback can erase unsynced business data
- **Where:** `data/AppDatabase.kt` lines 377–378, 399
- **Problem:**
  - `fallbackToDestructiveMigration(dropAllTables = true)` and the `deleteDatabase` recovery path wipe the whole local DB on a schema mismatch.
  - All 16 migrations just call `ensureAllTablesAndColumns`, and `exportSchema = false`, so schema drift is not tested.
  - Pending (unsynced) sales, payments, and edits are lost.
- **Fix:** Back up the DB file before any destructive step, add real migrations, and export schemas.

### Also noted (not counted above)
- `exportFullBackupJson` is labelled "Full backup" but writes only customers and products (sales/payments are counts only), so it cannot restore a shop.
- The PDF GST report silently drops rows after about 28 entries or when `y > 790`.
- Google sign-in error mapping uses `contains("10")` / `contains("7")` (`auth/GoogleAuthManager.kt` lines 174, 180), which match almost any message and show the wrong error text.
- Promise reminder notifications show customer name and amount on the lock screen (no `setVisibility`).

---

## Suggested fix order

1. Rules hardening (S-1, F-1) and App Check (S-5).
2. Sync correctness: F-2, F-3, F-4, F-5, C-4, P-2.
3. Cost and performance: P-1, P-3, P-6.
4. Money logic: C-1, C-2, C-3.
5. Crash and data-loss: O-1, O-2.
6. PIN/pairing hardening: S-2, S-3, S-4.
