package com.roadtwin.ai.feature.dashboard

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadtwin.ai.RoadTwinApplication
import com.roadtwin.ai.core.components.*
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun DashboardScreen(
    onNavigateToCamera: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToDetail: (sessionId: String) -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val dashboardViewModel: DashboardViewModel = viewModel { DashboardViewModel(app.reportsRepository) }
    val uiState by dashboardViewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        bottomBar = {
            DashboardBottomBar(
                currentScreen = "home",
                onHomeClick = {},
                onMapClick = onNavigateToMap,
                onCameraClick = onNavigateToCamera,
                onReportsClick = onNavigateToReports,
                onSettingsClick = onNavigateToSettings,
                onProfileClick = onNavigateToProfile
            )
        },
        containerColor = BackgroundOffWhite
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(18.dp)
        ) {
            // Header: "Good Morning, Operator" and Notification Bell
            item {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Good Morning,",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMediumGray
                        )
                        Text(
                            text = "Operator",
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkCharcoal
                        )
                    }

                    Box(
                        modifier = Modifier
                            .size(44.dp)
                            .clip(CircleShape)
                            .background(BackgroundWhite)
                            .padding(4.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Notifications,
                            contentDescription = "Notifications",
                            tint = TextDarkCharcoal,
                            modifier = Modifier.size(24.dp)
                        )
                        Box(
                            modifier = Modifier
                                .align(Alignment.TopEnd)
                                .size(8.dp)
                                .background(SeverityHigh, CircleShape)
                        )
                    }
                }
            }

            // Road Health Score Card (Screens 3 & 9)
            item {
                Surface(
                    shape = RoundedCornerShape(16.dp),
                    color = BackgroundWhite,
                    border = BorderStroke(1.dp, CardBorderColor),
                    shadowElevation = 1.dp,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Road Health Score",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextMediumGray,
                                fontWeight = FontWeight.Medium
                            )
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = null,
                                tint = TextMediumGray
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            if (uiState.roadHealthScore != null) {
                                val score = uiState.roadHealthScore!!
                                Column {
                                    Text(
                                        text = "$score / 100",
                                        style = MaterialTheme.typography.headlineLarge,
                                        fontWeight = FontWeight.ExtraBold,
                                        color = TextDarkCharcoal
                                    )
                                    Text(
                                        text = if (score > 70) "Good" else if (score > 40) "Moderate" else "Critical",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = if (score > 70) SeverityLow else RoadTwinOrange,
                                        fontWeight = FontWeight.Bold
                                    )
                                }

                                // Orange Smooth Sparkline Curve
                                SparklineCanvas(color = RoadTwinOrange, isActive = true)
                            } else {
                                Column {
                                    Text(
                                        text = "No data",
                                        style = MaterialTheme.typography.titleLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = TextDarkCharcoal
                                    )
                                    Text(
                                        text = "Not enough reports",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = TextDisabled
                                    )
                                }

                                // Gray Flat Line
                                SparklineCanvas(color = SurfaceVariantLight, isActive = false)
                            }
                        }
                    }
                }
            }

            // 4 Stat Cards in 2x2 Grid
            item {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HomeStatCard(
                            title = "Total Potholes",
                            count = "${uiState.totalPotholes}",
                            icon = Icons.Outlined.WarningAmber,
                            iconTint = RoadTwinOrange,
                            modifier = Modifier.weight(1f)
                        )
                        HomeStatCard(
                            title = "Pending Reports",
                            count = "${uiState.pendingUploads}",
                            icon = Icons.Outlined.ReportProblem,
                            iconTint = SeverityHigh,
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        HomeStatCard(
                            title = "Synced Reports",
                            count = "${uiState.syncedReports}",
                            icon = Icons.Outlined.CloudDone,
                            iconTint = SeverityLow,
                            modifier = Modifier.weight(1f)
                        )
                        HomeStatCard(
                            title = "High / Critical",
                            count = "${uiState.highSeverityCount}",
                            icon = Icons.Outlined.Shield,
                            iconTint = SeverityHigh,
                            modifier = Modifier.weight(1f)
                        )
                    }
                }
            }

            // Recent Activity Section
            item {
                Text(
                    text = "Recent Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkCharcoal
                )
            }

            if (uiState.recentSessions.isEmpty()) {
                item {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 12.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Text(
                            text = "No recent activity",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMediumGray,
                            fontWeight = FontWeight.Medium
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Start monitoring to create reports.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDisabled
                        )
                    }
                }
            } else {
                items(uiState.recentSessions.take(3), key = { it.sessionId }) { session ->
                    RecentSessionCard(
                        session = session,
                        onClick = { onNavigateToDetail(session.sessionId) }
                    )
                }
            }

            // START MONITORING Big Orange Button
            item {
                Button(
                    onClick = onNavigateToCamera,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(54.dp),
                    shape = RoundedCornerShape(27.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = RoadTwinOrange)
                ) {
                    Text(
                        text = "START MONITORING",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.ExtraBold,
                        color = Color.White,
                        letterSpacing = 0.5.sp
                    )
                }
            }

            // Secondary Outlined Action Row: [ View Reports ] and [ View Map ]
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    OutlinedButton(
                        onClick = onNavigateToReports,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = BackgroundWhite)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Description,
                            contentDescription = null,
                            tint = TextDarkCharcoal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "View Reports",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkCharcoal
                        )
                    }

                    OutlinedButton(
                        onClick = onNavigateToMap,
                        modifier = Modifier
                            .weight(1f)
                            .height(48.dp),
                        shape = RoundedCornerShape(12.dp),
                        border = BorderStroke(1.dp, CardBorderColor),
                        colors = ButtonDefaults.outlinedButtonColors(containerColor = BackgroundWhite)
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.Map,
                            contentDescription = null,
                            tint = TextDarkCharcoal,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "View Map",
                            style = MaterialTheme.typography.bodyMedium,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkCharcoal
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun HomeStatCard(
    title: String,
    count: String,
    icon: ImageVector,
    iconTint: Color,
    modifier: Modifier = Modifier
) {
    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BackgroundWhite,
        border = BorderStroke(1.dp, CardBorderColor),
        shadowElevation = 1.dp,
        modifier = modifier
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(22.dp)
            )

            Text(
                text = title,
                style = MaterialTheme.typography.bodySmall,
                color = TextMediumGray,
                fontWeight = FontWeight.Medium
            )

            Text(
                text = count,
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.ExtraBold,
                color = TextDarkCharcoal
            )
        }
    }
}

