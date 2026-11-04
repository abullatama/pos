package id.a3.core.data.local.entity

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Akun staf dari server (bootstrap/changes). Verifier PIN khusus perangkat ini dipakai untuk
 * memeriksa PIN manager secara offline. PIN mentah tidak pernah disimpan.
 */
@Entity(tableName = "users")
data class UserEntity(
    @PrimaryKey
    val id: Long,
    val name: String,
    val role: String,
    val active: Boolean,
    val pinKdfSalt: String?,
    val pinKdfIterations: Int?,
    val pinVerifier: String?,
    val updatedAt: Long = System.currentTimeMillis()
)
