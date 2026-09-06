package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "failed_pin_attempts")
data class FailedPinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val requestedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
)
