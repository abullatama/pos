package id.a3.pos.printer

import android.graphics.Bitmap
import android.graphics.Color
import com.google.zxing.BarcodeFormat
import com.google.zxing.qrcode.QRCodeWriter
import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * ESC/POS command builder for 58mm thermal printers (e.g. iWare C58BT / RPP02N).
 * Standard 58mm printer has 384 dots per line = 32 standard characters per line (Font A 12x24).
 */
class EscPosBuilder(
    val charsPerLine: Int = 32,
    private val charset: Charset = Charset.forName("GBK")
) {
    private val buffer = ByteArrayOutputStream()

    fun reset(): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x40)) // ESC @
    }

    fun alignLeft(): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x00)) // ESC a 0
    }

    fun alignCenter(): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x01)) // ESC a 1
    }

    fun alignRight(): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x61, 0x02)) // ESC a 2
    }

    fun boldOn(): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x45, 0x01)) // ESC E 1
    }

    fun boldOff(): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x45, 0x00)) // ESC E 0
    }

    fun textSize(widthMultiplier: Int = 1, heightMultiplier: Int = 1): EscPosBuilder = apply {
        val w = (widthMultiplier.coerceIn(1, 8) - 1) shl 4
        val h = (heightMultiplier.coerceIn(1, 8) - 1)
        val n = (w or h).toByte()
        buffer.write(byteArrayOf(0x1D, 0x21, n)) // GS ! n
    }

    fun text(str: String): EscPosBuilder = apply {
        buffer.write(str.toByteArray(charset))
    }

    fun textLine(str: String = ""): EscPosBuilder = apply {
        if (str.isNotEmpty()) {
            buffer.write(str.toByteArray(charset))
        }
        buffer.write(byteArrayOf(0x0A)) // LF
    }

    fun divider(char: Char = '-'): EscPosBuilder = apply {
        textLine(char.toString().repeat(charsPerLine))
    }

    fun twoColumns(left: String, right: String): EscPosBuilder = apply {
        val availableLeft = charsPerLine - right.length
        val formatted = if (availableLeft <= 0) {
            left.take(charsPerLine) + "\n" + right.padStart(charsPerLine)
        } else {
            left.take(availableLeft).padEnd(availableLeft) + right
        }
        textLine(formatted)
    }

    /**
     * Print QR code as thermal raster bitmap (GS v 0).
     * This is universally supported by 58mm printers like iWare C58BT/RPP02N
     * where native GS ( k command is ignored or causes a freeze.
     */
    fun qrCodeBitmap(data: String, size: Int = 200): EscPosBuilder = apply {
        val writer = QRCodeWriter()
        val bitMatrix = writer.encode(data, BarcodeFormat.QR_CODE, size, size)
        val width = bitMatrix.width
        val height = bitMatrix.height
        val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.RGB_565)
        for (x in 0 until width) {
            for (y in 0 until height) {
                bitmap.setPixel(x, y, if (bitMatrix.get(x, y)) Color.BLACK else Color.WHITE)
            }
        }
        rasterBitmap(bitmap)
    }

    /**
     * Print Bitmap using GS v 0 raster bit image command.
     */
    fun rasterBitmap(bitmap: Bitmap): EscPosBuilder = apply {
        val width = bitmap.width
        val height = bitmap.height
        val widthBytes = (width + 7) / 8

        // GS v 0 0 xL xH yL yH d1...dk
        val xL = (widthBytes and 0xFF).toByte()
        val xH = ((widthBytes shr 8) and 0xFF).toByte()
        val yL = (height and 0xFF).toByte()
        val yH = ((height shr 8) and 0xFF).toByte()

        buffer.write(byteArrayOf(0x1D, 0x76, 0x30, 0x00, xL, xH, yL, yH))

        for (y in 0 until height) {
            for (xByte in 0 until widthBytes) {
                var byteVal = 0
                for (bit in 0 until 8) {
                    val x = xByte * 8 + bit
                    if (x < width) {
                        val pixel = bitmap.getPixel(x, y)
                        val r = (pixel shr 16) and 0xFF
                        val g = (pixel shr 8) and 0xFF
                        val b = pixel and 0xFF
                        // Luminance threshold for monochrome
                        val gray = (r * 30 + g * 59 + b * 11) / 100
                        if (gray < 128) {
                            byteVal = byteVal or (1 shl (7 - bit))
                        }
                    }
                }
                buffer.write(byteVal)
            }
        }
    }

    fun feed(lines: Int = 3): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x64, lines.coerceIn(1, 255).toByte())) // ESC d n
    }

    fun cut(partial: Boolean = true): EscPosBuilder = apply {
        val m = if (partial) 0x01.toByte() else 0x00.toByte()
        buffer.write(byteArrayOf(0x1D, 0x56, m))
    }

    fun openCashDrawer(): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()))
    }

    fun build(): ByteArray = buffer.toByteArray()
}
