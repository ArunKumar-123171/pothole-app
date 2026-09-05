package com.roadtwin.ai.feature.dashboard

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
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
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
            RoadTwinBottomBar(
                currentScreen = "home",
                onHomeClick = {},
                onReportsClick = onNavigateToReports,
                onMapClick = onNavigateToMap,
                onSettingsClick = onNavigateToSettings,
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
            contentPadding = PaddingValues(top = 12.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // 1. Header: Branding & Profile Avatar
            item {
                DashboardHeader(
                    onProfileClick = onNavigateToProfile
                )
            }

            // 2. Greeting & Inspection Subtitle
            item {
                DashboardGreeting()
            }

            // 3. PRIMARY HERO ACTION: START MONITORING CARD (Immediate access, no scrolling)
            item {
                StartMonitoringHeroCard(
                    onClick = onNavigateToCamera
                )
            }

            // 4. ROAD HEALTH SCORE (Directly beneath Start Monitoring)
            item {
                RoadHealthScoreCard(
                    score = uiState.roadHealthScore,
                    hasSessions = uiState.sessionsCount > 0
                )
            }

            // 5. 2x2 STATISTICS GRID
            item {
                StatisticsGrid(
                    totalPotholes = uiState.totalPotholes,
                    pendingReports = uiState.pendingUploads,
                    syncedReports = uiState.syncedReports,
                    highSeverityCount = uiState.highSeverityCount
                )
            }

            // 6. RECENT ACTIVITY
            item {
                SectionHeader(
                    title = "Recent Activity",
                    actionText = if (uiState.recentSessions.isNotEmpty()) "See All >" else null,
                    onActionClick = onNavigateToReports
                )
            }

            if (uiState.recentSessions.isEmpty()) {
                item {
                    RecentActivityEmptyState()
                }
            } else {
                items(uiState.recentSessions.take(3), key = { it.sessionId }) { session ->
                    RecentSessionItem(
                        session = session,
                        onClick = { onNavigateToDetail(session.sessionId) }
                    )
                }
            }

            // 7. QUICK ACTIONS (View Reports, View Map)
            item {
                SectionHeader(
                    title = "Quick Actions"
                )
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    RoadTwinOutlinedButton(
                        text = "View Reports",
                        onClick = onNavigateToReports,
                        icon = Icons.Outlined.Description,
                        borderColor = CardBorderColor,
                        contentColor = TextNavy,
                        modifier = Modifier.weight(1f),
                        height = 48.dp
                    )

                    RoadTwinOutlinedButton(
                        text = "View Map",
                        onClick = onNavigateToMap,
                        icon = Icons.Outlined.Map,
                        borderColor = CardBorderColor,
                        contentColor = TextNavy,
                        modifier = Modifier.weight(1f),
                        height = 48.dp
                    )
                }
            }
        }
    }
}

@Composable
fun DashboardHeader(
    onProfileClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .padding(top = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            RoadTwinLogoMark()
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        text = "RoadTwin",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = TextNavy,
                        fontSize = 21.sp
                    )
                    Spacer(modifier = Modifier.width(3.dp))
                    Text(
                        text = "AI",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Black,
                        color = RoadTwinBlue,
                        fontSize = 21.sp
                    )
                }
                Text(
                    text = "Smarter Roads • Safer Journeys",
                    style = MaterialTheme.typography.bodySmall,
                    fontSize = 11.sp,
                    color = TextMediumGray,
                    fontWeight = FontWeight.Medium
                )
            }
        }

        Box(
            modifier = Modifier
                .size(42.dp)
                .clip(CircleShape)
                .background(RoadTwinBlueLight)
                .clickable(onClick = onProfileClick),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                imageVector = Icons.Default.Person,
                contentDescription = "Profile",
                tint = RoadTwinBlue,
                modifier = Modifier.size(24.dp)
            )
        }
    }
}

@Composable
fun RoadTwinLogoMark(modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .size(40.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.linearGradient(
                    listOf(RoadTwinBlue, RoadTwinBlueDark)
                )
            ),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(22.dp)) {
            val w = size.width
            val h = size.height

            // Perspective trapezoid path representing the road ahead
            val roadPath = Path().apply {
                moveTo(w * 0.35f, h * 0.16f)
                lineTo(w * 0.65f, h * 0.16f)
                lineTo(w * 0.90f, h * 0.86f)
                lineTo(w * 0.10f, h * 0.86f)
                close()
            }
            drawPath(
                path = roadPath,
                color = Color.White.copy(alpha = 0.28f)
            )

            // Dashed center road dividers
            val strokeW = 2.dp.toPx()
            drawLine(
                color = Color.White,
                start = Offset(w * 0.5f, h * 0.22f),
                end = Offset(w * 0.5f, h * 0.44f),
                strokeWidth = strokeW,
                cap = StrokeCap.Round
            )
            drawLine(
                color = Color.White,
                start = Offset(w * 0.5f, h * 0.54f),
                end = Offset(w * 0.5f, h * 0.80f),
                strokeWidth = strokeW * 1.25f,
                cap = StrokeCap.Round
            )
        }
    }
}

