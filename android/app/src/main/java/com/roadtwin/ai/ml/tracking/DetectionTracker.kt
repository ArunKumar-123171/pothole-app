package com.roadtwin.ai.ml.tracking

import android.graphics.RectF
import android.util.Log
import com.roadtwin.ai.ml.inference.DetectionResult
import com.roadtwin.ai.ml.model.ModelConfig
import com.roadtwin.ai.ml.postprocessing.NMS
import kotlin.math.*

private const val TAG = "DetectionTracker"

data class TrackedPothole(
    val id: Int,
    var boundingBox: RectF,
    val firstDetectedTime: Long,
    var lastSeenTime: Long,
    var consecutiveFrames: Int,
    var isConfirmed: Boolean = false
)

data class RecentReport(
    val latitude: Double,
    val longitude: Double,
    val timestamp: Long
)

class DetectionTracker {
    private val activeTracks = mutableListOf<TrackedPothole>()
    private val recentReports = mutableListOf<RecentReport>()
    private var nextId = 1

    /**
     * Updates tracks with new detections from the current camera frame.
     * Triggers the callback for newly confirmed stable potholes (3+ compatible frames).
     */
    fun update(
        detections: List<DetectionResult>,
        onPotholeConfirmed: (DetectionResult) -> Unit
    ) {
        val currentTime = System.currentTimeMillis()

        // 1. Match new detections to existing tracks using IoU
        val unmatchedDetections = detections.toMutableList()
        val matchedTracks = mutableSetOf<TrackedPothole>()

        for (track in activeTracks) {
            var bestMatch: DetectionResult? = null
            var maxIoU = 0.30f // Minimum overlap to consider it the same pothole object

            for (detection in unmatchedDetections) {
                val iou = NMS.calculateIoU(track.boundingBox, detection.boundingBox)
                if (iou > maxIoU) {
                    maxIoU = iou
                    bestMatch = detection
                }
            }

            if (bestMatch != null) {
                track.boundingBox = bestMatch.boundingBox
                track.lastSeenTime = currentTime
                track.consecutiveFrames++
                matchedTracks.add(track)
                unmatchedDetections.remove(bestMatch)

                // Trigger confirmation only when stability threshold is met
                if (track.consecutiveFrames >= ModelConfig.minStableFrames && !track.isConfirmed) {
                    track.isConfirmed = true
                    Log.d(TAG, "Pothole track #${track.id} confirmed stable after ${track.consecutiveFrames} frames.")
                    onPotholeConfirmed(bestMatch)
                }
            }
        }

        // 2. Create new candidate tracks for unmatched detections
        for (detection in unmatchedDetections) {
            val newTrack = TrackedPothole(
                id = nextId++,
                boundingBox = detection.boundingBox,
                firstDetectedTime = currentTime,
                lastSeenTime = currentTime,
                consecutiveFrames = 1
            )
            activeTracks.add(newTrack)
            Log.d(TAG, "Started tracking new candidate pothole #${newTrack.id}")
        }

        // 3. Remove stale tracks (not seen for more than 500ms)
        activeTracks.removeAll { track ->
            (currentTime - track.lastSeenTime > 500) && !matchedTracks.contains(track)
        }
    }

    /**
     * Checks if a GPS coordinate is a duplicate of a recently reported pothole.
     */
    fun isDuplicateLocation(latitude: Double, longitude: Double): Boolean {
        if (latitude == 0.0 && longitude == 0.0) return false
        val currentTime = System.currentTimeMillis()

        // Remove reports older than the cooldown duration
        recentReports.removeAll { currentTime - it.timestamp > ModelConfig.cooldownTimeMs }

        for (report in recentReports) {
            val distance = calculateDistanceInMeters(
                latitude, longitude,
                report.latitude, report.longitude
            )
            if (distance < ModelConfig.cooldownDistanceMeters) {
                Log.d(TAG, "Suppressed duplicate pothole detection: distance ${String.format("%.1f", distance)}m < ${ModelConfig.cooldownDistanceMeters}m")
                return true
            }
        }

        // Record this coordinate for future duplicate checks
        recentReports.add(RecentReport(latitude, longitude, currentTime))
        return false
    }

    /**
     * Haversine formula to compute distance in meters between two GPS coordinates.
     */
    private fun calculateDistanceInMeters(
        lat1: Double, lon1: Double,
        lat2: Double, lon2: Double
    ): Double {
        val r = 6371000.0 // Earth radius in meters
        val dLat = Math.toRadians(lat2 - lat1)
        val dLon = Math.toRadians(lon2 - lon1)
        val a = sin(dLat / 2).pow(2.0) +
                cos(Math.toRadians(lat1)) * cos(Math.toRadians(lat2)) *
                sin(dLon / 2).pow(2.0)
        val c = 2 * atan2(sqrt(a), sqrt(1 - a))
        return r * c
    }

    fun clear() {
        activeTracks.clear()
        recentReports.clear()
    }
}
