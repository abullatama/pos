package id.a3.core.auth.api

import okhttp3.Dns
import java.net.InetAddress
import java.net.UnknownHostException

/**
 * DNS Resolver tangguh untuk tablet POS A3.
 * Mengutamakan DNS bawaan sistem Android. Jika router Wi-Fi lokal mengalami gangguan DNS
 * (UnknownHostException: No address associated with hostname), secara otomatis menyediakan
 * fallback alamat IP resmi untuk domain backend (a3-api.nen.asia & a3-api.sidik.cloud).
 */
object RobustDns : Dns {

    private val FALLBACK_HOST_MAP: Map<String, List<ByteArray>> = mapOf(
        "a3-api.nen.asia" to listOf(
            byteArrayOf(203.toByte(), 175.toByte(), 10.toByte(), 97.toByte())
        ),
        "a3-api.sidik.cloud" to listOf(
            byteArrayOf(104.toByte(), 21.toByte(), 23.toByte(), 205.toByte()),
            byteArrayOf(172.toByte(), 67.toByte(), 182.toByte(), 160.toByte())
        )
    )

    override fun lookup(hostname: String): List<InetAddress> {
        return try {
            val addresses = Dns.SYSTEM.lookup(hostname)
            if (addresses.isNotEmpty()) addresses else (fallbackLookup(hostname) ?: emptyList())
        } catch (e: UnknownHostException) {
            fallbackLookup(hostname) ?: throw e
        }
    }

    internal fun fallbackLookup(hostname: String): List<InetAddress>? {
        val ipByteArrays = FALLBACK_HOST_MAP.entries.firstOrNull {
            it.key.equals(hostname, ignoreCase = true)
        }?.value ?: return null

        return try {
            ipByteArrays.map { ip ->
                InetAddress.getByAddress(hostname, ip)
            }
        } catch (_: Exception) {
            null
        }
    }
}
