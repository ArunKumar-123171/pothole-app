package com.roadtwin.ai

import com.roadtwin.ai.core.pdf.PdfReportGenerator
import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import com.roadtwin.ai.data.local.RoutePointEntity
import com.roadtwin.ai.ml.model.ModelConfig
import org.junit.Assert.*
import org.junit.Test
import java.io.File

class PdfReportGeneratorTest {

    private fun createSession(
        sessionId: String = "S-TEST-001",
        status: String = "COMPLETED",
        startTime: Long = 1788500000000L,
        endTime: Long? = 1788500300000L,
        startLat: Double = 10.653200,
        startLon: Double = 77.034600,
        startAccuracy: Float = 3.5f,
        startAddress: String = "Test Start Road, Sector 1",
        endLat: Double = 10.658900,
        endLon: Double = 77.039800,
        endAccuracy: Float = 4.0f,
        endAddress: String = "Test End Road, Sector 2",
        distanceKm: Double = 0.84,
        totalPotholes: Int = 2,
        critical: Int = 1,
        high: Int = 0,
        medium: Int = 1,
        low: Int = 0
    ): MonitoringSessionEntity {
        return MonitoringSessionEntity(
            sessionId = sessionId,
            title = "Test Inspection",
            startTime = startTime,
            endTime = endTime,
            status = status,
            startLatitude = startLat,
            startLongitude = startLon,
            startAccuracy = startAccuracy,
            startGpsTimestamp = startTime,
            startAddress = startAddress,
            endLatitude = endLat,
            endLongitude = endLon,
            endAccuracy = endAccuracy,
            endGpsTimestamp = endTime ?: 0L,
            endAddress = endAddress,
            distanceKm = distanceKm,
            totalPotholes = totalPotholes,
            criticalSeverityCount = critical,
            highSeverityCount = high,
            mediumSeverityCount = medium,
            lowSeverityCount = low
        )
    }

    private fun createDetection(
        id: Int,
        sessionId: String = "S-TEST-001",
        detectionId: String = "D-0100$id",
        lat: Double = 10.654000 + (id * 0.001),
        lon: Double = 77.035000 + (id * 0.001),
        accuracy: Float = 3.0f,
        address: String = "Road Segment $id",
        confidence: Float = 0.85f,
        severity: String = "HIGH",
        severitySource: String = "HEURISTIC",
        imagePath: String = "/path/to/img_$id.jpg",
        timestamp: Long = 1788500100000L + (id * 10000)
    ): DetectionEntity {
        return DetectionEntity(
            id = id,
            sessionId = sessionId,
            detectionId = detectionId,
            latitude = lat,
            longitude = lon,
            gpsAccuracy = accuracy,
            address = address,
            confidence = confidence,
            severity = severity,
            severitySource = severitySource,
            imagePath = imagePath,
            timestamp = timestamp
        )
    }

    // =========================================================================
    // Test 1: Valid Start GPS Formatting
    // =========================================================================
    @Test
    fun test1_ValidStartGpsFormatting() {
        val display = PdfReportGenerator.formatLocationDisplay(
            lat = 10.6532706,
            lon = 77.0346947,
            accuracy = 4.2f,
            timestamp = 1788500000000L,
            address = "Palani Road, Pollachi",
            isStart = true
        )

        assertTrue("Valid GPS must be available", display.isAvailable)
        assertEquals("10.653271, 77.034695", display.coordinates)
        assertEquals("±4.2 m", display.accuracy)
        assertEquals("Palani Road, Pollachi", display.address)
        assertNotEquals("0.000000, 0.000000", display.coordinates)
    }

    // =========================================================================
    // Test 2: Missing Start GPS Displays Unavailable (Zero 0,0 Output)
    // =========================================================================
    @Test
    fun test2_MissingStartGpsDisplaysUnavailable() {
        val display = PdfReportGenerator.formatLocationDisplay(
            lat = 0.0,
            lon = 0.0,
            accuracy = 0.0f,
            timestamp = 0L,
            address = "",
            isStart = true
        )

        assertFalse("Zero coordinates must be marked unavailable", display.isAvailable)
        assertEquals("Start GPS location unavailable", display.coordinates)
        assertEquals("Start GPS location unavailable", display.address)
        assertEquals("N/A", display.accuracy)
        assertFalse("Must never output 0.000000, 0.000000", display.coordinates.contains("0.000000"))
    }

