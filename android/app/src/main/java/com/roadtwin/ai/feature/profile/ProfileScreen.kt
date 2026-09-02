package com.roadtwin.ai.feature.profile

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.feature.dashboard.DashboardBottomBar

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBenchmarks: () -> Unit
) {
    val context = LocalContext.current
    var showAboutDialog by remember { mutableStateOf(false) }

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
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDarkCharcoal)
                    }
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "About RoadTwin AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkCharcoal
                    )
                }
            }
        },
        bottomBar = {
            DashboardBottomBar(
                currentScreen = "profile",
                onHomeClick = onNavigateToDashboard,
                onMapClick = onNavigateToMap,
                onCameraClick = onNavigateToCamera,
                onReportsClick = onNavigateToReports,
                onSettingsClick = onNavigateToSettings,
                onProfileClick = {}
            )
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Spacer(modifier = Modifier.height(28.dp))

            // Hexagonal Shield with Road 'V'
            Box(
                modifier = Modifier.size(88.dp),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val w = size.width
                    val h = size.height

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
                        style = Stroke(width = 5.dp.toPx())
                    )

                    val vPath = Path().apply {
                        moveTo(w * 0.30f, h * 0.35f)
                        lineTo(w * 0.5f, h * 0.65f)
                        lineTo(w * 0.70f, h * 0.35f)
                    }
                    drawPath(
                        path = vPath,
                        color = TextDarkCharcoal,
                        style = Stroke(width = 6.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
                    )

                    drawCircle(
                        color = TextDarkCharcoal,
                        radius = 4.dp.toPx(),
                        center = Offset(w * 0.5f, h * 0.28f)
                    )
                }
            }

            Spacer(modifier = Modifier.height(16.dp))

            // RoadTwin AI Title & Subtitle
            Text(
                text = "RoadTwin AI",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = TextDarkCharcoal
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Intelligent Road Monitoring",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMediumGray
            )

            Spacer(modifier = Modifier.height(4.dp))

            Text(
                text = "Version 1.0.0",
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled
            )

            Spacer(modifier = Modifier.height(36.dp))

            // Menu Items List (Screen 16)
            Surface(
                shape = RoundedCornerShape(14.dp),
                color = BackgroundWhite,
                border = BorderStroke(1.dp, CardBorderColor),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column {
                    ProfileMenuRow(
                        icon = Icons.Outlined.Person,
                        label = "About Application",
                        onClick = { showAboutDialog = true }
                    )
                    HorizontalDivider(color = SurfaceVariantLight, thickness = 0.8.dp)

                    ProfileMenuRow(
                        icon = Icons.Outlined.Layers,
                        label = "Model Management",
                        onClick = onNavigateToBenchmarks
                    )
                    HorizontalDivider(color = SurfaceVariantLight, thickness = 0.8.dp)

                    ProfileMenuRow(
                        icon = Icons.Outlined.Lock,
                        label = "Privacy Policy",
                        onClick = { Toast.makeText(context, "All data is stored securely on device", Toast.LENGTH_SHORT).show() }
                    )
                    HorizontalDivider(color = SurfaceVariantLight, thickness = 0.8.dp)

                    ProfileMenuRow(
                        icon = Icons.Outlined.HelpOutline,
                        label = "Help & Support",
                        trailingTag = "v2.0 (Active)",
                        onClick = { Toast.makeText(context, "RoadTwin AI v2.0 edge monitoring active", Toast.LENGTH_SHORT).show() }
                    )
                }
            }
        }
    }

    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            containerColor = BackgroundWhite,
            title = { Text("About RoadTwin AI", color = TextDarkCharcoal, fontWeight = FontWeight.Bold) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("RoadTwin AI is an intelligent road monitoring mobile system that detects potholes on-device using a quantized YOLO26n vision model and produces standardized inspection reports.", color = TextDarkCharcoal, fontSize = 14.sp)
                    Spacer(Modifier.height(4.dp))
                    Text("• On-Device YOLO26n Inference", color = TextMediumGray, fontSize = 13.sp)
                    Text("• Multi-Frame IoU Stabilization", color = TextMediumGray, fontSize = 13.sp)
                    Text("• Offline-First Room Architecture", color = TextMediumGray, fontSize = 13.sp)
                    Text("• Native PDF Report Generator", color = TextMediumGray, fontSize = 13.sp)
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Close", color = RoadTwinOrange, fontWeight = FontWeight.Bold)
                }
            }
        )
    }
}

@Composable
fun ProfileMenuRow(
    icon: ImageVector,
    label: String,
    trailingTag: String? = null,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(horizontal = 16.dp, vertical = 15.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = TextDarkCharcoal,
            modifier = Modifier.size(20.dp)
        )

        Spacer(Modifier.width(16.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextDarkCharcoal,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        if (trailingTag != null) {
            Text(
                text = trailingTag,
                style = MaterialTheme.typography.labelSmall,
                color = TextDisabled,
                fontSize = 11.sp
            )
            Spacer(Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.AutoMirrored.Filled.ArrowForward,
            contentDescription = null,
            tint = SurfaceVariantLight,
            modifier = Modifier.size(16.dp)
        )
    }
}
