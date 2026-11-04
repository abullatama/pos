package id.a3.core.data.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class CorrectionReasonMapperTest {

    @Test
    fun `toBackend maps English reason codes to Laravel 12 Indonesian codes`() {
        assertEquals("salah_input", CorrectionReasonMapper.toBackend("wrong_input"))
        assertEquals("pelanggan_batal", CorrectionReasonMapper.toBackend("customer_cancelled"))
        assertEquals("komplain_kualitas", CorrectionReasonMapper.toBackend("quality_complaint"))
        assertEquals("salah_buat", CorrectionReasonMapper.toBackend("wrong_made"))
        assertEquals("promo", CorrectionReasonMapper.toBackend("promo"))
        assertEquals("lainnya", CorrectionReasonMapper.toBackend("other"))
    }

    @Test
    fun `toBackend preserves already mapped Indonesian codes`() {
        assertEquals("salah_input", CorrectionReasonMapper.toBackend("salah_input"))
        assertEquals("pelanggan_batal", CorrectionReasonMapper.toBackend("pelanggan_batal"))
        assertEquals("komplain_kualitas", CorrectionReasonMapper.toBackend("komplain_kualitas"))
        assertEquals("salah_buat", CorrectionReasonMapper.toBackend("salah_buat"))
        assertEquals("promo", CorrectionReasonMapper.toBackend("promo"))
        assertEquals("lainnya", CorrectionReasonMapper.toBackend("lainnya"))
    }

    @Test
    fun `toOpenApi maps Indonesian reason codes to English OpenAPI codes`() {
        assertEquals("wrong_input", CorrectionReasonMapper.toOpenApi("salah_input"))
        assertEquals("customer_cancelled", CorrectionReasonMapper.toOpenApi("pelanggan_batal"))
        assertEquals("quality_complaint", CorrectionReasonMapper.toOpenApi("komplain_kualitas"))
        assertEquals("wrong_made", CorrectionReasonMapper.toOpenApi("salah_buat"))
        assertEquals("promo", CorrectionReasonMapper.toOpenApi("promo"))
        assertEquals("other", CorrectionReasonMapper.toOpenApi("lainnya"))
    }

    @Test
    fun `VALID_REASONS contains all English and Indonesian codes`() {
        assertTrue(CorrectionReasonMapper.VALID_REASONS.contains("wrong_input"))
        assertTrue(CorrectionReasonMapper.VALID_REASONS.contains("salah_input"))
        assertTrue(CorrectionReasonMapper.VALID_REASONS.contains("other"))
        assertTrue(CorrectionReasonMapper.VALID_REASONS.contains("lainnya"))
    }
}
