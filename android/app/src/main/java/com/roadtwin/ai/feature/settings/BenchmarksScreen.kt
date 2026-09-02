package com.roadtwin.ai.feature.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.feature.dashboard.DashboardBottomBar
import com.roadtwin.ai.ml.model.ModelConfig

@Composable
fun BenchmarksScreen(
    onBack: () -> Unit,
    onNavigateToDashboard: () -> Unit,
    onNavigateToMap: () -> Unit,
    onNavigateToCamera: () -> Unit,
    onNavigateToReports: () -> Unit,
    onNavigateToSettings: () -> Unit,
    onNavigateToProfile: () -> Unit
) {
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
                        text = "Benchmarks",
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
                onSettingsClick = onNavigateToSettings,
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(28.dp)
        ) {
            // Section 1: Model Test Results (Research)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Model Test Results (Research)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkCharcoal
                    )

                    BenchmarkSpecRow("Precision", ModelConfig.BENCHMARK_PRECISION)
                    BenchmarkSpecRow("Recall", ModelConfig.BENCHMARK_RECALL)
                    BenchmarkSpecRow("mAP@50", ModelConfig.BENCHMARK_MAP_50)
                    BenchmarkSpecRow("mAP@50:95", ModelConfig.BENCHMARK_MAP_50_95)
                }
            }

            // Section 2: Mobile Performance (This Device)
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Mobile Performance (This Device)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkCharcoal
                    )

                    BenchmarkSpecRow("Average Latency", "Not measured")
                    BenchmarkSpecRow("FPS", "Not measured")
                }
            }

            // Section 3: Model Characteristics
            item {
                Column(verticalArrangement = Arrangement.spacedBy(14.dp)) {
                    Text(
                        text = "Model Characteristics",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkCharcoal
                    )

                    BenchmarkSpecRow("Parameters", "~ 2.375 M")
                    BenchmarkSpecRow("GFLOPs (416)", "~ 5.3")
                    BenchmarkSpecRow("Model Size", "~ 9.1 MB")
                }
            }
        }
    }
}

@Composable
fun BenchmarkSpecRow(label: String, value: String) {
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
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextDarkCharcoal,
            fontWeight = FontWeight.Bold
        )
    }
}
