package id.a3.feature.cafe.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.entity.OrderEntity
import id.a3.core.data.local.entity.OrderLineEntity
import id.a3.core.data.local.entity.OrderWithLines
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.core.domain.cafe.CafeCalculationInput
import id.a3.core.domain.cafe.CafeCalculationResult
import id.a3.core.domain.cafe.CafeCalculator
import id.a3.core.domain.cafe.CafeOrderLine
import id.a3.core.domain.cafe.OrderDiscount
import id.a3.core.domain.cafe.TaxMode
import id.a3.core.printer.PrinterService
import id.a3.core.printer.format.CustomerReceiptData
import id.a3.core.printer.format.CustomerReceiptFormatter
import id.a3.core.printer.format.CustomerReceiptLine
import id.a3.core.printer.format.KitchenTicketFormatter
import id.a3.core.printer.format.KitchenTicketItem
import id.a3.feature.cafe.model.AppliedDiscount
import id.a3.feature.cafe.model.CafeCartLine
import id.a3.feature.cafe.model.CafeOrderType
import id.a3.feature.cafe.model.CafeProduct
import id.a3.feature.cafe.repository.CafeMenuRepository
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class CafeOrderHeader(
    val orderId: String = UUID.randomUUID().toString(),
    val orderType: CafeOrderType = CafeOrderType.DINE_IN,
    val customerName: String = "Pelanggan",
    val tableOrQueueLabel: String = "Meja 01",
    val receiptId: String = "C1-0001"
) {
    val isDineIn: Boolean get() = orderType == CafeOrderType.DINE_IN
}

data class CafeMenuUiState(
    val products: List<CafeProduct> = emptyList(),
    val allProducts: List<CafeProduct> = emptyList(),
    val categories: List<String> = listOf("Semua", "Kopi", "Non-kopi", "Roti & Makanan"),
    val selectedCategory: String = "Semua",
    val searchQuery: String = "",
    val cartLines: List<CafeCartLine> = emptyList(),
    val orderHeader: CafeOrderHeader = CafeOrderHeader(),
    val discount: OrderDiscount? = null,
    val appliedDiscount: AppliedDiscount? = null,
    val isDiscountDialogOpen: Boolean = false,
    val isMoveTableDialogOpen: Boolean = false,
    val calculation: CafeCalculationResult = CafeCalculationResult(
        subtotal = 0L,
        discount = 0L,
        serviceCharge = 0L,
        tax = 0L,
        rounding = 0L,
        total = 0L
    ),
    val sheetProduct: CafeProduct? = null,
    val isLoading: Boolean = false
)

