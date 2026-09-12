package com.slam.app.data.local

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction

@Dao
abstract class EventLedgerDao {
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    protected abstract suspend fun insertEvent(event: SlamEventEntity): Long

    @Insert(onConflict = OnConflictStrategy.ABORT)
    protected abstract suspend fun insertOutbox(outbox: OutboxEntity)

    @Query(
        "SELECT COUNT(*) FROM slam_events " +
            "WHERE accountId = :accountId AND periodStart = :periodStart " +
            "AND state IN ('RESERVED', 'FINALIZED')"
    )
    abstract suspend fun used(accountId: String, periodStart: Long): Int

    @Query(
        "SELECT COUNT(*) FROM slam_events " +
            "WHERE accountId = :accountId AND periodStart = :periodStart AND state = 'RESERVED'"
    )
    abstract suspend fun reserved(accountId: String, periodStart: Long): Int

    @Query("UPDATE slam_events SET state = :state, finalizedAt = :at WHERE eventId = :eventId AND state = 'RESERVED'")
    protected abstract suspend fun transition(eventId: String, state: String, at: Long): Int

    @Transaction
    open suspend fun reserve(event: SlamEventEntity, availableRemaining: Int): Boolean {
        if (availableRemaining >= 0 &&
            reserved(event.accountId, event.periodStart) >= availableRemaining
        ) return false
        return insertEvent(event) != -1L
    }

    @Transaction
    open suspend fun finalize(eventId: String, outbox: OutboxEntity): Boolean {
        if (transition(eventId, EventState.FINALIZED, System.currentTimeMillis()) != 1) return false
        insertOutbox(outbox)
        return true
    }

    @Transaction
    open suspend fun refund(eventId: String): Boolean =
        transition(eventId, EventState.REFUNDED, System.currentTimeMillis()) == 1

    @Query("SELECT * FROM event_outbox WHERE accountId = :accountId AND state = 'PENDING' ORDER BY createdAt LIMIT :limit")
    abstract suspend fun pending(accountId: String, limit: Int = 20): List<OutboxEntity>

    @Query("UPDATE event_outbox SET state = 'SENT', attempts = attempts + 1, lastAttemptAt = :at WHERE eventId = :eventId")
    abstract suspend fun markSent(eventId: String, at: Long = System.currentTimeMillis())

    @Query("UPDATE event_outbox SET attempts = attempts + 1, lastAttemptAt = :at WHERE eventId = :eventId")
    abstract suspend fun markAttempt(eventId: String, at: Long = System.currentTimeMillis())

    @Query("UPDATE event_outbox SET state = 'FAILED', attempts = attempts + 1, lastAttemptAt = :at WHERE eventId = :eventId")
    abstract suspend fun markFailed(eventId: String, at: Long = System.currentTimeMillis())

    @Query(
        "UPDATE event_outbox SET state = 'PENDING' WHERE accountId = :accountId AND state = 'FAILED' " +
            "AND (accuracy = 'LAST_KNOWN' OR accuracy = 'LOW')",
    )
    abstract suspend fun requeueFailedLastKnown(accountId: String): Int

    @Query("DELETE FROM event_outbox WHERE state IN ('SENT', 'FAILED') AND createdAt < :before")
    abstract suspend fun pruneCompleted(before: Long)

    @Query("DELETE FROM slam_events WHERE accountId = :accountId")
    abstract suspend fun clearEvents(accountId: String)

    @Query("DELETE FROM event_outbox WHERE accountId = :accountId")
    abstract suspend fun clearOutbox(accountId: String)
}
