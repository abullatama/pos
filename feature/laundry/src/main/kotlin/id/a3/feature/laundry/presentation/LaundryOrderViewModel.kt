package id.a3.feature.laundry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryServiceEntity
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.util.PhoneNumberUtil
import id.a3.core.domain.laundry.LaundryCalculationResult
import id.a3.core.domain.laundry.LaundryCalculator
import id.a3.core.domain.laundry.LaundryOrderLine
import id.a3.core.domain.laundry.LaundryUnit
import id.a3.core.printer.PrinterService
import id.a3.core.printer.format.LaundryTagData
import id.a3.core.printer.format.LaundryTagFormatter
import id.a3.core.printer.format.LaundryTagLine
import id.a3.feature.laundry.repository.LaundryOrderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDateTime
import java.time.format.DateTimeFormatter
import java.util.Locale
import java.util.UUID

enum class LaundryPaymentOption {
    PAY_ON_PICKUP, // Bayar saat ambil (order status = open)
    PAID_UPFRONT,   // Lunas di muka (menuju alur pembayaran S05-S08)
    DP              // Uang Muka (DP)
}

enum class LaundryCategoryFilter {
    ALL,
    KILOAN,
    SATUAN
}

data class LaundrySelectedLine(
    val service: LaundryServiceEntity,
    val weightKg: Double = 1.0,
    val pieces: Int = 1,
    val isExpress: Boolean = false,
    val note: String = ""
)

data class LaundryOrderUiState(
    val availableServices: List<LaundryServiceEntity> = emptyList(),
    val categoryFilter: LaundryCategoryFilter = LaundryCategoryFilter.ALL,
    val filteredServices: List<LaundryServiceEntity> = emptyList(),
    val lines: List<LaundrySelectedLine> = emptyList(),
    val calculation: LaundryCalculationResult = LaundryCalculationResult(
        billedWeights = emptyList(),
        lineTotals = emptyList(),
        total = 0L
    ),
    val selectedCustomer: CustomerEntity? = null,
    val isNewCustomer: Boolean = false,
    val customerSearchQuery: String = "",
    val customerSearchResults: List<CustomerEntity> = emptyList(),
    val isCustomerDialogOpen: Boolean = false,
    val paymentOption: LaundryPaymentOption = LaundryPaymentOption.PAY_ON_PICKUP,
    val dpAmount: Long = 0L,
    val estimatedCompletionText: String = "",
    val selectedServiceForInput: LaundryServiceEntity? = null,
    val inputWeightKg: Double = 1.0,
    val inputPieces: Int = 1,
    val inputIsExpress: Boolean = false,
    val inputNote: String = "",
    val orderSuccessNotification: String? = null,
    val isLoading: Boolean = false
) {
    val customerName: String get() = selectedCustomer?.name.orEmpty()
    val customerPhone: String get() = selectedCustomer?.phone.orEmpty()
    val isFormReadyToSubmit: Boolean get() = lines.isNotEmpty() && selectedCustomer != null
}

