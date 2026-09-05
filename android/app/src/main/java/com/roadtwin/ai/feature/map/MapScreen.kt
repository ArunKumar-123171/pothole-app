package com.roadtwin.ai.feature.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.MyLocation
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
import com.google.android.gms.maps.CameraUpdateFactory
import com.google.android.gms.maps.model.BitmapDescriptorFactory
import com.google.android.gms.maps.model.CameraPosition
import com.google.android.gms.maps.model.LatLng
import com.google.maps.android.compose.*
import com.roadtwin.ai.RoadTwinApplication
import com.roadtwin.ai.core.components.*
import com.roadtwin.ai.core.location.LocationManager
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.data.local.DetectionEntity
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun MapScreen(
    onBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToDetail: (sessionId: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val scope = rememberCoroutineScope()
    val locationManager = remember { LocationManager(context) }

    val detectionsFlow = remember { app.reportsRepository.getAllDetections() }
    val detections by detectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val sessionsFlow = remember { app.reportsRepository.getAllSessions() }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var selectedSeverityFilter by remember { mutableStateOf("All") }
    var selectedDetectionId by remember { mutableStateOf<Int?>(null) }

    val cameraPositionState = rememberCameraPositionState()

    LaunchedEffect(Unit) {
        val loc = locationManager.getLastKnownLocation() ?: locationManager.getCurrentLocation()
        if (loc != null && loc.latitude != 0.0 && loc.longitude != 0.0) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(loc.latitude, loc.longitude),
                14f
            )
        }
    }

    LaunchedEffect(detections) {
        val firstValid = detections.firstOrNull { it.latitude != 0.0 }
        if (firstValid != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(firstValid.latitude, firstValid.longitude),
                14f
            )
            selectedDetectionId = firstValid.id
        }
    }

    val filteredDetections = remember(detections, selectedSeverityFilter) {
        if (selectedSeverityFilter.equals("All", ignoreCase = true)) {
            detections.filter { it.latitude != 0.0 }
        } else {
            detections.filter {
                it.latitude != 0.0 && it.severity.equals(selectedSeverityFilter, ignoreCase = true)
            }
        }
    }

    val activeDetection = remember(detections, selectedDetectionId) {
        detections.find { it.id == selectedDetectionId } ?: filteredDetections.firstOrNull()
    }

    val latestCompletedSession = remember(sessions) {
        sessions.firstOrNull { it.status == "COMPLETED" } ?: sessions.firstOrNull()
    }
    val sessionRoutePointsFlow = remember(latestCompletedSession?.sessionId) {
        val sid = latestCompletedSession?.sessionId
        if (sid != null) app.reportsRepository.getRoutePointsForSession(sid) else kotlinx.coroutines.flow.flowOf(emptyList())
    }
    val routePoints by sessionRoutePointsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    LaunchedEffect(routePoints) {
        if (routePoints.isNotEmpty()) {
            val first = routePoints.first()
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(first.latitude, first.longitude),
                15f
            )
        }
    }

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextNavy)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "Pothole Map",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                }
            }
        },
        bottomBar = {
            RoadTwinBottomBar(
                currentScreen = "map",
                onHomeClick = onNavigateToDashboard,
                onReportsClick = onNavigateToReports,
                onMapClick = {},
                onSettingsClick = onNavigateToSettings,
                onProfileClick = onNavigateToProfile
            )
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Google Map View
            GoogleMap(
                modifier = Modifier.fillMaxSize(),
                cameraPositionState = cameraPositionState,
                uiSettings = MapUiSettings(zoomControlsEnabled = false, myLocationButtonEnabled = false)
            ) {
                // 1. Start Marker (Green)
                if (latestCompletedSession != null && latestCompletedSession.startLatitude != 0.0 && latestCompletedSession.startLongitude != 0.0) {
                    Marker(
                        state = rememberMarkerState(position = LatLng(latestCompletedSession.startLatitude, latestCompletedSession.startLongitude)),
                        title = "Start: ${latestCompletedSession.title}",
                        snippet = "Origin: ${latestCompletedSession.startAddress}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_GREEN)
                    )
                }

                // 2. Real GPS Route (Continuous Polyline from Room route_points)
                if (routePoints.size > 1) {
                    Polyline(
                        points = routePoints.map { LatLng(it.latitude, it.longitude) },
                        color = RoadTwinBlue,
                        width = 10f
                    )
                }

                // 3. Pothole Markers (Severity-colored)
                filteredDetections.forEach { d ->
                    val hue = when (d.severity.uppercase()) {
                        "CRITICAL" -> BitmapDescriptorFactory.HUE_RED
                        "HIGH" -> BitmapDescriptorFactory.HUE_ORANGE
                        "MEDIUM" -> BitmapDescriptorFactory.HUE_YELLOW
                        else -> BitmapDescriptorFactory.HUE_GREEN
                    }

                    Marker(
                        state = rememberMarkerState(position = LatLng(d.latitude, d.longitude)),
                        title = "Pothole ${d.detectionId}",
                        snippet = "${d.severity} • ${(d.confidence * 100).toInt()}%",
                        icon = BitmapDescriptorFactory.defaultMarker(hue),
                        onClick = {
                            selectedDetectionId = d.id
                            false
                        }
                    )
                }

                // 4. End Marker (Red, distinct from start)
                if (latestCompletedSession != null && latestCompletedSession.endLatitude != 0.0 && latestCompletedSession.endLongitude != 0.0 &&
                    (latestCompletedSession.endLatitude != latestCompletedSession.startLatitude || latestCompletedSession.endLongitude != latestCompletedSession.startLongitude)) {
                    Marker(
                        state = rememberMarkerState(position = LatLng(latestCompletedSession.endLatitude, latestCompletedSession.endLongitude)),
                        title = "End: ${latestCompletedSession.title}",
                        snippet = "Destination: ${latestCompletedSession.endAddress}",
                        icon = BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_RED)
                    )
                }
            }

            // Top Severity Filter Chips Row
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
                shape = RoundedCornerShape(20.dp),
                color = BackgroundWhite.copy(alpha = 0.95f),
                border = BorderStroke(1.dp, CardBorderColor),
                shadowElevation = 2.dp
            ) {
                Row(
                    modifier = Modifier
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceAround,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    listOf(
                        "All" to RoadTwinBlue,
                        "Critical" to RoadTwinRed,
                        "High" to Color(0xFFEA580C),
                        "Medium" to RoadTwinOrange,
                        "Low" to RoadTwinGreen
                    ).forEach { (sev, color) ->
                        val isSelected = selectedSeverityFilter.equals(sev, ignoreCase = true)
                        Row(
                            modifier = Modifier
                                .clip(RoundedCornerShape(12.dp))
                                .background(if (isSelected) RoadTwinBlueLight else Color.Transparent)
                                .clickable { selectedSeverityFilter = sev }
                                .padding(horizontal = 8.dp, vertical = 4.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(color)
                            )
                            Text(
                                text = sev,
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = if (isSelected) RoadTwinBlue else TextMediumGray
                            )
                        }
                    }
                }
            }

            // GPS Re-center Floating Action Button
            FloatingActionButton(
                onClick = {
                    scope.launch {
                        val loc = locationManager.getCurrentLocation()
                        if (loc != null) {
                            cameraPositionState.animate(
                                CameraUpdateFactory.newLatLngZoom(
                                    LatLng(loc.latitude, loc.longitude),
                                    15f
                                )
                            )
                        }
                    }
                },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(end = 16.dp, bottom = if (activeDetection != null) 180.dp else 16.dp),
                containerColor = BackgroundWhite,
                contentColor = RoadTwinBlue,
                shape = CircleShape,
                elevation = FloatingActionButtonDefaults.elevation(defaultElevation = 3.dp)
            ) {
                Icon(Icons.Outlined.MyLocation, contentDescription = "My Location", modifier = Modifier.size(22.dp))
            }

            // Bottom Floating Card for Selected Pothole
            if (activeDetection != null) {
                val dateSdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
                val timeStr = remember(activeDetection.timestamp) { dateSdf.format(Date(activeDetection.timestamp)) }
                val imageFile = remember(activeDetection.imagePath) { File(activeDetection.imagePath) }

                RoadTwinCard(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 12.dp)
                        .fillMaxWidth(),
                    cornerRadius = 16.dp
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            // Thumbnail
                            Box(
                                modifier = Modifier
                                    .size(54.dp)
                                    .clip(RoundedCornerShape(10.dp))
                                    .background(SurfaceLight),
                                contentAlignment = Alignment.Center
                            ) {
                                if (imageFile.exists()) {
                                    val bitmap = remember(activeDetection.imagePath) {
                                        android.graphics.BitmapFactory.decodeFile(imageFile.absolutePath)
                                    }
                                    if (bitmap != null) {
                                        Image(
                                            bitmap = bitmap.asImageBitmap(),
                                            contentDescription = null,
                                            modifier = Modifier.fillMaxSize(),
                                            contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                        )
                                    } else {
                                        Icon(Icons.Default.Image, contentDescription = null, tint = TextDisabled)
                                    }
                                } else {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = TextDisabled)
                                }
                            }

                            Spacer(Modifier.width(12.dp))

                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = activeDetection.address.ifBlank { if (activeDetection.latitude != 0.0) String.format(Locale.US, "%.4f, %.4f", activeDetection.latitude, activeDetection.longitude) else "Pothole Location" },
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextNavy,
                                    maxLines = 1
                                )
                                Spacer(Modifier.height(2.dp))
                                Text(
                                    text = "$timeStr • Confidence ${(activeDetection.confidence * 100).toInt()}%",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextMediumGray
                                )
                            }

                            Spacer(Modifier.width(8.dp))

                            SeverityBadge(severity = activeDetection.severity)
                        }

                        Spacer(Modifier.height(10.dp))

                        // View Details Action Button
                        RoadTwinButton(
                            text = "View Details",
                            onClick = { onNavigateToDetail(activeDetection.sessionId) },
                            modifier = Modifier.fillMaxWidth(),
                            height = 44.dp,
                            cornerRadius = 10.dp
                        )
                    }
                }
            }
        }
    }
}