@Composable
fun DashboardGreeting(modifier: Modifier = Modifier) {
    val greeting = remember {
        val hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY)
        when (hour) {
            in 4..11 -> "Good Morning,"
            in 12..16 -> "Good Afternoon,"
            in 17..21 -> "Good Evening,"
            else -> "Good Evening,"
        }
    }

    Column(modifier = modifier.padding(top = 2.dp)) {
        Text(
            text = greeting,
            style = MaterialTheme.typography.bodyMedium,
            color = TextMediumGray,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = "Operator",
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = FontWeight.ExtraBold,
            color = TextNavy
        )
        Spacer(modifier = Modifier.height(2.dp))
        Text(
            text = "Let's make our roads safer today!",
            style = MaterialTheme.typography.bodySmall,
            color = TextMediumGray
        )
    }
}

@Composable
fun StartMonitoringHeroCard(
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Card(
        onClick = onClick,
        modifier = modifier
            .fillMaxWidth()
            .height(82.dp),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp, pressedElevation = 4.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(
                    Brush.horizontalGradient(
                        listOf(RoadTwinBlue, RoadTwinBlueDark)
                    )
                )
                .padding(horizontal = 16.dp, vertical = 14.dp),
            contentAlignment = Alignment.CenterStart
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.weight(1f)
                ) {
                    // Play icon in semi-translucent circular container
                    Box(
                        modifier = Modifier
                            .size(50.dp)
                            .clip(CircleShape)
                            .background(Color.White.copy(alpha = 0.22f)),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.PlayArrow,
                            contentDescription = null,
                            tint = Color.White,
                            modifier = Modifier.size(30.dp)
                        )
                    }

                    Spacer(modifier = Modifier.width(14.dp))

                    Column {
                        Text(
                            text = "START MONITORING",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 16.sp,
                            letterSpacing = 0.6.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = "Detect potholes in real-time",
                            style = MaterialTheme.typography.bodySmall,
                            color = Color.White.copy(alpha = 0.88f),
                            fontWeight = FontWeight.Normal,
                            fontSize = 12.sp
                        )
                    }
                }

                // Right arrow icon container
                Box(
                    modifier = Modifier
                        .size(34.dp)
                        .clip(CircleShape)
                        .background(Color.White.copy(alpha = 0.15f)),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Start Monitoring",
                        tint = Color.White,
                        modifier = Modifier.size(20.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun RoadHealthScoreCard(
    score: Int?,
    hasSessions: Boolean,
    modifier: Modifier = Modifier
) {
    val displayScore = score ?: 100
    val (statusText, statusColor) = when {
        displayScore >= 80 -> "Excellent" to Color(0xFF4ADE80)
        displayScore >= 65 -> "Good" to Color(0xFF86EFAC)
        displayScore >= 50 -> "Moderate" to Color(0xFFFDE047)
        displayScore >= 30 -> "Poor" to Color(0xFFFDBA74)
        else -> "Critical" to Color(0xFFF87171)
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        shape = RoundedCornerShape(18.dp),
        colors = CardDefaults.cardColors(containerColor = Color.Transparent),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(
                    Brush.horizontalGradient(
                        listOf(Color(0xFF0F172A), Color(0xFF1E293B))
                    )
                )
                .padding(20.dp)
        ) {
            Column {
                // Top Row: Title & Trend Pill
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "ROAD HEALTH SCORE",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White.copy(alpha = 0.75f),
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 0.6.sp
                    )

                    Surface(
                        color = Color.White.copy(alpha = 0.12f),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = if (hasSessions) "↗ Improving" else "• Ready",
                                color = Color(0xFF86EFAC),
                                fontSize = 11.sp,
                                fontWeight = FontWeight.SemiBold
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Score + Status + 5-Bar Visualizer Row
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "$displayScore / 100",
                            style = MaterialTheme.typography.headlineLarge,
                            fontWeight = FontWeight.ExtraBold,
                            color = Color.White,
                            fontSize = 32.sp
                        )
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = if (!hasSessions && score == null) "Optimal • No defects detected" else statusText,
                            style = MaterialTheme.typography.titleSmall,
                            color = statusColor,
                            fontWeight = FontWeight.Bold
                        )
                    }

                    // 5-Bar Road Quality Visualizer
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(4.dp),
                        verticalAlignment = Alignment.Bottom,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        val activeBars = ((displayScore / 100f) * 5).toInt().coerceIn(1, 5)
                        listOf(12, 18, 24, 30, 36).forEachIndexed { index, heightDp ->
                            val isBarActive = index < activeBars
                            Box(
                                modifier = Modifier
                                    .width(6.dp)
                                    .height(heightDp.dp)
                                    .clip(RoundedCornerShape(3.dp))
                                    .background(
                                        if (isBarActive) statusColor else Color.White.copy(alpha = 0.2f)
                                    )
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Horizontal Progress Meter Bar
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(6.dp)
                        .clip(RoundedCornerShape(3.dp))
                        .background(Color.White.copy(alpha = 0.18f))
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth((displayScore / 100f).coerceIn(0.05f, 1f))
                            .fillMaxHeight()
                            .clip(RoundedCornerShape(3.dp))
                            .background(statusColor)
                    )
                }
            }
        }
    }
}

