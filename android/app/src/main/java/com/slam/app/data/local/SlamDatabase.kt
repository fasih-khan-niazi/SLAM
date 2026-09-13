package com.slam.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import com.slam.app.data.AccountIdentity
import java.security.MessageDigest

@Database(
    entities = [
        LocationHistoryEntity::class,
        TrustedNumberEntity::class,
        FailedPinEntity::class,
        LastLocationEntity::class,
        SlamEventEntity::class,
        OutboxEntity::class,
        SmsReceiptEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class SlamDatabase : RoomDatabase() {
    abstract fun locationHistory(): LocationHistoryDao
    abstract fun trustedNumbers(): TrustedNumberDao
    abstract fun failedPins(): FailedPinDao
    abstract fun lastLocations(): LastLocationDao
    abstract fun eventLedger(): EventLedgerDao
    abstract fun smsReceipts(): SmsReceiptDao

    companion object {
        private val instances = mutableMapOf<String, SlamDatabase>()

        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS trusted_numbers (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        label TEXT NOT NULL,
                        number TEXT NOT NULL,
                        normalized TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
                db.execSQL(
                    "CREATE UNIQUE INDEX IF NOT EXISTS index_trusted_numbers_normalized ON trusted_numbers(normalized)",
                )
                db.execSQL(
                    """
                    CREATE TABLE IF NOT EXISTS failed_pin_attempts (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        requestedBy TEXT NOT NULL,
                        createdAt INTEGER NOT NULL
                    )
                    """.trimIndent(),
                )
            }
        }

        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE location_history ADD COLUMN accountId TEXT NOT NULL DEFAULT 'legacy'")
                db.execSQL("ALTER TABLE location_history ADD COLUMN accuracyMeters REAL")
                db.execSQL("ALTER TABLE location_history ADD COLUMN provider TEXT")
                db.execSQL("ALTER TABLE location_history ADD COLUMN locationTimestamp INTEGER")
                db.execSQL("ALTER TABLE location_history ADD COLUMN isLastKnownFallback INTEGER NOT NULL DEFAULT 0")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_location_history_accountId_createdAt ON location_history(accountId, createdAt)")

                db.execSQL("DROP INDEX IF EXISTS index_trusted_numbers_normalized")
                db.execSQL("ALTER TABLE trusted_numbers ADD COLUMN accountId TEXT NOT NULL DEFAULT 'legacy'")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_trusted_numbers_accountId_normalized ON trusted_numbers(accountId, normalized)")

                db.execSQL("ALTER TABLE failed_pin_attempts ADD COLUMN accountId TEXT NOT NULL DEFAULT 'legacy'")
                db.execSQL("CREATE INDEX IF NOT EXISTS index_failed_pin_attempts_accountId_createdAt ON failed_pin_attempts(accountId, createdAt)")

                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS last_locations (" +
                        "accountId TEXT NOT NULL PRIMARY KEY, latitude REAL NOT NULL, longitude REAL NOT NULL, " +
                        "accuracyMeters REAL, provider TEXT NOT NULL, locationTimestamp INTEGER NOT NULL, savedAt INTEGER NOT NULL)"
                )
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS slam_events (" +
                        "eventId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, kind TEXT NOT NULL, " +
                        "dedupeKey TEXT NOT NULL, periodStart INTEGER NOT NULL, state TEXT NOT NULL, " +
                        "createdAt INTEGER NOT NULL, finalizedAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_slam_events_accountId_periodStart_state ON slam_events(accountId, periodStart, state)")
                db.execSQL("CREATE UNIQUE INDEX IF NOT EXISTS index_slam_events_accountId_dedupeKey ON slam_events(accountId, dedupeKey)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS event_outbox (" +
                        "eventId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, latitude REAL NOT NULL, " +
                        "longitude REAL NOT NULL, accuracy TEXT NOT NULL, accuracyMeters REAL, provider TEXT NOT NULL, " +
                        "locationTimestamp INTEGER NOT NULL, isLastKnownFallback INTEGER NOT NULL, requestedBy TEXT NOT NULL, " +
                        "state TEXT NOT NULL, attempts INTEGER NOT NULL, createdAt INTEGER NOT NULL, lastAttemptAt INTEGER)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_event_outbox_accountId_state_createdAt ON event_outbox(accountId, state, createdAt)")
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS sms_receipts (" +
                        "receiptId TEXT NOT NULL PRIMARY KEY, accountId TEXT NOT NULL, receivedAt INTEGER NOT NULL)"
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS index_sms_receipts_accountId_receivedAt ON sms_receipts(accountId, receivedAt)")
            }
        }

        fun get(context: Context): SlamDatabase {
            return get(context, AccountIdentity.current(context))
        }

        fun get(context: Context, accountId: String): SlamDatabase {
            return synchronized(this) {
                instances[accountId] ?: Room.databaseBuilder(
                    context.applicationContext,
                    SlamDatabase::class.java,
                    databaseName(accountId),
                )
                    .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                    .build()
                    .also { instances[accountId] = it }
            }
        }

        fun close(context: Context, accountId: String) {
            synchronized(this) { instances.remove(accountId)?.close() }
            context.applicationContext.deleteDatabase(databaseName(accountId))
        }

        fun databaseName(accountId: String): String {
            if (accountId == AccountIdentity.LEGACY_ACCOUNT_ID) return "slam.db"
            val hash = MessageDigest.getInstance("SHA-256")
                .digest(accountId.toByteArray())
                .take(12)
                .joinToString("") { "%02x".format(it) }
            return "slam-$hash.db"
        }
    }
}
