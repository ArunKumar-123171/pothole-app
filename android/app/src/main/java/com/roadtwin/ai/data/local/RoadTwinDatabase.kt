package com.roadtwin.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [MonitoringSessionEntity::class, DetectionEntity::class],
    version = 4,
    exportSchema = false
)
abstract class RoadTwinDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun detectionDao(): DetectionDao

    companion object {
        @Volatile
        private var INSTANCE: RoadTwinDatabase? = null

        fun getDatabase(context: Context): RoadTwinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoadTwinDatabase::class.java,
                    "roadtwin_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