@Composable
fun StatisticsGrid(
    totalPotholes: Int,
    pendingReports: Int,
    syncedReports: Int,
    highSeverityCount: Int,
    modifier: Modifier = Modifier
) {
    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeStatCard(
                title = "Total Potholes",
                count = "$totalPotholes",
                icon = Icons.Outlined.WarningAmber,
                iconTint = RoadTwinRed,
                iconBg = RoadTwinRedLight,
                modifier = Modifier.weight(1f)
            )
            HomeStatCard(
                title = "Pending Reports",
                count = "$pendingReports",
                icon = Icons.Outlined.ReportProblem,
                iconTint = RoadTwinOrange,
                iconBg = RoadTwinOrangeLight,
                modifier = Modifier.weight(1f)
            )
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            HomeStatCard(
                title = "Synced Reports",
                count = "$syncedReports",
                icon = Icons.Outlined.CloudDone,
                iconTint = RoadTwinGreen,
                iconBg = RoadTwinGreenLight,
                modifier = Modifier.weight(1f)
            )
            HomeStatCard(
                title = "High / Critical",
                count = "$highSeverityCount",
                icon = Icons.Outlined.Shield,
                iconTint = RoadTwinRed,
                iconBg = RoadTwinRedLight,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@Composable
fun HomeStatCard(
    title: String,
    count: String,
    icon: ImageVector,
    iconTint: Color,
    iconBg: Color,
    modifier: Modifier = Modifier
) {
    RoadTwinCard(
        modifier = modifier,
        cornerRadius = 16.dp
    ) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Box(
                modifier = Modifier
                    .size(38.dp)
                    .clip(RoundedCornerShape(10.dp))
                    .background(iconBg),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = iconTint,
                    modifier = Modifier.size(22.dp)
                )
            }

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
                color = TextNavy
            )
        }
    }
}

@Composable
fun RecentActivityEmptyState(modifier: Modifier = Modifier) {
    RoadTwinCard(
        modifier = modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(24.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Outlined.History,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(36.dp)
            )
            Spacer(modifier = Modifier.height(8.dp))
            Text(
                text = "No recent activity",
                style = MaterialTheme.typography.bodyMedium,
                color = TextMediumGray,
                fontWeight = FontWeight.Medium
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = "Start monitoring to inspect and record roads.",
                style = MaterialTheme.typography.bodySmall,
                color = TextDisabled
            )
        }
    }
}

@Composable
fun RecentSessionItem(
    session: MonitoringSessionEntity,
    onClick: () -> Unit
) {
    val sdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val timeText = remember(session.startTime) { sdf.format(Date(session.startTime)) }
    val hasPotholes = session.totalPotholes > 0
    val locationTitle = remember(session) {
        when {
            session.startAddress.isNotBlank() -> session.startAddress
            session.startLatitude != 0.0 || session.startLongitude != 0.0 -> "GPS location recorded"
            session.title.isNotBlank() -> session.title
            else -> "Monitoring Session"
        }
    }

    RoadTwinCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        cornerRadius = 14.dp
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.weight(1f)
            ) {
                // Pin Icon
                Box(
                    modifier = Modifier
                        .size(38.dp)
                        .clip(CircleShape)
                        .background(if (hasPotholes) RoadTwinRedLight else RoadTwinGreenLight),
                    contentAlignment = Alignment.Center
                ) {
                    Icon(
                        imageVector = Icons.Default.LocationOn,
                        contentDescription = null,
                        tint = if (hasPotholes) RoadTwinRed else RoadTwinGreen,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(Modifier.width(12.dp))

                Column {
                    Text(
                        text = locationTitle,
                        style = MaterialTheme.typography.bodyMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(Modifier.height(2.dp))
                    Text(
                        text = "${session.totalPotholes} ${if (session.totalPotholes == 1) "pothole" else "potholes"} • $timeText",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMediumGray
                    )
                }
            }

            Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = TextDisabled,
                modifier = Modifier.size(20.dp)
            )
        }
    }
}
