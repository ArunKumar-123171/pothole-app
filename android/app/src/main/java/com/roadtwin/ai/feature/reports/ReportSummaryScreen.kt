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

private val TopOrangeBanner = Color(0xFFFF7A00)

@Composable
fun ReportSummaryScreen(
    sessionId: String,
    onBack: () -> Unit,
    onNavigateToEdit: (sessionId: String) -> Unit,
    onNavigateToReports: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val scope = rememberCoroutineScope()

    val sessionFlow = remember(sessionId) { app.reportsRepository.getSessionById(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    val detectionsFlow = remember(sessionId) { app.reportsRepository.getDetectionsForSession(sessionId) }
    val detections by detectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var currentPotholeIndex by remember { mutableIntStateOf(0) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val currentSession = session

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = TopOrangeBanner
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
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = Color.White)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Summary",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                    }

                    IconButton(onClick = { showDeleteConfirm = true }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Default.DeleteOutline, contentDescription = "Delete", tint = Color.White)
                    }
                }
            }
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        if (currentSession == null) {
            Box(
                modifier = Modifier.fillMaxSize().padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RoadTwinOrange)
            }
        } else {
            val startLatLng = LatLng(
                if (currentSession.startLatitude != 0.0) currentSession.startLatitude else 11.0168,
                if (currentSession.startLongitude != 0.0) currentSession.startLongitude else 76.9558
            )
            val cameraPositionState = rememberCameraPositionState {
                position = CameraPosition.fromLatLngZoom(startLatLng, 14f)
            }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .verticalScroll(rememberScrollState())
            ) {
                // Embedded Google Map View
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                ) {
                    GoogleMap(
                        modifier = Modifier.fillMaxSize(),
                        cameraPositionState = cameraPositionState,
                        uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
                    ) {
                        if (currentSession.startLatitude != 0.0) {
                            Marker(
                                state = rememberMarkerState(position = startLatLng),
                                title = "Start Point",
                                icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                            )
                        }

                        detections.forEach { d ->
                            if (d.latitude != 0.0) {
                                Marker(
                                    state = rememberMarkerState(position = LatLng(d.latitude, d.longitude)),
                                    title = "Pothole ${d.detectionId}",
                                    icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Route Address Details (Start & End with dot-line indicator)
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp)
                ) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        Column(
                            horizontalAlignment = Alignment.CenterHorizontally,
                            modifier = Modifier.padding(top = 4.dp, end = 12.dp)
                        ) {
                            Box(modifier = Modifier.size(10.dp).background(Color.Black, CircleShape))
                            Box(modifier = Modifier.width(2.dp).height(44.dp).background(Color.Black))
                            Icon(Icons.Default.ArrowDropDown, contentDescription = null, tint = Color.Black, modifier = Modifier.size(16.dp))
                        }

                        Column(
                            modifier = Modifier.weight(1f),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Column {
                                Text("Start", style = MaterialTheme.typography.labelSmall, color = TextMediumGray)
                                Text(
                                    text = currentSession.startAddress.ifBlank { "Location pending" },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextDarkCharcoal,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 18.sp
                                )
                            }

                            Column {
                                Text("End", style = MaterialTheme.typography.labelSmall, color = TextMediumGray)
                                Text(
                                    text = currentSession.endAddress.ifBlank { currentSession.startAddress },
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextDarkCharcoal,
                                    fontWeight = FontWeight.Medium,
                                    lineHeight = 18.sp
                                )
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    HorizontalDivider(color = SurfaceVariantLight, thickness = 1.dp)
                    Spacer(modifier = Modifier.height(12.dp))

                    // Distance & Total Potholes Metrics Row
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column {
                            Text("Distance", style = MaterialTheme.typography.labelSmall, color = TextMediumGray)
                            Text(
                                text = String.format(Locale.US, "%.2f km", currentSession.distanceKm),
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextDarkCharcoal
                            )
                        }

                        Column(horizontalAlignment = Alignment.End) {
                            Text("Total Potholes", style = MaterialTheme.typography.labelSmall, color = TextMediumGray)
                            Text(
                                text = "${currentSession.totalPotholes}",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextDarkCharcoal
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    // Pothole Carousel Card with "< 1 of N >"
                    if (detections.isNotEmpty()) {
                        val activeIndex = currentPotholeIndex.coerceIn(0, detections.lastIndex)
                        val activeDetection = detections[activeIndex]

                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = BackgroundWhite,
                            border = BorderStroke(1.dp, CardBorderColor),
                            shadowElevation = 1.dp,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(14.dp),
                                horizontalAlignment = Alignment.CenterHorizontally
                            ) {
                                // Pagination Row: < 1 of 2 >
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.Center,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    IconButton(
                                        onClick = { if (currentPotholeIndex > 0) currentPotholeIndex-- },
                                        enabled = currentPotholeIndex > 0,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronLeft, contentDescription = "Prev", tint = if (currentPotholeIndex > 0) TextDarkCharcoal else TextDisabled)
                                    }

                                    Spacer(Modifier.width(12.dp))

                                    Text(
                                        text = "${activeIndex + 1} of ${detections.size}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDarkCharcoal
                                    )

                                    Spacer(Modifier.width(12.dp))

                                    IconButton(
                                        onClick = { if (currentPotholeIndex < detections.lastIndex) currentPotholeIndex++ },
                                        enabled = currentPotholeIndex < detections.lastIndex,
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(Icons.Default.ChevronRight, contentDescription = "Next", tint = if (currentPotholeIndex < detections.lastIndex) TextDarkCharcoal else TextDisabled)
                                    }
                                }

                                Spacer(modifier = Modifier.height(8.dp))

                                // Captured Pothole Image with Green Badge
                                val imageFile = File(activeDetection.imagePath)
                                Box(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .height(180.dp)
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

                                    // Green Confidence Badge ("91%") at bottom right of image
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.BottomEnd)
                                            .padding(8.dp)
                                            .clip(RoundedCornerShape(6.dp))
                                            .background(LiveGreen)
                                            .padding(horizontal = 8.dp, vertical = 4.dp)
                                    ) {
                                        Text(
                                            text = String.format(Locale.US, "%d%%", (activeDetection.confidence * 100).toInt()),
                                            color = Color.White,
                                            fontWeight = FontWeight.Bold,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }

                    Spacer(modifier = Modifier.height(24.dp))

                    // 3 Action Buttons: [ Discard ] [ Edit ] [ Save Report ]
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // Discard Button
                        OutlinedButton(
                            onClick = { showDeleteConfirm = true },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, RoadTwinOrange),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BackgroundWhite)
                        ) {
                            Text("Discard", color = RoadTwinOrange, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Edit Button
                        OutlinedButton(
                            onClick = { onNavigateToEdit(sessionId) },
                            modifier = Modifier.weight(1f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            border = BorderStroke(1.5.dp, RoadTwinOrange),
                            colors = ButtonDefaults.outlinedButtonColors(containerColor = BackgroundWhite)
                        ) {
                            Text("Edit", color = TextDarkCharcoal, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }

                        // Save Report Button (Solid Orange)
                        Button(
                            onClick = {
                                Toast.makeText(context, "Report saved locally", Toast.LENGTH_SHORT).show()
                                onNavigateToReports()
                            },
                            modifier = Modifier.weight(1.4f).height(48.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoadTwinOrange)
                        ) {
                            Text("Save Report", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                        }
                    }

                    Spacer(modifier = Modifier.height(32.dp))
                }
            }
        }
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            containerColor = BackgroundWhite,
            title = { Text("Discard Report", color = TextDarkCharcoal, fontWeight = FontWeight.Bold) },
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
                    Text("Discard", color = SeverityHigh, fontWeight = FontWeight.Bold)
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
