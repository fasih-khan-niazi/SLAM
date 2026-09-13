package com.slam.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class SmsReceiptDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insert(receipt: SmsReceiptEntity): Long

    @Query("DELETE FROM sms_receipts WHERE receivedAt < :before")
    protected abstract suspend fun prune(before: Long)

    @Transaction
    open suspend fun accept(receipt: SmsReceiptEntity, ttlMs: Long = 24L * 60L * 60L * 1_000L): Boolean {
        prune(receipt.receivedAt - ttlMs)
        return insert(receipt) != -1L
    }

    @Query("DELETE FROM sms_receipts WHERE accountId = :accountId")
    abstract suspend fun clear(accountId: String)
}