class CafeMenuViewModel(
    private val menuRepository: CafeMenuRepository,
    private val transactionManager: OrderTransactionManager? = null,
    val printerService: PrinterService? = null,
    private val ioDispatcher: CoroutineDispatcher = Dispatchers.IO
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("Semua")
    private val _searchQuery = MutableStateFlow("")
    private val _cartLines = MutableStateFlow<List<CafeCartLine>>(emptyList())
    private val _orderHeader = MutableStateFlow(CafeOrderHeader())
    private val _discount = MutableStateFlow<OrderDiscount?>(null)
    private val _appliedDiscount = MutableStateFlow<AppliedDiscount?>(null)
    private val _isDiscountDialogOpen = MutableStateFlow(false)
    private val _isMoveTableDialogOpen = MutableStateFlow(false)
    private val _sheetProduct = MutableStateFlow<CafeProduct?>(null)

    // Pengaturan persentase biaya layanan dan pajak (bawaan OQ-04 & OQ-05: 0% / mode ditambahkan)
    private val _servicePercent = MutableStateFlow(0)
    private val _taxPercent = MutableStateFlow(0)
    private val _taxMode = MutableStateFlow(TaxMode.ADDED)

    // Pelacak percobaan PIN manager salah (COR-07)
    private var failedPinAttempts = 0
    private var isManagerLockedUntilMs = 0L

    init {
        viewModelScope.launch {
            menuRepository.seedInitialMenuIfEmpty()
        }
    }

    private val _filterState = combine(_selectedCategory, _searchQuery) { cat, q -> cat to q }
    private val _cartState = combine(_cartLines, _discount, _orderHeader) { cart, disc, header ->
        Triple(cart, disc, header)
    }
    private val _dialogUiState = combine(_appliedDiscount, _isDiscountDialogOpen, _isMoveTableDialogOpen) { applied, isDiscOpen, isMoveOpen ->
        Triple(applied, isDiscOpen, isMoveOpen)
    }

    val uiState: StateFlow<CafeMenuUiState> = combine(
        menuRepository.observeProducts(),
        _filterState,
        _cartState,
        _dialogUiState,
        _sheetProduct
    ) { products, (category, query), (cart, discount, header), (appliedDisc, isDiscOpen, isMoveOpen), sheetProd ->
        val filtered = products.filter { prod ->
            val matchesCategory = (category == "Semua") || (prod.category.equals(category, ignoreCase = true))
            val matchesQuery = query.isBlank() || prod.name.contains(query.trim(), ignoreCase = true)
            matchesCategory && matchesQuery
        }

        val domainLines = cart.map { line ->
            CafeOrderLine(
                unitPrice = line.unitPrice,
                quantity = line.quantity
            )
        }

        val calculation = CafeCalculator.calculate(
            CafeCalculationInput(
                lines = domainLines,
                discount = discount,
                servicePercent = _servicePercent.value,
                taxPercent = _taxPercent.value,
                taxMode = _taxMode.value
            )
        )

        val dynamicCategories = listOf("Semua") + products.map { it.category }.distinct()

        CafeMenuUiState(
            products = filtered,
            allProducts = products,
            categories = dynamicCategories,
            selectedCategory = category,
            searchQuery = query,
            cartLines = cart,
            orderHeader = header,
            discount = discount,
            appliedDiscount = appliedDisc,
            isDiscountDialogOpen = isDiscOpen,
            isMoveTableDialogOpen = isMoveOpen,
            calculation = calculation,
            sheetProduct = sheetProd,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CafeMenuUiState(isLoading = true)
    )

    /**
     * Aliran data seluruh pesanan terbuka (open orders per meja) di cafe secara reaktif dari Room.
     */
    val openOrders: StateFlow<List<OrderWithLines>> = (transactionManager?.observeOpenOrders() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    /**
     * Aliran data seluruh pesanan yang sudah lunas/selesai secara reaktif dari Room.
     */
    val completedOrders: StateFlow<List<OrderWithLines>> = (transactionManager?.observeCompletedOrders() ?: kotlinx.coroutines.flow.flowOf(emptyList()))
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _isCheckingOut = MutableStateFlow(false)
    val isCheckingOut: StateFlow<Boolean> = _isCheckingOut.asStateFlow()

    fun setCheckingOut(checkingOut: Boolean) {
        _isCheckingOut.value = checkingOut
    }

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun onSearchQueryChanged(query: String) {
        _searchQuery.value = query
    }

    /**
     * Ketika kartu menu diketuk:
     * Seluruh menu (baik menu beropsi maupun standar) membuka C02 Modal Bottom Sheet untuk
     * verifikasi pesanan, pemilihan kuantitas, catatan barista/dapur, serta opsi varian/add-on
     * sebelum dimasukkan ke dalam struk belanja.
     */
    fun onProductClicked(product: CafeProduct) {
        if (product.soldOut) return
        _sheetProduct.value = product
    }

    fun dismissSheet() {
        _sheetProduct.value = null
    }

    fun addCartLine(line: CafeCartLine) {
        val current = _cartLines.value.toMutableList()
        // Cek apakah item yang identik (produk sama, varian sama, addon sama, catatan sama) sudah ada
        val existingIndex = current.indexOfFirst {
            it.product.id == line.product.id &&
                it.selectedVariant == line.selectedVariant &&
                it.selectedAddons == line.selectedAddons &&
                it.note == line.note
        }

        if (existingIndex >= 0) {
            val existing = current[existingIndex]
            current[existingIndex] = existing.copy(quantity = existing.quantity + line.quantity)
        } else {
            current.add(line)
        }
        _cartLines.value = current
        _sheetProduct.value = null
    }

    fun updateQuantity(lineId: String, delta: Int) {
        val current = _cartLines.value.toMutableList()
        val index = current.indexOfFirst { it.id == lineId }
        if (index >= 0) {
            val existing = current[index]
            val newQty = existing.quantity + delta
            if (newQty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = existing.copy(quantity = newQty)
            }
            _cartLines.value = current
        }
    }

    fun removeCartLine(lineId: String) {
        val current = _cartLines.value.toMutableList()
        current.removeAll { it.id == lineId }
        _cartLines.value = current
    }

    fun clearCart() {
        _cartLines.value = emptyList()
        _discount.value = null
        _appliedDiscount.value = null
        _orderHeader.value = CafeOrderHeader()
        _isCheckingOut.value = false
    }

    fun applyDiscount(discount: OrderDiscount?) {
        _discount.value = discount
        if (discount == null) {
            _appliedDiscount.value = null
        }
    }

    fun openDiscountDialog() {
        _isDiscountDialogOpen.value = true
    }

    fun dismissDiscountDialog() {
        _isDiscountDialogOpen.value = false
    }

    fun applyAppliedDiscount(applied: AppliedDiscount) {
        _appliedDiscount.value = applied
        _discount.value = applied.discount
        _isDiscountDialogOpen.value = false
    }

    fun removeDiscount() {
        _appliedDiscount.value = null
        _discount.value = null
        _isDiscountDialogOpen.value = false
    }

    suspend fun verifyManagerPin(pin: String): Boolean {
        val now = System.currentTimeMillis()
        if (now < isManagerLockedUntilMs) {
            return false
        }

        // Bawaan seed users.json role "manager" PIN: "4444"
        val isCorrect = pin == "4444"

        if (isCorrect) {
            failedPinAttempts = 0
            return true
        } else {
            failedPinAttempts++
            if (failedPinAttempts >= 3) {
                // Kunci 5 menit (300 detik) sesuai aturan COR-07
                isManagerLockedUntilMs = now + (300 * 1000L)
            }
            return false
        }
    }

    fun setOrderType(type: CafeOrderType) {
        val current = _orderHeader.value
        val defaultLabel = when (type) {
            CafeOrderType.DINE_IN -> if (current.tableOrQueueLabel.startsWith("Meja")) current.tableOrQueueLabel else "Meja 01"
            CafeOrderType.TAKE_AWAY -> "Bungkus"
            CafeOrderType.ORDER_ONLINE -> "Ojol / Online"
        }
        _orderHeader.value = current.copy(
            orderType = type,
            tableOrQueueLabel = defaultLabel
        )
    }

    fun setCustomerName(name: String) {
        _orderHeader.value = _orderHeader.value.copy(customerName = name)
    }

    fun setTableOrQueueLabel(label: String) {
        _orderHeader.value = _orderHeader.value.copy(tableOrQueueLabel = label)
    }

    fun setOrderHeader(isDineIn: Boolean, tableOrQueueLabel: String) {
        _orderHeader.value = _orderHeader.value.copy(
            orderType = if (isDineIn) CafeOrderType.DINE_IN else CafeOrderType.TAKE_AWAY,
            tableOrQueueLabel = tableOrQueueLabel
        )
    }

    fun openMoveTableDialog() {
        _isMoveTableDialogOpen.value = true
    }

    fun dismissMoveTableDialog() {
        _isMoveTableDialogOpen.value = false
    }

    fun moveTable(newTableLabel: String) {
        val currentHeader = _orderHeader.value
        val fromTable = currentHeader.tableOrQueueLabel
        if (fromTable == newTableLabel) {
            _isMoveTableDialogOpen.value = false
            return
        }
        _orderHeader.value = currentHeader.copy(tableOrQueueLabel = newTableLabel)
        _isMoveTableDialogOpen.value = false

        viewModelScope.launch {
            transactionManager?.recordTableMoved(
                orderId = currentHeader.orderId,
                fromTable = fromTable,
                toTable = newTableLabel
            )
        }
    }

    fun toggleProductSoldOut(productId: String, soldOut: Boolean) {
        viewModelScope.launch {
            menuRepository.setProductSoldOut(productId, soldOut)
        }
    }

    /**
     * Mengirim pesanan baru ke dapur (Dine In) dan menyimpannya sebagai pesanan terbuka (Open Order).
     * Mencatat event outbox 'order.created' dan 'order.sent_to_kitchen' secara atomik.
     */
    suspend fun sendOrderToKitchen(): Result<String> {
        val cart = _cartLines.value
        if (cart.isEmpty()) {
            return Result.failure(IllegalStateException("Keranjang masih kosong"))
        }
        val header = _orderHeader.value
        val calc = uiState.value.calculation

        val orderEntity = OrderEntity(
            id = header.orderId,
            displayNumber = header.receiptId,
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = header.orderType.name.lowercase(),
            tableLabel = header.tableOrQueueLabel,
            status = "open",
            subtotal = calc.subtotal,
            discount = calc.discount,
            serviceCharge = calc.serviceCharge,
            tax = calc.tax,
            rounding = calc.rounding,
            total = calc.total
        )

        val lineEntities = cart.map { line ->
            val optionsStr = (listOfNotNull(line.selectedVariant?.name) + line.selectedAddons.map { it.name }).joinToString(", ")
            val fullNote = listOfNotNull(optionsStr.ifBlank { null }, line.note.ifBlank { null }).joinToString(" | ")
            OrderLineEntity(
                id = line.id,
                orderId = header.orderId,
                itemRefId = line.product.id,
                nameSnapshot = line.product.name,
                quantity = line.quantity,
                unitPriceSnapshot = line.unitPrice,
                costSnapshot = 0L,
                note = fullNote.ifBlank { null }
            )
        }

        try {
            transactionManager?.createOpenOrderWithKitchenTicket(
                order = orderEntity,
                lines = lineEntities
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }

        // Susun item untuk tiket dapur dan cetak ke printer fisik 58mm (PRT-03: hanya item baru, tanpa harga)
        val kitchenItems = cart.map { line ->
            val optionsStr = (listOfNotNull(line.selectedVariant?.name) + line.selectedAddons.map { it.name }).joinToString(", ")
            KitchenTicketItem(
                name = line.product.name,
                quantity = line.quantity,
                optionsSummary = optionsStr.ifBlank { null },
                note = line.note.ifBlank { null }
            )
        }
        val currentTimeStr = SimpleDateFormat("HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).format(Date())
        val ticketBytes = KitchenTicketFormatter.format(
            orderDisplayNumber = header.receiptId,
            tableLabel = header.tableOrQueueLabel,
            occurredAt = currentTimeStr,
            items = kitchenItems,
            orderType = if (header.isDineIn) "DINE IN" else "TAKE AWAY"
        )
        // Cetak tiket dapur secara asynchronous di background worker agar tidak memblokir antarmuka kasir
        viewModelScope.launch(ioDispatcher) {
            try {
                printerService?.print(ticketBytes)
            } catch (_: Exception) {}
        }

        val sentOrderId = header.orderId

        // Bersihkan keranjang dan reset header untuk pesanan baru berikutnya
        _cartLines.value = emptyList()
        _discount.value = null
        _appliedDiscount.value = null
        _orderHeader.value = CafeOrderHeader()

        return Result.success(sentOrderId)
    }

    /**
     * Mempersiapkan order untuk pembayaran langsung di kasir (Dine In langsung bayar / Take Away).
     * Menyimpan order ke Room dan mencetak tiket dapur jika Dine In, tetapi TIDAK membersihkan keranjang.
     * Keranjang hanya dibersihkan saat pembayaran berhasil diselesaikan di PaymentScreen.
     */
    suspend fun directCheckout(calculation: CafeCalculationResult): Result<String> {
        val cart = _cartLines.value
        if (cart.isEmpty()) {
            return Result.failure(IllegalStateException("Keranjang masih kosong"))
        }
        val header = _orderHeader.value

        val orderEntity = OrderEntity(
            id = header.orderId,
            displayNumber = header.receiptId,
            businessUnitId = "bu-cafe-01",
            shiftId = "shift-001",
            deviceId = "dev-cafe-1",
            cashierId = "cashier-1",
            orderType = header.orderType.name.lowercase(),
            tableLabel = header.tableOrQueueLabel,
            status = "open",
            subtotal = calculation.subtotal,
            discount = calculation.discount,
            serviceCharge = calculation.serviceCharge,
            tax = calculation.tax,
            rounding = calculation.rounding,
            total = calculation.total
        )

        val lineEntities = cart.map { line ->
            val optionsStr = (listOfNotNull(line.selectedVariant?.name) + line.selectedAddons.map { it.name }).joinToString(", ")
            val fullNote = listOfNotNull(optionsStr.ifBlank { null }, line.note.ifBlank { null }).joinToString(" | ")
            OrderLineEntity(
                id = line.id,
                orderId = header.orderId,
                itemRefId = line.product.id,
                nameSnapshot = line.product.name,
                quantity = line.quantity,
                unitPriceSnapshot = line.unitPrice,
                costSnapshot = 0L,
                note = fullNote.ifBlank { null }
            )
        }

        try {
            transactionManager?.createOpenOrderWithKitchenTicket(
                order = orderEntity,
                lines = lineEntities
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }

        // Jika Dine-in, kirim tiket dapur di latar belakang
        if (header.isDineIn) {
            val kitchenItems = cart.map { line ->
                val optionsStr = (listOfNotNull(line.selectedVariant?.name) + line.selectedAddons.map { it.name }).joinToString(", ")
                KitchenTicketItem(
                    name = line.product.name,
                    quantity = line.quantity,
                    optionsSummary = optionsStr.ifBlank { null },
                    note = line.note.ifBlank { null }
                )
            }
            val currentTimeStr = SimpleDateFormat("HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).format(Date())
            val ticketBytes = KitchenTicketFormatter.format(
                orderDisplayNumber = header.receiptId,
                tableLabel = header.tableOrQueueLabel,
                occurredAt = currentTimeStr,
                items = kitchenItems,
                orderType = "DINE IN"
            )
            // Cetak tiket dapur secara asynchronous di background worker agar kasir langsung masuk ke pembayaran tanpa menunggu printer
            viewModelScope.launch(ioDispatcher) {
                try {
                    printerService?.print(ticketBytes)
                } catch (_: Exception) {}
            }
        }

        return Result.success(header.orderId)
    }

    /**
     * Mencetak ulang struk pelanggan dengan tanda *** SALINAN *** (PRT-05) dari riwayat pesanan yang sudah lunas.
     */
    suspend fun reprintCompletedOrderReceipt(orderWithLines: OrderWithLines): Result<Unit> {
        val printer = printerService ?: return Result.failure(IllegalStateException("Printer belum terhubung"))
        val order = orderWithLines.order
        val lines = orderWithLines.lines.map { line ->
            CustomerReceiptLine(
                name = line.nameSnapshot,
                quantity = line.quantity,
                unitPrice = line.unitPriceSnapshot,
                lineTotal = line.unitPriceSnapshot * line.quantity,
                optionsOrNote = line.note
            )
        }
        val dateStr = SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.forLanguageTag("id-ID")).format(Date(order.createdAt))
        val receiptData = CustomerReceiptData(
            orderDisplayNumber = order.displayNumber,
            tableOrQueueLabel = order.tableLabel,
            occurredAt = dateStr,
            lines = lines,
            subtotal = order.subtotal,
            discount = order.discount,
            serviceCharge = order.serviceCharge,
            tax = order.tax,
            rounding = order.rounding,
            total = order.total,
            paymentMethodName = "Lunas",
            isCopy = true
        )
        val bytes = CustomerReceiptFormatter.format(receiptData)
        return withContext(Dispatchers.IO) {
            printer.print(bytes)
        }
    }

    /**
     * Menambahkan item tambahan ke pesanan terbuka yang sudah ada di dapur.
     * Mencatat event outbox 'order.items_added' dan 'order.sent_to_kitchen' (hanya baris baru).
     */
    suspend fun appendItemsToOpenOrder(
        orderId: String,
        newItems: List<CafeCartLine>
    ): Result<Unit> {
        if (newItems.isEmpty()) {
            return Result.failure(IllegalStateException("Tidak ada item baru untuk ditambahkan"))
        }

        val existing = transactionManager?.getOrderWithLines(orderId)
            ?: return Result.failure(IllegalStateException("Pesanan tidak ditemukan"))

        val newLineEntities = newItems.map { line ->
            val optionsStr = (listOfNotNull(line.selectedVariant?.name) + line.selectedAddons.map { it.name }).joinToString(", ")
            val fullNote = listOfNotNull(optionsStr.ifBlank { null }, line.note.ifBlank { null }).joinToString(" | ")
            OrderLineEntity(
                id = line.id,
                orderId = orderId,
                itemRefId = line.product.id,
                nameSnapshot = line.product.name,
                quantity = line.quantity,
                unitPriceSnapshot = line.unitPrice,
                costSnapshot = 0L,
                note = fullNote.ifBlank { null }
            )
        }

        // Hitung ulang total gabungan seluruh pesanan (item lama + item baru)
        val allLines = existing.lines.map {
            CafeOrderLine(unitPrice = it.unitPriceSnapshot, quantity = it.quantity)
        } + newItems.map {
            CafeOrderLine(unitPrice = it.unitPrice, quantity = it.quantity)
        }

        val calculation = CafeCalculator.calculate(
            CafeCalculationInput(
                lines = allLines,
                discount = null,
                servicePercent = _servicePercent.value,
                taxPercent = _taxPercent.value,
                taxMode = _taxMode.value
            )
        )

        try {
            transactionManager.addItemsToOpenOrder(
                orderId = orderId,
                newLines = newLineEntities,
                newSubtotal = calculation.subtotal,
                newDiscount = calculation.discount,
                newServiceCharge = calculation.serviceCharge,
                newTax = calculation.tax,
                newRounding = calculation.rounding,
                newTotal = calculation.total
            )
        } catch (e: Exception) {
            return Result.failure(e)
        }

        // Cetak tiket dapur HANYA untuk item baru (PRT-03 & CAF-07)
        val newKitchenItems = newItems.map { line ->
            val optionsStr = (listOfNotNull(line.selectedVariant?.name) + line.selectedAddons.map { it.name }).joinToString(", ")
            KitchenTicketItem(
                name = line.product.name,
                quantity = line.quantity,
                optionsSummary = optionsStr.ifBlank { null },
                note = line.note.ifBlank { null }
            )
        }
        val currentTimeStr = SimpleDateFormat("HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).format(Date())
        val ticketBytes = KitchenTicketFormatter.format(
            orderDisplayNumber = existing.order.displayNumber,
            tableLabel = existing.order.tableLabel ?: "Meja",
            occurredAt = currentTimeStr,
            items = newKitchenItems,
            orderType = "DINE IN (TAMBAHAN)"
        )
        try {
            printerService?.print(ticketBytes)
        } catch (_: Exception) {}

        return Result.success(Unit)
    }

    /**
     * Mencetak ulang tiket dapur untuk pesanan terbuka yang sedang aktif.
     */
    suspend fun reprintKitchenTicket(orderId: String): Result<Unit> {
        val existing = transactionManager?.getOrderWithLines(orderId)
            ?: return Result.failure(IllegalStateException("Pesanan tidak ditemukan"))

        val items = existing.lines.map { line ->
            KitchenTicketItem(
                name = line.nameSnapshot,
                quantity = line.quantity,
                optionsSummary = null,
                note = line.note
            )
        }

        val currentTimeStr = SimpleDateFormat("HH:mm 'WIB'", Locale.forLanguageTag("id-ID")).format(Date())
        val ticketBytes = KitchenTicketFormatter.format(
            orderDisplayNumber = existing.order.displayNumber,
            tableLabel = existing.order.tableLabel ?: "Meja",
            occurredAt = currentTimeStr,
            items = items,
            orderType = "DINE IN (SALINAN)"
        )

        return printerService?.print(ticketBytes) ?: Result.success(Unit)
    }

    fun configureTaxAndService(servicePercent: Int, taxPercent: Int, taxMode: TaxMode = TaxMode.ADDED) {
        _servicePercent.value = servicePercent
        _taxPercent.value = taxPercent
        _taxMode.value = taxMode
    }
}
