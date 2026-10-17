package id.a3.feature.cafe.model

import java.util.UUID

enum class OptionKind {
    VARIANT,
    ADDON
}

enum class CafeOrderType(val label: String) {
    DINE_IN("Dine In"),
    TAKE_AWAY("Take Away"),
    ORDER_ONLINE("Order Online")
}

data class MenuOptionItem(
    val id: String = UUID.randomUUID().toString(),
    val kind: OptionKind,
    val name: String,
    val priceDelta: Long = 0L
)

data class CafeProduct(
    val id: String,
    val name: String,
    val category: String,
    val basePrice: Long,
    val soldOut: Boolean = false,
    val options: List<MenuOptionItem> = emptyList()
) {
    val variants: List<MenuOptionItem>
        get() = options.filter { it.kind == OptionKind.VARIANT }

    val addons: List<MenuOptionItem>
        get() = options.filter { it.kind == OptionKind.ADDON }
}

data class CafeCartLine(
    val id: String = UUID.randomUUID().toString(),
    val product: CafeProduct,
    val selectedVariant: MenuOptionItem? = null,
    val selectedAddons: List<MenuOptionItem> = emptyList(),
    val note: String = "",
    val quantity: Int = 1
) {
    val unitPrice: Long
        get() = product.basePrice +
            (selectedVariant?.priceDelta ?: 0L) +
            selectedAddons.sumOf { it.priceDelta }

    val lineTotal: Long
        get() = unitPrice * quantity.toLong()

    val optionsDescription: String
        get() {
            val parts = mutableListOf<String>()
            if (selectedVariant != null) {
                parts.add(selectedVariant.name)
            }
            selectedAddons.forEach {
                val deltaStr = if (it.priceDelta > 0) " (+Rp ${it.priceDelta})" else ""
                parts.add("${it.name}$deltaStr")
            }
            return parts.joinToString(" · ")
        }
}
