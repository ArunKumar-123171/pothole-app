package com.roadtwin.ai.feature.onboarding

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtwin.ai.core.theme.*
import kotlinx.coroutines.delay

@Composable
fun SplashScreen(onNavigateToDashboard: () -> Unit) {
    var progress by remember { mutableFloatStateOf(0.1f) }

    LaunchedEffect(Unit) {
        val steps = 20
        for (i in 1..steps) {
            delay(80)
            progress = i.toFloat() / steps
        }
        delay(200)
        onNavigateToDashboard()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(BackgroundWhite)
    ) {
        // Soft scenic / mountain landscape watermark at top
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(300.dp)
                .align(Alignment.TopCenter)
        ) {
            val w = size.width
            val h = size.height

            // Mountain silhouette background gradient
            val mountainPath = Path().apply {
                moveTo(0f, h * 0.7f)
                lineTo(w * 0.25f, h * 0.35f)
                lineTo(w * 0.5f, h * 0.6f)
                lineTo(w * 0.75f, h * 0.25f)
                lineTo(w, h * 0.55f)
                lineTo(w, h)
                lineTo(0f, h)
                close()
            }
            drawPath(
                path = mountainPath,
                brush = Brush.verticalGradient(
                    colors = listOf(Color(0xFFE2E8F0).copy(alpha = 0.4f), Color.White)
                )
            )
        }

        // Center Content: Logo Shield, App Name, Subtitle
        Column(
            modifier = Modifier
                .align(Alignment.Center)
                .padding(horizontal = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            // Hexagonal Shield with Road 'V'
            Box(
                modifier = Modifier.size(100.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

                    // Hexagonal shield outline
                    val shieldPath = Path().apply {
                        moveTo(w * 0.5f, 0f)
                        lineTo(w * 0.95f, h * 0.25f)
                        lineTo(w * 0.95f, h * 0.75f)
                        lineTo(w * 0.5f, h)
                        lineTo(w * 0.05f, h * 0.75f)
                        lineTo(w * 0.05f, h * 0.25f)
                        close()
                    }

                    drawPath(
                        path = shieldPath,
                        color = RoadTwinOrange,
                        style = Stroke(width = 6.dp.toPx())
                    )

                    // Road V mark
                    val vPath = Path().apply {
                        moveTo(w * 0.28f, h * 0.35f)
                        lineTo(w * 0.5f, h * 0.68f)
                        lineTo(w * 0.72f, h * 0.35f)
                    }
                    drawPath(
                        path = vPath,
                        color = TextDarkCharcoal,
                        style = Stroke(width = 8.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )

                    // Top center dot
                    drawCircle(
                        color = TextDarkCharcoal,
                        radius = 5.dp.toPx(),
                        center = Offset(w * 0.5f, h * 0.28f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(24.dp))

            // App Name
            Text(
                text = "RoadTwin AI",
                style = MaterialTheme.typography.headlineLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextDarkCharcoal,
                letterSpacing = 0.5.sp
            )

            Spacer(modifier = Modifier.height(8.dp))

            // Subtitle
            Text(
                text = "Intelligent Road Monitoring\nfor Safer Journeys",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMediumGray,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp
            )
        }

        // Bottom Progress Bar
        Box(
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .padding(bottom = 48.dp, start = 64.dp, end = 64.dp)
                .fillMaxWidth()
        ) {
            LinearProgressIndicator(
                progress = { progress },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(6.dp)
                    .clip(RoundedCornerShape(3.dp)),
                color = RoadTwinOrange,
                trackColor = SurfaceVariantLight
            )
        }
    }
}
