package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "last_locations")
data class LastLocationEntity(
    @PrimaryKey val accountId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracyMeters: Float?,
    val provider: String,
    val locationTimestamp: Long,
    val savedAt: Long,
)
