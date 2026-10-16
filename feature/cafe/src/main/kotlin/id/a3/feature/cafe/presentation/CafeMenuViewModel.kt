package id.a3.feature.cafe.presentation

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import id.a3.core.data.local.entity.MenuItemEntity
import id.a3.core.domain.cafe.CafeCalculationInput
import id.a3.core.domain.cafe.CafeCalculationResult
import id.a3.core.domain.cafe.CafeCalculator
import id.a3.core.domain.cafe.CafeOrderLine
import id.a3.core.domain.cafe.OrderDiscount
import id.a3.core.domain.cafe.TaxMode
import id.a3.feature.cafe.repository.CafeMenuRepository
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class CartItem(
    val menuItem: MenuItemEntity,
    val quantity: Int = 1,
    val note: String = ""
)

data class CafeMenuUiState(
    val menuItems: List<MenuItemEntity> = emptyList(),
    val selectedCategory: String = "Semua",
    val cartItems: List<CartItem> = emptyList(),
    val calculation: CafeCalculationResult = CafeCalculationResult(
        subtotal = 0L,
        discount = 0L,
        serviceCharge = 0L,
        tax = 0L,
        rounding = 0L,
        total = 0L
    ),
    val isLoading: Boolean = false
)

class CafeMenuViewModel(
    private val menuRepository: CafeMenuRepository
) : ViewModel() {

    private val _selectedCategory = MutableStateFlow("Semua")
    private val _cartItems = MutableStateFlow<List<CartItem>>(emptyList())
    private val _discount = MutableStateFlow<OrderDiscount?>(null)
    private val _servicePercent = MutableStateFlow(0) // 0% bawaan OQ-05
    private val _taxPercent = MutableStateFlow(0)
    private val _taxMode = MutableStateFlow(TaxMode.ADDED)

    val uiState: StateFlow<CafeMenuUiState> = combine(
        menuRepository.observeMenuItems(),
        _selectedCategory,
        _cartItems,
        _discount
    ) { items, category, cart, discount ->
        val filtered = if (category == "Semua") {
            items
        } else {
            items.filter { it.category == category }
        }

        val domainLines = cart.map {
            CafeOrderLine(
                unitPrice = it.menuItem.price,
                quantity = it.quantity
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

        CafeMenuUiState(
            menuItems = filtered,
            selectedCategory = category,
            cartItems = cart,
            calculation = calculation,
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = CafeMenuUiState(isLoading = true)
    )

    fun selectCategory(category: String) {
        _selectedCategory.value = category
    }

    fun addToCart(item: MenuItemEntity) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.menuItem.id == item.id }
        if (index >= 0) {
            val existing = current[index]
            current[index] = existing.copy(quantity = existing.quantity + 1)
        } else {
            current.add(CartItem(menuItem = item, quantity = 1))
        }
        _cartItems.value = current
    }

    fun updateQuantity(itemId: String, delta: Int) {
        val current = _cartItems.value.toMutableList()
        val index = current.indexOfFirst { it.menuItem.id == itemId }
        if (index >= 0) {
            val existing = current[index]
            val newQty = existing.quantity + delta
            if (newQty <= 0) {
                current.removeAt(index)
            } else {
                current[index] = existing.copy(quantity = newQty)
            }
            _cartItems.value = current
        }
    }

    fun clearCart() {
        _cartItems.value = emptyList()
    }
}
