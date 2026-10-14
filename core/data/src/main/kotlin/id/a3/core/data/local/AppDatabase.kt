package id.a3.core.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OutboxEntity

@Database(
    entities = [
        OrderEntity::class,
        OrderLineEntity::class,
        OutboxEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {

    abstract fun orderDao(): OrderDao
    abstract fun outboxDao(): OutboxDao

    companion object {
        const val DATABASE_NAME = "a3_pos.db"

        fun build(context: Context): AppDatabase {
            return Room.databaseBuilder(
                context.applicationContext,
                AppDatabase::class.java,
                DATABASE_NAME
            ).fallbackToDestructiveMigration(false)
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
