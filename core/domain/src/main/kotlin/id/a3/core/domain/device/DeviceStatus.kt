package id.a3.core.domain.device

/**
 * Status siklus hidup pendaftaran perangkat tablet:
 * - UNREGISTERED: Tablet baru pertama kali buka, belum didaftarkan.
 * - PENDING: Kode pendaftaran telah dikirim ke backend, menunggu persetujuan owner di portal web.
 * - ACTIVE: Perangkat telah disetujui owner; kredensial perangkat tersimpan aman di tablet.
 * - REVOKED: Akses tablet dicabut oleh owner; tablet terkunci total.
 */
enum class DeviceStatus {
    UNREGISTERED,
    PENDING,
    ACTIVE,
    REVOKED
}
