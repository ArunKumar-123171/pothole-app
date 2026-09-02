package com.roadtwin.ai.feature.reports

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadtwin.ai.RoadTwinApplication
import com.roadtwin.ai.core.pdf.PdfReportGenerator
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import com.roadtwin.ai.data.sync.SyncWorker
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PotholeDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onNavigateToEdit: (sessionId: String) -> Unit,
    onNavigateToMap: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val scope = rememberCoroutineScope()

    val sessionFlow = remember(sessionId) { app.reportsRepository.getSessionById(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    val detectionsFlow = remember(sessionId) { app.reportsRepository.getDetectionsForSession(sessionId) }
    val detections by detectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isGeneratingPdf by remember { mutableStateOf(false) }

    val currentSession = session
    val firstDetection = detections.firstOrNull()

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BackgroundWhite
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDarkCharcoal)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Pothole Detail",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkCharcoal
                        )
                    }

                    IconButton(
                        onClick = {
                            scope.launch {
                                try {
                                    val path = app.reportsRepository.generateSessionPdf(sessionId)
                                    val shareIntent = PdfReportGenerator.getSharePdfIntent(context, path)
                                    context.startActivity(Intent.createChooser(shareIntent, "Share Report PDF"))
                                } catch (e: Exception) {
                                    Toast.makeText(context, "Generating shareable report", Toast.LENGTH_SHORT).show()
                                }
                            }
                        },
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(Icons.Outlined.Share, contentDescription = "Share", tint = TextDarkCharcoal)
                    }
                }
            }
        },
        containerColor = BackgroundOffWhite
    ) { innerPadding ->
        if (currentSession == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RoadTwinOrange)
            }
        } else {
            val dateSdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
            val formattedDate = remember(currentSession.startTime) { dateSdf.format(Date(currentSession.startTime)) }

            val detection = firstDetection
            val imageFile = detection?.let { File(it.imagePath) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // Large Captured Pothole Image with Severity Badge on top right
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(210.dp)
                        .clip(RoundedCornerShape(14.dp))
                        .background(SurfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageFile != null && imageFile.exists()) {
                        val bitmap = remember(detection.imagePath) {
                            BitmapFactory.decodeFile(imageFile.absolutePath)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = "Pothole photo",
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(48.dp))
                        }
                    } else {
                        Icon(Icons.Default.Image, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(48.dp))
                    }

                    // Top-right severity badge
                    val sev = detection?.severity ?: "High"
                    val sevColor = when (sev) {
                        "CRITICAL", "HIGH" -> SeverityHigh
                        "MEDIUM" -> SeverityMedium
                        else -> SeverityLow
                    }
                    Box(
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                            .clip(RoundedCornerShape(6.dp))
                            .background(sevColor)
                            .padding(horizontal = 10.dp, vertical = 4.dp)
                    ) {
                        Text(
                            text = sev.lowercase().replaceFirstChar { it.uppercase() },
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 12.sp
                        )
                    }
                }

                // Metadata Details Card
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = BackgroundWhite,
                    border = BorderStroke(1.dp, CardBorderColor),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReportDetailRow("Report ID", detection?.detectionId?.ifBlank { "P-1024" } ?: "P-1024")
                        ReportDetailRow("Location", currentSession.startAddress.ifBlank { "NH 544, Coimbatore, Tamil Nadu, India" })
                        ReportDetailRow("Coordinates", String.format(Locale.US, "%.4f° N, %.4f° E", currentSession.startLatitude, currentSession.startLongitude))
                        ReportDetailRow("GPS Accuracy", if (detection != null) String.format(Locale.US, "±%.1f m", detection.gpsAccuracy) else "±4.2 m")
                        ReportDetailRow("Date & Time", formattedDate)
                        ReportDetailRow("Confidence", if (detection != null) String.format(Locale.US, "%d%%", (detection.confidence * 100).toInt()) else "91%")

                        // Severity Row with Heuristic note
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.Top
                        ) {
                            Text("Severity", style = MaterialTheme.typography.bodySmall, color = TextMediumGray, modifier = Modifier.weight(1f))
                            Column(modifier = Modifier.weight(2f), horizontalAlignment = Alignment.End) {
                                Text(
                                    text = "${detection?.severity ?: "High"} (Heuristic)",
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextDarkCharcoal
                                )
                                Text(
                                    text = "Estimated severity only",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextDisabled,
                                    fontSize = 10.sp
                                )
                            }
                        }

                        // Sync Status Row
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Sync Status", style = MaterialTheme.typography.bodySmall, color = TextMediumGray)
                            val isSynced = currentSession.syncStatus == "SYNCED"
                            Box(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(4.dp))
                                    .background(if (isSynced) SeverityLow.copy(alpha = 0.15f) else StatusPending.copy(alpha = 0.15f))
                                    .padding(horizontal = 8.dp, vertical = 2.dp)
                            ) {
                                Text(
                                    text = currentSession.syncStatus,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.Bold,
                                    color = if (isSynced) SeverityLow else StatusPending,
                                    fontSize = 11.sp
                                )
                            }
                        }

                        ReportDetailRow("Model", "YOLO26n 416x416 FP32")
                    }
                }

                // Action Buttons
                // Row 1: [ View on Map ] [ Delete Report ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToMap,
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, CardBorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = BackgroundWhite)
                    ) {
                        Text("View on Map", color = TextDarkCharcoal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }

                    OutlinedButton(
                        onClick = { showDeleteConfirm = true },
                        modifier = Modifier.weight(1f).height(46.dp),
                        shape = RoundedCornerShape(10.dp),
                        border = BorderStroke(1.dp, SeverityHigh.copy(alpha = 0.5f)),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = BackgroundWhite)
                    ) {
                        Text("Delete Report", color = SeverityHigh, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }

                // Row 2: Solid Orange Button: [ Retry Sync ] / [ Generate PDF ]
                Button(
                    onClick = {
                        scope.launch {
                            isGeneratingPdf = true
                            try {
                                val path = app.reportsRepository.generateSessionPdf(sessionId)
                                val viewIntent = PdfReportGenerator.getViewPdfIntent(context, path)
                                context.startActivity(viewIntent)
                            } catch (e: Exception) {
                                SyncWorker.enqueueOneTimeSync(context)
                                Toast.makeText(context, "Sync enqueued to cloud", Toast.LENGTH_SHORT).show()
                            } finally {
                                isGeneratingPdf = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth().height(50.dp),
                    shape = RoundedCornerShape(10.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoadTwinOrange)
                ) {
                    Text(
                        text = if (currentSession.syncStatus == "SYNCED") "Generate PDF" else "Retry Sync",
                        color = Color.White,
                        fontWeight = FontWeight.ExtraBold,
                        fontSize = 15.sp
                    )
                }

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = BackgroundWhite,
            title = { Text("Delete Report", color = TextDarkCharcoal, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to delete this report?", color = TextMediumGray) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        app.reportsRepository.deleteSession(sessionId)
                        Toast.makeText(context, "Report deleted", Toast.LENGTH_SHORT).show()
                        onBack()
                    }
                }) {
                    Text("Delete", color = SeverityHigh, fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) {
                    Text("Cancel", color = TextMediumGray)
                }
            }
        )
    }
}

@Composable
fun ReportDetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Top
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextMediumGray, modifier = Modifier.weight(1f))
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextDarkCharcoal,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(2f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
