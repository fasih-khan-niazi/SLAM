package com.slam.app.data.local

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "sms_receipts",
    indices = [Index(value = ["accountId", "receivedAt"])],
)
data class SmsReceiptEntity(
    @PrimaryKey val receiptId: String,
    val accountId: String,
    val receivedAt: Long,
)
