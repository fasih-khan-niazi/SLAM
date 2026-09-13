package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.slam.app.data.AccountIdentity

@Entity(
    tableName = "location_history",
    indices = [Index(value = ["accountId", "createdAt"])],
)
data class LocationHistoryEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "'legacy'") val accountId: String = AccountIdentity.active(),
    val latitude: Double,
    val longitude: Double,
    val accuracy: String,
    val accuracyMeters: Float? = null,
    val provider: String? = null,
    val locationTimestamp: Long? = null,
    @ColumnInfo(defaultValue = "0") val isLastKnownFallback: Boolean = false,
    val requestedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
)
