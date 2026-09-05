package com.roadtwin.ai.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [
        MonitoringSessionEntity::class,
        DetectionEntity::class,
        RoutePointEntity::class
    ],
    version = 6,
    exportSchema = false
)
abstract class RoadTwinDatabase : RoomDatabase() {
    abstract fun sessionDao(): SessionDao
    abstract fun detectionDao(): DetectionDao
    abstract fun routePointDao(): RoutePointDao

    companion object {
        @Volatile
        private var INSTANCE: RoadTwinDatabase? = null

        val MIGRATION_4_5 = object : Migration(4, 5) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add syncedAt column to monitoring_sessions if not existing
                db.execSQL("ALTER TABLE monitoring_sessions ADD COLUMN syncedAt INTEGER DEFAULT NULL")

                // 2. Add syncedAt column to detections if not existing
                db.execSQL("ALTER TABLE detections ADD COLUMN syncedAt INTEGER DEFAULT NULL")

                // 3. Specifically repair legacy corrupted session records (e.g. S-03100)
                db.execSQL("""
                    UPDATE monitoring_sessions 
                    SET status = 'COMPLETED', 
                        syncStatus = 'PENDING_UPLOAD', 
                        syncedAt = NULL 
                    WHERE status = 'ACTIVE' AND totalPotholes > 0
                """.trimIndent())

                db.execSQL("""
                    UPDATE monitoring_sessions 
                    SET status = 'CANCELLED', 
                        endTime = updatedAt, 
                        syncStatus = 'NOT_SYNCED', 
                        syncedAt = NULL 
                    WHERE status = 'ACTIVE' AND totalPotholes = 0
                """.trimIndent())

                db.execSQL("""
                    UPDATE monitoring_sessions 
                    SET endTime = NULL, 
                        syncStatus = 'NOT_SYNCED', 
                        syncedAt = NULL 
                    WHERE status = 'ACTIVE'
                """.trimIndent())
            }
        }

        val MIGRATION_5_6 = object : Migration(5, 6) {
            override fun migrate(db: SupportSQLiteDatabase) {
                // 1. Add start/end GPS accuracy and GPS timestamp columns to monitoring_sessions
                db.execSQL("ALTER TABLE monitoring_sessions ADD COLUMN startAccuracy REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE monitoring_sessions ADD COLUMN startGpsTimestamp INTEGER DEFAULT NULL")
                db.execSQL("ALTER TABLE monitoring_sessions ADD COLUMN endAccuracy REAL DEFAULT NULL")
                db.execSQL("ALTER TABLE monitoring_sessions ADD COLUMN endGpsTimestamp INTEGER DEFAULT NULL")

                // 2. Create route_points table for continuous GPS breadcrumb persistence
                db.execSQL("""
                    CREATE TABLE IF NOT EXISTS route_points (
                        id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                        sessionId TEXT NOT NULL,
                        latitude REAL NOT NULL,
                        longitude REAL NOT NULL,
                        accuracy REAL NOT NULL,
                        timestamp INTEGER NOT NULL
                    )
                """.trimIndent())

                // 3. Create index on route_points(sessionId) for fast query performance
                db.execSQL("CREATE INDEX IF NOT EXISTS index_route_points_sessionId ON route_points(sessionId)")
            }
        }

        fun getDatabase(context: Context): RoadTwinDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    RoadTwinDatabase::class.java,
                    "roadtwin_database"
                )
                .addMigrations(MIGRATION_4_5, MIGRATION_5_6)
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
