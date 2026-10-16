package id.a3.feature.laundry.scanner

import com.google.zxing.BarcodeFormat
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer
import com.google.zxing.qrcode.QRCodeWriter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Test
import kotlin.random.Random

/**
 * Pengujian algoritma decoding QR Tag (F04):
 * Memastikan ZXing HybridBinarizer mampu membaca payload tag laundry ("ORD-20261012-L1-0001")
 * dalam kondisi:
 * 1. Tag Normal (kontras tajam).
 * 2. Tag Agak Pudar (thermal paper fading: kontras rendah / hitam menjadi abu-abu).
 * 3. Tag Sedikit Lembap / Basah (terkena uap air / noda air / gradien pencahayaan lokal tidak merata).
 */
class QrCodeAnalyzerTest {

    private val reader = MultiFormatReader()
    private val expectedPayload = "ORD-20261012-L1-0001"

    private fun generateQrMatrix(text: String, size: Int = 200) =
        QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size)

    @Test
    fun `decode normal crisp QR code successfully`() {
        val size = 200
        val bitMatrix = generateQrMatrix(expectedPayload, size)
        val yuvData = ByteArray(size * size)

        for (y in 0 until size) {
            for (x in 0 until size) {
                // Hitam = 0, Putih = 255
                yuvData[y * size + x] = if (bitMatrix.get(x, y)) 0.toByte() else 255.toByte()
            }
        }

        val source = PlanarYUVLuminanceSource(yuvData, size, size, 0, 0, size, size, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        val result = reader.decodeWithState(bitmap)
        assertNotNull(result)
        assertEquals(expectedPayload, result.text)
    }

    @Test
    fun `decode faded thermal paper QR code successfully`() {
        val size = 200
        val bitMatrix = generateQrMatrix(expectedPayload, size)
        val yuvData = ByteArray(size * size)

        // Simulasikan kertas thermal pudar akibat panas/lama:
        // Latar putih menjadi abu-abu terang (220), modul hitam pudar menjadi abu-abu sedang (140)
        // Dynamic range turun drastis dari 255 menjadi 80
        for (y in 0 until size) {
            for (x in 0 until size) {
                val lum = if (bitMatrix.get(x, y)) 140 else 220
                yuvData[y * size + x] = lum.toByte()
            }
        }

        val source = PlanarYUVLuminanceSource(yuvData, size, size, 0, 0, size, size, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        val result = reader.decodeWithState(bitmap)
        assertNotNull(result)
        assertEquals("Faded QR code must be successfully decoded by HybridBinarizer", expectedPayload, result.text)
    }

    @Test
    fun `decode damp or moisture-stained QR code with localized luminance variation successfully`() {
        val size = 200
        val bitMatrix = generateQrMatrix(expectedPayload, size)
        val yuvData = ByteArray(size * size)
        val random = Random(42)

        // Simulasikan kertas roll lembap / terkena cipratan air cucian:
        // 1. Gradien kelembapan melintang diagonal (air membuat kertas menggelap sebagian).
        // 2. Noise bercak air acak di beberapa titik.
        for (y in 0 until size) {
            val moistureGradient = ((xNorm(y, size)) * 50).toInt() // menggelap bertahap hingga 50
            for (x in 0 until size) {
                val isDarkModule = bitMatrix.get(x, y)
                val baseLum = if (isDarkModule) 30 else 235
                val spotNoise = random.nextInt(-10, 10)

                // Lembap membuat kertas basah lebih gelap
                val finalLum = (baseLum - moistureGradient + spotNoise).coerceIn(0, 255)
                yuvData[y * size + x] = finalLum.toByte()
            }
        }

        val source = PlanarYUVLuminanceSource(yuvData, size, size, 0, 0, size, size, false)
        val bitmap = BinaryBitmap(HybridBinarizer(source))

        val result = reader.decodeWithState(bitmap)
        assertNotNull(result)
        assertEquals("Damp/moist QR code must be successfully decoded by HybridBinarizer", expectedPayload, result.text)
    }

    private fun xNorm(pos: Int, total: Int): Double = pos.toDouble() / total.toDouble()
}
