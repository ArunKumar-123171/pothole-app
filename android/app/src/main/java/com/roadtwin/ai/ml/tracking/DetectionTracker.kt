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
    val trackId: Int,
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
     * Triggers the callback for newly confirmed stable potholes.
     */
    fun update(
        detections: List<DetectionResult>,
        onPotholeConfirmed: (TrackedPothole, DetectionResult) -> Unit
    ) {
        val currentTime = System.currentTimeMillis()

        // 1. Match new detections to existing tracks using IoU + Centroid motion dynamics
        val unmatchedDetections = detections.toMutableList()
        val matchedTracks = mutableSetOf<TrackedPothole>()

        val sortedTracks = activeTracks.sortedByDescending { it.consecutiveFrames }

        for (track in sortedTracks) {
            var bestMatch: DetectionResult? = null
            var bestScore = -1f

            val trackCenterX = (track.boundingBox.left + track.boundingBox.right) / 2f
            val trackCenterY = (track.boundingBox.top + track.boundingBox.bottom) / 2f

            for (detection in unmatchedDetections) {
                val detCenterX = (detection.boundingBox.left + detection.boundingBox.right) / 2f
                val detCenterY = (detection.boundingBox.top + detection.boundingBox.bottom) / 2f

                val dist = hypot(detCenterX - trackCenterX, detCenterY - trackCenterY)
                val iou = NMS.calculateIoU(track.boundingBox, detection.boundingBox)

                // In forward vehicle movement, potholes shift downward (detCenterY >= trackCenterY - 0.08)
                val isCompatibleMotion = detCenterY >= trackCenterY - 0.08f && dist < 0.25f
                val isIoUMatch = iou >= 0.15f

                if (isIoUMatch || isCompatibleMotion) {
                    val score = (iou * 2.0f) + (1.0f - dist.coerceAtMost(1.0f))
                    if (score > bestScore) {
                        bestScore = score
                        bestMatch = detection
                    }
                }
            }

            if (bestMatch != null) {
                track.boundingBox = bestMatch.boundingBox
                track.lastSeenTime = currentTime
                track.consecutiveFrames++
                matchedTracks.add(track)
                unmatchedDetections.remove(bestMatch)

                // Trigger confirmation when stability threshold is met
                if (track.consecutiveFrames >= ModelConfig.minStableFrames && !track.isConfirmed) {
                    track.isConfirmed = true
                    Log.d(TAG, "Pothole track #${track.id} confirmed stable after ${track.consecutiveFrames} frames.")
                    onPotholeConfirmed(track, bestMatch)
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

        // 3. Remove stale tracks (not seen for more than 750ms)
        activeTracks.removeAll { track ->
            (currentTime - track.lastSeenTime > 750) && !matchedTracks.contains(track)
        }
    }

    /**
     * Backward-compatible overload accepting single-argument callback.
     */
    fun update(
        detections: List<DetectionResult>,
        onPotholeConfirmed: (DetectionResult) -> Unit
    ) {
        update(detections) { _, detection -> onPotholeConfirmed(detection) }
    }

    /**
     * Checks if a GPS coordinate is a duplicate of a recently reported pothole.
     * Simultaneous tracks in the same frame/burst (<= 300ms) are never suppressed.
     * Re-detections at the same location within the 3m cooldown window (> 300ms) are suppressed.
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
            val timeDiff = currentTime - report.timestamp
            if (distance < ModelConfig.cooldownDistanceMeters && timeDiff > 300) {
                Log.d(TAG, "Suppressed duplicate pothole detection: distance ${String.format("%.1f", distance)}m < ${ModelConfig.cooldownDistanceMeters}m")
                return true
            }
        }

        // Record this coordinate for future duplicate checks
        recentReports.add(RecentReport(0, latitude, longitude, currentTime))
        return false
    }

    fun isDuplicateLocation(trackId: Int, latitude: Double, longitude: Double): Boolean {
        return isDuplicateLocation(latitude, longitude)
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
