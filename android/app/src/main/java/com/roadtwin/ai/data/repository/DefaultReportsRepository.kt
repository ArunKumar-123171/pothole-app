package com.roadtwin.ai.data.repository

import android.content.Context
import android.util.Log
import com.roadtwin.ai.core.pdf.PdfReportGenerator
import com.roadtwin.ai.data.local.DetectionDao
import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import com.roadtwin.ai.data.local.SessionDao
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
    private val syncManager: FirebaseSyncManager? = null
) : ReportsRepository {

    override fun getAllSessions(): Flow<List<MonitoringSessionEntity>> =
        sessionDao.getAllSessions()

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

    override suspend fun getDetectionById(id: Int): DetectionEntity? = withContext(Dispatchers.IO) {
        detectionDao.getDetectionById(id)
    }

    override suspend fun getSessionDirect(sessionId: String): MonitoringSessionEntity? = withContext(Dispatchers.IO) {
        sessionDao.getSessionById(sessionId)
    }

    override suspend fun getDetectionsForSessionDirect(sessionId: String): List<DetectionEntity> = withContext(Dispatchers.IO) {
        detectionDao.getDetectionsForSessionOnce(sessionId)
    }

    override suspend fun startNewSession(
        startLatitude: Double,
        startLongitude: Double,
        startAddress: String
    ): MonitoringSessionEntity = withContext(Dispatchers.IO) {
        val sessionSeq = (System.currentTimeMillis() % 100000).toString().padStart(5, '0')
        val sessionId = "S-$sessionSeq"

        val session = MonitoringSessionEntity(
            sessionId = sessionId,
            title = "Monitoring $sessionId",
            notes = "",
            remarks = "",
            startTime = System.currentTimeMillis(),
            endTime = System.currentTimeMillis(),
            startLatitude = startLatitude,
            startLongitude = startLongitude,
            startAddress = startAddress,
            endLatitude = startLatitude,
            endLongitude = startLongitude,
            endAddress = startAddress,
            distanceKm = 0.0,
            totalPotholes = 0,
            highSeverityCount = 0,
            mediumSeverityCount = 0,
            lowSeverityCount = 0,
            criticalSeverityCount = 0,
            status = "ACTIVE",
            syncStatus = "PENDING_UPLOAD",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        sessionDao.insert(session)
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
        val detectionSeq = (System.currentTimeMillis() % 100000).toString().padStart(5, '0')
        val detectionId = "D-$detectionSeq"

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
            syncStatus = "PENDING_UPLOAD",
            createdAt = System.currentTimeMillis(),
            updatedAt = System.currentTimeMillis()
        )

        val insertedId = detectionDao.insert(detection)

        // Update counts on the parent session
        val currentSession = sessionDao.getSessionById(sessionId)
        if (currentSession != null) {
            val updatedSession = currentSession.copy(
                totalPotholes = currentSession.totalPotholes + 1,
                criticalSeverityCount = if (severity == "CRITICAL") currentSession.criticalSeverityCount + 1 else currentSession.criticalSeverityCount,
                highSeverityCount = if (severity == "HIGH") currentSession.highSeverityCount + 1 else currentSession.highSeverityCount,
                mediumSeverityCount = if (severity == "MEDIUM") currentSession.mediumSeverityCount + 1 else currentSession.mediumSeverityCount,
                lowSeverityCount = if (severity == "LOW") currentSession.lowSeverityCount + 1 else currentSession.lowSeverityCount,
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
        title: String,
        notes: String,
        remarks: String
    ): MonitoringSessionEntity = withContext(Dispatchers.IO) {
        val existing = sessionDao.getSessionById(sessionId)
            ?: throw IllegalStateException("Session $sessionId not found")

        val detections = detectionDao.getDetectionsForSessionOnce(sessionId)
        val critical = detections.count { it.severity == "CRITICAL" }
        val high = detections.count { it.severity == "HIGH" }
        val medium = detections.count { it.severity == "MEDIUM" }
        val low = detections.count { it.severity == "LOW" }

        val completed = existing.copy(
            title = if (title.isNotBlank()) title else existing.title,
            notes = if (notes.isNotBlank()) notes else existing.notes,
            remarks = if (remarks.isNotBlank()) remarks else existing.remarks,
            endTime = System.currentTimeMillis(),
            endLatitude = if (endLatitude != 0.0) endLatitude else existing.endLatitude,
            endLongitude = if (endLongitude != 0.0) endLongitude else existing.endLongitude,
            endAddress = if (endAddress.isNotBlank()) endAddress else existing.endAddress,
            distanceKm = distanceKm,
            totalPotholes = detections.size,
            criticalSeverityCount = critical,
            highSeverityCount = high,
            mediumSeverityCount = medium,
            lowSeverityCount = low,
            status = "COMPLETED",
            syncStatus = "PENDING_UPLOAD",
            updatedAt = System.currentTimeMillis()
        )

        sessionDao.update(completed)
        Log.d(TAG, "Finished session $sessionId. Total potholes: ${detections.size}, Distance: $distanceKm km")
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
                    updatedAt = System.currentTimeMillis()
                )
                detectionDao.update(updatedDet)
            }
        }

        // 3. Recalculate session severity distribution and totals
        val remainingDetections = detectionDao.getDetectionsForSessionOnce(sessionId)
        val critical = remainingDetections.count { it.severity == "CRITICAL" }
        val high = remainingDetections.count { it.severity == "HIGH" }
        val medium = remainingDetections.count { it.severity == "MEDIUM" }
        val low = remainingDetections.count { it.severity == "LOW" }

        val updatedSession = session.copy(
            title = title.ifBlank { session.title },
            notes = notes,
            remarks = remarks,
            totalPotholes = remainingDetections.size,
            criticalSeverityCount = critical,
            highSeverityCount = high,
            mediumSeverityCount = medium,
            lowSeverityCount = low,
            syncStatus = "PENDING_UPLOAD",
            updatedAt = System.currentTimeMillis()
        )

        sessionDao.update(updatedSession)
        Log.d(TAG, "Updated report for session $sessionId with ${remainingDetections.size} detections.")
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
                criticalSeverityCount = remaining.count { it.severity == "CRITICAL" },
                highSeverityCount = remaining.count { it.severity == "HIGH" },
                mediumSeverityCount = remaining.count { it.severity == "MEDIUM" },
                lowSeverityCount = remaining.count { it.severity == "LOW" },
                updatedAt = System.currentTimeMillis()
            )
            sessionDao.update(updated)
        }
    }

    override suspend fun generateSessionPdf(sessionId: String): String = withContext(Dispatchers.IO) {
        val session = sessionDao.getSessionById(sessionId)
            ?: throw IllegalStateException("Session $sessionId not found")
        val detections = detectionDao.getDetectionsForSessionOnce(sessionId)

        val generatedPath = PdfReportGenerator.generateSessionPdf(context, session, detections)
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
