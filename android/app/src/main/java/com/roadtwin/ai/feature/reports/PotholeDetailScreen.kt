package com.roadtwin.ai.feature.reports

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
import androidx.compose.material.icons.outlined.*
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
import com.roadtwin.ai.core.components.*
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun PotholeDetailScreen(
    sessionId: String,
    onBack: () -> Unit,
    onNavigateToEdit: (sessionId: String) -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToGeneratePdf: (sessionId: String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val scope = rememberCoroutineScope()

    val sessionFlow = remember(sessionId) { app.reportsRepository.getSessionById(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    val detectionsFlow = remember(sessionId) { app.reportsRepository.getDetectionsForSession(sessionId) }
    val detections by detectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isSyncingToFirebase by remember { mutableStateOf(false) }

    val currentSession = session
    val firstDetection = detections.firstOrNull()

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BackgroundWhite,
                border = BorderStroke(0.5.dp, CardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextNavy)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Report Details",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                    }

                    IconButton(onClick = { onNavigateToEdit(sessionId) }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Outlined.Edit, contentDescription = "Edit", tint = TextNavy)
                    }
                }
            }
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        if (currentSession == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RoadTwinBlue)
            }
        } else {
            val dateSdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
            val formattedDate = remember(currentSession.startTime) { dateSdf.format(Date(currentSession.startTime)) }

            val detection = firstDetection
            val imageFile = detection?.let { File(it.imagePath) }
            val primarySeverity = detection?.severity ?: (
                if (currentSession.criticalSeverityCount > 0) "CRITICAL"
                else if (currentSession.highSeverityCount > 0) "HIGH"
                else if (currentSession.mediumSeverityCount > 0) "MEDIUM"
                else "LOW"
            )

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // 1. Hero Pothole Image with Top-Right Severity Badge
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .clip(RoundedCornerShape(16.dp))
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

                    // Top-right severity chip
                    SeverityBadge(
                        severity = primarySeverity,
                        modifier = Modifier
                            .align(Alignment.TopEnd)
                            .padding(12.dp)
                    )
                }

                // 2. Metadata Table Card
                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        ReportDetailRow("Report ID", detection?.detectionId?.ifBlank { "D-${detection.id}" } ?: currentSession.sessionId)
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        ReportDetailRow("Location", currentSession.startAddress.ifBlank { if (currentSession.startLatitude != 0.0) String.format(Locale.US, "%.4f, %.4f", currentSession.startLatitude, currentSession.startLongitude) else "Location not available" })
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        ReportDetailRow("Coordinates", if (detection != null) String.format(Locale.US, "%.4f° N, %.4f° E", detection.latitude, detection.longitude) else String.format(Locale.US, "%.4f° N, %.4f° E", currentSession.startLatitude, currentSession.startLongitude))
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        ReportDetailRow("GPS Accuracy", if (detection != null) String.format(Locale.US, "± %.1f m", detection.gpsAccuracy) else "N/A")
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        ReportDetailRow("Date & Time", formattedDate)
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        ReportDetailRow("Confidence", if (detection != null) String.format(Locale.US, "%d%%", (detection.confidence * 100).toInt()) else "N/A")
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        ReportDetailRow("Severity", primarySeverity)
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        // Severity Source (Heuristic vs Manual Override)
                        val isManual = detection?.severitySource == "MANUAL_OVERRIDE"
                        ReportDetailRow("Severity Source", if (isManual) "Manual Override" else "Heuristic")
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        ReportDetailRow("Model", detection?.modelVersion?.ifBlank { "YOLO26n 416x416 FP32" } ?: "YOLO26n 416x416 FP32")
                    }
                }

                // 3. Action Row 1: [ View on Map ] and [ Generate PDF ]
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RoadTwinOutlinedButton(
                        text = "View on Map",
                        onClick = onNavigateToMap,
                        icon = Icons.Outlined.Map,
                        borderColor = CardBorderColor,
                        contentColor = TextNavy,
                        modifier = Modifier.weight(1f),
                        height = 48.dp
                    )

                    RoadTwinOutlinedButton(
                        text = "Generate PDF",
                        onClick = { onNavigateToGeneratePdf(sessionId) },
                        icon = Icons.Outlined.PictureAsPdf,
                        borderColor = CardBorderColor,
                        contentColor = TextNavy,
                        modifier = Modifier.weight(1f),
                        height = 48.dp
                    )
                }

                // 4. Primary Blue Button: SEND TO FIREBASE
                RoadTwinButton(
                    text = if (isSyncingToFirebase) "Sending..." else "Send to Firebase",
                    onClick = {
                        scope.launch {
                            isSyncingToFirebase = true
                            try {
                                val result = app.syncManager.syncPendingReports()
                                if (result.success) {
                                    Toast.makeText(context, "Metadata synced to Firebase Firestore!", Toast.LENGTH_SHORT).show()
                                } else {
                                    Toast.makeText(context, "Sync status: ${result.message}", Toast.LENGTH_SHORT).show()
                                }
                            } catch (e: Exception) {
                                Toast.makeText(context, "Sync enqueued: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSyncingToFirebase = false
                            }
                        }
                    },
                    icon = Icons.Default.CloudUpload,
                    containerColor = RoadTwinBlue,
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                    cornerRadius = 14.dp,
                    enabled = !isSyncingToFirebase
                )

                // 5. Destructive Button: DELETE REPORT
                RoadTwinButton(
                    text = "Delete Report",
                    onClick = { showDeleteConfirm = true },
                    icon = Icons.Default.DeleteOutline,
                    containerColor = RoadTwinRed,
                    modifier = Modifier.fillMaxWidth(),
                    height = 50.dp,
                    cornerRadius = 14.dp
                )

                Spacer(Modifier.height(24.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = BackgroundWhite,
            title = { Text("Delete Report", color = TextNavy, fontWeight = FontWeight.Bold) },
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
                    Text("Delete", color = RoadTwinRed, fontWeight = FontWeight.Bold)
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
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMediumGray,
            modifier = Modifier.weight(1f)
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextNavy,
            fontWeight = FontWeight.SemiBold,
            modifier = Modifier.weight(1.8f),
            textAlign = androidx.compose.ui.text.style.TextAlign.End
        )
    }
}
