package com.roadtwin.ai.feature.onboarding

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Place
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtwin.ai.core.theme.*

@Composable
fun OnboardingScreen(onNavigateToDashboard: () -> Unit) {
    var currentStep by remember { mutableIntStateOf(0) }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWhite)
            .statusBarsPadding()
            .navigationBarsPadding()
            .padding(horizontal = 24.dp, vertical = 16.dp)
    ) {
        // Skip Button
        Text(
            text = "Skip",
            style = MaterialTheme.typography.bodyLarge,
            color = TextMediumGray,
            fontWeight = FontWeight.Medium,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .clickable { onNavigateToDashboard() }
                .padding(8.dp)
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 48.dp)
        ) {
            // Title
            Text(
                text = "Smart Roads\nBetter Tomorrow",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextDarkCharcoal,
                lineHeight = 36.sp
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Subtitle Description
            Text(
                text = "AI-Powered detection, real-time reporting and predictive maintenance for better road infrastructure.",
                style = MaterialTheme.typography.bodyLarge,
                color = TextMediumGray,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(48.dp))

            // Curving Road Circular Illustration with Location Pin
            Box(
                modifier = Modifier
                    .size(260.dp)
                    .align(Alignment.CenterHorizontally),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Soft background circle
                    drawCircle(
                        color = Color(0xFFF1F5F9),
                        radius = w * 0.48f,
                        center = Offset(w * 0.5f, h * 0.5f)
                    )

                    // Curving Asphalt Road
                    val roadPath = Path().apply {
                        moveTo(w * 0.15f, h * 0.85f)
                        cubicTo(
                            w * 0.25f, h * 0.45f,
                            w * 0.75f, h * 0.65f,
                            w * 0.85f, h * 0.25f
                        )
                    }

                    // Road surface
                    drawPath(
                        path = roadPath,
                        color = Color(0xFF334155),
                        style = Stroke(width = 44.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )

                    // Dashed Centerline
                    drawPath(
                        path = roadPath,
                        color = Color(0xFFFBBF24),
                        style = Stroke(
                            width = 3.dp.toPx(),
                            pathEffect = androidx.compose.ui.graphics.PathEffect.dashPathEffect(
                                floatArrayOf(12.dp.toPx(), 12.dp.toPx()),
                                0f
                            )
                        )
                    )
                }

                // Orange Location Marker Icon at destination
                Box(
                    modifier = Modifier
                        .offset(x = 64.dp, y = (-56).dp)
                        .size(40.dp)
                        .background(RoadTwinOrange, CircleShape),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.Place,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(24.dp)
                    )
                }
            }
        }

        // Bottom Row: 4-Dots Indicator and Next Button
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // 4 Dots
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                for (i in 0..3) {
                    if (i == currentStep) {
                        Box(
                            modifier = Modifier
                                .width(20.dp)
                                .height(8.dp)
                                .clip(RoundedCornerShape(4.dp))
                                .background(RoadTwinOrange)
                        )
                    } else {
                        Box(
                            modifier = Modifier
                                .size(8.dp)
                                .clip(CircleShape)
                                .background(SurfaceVariantLight)
                        )
                    }
                }
            }

            // Next / Get Started Text Button
            Text(
                text = if (currentStep == 3) "Get Started" else "Next",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.Bold,
                color = RoadTwinOrange,
                modifier = Modifier
                    .clickable {
                        if (currentStep < 3) {
                            currentStep++
                        } else {
                            onNavigateToDashboard()
                        }
                    }
                    .padding(8.dp)
            )
        }
    }
}
