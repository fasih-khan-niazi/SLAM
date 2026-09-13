package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "slam_events",
    indices = [
        Index(value = ["accountId", "periodStart", "state"]),
        Index(value = ["accountId", "dedupeKey"], unique = true),
    ],
)
data class SlamEventEntity(
    @PrimaryKey val eventId: String,
    val accountId: String,
    val kind: String,
    val dedupeKey: String,
    val periodStart: Long,
    val state: String,
    val createdAt: Long,
    val finalizedAt: Long? = null,
)

object EventState {
    const val RESERVED = "RESERVED"
    const val FINALIZED = "FINALIZED"
    const val REFUNDED = "REFUNDED"
}
