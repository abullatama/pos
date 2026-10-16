package id.a3.feature.laundry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.domain.laundry.LaundryCalculationResult
import id.a3.core.domain.laundry.LaundryCalculator
import id.a3.core.domain.laundry.LaundryOrderLine
import id.a3.core.domain.laundry.LaundryUnit
import id.a3.feature.laundry.repository.LaundryOrderRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class LaundrySelectedLine(
    val service: LaundryServiceEntity,
    val weightKg: Double = 1.0,
    val pieces: Int = 1,
    val isExpress: Boolean = false,
    val note: String = ""
)

data class LaundryOrderUiState(
    val availableServices: List<LaundryServiceEntity> = emptyList(),
    val lines: List<LaundrySelectedLine> = emptyList(),
    val calculation: LaundryCalculationResult = LaundryCalculationResult(
        billedWeights = emptyList(),
        lineTotals = emptyList(),
        total = 0L
    ),
    val customerPhone: String = "",
    val customerName: String = "",
    val isLoading: Boolean = false
)

class LaundryOrderViewModel(
    private val repository: LaundryOrderRepository
) : ViewModel() {

    private val _lines = MutableStateFlow<List<LaundrySelectedLine>>(emptyList())
    private val _customerPhone = MutableStateFlow("")
    private val _customerName = MutableStateFlow("")

    val uiState: StateFlow<LaundryOrderUiState> = combine(
        repository.observeServices(),
        _lines,
        _customerPhone,
        _customerName
    ) { services, lines, phone, name ->
        val domainLines = lines.map { selected ->
            val isPcs = selected.service.unit.lowercase() == "pcs"
            val effectiveRate = if (selected.isExpress && selected.service.expressRate > 0) {
                selected.service.expressRate
            } else {
                selected.service.rate
            }

            if (isPcs) {
                LaundryOrderLine(
                    unit = LaundryUnit.PCS,
                    rate = effectiveRate,
                    quantity = selected.pieces
                )
            } else {
                LaundryOrderLine(
                    unit = LaundryUnit.KG,
                    rate = effectiveRate,
                    weight = selected.weightKg,
                    minWeight = selected.service.minWeight,
                    step = selected.service.weightStep
                )
            }
        }

        val calculation = LaundryCalculator.calculateOrder(domainLines)

        LaundryOrderUiState(
            availableServices = services,
            lines = lines,
            calculation = calculation,
            customerPhone = phone,
            customerName = name,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LaundryOrderUiState(isLoading = true)
    )

    fun addLine(service: LaundryServiceEntity) {
        val current = _lines.value.toMutableList()
        current.add(LaundrySelectedLine(service = service))
        _lines.value = current
    }

    fun updateWeight(index: Int, weight: Double) {
        val current = _lines.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(weightKg = weight.coerceAtLeast(0.1))
            _lines.value = current
        }
    }

    fun toggleExpress(index: Int) {
        val current = _lines.value.toMutableList()
        if (index in current.indices) {
            current[index] = current[index].copy(isExpress = !current[index].isExpress)
            _lines.value = current
        }
    }

    fun removeLine(index: Int) {
        val current = _lines.value.toMutableList()
        if (index in current.indices) {
            current.removeAt(index)
            _lines.value = current
        }
    }

    fun setCustomer(name: String, phone: String) {
        _customerName.value = name
        _customerPhone.value = phone
    }

    fun clearOrder() {
        _lines.value = emptyList()
        _customerName.value = ""
        _customerPhone.value = ""
    }
}
