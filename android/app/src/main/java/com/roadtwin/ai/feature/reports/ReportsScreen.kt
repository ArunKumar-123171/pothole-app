package com.roadtwin.ai.feature.reports

import android.graphics.BitmapFactory
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.roadtwin.ai.RoadTwinApplication
import com.roadtwin.ai.core.components.*
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import com.roadtwin.ai.feature.dashboard.DashboardBottomBar
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun ReportsScreen(
    onBack: () -> Unit,
    onNavigateToDetail: (sessionId: String) -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val reportsViewModel: ReportsViewModel = viewModel { ReportsViewModel(app.reportsRepository) }
    val uiState by reportsViewModel.uiState.collectAsStateWithLifecycle()

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
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDarkCharcoal)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Reports",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextDarkCharcoal
                        )
                    }

                    if (uiState.isSyncing) {
                        CircularProgressIndicator(color = RoadTwinOrange, modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                    } else {
                        IconButton(onClick = { reportsViewModel.syncNow() }, modifier = Modifier.size(32.dp)) {
                            Icon(Icons.Default.Refresh, contentDescription = "Sync", tint = TextDarkCharcoal)
                        }
                    }
                }
            }
        },
        bottomBar = {
            DashboardBottomBar(
                currentScreen = "reports",
                onHomeClick = onNavigateToDashboard,
                onMapClick = onNavigateToMap,
                onCameraClick = onNavigateToCamera,
                onReportsClick = {},
                onSettingsClick = onNavigateToSettings,
                onProfileClick = onNavigateToProfile
            )
        },
        containerColor = BackgroundOffWhite
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Orange Pill Filter Tabs (ALL, PENDING, SYNCED)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("All", "Pending", "Synced").forEach { tab ->
                    val isSelected = uiState.selectedTab.equals(tab, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(38.dp)
                            .clip(RoundedCornerShape(19.dp))
                            .background(if (isSelected) RoadTwinOrange else Color.Transparent)
                            .clickable { reportsViewModel.setFilterTab(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab.uppercase(),
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextMediumGray
                        )
                    }
                }
            }

            // Reports List
            if (uiState.filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize().weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = when (uiState.selectedTab) {
                            "Pending" -> "No pending reports"
                            "Synced" -> "No synced reports"
                            else -> "No reports yet"
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMediumGray
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 8.dp, bottom = 24.dp),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    items(uiState.filteredSessions, key = { it.sessionId }) { session ->
                        ReportItemCard(
                            session = session,
                            onClick = { onNavigateToDetail(session.sessionId) }
                        )
                    }

                    item {
                        Text(
                            text = if (uiState.selectedTab.equals("Pending", true)) "No more pending reports" else "No more reports",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDisabled,
                            textAlign = TextAlign.Center,
                            modifier = Modifier.fillMaxWidth().padding(vertical = 16.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ReportItemCard(
    session: MonitoringSessionEntity,
    onClick: () -> Unit
) {
    val dateSdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
    val formattedDate = remember(session.startTime) { dateSdf.format(Date(session.startTime)) }

    // Primary severity determination
    val primarySeverity = when {
        session.criticalSeverityCount > 0 -> "Critical"
        session.highSeverityCount > 0 -> "High"
        session.mediumSeverityCount > 0 -> "Medium"
        else -> "Low"
    }
    val severityColor = when (primarySeverity) {
        "Critical", "High" -> SeverityHigh
        "Medium" -> SeverityMedium
        else -> SeverityLow
    }

    Surface(
        shape = RoundedCornerShape(14.dp),
        color = BackgroundWhite,
        border = BorderStroke(1.dp, CardBorderColor),
        shadowElevation = 1.dp,
        modifier = Modifier.fillMaxWidth().clickable { onClick() }
    ) {
        Row(
            modifier = Modifier.padding(14.dp).fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
                // Title
                Text(
                    text = session.startAddress.ifBlank { session.title },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextDarkCharcoal,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                // Date & Time
                Text(
                    text = formattedDate,
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMediumGray
                )

                Spacer(modifier = Modifier.height(4.dp))

                // Distance and Potholes metrics
                Text(
                    text = "Distance: ${String.format(Locale.US, "%.2f km", session.distanceKm)} | Potholes: ${session.totalPotholes}",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMediumGray,
                    fontWeight = FontWeight.Medium
                )

                Spacer(modifier = Modifier.height(8.dp))

                // Severity Dot & Sync Pill Badge
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(modifier = Modifier.size(6.dp).background(severityColor, CircleShape))
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = primarySeverity,
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDarkCharcoal,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    val isSynced = session.syncStatus == "SYNCED"
                    Box(
                        modifier = Modifier
                            .clip(RoundedCornerShape(4.dp))
                            .background(if (isSynced) SeverityLow.copy(alpha = 0.15f) else StatusPending.copy(alpha = 0.15f))
                            .padding(horizontal = 6.dp, vertical = 2.dp)
                    ) {
                        Text(
                            text = if (isSynced) "SYNCED" else "PENDING",
                            style = MaterialTheme.typography.labelSmall,
                            fontWeight = FontWeight.Bold,
                            color = if (isSynced) SeverityLow else StatusPending,
                            fontSize = 10.sp
                        )
                    }
                }
            }

            // Right Thumbnail Photo
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .clip(RoundedCornerShape(8.dp))
                    .background(SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = Icons.Default.Image,
                    contentDescription = null,
                    tint = TextDisabled,
                    modifier = Modifier.size(24.dp)
                )
            }
        }
    }
}
