package com.roadtwin.ai.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddRoad
import androidx.compose.material.icons.filled.ElectricBolt
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtwin.ai.core.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToDashboard: () -> Unit) {
    LaunchedEffect(Unit) {
        delay(1600)
        onNavigateToDashboard()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWhite)
    ) {
        // Perspective road drawing at background
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(360.dp)
                .align(Alignment.Center)
        ) {
            val w = size.width
            val h = size.height

            // Road surface
            val roadPath = Path().apply {
                moveTo(w * 0.42f, 0f)
                lineTo(w * 0.58f, 0f)
                lineTo(w * 0.95f, h)
                lineTo(w * 0.05f, h)
                close()
            }
            drawPath(
                path = roadPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE2E8F0).copy(alpha = 0.3f), Color(0xFFF1F5F9))
                )
            )

            // Dashed center lane
            val laneCount = 8
            for (i in 0 until laneCount) {
                val startY = (i.toFloat() / laneCount) * h
                val endY = startY + (h / (laneCount * 2f))
                val laneWidth = 2f + (i * 1.5f)
                drawLine(
                    color = RoadTwinBlue.copy(alpha = 0.4f),
                    start = Offset(w * 0.5f, startY),
                    end = Offset(w * 0.5f, endY),
                    strokeWidth = laneWidth
                )
            }
        }

        // Main Brand & Logo Column
        Column(
            modifier = Modifier
                .fillMaxSize()
                .statusBarsPadding()
                .padding(horizontal = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Spacer(modifier = Modifier.height(32.dp))

            // Center Branding Section
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center
            ) {
                // App Icon Badge
                Box(
                    modifier = Modifier
                        .size(92.dp)
                        .clip(RoundedCornerShape(24.dp))
                        .background(
                            Brush.linearGradient(
                                colors = listOf(RoadTwinBlue, RoadTwinBlueDark)
                            )
                        )
                        .border(2.dp, RoadTwinBlueLight, RoundedCornerShape(24.dp)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.AddRoad,
                        contentDescription = "RoadTwin AI Icon",
                        tint = Color.White,
                        modifier = Modifier.size(52.dp)
                    )
                }

                Spacer(modifier = Modifier.height(20.dp))

                // App Title
                Text(
                    text = "RoadTwin AI",
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = TextNavy,
                    letterSpacing = 0.5.sp
                )

                Spacer(modifier = Modifier.height(6.dp))

                // Tagline
                Text(
                    text = "Smarter Roads • Safer Journeys",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = RoadTwinBlueDark,
                    fontSize = 15.sp
                )

                Spacer(modifier = Modifier.height(36.dp))

                // Feature Highlights Pills
                Column(
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                    modifier = Modifier.fillMaxWidth(0.85f)
                ) {
                    SplashFeaturePill(icon = Icons.Default.ElectricBolt, text = "Detect Potholes")
                    SplashFeaturePill(icon = Icons.Default.Timer, text = "Save Time")
                    SplashFeaturePill(icon = Icons.Default.Security, text = "Safer Communities")
                }
            }

            // Bottom Loading Indicator & Caption
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 32.dp)
            ) {
                CircularProgressIndicator(
                    modifier = Modifier.size(28.dp),
                    color = RoadTwinBlue,
                    strokeWidth = 3.dp
                )
                Spacer(modifier = Modifier.height(14.dp))
                Text(
                    text = "Building Better Roads...",
                    style = MaterialTheme.typography.labelLarge,
                    color = TextMediumGray,
                    fontWeight = FontWeight.Medium
                )
            }
        }
    }
}

@Composable
private fun SplashFeaturePill(
    icon: ImageVector,
    text: String
) {
    Surface(
        color = BackgroundWhite.copy(alpha = 0.9f),
        border = androidx.compose.foundation.BorderStroke(1.dp, CardBorderColor),
        shape = RoundedCornerShape(20.dp),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(28.dp)
                    .clip(CircleShape)
                    .background(RoadTwinBlueLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = RoadTwinBlue,
                    modifier = Modifier.size(16.dp)
                )
            }
            Text(
                text = text,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )
        }
    }
}
