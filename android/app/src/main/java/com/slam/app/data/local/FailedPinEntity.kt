package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.slam.app.data.AccountIdentity

@Entity(
    tableName = "failed_pin_attempts",
    indices = [Index(value = ["accountId", "createdAt"])],
)
data class FailedPinEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "'legacy'") val accountId: String = AccountIdentity.active(),
    val requestedBy: String,
    val createdAt: Long = System.currentTimeMillis(),
)
