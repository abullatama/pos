package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Entitas Room untuk opsi menu (varian dan add-on) AA Coffeeshop.
 * Bersumber dari seksi master data 'menu_options' pada backend server.
 *
 * @param id ID unik opsi dari server (String / Int)
 * @param menuItemId ID item menu induk (relasi foreign key logis ke menu_items.id)
 * @param kind Jenis opsi: "variant" (pilihan tunggal) atau "addon" (pilihan jamak)
 * @param name Nama tampilan opsi (misal: "Large", "Extra Shot Espresso", "Oat Milk")
 * @param priceDelta Selisih harga dalam rupiah penuh (misal: 5000L)
 * @param active Status ketersediaan opsi
 */
@Entity(
    tableName = "menu_options",
    indices = [Index(value = ["menuItemId"])]
)
data class MenuOptionEntity(
    @PrimaryKey
    val id: String,
    val menuItemId: String,
    val kind: String, // "variant" or "addon"
    val name: String,
    val priceDelta: Long = 0L,
    val active: Boolean = true
)
