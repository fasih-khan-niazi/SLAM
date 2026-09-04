package com.slam.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface FailedPinDao {
    @Insert
    suspend fun insert(entity: FailedPinEntity)

    @Query("SELECT COUNT(*) FROM failed_pin_attempts")
    suspend fun count(): Int

    @Query("SELECT * FROM failed_pin_attempts ORDER BY createdAt DESC LIMIT 20")
    suspend fun latest(): List<FailedPinEntity>
}
