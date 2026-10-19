package id.a3.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.a3.core.data.local.dao.CashOutDao
import id.a3.core.data.local.dao.CorrectionDao
import id.a3.core.data.local.dao.CustomerDao
import id.a3.core.data.local.dao.LaundryPickupDao
import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.dao.PaymentDao
import id.a3.core.data.local.dao.ShiftDao
import id.a3.core.data.local.dao.SyncStateDao
import id.a3.core.data.local.entity.CashOutEntity
import id.a3.core.data.local.entity.CorrectionEntity
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryPickupEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.MenuItemEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OutboxEntity
import id.a3.core.data.local.entity.PaymentEntity
import id.a3.core.data.local.entity.ShiftEntity
import id.a3.core.data.local.entity.SyncStateEntity

@Database(
    entities = [
        OrderEntity::class,
        OrderLineEntity::class,
        OutboxEntity::class,
        SyncStateEntity::class,
        MenuItemEntity::class,
        LaundryServiceEntity::class,
        CustomerEntity::class,
        LaundryPickupEntity::class,
        ShiftEntity::class,
        PaymentEntity::class,
        CorrectionEntity::class,
        CashOutEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun masterDataDao(): MasterDataDao
    abstract fun customerDao(): CustomerDao
    abstract fun laundryPickupDao(): LaundryPickupDao
    abstract fun shiftDao(): ShiftDao
    abstract fun paymentDao(): PaymentDao
    abstract fun correctionDao(): CorrectionDao
    abstract fun cashOutDao(): CashOutDao

    companion object {
        const val DATABASE_NAME = "a3_pos.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbox ADD COLUMN reasonCode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE outbox ADD COLUMN flagsJson TEXT DEFAULT NULL")
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `shifts` (
                        `id` TEXT NOT NULL,
                        `businessUnitId` TEXT NOT NULL,
                        `deviceId` TEXT NOT NULL,
                        `cashierId` TEXT NOT NULL,
                        `cashierName` TEXT NOT NULL,
                        `status` TEXT NOT NULL,
                        `openingCash` INTEGER NOT NULL,
                        `openingCashBreakdownJson` TEXT NOT NULL,
                        `differenceReason` TEXT DEFAULT NULL,
                        `openedAt` INTEGER NOT NULL,
                        `closedAt` INTEGER DEFAULT NULL,
                        `countedCash` INTEGER DEFAULT NULL,
                        `cashLeft` INTEGER DEFAULT NULL,
                        `cashDeposited` INTEGER DEFAULT NULL,
                        `cashDifferenceReason` TEXT DEFAULT NULL,
                        `approvedBy` TEXT DEFAULT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
            }
        }

        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `payments` (
                        `id` TEXT NOT NULL,
                        `orderId` TEXT NOT NULL,
                        `method` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `tendered` INTEGER DEFAULT NULL,
                        `idempotencyKey` TEXT NOT NULL,
                        `shiftId` TEXT DEFAULT NULL,
                        `cashierId` TEXT DEFAULT NULL,
                        `isDp` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_orderId` ON `payments` (`orderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_payments_shiftId` ON `payments` (`shiftId`)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS `index_payments_idempotencyKey` ON `payments` (`idempotencyKey`)")
            }
        }

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `corrections` (
                        `id` TEXT NOT NULL,
                        `orderId` TEXT NOT NULL,
                        `shiftId` TEXT DEFAULT NULL,
                        `type` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `refundMethod` TEXT DEFAULT 'cash',
                        `originalPaymentId` TEXT DEFAULT NULL,
                        `orderLineIdsJson` TEXT NOT NULL DEFAULT '[]',
                        `reasonCode` TEXT NOT NULL,
                        `note` TEXT DEFAULT NULL,
                        `approvedBy` TEXT NOT NULL,
                        `isOldTransactionRefund` INTEGER NOT NULL DEFAULT 0,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_corrections_orderId` ON `corrections` (`orderId`)")
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_corrections_shiftId` ON `corrections` (`shiftId`)")
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS `cash_outs` (
                        `id` TEXT NOT NULL,
                        `shiftId` TEXT NOT NULL,
                        `amount` INTEGER NOT NULL,
                        `category` TEXT NOT NULL,
                        `reason` TEXT NOT NULL,
                        `receiptFileId` TEXT DEFAULT NULL,
                        `receiptFilePath` TEXT DEFAULT NULL,
                        `approvedBy` TEXT DEFAULT NULL,
                        `createdAt` INTEGER NOT NULL,
                        PRIMARY KEY(`id`)
                    )
                """.trimIndent())
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_cash_outs_shiftId` ON `cash_outs` (`shiftId`)")
            }
        }

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4, MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration(false)
                .build()
        }

        fun buildInMemory(context: Context): AppDatabase {
            return Room.inMemoryDatabaseBuilder(
                context.applicationContext,
                AppDatabase::class.java
            ).allowMainThreadQueries()
                .build()
        }
    }
}
