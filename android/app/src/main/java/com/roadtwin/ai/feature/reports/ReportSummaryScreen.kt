package com.roadtwin.ai.feature.reports

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.roadtwin.ai.RoadTwinApplication
import com.roadtwin.ai.core.components.*
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import com.roadtwin.ai.data.sync.SyncWorker
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@Composable
fun ReportSummaryScreen(
    sessionId: String,
    onBack: () -> Unit,
    onNavigateToEdit: (sessionId: String) -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToReports: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val scope = rememberCoroutineScope()

    val sessionFlow = remember(sessionId) { app.reportsRepository.getSessionById(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    val detectionsFlow = remember(sessionId) { app.reportsRepository.getDetectionsForSession(sessionId) }
    val detections by detectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val routePointsFlow = remember(sessionId) { app.reportsRepository.getRoutePointsForSession(sessionId) }
    val routePoints by routePointsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var currentPotholeIndex by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }
    var isSyncingToFirebase by remember { mutableStateOf(false) }

    val currentSession = session

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
                            text = "Session Summary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                    }

                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = RoadTwinRed)
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
            val validLat = when {
                currentSession.startLatitude != 0.0 -> currentSession.startLatitude
                currentSession.endLatitude != 0.0 -> currentSession.endLatitude
                detections.any { it.latitude != 0.0 } -> detections.first { it.latitude != 0.0 }.latitude
                else -> 0.0
            }
            val validLon = when {
                currentSession.startLongitude != 0.0 -> currentSession.startLongitude
                currentSession.endLongitude != 0.0 -> currentSession.endLongitude
                detections.any { it.longitude != 0.0 } -> detections.first { it.longitude != 0.0 }.longitude
                else -> 0.0
            }
            val hasValidCoords = validLat != 0.0 && validLon != 0.0
            val startLatLng = if (hasValidCoords) LatLng(validLat, validLon) else null
            val cameraPositionState = rememberCameraPositionState {
                if (startLatLng != null) {
                    position = CameraPosition.fromLatLngZoom(startLatLng, 14f)
                }
            }

            // Duration calculation
            val durationSec = if (currentSession.endTime != null && currentSession.endTime!! > currentSession.startTime) {
                (currentSession.endTime!! - currentSession.startTime) / 1000
            } else {
                0L
            }
            val hrs = durationSec / 3600
            val mins = (durationSec % 3600) / 60
            val secs = durationSec % 60
            val durationFormatted = if (hrs > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
            } else {
                String.format(Locale.US, "00:%02d:%02d", mins, secs)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 20.dp, vertical = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Top Embedded Route Map Card
                RoadTwinCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(180.dp),
                    cornerRadius = 16.dp
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
                    ) {
                        if (currentSession.startLatitude != 0.0) {
                            Marker(
                                state = rememberMarkerState(position = LatLng(currentSession.startLatitude, currentSession.startLongitude)),
                                title = "Start Point",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            )
                        }

                        if (routePoints.size > 1) {
                            Polyline(
                                points = routePoints.map { LatLng(it.latitude, it.longitude) },
                                color = RoadTwinBlue,
                                width = 8f
                            )
                        }

                        if (currentSession.endLatitude != 0.0 && (currentSession.endLatitude != currentSession.startLatitude || currentSession.endLongitude != currentSession.startLongitude)) {
                            Marker(
                                state = rememberMarkerState(position = LatLng(currentSession.endLatitude, currentSession.endLongitude)),
                                title = "End Point",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                            )
                        }

                        detections.forEach { d ->
                            if (d.latitude != 0.0) {
                                Marker(
                                    state = rememberMarkerState(position = LatLng(d.latitude, d.longitude)),
                                    title = "Pothole ${d.detectionId}",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_ORANGE)
                                )
                            }
                        }
                    }
                }

                // 2. Metrics Card (Distance Covered, Duration, Total Potholes)
                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        SummaryMetricRow(
                            icon = Icons.Outlined.NearMe,
                            label = "Distance Covered",
                            value = String.format(Locale.US, "%.2f km", currentSession.distanceKm)
                        )
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        SummaryMetricRow(
                            icon = Icons.Outlined.Timer,
                            label = "Duration",
                            value = durationFormatted
                        )
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        SummaryMetricRow(
                            icon = Icons.Outlined.WarningAmber,
                            label = "Total Potholes",
                            value = "${currentSession.totalPotholes}"
                        )
                    }
                }

                // 3. Severity Breakdown Card (Critical, High, Medium, Low)
                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Text(
                            text = "Severity Summary",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )

                        Spacer(modifier = Modifier.height(2.dp))

                        SeverityCountRow("Critical", currentSession.criticalSeverityCount, RoadTwinRed)
                        SeverityCountRow("High", currentSession.highSeverityCount, Color(0xFFEA580C))
                        SeverityCountRow("Medium", currentSession.mediumSeverityCount, RoadTwinOrange)
                        SeverityCountRow("Low", currentSession.lowSeverityCount, RoadTwinGreen)
                    }
                }

                // 4. Pothole Evidence Carousel Preview
                if (detections.isNotEmpty()) {
                    val activeIndex = currentPotholeIndex.coerceIn(0, detections.lastIndex)
                    val activeDetection = detections[activeIndex]

                    RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            modifier = Modifier.padding(14.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Captured Evidence",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextNavy
                                )

                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    IconButton(
                                        onClick = { if (currentPotholeIndex > 0) currentPotholeIndex-- },
                                        enabled = currentPotholeIndex > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", tint = if (currentPotholeIndex > 0) TextNavy else TextDisabled)
                                    }

                                    Text(
                                        text = "${activeIndex + 1} of ${detections.size}",
                                        style = MaterialTheme.typography.bodySmall,
                                        fontWeight = FontWeight.Bold,
                                        color = TextNavy
                                    )

                                    IconButton(
                                        onClick = { if (currentPotholeIndex < detections.lastIndex) currentPotholeIndex++ },
                                        enabled = currentPotholeIndex < detections.lastIndex,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = if (currentPotholeIndex < detections.lastIndex) TextNavy else TextDisabled)
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.height(8.dp))

                            val imageFile = File(activeDetection.imagePath)
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(170.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(SurfaceLight),
                                contentAlignment = Alignment.Center
                            ) {
                                if (imageFile.exists()) {
                                    val bitmap = remember(activeDetection.imagePath) {
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
                                        Text("Photo saved locally", color = TextMediumGray)
                                    }
                                } else {
                                    Text("Photo saved locally", color = TextMediumGray)
                                }

                                // Top Right Severity Badge
                                SeverityBadge(
                                    severity = activeDetection.severity,
                                    modifier = Modifier
                                        .align(Alignment.TopEnd)
                                        .padding(8.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                // 5. Primary Action: SAVE & SYNC (Green Button)
                RoadTwinButton(
                    text = "SAVE & SYNC",
                    onClick = {
                        scope.launch {
                            isSyncingToFirebase = true
                            try {
                                app.reportsRepository.syncPendingReports()
                                Toast.makeText(context, "Session saved and synced successfully!", Toast.LENGTH_SHORT).show()
                                onNavigateToReports()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Report saved locally (sync queued)", Toast.LENGTH_SHORT).show()
                                onNavigateToReports()
                            } finally {
                                isSyncingToFirebase = false
                            }
                        }
                    },
                    icon = Icons.Default.CloudDone,
                    containerColor = RoadTwinGreen,
                    modifier = Modifier.fillMaxWidth(),
                    height = 52.dp,
                    cornerRadius = 14.dp
                )

                // 6. Firebase Upload Button: SEND TO FIREBASE (Blue Button)
                RoadTwinButton(
                    text = if (isSyncingToFirebase) "Sending..." else "Send to Firebase",
                    onClick = {
                        scope.launch {
                            isSyncingToFirebase = true
                            try {
                                val result = app.syncManager.syncPendingReports()
                                if (result.success) {
                                    Toast.makeText(context, "Uploaded metadata to Firebase Firestore!", Toast.LENGTH_SHORT).show()
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

                // 7. Secondary Action: View on Map & Edit
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RoadTwinOutlinedButton(
                        text = "View on Map",
                        onClick = onNavigateToMap,
                        icon = Icons.Outlined.Map,
                        modifier = Modifier.weight(1f),
                        height = 46.dp
                    )

                    RoadTwinOutlinedButton(
                        text = "Edit Report",
                        onClick = { onNavigateToEdit(sessionId) },
                        icon = Icons.Outlined.Edit,
                        borderColor = CardBorderColor,
                        contentColor = TextNavy,
                        modifier = Modifier.weight(1f),
                        height = 46.dp
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = BackgroundWhite,
            title = { Text("Discard Report", color = TextNavy, fontWeight = FontWeight.Bold) },
            text = { Text("Are you sure you want to discard this report session and delete all captured evidence?", color = TextMediumGray) },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    scope.launch {
                        app.reportsRepository.deleteSession(sessionId)
                        Toast.makeText(context, "Session discarded", Toast.LENGTH_SHORT).show()
                        onNavigateToReports()
                    }
                }) {
                    Text("Discard", color = RoadTwinRed, fontWeight = FontWeight.Bold)
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
private fun SummaryMetricRow(
    icon: ImageVector,
    label: String,
    value: String
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RoadTwinBlue,
                modifier = Modifier.size(20.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextMediumGray,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = value,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextNavy
        )
    }
}

@Composable
private fun SeverityCountRow(
    label: String,
    count: Int,
    color: Color
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(10.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextNavy,
                fontWeight = FontWeight.Medium
            )
        }

        Text(
            text = "$count",
            style = MaterialTheme.typography.bodyMedium,
            fontWeight = FontWeight.Bold,
            color = TextNavy
        )
    }
}
