package id.a3.core.data.repository

import androidx.room.withTransaction
import id.a3.core.data.local.AppDatabase
import id.a3.core.data.local.dao.OrderDao
import id.a3.core.data.local.dao.OutboxDao
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OutboxEntity

/**
 * Mengelola penulisan transaksi lokal dan outbox secara atomik:
 * Pesanan dan antrean outbox ditulis dalam satu transaksi database Room.
 * Jika salah satu gagal, seluruh transaksi dibatalkan (tidak ada outbox yatim / order tanpa outbox).
 */
class OrderTransactionManager(
    private val database: AppDatabase,
    private val orderDao: OrderDao = database.orderDao(),
    private val outboxDao: OutboxDao = database.outboxDao()
) {

    /**
     * Menyimpan order, rincian baris item, dan antrean outbox secara atomik dalam satu transaksi Room.
     */
    suspend fun createOrderWithOutbox(
        order: OrderEntity,
        lines: List<OrderLineEntity>,
        outbox: OutboxEntity
    ) {
        database.withTransaction {
            orderDao.insertOrder(order)
            if (lines.isNotEmpty()) {
                orderDao.insertLines(lines)
            }
            outboxDao.insert(outbox)
        }
    }
}
