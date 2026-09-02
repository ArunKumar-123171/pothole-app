package com.roadtwin.ai.core.pdf

import android.content.Context
import android.content.Intent
import android.graphics.*
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.util.Log
import androidx.core.content.FileProvider
import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream
import java.text.SimpleDateFormat
import java.util.*

private const val TAG = "PdfReportGenerator"

object PdfReportGenerator {
    // Standard A4 page dimensions in points (72 points per inch)
    private const val PAGE_WIDTH = 595
    private const val PAGE_HEIGHT = 842

    private val primaryColor = Color.rgb(0, 106, 106) // RoadTwin Teal-Green
    private val darkCharcoal = Color.rgb(26, 28, 30)
    private val mediumGray = Color.rgb(100, 105, 115)
    private val lightGray = Color.rgb(240, 242, 245)
    private val borderGray = Color.rgb(220, 225, 230)
    private val severityCritical = Color.rgb(183, 28, 28)
    private val severityHigh = Color.rgb(229, 57, 53)
    private val severityMedium = Color.rgb(255, 179, 0)
    private val severityLow = Color.rgb(76, 175, 80)

    /**
     * Generates a complete multi-page PDF report locally for a monitoring session.
     * Returns the absolute file path of the generated PDF file.
     */
    suspend fun generateSessionPdf(
        context: Context,
        session: MonitoringSessionEntity,
        detections: List<DetectionEntity>
    ): String = withContext(Dispatchers.IO) {
        val pdfDir = File(context.filesDir, "roadtwin/pdf")
        if (!pdfDir.exists()) {
            pdfDir.mkdirs()
        }

        val pdfFile = File(pdfDir, "RoadTwin_Report_${session.sessionId}.pdf")
        val document = PdfDocument()

        val dateFormat = SimpleDateFormat("dd MMMM yyyy, hh:mm a", Locale.getDefault())
        val dateText = dateFormat.format(Date(session.startTime))

        // Total pages calculation: 1 Cover + 1 Summary + 1 page per 2 detections (or 1 per page)
        val detectionsPerPage = 2
        val detectionPages = if (detections.isEmpty()) 0 else (detections.size + detectionsPerPage - 1) / detectionsPerPage
        val totalPages = 2 + detectionPages

        var pageNumber = 1

        // ==========================================
        // PAGE 1: COVER PAGE
        // ==========================================
        val pageInfo1 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val page1 = document.startPage(pageInfo1)
        drawCoverPage(page1.canvas, session, detections, dateText, pageNumber, totalPages)
        document.finishPage(page1)
        pageNumber++

        // ==========================================
        // PAGE 2: SESSION SUMMARY & ROUTE
        // ==========================================
        val pageInfo2 = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
        val page2 = document.startPage(pageInfo2)
        drawSummaryPage(page2.canvas, session, detections, dateText, pageNumber, totalPages)
        document.finishPage(page2)
        pageNumber++

        // ==========================================
        // SUBSEQUENT PAGES: DETECTION CARDS
        // ==========================================
        if (detections.isNotEmpty()) {
            val chunks = detections.chunked(detectionsPerPage)
            for (chunk in chunks) {
                val pageInfo = PdfDocument.PageInfo.Builder(PAGE_WIDTH, PAGE_HEIGHT, pageNumber).create()
                val page = document.startPage(pageInfo)
                drawDetectionsPage(page.canvas, chunk, pageNumber, totalPages)
                document.finishPage(page)
                pageNumber++
            }
        }

        // Write document to disk
        FileOutputStream(pdfFile).use { out ->
            document.writeTo(out)
        }
        document.close()

        Log.d(TAG, "Generated PDF report successfully at ${pdfFile.absolutePath}")
        pdfFile.absolutePath
    }

    private fun drawCoverPage(
        canvas: Canvas,
        session: MonitoringSessionEntity,
        detections: List<DetectionEntity>,
        dateText: String,
        pageNumber: Int,
        totalPages: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Top Banner Accent
        paint.color = primaryColor
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 18f, paint)

