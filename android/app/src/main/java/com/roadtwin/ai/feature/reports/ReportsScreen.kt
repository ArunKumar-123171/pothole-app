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
import androidx.compose.material.icons.outlined.Refresh
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
import com.roadtwin.ai.data.local.MonitoringSessionEntity
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
                color = BackgroundWhite,
                border = BorderStroke(0.5.dp, CardBorderColor)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .statusBarsPadding()
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = onBack, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextNavy)
                        }
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "My Reports",
                            style = MaterialTheme.typography.titleLarge,
                            fontWeight = FontWeight.Bold,
                            color = TextNavy
                        )
                    }

                    if (uiState.isSyncing) {
                        CircularProgressIndicator(
                            color = RoadTwinBlue,
                            modifier = Modifier.size(22.dp),
                            strokeWidth = 2.dp
                        )
                    } else {
                        IconButton(onClick = { reportsViewModel.syncNow() }, modifier = Modifier.size(36.dp)) {
                            Icon(Icons.Outlined.Refresh, contentDescription = "Sync", tint = RoadTwinBlue)
                        }
                    }
                }
            }
        },
        bottomBar = {
            RoadTwinBottomBar(
                currentScreen = "reports",
                onHomeClick = onNavigateToDashboard,
                onReportsClick = {},
                onMapClick = onNavigateToMap,
                onSettingsClick = onNavigateToSettings,
                onProfileClick = onNavigateToProfile
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            // Blue Pill Filter Tabs (ALL, PENDING, SYNCED)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                listOf("All", "Pending", "Synced").forEach { tab ->
                    val isSelected = uiState.selectedTab.equals(tab, ignoreCase = true)
                    Box(
                        modifier = Modifier
                            .weight(1f)
                            .height(40.dp)
                            .clip(RoundedCornerShape(20.dp))
                            .background(if (isSelected) RoadTwinBlue else SurfaceVariantLight)
                            .clickable { reportsViewModel.setFilterTab(tab) },
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = tab,
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = if (isSelected) Color.White else TextMediumGray,
                            fontSize = 13.sp
                        )
                    }
                }
            }

            // Reports List
            if (uiState.filteredSessions.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f),
                    contentAlignment = Alignment.Center
                ) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            imageVector = Icons.Default.Description,
                            contentDescription = null,
                            tint = TextDisabled,
                            modifier = Modifier.size(48.dp)
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                        Text(
                            text = when (uiState.selectedTab) {
                                "Pending" -> "No pending reports"
                                "Synced" -> "No synced reports"
                                else -> "No reports yet"
                            },
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextMediumGray,
                            fontWeight = FontWeight.Medium
                        )
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .weight(1f)
                        .padding(horizontal = 20.dp),
                    contentPadding = PaddingValues(top = 4.dp, bottom = 24.dp),
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
                            text = if (uiState.selectedTab.equals("Pending", true)) "No more pending reports" else "All reports loaded",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextDisabled,
                            textAlign = TextAlign.Center,
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 16.dp)
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
    val dateSdf = remember { SimpleDateFormat("hh:mm a", Locale.getDefault()) }
    val formattedTime = remember(session.startTime) { dateSdf.format(Date(session.startTime)) }

    // Primary severity determination
    val primarySeverity = when {
        session.criticalSeverityCount > 0 -> "CRITICAL"
        session.highSeverityCount > 0 -> "HIGH"
        session.mediumSeverityCount > 0 -> "MEDIUM"
        else -> "LOW"
    }

    val context = LocalContext.current
    val sessionDir = remember(session.sessionId) {
        File(context.filesDir, "roadtwin/reports/${session.sessionId}")
    }
    val firstImage = remember(sessionDir) {
        if (sessionDir.exists()) {
            sessionDir.listFiles()?.firstOrNull { it.extension.lowercase() in listOf("jpg", "jpeg", "png") }
        } else null
    }
    val thumbnailBitmap = remember(firstImage) {
        firstImage?.let {
            try {
                BitmapFactory.decodeFile(it.absolutePath)
            } catch (e: Exception) {
                null
            }
        }
    }

    RoadTwinCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = onClick,
        cornerRadius = 16.dp
    ) {
        Row(
            modifier = Modifier
                .padding(14.dp)
                .fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Left Thumbnail
            Box(
                modifier = Modifier
                    .size(60.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(SurfaceLight),
                contentAlignment = Alignment.Center
            ) {
                if (thumbnailBitmap != null) {
                    Image(
                        bitmap = thumbnailBitmap.asImageBitmap(),
                        contentDescription = "Pothole thumbnail",
                        modifier = Modifier.fillMaxSize(),
                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                    )
                } else {
                    Icon(
                        imageVector = Icons.Default.Image,
                        contentDescription = null,
                        tint = TextDisabled,
                        modifier = Modifier.size(28.dp)
                    )
                }
            }

            Spacer(Modifier.width(12.dp))

            // Middle Content: Address, Pothole Count + Time, Severity Chip
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = session.startAddress.ifBlank { session.title },
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = TextNavy,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = "${session.totalPotholes} potholes • $formattedTime",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMediumGray
                )

                Spacer(modifier = Modifier.height(6.dp))

                SeverityBadge(severity = primarySeverity)
            }

            Spacer(Modifier.width(8.dp))

            // Right: Sync Status Badge
            SyncStatusBadge(
                syncStatus = session.syncStatus,
                isCompleted = session.status == "COMPLETED"
            )
        }
    }
}
