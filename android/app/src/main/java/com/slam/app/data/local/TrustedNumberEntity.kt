package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "trusted_numbers",
    indices = [Index(value = ["normalized"], unique = true)],
)
data class TrustedNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val label: String,
    val number: String,
    val normalized: String,
    val createdAt: Long = System.currentTimeMillis(),
)
