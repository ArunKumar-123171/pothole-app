package com.roadtwin.ai.feature.profile

import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ExitToApp
import androidx.compose.material.icons.filled.*
import androidx.compose.material.icons.outlined.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadtwin.ai.RoadTwinApplication
import com.roadtwin.ai.core.components.RoadTwinBottomBar
import com.roadtwin.ai.core.components.RoadTwinCard
import com.roadtwin.ai.core.theme.*
import java.util.Locale

@Composable
fun ProfileScreen(
    onBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToBenchmarks: () -> Unit,
    onNavigateToAbout: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication

    val sessionsFlow = remember { app.reportsRepository.getCompletedSessions() }
    val sessions by sessionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    val totalPotholesCount = remember(sessions) { sessions.sumOf { it.totalPotholes } }
    val totalDistanceKm = remember(sessions) { sessions.sumOf { it.distanceKm } }
    val totalReportsCount = remember(sessions) { sessions.size }

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
                        text = "Profile",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                }
            }
        },
        bottomBar = {
            RoadTwinBottomBar(
                currentScreen = "profile",
                onHomeClick = onNavigateToDashboard,
                onReportsClick = onNavigateToReports,
                onMapClick = onNavigateToMap,
                onSettingsClick = onNavigateToSettings,
                onProfileClick = {}
            )
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 20.dp, bottom = 32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // 1. Operator Avatar & Name Card
            item {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Box(
                        modifier = Modifier
                            .size(88.dp)
                            .clip(CircleShape)
                            .background(RoadTwinBlue),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.Person,
                            contentDescription = "Avatar",
                            tint = Color.White,
                            modifier = Modifier.size(52.dp)
                        )
                    }

                    Spacer(modifier = Modifier.height(14.dp))

                    Text(
                        text = "Operator",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    Text(
                        text = "operator@roadtwin.ai",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextMediumGray
                    )

                    Spacer(modifier = Modifier.height(4.dp))

                    Text(
                        text = "Field Inspector",
                        style = MaterialTheme.typography.bodySmall,
                        color = RoadTwinBlue,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }

            // 2. 3 Statistics Cards in a Row (Reports, Potholes, Distance)
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ProfileStatCard(
                        count = "$totalReportsCount",
                        label = "Reports",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatCard(
                        count = "$totalPotholesCount",
                        label = "Potholes",
                        modifier = Modifier.weight(1f)
                    )
                    ProfileStatCard(
                        count = String.format(Locale.US, "%.0f km", totalDistanceKm),
                        label = "Distance",
                        modifier = Modifier.weight(1f)
                    )
                }
            }

            // 3. Menu List Card
            item {
                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column {
                        ProfileMenuRow(
                            icon = Icons.Outlined.Edit,
                            label = "Edit Profile",
                            onClick = { Toast.makeText(context, "Logged in as primary field operator", Toast.LENGTH_SHORT).show() }
                        )
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        ProfileMenuRow(
                            icon = Icons.Outlined.Info,
                            label = "App Information",
                            onClick = onNavigateToAbout
                        )
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        @Suppress("DEPRECATION")
                        ProfileMenuRow(
                            icon = Icons.Outlined.HelpOutline,
                            label = "Help & Support",
                            onClick = { Toast.makeText(context, "RoadTwin AI v1.0.0 edge road inspection system", Toast.LENGTH_SHORT).show() }
                        )
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        ProfileMenuRow(
                            icon = Icons.Outlined.Science,
                            label = "Model Benchmarks",
                            onClick = onNavigateToBenchmarks
                        )
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)

                        ProfileMenuRow(
                            icon = Icons.AutoMirrored.Filled.ExitToApp,
                            label = "About System",
                            onClick = onNavigateToAbout
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun ProfileStatCard(
    count: String,
    label: String,
    modifier: Modifier = Modifier
) {
    RoadTwinCard(
        modifier = modifier,
        cornerRadius = 14.dp
    ) {
        Column(
            modifier = Modifier
                .padding(vertical = 14.dp, horizontal = 8.dp)
                .fillMaxWidth(),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            Text(
                text = count,
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = TextNavy
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = label,
                style = MaterialTheme.typography.bodySmall,
                color = TextMediumGray,
                fontWeight = FontWeight.Medium
            )
        }
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
            tint = RoadTwinBlue,
            modifier = Modifier.size(22.dp)
        )

        Spacer(Modifier.width(16.dp))

        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = TextNavy,
            fontWeight = FontWeight.Medium,
            modifier = Modifier.weight(1f)
        )

        if (trailingTag != null) {
            Text(
                text = trailingTag,
                style = MaterialTheme.typography.labelSmall,
                color = TextMediumGray,
                fontSize = 11.sp
            )
            Spacer(Modifier.width(8.dp))
        }

        Icon(
            imageVector = Icons.Default.ChevronRight,
            contentDescription = null,
            tint = TextDisabled,
            modifier = Modifier.size(18.dp)
        )
    }
}