@Composable
fun SparklineCanvas(color: Color, isActive: Boolean) {
    Canvas(modifier = Modifier.width(100.dp).height(44.dp)) {
        val w = size.width
        val h = size.height

        val path = Path().apply {
            if (isActive) {
                moveTo(0f, h * 0.7f)
                cubicTo(w * 0.3f, h * 0.9f, w * 0.6f, h * 0.2f, w, h * 0.4f)
            } else {
                moveTo(0f, h * 0.5f)
                lineTo(w, h * 0.5f)
            }
        }

        drawPath(
            path = path,
            color = color,
            style = Stroke(width = 3.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round)
        )
    }
}

@Composable
fun RecentSessionCard(
    session: MonitoringSessionEntity,
    onClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()) }
    val timeText = remember(session.startTime) { sdf.format(Date(session.startTime)) }

    Surface(
        shape = RoundedCornerShape(12.dp),
        color = BackgroundWhite,
        border = BorderStroke(1.dp, CardBorderColor),
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 8.dp)) {
                Text(
                    text = session.startAddress.ifBlank { session.title },
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkCharcoal,
                    maxLines = 1
                )
                Text(
                    text = "$timeText • ${String.format(Locale.US, "%.2f km", session.distanceKm)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMediumGray
                )
            }

            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(6.dp))
                    .background(if (session.totalPotholes > 0) SeverityHigh.copy(alpha = 0.12f) else SeverityLow.copy(alpha = 0.12f))
                    .padding(horizontal = 8.dp, vertical = 4.dp)
            ) {
                Text(
                    text = "${session.totalPotholes} Potholes",
                    style = MaterialTheme.typography.labelSmall,
                    fontWeight = FontWeight.Bold,
                    color = if (session.totalPotholes > 0) SeverityHigh else SeverityLow
                )
            }
        }
    }
}

@Composable
fun DashboardBottomBar(
    currentScreen: String,
    onHomeClick: () -> Unit,
    onMapClick: () -> Unit,
    onCameraClick: () -> Unit,
    onReportsClick: () -> Unit,
    onSettingsClick: () -> Unit,
    onProfileClick: () -> Unit
) {
    NavigationBar(
        containerColor = BackgroundWhite,
        tonalElevation = 2.dp,
        modifier = Modifier.height(72.dp),
        windowInsets = WindowInsets.navigationBars
    ) {
        NavigationBarItem(
            selected = currentScreen == "home",
            onClick = onHomeClick,
            icon = { Icon(Icons.Default.Home, contentDescription = "Home") },
            label = { Text("Home", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoadTwinOrange,
                selectedTextColor = RoadTwinOrange,
                indicatorColor = RoadTwinOrangeLight,
                unselectedIconColor = TextMediumGray,
                unselectedTextColor = TextMediumGray
            )
        )

        NavigationBarItem(
            selected = currentScreen == "reports",
            onClick = onReportsClick,
            icon = { Icon(Icons.Default.Description, contentDescription = "Reports") },
            label = { Text("Reports", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoadTwinOrange,
                selectedTextColor = RoadTwinOrange,
                indicatorColor = RoadTwinOrangeLight,
                unselectedIconColor = TextMediumGray,
                unselectedTextColor = TextMediumGray
            )
        )

        NavigationBarItem(
            selected = currentScreen == "map",
            onClick = onMapClick,
            icon = { Icon(Icons.Default.Place, contentDescription = "Map") },
            label = { Text("Map", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoadTwinOrange,
                selectedTextColor = RoadTwinOrange,
                indicatorColor = RoadTwinOrangeLight,
                unselectedIconColor = TextMediumGray,
                unselectedTextColor = TextMediumGray
            )
        )

        NavigationBarItem(
            selected = currentScreen == "settings",
            onClick = onSettingsClick,
            icon = { Icon(Icons.Default.Tune, contentDescription = "Settings") },
            label = { Text("Settings", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoadTwinOrange,
                selectedTextColor = RoadTwinOrange,
                indicatorColor = RoadTwinOrangeLight,
                unselectedIconColor = TextMediumGray,
                unselectedTextColor = TextMediumGray
            )
        )

        NavigationBarItem(
            selected = currentScreen == "profile",
            onClick = onProfileClick,
            icon = { Icon(Icons.Default.Person, contentDescription = "Profile") },
            label = { Text("Profile", fontWeight = FontWeight.Bold, fontSize = 10.sp) },
            colors = NavigationBarItemDefaults.colors(
                selectedIconColor = RoadTwinOrange,
                selectedTextColor = RoadTwinOrange,
                indicatorColor = RoadTwinOrangeLight,
                unselectedIconColor = TextMediumGray,
                unselectedTextColor = TextMediumGray
            )
        )
    }
}
