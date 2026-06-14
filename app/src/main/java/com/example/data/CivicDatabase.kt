package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [CivicReport::class, CivicComment::class, SavedReport::class, CivicReel::class, CivicNotification::class], version = 5, exportSchema = false)
abstract class CivicDatabase : RoomDatabase() {
    abstract fun civicReportDao(): CivicReportDao

    companion object {
        @Volatile
        private var INSTANCE: CivicDatabase? = null

        fun getDatabase(context: Context): CivicDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    CivicDatabase::class.java,
                    "civic_router_database"
                )
                    .fallbackToDestructiveMigration()
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
