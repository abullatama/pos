package id.a3.core.printer.format

import id.a3.core.printer.escpos.EscPosBuilder

data class KitchenTicketItem(
    val name: String,
    val quantity: Int,
    val optionsSummary: String? = null,
    val note: String? = null
)

/**
 * Penyusun format tiket dapur / bar termal 58mm (ESC/POS).
 *
 * Sesuai aturan PRT-03, PRT-04, dan CAF-07:
 * - Hanya memuat item pesanan yang baru dikirim.
 * - Tanpa harga sama sekali (dilarang menampilkan nominal rupiah pada tiket dapur).
 * - Menampilkan nomor meja, nomor pesanan, dan waktu pengiriman secara jelas.
 */
object KitchenTicketFormatter {

    fun format(
        orderDisplayNumber: String,
        tableLabel: String,
        occurredAt: String,
        items: List<KitchenTicketItem>,
        orderType: String = "DINE IN",
        staffName: String? = null,
        charsPerLine: Int = 32
    ): ByteArray {
        val builder = EscPosBuilder(charsPerLine = charsPerLine)

        builder.reset()
            .alignCenter()
            .boldOn()
            .textSize(widthMultiplier = 2, heightMultiplier = 2)
            .textLine("TIKET DAPUR")
            .textSize(widthMultiplier = 1, heightMultiplier = 1)
            .textLine(tableLabel.uppercase())
            .boldOff()
            .divider('=')
            .alignLeft()
            .twoColumns("No. Order:", orderDisplayNumber)
            .twoColumns("Tipe:", orderType)
            .twoColumns("Waktu:", occurredAt)

        if (!staffName.isNullOrBlank()) {
            builder.twoColumns("Kasir:", staffName)
        }

        builder.divider('-')
            .boldOn()
            .textLine("ITEM PESANAN (BARU):")
            .boldOff()

        items.forEach { item ->
            builder.boldOn()
                .textLine("${item.quantity}x ${item.name}")
                .boldOff()

            if (!item.optionsSummary.isNullOrBlank()) {
                builder.textLine("   + ${item.optionsSummary}")
            }

            if (!item.note.isNullOrBlank()) {
                builder.textLine("   Catatan: ${item.note}")
            }
        }

        builder.divider('=')
            .alignCenter()
            .textLine("*** HANYA UNTUK DAPUR / BAR ***")
            .textLine("*** TANPA HARGA ***")
            .feed(4)

        return builder.build()
    }
}
