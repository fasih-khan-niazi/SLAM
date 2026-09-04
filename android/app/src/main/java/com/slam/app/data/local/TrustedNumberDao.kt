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

    @Query("SELECT COUNT(*) FROM trusted_numbers")
    suspend fun count(): Int

    @Query("SELECT * FROM trusted_numbers WHERE normalized = :normalized LIMIT 1")
    suspend fun findByNormalized(normalized: String): TrustedNumberEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TrustedNumberEntity)

    @Delete
    suspend fun delete(entity: TrustedNumberEntity)
}
