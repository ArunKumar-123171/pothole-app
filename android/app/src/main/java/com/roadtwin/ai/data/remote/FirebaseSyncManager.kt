package com.roadtwin.ai.data.remote

import android.content.Context
import android.util.Log
import com.google.firebase.FirebaseApp
import com.google.firebase.firestore.FirebaseFirestore
import com.roadtwin.ai.data.local.DetectionDao
import com.roadtwin.ai.data.local.RoutePointDao
import com.roadtwin.ai.data.local.SessionDao
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.tasks.await
import kotlinx.coroutines.withContext

private const val TAG = "FirebaseSyncManager"

class FirebaseSyncManager(
    private val context: Context,
    private val sessionDao: SessionDao,
    private val detectionDao: DetectionDao,
    private val routePointDao: RoutePointDao? = null
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
     * Synchronizes all pending monitoring sessions, real GPS routePoints, and detection metadata to Firestore.
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
            // Strict safeguard: ACTIVE sessions or sessions without valid endTime must NEVER be synced to Firestore
            if (session.status != "COMPLETED" || session.endTime == null) {
                Log.w(TAG, "Skipping session #${session.sessionId}: Cannot upload non-completed session (status=${session.status}, endTime=${session.endTime})")
                continue
            }

            try {
                sessionDao.updateSyncStatus(session.sessionId, "UPLOADING")

                // 1. Retrieve all detections belonging to this session
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

                // 2. Retrieve all continuous real GPS route points for this session
                val routePoints = routePointDao?.getRoutePointsForSessionOnce(session.sessionId) ?: emptyList()
                val routePointsData = routePoints.map { pt ->
                    mapOf(
                        "latitude" to pt.latitude,
                        "longitude" to pt.longitude,
                        "accuracy" to pt.accuracy,
                        "timestamp" to pt.timestamp
                    )
                }

                val criticalCount = detections.count { it.severity.equals("CRITICAL", ignoreCase = true) }
                val highCount = detections.count { it.severity.equals("HIGH", ignoreCase = true) }
                val mediumCount = detections.count { it.severity.equals("MEDIUM", ignoreCase = true) }
                val lowCount = detections.count { it.severity.equals("LOW", ignoreCase = true) }
                val totalCount = detections.size

                val syncTimestamp = System.currentTimeMillis()

                // Structured startLocation (with accuracy and timestamp)
                val startLocationData = if (session.startLatitude != 0.0 && session.startLongitude != 0.0) {
                    mapOf(
                        "latitude" to session.startLatitude,
                        "longitude" to session.startLongitude,
                        "accuracy" to (session.startAccuracy ?: 0f),
                        "timestamp" to (session.startGpsTimestamp ?: session.startTime)
                    )
                } else {
                    null
                }

                // Structured endLocation (null if no valid GPS fix was acquired at stop, never copied from start)
                val endLocationData = if (session.endLatitude != 0.0 && session.endLongitude != 0.0) {
                    mapOf(
                        "latitude" to session.endLatitude,
                        "longitude" to session.endLongitude,
                        "accuracy" to (session.endAccuracy ?: 0f),
                        "timestamp" to (session.endGpsTimestamp ?: session.endTime ?: syncTimestamp)
                    )
                } else {
                    null
                }

                val sessionData = hashMapOf(
                    "sessionId" to session.sessionId,
                    "title" to session.title,
                    "notes" to session.notes,
                    "remarks" to session.remarks,
                    "startTime" to session.startTime,
                    "endTime" to session.endTime,
                    "distanceKm" to session.distanceKm,
                    "totalPotholes" to totalCount,
                    "criticalSeverityCount" to criticalCount,
                    "highSeverityCount" to highCount,
                    "mediumSeverityCount" to mediumCount,
                    "lowSeverityCount" to lowCount,
                    "startLocation" to startLocationData,
                    "endLocation" to endLocationData,
                    "routePoints" to routePointsData,
                    "detections" to detectionsData,
                    "status" to "COMPLETED",
                    "syncStatus" to "SYNCED",
                    "syncedAt" to syncTimestamp
                )

                // Upload structured document to Firestore
                firestore.collection("reports")
                    .document(session.sessionId)
                    .set(sessionData)
                    .await()

                // Update Room state only AFTER successful Firestore write
                val updatedSession = session.copy(
                    totalPotholes = totalCount,
                    criticalSeverityCount = criticalCount,
                    highSeverityCount = highCount,
                    mediumSeverityCount = mediumCount,
                    lowSeverityCount = lowCount,
                    syncStatus = "SYNCED",
                    syncedAt = syncTimestamp,
                    updatedAt = syncTimestamp
                )
                sessionDao.update(updatedSession)

                for (d in detections) {
                    detectionDao.updateSyncStatus(d.id, "SYNCED", syncedAt = syncTimestamp, updatedAt = syncTimestamp)
                }

                successCount++
                Log.d(TAG, "Successfully synced report #${session.sessionId} with ${routePoints.size} route points and $totalCount detections to Firestore.")
            } catch (e: Exception) {
                Log.e(TAG, "Failed to upload metadata for session #${session.sessionId}", e)
                sessionDao.updateSyncStatus(session.sessionId, "PENDING_UPLOAD", syncedAt = null)
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
