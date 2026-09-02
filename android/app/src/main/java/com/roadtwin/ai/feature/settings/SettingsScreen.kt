package com.roadtwin.ai.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.feature.dashboard.DashboardBottomBar
import com.roadtwin.ai.ml.model.ModelConfig
import java.util.Locale

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    var confidenceThreshold by remember { mutableFloatStateOf(ModelConfig.confidenceThreshold) }
    var iouThreshold by remember { mutableFloatStateOf(ModelConfig.nmsIouThreshold) }
    var minStableFrames by remember { mutableFloatStateOf(ModelConfig.minStableFrames.toFloat()) }
    var duplicateDistance by remember { mutableFloatStateOf(ModelConfig.cooldownDistanceMeters.toFloat()) }
    var duplicateCooldownSec by remember { mutableFloatStateOf((ModelConfig.cooldownTimeMs / 1000).toFloat()) }

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
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkCharcoal
                    )
                }
            }
        },
        bottomBar = {
            DashboardBottomBar(
                currentScreen = "settings",
                onHomeClick = onNavigateToDashboard,
                onMapClick = onNavigateToMap,
                onCameraClick = onNavigateToCamera,
                onReportsClick = onNavigateToReports,
                onSettingsClick = {},
                onProfileClick = onNavigateToProfile
            )
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 24.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            item {
                Text(
                    text = "Detection Settings",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkCharcoal
                )
            }

            // 1. Confidence Threshold
            item {
                SettingsSliderItem(
                    label = "Confidence Threshold",
                    value = confidenceThreshold,
                    onValueChange = {
                        confidenceThreshold = it
                        ModelConfig.confidenceThreshold = it
                    },
                    valueRange = 0.20f..0.85f,
                    displayValue = String.format(Locale.US, "%.2f", confidenceThreshold)
                )
            }

            // 2. IoU Threshold
            item {
                SettingsSliderItem(
                    label = "IoU Threshold",
                    value = iouThreshold,
                    onValueChange = {
                        iouThreshold = it
                        ModelConfig.nmsIouThreshold = it
                    },
                    valueRange = 0.20f..0.70f,
                    displayValue = String.format(Locale.US, "%.2f", iouThreshold)
                )
            }

            // 3. Stable Frames (Consecutive)
            item {
                SettingsSliderItem(
                    label = "Stable Frames (Consecutive)",
                    value = minStableFrames,
                    onValueChange = {
                        minStableFrames = it
                        ModelConfig.minStableFrames = it.toInt()
                    },
                    valueRange = 1f..6f,
                    displayValue = "${minStableFrames.toInt()}"
                )
            }

            // 4. Duplicate Distance (meters)
            item {
                SettingsSliderItem(
                    label = "Duplicate Distance (meters)",
                    value = duplicateDistance,
                    onValueChange = {
                        duplicateDistance = it
                        ModelConfig.cooldownDistanceMeters = it.toDouble()
                    },
                    valueRange = 5f..50f,
                    displayValue = "${duplicateDistance.toInt()}"
                )
            }

            // 5. Duplicate Cooldown (seconds)
            item {
                SettingsSliderItem(
                    label = "Duplicate Cooldown (seconds)",
                    value = duplicateCooldownSec,
                    onValueChange = {
                        duplicateCooldownSec = it
                        ModelConfig.cooldownTimeMs = (it * 1000).toLong()
                    },
                    valueRange = 2f..30f,
                    displayValue = "${duplicateCooldownSec.toInt()}"
                )
            }
        }
    }
}

@Composable
fun SettingsSliderItem(
    label: String,
    value: Float,
    onValueChange: (Float) -> Unit,
    valueRange: ClosedFloatingPointRange<Float>,
    displayValue: String
) {
    Column {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextDarkCharcoal,
                fontWeight = FontWeight.Medium
            )
            Text(
                text = displayValue,
                style = MaterialTheme.typography.bodyMedium,
                color = TextDarkCharcoal,
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(Modifier.height(4.dp))

        Slider(
            value = value,
            onValueChange = onValueChange,
            valueRange = valueRange,
            colors = SliderDefaults.colors(
                thumbColor = RoadTwinOrange,
                activeTrackColor = RoadTwinOrange,
                inactiveTrackColor = SurfaceVariantLight
            )
        )
    }
}