        // Header Title
        paint.color = primaryColor
        paint.textSize = 28f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ROAD TWIN AI", 40f, 80f, paint)

        paint.color = mediumGray
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Intelligent Road Monitoring & Infrastructure Inspection System", 40f, 102f, paint)

        // Thin Separator Line
        paint.color = borderGray
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 120f, (PAGE_WIDTH - 40).toFloat(), 120f, paint)

        // Main Report Title Card
        val cardRect = RectF(40f, 140f, (PAGE_WIDTH - 40).toFloat(), 280f)
        paint.color = lightGray
        paint.style = Paint.Style.FILL
        canvas.drawRoundRect(cardRect, 12f, 12f, paint)

        paint.color = primaryColor
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(session.title.ifBlank { "Road Monitoring Inspection Report" }, 60f, 180f, paint)

        paint.color = darkCharcoal
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("Monitoring Session ID: ${session.sessionId}", 60f, 210f, paint)
        canvas.drawText("Inspection Date: $dateText", 60f, 230f, paint)
        canvas.drawText("Status: COMPLETED (Verified via YOLO26n Edge AI)", 60f, 250f, paint)

        // Key Statistics Grid (4 Cards)
        val cardW = (PAGE_WIDTH - 80 - 15) / 2f
        val cardH = 75f

        drawStatCard(canvas, 40f, 305f, cardW, cardH, "TOTAL POTHOLES", "${session.totalPotholes}", primaryColor)
        drawStatCard(canvas, 40f + cardW + 15f, 305f, cardW, cardH, "DISTANCE TRAVELED", String.format(Locale.US, "%.2f km", session.distanceKm), primaryColor)

        val criticalHigh = session.criticalSeverityCount + session.highSeverityCount
        drawStatCard(canvas, 40f, 395f, cardW, cardH, "HIGH / CRITICAL POTHOLES", "$criticalHigh", if (criticalHigh > 0) severityHigh else severityLow)
        drawStatCard(canvas, 40f + cardW + 15f, 395f, cardW, cardH, "MODERATE / LOW POTHOLES", "${session.mediumSeverityCount + session.lowSeverityCount}", severityMedium)

        // Session Metadata Overview Box
        paint.color = lightGray
        val metaRect = RectF(40f, 490f, (PAGE_WIDTH - 40).toFloat(), 720f)
        canvas.drawRoundRect(metaRect, 12f, 12f, paint)

        paint.color = darkCharcoal
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("INSPECTION SPECIFICATIONS", 60f, 520f, paint)

        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        paint.color = darkCharcoal

        val specLines = listOf(
            "Detection Engine:" to "YOLO26n (FP32, 416x416 Input, LiteRT / TFLite)",
            "Stability Protocol:" to "Multi-frame IoU Tracking (3 consecutive compatible frames)",
            "Duplicate Suppression:" to "20 meters spatial distance & 10s cooldown threshold",
            "Localization:" to "High-Accuracy GPS / Fused Location Provider",
            "Severity Assessment:" to "Bounding Box Area & Confidence Heuristic",
            "Data Sovereignty:" to "Offline-First Room Storage (Zero Cloud Image Dependency)",
            "Start Location:" to session.startAddress,
            "End Location:" to session.endAddress
        )

        var specY = 550f
        for ((label, value) in specLines) {
            paint.color = mediumGray
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(label, 60f, specY, paint)

            paint.color = darkCharcoal
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            val truncatedVal = if (value.length > 45) value.take(42) + "..." else value
            canvas.drawText(truncatedVal, 210f, specY, paint)
            specY += 20f
        }

        drawFooter(canvas, pageNumber, totalPages)
    }

    private fun drawSummaryPage(
        canvas: Canvas,
        session: MonitoringSessionEntity,
        detections: List<DetectionEntity>,
        dateText: String,
        pageNumber: Int,
        totalPages: Int
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        drawHeader(canvas, "Session Summary & Route Inspection")

        var y = 100f

        // Location & Route Card
        paint.color = lightGray
        val locCard = RectF(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 170f)
        canvas.drawRoundRect(locCard, 10f, 10f, paint)

        paint.color = primaryColor
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ROUTE & GEOGRAPHIC FOOTPRINT", 60f, y + 28f, paint)

        paint.color = darkCharcoal
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        canvas.drawText("Origin Location:", 60f, y + 55f, paint)
        paint.color = mediumGray
        canvas.drawText(session.startAddress, 160f, y + 55f, paint)
        canvas.drawText(String.format(Locale.US, "Coordinates: %.6f, %.6f", session.startLatitude, session.startLongitude), 160f, y + 70f, paint)

        paint.color = darkCharcoal
        canvas.drawText("Destination Location:", 60f, y + 100f, paint)
        paint.color = mediumGray
        canvas.drawText(session.endAddress, 160f, y + 100f, paint)
        canvas.drawText(String.format(Locale.US, "Coordinates: %.6f, %.6f", session.endLatitude, session.endLongitude), 160f, y + 115f, paint)

        paint.color = primaryColor
        canvas.drawText("Total Distance Traveled: ${String.format(Locale.US, "%.2f km", session.distanceKm)}", 60f, y + 148f, paint)

        y += 190f

        // Inspector Notes / Remarks Card
        paint.color = lightGray
        val notesCard = RectF(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 120f)
        canvas.drawRoundRect(notesCard, 10f, 10f, paint)

        paint.color = primaryColor
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("INSPECTION REMARKS & OBSERVATIONS", 60f, y + 28f, paint)

        paint.color = darkCharcoal
        paint.textSize = 11f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)

        val notesText = session.notes.ifBlank { "No special inspector notes recorded for this monitoring session." }
        val remarksText = session.remarks.ifBlank { "Automated detection completed successfully." }

        canvas.drawText("Notes: $notesText", 60f, y + 55f, paint)
        canvas.drawText("Remarks: $remarksText", 60f, y + 80f, paint)
        canvas.drawText("Inspector: RoadTwin AI Automated Operator", 60f, y + 102f, paint)

        y += 140f

        // Severity Distribution Breakdown
        paint.color = lightGray
        val distCard = RectF(40f, y, (PAGE_WIDTH - 40).toFloat(), y + 160f)
        canvas.drawRoundRect(distCard, 10f, 10f, paint)

        paint.color = primaryColor
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("POTHOLE SEVERITY BREAKDOWN", 60f, y + 28f, paint)

        val sevY = y + 60f
        drawSeverityIndicator(canvas, 60f, sevY, "CRITICAL", "${session.criticalSeverityCount}", severityCritical)
        drawSeverityIndicator(canvas, 180f, sevY, "HIGH", "${session.highSeverityCount}", severityHigh)
        drawSeverityIndicator(canvas, 300f, sevY, "MEDIUM", "${session.mediumSeverityCount}", severityMedium)
        drawSeverityIndicator(canvas, 420f, sevY, "LOW", "${session.lowSeverityCount}", severityLow)

        paint.color = mediumGray
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.ITALIC)
        canvas.drawText("* Note: Pothole severity is estimated using relative bounding-box area and temporal stability heuristics.", 60f, y + 140f, paint)

        drawFooter(canvas, pageNumber, totalPages)
    }

    private fun drawDetectionsPage(
        canvas: Canvas,
        chunk: List<DetectionEntity>,
        pageNumber: Int,
        totalPages: Int
    ) {
        drawHeader(canvas, "Pothole Detection Evidence Logs")

        var startY = 85f
        val cardHeight = 330f

        for (detection in chunk) {
            drawPotholeCard(canvas, 40f, startY, (PAGE_WIDTH - 80).toFloat(), cardHeight, detection)
            startY += cardHeight + 15f
        }

        drawFooter(canvas, pageNumber, totalPages)
    }

    private fun drawPotholeCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        width: Float,
        height: Float,
        detection: DetectionEntity
    ) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)

        // Card Background
        paint.color = lightGray
        val cardRect = RectF(x, y, x + width, y + height)
        canvas.drawRoundRect(cardRect, 10f, 10f, paint)

        // Card Border
        paint.style = Paint.Style.STROKE
        paint.color = borderGray
        paint.strokeWidth = 1f
        canvas.drawRoundRect(cardRect, 10f, 10f, paint)
        paint.style = Paint.Style.FILL

        // Header Title
        paint.color = darkCharcoal
        paint.textSize = 13f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        val pId = detection.detectionId.ifBlank { "P-${detection.id}" }
        canvas.drawText("POTHOLE DETECTION #$pId", x + 16f, y + 26f, paint)

        // Severity Badge
        val sevColor = when (detection.severity.uppercase()) {
            "CRITICAL" -> severityCritical
            "HIGH" -> severityHigh
            "MEDIUM" -> severityMedium
            else -> severityLow
        }
        val sevText = "${detection.severity} (Heuristic)"
        val badgeW = 120f
        paint.color = sevColor
        canvas.drawRoundRect(RectF(x + width - badgeW - 16f, y + 12f, x + width - 16f, y + 32f), 4f, 4f, paint)
        paint.color = Color.WHITE
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(sevText, x + width - badgeW - 8f, y + 26f, paint)

        // Left Side: Image Drawing
        val imgX = x + 16f
        val imgY = y + 42f
        val imgW = 200f
        val imgH = 200f

        val file = File(detection.imagePath)
        if (file.exists()) {
            try {
                val bitmap = BitmapFactory.decodeFile(file.absolutePath)
                if (bitmap != null) {
                    val srcRect = Rect(0, 0, bitmap.width, bitmap.height)
                    val dstRect = RectF(imgX, imgY, imgX + imgW, imgY + imgH)
                    canvas.drawBitmap(bitmap, srcRect, dstRect, paint)
                    bitmap.recycle()
                } else {
                    drawMissingImagePlaceholder(canvas, imgX, imgY, imgW, imgH)
                }
            } catch (e: Exception) {
                drawMissingImagePlaceholder(canvas, imgX, imgY, imgW, imgH)
            }
        } else {
            drawMissingImagePlaceholder(canvas, imgX, imgY, imgW, imgH)
        }

        // Right Side: Detailed Metadata
        val infoX = imgX + imgW + 16f
        var infoY = y + 60f
        paint.textSize = 10f

        val details = listOf(
            "Confidence:" to String.format(Locale.US, "%.1f%%", detection.confidence * 100),
            "Severity Source:" to detection.severitySource,
            "Latitude:" to String.format(Locale.US, "%.6f", detection.latitude),
            "Longitude:" to String.format(Locale.US, "%.6f", detection.longitude),
            "GPS Accuracy:" to String.format(Locale.US, "±%.1f m", detection.gpsAccuracy),
            "Captured At:" to SimpleDateFormat("hh:mm:ss a", Locale.getDefault()).format(Date(detection.timestamp)),
            "Model:" to detection.modelVersion,
            "Input Tensor:" to detection.modelInputSize,
            "Precision:" to detection.modelPrecision
        )

        for ((lbl, valStr) in details) {
            paint.color = mediumGray
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            canvas.drawText(lbl, infoX, infoY, paint)

            paint.color = darkCharcoal
            paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
            canvas.drawText(valStr, infoX + 90f, infoY, paint)
            infoY += 20f
        }

        // Bottom Street Address Box
        val addrY = y + height - 36f
        paint.color = borderGray
        canvas.drawLine(x + 16f, addrY - 10f, x + width - 16f, addrY - 10f, paint)

        paint.color = mediumGray
        paint.textSize = 9.5f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("Geotagged Location: ", x + 16f, addrY + 8f, paint)

        paint.color = darkCharcoal
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        val truncatedAddr = if (detection.address.length > 70) detection.address.take(67) + "..." else detection.address
        canvas.drawText(truncatedAddr, x + 125f, addrY + 8f, paint)
    }

    private fun drawMissingImagePlaceholder(canvas: Canvas, x: Float, y: Float, w: Float, h: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = Color.rgb(230, 233, 238)
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 8f, 8f, paint)

        paint.color = mediumGray
        paint.textSize = 10f
        paint.textAlign = Paint.Align.CENTER
        canvas.drawText("Image Captured Locally", x + w / 2f, y + h / 2f, paint)
        paint.textAlign = Paint.Align.LEFT
    }

    private fun drawStatCard(canvas: Canvas, x: Float, y: Float, w: Float, h: Float, label: String, value: String, valueColor: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = lightGray
        canvas.drawRoundRect(RectF(x, y, x + w, y + h), 8f, 8f, paint)

        paint.color = mediumGray
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(label, x + 12f, y + 24f, paint)

        paint.color = valueColor
        paint.textSize = 20f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(value, x + 12f, y + 54f, paint)
    }

    private fun drawSeverityIndicator(canvas: Canvas, x: Float, y: Float, label: String, count: String, color: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = color
        canvas.drawCircle(x + 12f, y + 16f, 8f, paint)

        paint.color = darkCharcoal
        paint.textSize = 12f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText(count, x + 28f, y + 20f, paint)

        paint.color = mediumGray
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(label, x, y + 42f, paint)
    }

    private fun drawHeader(canvas: Canvas, title: String) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = primaryColor
        canvas.drawRect(0f, 0f, PAGE_WIDTH.toFloat(), 12f, paint)

        paint.color = primaryColor
        paint.textSize = 16f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
        canvas.drawText("ROAD TWIN AI", 40f, 45f, paint)

        paint.color = darkCharcoal
        paint.textSize = 14f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("— $title", 160f, 45f, paint)

        paint.color = borderGray
        paint.strokeWidth = 1f
        canvas.drawLine(40f, 60f, (PAGE_WIDTH - 40).toFloat(), 60f, paint)
    }

    private fun drawFooter(canvas: Canvas, pageNumber: Int, totalPages: Int) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = borderGray
        paint.strokeWidth = 1f
        canvas.drawLine(40f, (PAGE_HEIGHT - 45).toFloat(), (PAGE_WIDTH - 40).toFloat(), (PAGE_HEIGHT - 45).toFloat(), paint)

        paint.color = mediumGray
        paint.textSize = 9f
        paint.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText("RoadTwin AI • Offline-First Municipal Road Inspection Protocol", 40f, (PAGE_HEIGHT - 25).toFloat(), paint)

        val pageText = "Page $pageNumber of $totalPages"
        paint.textAlign = Paint.Align.RIGHT
        canvas.drawText(pageText, (PAGE_WIDTH - 40).toFloat(), (PAGE_HEIGHT - 25).toFloat(), paint)
        paint.textAlign = Paint.Align.LEFT
    }

    /**
     * Creates an Intent to share the generated PDF via external apps (email, messaging, files).
     */
    fun getSharePdfIntent(context: Context, pdfPath: String): Intent {
        val file = File(pdfPath)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_SEND).apply {
            type = "application/pdf"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_SUBJECT, "RoadTwin AI Pothole Detection Report")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
    }

    /**
     * Creates an Intent to view/open the PDF in a PDF viewer app.
     */
    fun getViewPdfIntent(context: Context, pdfPath: String): Intent {
        val file = File(pdfPath)
        val uri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.fileprovider",
            file
        )
        return Intent(Intent.ACTION_VIEW).apply {
            setDataAndType(uri, "application/pdf")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
        }
    }
}
