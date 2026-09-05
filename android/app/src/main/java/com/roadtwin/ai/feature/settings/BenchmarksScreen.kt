package com.roadtwin.ai.feature.settings

import androidx.compose.foundation.BorderStroke
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
import com.roadtwin.ai.core.components.RoadTwinBottomBar
import com.roadtwin.ai.core.components.RoadTwinCard
import com.roadtwin.ai.core.components.SectionHeader
import com.roadtwin.ai.core.theme.*
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
                        text = "Model Benchmarks",
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
            contentPadding = PaddingValues(top = 16.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Section 1: Model Test Results (Research)
            item {
                SectionHeader(title = "Model Test Results (Research)")
                Spacer(Modifier.height(4.dp))
                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BenchmarkSpecRow("Precision", ModelConfig.BENCHMARK_PRECISION)
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        BenchmarkSpecRow("Recall", ModelConfig.BENCHMARK_RECALL)
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        BenchmarkSpecRow("mAP@50", ModelConfig.BENCHMARK_MAP_50)
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        BenchmarkSpecRow("mAP@50:95", ModelConfig.BENCHMARK_MAP_50_95)
                    }
                }
            }

            // Section 2: Mobile Performance (This Device)
            item {
                SectionHeader(title = "Mobile Performance (This Device)")
                Spacer(Modifier.height(4.dp))
                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BenchmarkSpecRow("Average Latency", "12 ms (FP32)")
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        BenchmarkSpecRow("Inference FPS", "10-15 FPS")
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        BenchmarkSpecRow("Acceleration Delegate", "NNAPI / GPU")
                    }
                }
            }

            // Section 3: Model Characteristics
            item {
                SectionHeader(title = "Model Characteristics")
                Spacer(Modifier.height(4.dp))
                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        BenchmarkSpecRow("Architecture", "YOLO26n Single-Class")
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        BenchmarkSpecRow("Input Resolution", "416 x 416 x 3")
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        BenchmarkSpecRow("Parameters", "~ 2.375 M")
                        HorizontalDivider(color = CardBorderColor, thickness = 0.8.dp)
                        BenchmarkSpecRow("Model Size", "~ 9.1 MB")
                    }
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
            color = TextMediumGray,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = value,
            style = MaterialTheme.typography.bodyMedium,
            color = TextNavy,
            fontWeight = FontWeight.Bold
        )
    }
}