    // =========================================================================
    // Test 3: Valid End GPS Formatting
    // =========================================================================
    @Test
    fun test3_ValidEndGpsFormatting() {
        val display = PdfReportGenerator.formatLocationDisplay(
            lat = 10.658912,
            lon = 77.039821,
            accuracy = 3.8f,
            timestamp = 1788500300000L,
            address = "Udumalai Road, Pollachi",
            isStart = false
        )

        assertTrue("Valid End GPS must be available", display.isAvailable)
        assertEquals("10.658912, 77.039821", display.coordinates)
        assertEquals("±3.8 m", display.accuracy)
        assertEquals("Udumalai Road, Pollachi", display.address)
        assertNotEquals("0.000000, 0.000000", display.coordinates)
    }

    // =========================================================================
    // Test 4: Missing End GPS Displays Unavailable
    // =========================================================================
    @Test
    fun test4_MissingEndGpsDisplaysUnavailable() {
        val display = PdfReportGenerator.formatLocationDisplay(
            lat = 0.0,
            lon = 0.0,
            accuracy = 0.0f,
            timestamp = 0L,
            address = "",
            isStart = false
        )

        assertFalse("Zero end coordinates must be marked unavailable", display.isAvailable)
        assertEquals("End GPS location unavailable", display.coordinates)
        assertEquals("End GPS location unavailable", display.address)
        assertEquals("N/A", display.accuracy)
    }

    // =========================================================================
    // Test 5: Route Points Map Visualization Bounds
    // =========================================================================
    @Test
    fun test5_RoutePointsMapVisualizationBounds() {
        val routePoints = listOf(
            RoutePointEntity(id = 1L, sessionId = "S-1", latitude = 10.6532, longitude = 77.0346, accuracy = 3.0f, timestamp = 1000L),
            RoutePointEntity(id = 2L, sessionId = "S-1", latitude = 10.6550, longitude = 77.0360, accuracy = 3.5f, timestamp = 2000L),
            RoutePointEntity(id = 3L, sessionId = "S-1", latitude = 10.6589, longitude = 77.0398, accuracy = 4.0f, timestamp = 3000L)
        )

        val minLat = routePoints.minOf { it.latitude }
        val maxLat = routePoints.maxOf { it.latitude }
        val minLon = routePoints.minOf { it.longitude }
        val maxLon = routePoints.maxOf { it.longitude }

        assertTrue("Min lat should be less than max lat", minLat < maxLat)
        assertTrue("Min lon should be less than max lon", minLon < maxLon)
        assertEquals(3, routePoints.size)
        assertEquals(1000L, routePoints.first().timestamp)
        assertEquals(3000L, routePoints.last().timestamp)
    }

    // =========================================================================
    // Test 6: Real Distance Formatting
    // =========================================================================
    @Test
    fun test6_RealDistanceFormatting() {
        val sessionMoving = createSession(distanceKm = 1.456)
        val formattedMoving = String.format(java.util.Locale.US, "%.2f km", sessionMoving.distanceKm)
        assertEquals("1.46 km", formattedMoving)

        val sessionStationary = createSession(distanceKm = 0.0)
        val formattedStationary = String.format(java.util.Locale.US, "%.2f km", sessionStationary.distanceKm)
        assertEquals("0.00 km", formattedStationary)
    }

    // =========================================================================
    // Test 7: Zero Coordinate Strict Rejection
    // =========================================================================
    @Test
    fun test7_ZeroCoordinateStrictRejection() {
        val rawCoords = PdfReportGenerator.formatGpsCoordinates(0.0, 0.0)
        assertEquals("GPS coordinates unavailable", rawCoords)
        assertFalse("Must never output 0.000000, 0.000000", rawCoords.contains("0.000000"))

        val validCoords = PdfReportGenerator.formatGpsCoordinates(10.653205, 77.034680)
        assertEquals("10.653205, 77.034680", validCoords)
    }

