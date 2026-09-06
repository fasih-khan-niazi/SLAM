package com.slam.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query

@Dao
interface LocationHistoryDao {
    @Insert
    suspend fun insert(entity: LocationHistoryEntity)

    @Query("SELECT * FROM location_history ORDER BY createdAt DESC LIMIT 10")
    suspend fun latest(): List<LocationHistoryEntity>

    @Query(
        """
        DELETE FROM location_history WHERE id NOT IN (
            SELECT id FROM location_history ORDER BY createdAt DESC LIMIT 10
        )
        """
    )
    suspend fun trim()
}
