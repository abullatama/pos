package id.a3.feature.shift.repository

import id.a3.core.data.local.dao.ShiftDao
import id.a3.core.data.local.entity.CashOutEntity
import id.a3.core.data.local.entity.PaymentEntity
import id.a3.core.data.local.entity.ShiftEntity
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.core.domain.cash.CashCalculator
import id.a3.core.domain.cash.ShiftCashInput
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.util.UUID

data class ActiveShift(
    val shiftId: String,
    val businessUnitId: String = "bu-01",
    val deviceId: String = "dev-1",
    val cashierId: String,
    val cashierName: String,
    val openedAt: String,
    val openingCash: Long,
    val openingReason: String? = null
)

data class ShiftSummary(
    val shift: ActiveShift,
    val grossSales: Long,
    val totalDiscount: Long = 0L,
    val totalRefund: Long = 0L,
    val netSales: Long,
    val transactionCount: Int,
    val cashSales: Long,
    val cashDpAndSettlement: Long = 0L,
    val qrisSales: Long,
    val cashOuts: Long = 0L,
    val expectedCash: Long,
    val qrisPayments: List<PaymentEntity> = emptyList(),
    val openOrderIds: List<String> = emptyList()
)

interface ShiftRepository {
    fun observeCurrentShift(): Flow<ActiveShift?>
    suspend fun getActiveShift(): ActiveShift?
    suspend fun openShift(
        cashierId: String,
        cashierName: String,
        openingCash: Long,
        breakdown: Map<Long, Int> = emptyMap(),
        reason: String? = null,
        businessUnitId: String = "bu-01",
        deviceId: String = "dev-1"
    ): Result<ActiveShift>
    suspend fun getShiftSummary(shiftId: String): Result<ShiftSummary>
    fun observeCashOutsForShift(shiftId: String): Flow<List<CashOutEntity>>
    suspend fun recordCashOut(
        amount: Long,
        category: String,
        reason: String,
        receiptFileId: String? = null,
        receiptFilePath: String? = null,
        approvedBy: String? = null,
        deviceId: String = "dev-1"
    ): Result<Unit>
    suspend fun closeShift(
        shiftId: String,
        countedCash: Long,
        differenceReason: String? = null,
        cashLeft: Long,
        cashDeposited: Long,
        cashTakenBy: String? = null,
        qrisCheckedPaymentIds: List<String> = emptyList(),
        handoverOrderIds: List<String> = emptyList()
    ): Result<Unit>
    suspend fun forceCloseShift(
        shiftId: String,
        note: String,
        approvedBy: String
    ): Result<Unit>
}

