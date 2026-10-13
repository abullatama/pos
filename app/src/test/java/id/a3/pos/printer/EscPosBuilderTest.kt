package id.a3.pos.printer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EscPosBuilderTest {

    @Test
    fun testTwoColumnsFormatting() {
        val builder = EscPosBuilder(charsPerLine = 32)
        builder.twoColumns("Subtotal", "Rp25.000")
        val output = String(builder.build(), Charsets.ISO_8859_1)

        // "Subtotal" (8 chars) + 16 spaces + "Rp25.000" (8 chars) = 32 chars + \n
        assertTrue(output.startsWith("Subtotal                Rp25.000\n"))
    }

    @Test
    fun testDividerLength() {
        val builder = EscPosBuilder(charsPerLine = 32)
        builder.divider('-')
        val output = String(builder.build(), Charsets.ISO_8859_1)

        assertEquals("-".repeat(32) + "\n", output)
    }

    @Test
    fun testEscPosCommandBytes() {
        val builder = EscPosBuilder(charsPerLine = 32)
        builder.reset()
            .boldOn()
            .textLine("TEST")
            .boldOff()
            .feed(2)
            .cut(partial = true)

        val bytes = builder.build()

        // ESC @ -> 0x1B, 0x40
        assertEquals(0x1B.toByte(), bytes[0])
        assertEquals(0x40.toByte(), bytes[1])

        // ESC E 1 -> 0x1B, 0x45, 0x01
        assertEquals(0x1B.toByte(), bytes[2])
        assertEquals(0x45.toByte(), bytes[3])
        assertEquals(0x01.toByte(), bytes[4])
    }
}
