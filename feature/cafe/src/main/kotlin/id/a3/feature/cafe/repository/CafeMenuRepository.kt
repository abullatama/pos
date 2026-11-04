package id.a3.feature.cafe.repository

import id.a3.core.data.local.dao.MasterDataDao
import id.a3.core.data.local.dao.SyncStateDao
import id.a3.core.data.local.entity.MenuItemEntity
import id.a3.core.data.repository.OrderTransactionManager
import id.a3.core.domain.cafe.DiscountType
import id.a3.core.domain.cafe.TaxMode
import id.a3.feature.cafe.model.CafeProduct
import id.a3.feature.cafe.model.DiscountPreset
import id.a3.feature.cafe.model.MenuOptionItem
import id.a3.feature.cafe.model.OptionKind
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map

data class TaxAndServiceSettings(
    val taxPercent: Int = 0,
    val taxMode: TaxMode = TaxMode.ADDED,
    val servicePercent: Int = 0
)

interface CafeMenuRepository {
    fun observeProducts(): Flow<List<CafeProduct>>
    fun observeDiscountPresets(): Flow<List<DiscountPreset>>
    fun observeTaxAndService(): Flow<TaxAndServiceSettings>
    suspend fun getProductById(id: String): CafeProduct?
    suspend fun seedInitialMenuIfEmpty()
    suspend fun setProductSoldOut(id: String, soldOut: Boolean)
}

class DefaultCafeMenuRepository(
    private val masterDataDao: MasterDataDao,
    private val transactionManager: OrderTransactionManager? = null,
    private val syncStateDao: SyncStateDao? = null
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
        // Mode produksi / live server: tidak menyuntikkan menu dummy. Seluruh menu murni berasal dari server.
    }

    override fun observeProducts(): Flow<List<CafeProduct>> {
        return combine(
            masterDataDao.observeActiveMenuItems(),
            masterDataDao.observeAllActiveMenuOptions()
        ) { menuEntities, optionEntities ->
            val optionsByMenuItem = optionEntities.groupBy { it.menuItemId }
            menuEntities.map { entity ->
                val options = optionsByMenuItem[entity.id].orEmpty().map { optEntity ->
                    MenuOptionItem(
                        id = optEntity.id,
                        kind = if (optEntity.kind.equals("variant", ignoreCase = true) || optEntity.kind.equals("varian", ignoreCase = true)) {
                            OptionKind.VARIANT
                        } else {
                            OptionKind.ADDON
                        },
                        name = optEntity.name,
                        priceDelta = optEntity.priceDelta
                    )
                }
                CafeProduct(
                    id = entity.id,
                    name = entity.name,
                    category = entity.category,
                    basePrice = entity.price,
                    soldOut = entity.soldOut,
                    options = options
                )
            }
        }
    }

    override fun observeDiscountPresets(): Flow<List<DiscountPreset>> {
        return masterDataDao.observeActiveDiscountPresets().map { list ->
            list.map { entity ->
                val type = if (entity.type.equals("amount", ignoreCase = true) || entity.type.equals("nominal", ignoreCase = true)) {
                    DiscountType.AMOUNT
                } else {
                    DiscountType.PERCENT
                }
                DiscountPreset(
                    id = entity.id,
                    name = entity.name,
                    type = type,
                    value = entity.value
                )
            }
        }
    }

    override fun observeTaxAndService(): Flow<TaxAndServiceSettings> {
        val dao = syncStateDao ?: return kotlinx.coroutines.flow.flowOf(TaxAndServiceSettings())
        return combine(
            dao.observe("tax_percent"),
            dao.observe("tax_mode"),
            dao.observe("service_charge_percent")
        ) { taxStr, modeStr, serviceStr ->
            val taxPercent = taxStr?.toIntOrNull() ?: 0
            val taxMode = if (modeStr?.equals("included", ignoreCase = true) == true) TaxMode.INCLUDED else TaxMode.ADDED
            val servicePercent = serviceStr?.toIntOrNull() ?: 0
            TaxAndServiceSettings(
                taxPercent = taxPercent,
                taxMode = taxMode,
                servicePercent = servicePercent
            )
        }
    }

    override suspend fun getProductById(id: String): CafeProduct? {
        val entity = masterDataDao.getMenuItem(id) ?: return null
        val options = masterDataDao.getActiveOptionsForMenuItem(id).map { optEntity ->
            MenuOptionItem(
                id = optEntity.id,
                kind = if (optEntity.kind.equals("variant", ignoreCase = true) || optEntity.kind.equals("varian", ignoreCase = true)) {
                    OptionKind.VARIANT
                } else {
                    OptionKind.ADDON
                },
                name = optEntity.name,
                priceDelta = optEntity.priceDelta
            )
        }
        return CafeProduct(
            id = entity.id,
            name = entity.name,
            category = entity.category,
            basePrice = entity.price,
            soldOut = entity.soldOut,
            options = options
        )
    }

    override suspend fun setProductSoldOut(id: String, soldOut: Boolean) {
        // Mode Sementara (Opsi B2): Hanya perbarui ketersediaan menu di Room database lokal.
        // Penulisan event outbox 'menu.sold_out_changed' dinonaktifkan sementara karena server backend
        // belum mendaftarkan tipe event tersebut pada POST /v1/sync/bundles.
        masterDataDao.updateMenuItemSoldOut(id, soldOut)
    }
}
