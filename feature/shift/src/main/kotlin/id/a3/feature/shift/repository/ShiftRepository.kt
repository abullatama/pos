package id.a3.feature.shift.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

data class ActiveShift(
    val shiftId: String,
    val cashierId: String,
    val cashierName: String,
    val openedAt: String,
    val openingCash: Long,
    val openingReason: String? = null
)

interface ShiftRepository {
    fun observeCurrentShift(): Flow<ActiveShift?>
    suspend fun openShift(
        cashierId: String,
        cashierName: String,
        openingCash: Long,
        reason: String? = null
    ): Result<ActiveShift>
    suspend fun closeShift(shiftId: String, countedCash: Long, reason: String? = null): Result<Unit>
}

class DefaultShiftRepository : ShiftRepository {

    private val _currentShift = MutableStateFlow<ActiveShift?>(null)

    override fun observeCurrentShift(): Flow<ActiveShift?> = _currentShift.asStateFlow()

    override suspend fun openShift(
        cashierId: String,
        cashierName: String,
        openingCash: Long,
        reason: String?
    ): Result<ActiveShift> {
        val shift = ActiveShift(
            shiftId = java.util.UUID.randomUUID().toString(),
            cashierId = cashierId,
            cashierName = cashierName,
            openedAt = java.time.Instant.now().toString(),
            openingCash = openingCash,
            openingReason = reason
        )
        _currentShift.value = shift
        return Result.success(shift)
    }

    override suspend fun closeShift(shiftId: String, countedCash: Long, reason: String?): Result<Unit> {
        _currentShift.value = null
        return Result.success(Unit)
    }
}
