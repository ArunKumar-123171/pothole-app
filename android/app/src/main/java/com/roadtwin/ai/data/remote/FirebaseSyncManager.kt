package com.roadtwin.ai.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.roadtwin.ai.data.local.DetectionDao
import com.roadtwin.ai.data.local.SessionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "FirebaseSyncManager"

class FirebaseSyncManager(
    private val context: Context,
    private val sessionDao: SessionDao,
    private val detectionDao: DetectionDao
) {
    /**
     * Checks if Firebase is initialized and available in the current application context.
     * Returns true if Google Services / FirebaseApp has been successfully configured.
     */
    fun isFirebaseAvailable(): Boolean {
        return try {
            FirebaseApp.getApps(context).isNotEmpty()
        } catch (e: Exception) {
            Log.d(TAG, "Firebase is not initialized: ${e.message}")
            false
        }
    }

    /**
     * Synchronizes all pending monitoring sessions and their detection metadata to Firestore.
     * ZERO Firebase Storage dependency - uploads structured JSON-like metadata only.
     * If Firebase is unconfigured or offline, this operates safely without crashing.
     */
    suspend fun syncPendingReports(): SyncResult = withContext(Dispatchers.IO) {
        if (!isFirebaseAvailable()) {
            Log.i(TAG, "Firebase is not configured. Operating in offline Room mode.")
            return@withContext SyncResult(success = true, uploadedCount = 0, message = "Offline mode - Firebase not configured")
        }

        val pendingSessions = sessionDao.getPendingSessions()
        if (pendingSessions.isEmpty()) {
            return@withContext SyncResult(success = true, uploadedCount = 0, message = "No pending reports to synchronize")
        }

        val firestore = try {
            FirebaseFirestore.getInstance()
        } catch (e: Exception) {
            Log.w(TAG, "Failed to obtain FirebaseFirestore instance", e)
            null
        }

        if (firestore == null) {
            return@withContext SyncResult(success = false, uploadedCount = 0, message = "Firestore service unavailable")
        }

        var successCount = 0
        var failCount = 0

        for (session in pendingSessions) {
            try {
                sessionDao.updateSyncStatus(session.sessionId, "UPLOADING")

                // Retrieve all detections belonging to this session
                val detections = detectionDao.getDetectionsForSessionOnce(session.sessionId)

                val detectionsData = detections.map { d ->
                    mapOf(
                        "detectionId" to d.detectionId.ifBlank { "D-${d.id}" },
                        "latitude" to d.latitude,
                        "longitude" to d.longitude,
                        "gpsAccuracy" to d.gpsAccuracy,
                        "address" to d.address,
                        "confidence" to d.confidence,
                        "severity" to d.severity,
                        "severitySource" to d.severitySource,
                        "timestamp" to d.timestamp,
                        "modelName" to d.modelName,
                        "modelVersion" to d.modelVersion,
                        "modelInputSize" to d.modelInputSize,
                        "modelPrecision" to d.modelPrecision
                    )
                }

                val sessionData = hashMapOf(
                    "sessionId" to session.sessionId,
                    "title" to session.title,
                    "notes" to session.notes,
                    "remarks" to session.remarks,
                    "startTime" to session.startTime,
                    "endTime" to session.endTime,
                    "distanceKm" to session.distanceKm,
                    "totalPotholes" to session.totalPotholes,
                    "criticalSeverityCount" to session.criticalSeverityCount,
                    "highSeverityCount" to session.highSeverityCount,
                    "mediumSeverityCount" to session.mediumSeverityCount,
                    "lowSeverityCount" to session.lowSeverityCount,
                    "startLocation" to mapOf(
                        "latitude" to session.startLatitude,
                        "longitude" to session.startLongitude,
                        "address" to session.startAddress
                    ),
                    "endLocation" to mapOf(
                        "latitude" to session.endLatitude,
                        "longitude" to session.endLongitude,
                        "address" to session.endAddress
                    ),
                    "detections" to detectionsData,
                    "status" to session.status,
                    "syncedAt" to System.currentTimeMillis()
                )

                // Upload metadata document to Firestore
                firestore.collection("reports")
                    .document(session.sessionId)
                    .set(sessionData)
                    .await()

                // Update Room state
                sessionDao.updateSyncStatus(session.sessionId, "SYNCED")
                for (d in detections) {
                    detectionDao.updateSyncStatus(d.id, "SYNCED")
                }

                successCount++
                Log.d(TAG, "Successfully synced report #${session.sessionId} with ${detections.size} detections to Firestore.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload metadata for session #${session.sessionId}", e)
                sessionDao.updateSyncStatus(session.sessionId, "FAILED")
                failCount++
            }
        }

        SyncResult(
            success = failCount == 0,
            uploadedCount = successCount,
            message = "Synced $successCount reports ($failCount failed)"
        )
    }

    data class SyncResult(
        val success: Boolean,
        val uploadedCount: Int,
        val message: String
    )
}
