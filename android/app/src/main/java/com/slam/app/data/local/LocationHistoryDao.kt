package com.slam.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LocationHistoryDao {
    @Insert
    suspend fun insert(entity: LocationHistoryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertIgnore(entity: LocationHistoryEntity): Long

    @Query("SELECT * FROM location_history ORDER BY createdAt DESC LIMIT 40")
    suspend fun latest(): List<LocationHistoryEntity>

    @Query("SELECT * FROM location_history WHERE accountId = :accountId ORDER BY createdAt DESC LIMIT 40")
    suspend fun latest(accountId: String): List<LocationHistoryEntity>

    @Query(
        """
        SELECT id FROM location_history
        WHERE accountId = :accountId
          AND ABS(latitude - :latitude) < 0.00001
          AND ABS(longitude - :longitude) < 0.00001
          AND ABS(COALESCE(locationTimestamp, createdAt) - :timestampMs) < 5000
        LIMIT 1
        """
    )
    suspend fun findNear(
        accountId: String,
        latitude: Double,
        longitude: Double,
        timestampMs: Long,
    ): Long?

    @Query(
        """
        DELETE FROM location_history WHERE id NOT IN (
            SELECT id FROM location_history ORDER BY createdAt DESC LIMIT 40
        )
        """
    )
    suspend fun trim()

    @Query(
        """
        DELETE FROM location_history
        WHERE accountId = :accountId AND id NOT IN (
            SELECT id FROM location_history
            WHERE accountId = :accountId ORDER BY createdAt DESC LIMIT 40
        )
        """
    )
    suspend fun trim(accountId: String)

    @Query("DELETE FROM location_history WHERE accountId = :accountId")
    suspend fun clear(accountId: String)
}
