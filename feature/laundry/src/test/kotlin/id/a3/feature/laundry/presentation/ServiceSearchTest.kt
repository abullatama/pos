package id.a3.feature.laundry.presentation

import id.a3.core.data.local.entity.LaundryServiceEntity
import org.junit.Assert.assertEquals
import org.junit.Test

class ServiceSearchTest {
    private fun service(id: String, name: String) = LaundryServiceEntity(
        id = id,
        name = name,
        unit = "pcs",
        rate = 10_000L
    )

    private val all = listOf(
        service("1", "Cuci + Setrika Reguler"),
        service("2", "Cuci + Setrika Reguler (Express)"),
        service("3", "Bedcover"),
        service("4", "Sepatu"),
        service("5", "Jas")
    )

    @Test
    fun `kata kunci kosong menampilkan semua layanan`() {
        assertEquals(all, filterServicesByQuery(all, ""))
        assertEquals(all, filterServicesByQuery(all, "   "))
    }

    @Test
    fun `pencarian tidak membedakan huruf besar kecil`() {
        assertEquals(listOf("3"), filterServicesByQuery(all, "BEDCOVER").map { it.id })
    }

    @Test
    fun `sebagian nama cukup`() {
        assertEquals(listOf("4"), filterServicesByQuery(all, "sepa").map { it.id })
    }

    @Test
    fun `beberapa kata semuanya harus cocok`() {
        assertEquals(listOf("2"), filterServicesByQuery(all, "cuci express").map { it.id })
        assertEquals(listOf("1", "2"), filterServicesByQuery(all, "setrika cuci").map { it.id })
    }

    @Test
    fun `tidak ada yang cocok menghasilkan daftar kosong`() {
        assertEquals(emptyList<LaundryServiceEntity>(), filterServicesByQuery(all, "karpet"))
    }
}
