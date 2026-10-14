package id.a3.core.domain

import id.a3.core.domain.cafe.CafeCalculationInput
import id.a3.core.domain.cafe.CafeCalculator
import id.a3.core.domain.cafe.CafeOrderLine
import id.a3.core.domain.cafe.DiscountType
import id.a3.core.domain.cafe.OrderDiscount
import id.a3.core.domain.cafe.TaxMode
import id.a3.core.domain.cash.CashCalculator
import id.a3.core.domain.cash.ShiftCashInput
import id.a3.core.domain.laundry.LaundryCalculator
import id.a3.core.domain.laundry.LaundryOrderLine
import id.a3.core.domain.laundry.LaundryPaymentInput
import id.a3.core.domain.laundry.LaundryUnit
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.double
import kotlinx.serialization.json.int
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.long
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class TestVectorValidationTest {

    private val json = Json { ignoreUnknownKeys = true }

    private fun findTestVectorsFile(): File {
        val candidates = listOf(
            File("../../contracts/test-vectors.json"),
            File("../contracts/test-vectors.json"),
            File("contracts/test-vectors.json"),
            File(System.getProperty("user.dir"), "../../contracts/test-vectors.json"),
            File(System.getProperty("user.dir"), "contracts/test-vectors.json")
        )
        return candidates.firstOrNull { it.exists() }
            ?: throw IllegalStateException("Berkas contracts/test-vectors.json tidak ditemukan dari: ${System.getProperty("user.dir")}")
    }

    @Test
    fun `validate all test vectors from contracts test-vectors json`() {
        val file = findTestVectorsFile()
        val root = json.parseToJsonElement(file.readText()).jsonObject

        // 1. Cafe vectors
        val cafeArray = root["cafe"]!!.jsonArray
        for (element in cafeArray) {
            val obj = element.jsonObject
            val id = obj["id"]!!.jsonPrimitive.content
            val input = obj["input"]!!.jsonObject
            val expected = obj["expected"]!!.jsonObject

            val lines = input["lines"]!!.jsonArray.map { lineElem ->
                val lineObj = lineElem.jsonObject
                CafeOrderLine(
                    unitPrice = lineObj["unit_price"]!!.jsonPrimitive.long,
                    quantity = lineObj["quantity"]!!.jsonPrimitive.int
                )
            }

            val discount = input["discount"]?.let { discElem ->
                if (discElem.toString() == "null") null
                else {
                    val discObj = discElem.jsonObject
                    val type = when (discObj["type"]!!.jsonPrimitive.content) {
                        "percent" -> DiscountType.PERCENT
                        "amount" -> DiscountType.AMOUNT
                        else -> error("Unknown discount type")
                    }
                    OrderDiscount(type = type, value = discObj["value"]!!.jsonPrimitive.long)
                }
            }

            val servicePercent = input["service_percent"]!!.jsonPrimitive.int
            val taxPercent = input["tax_percent"]!!.jsonPrimitive.int
            val taxMode = when (input["tax_mode"]!!.jsonPrimitive.content) {
                "added" -> TaxMode.ADDED
                "included" -> TaxMode.INCLUDED
                else -> error("Unknown tax mode")
            }

            val result = CafeCalculator.calculate(
                CafeCalculationInput(
                    lines = lines,
                    discount = discount,
                    servicePercent = servicePercent,
                    taxPercent = taxPercent,
                    taxMode = taxMode
                )
            )

            assertEquals("[$id] subtotal", expected["subtotal"]!!.jsonPrimitive.long, result.subtotal)
            assertEquals("[$id] discount", expected["discount"]!!.jsonPrimitive.long, result.discount)
            assertEquals("[$id] service_charge", expected["service_charge"]!!.jsonPrimitive.long, result.serviceCharge)
            assertEquals("[$id] tax", expected["tax"]!!.jsonPrimitive.long, result.tax)
            assertEquals("[$id] rounding", expected["rounding"]!!.jsonPrimitive.long, result.rounding)
            assertEquals("[$id] total", expected["total"]!!.jsonPrimitive.long, result.total)
        }

        // 2. Laundry vectors
        val laundryArray = root["laundry"]!!.jsonArray
        for (element in laundryArray) {
            val obj = element.jsonObject
            val id = obj["id"]!!.jsonPrimitive.content
            val input = obj["input"]!!.jsonObject
            val expected = obj["expected"]!!.jsonObject

            if (input.containsKey("lines")) {
                val lines = input["lines"]!!.jsonArray.map { lineElem ->
                    val lineObj = lineElem.jsonObject
                    val unit = when (lineObj["unit"]!!.jsonPrimitive.content) {
                        "kg" -> LaundryUnit.KG
                        "pcs" -> LaundryUnit.PCS
                        else -> error("Unknown unit")
                    }
                    LaundryOrderLine(
                        unit = unit,
                        rate = lineObj["rate"]!!.jsonPrimitive.long,
                        weight = lineObj["weight"]?.jsonPrimitive?.double,
                        minWeight = lineObj["min_weight"]?.jsonPrimitive?.double ?: 1.0,
                        step = lineObj["step"]?.jsonPrimitive?.double ?: 0.5,
                        quantity = lineObj["quantity"]?.jsonPrimitive?.int
                    )
                }

                val result = LaundryCalculator.calculateOrder(lines)

                val expectedBilledWeights = expected["billed_weights"]!!.jsonArray.map { it.jsonPrimitive.double }
                val expectedLineTotals = expected["line_totals"]!!.jsonArray.map { it.jsonPrimitive.long }
                val expectedTotal = expected["total"]!!.jsonPrimitive.long

                assertEquals("[$id] billed_weights count", expectedBilledWeights.size, result.billedWeights.size)
                expectedBilledWeights.forEachIndexed { idx, w ->
                    assertEquals("[$id] billed_weight[$idx]", w, result.billedWeights[idx], 0.0001)
                }
                assertEquals("[$id] line_totals", expectedLineTotals, result.lineTotals)
                assertEquals("[$id] total", expectedTotal, result.total)
            } else {
                val total = input["total"]!!.jsonPrimitive.long
                val payments = input["payments"]!!.jsonArray.map { it.jsonPrimitive.long }

                val result = LaundryCalculator.calculatePayment(
                    LaundryPaymentInput(total = total, payments = payments)
                )

                assertEquals("[$id] remaining", expected["remaining"]!!.jsonPrimitive.long, result.remaining)
                assertEquals("[$id] payment_status", expected["payment_status"]!!.jsonPrimitive.content, result.paymentStatus)
                assertEquals("[$id] after_full_payment", expected["after_full_payment"]!!.jsonPrimitive.content, result.afterFullPayment)
            }
        }

        // 3. Cash vectors
        val cashArray = root["cash"]!!.jsonArray
        for (element in cashArray) {
            val obj = element.jsonObject
            val id = obj["id"]!!.jsonPrimitive.content
            val input = obj["input"]!!.jsonObject
            val expected = obj["expected"]!!.jsonObject

            if (input.containsKey("tendered")) {
                val total = input["total"]!!.jsonPrimitive.long
                val tendered = input["tendered"]!!.jsonPrimitive.long
                val change = CashCalculator.calculateChange(total = total, tendered = tendered)
                assertEquals("[$id] change", expected["change"]!!.jsonPrimitive.long, change)
            } else {
                val shiftCash = ShiftCashInput(
                    openingCash = input["opening_cash"]!!.jsonPrimitive.long,
                    cashSales = input["cash_sales"]!!.jsonPrimitive.long,
                    cashDpAndSettlement = input["cash_dp_and_settlement"]!!.jsonPrimitive.long,
                    cashRefunds = input["cash_refunds"]!!.jsonPrimitive.long,
                    cashOuts = input["cash_outs"]!!.jsonPrimitive.long
                )
                val expectedCash = CashCalculator.calculateExpectedCash(shiftCash)
                assertEquals("[$id] expected_cash", expected["expected_cash"]!!.jsonPrimitive.long, expectedCash)
            }
        }
    }

    @Test
    fun `C1 - Cafe Diskon 10 persen, pajak 10 persen ditambahkan`() {
        val input = CafeCalculationInput(
            lines = listOf(
                CafeOrderLine(unitPrice = 34000L, quantity = 1),
                CafeOrderLine(unitPrice = 28000L, quantity = 1)
            ),
            discount = OrderDiscount(type = DiscountType.PERCENT, value = 10L),
            servicePercent = 0,
            taxPercent = 10,
            taxMode = TaxMode.ADDED
        )
        val result = CafeCalculator.calculate(input)
        assertEquals(62000L, result.subtotal)
        assertEquals(6200L, result.discount)
        assertEquals(0L, result.serviceCharge)
        assertEquals(5580L, result.tax)
        assertEquals(20L, result.rounding)
        assertEquals(61400L, result.total)
    }

    @Test
    fun `C2 - Cafe Diskon nominal, biaya layanan 5 persen, pajak atas dpp + layanan, pembulatan turun`() {
        val input = CafeCalculationInput(
            lines = listOf(
                CafeOrderLine(unitPrice = 40000L, quantity = 2)
            ),
            discount = OrderDiscount(type = DiscountType.AMOUNT, value = 5000L),
            servicePercent = 5,
            taxPercent = 10,
            taxMode = TaxMode.ADDED
        )
        val result = CafeCalculator.calculate(input)
        assertEquals(80000L, result.subtotal)
        assertEquals(5000L, result.discount)
        assertEquals(3750L, result.serviceCharge)
        assertEquals(7875L, result.tax)
        assertEquals(-25L, result.rounding)
        assertEquals(86600L, result.total)
    }

    @Test
    fun `C3 - Cafe Pembulatan setengah naik`() {
        val input = CafeCalculationInput(
            lines = listOf(
                CafeOrderLine(unitPrice = 10050L, quantity = 1)
            ),
            discount = null,
            servicePercent = 0,
            taxPercent = 0,
            taxMode = TaxMode.ADDED
        )
        val result = CafeCalculator.calculate(input)
        assertEquals(10050L, result.subtotal)
        assertEquals(0L, result.discount)
        assertEquals(0L, result.serviceCharge)
        assertEquals(0L, result.tax)
        assertEquals(50L, result.rounding)
        assertEquals(10100L, result.total)
    }

    @Test
    fun `C4 - Cafe Harga sudah termasuk pajak (included mode OQ-04)`() {
        val input = CafeCalculationInput(
            lines = listOf(
                CafeOrderLine(unitPrice = 111000L, quantity = 1)
            ),
            discount = null,
            servicePercent = 0,
            taxPercent = 10,
            taxMode = TaxMode.INCLUDED
        )
        val result = CafeCalculator.calculate(input)
        assertEquals(111000L, result.subtotal)
        assertEquals(0L, result.discount)
        assertEquals(0L, result.serviceCharge)
        assertEquals(10091L, result.tax)
        assertEquals(0L, result.rounding)
        assertEquals(111000L, result.total)
    }

    @Test
    fun `L1 - Laundry Per kg dibulatkan naik ke kelipatan 0,5 ditambah item per pcs`() {
        val lines = listOf(
            LaundryOrderLine(unit = LaundryUnit.KG, weight = 3.7, rate = 7000L, minWeight = 1.0, step = 0.5),
            LaundryOrderLine(unit = LaundryUnit.PCS, quantity = 1, rate = 25000L)
        )
        val result = LaundryCalculator.calculateOrder(lines)
        assertEquals(listOf(4.0), result.billedWeights)
        assertEquals(listOf(28000L, 25000L), result.lineTotals)
        assertEquals(53000L, result.total)
    }

    @Test
    fun `L2 - Laundry Berat minimum`() {
        val lines = listOf(
            LaundryOrderLine(unit = LaundryUnit.KG, weight = 0.3, rate = 7000L, minWeight = 1.0, step = 0.5)
        )
        val result = LaundryCalculator.calculateOrder(lines)
        assertEquals(listOf(1.0), result.billedWeights)
        assertEquals(listOf(7000L), result.lineTotals)
        assertEquals(7000L, result.total)
    }

    @Test
    fun `L3 - Laundry Express`() {
        val lines = listOf(
            LaundryOrderLine(unit = LaundryUnit.KG, weight = 2.1, rate = 10000L, minWeight = 1.0, step = 0.5)
        )
        val result = LaundryCalculator.calculateOrder(lines)
        assertEquals(listOf(2.5), result.billedWeights)
        assertEquals(listOf(25000L), result.lineTotals)
        assertEquals(25000L, result.total)
    }

    @Test
    fun `L4 - Laundry DP lalu pelunasan`() {
        val input = LaundryPaymentInput(total = 53000L, payments = listOf(20000L))
        val result = LaundryCalculator.calculatePayment(input)
        assertEquals(33000L, result.remaining)
        assertEquals("dp", result.paymentStatus)
        assertEquals("paid", result.afterFullPayment)
    }

    @Test
    fun `K1 - Cash Kas seharusnya saat tutup shift`() {
        val input = ShiftCashInput(
            openingCash = 200000L,
            cashSales = 500000L,
            cashDpAndSettlement = 100000L,
            cashRefunds = 25000L,
            cashOuts = 50000L
        )
        val expected = CashCalculator.calculateExpectedCash(input)
        assertEquals(725000L, expected)
    }

    @Test
    fun `K2 - Cash Kembalian tunai`() {
        val change = CashCalculator.calculateChange(total = 61400L, tendered = 70000L)
        assertEquals(8600L, change)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `K2 - Cash Kurang bayar tunai ditolak`() {
        CashCalculator.calculateChange(total = 61400L, tendered = 50000L)
    }
}
