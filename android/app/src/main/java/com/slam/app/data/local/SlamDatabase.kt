package com.slam.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        LocationHistoryEntity::class,
        TrustedNumberEntity::class,
        FailedPinEntity::class,
    ],
    version = 2,
    exportSchema = false,
)
abstract class SlamDatabase : RoomDatabase() {
    abstract fun locationHistory(): LocationHistoryDao
    abstract fun trustedNumbers(): TrustedNumberDao
    abstract fun failedPins(): FailedPinDao

    companion object {
        @Volatile
        private var instance: SlamDatabase? = null

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

        fun get(context: Context): SlamDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SlamDatabase::class.java,
                    "slam.db",
                )
                    .addMigrations(MIGRATION_1_2)
                    .fallbackToDestructiveMigration()
                    .build()
                    .also { instance = it }
            }
        }
    }
}