@OptIn(ExperimentalCoroutinesApi::class)
class LaundryOrderViewModel(
    private val repository: LaundryOrderRepository,
    private val printerService: PrinterService? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO,
    private val deviceCodeProvider: (() -> String?)? = null
) : ViewModel() {

    val devicePrefix: String
        get() {
            val code = deviceCodeProvider?.invoke()?.takeIf { it.isNotBlank() }
            return if (code != null) "$code-" else "L1-"
        }

    private val _lines = MutableStateFlow<List<LaundrySelectedLine>>(emptyList())
    private val _selectedCustomer = MutableStateFlow<CustomerEntity?>(null)
    private val _isNewCustomer = MutableStateFlow(false)
    private val _customerSearchQuery = MutableStateFlow("")
    private val _isCustomerDialogOpen = MutableStateFlow(false)
    private val _categoryFilter = MutableStateFlow(LaundryCategoryFilter.ALL)
    private val _paymentOption = MutableStateFlow(LaundryPaymentOption.PAY_ON_PICKUP)
    private val _dpAmount = MutableStateFlow(0L)
    private val _selectedServiceForInput = MutableStateFlow<LaundryServiceEntity?>(null)
    private val _inputWeightKg = MutableStateFlow(1.0)
    private val _inputPieces = MutableStateFlow(1)
    private val _inputIsExpress = MutableStateFlow(false)
    private val _inputNote = MutableStateFlow("")
    private val _orderSuccessNotification = MutableStateFlow<String?>(null)

    init {
        viewModelScope.launch(ioDispatcher) {
            repository.seedInitialServicesIfEmpty()
        }
    }

    fun refreshServices() {
        viewModelScope.launch(ioDispatcher) {
            repository.seedInitialServicesIfEmpty()
        }
    }

    private val _customerSearchResults: StateFlow<List<CustomerEntity>> = _customerSearchQuery
        .flatMapLatest { query ->
            val trimmed = query.trim()
            if (trimmed.isEmpty()) {
                repository.observeRecentCustomers(10)
            } else {
                repository.searchCustomers(trimmed)
            }
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _inputFormFlow = combine(
        _selectedServiceForInput,
        _inputWeightKg,
        _inputPieces,
        _inputIsExpress,
        _inputNote
    ) { service, weight, pieces, express, note ->
        FormInputState(service, weight, pieces, express, note)
    }

    private data class FormInputState(
        val service: LaundryServiceEntity?,
        val weight: Double,
        val pieces: Int,
        val express: Boolean,
        val note: String
    )

    private val _customerFlow = combine(_selectedCustomer, _isNewCustomer) { cust, isNew ->
        cust to isNew
    }

    private val _searchFlow = combine(
        _customerSearchQuery,
        _customerSearchResults,
        _isCustomerDialogOpen
    ) { q, res, open ->
        Triple(q, res, open)
    }

    private val _paymentFlow = combine(_paymentOption, _dpAmount) { opt, dp ->
        opt to dp
    }

    val uiState: StateFlow<LaundryOrderUiState> = combine(
        repository.observeServices(),
        _lines,
        _categoryFilter,
        _paymentFlow,
        _orderSuccessNotification,
        _customerFlow,
        _searchFlow,
        _inputFormFlow
    ) { args: Array<Any?> ->
        @Suppress("UNCHECKED_CAST")
        val services = (args[0] as? List<*>)?.filterIsInstance<LaundryServiceEntity>() ?: emptyList()
        @Suppress("UNCHECKED_CAST")
        val lines = (args[1] as? List<*>)?.filterIsInstance<LaundrySelectedLine>() ?: emptyList()
        val category = args[2] as? LaundryCategoryFilter ?: LaundryCategoryFilter.ALL
        val paymentPair = args[3] as? Pair<*, *>
        val paymentOpt = paymentPair?.first as? LaundryPaymentOption ?: LaundryPaymentOption.PAY_ON_PICKUP
        val rawDp = paymentPair?.second as? Long ?: 0L
        val successNotif = args[4] as? String
        val customerPair = args[5] as? Pair<*, *>
        val customer = customerPair?.first as? CustomerEntity
        val isNew = customerPair?.second as? Boolean ?: false
        val searchTriple = args[6] as? Triple<*, *, *>
        val query = searchTriple?.first as? String ?: ""
        val searchResults = (searchTriple?.second as? List<*>)?.filterIsInstance<CustomerEntity>() ?: emptyList()
        val isDialogOpen = searchTriple?.third as? Boolean ?: false
        val formInput = args[7] as? FormInputState ?: FormInputState(null, 1.0, 1, false, "")

        val filteredServices = when (category) {
            LaundryCategoryFilter.ALL -> services
            LaundryCategoryFilter.KILOAN -> services.filter {
                val u = it.unit.lowercase().trim()
                u == "kg" || u == "kilo" || u == "kiloan" || u == "kilogram"
            }
            LaundryCategoryFilter.SATUAN -> services.filter {
                val u = it.unit.lowercase().trim()
                u == "pcs" || u == "satuan" || u == "potong" || u == "buah" || u == "lembar" || u == "pc"
            }
        }

        // Domain lines calculation
        val domainLines = lines.map { selected ->
            val u = selected.service.unit.lowercase().trim()
            val isPcs = u in listOf("pcs", "satuan", "potong", "buah", "lembar", "pc")
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

        // Estimated completion calculation
        val hasExpress = lines.any { it.isExpress } || formInput.express
        val etaText = formatEtaText(hasExpress)

        val defaultSelectedService = formInput.service ?: filteredServices.firstOrNull()

        val effectiveDp = if (rawDp > 0L) rawDp else (calculation.total / 2).coerceAtLeast(10000L).coerceAtMost(calculation.total.coerceAtLeast(10000L))

        LaundryOrderUiState(
            availableServices = services,
            categoryFilter = category,
            filteredServices = filteredServices,
            lines = lines,
            calculation = calculation,
            selectedCustomer = customer,
            isNewCustomer = isNew,
            customerSearchQuery = query,
            customerSearchResults = searchResults,
            isCustomerDialogOpen = isDialogOpen,
            paymentOption = paymentOpt,
            dpAmount = effectiveDp,
            estimatedCompletionText = etaText,
            selectedServiceForInput = defaultSelectedService,
            inputWeightKg = formInput.weight,
            inputPieces = formInput.pieces,
            inputIsExpress = formInput.express,
            inputNote = formInput.note,
            orderSuccessNotification = successNotif,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LaundryOrderUiState(isLoading = true)
    )

    fun setCategoryFilter(filter: LaundryCategoryFilter) {
        _categoryFilter.value = filter
    }

    fun selectServiceForInput(service: LaundryServiceEntity) {
        _selectedServiceForInput.value = service
        if (service.unit.lowercase() == "kg") {
            _inputWeightKg.value = service.minWeight.coerceAtLeast(1.0)
        } else {
            _inputPieces.value = 1
        }
    }

    fun setInputWeight(weight: Double) {
        val min = _selectedServiceForInput.value?.minWeight ?: 0.5
        _inputWeightKg.value = weight.coerceAtLeast(min)
    }

    fun adjustInputWeight(delta: Double) {
        val current = _inputWeightKg.value
        val step = _selectedServiceForInput.value?.weightStep ?: 0.5
        val min = _selectedServiceForInput.value?.minWeight ?: 0.5
        val newWeight = Math.round((current + delta) * 10.0) / 10.0
        _inputWeightKg.value = newWeight.coerceAtLeast(min)
    }

    fun setInputPieces(pieces: Int) {
        _inputPieces.value = pieces.coerceAtLeast(1)
    }

    fun adjustInputPieces(delta: Int) {
        _inputPieces.value = (_inputPieces.value + delta).coerceAtLeast(1)
    }

    fun toggleInputExpress() {
        _inputIsExpress.value = !_inputIsExpress.value
    }

    fun setInputNote(note: String) {
        _inputNote.value = note
    }

    fun addConfiguredServiceToLines() {
        val service = _selectedServiceForInput.value
            ?: uiState.value.filteredServices.firstOrNull()
            ?: return

        val isPcs = service.unit.lowercase() == "pcs"
        val line = LaundrySelectedLine(
            service = service,
            weightKg = if (isPcs) 1.0 else _inputWeightKg.value,
            pieces = if (isPcs) _inputPieces.value else 1,
            isExpress = _inputIsExpress.value,
            note = _inputNote.value.trim()
        )

        val current = _lines.value.toMutableList()
        current.add(line)
        _lines.value = current

        // Reset input form
        _inputNote.value = ""
        _inputIsExpress.value = false
        if (isPcs) {
            _inputPieces.value = 1
        } else {
            _inputWeightKg.value = service.minWeight.coerceAtLeast(1.0)
        }
    }

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

    fun setPaymentOption(option: LaundryPaymentOption) {
        _paymentOption.value = option
    }

    fun setDpAmount(amount: Long) {
        _dpAmount.value = amount.coerceAtLeast(0L)
    }

    fun setCustomerDialogOpen(isOpen: Boolean) {
        _isCustomerDialogOpen.value = isOpen
        if (isOpen) {
            _customerSearchQuery.value = ""
        }
    }

    fun onCustomerSearchQueryChange(query: String) {
        _customerSearchQuery.value = query
    }

    fun selectCustomer(customer: CustomerEntity, isNew: Boolean = false) {
        _selectedCustomer.value = customer
        _isNewCustomer.value = isNew
        _isCustomerDialogOpen.value = false
    }

    suspend fun findCustomerByPhone(phone: String): CustomerEntity? = repository.findCustomerByPhone(phone)

    fun createAndSelectNewCustomer(name: String, phone: String) {
        val normalized = PhoneNumberUtil.normalize(phone)
        val customer = CustomerEntity(
            id = UUID.randomUUID().toString(),
            name = name.trim(),
            phone = normalized
        )
        selectCustomer(customer, isNew = true)
    }

    fun setCustomer(name: String, phone: String) {
        createAndSelectNewCustomer(name, phone)
    }

    fun clearCustomer() {
        _selectedCustomer.value = null
        _isNewCustomer.value = false
    }

    fun clearOrder() {
        _lines.value = emptyList()
        _inputNote.value = ""
        _inputIsExpress.value = false
        clearCustomer()
    }

    fun dismissNotification() {
        _orderSuccessNotification.value = null
    }

    suspend fun submitOrder(
        displayNumber: String? = null,
        deviceId: String = "dev-laundry-1",
        shiftId: String? = null,
        cashierId: String = "u-cashier-2",
        cashierName: String = "Kasir Laundry",
        storeName: String = "ATHIA LAUNDRY HOUSE"
    ): Result<OrderEntity> {
        val finalDisplayNumber = if (!displayNumber.isNullOrBlank()) {
            displayNumber
        } else {
            repository.getNextDisplayNumber(devicePrefix)
        }
        val currentState = uiState.value
        val orderId = UUID.randomUUID().toString()

        val order = OrderEntity(
            id = orderId,
            displayNumber = finalDisplayNumber,
            businessUnitId = "bu-laundry-01",
            shiftId = shiftId,
            deviceId = deviceId,
            cashierId = cashierId,
            orderType = "laundry",
            customerId = currentState.selectedCustomer?.id,
            status = "open",
            subtotal = currentState.calculation.total,
            total = currentState.calculation.total,
            paidAmount = 0L
        )

        val orderLines = currentState.lines.mapIndexed { index, selected ->
            val billedWeight = currentState.calculation.billedWeights.getOrNull(index) ?: selected.weightKg
            val lineTotal = currentState.calculation.lineTotals.getOrNull(index) ?: 0L
            val isPcs = selected.service.unit.lowercase() == "pcs"

            OrderLineEntity(
                id = UUID.randomUUID().toString(),
                orderId = orderId,
                itemRefId = selected.service.id,
                nameSnapshot = selected.service.name + if (selected.isExpress) " (Express)" else "",
                quantity = if (isPcs) selected.pieces else 1,
                weight = if (isPcs) null else billedWeight,
                unitPriceSnapshot = if (isPcs) selected.service.rate else (if (billedWeight > 0.0) (lineTotal / billedWeight).toLong() else selected.service.rate),
                note = selected.note.ifBlank { null }
            )
        }

        val result = repository.createLaundryOrder(
            order = order,
            lines = orderLines,
            customer = currentState.selectedCustomer,
            isNewCustomer = currentState.isNewCustomer,
            deviceId = deviceId,
            shiftId = shiftId
        )

        return if (result.isSuccess) {
            _orderSuccessNotification.value = "Pesanan ${order.displayNumber} berhasil disimpan"

            // Cetak tag cucian secara asinkron (non-blocking) ke printer termal 58mm (PRT-03, LDR-06)
            if (printerService != null) {
                viewModelScope.launch(ioDispatcher) {
                    try {
                        val dateFormat = java.text.SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID"))
                        val tagData = LaundryTagData(
                            orderId = order.id,
                            displayNumber = order.displayNumber,
                            customerName = currentState.selectedCustomer?.name ?: "Pelanggan Athia",
                            customerPhone = currentState.selectedCustomer?.phone,
                            receivedAt = dateFormat.format(java.util.Date(order.createdAt)),
                            estimatedCompletion = currentState.estimatedCompletionText,
                            isExpress = currentState.lines.any { it.isExpress },
                            lines = currentState.lines.map { line ->
                                LaundryTagLine(
                                    name = line.service.name,
                                    quantityOrWeight = if (line.service.unit.lowercase() == "pcs") "${line.pieces} pcs" else "${line.weightKg} kg",
                                    isExpress = line.isExpress,
                                    note = line.note.ifBlank { null }
                                )
                            },
                            cashierOrStaffName = cashierName,
                            storeName = storeName
                        )
                        val bytes = LaundryTagFormatter.format(tagData)
                        printerService.print(bytes)
                    } catch (_: Exception) {}
                }
            }

            clearOrder()
            Result.success(order)
        } else {
            Result.failure(result.exceptionOrNull() ?: Exception("Gagal membuat order"))
        }
    }

    private fun formatEtaText(isExpress: Boolean): String {
        return try {
            val now = LocalDateTime.now()
            val eta = if (isExpress) now.plusHours(6) else now.plusDays(2)
            val formatter = DateTimeFormatter.ofPattern("EEE, d MMM · HH.mm", Locale.forLanguageTag("id-ID"))
            eta.format(formatter) + if (isExpress) " (Express 6 Jam)" else " (Reguler 2 Hari)"
        } catch (_: Exception) {
            if (isExpress) "6 Jam dari sekarang" else "2 Hari dari sekarang"
        }
    }
}
