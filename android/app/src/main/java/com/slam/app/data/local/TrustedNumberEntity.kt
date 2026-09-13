package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.ColumnInfo
import com.slam.app.data.AccountIdentity

@Entity(
    tableName = "trusted_numbers",
    indices = [Index(value = ["accountId", "normalized"], unique = true)],
)
data class TrustedNumberEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    @ColumnInfo(defaultValue = "'legacy'") val accountId: String = AccountIdentity.active(),
    val label: String,
    val number: String,
    val normalized: String,
    val createdAt: Long = System.currentTimeMillis(),
)
