package com.roadtwin.ai.feature.profile

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.automirrored.outlined.Assignment
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtwin.ai.core.components.RoadTwinCard
import com.roadtwin.ai.core.theme.*

@Composable
fun AboutScreen(
    onBack: () -> Unit
) {
    val context = LocalContext.current

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BackgroundWhite,
                border = BorderStroke(1.dp, CardBorderColor)
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
                        text = "About",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                }
            }
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp)
                .verticalScroll(rememberScrollState()),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // App Logo Badge
            Box(
                modifier = Modifier
                    .size(80.dp)
                    .clip(RoundedCornerShape(20.dp))
                    .background(RoadTwinBlue),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.AddRoad,
                    contentDescription = "RoadTwin AI Logo",
                    tint = Color.White,
                    modifier = Modifier.size(44.dp)
                )
            }

            Spacer(modifier = Modifier.height(16.dp))

            // App Title & Version
            Text(
                text = "RoadTwin AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextNavy
            )
            Spacer(modifier = Modifier.height(4.dp))
            Surface(
                color = RoadTwinBlueLight,
                shape = RoundedCornerShape(12.dp)
            ) {
                Text(
                    text = "VERSION 1.0.0",
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    color = RoadTwinBlue,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold
                )
            }

            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "Smarter Roads • Safer Journeys",
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.SemiBold,
                color = RoadTwinBlueDark
            )

            Spacer(modifier = Modifier.height(16.dp))

            // Short Description
            Text(
                text = "An AI-powered solution for real-time pothole detection and road monitoring to build safer and smarter cities.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMediumGray,
                textAlign = TextAlign.Center,
                lineHeight = 22.sp
            )

            Spacer(modifier = Modifier.height(24.dp))

            // Legal & License Links Card
            RoadTwinCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                borderColor = CardBorderColor
            ) {
                Column(modifier = Modifier.padding(vertical = 4.dp)) {
                    AboutMenuItem(
                        icon = Icons.Outlined.Policy,
                        title = "Privacy Policy",
                        onClick = { Toast.makeText(context, "RoadTwin AI prioritizes privacy. No telemetry without consent.", Toast.LENGTH_SHORT).show() }
                    )
                    HorizontalDivider(color = SurfaceVariantLight, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    AboutMenuItem(
                        icon = Icons.AutoMirrored.Outlined.Assignment,
                        title = "Terms of Service",
                        onClick = { Toast.makeText(context, "RoadTwin AI Standard Community Terms v1.0", Toast.LENGTH_SHORT).show() }
                    )
                    HorizontalDivider(color = SurfaceVariantLight, thickness = 1.dp, modifier = Modifier.padding(horizontal = 16.dp))
                    AboutMenuItem(
                        icon = Icons.Outlined.Code,
                        title = "Open Source Licenses",
                        onClick = { Toast.makeText(context, "Kotlin, Jetpack Compose, CameraX, TensorFlow Lite, Room", Toast.LENGTH_SHORT).show() }
                    )
                }
            }

            Spacer(modifier = Modifier.height(20.dp))

            // Technology Stack Card
            RoadTwinCard(
                modifier = Modifier.fillMaxWidth(),
                cornerRadius = 14.dp,
                borderColor = CardBorderColor
            ) {
                Column(
                    modifier = Modifier.padding(16.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Text(
                        text = "Core Technology Stack",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                    val techs = listOf(
                        "Kotlin", "Jetpack Compose", "CameraX", "YOLO26n FP32",
                        "Google Play Services TFLite", "Room Database",
                        "Firebase Firestore", "Fused Location", "WorkManager"
                    )
                    FlowRow(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        techs.forEach { tech ->
                            Surface(
                                color = SurfaceLight,
                                border = BorderStroke(1.dp, CardBorderColor),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = tech,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = TextNavy,
                                    fontWeight = FontWeight.Medium
                                )
                            }
                        }
                    }
                }
            }

            Spacer(modifier = Modifier.height(32.dp))

            // Footer
            Text(
                text = "Made with ❤️ for Better Roads",
                style = MaterialTheme.typography.labelMedium,
                color = TextDisabled,
                fontWeight = FontWeight.Medium
            )

            Spacer(modifier = Modifier.height(24.dp))
        }
    }
}

@Composable
private fun AboutMenuItem(
    icon: ImageVector,
    title: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = RoadTwinBlue,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = title,
                style = MaterialTheme.typography.bodyMedium,
                color = TextNavy,
                fontWeight = FontWeight.Medium
            )
        }
        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = TextDisabled,
            modifier = Modifier.size(18.dp)
        )
    }
}
