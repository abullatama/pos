package id.a3.pos.printer

import java.io.ByteArrayOutputStream
import java.nio.charset.Charset

/**
 * ESC/POS command builder for 58mm thermal printers (e.g. iWare C58BT).
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
     * Standard ESC/POS QR code printing commands (GS ( k).
     * @param data Content to encode in QR
     * @param moduleSize dot width per module (1..16, typically 4 to 8 for 58mm)
     * @param errorCorrection 48=L (7%), 49=M (15%), 50=Q (25%), 51=H (30%)
     */
    fun qrCode(data: String, moduleSize: Int = 6, errorCorrection: Int = 49): EscPosBuilder = apply {
        val bytes = data.toByteArray(charset)
        val len = bytes.size + 3
        val pL = (len and 0xFF).toByte()
        val pH = ((len shr 8) and 0xFF).toByte()

        // 1. Model: Model 2 (49)
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x04, 0x00, 0x31, 0x41, 0x32, 0x00))

        // 2. Size: moduleSize (1..16)
        val sizeByte = moduleSize.coerceIn(1, 16).toByte()
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x43, sizeByte))

        // 3. Error correction level
        val ecByte = errorCorrection.coerceIn(48, 51).toByte()
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x45, ecByte))

        // 4. Store data into symbol storage area: GS ( k pL pH cn fn m d1...dk
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, pL, pH, 0x31, 0x50, 0x30))
        buffer.write(bytes)

        // 5. Print the symbol data: GS ( k pL pH cn fn m
        buffer.write(byteArrayOf(0x1D, 0x28, 0x6B, 0x03, 0x00, 0x31, 0x51, 0x30))
    }

    fun feed(lines: Int = 3): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x64, lines.coerceIn(1, 255).toByte())) // ESC d n
    }

    /**
     * Paper cut command (GS V m).
     * Note: iWare C58BT usually has manual tear bar, but if cutter hardware is present:
     * 0x00 = Full cut, 0x01 = Partial cut, 0x42 = Partial cut with feed
     */
    fun cut(partial: Boolean = true): EscPosBuilder = apply {
        val m = if (partial) 0x01.toByte() else 0x00.toByte()
        buffer.write(byteArrayOf(0x1D, 0x56, m))
    }

    /**
     * Cash drawer kick (ESC p m t1 t2) for RJ11 drawer port.
     */
    fun openCashDrawer(): EscPosBuilder = apply {
        buffer.write(byteArrayOf(0x1B, 0x70, 0x00, 0x19, 0xFA.toByte()))
    }

    fun build(): ByteArray = buffer.toByteArray()
}
