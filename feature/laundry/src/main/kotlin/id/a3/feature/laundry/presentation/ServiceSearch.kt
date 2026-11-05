package id.a3.feature.laundry.presentation

import id.a3.core.data.local.entity.LaundryServiceEntity

/**
 * Menyaring daftar layanan menurut kata kunci pencarian di menu Order.
 * Setiap kata pada kata kunci harus ada di nama layanan (huruf besar atau kecil tidak berpengaruh),
 * sehingga "cuci express" menemukan "Cuci + Setrika Reguler (Express)". Urutan asli dipertahankan.
 */
internal fun filterServicesByQuery(
    services: List<LaundryServiceEntity>,
    query: String
): List<LaundryServiceEntity> {
    val tokens = query.lowercase().trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    if (tokens.isEmpty()) return services
    return services.filter { service ->
        val name = service.name.lowercase()
        tokens.all { it in name }
    }
}