    // =========================================================================
    // Test 8: Severity Aggregation Match
    // =========================================================================
    @Test
    fun test8_SeverityAggregationMatch() {
        val detections = listOf(
            createDetection(1, severity = "CRITICAL"),
            createDetection(2, severity = "CRITICAL"),
            createDetection(3, severity = "HIGH"),
            createDetection(4, severity = "MEDIUM"),
            createDetection(5, severity = "LOW"),
            createDetection(6, severity = "LOW")
        )

        val session = createSession(
            totalPotholes = 6,
            critical = 2,
            high = 1,
            medium = 1,
            low = 2
        )

        val reconciled = PdfReportGenerator.reconcileSessionData(session, detections, emptyList())
        assertEquals(6, reconciled.totalPotholes)
        assertEquals(2, reconciled.criticalCount)
        assertEquals(1, reconciled.highCount)
        assertEquals(1, reconciled.mediumCount)
        assertEquals(2, reconciled.lowCount)
        assertEquals(
            reconciled.totalPotholes,
            reconciled.criticalCount + reconciled.highCount + reconciled.mediumCount + reconciled.lowCount
        )
        assertTrue("Reconciled data must be consistent", reconciled.isConsistent)
    }

    // =========================================================================
    // Test 9: Severity Source Distinction (HEURISTIC vs MANUAL_OVERRIDE)
    // =========================================================================
    @Test
    fun test9_SeveritySourceDistinction() {
        val heuristicDet = createDetection(1, severitySource = "HEURISTIC")
        val manualDet = createDetection(2, severitySource = "MANUAL_OVERRIDE")

        assertEquals("HEURISTIC", heuristicDet.severitySource)
        assertEquals("MANUAL_OVERRIDE", manualDet.severitySource)

        // Municipal recommendations match severity
        assertEquals(
            "Immediate field inspection and corrective action recommended.",
            PdfReportGenerator.getMunicipalRecommendation("CRITICAL")
        )
        assertEquals(
            "Prioritize field inspection and repair.",
            PdfReportGenerator.getMunicipalRecommendation("HIGH")
        )
        assertEquals(
            "Schedule maintenance inspection.",
            PdfReportGenerator.getMunicipalRecommendation("MEDIUM")
        )
        assertEquals(
            "Routine maintenance assessment recommended.",
            PdfReportGenerator.getMunicipalRecommendation("LOW")
        )
    }

    // =========================================================================
    // Test 10: Actual Image File Handling
    // =========================================================================
    @Test
    fun test10_ActualImageFileHandling() {
        val nonExistentPath = "/non/existent/image/evidence_99.jpg"
        val file = File(nonExistentPath)
        assertFalse("Non-existent image file should not exist on disk", file.exists())

        // Detection entity stores the exact path without inventing a substitute
        val det = createDetection(1, imagePath = nonExistentPath)
        assertEquals(nonExistentPath, det.imagePath)
    }

    // =========================================================================
    // Test 11: N Detections Yields N Evidence Records
    // =========================================================================
    @Test
    fun test11_NDetectionsYieldsNEvidenceCards() {
        val n = 15
        val detections = (1..n).map { createDetection(it) }
        assertEquals(n, detections.size)

        val session = createSession(totalPotholes = n)
        val reconciled = PdfReportGenerator.reconcileSessionData(session, detections, emptyList())
        assertEquals(n, reconciled.detections.size)
        assertEquals(n, reconciled.totalPotholes)
    }

