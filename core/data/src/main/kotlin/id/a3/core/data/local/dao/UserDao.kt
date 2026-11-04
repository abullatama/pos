package id.a3.core.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import id.a3.core.data.local.entity.UserEntity

@Dao
interface UserDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(user: UserEntity)

    @Query("DELETE FROM users WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM users")
    suspend fun clear()

    @Query("SELECT * FROM users WHERE id = :id")
    suspend fun getById(id: Long): UserEntity?

    /** Akun aktif berperan [roles] yang punya verifier PIN untuk perangkat ini. */
    @Query(
        "SELECT * FROM users WHERE active = 1 AND role IN (:roles) " +
            "AND pinKdfSalt IS NOT NULL AND pinKdfIterations IS NOT NULL AND pinVerifier IS NOT NULL"
    )
    suspend fun getActiveApprovers(roles: List<String>): List<UserEntity>
}
