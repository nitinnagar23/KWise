package com.example.data

import android.content.Context
import android.util.Log
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.example.data.dao.BillAuditLogDao
import com.example.data.dao.CustomerDao
import com.example.data.dao.EstimateDraftDao
import com.example.data.dao.PaymentDao
import com.example.data.dao.ProductDao
import com.example.data.dao.PurchaseDao
import com.example.data.dao.PurchaseInvoiceDao
import com.example.data.dao.RoleChangeLogDao
import com.example.data.dao.SaleDao
import com.example.data.dao.SettingsDao
import com.example.data.dao.SupplierPaymentDao
import com.example.data.dao.UserDao
import com.example.model.AppSettings
import com.example.model.BillAuditLog
import com.example.model.Customer
import com.example.model.EstimateDraft
import com.example.model.PaymentEntry
import com.example.model.Product
import com.example.model.PurchaseEntry
import com.example.model.PurchaseInvoice
import com.example.model.RoleChangeLog
import com.example.model.SaleEntry
import com.example.model.SupplierPayment
import com.example.model.User
import java.math.BigDecimal
import java.util.UUID

@Database(
    entities = [
        User::class,
        Customer::class,
        Product::class,
        SaleEntry::class,
        PaymentEntry::class,
        PurchaseEntry::class,
        PurchaseInvoice::class,
        SupplierPayment::class,
        AppSettings::class,
        RoleChangeLog::class,
        BillAuditLog::class,
        EstimateDraft::class
    ],
    version = 11,
    exportSchema = false
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun userDao(): UserDao
    abstract fun customerDao(): CustomerDao
    abstract fun productDao(): ProductDao
    abstract fun saleDao(): SaleDao
    abstract fun paymentDao(): PaymentDao
    abstract fun purchaseDao(): PurchaseDao
    abstract fun purchaseInvoiceDao(): PurchaseInvoiceDao
    abstract fun supplierPaymentDao(): SupplierPaymentDao
    abstract fun settingsDao(): SettingsDao
    abstract fun roleChangeLogDao(): RoleChangeLogDao
    abstract fun billAuditLogDao(): BillAuditLogDao
    abstract fun estimateDraftDao(): EstimateDraftDao

    companion object {
        private const val TAG = "KhataWiseDatabase"
        private const val DB_NAME = "pashu_aahar.db"

        @Volatile
        private var INSTANCE: AppDatabase? = null

        private fun safeAddColumn(
            db: SupportSQLiteDatabase,
            tableName: String,
            columnName: String,
            columnDefinition: String
        ) {
            try {
                var columnExists = false
                db.query("PRAGMA table_info(`$tableName`)").use { cursor ->
                    val nameIdx = cursor.getColumnIndex("name")
                    while (cursor.moveToNext()) {
                        if (nameIdx >= 0 && cursor.getString(nameIdx).equals(columnName, ignoreCase = true)) {
                            columnExists = true
                            break
                        }
                    }
                }
                if (!columnExists) {
                    db.execSQL("ALTER TABLE `$tableName` ADD COLUMN `$columnName` $columnDefinition")
                }
            } catch (e: Throwable) {
                Log.w(TAG, "safeAddColumn($tableName.$columnName): ${e.message}")
            }
        }

        fun ensureAllTablesAndColumns(db: SupportSQLiteDatabase) {
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `users` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `role` TEXT NOT NULL, `email` TEXT NOT NULL, `phone` TEXT NOT NULL, `deviceInfo` TEXT NOT NULL, `lastActiveAt` INTEGER NOT NULL, `pinHash` TEXT NOT NULL, `isActive` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `customers` (`id` TEXT NOT NULL, `name` TEXT NOT NULL, `fatherName` TEXT NOT NULL, `phone` TEXT NOT NULL, `phonesJson` TEXT NOT NULL, `village` TEXT NOT NULL, `photoDriveUrl` TEXT, `totalUdhari` TEXT NOT NULL, `creditLimit` TEXT NOT NULL, `promiseDate` INTEGER, `createdByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `products` (`id` TEXT NOT NULL, `feedName` TEXT NOT NULL, `baseUnit` TEXT NOT NULL, `kgPerBag` TEXT NOT NULL, `currentStockKg` TEXT NOT NULL, `averagePurchaseCostPerKg` TEXT NOT NULL, `sellingPricePerKg` TEXT NOT NULL, `looseSellingPricePerKg` TEXT NOT NULL, `isLooseOnly` INTEGER NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `sales` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, `customerId` TEXT NOT NULL, `productId` TEXT NOT NULL, `quantityKg` TEXT NOT NULL, `pricePerKgAtSale` TEXT NOT NULL, `costPerKgAtSale` TEXT NOT NULL, `totalSalePrice` TEXT NOT NULL, `paidAmount` TEXT NOT NULL, `udhariAmount` TEXT NOT NULL, `paymentStatus` TEXT NOT NULL, `soldByUserId` TEXT NOT NULL, `returnedKg` TEXT NOT NULL, `billStatus` TEXT NOT NULL, `billNumber` TEXT NOT NULL DEFAULT '', `paymentMode` TEXT NOT NULL DEFAULT 'CASH', `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `payments` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, `customerId` TEXT NOT NULL, `amount` TEXT NOT NULL, `linkedSaleId` TEXT, `note` TEXT NOT NULL, `collectedByUserId` TEXT NOT NULL, `paymentMode` TEXT NOT NULL DEFAULT 'CASH', `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `purchases` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, `wholesalerName` TEXT NOT NULL, `productId` TEXT NOT NULL, `quantityKg` TEXT NOT NULL, `totalPurchaseCost` TEXT NOT NULL, `paymentStatus` TEXT NOT NULL, `recordedByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `purchase_invoices` (`id` TEXT NOT NULL, `invoiceNumber` TEXT NOT NULL, `date` INTEGER NOT NULL, `wholesalerName` TEXT NOT NULL, `wholesalerPhone` TEXT NOT NULL, `itemsJson` TEXT NOT NULL, `ladhan` TEXT NOT NULL, `utaar` TEXT NOT NULL, `gaadiKiraya` TEXT NOT NULL, `goodsSubtotal` TEXT NOT NULL, `totalInvoiceCost` TEXT NOT NULL, `paidAmount` TEXT NOT NULL, `balanceAmount` TEXT NOT NULL, `paymentStatus` TEXT NOT NULL, `notes` TEXT NOT NULL, `recordedByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `supplier_payments` (`id` TEXT NOT NULL, `date` INTEGER NOT NULL, `wholesalerName` TEXT NOT NULL, `amount` TEXT NOT NULL, `paymentMode` TEXT NOT NULL, `note` TEXT NOT NULL, `recordedByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, `isDeleted` INTEGER NOT NULL, `syncStatus` TEXT NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `app_settings` (`id` INTEGER NOT NULL, `shopName` TEXT NOT NULL, `shopAddress` TEXT NOT NULL, `shopPhone` TEXT NOT NULL, `shopGstin` TEXT NOT NULL, `shopUpiId` TEXT NOT NULL, `themeMode` TEXT NOT NULL, `watchThreshold` TEXT NOT NULL, `criticalThreshold` TEXT NOT NULL, `appsScriptUrl` TEXT NOT NULL, `activeUserId` TEXT, `lastSyncTime` INTEGER NOT NULL, `storeAdminUid` TEXT NOT NULL, `language` TEXT NOT NULL, `nextBillNumber` INTEGER NOT NULL DEFAULT 1001, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `role_change_logs` (`id` TEXT NOT NULL, `targetUserId` TEXT NOT NULL, `targetUserName` TEXT NOT NULL, `oldRole` TEXT, `newRole` TEXT NOT NULL, `changedByUserId` TEXT NOT NULL, `changedByUserName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `bill_audit_logs` (`id` TEXT NOT NULL, `saleId` TEXT NOT NULL, `customerId` TEXT NOT NULL, `customerName` TEXT NOT NULL, `productName` TEXT NOT NULL, `actionType` TEXT NOT NULL, `previousSummary` TEXT NOT NULL, `newSummary` TEXT NOT NULL, `reason` TEXT NOT NULL, `performedByUserId` TEXT NOT NULL, `performedByUserName` TEXT NOT NULL, `timestamp` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )
            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `estimate_drafts` (`id` TEXT NOT NULL, `title` TEXT NOT NULL, `itemsJson` TEXT NOT NULL, `totalWeightKg` TEXT NOT NULL, `totalAmount` TEXT NOT NULL, `createdByUserId` TEXT NOT NULL, `createdAt` INTEGER NOT NULL, `updatedAt` INTEGER NOT NULL, PRIMARY KEY(`id`))"
            )

            // Idempotent column additions for all evolved tables
            safeAddColumn(db, "app_settings", "shopName", "TEXT NOT NULL DEFAULT 'KhataWise Store'")
            safeAddColumn(db, "app_settings", "shopAddress", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "app_settings", "shopPhone", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "app_settings", "shopGstin", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "app_settings", "shopUpiId", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "app_settings", "themeMode", "TEXT NOT NULL DEFAULT 'LIGHT'")
            safeAddColumn(db, "app_settings", "watchThreshold", "TEXT NOT NULL DEFAULT '2000'")
            safeAddColumn(db, "app_settings", "criticalThreshold", "TEXT NOT NULL DEFAULT '5000'")
            safeAddColumn(db, "app_settings", "appsScriptUrl", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "app_settings", "activeUserId", "TEXT")
            safeAddColumn(db, "app_settings", "lastSyncTime", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "app_settings", "storeAdminUid", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "app_settings", "language", "TEXT NOT NULL DEFAULT 'en'")
            safeAddColumn(db, "app_settings", "nextBillNumber", "INTEGER NOT NULL DEFAULT 1001")

            safeAddColumn(db, "users", "email", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "users", "phone", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "users", "deviceInfo", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "users", "lastActiveAt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "users", "pinHash", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "users", "isActive", "INTEGER NOT NULL DEFAULT 1")
            safeAddColumn(db, "users", "createdAt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "users", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "users", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "users", "syncStatus", "TEXT NOT NULL DEFAULT 'PENDING'")

            safeAddColumn(db, "customers", "fatherName", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "customers", "phone", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "customers", "phonesJson", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "customers", "village", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "customers", "photoDriveUrl", "TEXT")
            safeAddColumn(db, "customers", "totalUdhari", "TEXT NOT NULL DEFAULT '0'")
            safeAddColumn(db, "customers", "creditLimit", "TEXT NOT NULL DEFAULT '0'")
            safeAddColumn(db, "customers", "promiseDate", "INTEGER")
            safeAddColumn(db, "customers", "createdByUserId", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "customers", "createdAt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "customers", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "customers", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "customers", "syncStatus", "TEXT NOT NULL DEFAULT 'PENDING'")

            safeAddColumn(db, "products", "baseUnit", "TEXT NOT NULL DEFAULT 'kg'")
            safeAddColumn(db, "products", "kgPerBag", "TEXT NOT NULL DEFAULT '50'")
            safeAddColumn(db, "products", "currentStockKg", "TEXT NOT NULL DEFAULT '0'")
            safeAddColumn(db, "products", "averagePurchaseCostPerKg", "TEXT NOT NULL DEFAULT '0'")
            safeAddColumn(db, "products", "sellingPricePerKg", "TEXT NOT NULL DEFAULT '0'")
            safeAddColumn(db, "products", "looseSellingPricePerKg", "TEXT NOT NULL DEFAULT '0'")
            safeAddColumn(db, "products", "isLooseOnly", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "products", "createdAt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "products", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "products", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "products", "syncStatus", "TEXT NOT NULL DEFAULT 'PENDING'")

            safeAddColumn(db, "sales", "returnedKg", "TEXT NOT NULL DEFAULT '0'")
            safeAddColumn(db, "sales", "billStatus", "TEXT NOT NULL DEFAULT 'ACTIVE'")
            safeAddColumn(db, "sales", "billNumber", "TEXT NOT NULL DEFAULT ''")
            safeAddColumn(db, "sales", "paymentMode", "TEXT NOT NULL DEFAULT 'CASH'")
            safeAddColumn(db, "sales", "createdAt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "sales", "updatedAt", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "sales", "isDeleted", "INTEGER NOT NULL DEFAULT 0")
            safeAddColumn(db, "sales", "syncStatus", "TEXT NOT NULL DEFAULT 'PENDING'")

            safeAddColumn(db, "payments", "paymentMode", "TEXT NOT NULL DEFAULT 'CASH'")

            // Performance indexes on high-cardinality foreign keys and query filter columns
            try {
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_sales_customerId` ON `sales` (`customerId`, `isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_sales_date` ON `sales` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_sales_syncStatus` ON `sales` (`syncStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_payments_customerId` ON `payments` (`customerId`, `isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_payments_date` ON `payments` (`date`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_payments_syncStatus` ON `payments` (`syncStatus`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_purchases_productId` ON `purchases` (`productId`, `isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_purchase_invoices_wholesaler` ON `purchase_invoices` (`wholesalerName`, `isDeleted`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `idx_supplier_payments_wholesaler` ON `supplier_payments` (`wholesalerName`, `isDeleted`)")
            } catch (e: Throwable) {
                Log.w(TAG, "ensureIndexes in migration: ${e.message}")
            }

            try {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `app_settings` (
                        `id`, `shopName`, `shopAddress`, `shopPhone`, `shopGstin`, `shopUpiId`,
                        `themeMode`, `watchThreshold`, `criticalThreshold`, `appsScriptUrl`,
                        `activeUserId`, `lastSyncTime`, `storeAdminUid`, `language`
                    ) VALUES (
                        1, 'KhataWise Store', '', '', '', '',
                        'LIGHT', '2000', '5000', '',
                        NULL, 0, '', 'en'
                    )
                    """.trimIndent()
                )
            } catch (e: Throwable) {
                Log.w(TAG, "ensureDefaultSettings in migration: ${e.message}")
            }
        }

        private fun seedDefaultDataSql(db: SupportSQLiteDatabase) {
            try {
                db.execSQL(
                    """
                    INSERT OR IGNORE INTO `app_settings` (
                        `id`, `shopName`, `shopAddress`, `shopPhone`, `shopGstin`, `shopUpiId`,
                        `themeMode`, `watchThreshold`, `criticalThreshold`, `appsScriptUrl`,
                        `activeUserId`, `lastSyncTime`, `storeAdminUid`, `language`
                    ) VALUES (
                        1, 'KhataWise Store', '', '', '', '',
                        'LIGHT', '2000', '5000', '',
                        NULL, 0, '', 'en'
                    )
                    """.trimIndent()
                )

                var count = 0
                db.query("SELECT COUNT(*) FROM `products`").use { cursor ->
                    if (cursor.moveToFirst()) {
                        count = cursor.getInt(0)
                    }
                }
                if (count == 0) {
                    val now = System.currentTimeMillis()
                    val defaults = listOf(
                        arrayOf<Any>("prod_choker_40", "Choker Wheat Bran (40kg)", "kg", "40", "2000", "42.00", "49.00", "50.00", 0),
                        arrayOf<Any>("prod_kapila_50", "Kapila Pashu Aahar (50kg)", "kg", "50", "2500", "24.50", "29.00", "30.00", 0),
                        arrayOf<Any>("prod_amul_50", "Amul Dan Buffalo Special (50kg)", "kg", "50", "1800", "26.00", "31.00", "32.00", 0),
                        arrayOf<Any>("prod_sarson_50", "Khal Mustard / Sarson (50kg)", "kg", "50", "1200", "32.00", "37.00", "38.00", 0),
                        arrayOf<Any>("prod_binola_50", "Binola Khal / Cottonseed (50kg)", "kg", "50", "1500", "34.00", "40.00", "42.00", 0),
                        arrayOf<Any>("prod_chana_loose", "Chana (चना / Desi Gram)", "kg", "1", "800", "58.00", "65.00", "65.00", 1),
                        arrayOf<Any>("prod_chilka_40", "Chana Chilka / Churi", "kg", "40", "1000", "30.00", "35.00", "36.00", 0),
                        arrayOf<Any>("prod_bajra_loose", "Bajra (बाजरा / Pearl Millet)", "kg", "1", "1200", "22.00", "26.00", "26.00", 1),
                        arrayOf<Any>("prod_makka_loose", "Makka (मक्का / Yellow Maize Grain)", "kg", "1", "1500", "23.50", "28.00", "28.00", 1),
                        arrayOf<Any>("prod_mineral_1", "Mineral Mixture Chelated (1kg)", "kg", "1", "150", "85.00", "110.00", "110.00", 1)
                    )
                    for (row in defaults) {
                        db.execSQL(
                            """
                            INSERT OR IGNORE INTO `products` (
                                `id`, `feedName`, `baseUnit`, `kgPerBag`, `currentStockKg`,
                                `averagePurchaseCostPerKg`, `sellingPricePerKg`, `looseSellingPricePerKg`,
                                `isLooseOnly`, `createdAt`, `updatedAt`, `isDeleted`, `syncStatus`
                            ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, 0, 'PENDING')
                            """.trimIndent(),
                            arrayOf<Any>(row[0], row[1], row[2], row[3], row[4], row[5], row[6], row[7], row[8], now, now)
                        )
                    }
                }
            } catch (e: Throwable) {
                Log.w(TAG, "seedDefaultDataSql failed: ${e.message}")
            }
        }

        val MIGRATION_1_9 = object : Migration(1, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_2_9 = object : Migration(2, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_3_9 = object : Migration(3, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_4_9 = object : Migration(4, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_5_9 = object : Migration(5, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_6_7 = object : Migration(6, 7) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_6_9 = object : Migration(6, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_7_8 = object : Migration(7, 8) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_7_9 = object : Migration(7, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_8_9 = object : Migration(8, 9) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_9_10 = object : Migration(9, 10) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }
        val MIGRATION_10_11 = object : Migration(10, 11) {
            override fun migrate(db: SupportSQLiteDatabase) = ensureAllTablesAndColumns(db)
        }

        private fun buildDatabase(appContext: Context): AppDatabase {
            return Room.databaseBuilder(
                appContext,
                AppDatabase::class.java,
                DB_NAME
            )
                .addCallback(object : Callback() {
                    override fun onCreate(db: SupportSQLiteDatabase) {
                        super.onCreate(db)
                        seedDefaultDataSql(db)
                    }

                    override fun onOpen(db: SupportSQLiteDatabase) {
                        super.onOpen(db)
                        seedDefaultDataSql(db)
                    }
                })
                .addMigrations(
                    MIGRATION_1_9,
                    MIGRATION_2_3,
                    MIGRATION_2_9,
                    MIGRATION_3_4,
                    MIGRATION_3_9,
                    MIGRATION_4_5,
                    MIGRATION_4_9,
                    MIGRATION_5_6,
                    MIGRATION_5_9,
                    MIGRATION_6_7,
                    MIGRATION_6_9,
                    MIGRATION_7_8,
                    MIGRATION_7_9,
                    MIGRATION_8_9,
                    MIGRATION_9_10,
                    MIGRATION_10_11
                )
                .build()
        }

        private fun backupDatabaseFilesBeforeRecovery(context: Context) {
            try {
                val dbFile = context.getDatabasePath(DB_NAME)
                if (dbFile.exists() && dbFile.length() > 0L) {
                    val backupDir = java.io.File(context.noBackupFilesDir, "db_recovery_backups")
                    if (!backupDir.exists()) backupDir.mkdirs()
                    val stamp = System.currentTimeMillis()
                    listOf("", "-wal", "-shm").forEach { suffix ->
                        val src = java.io.File(dbFile.absolutePath + suffix)
                        if (src.exists()) {
                            val dst = java.io.File(backupDir, "${DB_NAME}_$stamp$suffix")
                            src.copyTo(dst, overwrite = true)
                        }
                    }
                    Log.w(TAG, "Backed up local database to ${backupDir.absolutePath} before recovery")
                }
            } catch (backupErr: Throwable) {
                Log.w(TAG, "Failed to back up database before recovery", backupErr)
            }
        }

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: run {
                    val appContext = context.applicationContext
                    var dbInstance = buildDatabase(appContext)
                    try {
                        dbInstance.openHelper.writableDatabase
                    } catch (e: Throwable) {
                        Log.e(TAG, "Database validation failed on open, backing up before recovery", e)
                        try {
                            dbInstance.close()
                        } catch (_: Throwable) {}
                        backupDatabaseFilesBeforeRecovery(appContext)
                        try {
                            appContext.deleteDatabase(DB_NAME)
                        } catch (_: Throwable) {}
                        dbInstance = buildDatabase(appContext)
                        try {
                            dbInstance.openHelper.writableDatabase
                        } catch (inner: Throwable) {
                            Log.e(TAG, "Recovered database open warning", inner)
                        }
                    }
                    INSTANCE = dbInstance
                    dbInstance
                }
            }
        }
    }

    suspend fun seedInitialData() {
        try {
            seedDefaultDataSql(openHelper.writableDatabase)
        } catch (_: Throwable) {}
    }
}
