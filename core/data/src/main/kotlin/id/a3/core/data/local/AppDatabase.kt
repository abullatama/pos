package id.a3.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import id.a3.core.data.local.dao.CustomerDao
import id.a3.core.data.local.dao.LaundryPickupDao
import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.dao.SyncStateDao
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryPickupEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.MenuItemEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OutboxEntity
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
        LaundryPickupEntity::class
    ],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao
    abstract fun outboxDao(): OutboxDao
    abstract fun syncStateDao(): SyncStateDao
    abstract fun masterDataDao(): MasterDataDao
    abstract fun customerDao(): CustomerDao
    abstract fun laundryPickupDao(): LaundryPickupDao

    companion object {
        const val DATABASE_NAME = "a3_pos.db"

        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE outbox ADD COLUMN reasonCode TEXT DEFAULT NULL")
                db.execSQL("ALTER TABLE outbox ADD COLUMN flagsJson TEXT DEFAULT NULL")
            }
        }

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            ).addMigrations(MIGRATION_1_2)
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
