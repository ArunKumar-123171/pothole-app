package com.roadtwin.ai.data.local

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/**
 * Represents a discrete road monitoring session initiated by the user.
 * A session begins when the user taps "Start Monitoring" and completes when "Stop" is pressed.
 */
@Entity(tableName = "monitoring_sessions")
data class MonitoringSessionEntity(
    @PrimaryKey val sessionId: String, // E.g. "S-1001" or UUID
    val title: String = "Road Monitoring Session",
    val notes: String = "",
    val remarks: String = "",
    val startTime: Long = System.currentTimeMillis(),
    val endTime: Long = System.currentTimeMillis(),
    val startLatitude: Double = 0.0,
    val startLongitude: Double = 0.0,
    val startAddress: String = "Location pending",
    val endLatitude: Double = 0.0,
    val endLongitude: Double = 0.0,
    val endAddress: String = "Location pending",
    val distanceKm: Double = 0.0,
    val totalPotholes: Int = 0,
    val highSeverityCount: Int = 0,
    val mediumSeverityCount: Int = 0,
    val lowSeverityCount: Int = 0,
    val criticalSeverityCount: Int = 0,
    val status: String = "COMPLETED", // ACTIVE, COMPLETED, CANCELLED
    val syncStatus: String = "PENDING_UPLOAD", // PENDING_UPLOAD, UPLOADING, SYNCED, FAILED
    val pdfLocalPath: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)

/**
 * Represents an individual pothole detection confirmed by YOLO26n (3+ stable frames)
 * during an active monitoring session.
 */
@Entity(
    tableName = "detections",
    indices = [Index(value = ["sessionId"])]
)
data class DetectionEntity(
    @PrimaryKey(autoGenerate = true) val id: Int = 0,
    val detectionId: String = "", // E.g. "D-1001"
    val sessionId: String = "", // Links to MonitoringSessionEntity.sessionId
    val latitude: Double = 0.0,
    val longitude: Double = 0.0,
    val gpsAccuracy: Float = 0.0f,
    val address: String = "Location unavailable",
    val confidence: Float = 0.0f,
    val severity: String = "MEDIUM", // Heuristically estimated: LOW, MEDIUM, HIGH, CRITICAL
    val severitySource: String = "HEURISTIC", // HEURISTIC, MANUAL_OVERRIDE
    val imagePath: String = "", // Local file path: roadtwin/reports/{sessionId}/{detectionId}.jpg
    val timestamp: Long = System.currentTimeMillis(),
    val roadId: String = "NH_544",
    val modelName: String = "pothole_detector.tflite",
    val modelVersion: String = "RoadTwin-YOLO26n-416-FP32",
    val modelInputSize: String = "416x416",
    val modelPrecision: String = "FP32",
    val confidenceThreshold: Float = 0.40f,
    val syncStatus: String = "PENDING_UPLOAD", // PENDING_UPLOAD, SYNCED, FAILED
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis()
)
