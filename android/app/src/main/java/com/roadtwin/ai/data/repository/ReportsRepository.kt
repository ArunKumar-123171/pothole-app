package com.roadtwin.ai.data.repository

import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import kotlinx.coroutines.flow.Flow

interface ReportsRepository {
    // Reactive Observables
    fun getAllSessions(): Flow<List<MonitoringSessionEntity>>
    fun getSessionById(sessionId: String): Flow<MonitoringSessionEntity?>
    fun getDetectionsForSession(sessionId: String): Flow<List<DetectionEntity>>
    fun getAllDetections(): Flow<List<DetectionEntity>>
    fun getTotalDetectionsCount(): Flow<Int>
    fun getTotalSessionsCount(): Flow<Int>

    // Synchronous / Suspend Operations
    suspend fun getDetectionById(id: Int): DetectionEntity?
    suspend fun getSessionDirect(sessionId: String): MonitoringSessionEntity?
    suspend fun getDetectionsForSessionDirect(sessionId: String): List<DetectionEntity>

    // Session Lifecycle
    suspend fun startNewSession(
        startLatitude: Double,
        startLongitude: Double,
        startAddress: String
    ): MonitoringSessionEntity

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
