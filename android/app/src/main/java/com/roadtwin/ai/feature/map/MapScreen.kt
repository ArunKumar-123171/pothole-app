package com.roadtwin.ai.feature.map

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FilterList
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
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
import com.roadtwin.ai.feature.dashboard.DashboardBottomBar
import kotlinx.coroutines.launch
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

    val defaultCenter = remember { LatLng(11.0168, 76.9558) }
    val cameraPositionState = rememberCameraPositionState {
        position = CameraPosition.fromLatLngZoom(defaultCenter, 13f)
    }

    var selectedSessionId by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(detections) {
        val firstValid = detections.firstOrNull { it.latitude != 0.0 }
        if (firstValid != null) {
            cameraPositionState.position = CameraPosition.fromLatLngZoom(
                LatLng(firstValid.latitude, firstValid.longitude),
                14f
            )
            selectedSessionId = firstValid.sessionId
        }
    }

    val activeSession = sessions.find { it.sessionId == selectedSessionId } ?: sessions.firstOrNull()

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
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.FilterList, contentDescription = "Filter", tint = TextDarkCharcoal)
                    }

                    Text(
                        text = "Road Map",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkCharcoal
                    )

                    IconButton(onClick = { }, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.Outlined.Search, contentDescription = "Search", tint = TextDarkCharcoal)
                    }
                }
            }
        },
        bottomBar = {
            DashboardBottomBar(
                currentScreen = "map",
                onHomeClick = onNavigateToDashboard,
                onMapClick = {},
                onCameraClick = onNavigateToCamera,
                onReportsClick = onNavigateToReports,
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
                detections.forEach { d ->
                    if (d.latitude != 0.0) {
                        val hue = when (d.severity.uppercase()) {
                            "CRITICAL", "HIGH" -> BitmapDescriptorFactory.HUE_RED
                            "MEDIUM" -> BitmapDescriptorFactory.HUE_ORANGE
                            else -> BitmapDescriptorFactory.HUE_GREEN
                        }

                        Marker(
                            state = rememberMarkerState(position = LatLng(d.latitude, d.longitude)),
                            title = "Pothole ${d.detectionId}",
                            icon = BitmapDescriptorFactory.defaultMarker(hue),
                            onClick = {
                                selectedSessionId = d.sessionId
                                true
                            }
                        )
                    }
                }
            }

            // GPS Re-center Floating Button (Crosshairs icon)
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
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp),
                containerColor = BackgroundWhite,
                contentColor = TextDarkCharcoal,
                shape = CircleShape
            ) {
                Icon(Icons.Default.MyLocation, contentDescription = "My Location", modifier = Modifier.size(20.dp))
            }

            // Bottom Floating Card (Matching Screen 8)
            if (activeSession != null) {
                val dateSdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
                val timeStr = remember(activeSession.startTime) { dateSdf.format(Date(activeSession.startTime)) }

                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BackgroundWhite,
                    border = BorderStroke(1.dp, CardBorderColor),
                    shadowElevation = 4.dp,
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(horizontal = 16.dp, vertical = 16.dp)
                        .fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Text(
                            text = activeSession.startAddress.ifBlank { "NH 544, Coimbatore, Tamil Nadu, India" },
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkCharcoal,
                            maxLines = 1
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "High | $timeStr",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMediumGray
                        )

                        Spacer(Modifier.height(4.dp))

                        Text(
                            text = "Potholes: ${activeSession.totalPotholes}",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.SemiBold,
                            color = TextDarkCharcoal
                        )

                        Spacer(Modifier.height(12.dp))

                        // Solid Orange "View Details" Button
                        Button(
                            onClick = { onNavigateToDetail(activeSession.sessionId) },
                            modifier = Modifier.fillMaxWidth().height(46.dp),
                            shape = RoundedCornerShape(10.dp),
                            colors = ButtonDefaults.buttonColors(containerColor = RoadTwinOrange)
                        ) {
                            Text(
                                text = "View Details",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                        }
                    }
                }
            }
        }
    }
}
