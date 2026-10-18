package id.a3.feature.laundry.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.entity.CustomerEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.printer.PrinterService
import id.a3.core.printer.format.LaundryTagData
import id.a3.core.printer.format.LaundryTagFormatter
import id.a3.core.printer.format.LaundryTagLine
import id.a3.feature.laundry.repository.LaundryOrderRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

data class LaundryScanUiState(
    val scannedOrder: OrderWithLines? = null,
    val customer: CustomerEntity? = null,
    val searchQuery: String = "",
    val isSearching: Boolean = false,
    val statusMessage: String? = null,
    val errorMessage: String? = null,
    val isActionLoading: Boolean = false
) {
    val currentStatus: String get() = scannedOrder?.order?.status?.lowercase().orEmpty()
    val canAdvance: Boolean get() = currentStatus in listOf("open", "received", "processing")
    val nextStatusLabel: String get() = when (currentStatus) {
        "open", "received" -> "Mulai Cuci (Tandai Diproses)"
        "processing" -> "Selesai Cuci (Tandai Siap)"
        "ready" -> "Cucian Siap Diambil"
        "picked_up" -> "Sudah Diambil"
        else -> "Perbarui Status"
    }
}

class LaundryScanViewModel(
    private val repository: LaundryOrderRepository,
    private val printerService: PrinterService? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _uiState = MutableStateFlow(LaundryScanUiState())
    val uiState: StateFlow<LaundryScanUiState> = _uiState.asStateFlow()

    fun onSearchQueryChange(query: String) {
        _uiState.update { it.copy(searchQuery = query, errorMessage = null) }
    }

    fun onScanResult(rawPayload: String) {
        val trimmed = rawPayload.trim()
        if (trimmed.isEmpty()) return
        lookupOrder(trimmed)
    }

    fun searchByManualQuery() {
        val query = _uiState.value.searchQuery.trim()
        if (query.isEmpty()) return
        lookupOrder(query)
    }

    private fun lookupOrder(identifier: String) {
        viewModelScope.launch {
            _uiState.update { it.copy(isSearching = true, errorMessage = null, statusMessage = null) }

            // 1. Coba cari dengan UUID
            var orderWithLines = repository.getOrderWithLines(identifier)

            // 2. Jika tidak ditemukan, coba cari dengan nomor tampil (misal L1-0012)
            if (orderWithLines == null) {
                orderWithLines = repository.getOrderWithLinesByDisplayNumber(identifier)
            }

            if (orderWithLines == null) {
                _uiState.update {
                    it.copy(
                        isSearching = false,
                        errorMessage = "Pesanan dengan kode '$identifier' tidak ditemukan",
                        scannedOrder = null,
                        customer = null
                    )
                }
            } else {
                val customer = orderWithLines.order.customerId?.let { custId ->
                    repository.getCustomerById(custId)
                }

                _uiState.update {
                    it.copy(
                        isSearching = false,
                        scannedOrder = orderWithLines,
                        customer = customer,
                        errorMessage = null
                    )
                }
            }
        }
    }

    fun advanceStatus() {
        val currentOrder = _uiState.value.scannedOrder?.order ?: return
        val currentStatus = currentOrder.status.lowercase()

        val targetStatus = when (currentStatus) {
            "open", "received" -> "processing"
            "processing" -> "ready"
            else -> return
        }

        viewModelScope.launch {
            _uiState.update { it.copy(isActionLoading = true, errorMessage = null) }

            val result = repository.advanceOrderStatus(
                orderId = currentOrder.id,
                targetStatus = targetStatus
            )

            if (result.isSuccess) {
                // Muat ulang order terbaru dari Room
                val updatedOrder = repository.getOrderWithLines(currentOrder.id)
                val statusNameIndo = if (targetStatus == "processing") "DIPROSES" else "SIAP DIAMBIL"

                _uiState.update {
                    it.copy(
                        isActionLoading = false,
                        scannedOrder = updatedOrder,
                        statusMessage = "Status pesanan ${currentOrder.displayNumber} berhasil diperbarui menjadi $statusNameIndo"
                    )
                }
            } else {
                _uiState.update {
                    it.copy(
                        isActionLoading = false,
                        errorMessage = result.exceptionOrNull()?.message ?: "Gagal memperbarui status cucian"
                    )
                }
            }
        }
    }

    fun printLaundryTag() {
        val current = _uiState.value.scannedOrder ?: return
        val order = current.order
        val customer = _uiState.value.customer

        if (printerService == null) {
            _uiState.update { it.copy(errorMessage = "Printer tidak terhubung") }
            return
        }

        viewModelScope.launch(ioDispatcher) {
            try {
                val dateFormat = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID"))
                val receivedAtStr = dateFormat.format(Date(order.createdAt))

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
                    receivedAt = receivedAtStr,
                    estimatedCompletion = "Sesuai Nota",
                    isExpress = lines.any { it.isExpress },
                    lines = lines,
                    cashierOrStaffName = "Staf Laundry"
                )

                val bytes = LaundryTagFormatter.format(tagData)
                printerService.print(bytes)
                _uiState.update { it.copy(statusMessage = "Tag cucian berhasil dikirim ke printer") }
            } catch (e: Exception) {
                _uiState.update { it.copy(errorMessage = "Gagal mencetak tag: ${e.message}") }
            }
        }
    }

    fun clearScannedOrder() {
        _uiState.update {
            it.copy(
                scannedOrder = null,
                customer = null,
                searchQuery = "",
                statusMessage = null,
                errorMessage = null
            )
        }
    }

    fun dismissMessage() {
        _uiState.update { it.copy(statusMessage = null, errorMessage = null) }
    }
}
