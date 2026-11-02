package id.a3.feature.laundry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.LaundryPickupEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.repository.LaundryPickupItem
import id.a3.core.domain.security.ManagerPinVerifier
import id.a3.core.printer.PrinterService
import id.a3.core.printer.format.LaundryTagData
import id.a3.core.printer.format.LaundryTagFormatter
import id.a3.core.printer.format.LaundryTagLine
import id.a3.core.ui.component.formatRupiah
import id.a3.feature.laundry.repository.LaundryOrderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class LaundryFilterStatus(val displayName: String, val dbStatus: String?) {
    ALL("Semua", null),
    RECEIVED("Diterima", "open"),
    PROCESSING("Diproses", "processing"),
    READY("Siap", "ready"),
    PICKED_UP("Diambil", "picked_up")
}

sealed interface PendingManagerAction {
    data class UnpaidPickup(val items: List<LaundryPickupItem>) : PendingManagerAction
    data class Rework(val reason: String) : PendingManagerAction
    data class Refund(val reasonCode: String, val note: String?, val amount: Long) : PendingManagerAction
}

data class LaundryOrdersUiState(
    val filterStatus: LaundryFilterStatus = LaundryFilterStatus.ALL,
    val orders: List<OrderWithLines> = emptyList(),
    val selectedOrder: OrderWithLines? = null,
    val customer: CustomerEntity? = null,
    val pickups: List<LaundryPickupEntity> = emptyList(),
    val isPickupDialogOpen: Boolean = false,
    val isReworkDialogOpen: Boolean = false,
    val isManagerPinDialogOpen: Boolean = false,
    val pendingAction: PendingManagerAction? = null,
    val reworkReason: String = "Noda belum hilang",
    val searchQuery: String = "",
    val pinErrorMessage: String? = null,
    val isActionLoading: Boolean = false,
    val successMessage: String? = null,
    val errorMessage: String? = null,
    val notificationStatus: String = "none",
    val notificationError: String? = null,
    val isCheckingNotification: Boolean = false
) {
    val remainingBalance: Long
        get() = ((selectedOrder?.order?.total ?: 0L) - (selectedOrder?.order?.paidAmount ?: 0L)).coerceAtLeast(0L)

    val isFullyPaid: Boolean
        get() = remainingBalance == 0L

    val currentStatus: String
        get() = selectedOrder?.order?.status?.lowercase().orEmpty()
}