class DefaultShiftRepository(
    private val shiftDao: ShiftDao,
    private val transactionManager: OrderTransactionManager
) : ShiftRepository {

    override fun observeCurrentShift(): Flow<ActiveShift?> {
        return shiftDao.observeActiveShift().map { entity ->
            entity?.toActiveShift()
        }
    }

    override suspend fun getActiveShift(): ActiveShift? {
        return shiftDao.getActiveShift()?.toActiveShift()
    }

    override suspend fun openShift(
        cashierId: String,
        cashierName: String,
        openingCash: Long,
        breakdown: Map<Long, Int>,
        reason: String?,
        businessUnitId: String,
        deviceId: String
    ): Result<ActiveShift> {
        val shiftId = UUID.randomUUID().toString()
        val openedAtMs = System.currentTimeMillis()

        val breakdownStringMap = breakdown.mapKeys { it.key.toString() }
        val breakdownJsonStr = buildJsonObject {
            breakdownStringMap.forEach { (k, v) ->
                if (v > 0) put(k, v)
            }
        }.toString()

        val shiftEntity = ShiftEntity(
            id = shiftId,
            businessUnitId = businessUnitId,
            deviceId = deviceId,
            cashierId = cashierId,
            cashierName = cashierName,
            status = "open",
            openingCash = openingCash,
            openingCashBreakdownJson = breakdownJsonStr,
            differenceReason = reason,
            openedAt = openedAtMs
        )

        return try {
            transactionManager.openShift(
                shift = shiftEntity,
                breakdownMap = breakdownStringMap,
                differenceReason = reason
            )
            Result.success(shiftEntity.toActiveShift())
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun getShiftSummary(shiftId: String): Result<ShiftSummary> {
        return try {
            val shift = shiftDao.getShiftById(shiftId)
                ?: throw IllegalArgumentException("Shift tidak ditemukan")

            val cashSales = transactionManager.paymentDao.getCashSalesForShift(shiftId)
            val cashDp = transactionManager.paymentDao.getCashDpForShift(shiftId)
            val qrisSales = transactionManager.paymentDao.getQrisTotalForShift(shiftId)
            val txCount = transactionManager.paymentDao.getTransactionCountForShift(shiftId)
            val qrisPayments = transactionManager.paymentDao.getQrisPaymentsForShift(shiftId)
            val openOrders = transactionManager.orderDao.observeOpenOrdersWithLines().first()
            val refund = transactionManager.correctionDao.getCashRefundTotalForShift(shiftId)
            val cashOuts = transactionManager.cashOutDao.getCashOutTotalForShift(shiftId)

            val orderDiscount = transactionManager.orderDao.getDiscountTotalForShift(shiftId)
            val grossSubtotal = transactionManager.orderDao.getGrossSubtotalForShift(shiftId)
            val gross = if (grossSubtotal > 0L) grossSubtotal else (cashSales + cashDp + qrisSales + orderDiscount)
            val discount = orderDiscount
            val net = gross - discount - refund

            val expected = CashCalculator.calculateExpectedCash(
                ShiftCashInput(
                    openingCash = shift.openingCash,
                    cashSales = cashSales,
                    cashDpAndSettlement = cashDp,
                    cashRefunds = refund,
                    cashOuts = cashOuts
                )
            )

            val summary = ShiftSummary(
                shift = shift.toActiveShift(),
                grossSales = gross,
                totalDiscount = discount,
                totalRefund = refund,
                netSales = net,
                transactionCount = txCount,
                cashSales = cashSales,
                cashDpAndSettlement = cashDp,
                qrisSales = qrisSales,
                cashOuts = cashOuts,
                expectedCash = expected,
                qrisPayments = qrisPayments,
                openOrderIds = openOrders.map { it.order.id }
            )
            Result.success(summary)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override fun observeCashOutsForShift(shiftId: String): Flow<List<CashOutEntity>> {
        return transactionManager.cashOutDao.observeCashOutsForShift(shiftId)
    }

    override suspend fun recordCashOut(
        amount: Long,
        category: String,
        reason: String,
        receiptFileId: String?,
        receiptFilePath: String?,
        approvedBy: String?,
        deviceId: String
    ): Result<Unit> {
        return try {
            transactionManager.recordCashOut(
                amount = amount,
                category = category,
                reason = reason,
                receiptFileId = receiptFileId,
                receiptFilePath = receiptFilePath,
                approvedBy = approvedBy,
                deviceId = deviceId
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun closeShift(
        shiftId: String,
        countedCash: Long,
        differenceReason: String?,
        cashLeft: Long,
        cashDeposited: Long,
        cashTakenBy: String?,
        qrisCheckedPaymentIds: List<String>,
        handoverOrderIds: List<String>
    ): Result<Unit> {
        return try {
            transactionManager.closeShift(
                shiftId = shiftId,
                countedCash = countedCash,
                differenceReason = differenceReason,
                cashLeft = cashLeft,
                cashDeposited = cashDeposited,
                cashTakenBy = cashTakenBy,
                qrisCheckedPaymentIds = qrisCheckedPaymentIds,
                handoverOrderIds = handoverOrderIds
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    override suspend fun forceCloseShift(
        shiftId: String,
        note: String,
        approvedBy: String
    ): Result<Unit> {
        return try {
            transactionManager.forceCloseShift(
                shiftId = shiftId,
                note = note,
                approvedBy = approvedBy
            )
            Result.success(Unit)
        } catch (e: Exception) {
            Result.failure(e)
        }
    }

    private fun ShiftEntity.toActiveShift(): ActiveShift {
        return ActiveShift(
            shiftId = id,
            businessUnitId = businessUnitId,
            deviceId = deviceId,
            cashierId = cashierId,
            cashierName = cashierName,
            openedAt = Instant.ofEpochMilli(openedAt).toString(),
            openingCash = openingCash,
            openingReason = differenceReason
        )
    }
}
