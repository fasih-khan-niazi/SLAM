package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "event_outbox",
    indices = [Index(value = ["accountId", "state", "createdAt"])],
)
data class OutboxEntity(
    @PrimaryKey val eventId: String,
    val accountId: String,
    val latitude: Double,
    val longitude: Double,
    val accuracy: String,
    val accuracyMeters: Float?,
    val provider: String,
    val locationTimestamp: Long,
    val isLastKnownFallback: Boolean,
    val requestedBy: String,
    val state: String = "PENDING",
    val attempts: Int = 0,
    val createdAt: Long,
    val lastAttemptAt: Long? = null,
)
