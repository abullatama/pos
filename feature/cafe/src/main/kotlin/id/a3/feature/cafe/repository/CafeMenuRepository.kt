package id.a3.feature.cafe.repository

import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.entity.MenuItemEntity
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.feature.cafe.model.CafeProduct
import id.a3.feature.cafe.model.MenuOptionItem
import id.a3.feature.cafe.model.OptionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

interface CafeMenuRepository {
    fun observeProducts(): Flow<List<CafeProduct>>
    suspend fun getProductById(id: String): CafeProduct?
    suspend fun seedInitialMenuIfEmpty()
    suspend fun setProductSoldOut(id: String, soldOut: Boolean)
}

class DefaultCafeMenuRepository(
    private val masterDataDao: MasterDataDao,
    private val transactionManager: OrderTransactionManager? = null
) : CafeMenuRepository {

    companion object {
        val INITIAL_MENU_CATALOG = listOf(
            CafeProduct(
                id = "menu-latte",
                name = "Caffe Latte",
                category = "Kopi",
                basePrice = 34000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Panas", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Dingin", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Shot Tambahan", priceDelta = 6000L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Susu Oat (Oat Milk)", priceDelta = 8000L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Sirup Vanila", priceDelta = 5000L)
                )
            ),
            CafeProduct(
                id = "menu-cappuccino",
                name = "Cappuccino",
                category = "Kopi",
                basePrice = 32000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Panas", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Dingin", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Shot Tambahan", priceDelta = 6000L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Taburan Kayu Manis", priceDelta = 2000L)
                )
            ),
            CafeProduct(
                id = "menu-americano",
                name = "Americano",
                category = "Kopi",
                basePrice = 26000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Panas", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Dingin", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Extra Shot", priceDelta = 6000L)
                )
            ),
            CafeProduct(
                id = "menu-espresso",
                name = "Single Espresso",
                category = "Kopi",
                basePrice = 18000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Double Shot", priceDelta = 6000L)
                )
            ),
            CafeProduct(
                id = "menu-caramel-macchiato",
                name = "Caramel Macchiato",
                category = "Kopi",
                basePrice = 38000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Panas", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Dingin", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Extra Caramel Drizzle", priceDelta = 4000L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Susu Oat", priceDelta = 8000L)
                )
            ),
            CafeProduct(
                id = "menu-matcha",
                name = "Matcha Uji Latte",
                category = "Non-kopi",
                basePrice = 36000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Panas", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Dingin", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Susu Oat", priceDelta = 8000L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Dirty Espresso Shot", priceDelta = 6000L)
                )
            ),
            CafeProduct(
                id = "menu-teh",
                name = "Teh Tarik Aceh",
                category = "Non-kopi",
                basePrice = 22000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Panas", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Dingin", priceDelta = 0L)
                )
            ),
            CafeProduct(
                id = "menu-chocolate",
                name = "Signature Dark Chocolate",
                category = "Non-kopi",
                basePrice = 30000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Panas", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.VARIANT, name = "Dingin", priceDelta = 0L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Melted Marshmallow", priceDelta = 5000L)
                )
            ),
            CafeProduct(
                id = "menu-croissant",
                name = "French Butter Croissant",
                category = "Roti & Makanan",
                basePrice = 28000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Selai Hazelnut Cokelat", priceDelta = 5000L),
                    MenuOptionItem(kind = OptionKind.ADDON, name = "French Butter Tambahan", priceDelta = 4000L)
                )
            ),
            CafeProduct(
                id = "menu-chocolat",
                name = "Pain au Chocolat",
                category = "Roti & Makanan",
                basePrice = 32000L,
                options = emptyList()
            ),
            CafeProduct(
                id = "menu-banana-bread",
                name = "Warm Artisan Banana Bread",
                category = "Roti & Makanan",
                basePrice = 24000L,
                options = emptyList()
            ),
            CafeProduct(
                id = "menu-truffle-fries",
                name = "Truffle Parmesan Fries",
                category = "Roti & Makanan",
                basePrice = 35000L,
                options = listOf(
                    MenuOptionItem(kind = OptionKind.ADDON, name = "Extra Truffle Aioli Dip", priceDelta = 6000L)
                )
            )
        )
    }

    override suspend fun seedInitialMenuIfEmpty() {
        val sample = masterDataDao.getMenuItem("menu-latte")
        if (sample == null) {
            val entities = INITIAL_MENU_CATALOG.map { prod ->
                MenuItemEntity(
                    id = prod.id,
                    name = prod.name,
                    category = prod.category,
                    price = prod.basePrice,
                    active = true,
                    soldOut = prod.soldOut
                )
            }
            masterDataDao.upsertMenuItems(entities)
        }
    }

    override fun observeProducts(): Flow<List<CafeProduct>> {
        return masterDataDao.observeActiveMenuItems().map { entities ->
            if (entities.isEmpty()) {
                INITIAL_MENU_CATALOG
            } else {
                entities.map { entity ->
                    val defaultItem = INITIAL_MENU_CATALOG.find { it.id == entity.id }
                    CafeProduct(
                        id = entity.id,
                        name = entity.name,
                        category = entity.category,
                        basePrice = entity.price,
                        soldOut = entity.soldOut,
                        options = defaultItem?.options ?: emptyList()
                    )
                }
            }
        }
    }

    override suspend fun getProductById(id: String): CafeProduct? {
        val entity = masterDataDao.getMenuItem(id)
        if (entity != null) {
            val defaultItem = INITIAL_MENU_CATALOG.find { it.id == entity.id }
            return CafeProduct(
                id = entity.id,
                name = entity.name,
                category = entity.category,
                basePrice = entity.price,
                soldOut = entity.soldOut,
                options = defaultItem?.options ?: emptyList()
            )
        }
        return INITIAL_MENU_CATALOG.find { it.id == id }
    }

    override suspend fun setProductSoldOut(id: String, soldOut: Boolean) {
        if (transactionManager != null) {
            transactionManager.recordSoldOutChanged(id, soldOut)
        } else {
            masterDataDao.updateMenuItemSoldOut(id, soldOut)
        }
    }
}
