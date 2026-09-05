package com.roadtwin.ai.feature.camera

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.ActivityInfo
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.AltRoute
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadtwin.ai.RoadTwinApplication
import com.roadtwin.ai.core.camera.CameraPreview
import com.roadtwin.ai.core.location.LocationManager
import com.roadtwin.ai.core.permissions.PermissionGateway
import com.roadtwin.ai.core.theme.*
import kotlinx.coroutines.delay
import java.util.*

private fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@OptIn(ExperimentalTextApi::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onNavigateToSummary: (sessionId: String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val locationManager = remember { LocationManager(context) }
    val viewModel: CameraViewModel = viewModel {
        CameraViewModel(app.reportsRepository)
    }

    // Auto-lock to Landscape mode when entering Live Detection, restore on exit
    DisposableEffect(Unit) {
        val activity = context.findActivity()
        val originalOrientation = activity?.requestedOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
        onDispose {
            activity?.requestedOrientation = originalOrientation
        }
    }

    // Lifecycle observer to refresh location services status on ON_RESUME
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                viewModel.refreshLocationStatus()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Intercept physical / gesture back button to safely finalize active session
    BackHandler {
        viewModel.stopMonitoring()
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToSummary.collect { sessionId ->
            if (sessionId.isNotBlank()) {
                onNavigateToSummary(sessionId)
            } else {
                onBack()
            }
        }
    }

    PermissionGateway {
        LaunchedEffect(Unit) {
            viewModel.initialize(context)
        }

        val detections by viewModel.detections.collectAsStateWithLifecycle()
        val loadState by viewModel.modelLoadState.collectAsStateWithLifecycle()
        val frameWidth by viewModel.frameWidth.collectAsStateWithLifecycle()
        val frameHeight by viewModel.frameHeight.collectAsStateWithLifecycle()
        val distanceKm by viewModel.sessionDistanceKm.collectAsStateWithLifecycle()
        val potholeCount by viewModel.potholeCount.collectAsStateWithLifecycle()
        val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()
        val gpsStatus by viewModel.gpsStatus.collectAsStateWithLifecycle()
        val isLocationEnabled by viewModel.isLocationEnabled.collectAsStateWithLifecycle()
        val liveSpeedKmh by viewModel.liveSpeedKmh.collectAsStateWithLifecycle()
        val liveFps by viewModel.liveFps.collectAsStateWithLifecycle()
        val isFlashOn by viewModel.isFlashOn.collectAsStateWithLifecycle()
        val zoomRatio by viewModel.zoomRatio.collectAsStateWithLifecycle()

        // Elapsed Session Timer
        var elapsedSeconds by remember { mutableLongStateOf(0L) }
        LaunchedEffect(Unit) {
            while (true) {
                delay(1000L)
                elapsedSeconds++
            }
        }
        val timerFormatted = remember(elapsedSeconds) {
            val hrs = elapsedSeconds / 3600
            val mins = (elapsedSeconds % 3600) / 60
            val secs = elapsedSeconds % 60
            if (hrs > 0) {
                String.format(Locale.US, "%02d:%02d:%02d", hrs, mins, secs)
            } else {
                String.format(Locale.US, "%02d:%02d", mins, secs)
            }
        }

        val textMeasurer = rememberTextMeasurer()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Camera Live View - takes 100% of viewport
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                analyzer = viewModel,
                isFlashOn = isFlashOn,
                zoomRatio = zoomRatio
            )

            // Real-Time Bounding Boxes Canvas
            Canvas(modifier = Modifier.fillMaxSize()) {
                val canvasWidth = size.width
                val canvasHeight = size.height

                if (frameWidth > 0 && frameHeight > 0) {
                    val scale = maxOf(canvasWidth / frameWidth, canvasHeight / frameHeight)
                    val scaledWidth = frameWidth * scale
                    val scaledHeight = frameHeight * scale
                    val offsetX = (scaledWidth - canvasWidth) / 2f
                    val offsetY = (scaledHeight - canvasHeight) / 2f

                    detections.forEach { detection ->
                        val box = detection.boundingBox
                        val left = box.left * scaledWidth - offsetX
                        val top = box.top * scaledHeight - offsetY
                        val right = box.right * scaledWidth - offsetX
                        val bottom = box.bottom * scaledHeight - offsetY

                        val conf = detection.confidence
                        val area = (box.right - box.left) * (box.bottom - box.top)
                        val boxColor = when {
                            conf > 0.85f || area > 0.15f -> Color(0xFFEF4444) // Red
                            conf > 0.75f || area > 0.08f -> Color(0xFFF97316) // Orange
                            conf > 0.60f -> Color(0xFF10B981) // Green
                            else -> Color(0xFFEF4444) // Red
                        }

                        // Bounding Box
                        drawRoundRect(
                            color = boxColor,
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(6f, 6f),
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Pill Tag with Score (e.g. "Pothole 0.89")
                        val labelText = String.format(Locale.US, "Pothole %.2f", conf)
                        drawText(
                            textMeasurer = textMeasurer,
                            text = labelText,
                            topLeft = Offset(left + 2.dp.toPx(), top - 24.dp.toPx()),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                background = boxColor
                            )
                        )
                    }
                }
            }

            // ==========================================
            // TOP FLOATING BAR (Back + Brand + Timer + Controls + FPS)
            // ==========================================
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .statusBarsPadding()
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .align(Alignment.TopCenter),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                // Left: Back Button & Brand Group
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Back Navigation Button (touch target >= 48dp)
                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { viewModel.stopMonitoring() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back / Stop Session",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }

                    // Brand Pill with Car Icon and Subtitle
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(36.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(Color(0xFF2563EB)),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.DirectionsCar,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = "RoadTwin AI",
                                color = Color.White,
                                fontWeight = FontWeight.ExtraBold,
                                fontSize = 15.sp
                            )
                            Text(
                                text = "Smarter Roads • Safer Journeys",
                                color = Color.White.copy(alpha = 0.75f),
                                fontWeight = FontWeight.Normal,
                                fontSize = 10.sp
                            )
                        }
                    }
                }

                // Center: Recording Timer Indicator
                Surface(
                    shape = RoundedCornerShape(20.dp),
                    color = Color.Black.copy(alpha = 0.55f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 7.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .clip(CircleShape)
                                .background(Color(0xFFEF4444))
                        )
                        Text(
                            text = "Recording... $timerFormatted",
                            color = Color.White,
                            fontWeight = FontWeight.Bold,
                            fontSize = 13.sp
                        )
                    }
                }

                // Right: Flash + Settings / Mute + FPS Pill
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Flash Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(if (isFlashOn) Color(0xFF2563EB) else Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { viewModel.toggleFlash() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.FlashOn,
                            contentDescription = "Flash",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Settings / Audio Mute Button
                    Box(
                        modifier = Modifier
                            .size(38.dp)
                            .clip(CircleShape)
                            .background(Color.Black.copy(alpha = 0.55f))
                            .border(1.dp, Color.White.copy(alpha = 0.25f), CircleShape)
                            .clickable { viewModel.toggleMute() },
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.Filled.Settings,
                            contentDescription = "Settings / Audio",
                            tint = Color.White,
                            modifier = Modifier.size(18.dp)
                        )
                    }

                    // Live FPS Indicator
                    Surface(
                        shape = RoundedCornerShape(20.dp),
                        color = Color.Black.copy(alpha = 0.55f),
                        border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f))
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(8.dp)
                                    .clip(CircleShape)
                                    .background(Color(0xFF22C55E))
                            )
                            Text(
                                text = "FPS: $liveFps",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 12.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // RIGHT FLOATING ZOOM CONTROLS (0.5x, 1.0x, 2.0x)
            // ==========================================
            Surface(
                shape = RoundedCornerShape(24.dp),
                color = Color.Black.copy(alpha = 0.55f),
                border = BorderStroke(1.dp, Color.White.copy(alpha = 0.2f)),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 4.dp, vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    val zooms = listOf(1.0f to "1.0x", 0.5f to "0.5x", 2.0f to "2.0x")
                    zooms.forEach { (ratio, label) ->
                        val isSelected = (zoomRatio == ratio)
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(if (isSelected) Color(0xFF2563EB) else Color.Transparent)
                                .clickable { viewModel.setZoomRatio(ratio) },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = label,
                                color = Color.White,
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                fontSize = 11.sp
                            )
                        }
                    }
                }
            }

            // ==========================================
            // COMPACT SEMI-TRANSPARENT BOTTOM HUD
            // [ GPS | POTHOLES | DISTANCE | SPEED ] + [ STOP ]
            // ==========================================
            Row(
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding()
                    .padding(start = 24.dp, end = 24.dp, bottom = 14.dp)
                    .fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                // 1. Semi-Transparent Floating Metric HUD
                Surface(
                    shape = RoundedCornerShape(24.dp),
                    color = Color(0xFF0F172A).copy(alpha = 0.60f),
                    border = BorderStroke(1.dp, Color.White.copy(alpha = 0.15f)),
                    modifier = Modifier.weight(1f)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 18.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        // A. GPS Indicator (3 States: Locked, Waiting, Location Off)
                        val isGpsLocked = gpsStatus.startsWith("GPS Locked")
                        val isLocOff = !isLocationEnabled || gpsStatus == "Location is turned off"

                        if (isLocOff) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable {
                                        val activity = context.findActivity()
                                        if (activity != null) {
                                            locationManager.requestLocationEnable(
                                                activity = activity,
                                                onFailed = { LocationManager.openLocationSettings(context) }
                                            )
                                        } else {
                                            LocationManager.openLocationSettings(context)
                                        }
                                    }
                                    .padding(horizontal = 4.dp, vertical = 2.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFEF4444)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOff,
                                        contentDescription = "Location Off",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Location Off",
                                        color = Color(0xFFEF4444),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Tap to Turn On ⚙️",
                                        color = Color(0xFF60A5FA),
                                        fontWeight = FontWeight.ExtraBold,
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else if (isGpsLocked) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFF10B981)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationOn,
                                        contentDescription = "GPS Locked",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "GPS Locked",
                                        color = Color(0xFF10B981),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    val accuracyStr = gpsStatus.substringAfter("GPS Locked", "±7.9 m").trim()
                                    Text(
                                        text = accuracyStr.ifEmpty { "±7.9 m" },
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        } else {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(34.dp)
                                        .clip(CircleShape)
                                        .background(Color(0xFFF59E0B)),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.LocationSearching,
                                        contentDescription = "Waiting for GPS",
                                        tint = Color.White,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column {
                                    Text(
                                        text = "Waiting for GPS",
                                        color = Color(0xFFF59E0B),
                                        fontWeight = FontWeight.Bold,
                                        fontSize = 12.sp
                                    )
                                    Text(
                                        text = "Searching...",
                                        color = Color.White.copy(alpha = 0.75f),
                                        fontSize = 10.sp
                                    )
                                }
                            }
                        }

                        // Divider 1
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )

                        // B. Metric: Potholes Detected
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF38BDF8).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.AltRoute,
                                    contentDescription = "Potholes",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.Start) {
                                Text(
                                    text = "$potholeCount",
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Potholes",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Divider 2
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )

                        // C. Metric: Distance
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF3B82F6).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Route,
                                    contentDescription = "Distance",
                                    tint = Color(0xFF3B82F6),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.Start) {
                                val distStr = if (distanceKm < 1.0) {
                                    String.format(Locale.US, "%.0f m", distanceKm * 1000)
                                } else {
                                    String.format(Locale.US, "%.2f km", distanceKm)
                                }
                                Text(
                                    text = distStr,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Distance",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }

                        // Divider 3
                        Box(
                            modifier = Modifier
                                .width(1.dp)
                                .height(32.dp)
                                .background(Color.White.copy(alpha = 0.15f))
                        )

                        // D. Metric: Speed
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(32.dp)
                                    .clip(RoundedCornerShape(8.dp))
                                    .background(Color(0xFF0284C7).copy(alpha = 0.15f)),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Speed,
                                    contentDescription = "Speed",
                                    tint = Color(0xFF38BDF8),
                                    modifier = Modifier.size(20.dp)
                                )
                            }
                            Column(horizontalAlignment = Alignment.Start) {
                                val speedStr = if (liveSpeedKmh > 0) "$liveSpeedKmh km/h" else "-- km/h"
                                Text(
                                    text = speedStr,
                                    color = Color.White,
                                    fontWeight = FontWeight.ExtraBold,
                                    fontSize = 16.sp
                                )
                                Text(
                                    text = "Speed",
                                    color = Color.White.copy(alpha = 0.7f),
                                    fontSize = 10.sp,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }

                // 2. Red Circular Stop Button (56dp target, red circle with white stop square + "Stop" label)
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Box(
                        modifier = Modifier
                            .size(56.dp)
                            .clip(CircleShape)
                            .background(Color(0xFFEF4444))
                            .border(2.dp, Color.White.copy(alpha = 0.4f), CircleShape)
                            .clickable {
                                viewModel.stopMonitoring()
                            },
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(Color.White)
                        )
                    }
                    Text(
                        text = "Stop",
                        color = Color.White,
                        fontWeight = FontWeight.Bold,
                        fontSize = 11.sp
                    )
                }
            }
        }
    }
}
