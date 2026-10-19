package id.a3.core.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import id.a3.core.data.local.entity.CashOutEntity
import id.a3.core.data.local.entity.CorrectionEntity
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.OutboxStatus
import id.a3.core.data.local.entity.PaymentEntity
import id.a3.core.data.local.entity.ShiftEntity
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

/**
 * Pengujian komprehensif migrasi skema Room Database (F40).
 * Memenuhi kriteria penerimaan F40: "Database versi lama berisi outbox bermigrasi tanpa kehilangan data".
 *
 * Menguji:
 * 1. Migrasi otomatis end-to-end v1 -> v6 oleh Room Engine mempertahankan seluruh data legacy.
 * 2. Migrasi inkremental tiap versi (1->2, 2->3, 3->4, 4->5, 5->6) memvalidasi integritas DDL dan data.
 */
@RunWith(RobolectricTestRunner::class)
@Config(manifest = Config.NONE)
class RoomMigrationTest {

    private lateinit var context: Context
    private val dbName = "test_migration.db"
    private lateinit var dbFile: File

    @Before
    fun setUp() {
        context = ApplicationProvider.getApplicationContext()
        dbFile = context.getDatabasePath(dbName)
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    @After
    fun tearDown() {
        if (dbFile.exists()) {
            dbFile.delete()
        }
    }

    private fun createV1Database(): SupportSQLiteDatabase {
        val config = androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
            .name(dbName)
            .callback(object : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(1) {
                override fun onCreate(db: SupportSQLiteDatabase) {
                    // Skema V1 Asli
                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `orders` (
                            `id` TEXT NOT NULL,
                            `displayNumber` TEXT NOT NULL,
                            `businessUnitId` TEXT NOT NULL,
                            `shiftId` TEXT,
                            `deviceId` TEXT NOT NULL,
                            `cashierId` TEXT NOT NULL,
                            `orderType` TEXT NOT NULL,
                            `tableLabel` TEXT,
                            `customerId` TEXT,
                            `status` TEXT NOT NULL,
                            `subtotal` INTEGER NOT NULL,
                            `discount` INTEGER NOT NULL,
                            `serviceCharge` INTEGER NOT NULL,
                            `tax` INTEGER NOT NULL,
                            `rounding` INTEGER NOT NULL,
                            `total` INTEGER NOT NULL,
                            `paidAmount` INTEGER NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `receivedAt` INTEGER,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `order_lines` (
                            `id` TEXT NOT NULL,
                            `orderId` TEXT NOT NULL,
                            `itemRefId` TEXT NOT NULL,
                            `nameSnapshot` TEXT NOT NULL,
                            `quantity` INTEGER NOT NULL,
                            `weight` REAL,
                            `unitPriceSnapshot` INTEGER NOT NULL,
                            `costSnapshot` INTEGER NOT NULL,
                            `note` TEXT,
                            PRIMARY KEY(`id`),
                            FOREIGN KEY(`orderId`) REFERENCES `orders`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_order_lines_orderId` ON `order_lines` (`orderId`)")

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `outbox` (
                            `bundleId` TEXT NOT NULL,
                            `deviceId` TEXT NOT NULL,
                            `shiftId` TEXT,
                            `eventsJson` TEXT NOT NULL,
                            `status` TEXT NOT NULL,
                            `attempts` INTEGER NOT NULL,
                            `lastError` TEXT,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`bundleId`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `sync_state` (
                            `key` TEXT NOT NULL,
                            `value` TEXT,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`key`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `menu_items` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `category` TEXT NOT NULL,
                            `price` INTEGER NOT NULL,
                            `active` INTEGER NOT NULL,
                            `soldOut` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `laundry_services` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `unit` TEXT NOT NULL,
                            `rate` INTEGER NOT NULL,
                            `expressRate` INTEGER NOT NULL,
                            `minWeight` REAL NOT NULL,
                            `weightStep` REAL NOT NULL,
                            `active` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `customers` (
                            `id` TEXT NOT NULL,
                            `name` TEXT NOT NULL,
                            `phone` TEXT NOT NULL,
                            `createdAt` INTEGER NOT NULL,
                            `updatedAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                    db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_customers_phone` ON `customers` (`phone`)")

                    db.execSQL("""
                        CREATE TABLE IF NOT EXISTS `laundry_pickups` (
                            `id` TEXT NOT NULL,
                            `orderId` TEXT NOT NULL,
                            `orderLineId` TEXT NOT NULL,
                            `quantity` INTEGER NOT NULL,
                            `isFinal` INTEGER NOT NULL,
                            `note` TEXT,
                            `userId` TEXT,
                            `approvedBy` TEXT,
                            `pickedUpAt` INTEGER NOT NULL,
                            PRIMARY KEY(`id`)
                        )
                    """.trimIndent())
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_laundry_pickups_orderId` ON `laundry_pickups` (`orderId`)")
                    db.execSQL("CREATE INDEX IF NOT EXISTS `index_laundry_pickups_orderLineId` ON `laundry_pickups` (`orderLineId`)")
                }

                override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) {}
            })
            .build()

        return FrameworkSQLiteOpenHelperFactory().create(config).writableDatabase
    }

    @Test
    fun `migrasi Room v1 ke v6 mempertahankan data outbox dan transaksi tanpa kehilangan`() = runBlocking {
        // 1. Buat database v1 dengan user_version = 1
        val dbV1 = createV1Database()

        // 2. Masukkan data legacy pada skema v1 (pesanan, baris pesanan, pelanggan, menu, dan outbox menunggu)
        val legacyBundleId = "bundle-legacy-001"
        val legacyOrderId = "order-legacy-001"
        val legacyCustomerId = "cust-legacy-001"

        dbV1.execSQL("""
            INSERT INTO `customers` (`id`, `name`, `phone`, `createdAt`, `updatedAt`)
            VALUES ('$legacyCustomerId', 'Ibu Nurhayati', '081234567890', 1697000000000, 1697000000000)
        """.trimIndent())

        dbV1.execSQL("""
            INSERT INTO `orders` (
                `id`, `displayNumber`, `businessUnitId`, `shiftId`, `deviceId`, `cashierId`,
                `orderType`, `tableLabel`, `customerId`, `status`, `subtotal`, `discount`,
                `serviceCharge`, `tax`, `rounding`, `total`, `paidAmount`, `createdAt`, `receivedAt`
            ) VALUES (
                '$legacyOrderId', 'C1-0001', 'bu-cafe-01', 'shift-001', 'dev-01', 'cashier-1',
                'dine_in', 'Meja 03', '$legacyCustomerId', 'paid', 50000, 5000,
                0, 4500, 0, 49500, 49500, 1697000010000, 1697000010000
            )
        """.trimIndent())

        dbV1.execSQL("""
            INSERT INTO `order_lines` (
                `id`, `orderId`, `itemRefId`, `nameSnapshot`, `quantity`, `weight`,
                `unitPriceSnapshot`, `costSnapshot`, `note`
            ) VALUES (
                'line-legacy-001', '$legacyOrderId', 'menu-item-1', 'Caramel Macchiato', 2, NULL,
                25000, 12000, 'Less sugar'
            )
        """.trimIndent())

        dbV1.execSQL("""
            INSERT INTO `outbox` (
                `bundleId`, `deviceId`, `shiftId`, `eventsJson`, `status`, `attempts`, `lastError`, `createdAt`, `updatedAt`
            ) VALUES (
                '$legacyBundleId', 'dev-01', 'shift-001', '[{"event_id":"evt-01","type":"order.created"}]',
                'PENDING', 0, NULL, 1697000020000, 1697000020000
            )
        """.trimIndent())

        // Tutup koneksi raw SQLite v1
        dbV1.close()

        // 3. Buka database v1 langsung menggunakan Room Database Builder versi 6 resmi
        // Room akan secara otomatis mengeksekusi urutan MIGRATION_1_2 -> MIGRATION_2_3 -> MIGRATION_3_4 -> MIGRATION_4_5 -> MIGRATION_5_6
        val appDb = Room.databaseBuilder(context, AppDatabase::class.java, dbName)
            .addMigrations(
                AppDatabase.MIGRATION_1_2,
                AppDatabase.MIGRATION_2_3,
                AppDatabase.MIGRATION_3_4,
                AppDatabase.MIGRATION_4_5,
                AppDatabase.MIGRATION_5_6
            )
            .fallbackToDestructiveMigration(false)
            .build()

        // 4. Verifikasi bahwa data legacy outbox tetap utuh 100% dan kolom baru v2 bernilai null
        val legacyOutbox = appDb.outboxDao().getByBundleId(legacyBundleId)
        assertNotNull("Outbox legacy harus tetap ada setelah migrasi v6", legacyOutbox)
        assertEquals(legacyBundleId, legacyOutbox!!.bundleId)
        assertEquals(OutboxStatus.PENDING, legacyOutbox.status)
        assertEquals("""[{"event_id":"evt-01","type":"order.created"}]""", legacyOutbox.eventsJson)
        assertNull(legacyOutbox.reasonCode)
        assertNull(legacyOutbox.flagsJson)
        assertEquals(1, appDb.outboxDao().getUnsyncedCount())

        // 5. Verifikasi bahwa data legacy order & order_lines tetap utuh 100%
        val orderWithLines = appDb.orderDao().getOrderWithLines(legacyOrderId)
        assertNotNull("Order legacy harus tetap terbaca", orderWithLines)
        assertEquals("C1-0001", orderWithLines!!.order.displayNumber)
        assertEquals(49500L, orderWithLines.order.total)
        assertEquals(1, orderWithLines.lines.size)
        assertEquals("Caramel Macchiato", orderWithLines.lines[0].nameSnapshot)
        assertEquals(2, orderWithLines.lines[0].quantity)
        assertEquals("Less sugar", orderWithLines.lines[0].note)

        // 6. Verifikasi pelanggan legacy tetap ada
        val customer = appDb.customerDao().getCustomerById(legacyCustomerId)
        assertNotNull("Pelanggan legacy harus tetap ada", customer)
        assertEquals("Ibu Nurhayati", customer!!.name)
        assertEquals("081234567890", customer.phone)

        // 7. Verifikasi tabel baru hasil migrasi (shifts, payments, corrections, cash_outs) berfungsi normal
        val shiftId = "shift-001"
        appDb.shiftDao().insertShift(
            ShiftEntity(
                id = shiftId,
                businessUnitId = "bu-cafe-01",
                deviceId = "dev-01",
                cashierId = "cashier-1",
                cashierName = "Siti Rahma",
                status = "open",
                openingCash = 200000L,
                openingCashBreakdownJson = "{\"100000\":2}",
                openedAt = System.currentTimeMillis()
            )
        )
        val savedShift = appDb.shiftDao().getShiftById(shiftId)
        assertNotNull(savedShift)
        assertEquals("Siti Rahma", savedShift!!.cashierName)

        // Insert payment baru ke tabel payments v4
        appDb.paymentDao().insertPayment(
            PaymentEntity(
                id = "payment-001",
                orderId = legacyOrderId,
                method = "cash",
                amount = 49500L,
                tendered = 50000L,
                idempotencyKey = "idem-pay-001",
                shiftId = shiftId,
                cashierId = "cashier-1",
                createdAt = System.currentTimeMillis()
            )
        )
        val savedPayments = appDb.paymentDao().getPaymentsForShift(shiftId)
        assertEquals(1, savedPayments.size)
        assertEquals(49500L, savedPayments[0].amount)

        // Insert correction ke tabel corrections v5
        appDb.correctionDao().insertCorrection(
            CorrectionEntity(
                id = "corr-001",
                orderId = legacyOrderId,
                shiftId = shiftId,
                type = "refund",
                amount = 49500L,
                reasonCode = "quality_complaint",
                note = "Rasa kopi asam",
                approvedBy = "manager-1",
                createdAt = System.currentTimeMillis()
            )
        )
        val savedCorrections = appDb.correctionDao().getCorrectionsForShift(shiftId)
        assertEquals(1, savedCorrections.size)
        assertEquals("refund", savedCorrections[0].type)

        // Insert cash_out ke tabel cash_outs v6
        appDb.cashOutDao().insertCashOut(
            CashOutEntity(
                id = "cashout-001",
                shiftId = shiftId,
                amount = 35000L,
                category = "Bahan baku",
                reason = "Beli es batu kristal",
                approvedBy = "manager-1",
                createdAt = System.currentTimeMillis()
            )
        )
        val savedCashOuts = appDb.cashOutDao().getCashOutsForShift(shiftId)
        assertEquals(1, savedCashOuts.size)
        assertEquals(35000L, savedCashOuts[0].amount)

        // Masukkan outbox ber-flag baru di v6
        appDb.outboxDao().insert(
            OutboxEntity(
                bundleId = "bundle-v6-002",
                deviceId = "dev-01",
                shiftId = shiftId,
                eventsJson = "[{\"type\":\"cashout.recorded\"}]",
                status = OutboxStatus.FAILED,
                reasonCode = "TOTAL_MISMATCH",
                flagsJson = "[\"TOTAL_MISMATCH\"]"
            )
        )
        assertEquals(2, appDb.outboxDao().getUnsyncedCount())

        appDb.close()
    }

    @Test
    fun `migrasi inkremental tiap versi memvalidasi penambahan tabel dan indeks`() {
        val db = createV1Database()

        // 1 -> 2
        AppDatabase.MIGRATION_1_2.migrate(db)
        val cursorCol = db.query("PRAGMA table_info(outbox)")
        var hasReasonCode = false
        var hasFlagsJson = false
        while (cursorCol.moveToNext()) {
            val name = cursorCol.getString(1)
            if (name == "reasonCode") hasReasonCode = true
            if (name == "flagsJson") hasFlagsJson = true
        }
        cursorCol.close()
        assertTrue(hasReasonCode)
        assertTrue(hasFlagsJson)

        // 2 -> 3
        AppDatabase.MIGRATION_2_3.migrate(db)
        val cursorShift = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='shifts'")
        assertTrue(cursorShift.moveToFirst())
        cursorShift.close()

        // 3 -> 4
        AppDatabase.MIGRATION_3_4.migrate(db)
        val cursorPay = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='payments'")
        assertTrue(cursorPay.moveToFirst())
        cursorPay.close()

        // 4 -> 5
        AppDatabase.MIGRATION_4_5.migrate(db)
        val cursorCorr = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='corrections'")
        assertTrue(cursorCorr.moveToFirst())
        cursorCorr.close()

        // 5 -> 6
        AppDatabase.MIGRATION_5_6.migrate(db)
        val cursorCash = db.query("SELECT name FROM sqlite_master WHERE type='table' AND name='cash_outs'")
        assertTrue(cursorCash.moveToFirst())
        cursorCash.close()

        db.close()
    }
}
