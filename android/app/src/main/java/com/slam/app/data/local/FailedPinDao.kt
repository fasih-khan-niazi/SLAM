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

    @Query("SELECT COUNT(*) FROM failed_pin_attempts WHERE createdAt >= :since")
    suspend fun countSince(since: Long): Int

    @Query("SELECT COUNT(*) FROM failed_pin_attempts WHERE accountId = :accountId AND createdAt >= :since")
    suspend fun countSince(accountId: String, since: Long): Int

    @Query(
        "SELECT COUNT(*) FROM failed_pin_attempts " +
            "WHERE accountId = :accountId AND requestedBy = :sender AND createdAt >= :since"
    )
    suspend fun countSinceSender(accountId: String, sender: String, since: Long): Int

    @Query("DELETE FROM failed_pin_attempts")
    suspend fun clear()

    @Query("DELETE FROM failed_pin_attempts WHERE accountId = :accountId")
    suspend fun clear(accountId: String)

    @Query("DELETE FROM failed_pin_attempts WHERE createdAt < :before")
    suspend fun deleteOlderThan(before: Long)

    @Query("DELETE FROM failed_pin_attempts WHERE accountId = :accountId AND createdAt < :before")
    suspend fun deleteOlderThan(accountId: String, before: Long)

    @Query("SELECT * FROM failed_pin_attempts ORDER BY createdAt DESC LIMIT 20")
    suspend fun latest(): List<FailedPinEntity>

    @Query("DELETE FROM failed_pin_attempts WHERE accountId = :accountId")
    suspend fun wipe(accountId: String)
}
