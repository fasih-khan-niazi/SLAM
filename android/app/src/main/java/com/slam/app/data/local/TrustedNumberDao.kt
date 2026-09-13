package com.slam.app.data.local

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface TrustedNumberDao {
    @Query("SELECT * FROM trusted_numbers ORDER BY createdAt DESC")
    suspend fun all(): List<TrustedNumberEntity>

    @Query("SELECT * FROM trusted_numbers WHERE accountId IN (:accountId, 'legacy') ORDER BY createdAt DESC")
    suspend fun all(accountId: String): List<TrustedNumberEntity>

    @Query("SELECT COUNT(*) FROM trusted_numbers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM trusted_numbers WHERE accountId IN (:accountId, 'legacy')")
    suspend fun count(accountId: String): Int

    @Query("SELECT * FROM trusted_numbers WHERE normalized = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): TrustedNumberEntity?

    @Query("SELECT * FROM trusted_numbers WHERE accountId IN (:accountId, 'legacy') AND normalized = :normalized LIMIT 1")
    suspend fun findByNormalized(accountId: String, normalized: String): TrustedNumberEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TrustedNumberEntity)

    @Delete
    suspend fun delete(entity: TrustedNumberEntity)

    @Query("DELETE FROM trusted_numbers WHERE accountId = :accountId")
    suspend fun clear(accountId: String)
}
