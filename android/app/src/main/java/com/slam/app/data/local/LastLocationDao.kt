package com.slam.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface LastLocationDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun save(location: LastLocationEntity)

    @Query("SELECT * FROM last_locations WHERE accountId = :accountId LIMIT 1")
    suspend fun get(accountId: String): LastLocationEntity?

    @Query("DELETE FROM last_locations WHERE accountId = :accountId")
    suspend fun clear(accountId: String)
}
