package com.roadtwin.ai.feature.camera

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Timer
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadtwin.ai.RoadTwinApplication
import com.roadtwin.ai.core.camera.CameraPreview
import com.roadtwin.ai.core.permissions.PermissionGateway
import com.roadtwin.ai.core.theme.*
import java.util.*

private val BoundingBoxGreen = Color(0xFF22C55E)
private val TopOrangeBanner = Color(0xFFFF7A00)

@OptIn(ExperimentalTextApi::class)
@Composable
fun CameraScreen(
    onBack: () -> Unit,
    onNavigateToSummary: (sessionId: String) -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val viewModel: CameraViewModel = viewModel {
        CameraViewModel(app.reportsRepository)
    }

    LaunchedEffect(Unit) {
        viewModel.initialize(context)
    }

    LaunchedEffect(Unit) {
        viewModel.navigateToSummary.collect { sessionId ->
            onNavigateToSummary(sessionId)
        }
    }

    PermissionGateway {
        val detections by viewModel.detections.collectAsStateWithLifecycle()
        val loadState by viewModel.modelLoadState.collectAsStateWithLifecycle()
        val frameWidth by viewModel.frameWidth.collectAsStateWithLifecycle()
        val frameHeight by viewModel.frameHeight.collectAsStateWithLifecycle()
        val distanceKm by viewModel.sessionDistanceKm.collectAsStateWithLifecycle()
        val potholeCount by viewModel.potholeCount.collectAsStateWithLifecycle()
        val isMuted by viewModel.isMuted.collectAsStateWithLifecycle()

        val textMeasurer = rememberTextMeasurer()

        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black)
        ) {
            // Camera Live View
            CameraPreview(
                modifier = Modifier.fillMaxSize(),
                analyzer = viewModel
            )

            // Bounding Box Drawing Canvas
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

                        // Green bounding box
                        drawRoundRect(
                            color = BoundingBoxGreen,
                            topLeft = Offset(left, top),
                            size = androidx.compose.ui.geometry.Size(right - left, bottom - top),
                            cornerRadius = androidx.compose.ui.geometry.CornerRadius(8f, 8f),
                            style = Stroke(width = 3.dp.toPx())
                        )

                        // Green pill tag with white text "Pothole 91%"
                        val confPercent = (detection.confidence * 100).toInt()
                        val labelText = "Pothole $confPercent%"
                        drawText(
                            textMeasurer = textMeasurer,
                            text = labelText,
                            topLeft = Offset(left + 4.dp.toPx(), top - 22.dp.toPx()),
                            style = TextStyle(
                                color = Color.White,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                background = BoundingBoxGreen
                            )
                        )
                    }
                }
            }

            // ==========================================
            // TOP ORANGE BANNER (Screen 4 & 10)
            // ==========================================
            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .align(Alignment.TopCenter),
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
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.size(32.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = Color.White
                        )
                    }

                    Text(
                        text = "Scanning...",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color.White
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.FlashOn,
                            contentDescription = "Flash",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                        Icon(
                            imageVector = Icons.Outlined.Timer,
                            contentDescription = "Timer",
                            tint = Color.White,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }
            }

            // ==========================================
            // GREEN "LIVE" BADGE (Top-Left)
            // ==========================================
            Box(
                modifier = Modifier
                    .padding(top = 76.dp, start = 16.dp)
                    .clip(RoundedCornerShape(6.dp))
                    .background(LiveGreen)
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "LIVE",
                    color = Color.White,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.ExtraBold
                )
            }

            // ==========================================
            // RESOLUTION TAG (Bottom-Left)
            // ==========================================
            Text(
                text = if (frameWidth > 0 && frameHeight > 0) "${frameWidth} x ${frameHeight}" else "1280 x 720",
                color = Color.White,
                fontSize = 13.sp,
                fontWeight = FontWeight.Bold,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .navigationBarsPadding()
                    .padding(start = 16.dp, bottom = 20.dp)
            )

            // ==========================================
            // RIGHT CONTROL PANEL (Matching Screen 4 & 10)
            // ==========================================
            Surface(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .padding(end = 16.dp)
                    .width(110.dp)
                    .clip(RoundedCornerShape(20.dp)),
                color = Color.White.copy(alpha = 0.95f),
                shadowElevation = 4.dp
            ) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    // Distance Indicator
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            text = "Distance",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextMediumGray
                        )
                        Text(
                            text = String.format(Locale.US, "%.2f km", distanceKm),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkCharcoal
                        )
                    }

                    // Potholes Inner Card
                    Surface(
                        shape = RoundedCornerShape(10.dp),
                        color = Color.White,
                        border = androidx.compose.foundation.BorderStroke(1.dp, SurfaceVariantLight),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(vertical = 8.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(
                                text = "Potholes",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextMediumGray,
                                fontSize = 11.sp
                            )
                            Text(
                                text = "$potholeCount",
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = TextDarkCharcoal
                            )
                        }
                    }

                    // Sound / Mute Button
                    IconButton(
                        onClick = { viewModel.toggleMute() },
                        modifier = Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(SurfaceLight)
                    ) {
                        Icon(
                            imageVector = if (isMuted) Icons.AutoMirrored.Filled.VolumeOff else Icons.AutoMirrored.Filled.VolumeUp,
                            contentDescription = "Mute Toggle",
                            tint = if (isMuted) TextMediumGray else TextDarkCharcoal,
                            modifier = Modifier.size(24.dp)
                        )
                    }

                    // Stop Button (Red Pill Button)
                    Button(
                        onClick = { viewModel.stopMonitoring() },
                        colors = ButtonDefaults.buttonColors(containerColor = StopRed),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().height(44.dp),
                        contentPadding = PaddingValues(0.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Text(
                                text = "Stop",
                                color = Color.White,
                                fontWeight = FontWeight.Bold,
                                fontSize = 15.sp
                            )
                            Spacer(Modifier.width(6.dp))
                            Icon(
                                imageVector = Icons.Default.Stop,
                                contentDescription = null,
                                tint = Color.White,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }
            }
        }
    }
}
