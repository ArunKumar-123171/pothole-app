package com.roadtwin.ai.feature.settings

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtwin.ai.core.components.RoadTwinBottomBar
import com.roadtwin.ai.core.components.RoadTwinCard
import com.roadtwin.ai.core.components.SectionHeader
import com.roadtwin.ai.core.theme.*
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
    var minStableFrames by remember { mutableIntStateOf(ModelConfig.minStableFrames) }
    var duplicateDistance by remember { mutableIntStateOf(ModelConfig.cooldownDistanceMeters.toInt()) }
    var cooldownSec by remember { mutableIntStateOf((ModelConfig.cooldownTimeMs / 1000).toInt()) }

    var saveImagesEnabled by remember { mutableStateOf(true) }
    var autoSyncEnabled by remember { mutableStateOf(true) }
    var darkModeEnabled by remember { mutableStateOf(false) }

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
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                }
            }
        },
        bottomBar = {
            RoadTwinBottomBar(
                currentScreen = "settings",
                onHomeClick = onNavigateToDashboard,
                onReportsClick = onNavigateToReports,
                onMapClick = onNavigateToMap,
                onSettingsClick = {},
                onProfileClick = onNavigateToProfile
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Detection Settings Section
            item {
                SectionHeader(title = "Detection Settings")
                Spacer(Modifier.height(4.dp))

                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        // Confidence Threshold Slider
                        Column {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = "Confidence Threshold",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextNavy,
                                    fontWeight = FontWeight.Medium
                                )
                                Text(
                                    text = String.format(Locale.US, "%.2f", confidenceThreshold),
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = TextNavy,
                                    fontWeight = FontWeight.Bold
                                )
                            }

                            Spacer(Modifier.height(6.dp))

                            Slider(
                                value = confidenceThreshold,
                                onValueChange = {
                                    confidenceThreshold = it
                                    ModelConfig.confidenceThreshold = it
                                },
                                valueRange = 0.20f..0.85f,
                                colors = SliderDefaults.colors(
                                    thumbColor = RoadTwinBlue,
                                    activeTrackColor = RoadTwinBlue,
                                    inactiveTrackColor = SurfaceVariantLight
                                )
                            )
                        }

                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        // Minimum Stable Frames
                        SettingsNavigationRow(
                            label = "Minimum Stable Frames",
                            value = "$minStableFrames",
                            onClick = {
                                minStableFrames = if (minStableFrames >= 5) 1 else minStableFrames + 1
                                ModelConfig.minStableFrames = minStableFrames
                            }
                        )

                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        // Duplicate Radius (meters)
                        SettingsNavigationRow(
                            label = "Duplicate Radius (meters)",
                            value = "$duplicateDistance",
                            onClick = {
                                duplicateDistance = if (duplicateDistance >= 15) 3 else duplicateDistance + 3
                                ModelConfig.cooldownDistanceMeters = duplicateDistance.toDouble()
                            }
                        )

                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        // Cooldown Time (seconds)
                        SettingsNavigationRow(
                            label = "Cooldown Time (seconds)",
                            value = "$cooldownSec",
                            onClick = {
                                cooldownSec = if (cooldownSec >= 15) 3 else cooldownSec + 3
                                ModelConfig.cooldownTimeMs = (cooldownSec * 1000).toLong()
                            }
                        )
                    }
                }
            }

            // 2. App Settings Section
            item {
                SectionHeader(title = "App Settings")
                Spacer(Modifier.height(4.dp))

                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        // Save Images Toggle
                        SettingsToggleRow(
                            icon = Icons.Outlined.PhotoCamera,
                            label = "Save Images",
                            checked = saveImagesEnabled,
                            onCheckedChange = { saveImagesEnabled = it }
                        )

                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        // Auto Sync Toggle
                        SettingsToggleRow(
                            icon = Icons.Outlined.CloudSync,
                            label = "Auto Sync",
                            checked = autoSyncEnabled,
                            onCheckedChange = { autoSyncEnabled = it }
                        )

                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        // Dark Mode Toggle
                        SettingsToggleRow(
                            icon = Icons.Outlined.DarkMode,
                            label = "Dark Mode",
                            checked = darkModeEnabled,
                            onCheckedChange = { darkModeEnabled = it }
                        )

                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        // Language Selection Row
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 4.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Icon(Icons.Outlined.Language, contentDescription = null, tint = RoadTwinBlue, modifier = Modifier.size(22.dp))
                                Text("Language", style = MaterialTheme.typography.bodyMedium, color = TextNavy, fontWeight = FontWeight.Medium)
                            }

                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("English", style = MaterialTheme.typography.bodyMedium, color = TextMediumGray)
                                Spacer(Modifier.width(4.dp))
                                Icon(Icons.Default.ChevronRight, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(20.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsNavigationRow(
    label: String,
    value: String,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable { onClick() }
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextNavy,
            fontWeight = FontWeight.Medium
        )

        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = value,
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = FontWeight.Bold,
                color = TextNavy
            )
            Spacer(Modifier.width(6.dp))
            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}

@Composable
fun SettingsToggleRow(
    icon: ImageVector,
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 2.dp),
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
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                color = TextNavy,
                fontWeight = FontWeight.Medium
            )
        }

        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.White,
                checkedTrackColor = RoadTwinGreen,
                uncheckedThumbColor = Color.White,
                uncheckedTrackColor = SurfaceVariantLight
            )
        )
    }
}