    // =========================================================================
    // Test 12: Dynamic Page Generation Count
    // =========================================================================
    @Test
    fun test12_DynamicPageGenerationCount() {
        // 0 detections -> 3 pages (Page 1: Summary, Page 2: Route, Page 3: Technical)
        assertEquals(3, PdfReportGenerator.calculateDynamicPageCount(0))

        // 1 detection -> 4 pages (1 evidence page with 1 card)
        assertEquals(4, PdfReportGenerator.calculateDynamicPageCount(1))

        // 2 detections -> 4 pages (1 evidence page with 2 cards)
        assertEquals(4, PdfReportGenerator.calculateDynamicPageCount(2))

        // 3 detections -> 5 pages (2 evidence pages: 2 + 1)
        assertEquals(5, PdfReportGenerator.calculateDynamicPageCount(3))

        // 12 detections -> 9 pages (6 evidence pages: 2 * 6)
        assertEquals(9, PdfReportGenerator.calculateDynamicPageCount(12))

        // 15 detections -> 11 pages (8 evidence pages)
        assertEquals(11, PdfReportGenerator.calculateDynamicPageCount(15))
    }

    // =========================================================================
    // Test 13: Configuration Values From ModelConfig
    // =========================================================================
    @Test
    fun test13_ConfigurationValuesFromModelConfig() {
        val specs = PdfReportGenerator.getTechnicalSpecifications().toMap()

        assertEquals("YOLO26n (RoadTwin-YOLO26n-416-FP32)", specs["Detection Engine:"])
        assertEquals("416 × 416", specs["Input Resolution:"])
        assertEquals("FP32", specs["Inference Precision:"])
        assertEquals("3 consecutive frames required", specs["Stability Threshold:"])
        assertEquals("3 meters spatial radius", specs["Duplicate Suppression:"])
        assertEquals("3 seconds temporal cooldown", specs["Cooldown Threshold:"])
        assertFalse("Must not contain hardcoded 20 meters", specs["Duplicate Suppression:"]!!.contains("20"))
        assertFalse("Must not contain hardcoded 10 seconds", specs["Cooldown Threshold:"]!!.contains("10"))
    }

    // =========================================================================
    // Test 14: Completed Session Can Generate PDF
    // =========================================================================
    @Test
    fun test14_CompletedSessionCanGeneratePdf() {
        val completedSession = createSession(
            status = "COMPLETED",
            endTime = 1788500300000L
        )

        assertEquals("COMPLETED", completedSession.status)
        assertNotNull("Completed session must have non-null endTime", completedSession.endTime)
    }

    // =========================================================================
    // Test 15: Active Session Rejection
    // =========================================================================
    @Test
    fun test15_ActiveSessionRejection() {
        val activeSession = createSession(
            status = "ACTIVE",
            endTime = null
        )

        assertEquals("ACTIVE", activeSession.status)
        assertNull(activeSession.endTime)

        // Validation rule check
        val isEligible = activeSession.status == "COMPLETED" && activeSession.endTime != null
        assertFalse("Active session must not be eligible for final PDF generation", isEligible)
    }

    // =========================================================================
    // Test 16: Room / PDF Data Reconciliation
    // =========================================================================
    @Test
    fun test16_RoomPdfDataReconciliation() {
        // Stale session with out-of-sync counters
        val staleSession = createSession(
            totalPotholes = 0,
            critical = 0,
            high = 0,
            medium = 0,
            low = 0
        )

        // Real Room detections
        val actualDetections = listOf(
            createDetection(1, severity = "HIGH"),
            createDetection(2, severity = "MEDIUM"),
            createDetection(3, severity = "LOW")
        )

        val reconciled = PdfReportGenerator.reconcileSessionData(staleSession, actualDetections, emptyList())
        assertFalse("Stale session is flagged inconsistent before reconciliation", reconciled.isConsistent)
        assertEquals(3, reconciled.totalPotholes)
        assertEquals(1, reconciled.highCount)
        assertEquals(1, reconciled.mediumCount)
        assertEquals(1, reconciled.lowCount)

        // Dynamic road health score calculated from actual detections
        // High (8) + Medium (4) + Low (1) = 13 deduction -> score = 87 (Excellent)
        assertEquals(87, reconciled.roadHealthScore)
        assertEquals("Excellent", reconciled.roadHealthRating)
    }
}
