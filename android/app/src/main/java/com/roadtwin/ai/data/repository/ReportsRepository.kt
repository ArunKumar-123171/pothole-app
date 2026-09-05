package com.roadtwin.ai.data.repository

import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import com.roadtwin.ai.data.local.RoutePointEntity
import kotlinx.coroutines.flow.Flow

interface ReportsRepository {
    // Reactive Observables
    fun getAllSessions(): Flow<List<MonitoringSessionEntity>>
    fun getCompletedSessions(): Flow<List<MonitoringSessionEntity>>
    fun getSessionById(sessionId: String): Flow<MonitoringSessionEntity?>
    fun getDetectionsForSession(sessionId: String): Flow<List<DetectionEntity>>
    fun getAllDetections(): Flow<List<DetectionEntity>>
    fun getTotalDetectionsCount(): Flow<Int>
    fun getTotalSessionsCount(): Flow<Int>
    fun getCompletedSessionsCount(): Flow<Int>
    fun getRoutePointsForSession(sessionId: String): Flow<List<RoutePointEntity>>

    // Synchronous / Suspend Operations
    suspend fun getDetectionById(id: Int): DetectionEntity?
    suspend fun getSessionDirect(sessionId: String): MonitoringSessionEntity?
    suspend fun getDetectionsForSessionDirect(sessionId: String): List<DetectionEntity>
    suspend fun getRoutePointsForSessionDirect(sessionId: String): List<RoutePointEntity>

    // Session Lifecycle
    suspend fun startNewSession(
        startLatitude: Double = 0.0,
        startLongitude: Double = 0.0,
        startAddress: String = "Waiting for GPS...",
        startAccuracy: Float? = null,
        startGpsTimestamp: Long? = null
    ): MonitoringSessionEntity

    suspend fun updateSessionStartLocation(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        timestamp: Long,
        address: String
    )

    suspend fun addRoutePoint(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        timestamp: Long = System.currentTimeMillis()
    ): Long

    suspend fun cleanupAbandonedSessions()

    suspend fun recordDetection(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float,
        address: String,
        confidence: Float,
        severity: String,
        imagePath: String,
        modelName: String = "pothole_detector.tflite",
        modelVersion: String = "RoadTwin-YOLO26n-416-FP32",
        modelInputSize: String = "416x416",
        modelPrecision: String = "FP32",
        confidenceThreshold: Float = 0.40f
    ): Long

    suspend fun finishSession(
        sessionId: String,
        endLatitude: Double,
        endLongitude: Double,
        endAddress: String,
        distanceKm: Double,
        endAccuracy: Float? = null,
        endGpsTimestamp: Long? = null,
        title: String = "",
        notes: String = "",
        remarks: String = ""
    ): MonitoringSessionEntity

    // Report Editing
    suspend fun updateSessionReport(
        sessionId: String,
        title: String,
        notes: String,
        remarks: String,
        severityOverrides: Map<Int, String> = emptyMap(),
        removedDetectionIds: List<Int> = emptyList()
    )

    // Deletion & Local Cleanup
    suspend fun deleteSession(sessionId: String)
    suspend fun deleteDetection(detectionId: Int)

    // PDF Generation
    suspend fun generateSessionPdf(sessionId: String): String

    // Cloud Synchronization
    suspend fun syncPendingReports()
}
