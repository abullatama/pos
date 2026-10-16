package id.a3.feature.laundry.scanner

import android.graphics.ImageFormat
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.PlanarYUVLuminanceSource
import com.google.zxing.common.HybridBinarizer

/**
 * Penganalisis frame kamera CameraX menggunakan ZXing HybridBinarizer:
 * Disesuaikan untuk membaca QR code tag thermal yang tercetak dari printer termal 58mm
 * (termasuk tag yang agak pudar atau sedikit lembap).
 */
class QrCodeAnalyzer(
    private val onQrCodeDetected: (String) -> Unit
) : ImageAnalysis.Analyzer {

    private val reader = MultiFormatReader()

    override fun analyze(image: ImageProxy) {
        if (image.format == ImageFormat.YUV_420_888 || image.format == ImageFormat.YUV_422_888 || image.format == ImageFormat.YUV_444_888) {
            val plane = image.planes[0]
            val buffer = plane.buffer
            val data = ByteArray(buffer.remaining())
            buffer.get(data)

            val width = image.width
            val height = image.height

            val source = PlanarYUVLuminanceSource(
                data,
                plane.rowStride,
                height,
                0,
                0,
                width,
                height,
                false
            )

            val binaryBitmap = BinaryBitmap(HybridBinarizer(source))

            try {
                val result = reader.decodeWithState(binaryBitmap)
                android.util.Log.i("LaundryScan", "QR Tag DETECTED: ${result.text}")
                onQrCodeDetected(result.text)
            } catch (_: Exception) {
                // Frame tidak memuat QR code yang valid, lewati ke frame berikutnya
            } finally {
                reader.reset()
            }
        }
        image.close()
    }
}
