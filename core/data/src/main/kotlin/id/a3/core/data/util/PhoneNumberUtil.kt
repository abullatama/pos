package id.a3.core.data.util

object PhoneNumberUtil {

    fun normalize(input: String): String {
        val digitsOnly = input.replace(Regex("[^0-9+]"), "")
        return when {
            digitsOnly.startsWith("+62") -> "0" + digitsOnly.substring(3)
            digitsOnly.startsWith("62") -> "0" + digitsOnly.substring(2)
            else -> digitsOnly
        }
    }

    fun isValid(input: String): Boolean {
        val normalized = normalize(input)
        return normalized.startsWith("08") && normalized.length in 10..14
    }

    fun formatDisplay(input: String): String {
        val norm = normalize(input)
        return when {
            norm.length <= 4 -> norm
            norm.length <= 8 -> "${norm.substring(0, 4)} ${norm.substring(4)}"
            norm.length <= 12 -> "${norm.substring(0, 4)} ${norm.substring(4, 8)} ${norm.substring(8)}"
            else -> "${norm.substring(0, 4)} ${norm.substring(4, 8)} ${norm.substring(8, 12)} ${norm.substring(12)}"
        }
    }
}