@OptIn(ExperimentalCoroutinesApi::class)
class LaundryOrdersViewModel(
    private val repository: LaundryOrderRepository,
    private val printerService: PrinterService? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _filterStatus = MutableStateFlow(LaundryFilterStatus.ALL)
    private val _searchQuery = MutableStateFlow("")
    private val _selectedOrderId = MutableStateFlow<String?>(null)
    private val _isPickupDialogOpen = MutableStateFlow(false)
    private val _isReworkDialogOpen = MutableStateFlow(false)
    private val _isManagerPinDialogOpen = MutableStateFlow(false)
    private val _pendingAction = MutableStateFlow<PendingManagerAction?>(null)
    private val _reworkReason = MutableStateFlow("Noda belum hilang")
    private val _pinErrorMessage = MutableStateFlow<String?>(null)
    private val _isActionLoading = MutableStateFlow(false)
    private val _successMessage = MutableStateFlow<String?>(null)
    private val _errorMessage = MutableStateFlow<String?>(null)
    private val _notificationStatus = MutableStateFlow("none")
    private val _notificationError = MutableStateFlow<String?>(null)
    private val _isCheckingNotification = MutableStateFlow(false)

    // Pelacak PIN salah (COR-07: 3x salah terkunci 5 menit)
    private var failedPinAttempts = 0
    private var isManagerLockedUntilMs = 0L

    private val _rawOrdersFlow: StateFlow<List<OrderWithLines>> = _filterStatus
        .flatMapLatest { filter ->
            repository.observeLaundryOrders(filter.dbStatus)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _ordersFlow: StateFlow<List<OrderWithLines>> = combine(
        _rawOrdersFlow,
        _searchQuery
    ) { orders, query ->
        val q = query.trim()
        if (q.isEmpty()) {
            orders
        } else {
            orders.filter { item ->
                item.order.displayNumber.contains(q, ignoreCase = true) ||
                    item.order.id.contains(q, ignoreCase = true)
            }
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    private val _selectedOrderAndCustomerFlow = combine(
        _ordersFlow,
        _selectedOrderId
    ) { orders, selectedId ->
        val selected = if (selectedId != null) {
            orders.find { it.order.id == selectedId }
        } else {
            orders.firstOrNull()
        }
        selected
    }.flatMapLatest { selected ->
        if (selected == null) {
            flowOf(Pair(null, null))
        } else {
            val customer = selected.order.customerId?.let { repository.getCustomerById(it) }
            flowOf(Pair(selected, customer))
        }
    }

    private val _pickupsFlow: StateFlow<List<LaundryPickupEntity>> = _selectedOrderId
        .flatMapLatest { orderId ->
            if (orderId == null) flowOf(emptyList())
            else repository.observePickups(orderId)
        }
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _dialogFlow = combine(
        _isPickupDialogOpen,
        _isReworkDialogOpen,
        _isManagerPinDialogOpen,
        _pendingAction,
        _reworkReason
    ) { pOpen, rOpen, pinOpen, action, reason ->
        DialogState(pOpen, rOpen, pinOpen, action, reason)
    }

    private val _statusMsgFlow = combine(
        _pinErrorMessage,
        _isActionLoading,
        _successMessage,
        _errorMessage,
        _notificationStatus,
        _notificationError,
        _isCheckingNotification
    ) { args: Array<Any?> ->
        StatusMessageState(
            pinErrorMessage = args[0] as String?,
            isActionLoading = args[1] as Boolean,
            successMessage = args[2] as String?,
            errorMessage = args[3] as String?,
            notificationStatus = args[4] as String,
            notificationError = args[5] as String?,
            isCheckingNotification = args[6] as Boolean
        )
    }

    val uiState: StateFlow<LaundryOrdersUiState> = combine(
        combine(_filterStatus, _ordersFlow) { filter, orders -> filter to orders },
        _selectedOrderAndCustomerFlow,
        _pickupsFlow,
        combine(_dialogFlow, _statusMsgFlow) { dialogs, statusMsg -> dialogs to statusMsg }
    ) { (filter, orders), (selectedOrder, customer), pickups, (dialogs, statusMsg) ->
        LaundryOrdersUiState(
            filterStatus = filter,
            orders = orders,
            selectedOrder = selectedOrder,
            customer = customer,
            pickups = pickups,
            isPickupDialogOpen = dialogs.isPickupDialogOpen,
            isReworkDialogOpen = dialogs.isReworkDialogOpen,
            isManagerPinDialogOpen = dialogs.isManagerPinDialogOpen,
            pendingAction = dialogs.pendingAction,
            reworkReason = dialogs.reworkReason,
            searchQuery = _searchQuery.value,
            pinErrorMessage = statusMsg.pinErrorMessage,
            isActionLoading = statusMsg.isActionLoading,
            successMessage = statusMsg.successMessage,
            errorMessage = statusMsg.errorMessage,
            notificationStatus = statusMsg.notificationStatus,
            notificationError = statusMsg.notificationError,
            isCheckingNotification = statusMsg.isCheckingNotification
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = LaundryOrdersUiState()
    )

    private data class DialogState(
        val isPickupDialogOpen: Boolean,
        val isReworkDialogOpen: Boolean,
        val isManagerPinDialogOpen: Boolean,
        val pendingAction: PendingManagerAction?,
        val reworkReason: String
    )

    private data class StatusMessageState(
        val pinErrorMessage: String?,
        val isActionLoading: Boolean,
        val successMessage: String?,
        val errorMessage: String?,
        val notificationStatus: String,
        val notificationError: String?,
        val isCheckingNotification: Boolean
    )

    fun setFilterStatus(filter: LaundryFilterStatus) {
        _filterStatus.value = filter
    }

    fun onSearchQueryChange(query: String) {
        _searchQuery.value = query
        val q = query.trim()
        if (q.isNotEmpty()) {
            viewModelScope.launch {
                val matched = repository.findOrderByNumberFlexible(q)
                if (matched != null) {
                    _selectedOrderId.value = matched.order.id
                }
            }
        }
    }

    fun selectOrderByScan(scannedPayload: String) {
        val trimmed = scannedPayload.trim()
        if (trimmed.isEmpty()) return
        viewModelScope.launch {
            // Coba dengan UUID atau nomor fleksibel
            var found = repository.getOrderWithLines(trimmed)
            if (found == null) {
                found = repository.findOrderByNumberFlexible(trimmed)
            }
            if (found != null) {
                _selectedOrderId.value = found.order.id
                _successMessage.value = "Pesanan ${found.order.displayNumber} berhasil ditemukan"
            } else {
                _errorMessage.value = "Pesanan dengan tag '$trimmed' tidak ditemukan"
            }
        }
    }

    fun advanceStatusToProcessing() {
        val current = uiState.value.selectedOrder?.order ?: return
        if (current.status.lowercase() != "open" && current.status.lowercase() != "received") return

        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.advanceOrderStatus(
                orderId = current.id,
                targetStatus = "processing"
            )
            _isActionLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Pesanan ${current.displayNumber} kini berstatus Diproses"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Gagal memproses status"
            }
        }
    }

    fun advanceStatusToReady() {
        val current = uiState.value.selectedOrder?.order ?: return
        if (current.status.lowercase() != "processing") return

        viewModelScope.launch {
            _isActionLoading.value = true
            val result = repository.advanceOrderStatus(
                orderId = current.id,
                targetStatus = "ready"
            )
            _isActionLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Pesanan ${current.displayNumber} siap diambil"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Gagal menandai siap"
            }
        }
    }

    fun selectOrder(order: OrderWithLines) {
        _selectedOrderId.value = order.order.id
        fetchNotificationStatus(order.order.id)
    }

    fun refreshNotificationStatus() {
        val current = _selectedOrderId.value ?: return
        fetchNotificationStatus(current)
    }

    fun fetchNotificationStatus(orderId: String) {
        viewModelScope.launch(ioDispatcher) {
            _isCheckingNotification.value = true
            val result = repository.getNotificationStatus(orderId)
            result.onSuccess { response ->
                _notificationStatus.value = response.effectiveStatus
                _notificationError.value = response.effectiveError
            }.onFailure { err ->
                _notificationStatus.value = "none"
                _notificationError.value = err.message
            }
            _isCheckingNotification.value = false
        }
    }

    fun buildWhatsAppMessage(orderWithLines: OrderWithLines, customer: CustomerEntity?): String {
        val customerName = customer?.name ?: "Pelanggan"
        val displayNumber = orderWithLines.order.displayNumber
        val totalFormatted = formatRupiah(orderWithLines.order.total)
        val paid = orderWithLines.order.paidAmount
        val remaining = (orderWithLines.order.total - paid).coerceAtLeast(0L)
        val statusText = if (remaining == 0L) "Lunas" else "Sisa Tagihan: ${formatRupiah(remaining)}"

        return when (orderWithLines.order.status.lowercase()) {
            "ready" -> {
                "Halo *$customerName*,\n\nCucian Anda dengan nomor nota *$displayNumber* sudah *SIAP DIAMBIL* di athialoundry.\n\nStatus Pembayaran: *$statusText*.\n\nMohon membawa nota fisik atau menunjukkan pesan ini saat mengambil cucian. Terima kasih telah mempercayakan cucian Anda kepada kami!"
            }
            "picked_up" -> {
                "Halo *$customerName*,\n\nPesanan cucian nomor nota *$displayNumber* telah berhasil *DIAMBIL* di athialoundry.\n\nTerima kasih atas kunjungan Anda dan sampai jumpa kembali!"
            }
            else -> {
                "Halo *$customerName*,\n\nTerima kasih telah mencuci di athialoundry.\nNomor Nota: *$displayNumber*\nTotal: *$totalFormatted*\nStatus: *$statusText*\n\nKami akan mengabari Anda kembali segera setelah cucian selesai dan siap diambil. Terima kasih!"
            }
        }
    }

    fun buildWhatsAppUrl(orderWithLines: OrderWithLines, customer: CustomerEntity?): String? {
        val rawPhone = customer?.phone?.takeIf { it.isNotBlank() } ?: return null
        val cleanDigits = rawPhone.filter { it.isDigit() }
        val normalizedPhone = when {
            cleanDigits.startsWith("62") -> cleanDigits
            cleanDigits.startsWith("08") -> "62" + cleanDigits.substring(1)
            else -> cleanDigits
        }
        val text = buildWhatsAppMessage(orderWithLines, customer)
        val encodedText = java.net.URLEncoder.encode(text, "UTF-8")
        return "https://api.whatsapp.com/send?phone=$normalizedPhone&text=$encodedText"
    }

    fun openPickupDialog() {
        _isPickupDialogOpen.value = true
    }

    fun closePickupDialog() {
        _isPickupDialogOpen.value = false
    }

    fun openReworkDialog() {
        _reworkReason.value = "Noda belum hilang"
        _isReworkDialogOpen.value = true
    }

    fun closeReworkDialog() {
        _isReworkDialogOpen.value = false
    }

    fun setReworkReason(reason: String) {
        _reworkReason.value = reason
    }

    fun closeManagerPinDialog() {
        _isManagerPinDialogOpen.value = false
        _pendingAction.value = null
        _pinErrorMessage.value = null
    }

    fun requestReworkWithPin(reason: String) {
        _isReworkDialogOpen.value = false
        _pendingAction.value = PendingManagerAction.Rework(reason)
        _isManagerPinDialogOpen.value = true
        _pinErrorMessage.value = null
    }

    fun submitPickup(items: List<LaundryPickupItem>) {
        val currentOrder = uiState.value.selectedOrder?.order ?: return
        val isUnpaid = currentOrder.paidAmount < currentOrder.total

        if (isUnpaid) {
            // F24: Wajib PIN Manager jika belum lunas
            _isPickupDialogOpen.value = false
            _pendingAction.value = PendingManagerAction.UnpaidPickup(items)
            _isManagerPinDialogOpen.value = true
            _pinErrorMessage.value = null
        } else {
            // Lunas: langsung eksekusi penyerahan tanpa PIN
            executePickup(currentOrder.id, items, approvedBy = null)
        }
    }

    fun submitAllItemsFinalPickup() {
        val current = uiState.value.selectedOrder ?: return
        val items = current.lines.map { line ->
            LaundryPickupItem(
                orderLineId = line.id,
                quantity = line.quantity,
                isFinal = true,
                note = "Penyerahan lengkap"
            )
        }
        submitPickup(items)
    }

    fun verifyManagerPinAndExecute(pin: String) {
        val now = System.currentTimeMillis()
        if (now < isManagerLockedUntilMs) {
            val remainingSec = ((isManagerLockedUntilMs - now) / 1000).coerceAtLeast(1)
            _pinErrorMessage.value = "Akun terkunci sementara ($remainingSec dtk)"
            return
        }

        val isCorrect = ManagerPinVerifier.verify(pin)
        if (!isCorrect) {
            failedPinAttempts++
            if (failedPinAttempts >= 3) {
                isManagerLockedUntilMs = now + (300 * 1000L) // 5 menit lockout
                _pinErrorMessage.value = "PIN salah 3x. Terkunci 5 menit (COR-07)"
            } else {
                _pinErrorMessage.value = "PIN Manager salah (${3 - failedPinAttempts}x tersisa)"
            }
            return
        }

        // PIN benar!
        failedPinAttempts = 0
        _pinErrorMessage.value = null
        val action = _pendingAction.value ?: return
        val currentOrder = uiState.value.selectedOrder?.order ?: return

        closeManagerPinDialog()

        when (action) {
            is PendingManagerAction.UnpaidPickup -> {
                executePickup(currentOrder.id, action.items, approvedBy = "u-manager-1")
            }
            is PendingManagerAction.Rework -> {
                executeRework(currentOrder.id, action.reason, approvedBy = "u-manager-1")
            }
            is PendingManagerAction.Refund -> {
                executeRefund(currentOrder.id, action.amount, action.reasonCode, action.note, approvedBy = "u-manager-1")
            }
        }
    }

    fun openRefundDialog(reasonCode: String = "quality_complaint", note: String? = null) {
        val current = uiState.value.selectedOrder?.order ?: return
        if (current.paidAmount <= 0L) {
            _errorMessage.value = "Pesanan ini belum memiliki pembayaran yang dapat di-refund"
            return
        }
        _pendingAction.value = PendingManagerAction.Refund(reasonCode, note, current.paidAmount)
        _isManagerPinDialogOpen.value = true
        _pinErrorMessage.value = null
    }

    private fun executeRefund(orderId: String, amount: Long, reasonCode: String, note: String?, approvedBy: String) {
        viewModelScope.launch {
            _isActionLoading.value = true
            val res = repository.recordCorrection(
                orderId = orderId,
                type = "refund",
                amount = amount,
                reasonCode = reasonCode,
                approvedBy = approvedBy,
                note = note
            )
            _isActionLoading.value = false
            if (res.isSuccess) {
                _successMessage.value = "Refund tunai berhasil diproses"
            } else {
                _errorMessage.value = res.exceptionOrNull()?.message ?: "Gagal memproses refund"
            }
        }
    }

    private fun executePickup(orderId: String, items: List<LaundryPickupItem>, approvedBy: String?) {
        viewModelScope.launch {
            _isActionLoading.value = true
            _isPickupDialogOpen.value = false

            val result = repository.recordPickups(
                orderId = orderId,
                items = items,
                approvedBy = approvedBy
            )

            _isActionLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Pengambilan pakaian berhasil dicatat"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Gagal mencatat pengambilan"
            }
        }
    }

    private fun executeRework(orderId: String, reason: String, approvedBy: String) {
        viewModelScope.launch {
            _isActionLoading.value = true

            val result = repository.reworkOrder(
                orderId = orderId,
                reason = reason,
                approvedBy = approvedBy
            )

            _isActionLoading.value = false
            if (result.isSuccess) {
                _successMessage.value = "Cucian berhasil dikembalikan ke status Diproses (Cuci Ulang)"
            } else {
                _errorMessage.value = result.exceptionOrNull()?.message ?: "Gagal memproses cuci ulang"
            }
        }
    }

    fun printLaundryTag(
        cashierOrStaffName: String = "Kasir Laundry",
        storeName: String = "ATHIA LAUNDRY HOUSE"
    ) {
        val current = uiState.value.selectedOrder ?: return
        val order = current.order
        val customer = uiState.value.customer

        if (printerService == null) {
            _errorMessage.value = "Printer tidak terhubung"
            return
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID"))
                val lines = current.lines.map { line ->
                    val isExpress = line.nameSnapshot.contains("Express", ignoreCase = true)
                    LaundryTagLine(
                        name = line.nameSnapshot,
                        quantityOrWeight = "${line.quantity}",
                        isExpress = isExpress,
                        note = line.note
                    )
                }

                val tagData = LaundryTagData(
                    orderId = order.id,
                    displayNumber = order.displayNumber,
                    customerName = customer?.name ?: "Pelanggan Athia",
                    customerPhone = customer?.phone,
                    receivedAt = dateFormat.format(Date(order.createdAt)),
                    estimatedCompletion = "Sesuai Nota",
                    isExpress = lines.any { it.isExpress },
                    lines = lines,
                    cashierOrStaffName = cashierOrStaffName,
                    storeName = storeName
                )

                val bytes = LaundryTagFormatter.format(tagData)
                printerService.print(bytes)
                _successMessage.value = "Tag cucian berhasil dikirim ke printer"
            } catch (e: Exception) {
                _errorMessage.value = "Gagal mencetak tag: ${e.message}"
            }
        }
    }

    fun dismissMessage() {
        _successMessage.value = null
        _errorMessage.value = null
    }
}
