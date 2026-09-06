package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "location_history")
data class LocationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val latitude: Double,
    val longitude: Double,
    val accuracy: String,
    val requestedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
)
