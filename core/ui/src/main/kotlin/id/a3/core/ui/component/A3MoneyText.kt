package id.a3.core.ui.component

import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.style.TextAlign
import id.a3.core.ui.theme.A3Theme
import java.text.NumberFormat
import java.util.Locale

/**
 * Menampilkan angka rupiah dengan format konsisten: "Rp 61.400".
 *
 * Menggunakan NumberFormat untuk locale id-ID dan tabular numbers agar rata kanan rapi.
 * Semua angka uang di aplikasi harus menggunakan komponen ini, bukan string manual.
 */
@Composable
fun A3MoneyText(
    amount: Long,
    modifier: Modifier = Modifier,
    style: TextStyle = LocalTextStyle.current,
    textAlign: TextAlign = TextAlign.End,
    showZeroAsFree: Boolean = false
) {
    val label = when {
        amount == 0L && showZeroAsFree -> "Gratis"
        else -> formatRupiah(amount)
    }

    Text(
        text = label,
        modifier = modifier,
        style = style.copy(
            fontFeatureSettings = "tnum"
        ),
        color = A3Theme.colors.ink,
        textAlign = textAlign
    )
}

fun formatRupiah(amount: Long): String {
    val formatter = NumberFormat.getCurrencyInstance(Locale.forLanguageTag("id-ID"))
    formatter.maximumFractionDigits = 0
    return formatter.format(amount).replace("Rp", "Rp ")
}
