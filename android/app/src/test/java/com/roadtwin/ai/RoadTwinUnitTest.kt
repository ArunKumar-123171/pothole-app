package com.roadtwin.ai

import android.graphics.RectF
import com.roadtwin.ai.core.location.LocationManager
import com.roadtwin.ai.ml.inference.DetectionResult
import com.roadtwin.ai.ml.model.ModelConfig
import com.roadtwin.ai.ml.tracking.DetectionTracker
import org.junit.Assert.*
import org.junit.Test
import java.io.File
import java.io.FileInputStream

class RoadTwinUnitTest {

    private fun createBox(left: Float, top: Float, right: Float, bottom: Float): RectF {
        return RectF().apply {
            this.left = left
            this.top = top
            this.right = right
            this.bottom = bottom
        }
    }

    @Test
    fun testModelFileExistsAndHasValidFlatBufferHeader() {
        val modelFile = File("src/main/assets/pothole_detector.tflite")
        assertTrue("Model file should exist in assets", modelFile.exists())
        assertTrue("Model file size should be greater than 1MB", modelFile.length() > 1_000_000)

        // Verify TFL3 identifier at bytes 4..7
        val bytes = ByteArray(8)
        FileInputStream(modelFile).use { it.read(bytes) }
        val identifier = String(bytes, 4, 4, Charsets.US_ASCII)
        assertEquals("TFL3", identifier)
    }

    @Test
    fun testDetectionTrackerRequiresThreeFramesForConfirmation() {
        val tracker = DetectionTracker()
        var confirmedCount = 0

        val box = createBox(0.2f, 0.2f, 0.4f, 0.4f)
        val detection = DetectionResult(
            boundingBox = box,
            confidence = 0.85f,
            classIndex = 0,
            label = "pothole"
        )

        // Frame 1
        tracker.update(listOf(detection)) { confirmedCount++ }
        assertEquals("Frame 1 should not trigger confirmation", 0, confirmedCount)

        // Frame 2
        tracker.update(listOf(detection)) { confirmedCount++ }
        assertEquals("Frame 2 should not trigger confirmation", 0, confirmedCount)

        // Frame 3 (Stability threshold met: 3 consecutive frames)
        tracker.update(listOf(detection)) { confirmedCount++ }
        assertEquals("Frame 3 should trigger confirmation", 1, confirmedCount)

        // Frame 4 (Same active track should not re-trigger duplicate event)
        tracker.update(listOf(detection)) { confirmedCount++ }
        assertEquals("Frame 4 on same track should not re-trigger", 1, confirmedCount)
    }

    @Test
    fun testSimultaneousMultiplePotholesTracking() {
        val tracker = DetectionTracker()
        val confirmedEvents = mutableListOf<DetectionResult>()

        val boxA = createBox(0.1f, 0.1f, 0.3f, 0.3f)
        val boxB = createBox(0.6f, 0.6f, 0.8f, 0.8f)

        val detA = DetectionResult(boxA, 0.88f, 0, "pothole")
        val detB = DetectionResult(boxB, 0.79f, 0, "pothole")

        // 3 frames with both A and B present simultaneously
        for (i in 1..3) {
            tracker.update(listOf(detA, detB)) { confirmedEvents.add(it) }
        }

        assertEquals("Both distinct potholes should confirm separately", 2, confirmedEvents.size)
    }

    @Test
    fun testDuplicateLocationSuppression() {
        val tracker = DetectionTracker()
        val lat = 11.0168
        val lon = 76.9558

        // First check: should not be duplicate (and records coordinate)
        assertFalse(tracker.isDuplicateLocation(lat, lon))

        // Second check within same location (< 20m): should be detected as duplicate
        assertTrue(tracker.isDuplicateLocation(lat, lon))

        // Check distant location (> 1km away): should not be duplicate
        val farLat = 11.0300
        val farLon = 76.9700
        assertFalse(tracker.isDuplicateLocation(farLat, farLon))
    }

    @Test
    fun testSpatialCooldownBoundaryConditions() {
        val tracker = DetectionTracker()
        val lat1 = 11.016800
        val lon1 = 76.955800

        // Initial record
        assertFalse(tracker.isDuplicateLocation(lat1, lon1))

        // Very close coordinate (~5 meters away): suppressed
        val closeLat = 11.016840
        val closeLon = 76.955800
        assertTrue("Location within 20m must be suppressed", tracker.isDuplicateLocation(closeLat, closeLon))

        // Coordinate ~50 meters away: allowed
        val farLat = 11.017300
        val farLon = 76.955800
        assertFalse("Location >20m must not be suppressed", tracker.isDuplicateLocation(farLat, farLon))
    }

    @Test
    fun testModelConfigConstants() {
        assertEquals("pothole_detector.tflite", ModelConfig.MODEL_NAME)
        assertEquals("RoadTwin-YOLO26n-416-FP32", ModelConfig.MODEL_VERSION)
        assertEquals(416, ModelConfig.INPUT_WIDTH)
        assertEquals(416, ModelConfig.INPUT_HEIGHT)
        assertEquals(300, ModelConfig.OUTPUT_MAX_BOXES)
        assertEquals(6, ModelConfig.OUTPUT_BOX_ELEMENTS)
        assertEquals(3, ModelConfig.minStableFrames)
        assertEquals(0.40f, ModelConfig.confidenceThreshold, 0.001f)
    }

    @Test
    fun testSeverityHeuristic() {
        // Critical: area > 0.15 or conf > 0.90
        val criticalBox = createBox(0f, 0f, 0.5f, 0.4f) // area = 0.20
        val criticalDetection = DetectionResult(criticalBox, 0.92f, 0, "pothole")
        assertEquals("CRITICAL", estimateSeverity(criticalDetection))

        // High: area > 0.08 or conf > 0.75
        val highBox = createBox(0f, 0f, 0.3f, 0.3f) // area = 0.09
        val highDetection = DetectionResult(highBox, 0.78f, 0, "pothole")
        assertEquals("HIGH", estimateSeverity(highDetection))

        // Medium: area > 0.03 or conf > 0.55
        val mediumBox = createBox(0f, 0f, 0.2f, 0.2f) // area = 0.04
        val mediumDetection = DetectionResult(mediumBox, 0.60f, 0, "pothole")
        assertEquals("MEDIUM", estimateSeverity(mediumDetection))

        // Low
        val lowBox = createBox(0f, 0f, 0.1f, 0.1f) // area = 0.01
        val lowDetection = DetectionResult(lowBox, 0.45f, 0, "pothole")
        assertEquals("LOW", estimateSeverity(lowDetection))
    }

    @Test
    fun testHaversineDistanceCalculation() {
        val lat1 = 11.0168
        val lon1 = 76.9558
        val lat2 = 11.0252
        val lon2 = 76.9740

        val distanceKm = LocationManager.calculateDistanceKm(lat1, lon1, lat2, lon2)
        assertTrue("Distance should be approximately 2.2 km", distanceKm in 1.8..2.6)

        // Same location delta should equal 0.0
        val zeroDelta = LocationManager.calculateDistanceKm(lat1, lon1, lat1, lon1)
        assertEquals(0.0, zeroDelta, 0.0001)
    }

    private fun estimateSeverity(detection: DetectionResult): String {
        val box = detection.boundingBox
        val area = (box.right - box.left) * (box.bottom - box.top)
        val conf = detection.confidence
        return when {
            area > 0.15f || conf > 0.90f -> "CRITICAL"
            area > 0.08f || conf > 0.75f -> "HIGH"
            area > 0.03f || conf > 0.55f -> "MEDIUM"
            else -> "LOW"
        }
    }
}
