package com.roadtwin.ai.data.local

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface SessionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: MonitoringSessionEntity): Long

    @Update
    suspend fun update(session: MonitoringSessionEntity)

    @Delete
    suspend fun delete(session: MonitoringSessionEntity)

    @Query("DELETE FROM monitoring_sessions WHERE sessionId = :sessionId")
    suspend fun deleteById(sessionId: String)

    @Query("SELECT * FROM monitoring_sessions WHERE sessionId = :sessionId LIMIT 1")
    suspend fun getSessionById(sessionId: String): MonitoringSessionEntity?

    @Query("SELECT * FROM monitoring_sessions WHERE sessionId = :sessionId LIMIT 1")
    fun observeSessionById(sessionId: String): Flow<MonitoringSessionEntity?>

    @Query("SELECT * FROM monitoring_sessions ORDER BY startTime DESC")
    fun getAllSessions(): Flow<List<MonitoringSessionEntity>>

    @Query("SELECT * FROM monitoring_sessions WHERE status = 'COMPLETED' ORDER BY startTime DESC")
    fun getCompletedSessions(): Flow<List<MonitoringSessionEntity>>

    @Query("SELECT * FROM monitoring_sessions WHERE status = 'ACTIVE'")
    suspend fun getActiveSessions(): List<MonitoringSessionEntity>

    @Query("SELECT * FROM monitoring_sessions WHERE status = 'COMPLETED' AND (syncStatus = 'PENDING_UPLOAD' OR syncStatus = 'FAILED') ORDER BY startTime ASC")
    suspend fun getPendingSessions(): List<MonitoringSessionEntity>

    @Query("UPDATE monitoring_sessions SET startLatitude = :latitude, startLongitude = :longitude, startAccuracy = :accuracy, startGpsTimestamp = :timestamp, startAddress = :address, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateStartLocation(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        timestamp: Long,
        address: String,
        updatedAt: Long = System.currentTimeMillis()
    )

    @Query("UPDATE monitoring_sessions SET syncStatus = :status, syncedAt = :syncedAt, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updateSyncStatus(sessionId: String, status: String, syncedAt: Long? = null, updatedAt: Long = System.currentTimeMillis())

    @Query("UPDATE monitoring_sessions SET pdfLocalPath = :pdfPath, updatedAt = :updatedAt WHERE sessionId = :sessionId")
    suspend fun updatePdfPath(sessionId: String, pdfPath: String, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM monitoring_sessions")
    fun getSessionsCount(): Flow<Int>

    @Query("SELECT COUNT(*) FROM monitoring_sessions WHERE status = 'COMPLETED'")
    fun getCompletedSessionsCount(): Flow<Int>

    @Query("DELETE FROM monitoring_sessions")
    suspend fun deleteAll()
}

@Dao
interface DetectionDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(detection: DetectionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(detections: List<DetectionEntity>)

    @Update
    suspend fun update(detection: DetectionEntity)

    @Delete
    suspend fun delete(detection: DetectionEntity)

    @Query("DELETE FROM detections WHERE id = :id")
    suspend fun deleteById(id: Int)

    @Query("DELETE FROM detections WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("SELECT * FROM detections WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getDetectionsForSession(sessionId: String): Flow<List<DetectionEntity>>

    @Query("SELECT * FROM detections WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getDetectionsForSessionOnce(sessionId: String): List<DetectionEntity>

    @Query("SELECT * FROM detections ORDER BY timestamp DESC")
    fun getAllDetections(): Flow<List<DetectionEntity>>

    @Query("SELECT * FROM detections WHERE id = :id LIMIT 1")
    suspend fun getDetectionById(id: Int): DetectionEntity?

    @Query("SELECT * FROM detections WHERE syncStatus = 'PENDING_UPLOAD' OR syncStatus = 'FAILED'")
    suspend fun getPendingDetections(): List<DetectionEntity>

    @Query("UPDATE detections SET syncStatus = :status, syncedAt = :syncedAt, updatedAt = :updatedAt WHERE id = :id")
    suspend fun updateSyncStatus(id: Int, status: String, syncedAt: Long? = null, updatedAt: Long = System.currentTimeMillis())

    @Query("SELECT COUNT(*) FROM detections")
    fun getDetectionsCount(): Flow<Int>

    @Query("SELECT * FROM detections WHERE severity = :severity ORDER BY timestamp DESC")
    fun getDetectionsBySeverity(severity: String): Flow<List<DetectionEntity>>

    @Query("SELECT * FROM detections ORDER BY timestamp DESC LIMIT :limit")
    fun getRecentDetections(limit: Int = 20): Flow<List<DetectionEntity>>

    @Query("DELETE FROM detections")
    suspend fun deleteAll()
}

@Dao
interface RoutePointDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(point: RoutePointEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(points: List<RoutePointEntity>)

    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getRoutePointsForSession(sessionId: String): Flow<List<RoutePointEntity>>

    @Query("SELECT * FROM route_points WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    suspend fun getRoutePointsForSessionOnce(sessionId: String): List<RoutePointEntity>

    @Query("SELECT * FROM route_points ORDER BY timestamp ASC")
    fun getAllRoutePoints(): Flow<List<RoutePointEntity>>

    @Query("DELETE FROM route_points WHERE sessionId = :sessionId")
    suspend fun deleteBySessionId(sessionId: String)

    @Query("DELETE FROM route_points")
    suspend fun deleteAll()
}
