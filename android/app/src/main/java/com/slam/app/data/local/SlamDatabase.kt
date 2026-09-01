package com.slam.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [LocationHistoryEntity::class], version = 1, exportSchema = false)
abstract class SlamDatabase : RoomDatabase() {
    abstract fun locationHistory(): LocationHistoryDao

    companion object {
        @Volatile
        private var instance: SlamDatabase? = null

        fun get(context: Context): SlamDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    SlamDatabase::class.java,
                    "slam.db",
                ).build().also { instance = it }
            }
        }
    }
}
