package com.roadtwin.ai.data.repository

import android.content.Context
import android.util.Log
import com.roadtwin.ai.core.pdf.PdfReportGenerator
import com.roadtwin.ai.data.local.*
import com.roadtwin.ai.data.remote.FirebaseSyncManager
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext
import java.io.File

private const val TAG = "DefaultReportsRepository"

class DefaultReportsRepository(
    private val context: Context,
    private val sessionDao: SessionDao,
    private val detectionDao: DetectionDao,
    private val routePointDao: RoutePointDao? = null,
    private val syncManager: FirebaseSyncManager? = null
) : ReportsRepository {

    override fun getAllSessions(): Flow<List<MonitoringSessionEntity>> =
        sessionDao.getAllSessions()

    override fun getCompletedSessions(): Flow<List<MonitoringSessionEntity>> =
        sessionDao.getCompletedSessions()

    override fun getSessionById(sessionId: String): Flow<MonitoringSessionEntity?> =
        sessionDao.observeSessionById(sessionId)

    override fun getDetectionsForSession(sessionId: String): Flow<List<DetectionEntity>> =
        detectionDao.getDetectionsForSession(sessionId)

    override fun getAllDetections(): Flow<List<DetectionEntity>> =
        detectionDao.getAllDetections()

    override fun getTotalDetectionsCount(): Flow<Int> =
        detectionDao.getDetectionsCount()

    override fun getTotalSessionsCount(): Flow<Int> =
        sessionDao.getSessionsCount()

    override fun getCompletedSessionsCount(): Flow<Int> =
        sessionDao.getCompletedSessionsCount()

    override fun getRoutePointsForSession(sessionId: String): Flow<List<RoutePointEntity>> =
        routePointDao?.getRoutePointsForSession(sessionId) ?: kotlinx.coroutines.flow.flowOf(emptyList())

    override suspend fun getDetectionById(id: Int): DetectionEntity? = withContext(Dispatchers.IO) {
        detectionDao.getDetectionById(id)
    }

    override suspend fun getSessionDirect(sessionId: String): MonitoringSessionEntity? = withContext(Dispatchers.IO) {
        sessionDao.getSessionById(sessionId)
    }

    override suspend fun getDetectionsForSessionDirect(sessionId: String): List<DetectionEntity> = withContext(Dispatchers.IO) {
        detectionDao.getDetectionsForSessionOnce(sessionId)
    }

    override suspend fun getRoutePointsForSessionDirect(sessionId: String): List<RoutePointEntity> = withContext(Dispatchers.IO) {
        routePointDao?.getRoutePointsForSessionOnce(sessionId) ?: emptyList()
    }

    override suspend fun updateSessionStartLocation(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        timestamp: Long,
        address: String
    ): Unit = withContext(Dispatchers.IO) {
        if (latitude == 0.0 && longitude == 0.0) return@withContext

        sessionDao.updateStartLocation(
            sessionId = sessionId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            timestamp = timestamp,
            address = address
        )

        // Record the initial GPS lock as the first point in RoutePointEntity if not already present
        val existingPoints = routePointDao?.getRoutePointsForSessionOnce(sessionId) ?: emptyList()
        if (existingPoints.isEmpty()) {
            routePointDao?.insert(
                RoutePointEntity(
                    sessionId = sessionId,
                    latitude = latitude,
                    longitude = longitude,
                    accuracy = accuracy,
                    timestamp = timestamp
                )
            )
        }
        Log.d(TAG, "Updated start location for session $sessionId -> ($latitude, $longitude, ±${accuracy}m)")
    }

    override suspend fun addRoutePoint(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        timestamp: Long
    ): Long = withContext(Dispatchers.IO) {
        if (latitude == 0.0 && longitude == 0.0) return@withContext -1L
        val point = RoutePointEntity(
            sessionId = sessionId,
            latitude = latitude,
            longitude = longitude,
            accuracy = accuracy,
            timestamp = timestamp
        )
        val id = routePointDao?.insert(point) ?: -1L
        id
    }

    override suspend fun cleanupAbandonedSessions(): Unit = withContext(Dispatchers.IO) {
        val activeSessions = sessionDao.getActiveSessions()
        for (active in activeSessions) {
            val detections = detectionDao.getDetectionsForSessionOnce(active.sessionId)
            if (detections.isEmpty() && active.distanceKm == 0.0) {
                // Empty active session orphaned by process kill or rapid abort
                val cancelled = active.copy(
                    status = "CANCELLED",
                    endTime = active.updatedAt,
                    syncStatus = "NOT_SYNCED",
                    syncedAt = null,
                    updatedAt = System.currentTimeMillis()
                )
                sessionDao.update(cancelled)
                Log.d(TAG, "Cleaned up empty abandoned active session #${active.sessionId} -> CANCELLED")
            } else {
                // Had recorded detections or distance - cleanly complete it to preserve user evidence
                val critical = detections.count { it.severity == "CRITICAL" }
                val high = detections.count { it.severity == "HIGH" }
                val medium = detections.count { it.severity == "MEDIUM" }
                val low = detections.count { it.severity == "LOW" }
                val completed = active.copy(
                    status = "COMPLETED",
                    endTime = active.updatedAt,
                    totalPotholes = detections.size,
                    criticalSeverityCount = critical,
                    highSeverityCount = high,
                    mediumSeverityCount = medium,
                    lowSeverityCount = low,
                    syncStatus = "PENDING_UPLOAD",
                    syncedAt = null,
                    updatedAt = System.currentTimeMillis()
                )
                sessionDao.update(completed)
                Log.d(TAG, "Recovered abandoned active session #${active.sessionId} with ${detections.size} detections -> COMPLETED")
            }
        }
    }

    private val sessionCounter = java.util.concurrent.atomic.AtomicInteger((System.currentTimeMillis() % 90000).toInt() + 10000)
    private val detectionCounter = java.util.concurrent.atomic.AtomicInteger(1000)

    override suspend fun startNewSession(
        startLatitude: Double,
        startLongitude: Double,
        startAddress: String,
        startAccuracy: Float?,
        startGpsTimestamp: Long?
    ): MonitoringSessionEntity = withContext(Dispatchers.IO) {
        // Clean up any previously abandoned active sessions
        cleanupAbandonedSessions()

        val seq = sessionCounter.incrementAndGet() % 100000
        val sessionId = "S-${seq.toString().padStart(5, '0')}"

        val session = MonitoringSessionEntity(
            sessionId = sessionId,
            title = "Monitoring $sessionId",
            notes = "",
            remarks = "",
            startTime = System.currentTimeMillis(),
            endTime = null, // Must remain null while ACTIVE
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            startAccuracy = startAccuracy,
            startGpsTimestamp = startGpsTimestamp,
            startAddress = startAddress,
            endLatitude = 0.0, // Initialized to 0.0 (never copied from startLatitude)
            endLongitude = 0.0,
            endAccuracy = null,
            endGpsTimestamp = null,
            endAddress = "",
            distanceKm = 0.0,
            totalPotholes = 0,
            highSeverityCount = 0,
            mediumSeverityCount = 0,
            lowSeverityCount = 0,
            criticalSeverityCount = 0,
            status = "ACTIVE",
            syncStatus = "NOT_SYNCED", // ACTIVE sessions start with NOT_SYNCED
            syncedAt = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        sessionDao.insert(session)

        // If a real valid location was provided at start, store first route point
        if (startLatitude != 0.0 && startLongitude != 0.0) {
            routePointDao?.insert(
                RoutePointEntity(
                    sessionId = sessionId,
                    latitude = startLatitude,
                    longitude = startLongitude,
                    accuracy = startAccuracy ?: 0f,
                    timestamp = startGpsTimestamp ?: session.startTime
                )
            )
        }

        Log.d(TAG, "Started new active monitoring session $sessionId at ($startLatitude, $startLongitude)")
        session
    }

    override suspend fun recordDetection(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        gpsAccuracy: Float,
        address: String,
        confidence: Float,
        severity: String,
        imagePath: String,
        modelName: String,
        modelVersion: String,
        modelInputSize: String,
        modelPrecision: String,
        confidenceThreshold: Float
    ): Long = withContext(Dispatchers.IO) {
        val dSeq = detectionCounter.incrementAndGet() % 100000
        val detectionId = "D-${dSeq.toString().padStart(5, '0')}"

        val detection = DetectionEntity(
            detectionId = detectionId,
            sessionId = sessionId,
            latitude = latitude,
            longitude = longitude,
            gpsAccuracy = gpsAccuracy,
            address = address,
            confidence = confidence,
            severity = severity,
            severitySource = "HEURISTIC",
            imagePath = imagePath,
            timestamp = System.currentTimeMillis(),
            modelName = modelName,
            modelVersion = modelVersion,
            modelInputSize = modelInputSize,
            modelPrecision = modelPrecision,
            confidenceThreshold = confidenceThreshold,
            syncStatus = "NOT_SYNCED",
            syncedAt = null,
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val insertedId = detectionDao.insert(detection)

        // Query all detections for this session to get accurate, race-free counts
        val currentSession = sessionDao.getSessionById(sessionId)
        if (currentSession != null) {
            val allDetections = detectionDao.getDetectionsForSessionOnce(sessionId)
            val updatedSession = currentSession.copy(
                totalPotholes = allDetections.size,
                criticalSeverityCount = allDetections.count { it.severity.equals("CRITICAL", ignoreCase = true) },
                highSeverityCount = allDetections.count { it.severity.equals("HIGH", ignoreCase = true) },
                mediumSeverityCount = allDetections.count { it.severity.equals("MEDIUM", ignoreCase = true) },
                lowSeverityCount = allDetections.count { it.severity.equals("LOW", ignoreCase = true) },
                updatedAt = System.currentTimeMillis()
            )
            sessionDao.update(updatedSession)
        }

        Log.d(TAG, "Recorded pothole #$detectionId for session $sessionId ($severity, ${(confidence * 100).toInt()}%)")
        insertedId
    }

    override suspend fun finishSession(
        sessionId: String,
        endLatitude: Double,
        endLongitude: Double,
        endAddress: String,
        distanceKm: Double,
        endAccuracy: Float?,
        endGpsTimestamp: Long?,
        title: String,
        notes: String,
        remarks: String
    ): MonitoringSessionEntity = withContext(Dispatchers.IO) {
        val existing = sessionDao.getSessionById(sessionId)
            ?: throw IllegalStateException("Session $sessionId not found")

        val detections = detectionDao.getDetectionsForSessionOnce(sessionId)
        val critical = detections.count { it.severity.equals("CRITICAL", ignoreCase = true) }
        val high = detections.count { it.severity.equals("HIGH", ignoreCase = true) }
        val medium = detections.count { it.severity.equals("MEDIUM", ignoreCase = true) }
        val low = detections.count { it.severity.equals("LOW", ignoreCase = true) }

        // If a real end location was acquired, record final route point if distinct
        if (endLatitude != 0.0 && endLongitude != 0.0) {
            val lastPoints = routePointDao?.getRoutePointsForSessionOnce(sessionId) ?: emptyList()
            val lastPt = lastPoints.lastOrNull()
            if (lastPt == null || lastPt.latitude != endLatitude || lastPt.longitude != endLongitude) {
                routePointDao?.insert(
                    RoutePointEntity(
                        sessionId = sessionId,
                        latitude = endLatitude,
                        longitude = endLongitude,
                        accuracy = endAccuracy ?: 0f,
                        timestamp = endGpsTimestamp ?: System.currentTimeMillis()
                    )
                )
            }
        }

        val completed = existing.copy(
            title = if (title.isNotBlank()) title else existing.title,
            notes = if (notes.isNotBlank()) notes else existing.notes,
            remarks = if (remarks.isNotBlank()) remarks else existing.remarks,
            endTime = System.currentTimeMillis(), // Populated ONLY on Stop Monitoring
            endLatitude = endLatitude, // Real end location or 0.0 if not available
            endLongitude = endLongitude,
            endAccuracy = endAccuracy,
            endGpsTimestamp = endGpsTimestamp,
            endAddress = endAddress,
            distanceKm = distanceKm,
            totalPotholes = detections.size,
            criticalSeverityCount = critical,
            highSeverityCount = high,
            mediumSeverityCount = medium,
            lowSeverityCount = low,
            status = "COMPLETED", // ACTIVE -> COMPLETED
            syncStatus = "PENDING_UPLOAD", // COMPLETED -> PENDING_UPLOAD
            syncedAt = null,
            updatedAt = System.currentTimeMillis()
        )

        sessionDao.update(completed)
        Log.d(TAG, "Finished session $sessionId. Total potholes: ${detections.size}, Distance: $distanceKm km, End: ($endLatitude, $endLongitude)")
        completed
    }

    override suspend fun updateSessionReport(
        sessionId: String,
        title: String,
        notes: String,
        remarks: String,
        severityOverrides: Map<Int, String>,
        removedDetectionIds: List<Int>
    ) = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId) ?: return@withContext

        // 1. Remove requested detections and their image files
        for (detId in removedDetectionIds) {
            val d = detectionDao.getDetectionById(detId)
            if (d != null) {
                val imgFile = File(d.imagePath)
                if (imgFile.exists()) {
                    imgFile.delete()
                }
                detectionDao.deleteById(detId)
            }
        }

        // 2. Apply manual severity overrides
        for ((detId, newSeverity) in severityOverrides) {
            val d = detectionDao.getDetectionById(detId)
            if (d != null && d.severity != newSeverity) {
                val updatedDet = d.copy(
                    severity = newSeverity,
                    severitySource = "MANUAL_OVERRIDE",
                    syncStatus = "PENDING_UPLOAD",
                    syncedAt = null,
                    updatedAt = System.currentTimeMillis()
                )
                detectionDao.update(updatedDet)
            }
        }

        // 3. Recalculate session severity distribution and totals
        val remainingDetections = detectionDao.getDetectionsForSessionOnce(sessionId)
        val critical = remainingDetections.count { it.severity.equals("CRITICAL", ignoreCase = true) }
        val high = remainingDetections.count { it.severity.equals("HIGH", ignoreCase = true) }
        val medium = remainingDetections.count { it.severity.equals("MEDIUM", ignoreCase = true) }
        val low = remainingDetections.count { it.severity.equals("LOW", ignoreCase = true) }

        val updatedSession = session.copy(
            title = title.ifBlank { session.title },
            notes = notes,
            remarks = remarks,
            totalPotholes = remainingDetections.size,
            criticalSeverityCount = critical,
            highSeverityCount = high,
            mediumSeverityCount = medium,
            lowSeverityCount = low,
            syncStatus = "PENDING_UPLOAD", // Editing a SYNCED or PENDING report returns it to PENDING_UPLOAD
            syncedAt = null, // Cleared until next successful cloud sync
            updatedAt = System.currentTimeMillis()
        )

        sessionDao.update(updatedSession)
        Log.d(TAG, "Updated report for session $sessionId with ${remainingDetections.size} detections. State returned to PENDING_UPLOAD.")
    }

    override suspend fun deleteSession(sessionId: String) = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId) ?: return@withContext
        val detections = detectionDao.getDetectionsForSessionOnce(sessionId)

        // Delete all local image files
        for (d in detections) {
            val img = File(d.imagePath)
            if (img.exists()) {
                img.delete()
            }
        }

        // Delete local session folder
        val sessionDir = File(context.filesDir, "roadtwin/reports/$sessionId")
        if (sessionDir.exists()) {
            sessionDir.deleteRecursively()
        }

        // Delete local PDF file if exists
        session.pdfLocalPath?.let { path ->
            val pdf = File(path)
            if (pdf.exists()) {
                pdf.delete()
            }
        }

        // Delete from Room
        routePointDao?.deleteBySessionId(sessionId)
        detectionDao.deleteBySessionId(sessionId)
        sessionDao.deleteById(sessionId)
        Log.d(TAG, "Permanently deleted session $sessionId and its associated assets.")
    }

    override suspend fun deleteDetection(detectionId: Int) = withContext(Dispatchers.IO) {
        val d = detectionDao.getDetectionById(detectionId) ?: return@withContext
        val img = File(d.imagePath)
        if (img.exists()) {
            img.delete()
        }

        detectionDao.deleteById(detectionId)

        // Update parent session counts
        val session = sessionDao.getSessionById(d.sessionId)
        if (session != null) {
            val remaining = detectionDao.getDetectionsForSessionOnce(d.sessionId)
            val updated = session.copy(
                totalPotholes = remaining.size,
                criticalSeverityCount = remaining.count { it.severity.equals("CRITICAL", ignoreCase = true) },
                highSeverityCount = remaining.count { it.severity.equals("HIGH", ignoreCase = true) },
                mediumSeverityCount = remaining.count { it.severity.equals("MEDIUM", ignoreCase = true) },
                lowSeverityCount = remaining.count { it.severity.equals("LOW", ignoreCase = true) },
                updatedAt = System.currentTimeMillis()
            )
            sessionDao.update(updated)
        }
    }

    override suspend fun generateSessionPdf(sessionId: String): String = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId)
            ?: throw IllegalStateException("Session $sessionId not found")

        // Finalized report status validation: active or incomplete sessions must not generate a final report
        if (session.status != "COMPLETED" || session.endTime == null) {
            throw IllegalStateException("Cannot generate final inspection report: session is ACTIVE or incomplete.")
        }

        val detections = detectionDao.getDetectionsForSessionOnce(sessionId)
        val routePoints = routePointDao?.getRoutePointsForSessionOnce(sessionId) ?: emptyList()

        // Data reconciliation: ensure session counters match actual DetectionEntity records
        val actualTotal = detections.size
        val actualCritical = detections.count { it.severity.equals("CRITICAL", ignoreCase = true) }
        val actualHigh = detections.count { it.severity.equals("HIGH", ignoreCase = true) }
        val actualMedium = detections.count { it.severity.equals("MEDIUM", ignoreCase = true) }
        val actualLow = detections.count { it.severity.equals("LOW", ignoreCase = true) }

        val reconciledSession = if (session.totalPotholes != actualTotal ||
            session.criticalSeverityCount != actualCritical ||
            session.highSeverityCount != actualHigh ||
            session.mediumSeverityCount != actualMedium ||
            session.lowSeverityCount != actualLow
        ) {
            val updated = session.copy(
                totalPotholes = actualTotal,
                criticalSeverityCount = actualCritical,
                highSeverityCount = actualHigh,
                mediumSeverityCount = actualMedium,
                lowSeverityCount = actualLow,
                updatedAt = System.currentTimeMillis()
            )
            sessionDao.update(updated)
            updated
        } else {
            session
        }

        val generatedPath = PdfReportGenerator.generateSessionPdf(context, reconciledSession, detections, routePoints)
        sessionDao.updatePdfPath(sessionId, generatedPath)
        generatedPath
    }

    override suspend fun syncPendingReports(): Unit = withContext(Dispatchers.IO) {
        if (syncManager != null && syncManager.isFirebaseAvailable()) {
            Log.d(TAG, "Triggering Firebase synchronization via FirebaseSyncManager...")
            syncManager.syncPendingReports()
        } else {
            Log.d(TAG, "Firebase unavailable or not configured. Reports will remain PENDING_UPLOAD.")
        }
        Unit
    }
}
