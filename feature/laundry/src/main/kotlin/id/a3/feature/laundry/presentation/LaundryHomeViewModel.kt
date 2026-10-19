package id.a3.feature.laundry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.feature.laundry.repository.LaundryOrderRepository
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class ReadyOrderItem(
    val orderWithLines: OrderWithLines,
    val customer: CustomerEntity?,
    val remainingBalance: Long
)

data class LaundryHomeUiState(
    val todayOrdersCount: Int = 0,
    val readyOrdersCount: Int = 0,
    val unpaidOrdersCount: Int = 0,
    val readyOrders: List<ReadyOrderItem> = emptyList(),
    val isLoading: Boolean = false
)

class LaundryHomeViewModel(
    private val repository: LaundryOrderRepository
) : ViewModel() {

    val uiState: StateFlow<LaundryHomeUiState> = combine(
        repository.observeTodayOrdersCount(),
        repository.observeReadyOrdersCount(),
        repository.observeUnpaidOrdersCount(),
        repository.observeReadyOrders(10)
    ) { todayCount, readyCount, unpaidCount, readyOrdersList ->
        val items = readyOrdersList.map { owl ->
            val customer = owl.order.customerId?.let { repository.getCustomerById(it) }
            val remaining = (owl.order.total - owl.order.paidAmount).coerceAtLeast(0L)
            ReadyOrderItem(
                orderWithLines = owl,
                customer = customer,
                remainingBalance = remaining
            )
        }

        LaundryHomeUiState(
            todayOrdersCount = todayCount,
            readyOrdersCount = readyCount,
            unpaidOrdersCount = unpaidCount,
            readyOrders = items,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LaundryHomeUiState(isLoading = false)
    )
}
