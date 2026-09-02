package com.roadtwin.ai.feature.reports

import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.roadtwin.ai.RoadTwinApplication
import com.roadtwin.ai.core.components.*
import com.roadtwin.ai.core.theme.*
import com.roadtwin.ai.data.local.DetectionEntity
import kotlinx.coroutines.launch
import java.io.File
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EditReportScreen(
    sessionId: String,
    onBack: () -> Unit,
    onSaved: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val scope = rememberCoroutineScope()

    val sessionFlow = remember(sessionId) { app.reportsRepository.getSessionById(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    val detectionsFlow = remember(sessionId) { app.reportsRepository.getDetectionsForSession(sessionId) }
    val initialDetections by detectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var title by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    var remarks by remember { mutableStateOf("") }

    val severityOverrides = remember { mutableStateMapOf<Int, String>() }
    val removedDetectionIds = remember { mutableStateListOf<Int>() }

    var isInitialized by remember { mutableStateOf(false) }

    LaunchedEffect(session) {
        val s = session
        if (s != null && !isInitialized) {
            title = s.title
            notes = s.notes
            remarks = s.remarks
            isInitialized = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Edit Report", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, color = TextDarkCharcoal) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TextDarkCharcoal)
                    }
                },
                actions = {
                    TextButton(
                        onClick = {
                            scope.launch {
                                app.reportsRepository.updateSessionReport(
                                    sessionId = sessionId,
                                    title = title,
                                    notes = notes,
                                    remarks = remarks,
                                    severityOverrides = severityOverrides.toMap(),
                                    removedDetectionIds = removedDetectionIds.toList()
                                )
                                Toast.makeText(context, "Report updated successfully", Toast.LENGTH_SHORT).show()
                                onSaved()
                            }
                        }
                    ) {
                        Text("SAVE", fontWeight = FontWeight.Bold, color = RoadTwinPrimary, fontSize = 16.sp)
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = BackgroundWhite)
            )
        },
        containerColor = BackgroundWhite
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 20.dp),
            contentPadding = PaddingValues(top = 16.dp, bottom = 40.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // General Details Card
            item {
                SectionHeader("Report Metadata")
                RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(16.dp)
                    ) {
                        OutlinedTextField(
                            value = title,
                            onValueChange = { title = it },
                            label = { Text("Report Title") },
                            modifier = Modifier.fillMaxWidth(),
                            singleLine = true,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RoadTwinPrimary,
                                focusedLabelColor = RoadTwinPrimary
                            )
                        )

                        OutlinedTextField(
                            value = notes,
                            onValueChange = { notes = it },
                            label = { Text("Inspector Notes") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RoadTwinPrimary,
                                focusedLabelColor = RoadTwinPrimary
                            )
                        )

                        OutlinedTextField(
                            value = remarks,
                            onValueChange = { remarks = it },
                            label = { Text("Action Remarks") },
                            modifier = Modifier.fillMaxWidth(),
                            minLines = 2,
                            colors = OutlinedTextFieldDefaults.colors(
                                focusedBorderColor = RoadTwinPrimary,
                                focusedLabelColor = RoadTwinPrimary
                            )
                        )
                    }
                }
            }

            // Detections List for Manual Modification / Removal
            item {
                SectionHeader("Pothole Detections (${initialDetections.size - removedDetectionIds.size})")
                Text(
                    text = "You can manually adjust severity or remove false positive detections before generating the final PDF.",
                    style = MaterialTheme.typography.bodySmall,
                    color = TextMediumGray
                )
            }

            val visibleDetections = initialDetections.filter { it.id !in removedDetectionIds }

            if (visibleDetections.isEmpty()) {
                item {
                    EmptyState(
                        message = "All detections removed from this report",
                        icon = Icons.Default.Info
                    )
                }
            } else {
                items(visibleDetections, key = { it.id }) { detection ->
                    EditableDetectionCard(
                        detection = detection,
                        currentSeverity = severityOverrides[detection.id] ?: detection.severity,
                        onSeverityChanged = { newSev ->
                            severityOverrides[detection.id] = newSev
                        },
                        onRemove = {
                            removedDetectionIds.add(detection.id)
                        }
                    )
                }
            }
        }
    }
}

@Composable
fun EditableDetectionCard(
    detection: DetectionEntity,
    currentSeverity: String,
    onSeverityChanged: (String) -> Unit,
    onRemove: () -> Unit
) {
    RoadTwinCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                val imageFile = File(detection.imagePath)
                Box(
                    modifier = Modifier
                        .size(64.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(SurfaceLight),
                    contentAlignment = Alignment.Center
                ) {
                    if (imageFile.exists()) {
                        val bitmap = remember(detection.imagePath) {
                            BitmapFactory.decodeFile(imageFile.absolutePath)
                        }
                        if (bitmap != null) {
                            Image(
                                bitmap = bitmap.asImageBitmap(),
                                contentDescription = null,
                                modifier = Modifier.fillMaxSize(),
                                contentScale = androidx.compose.ui.layout.ContentScale.Crop
                            )
                        } else {
                            Icon(Icons.Default.Image, contentDescription = null, tint = TextDisabled)
                        }
                    } else {
                        Icon(Icons.Default.Image, contentDescription = null, tint = TextDisabled)
                    }
                }

                Spacer(Modifier.width(12.dp))

                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "Detection #${detection.detectionId.ifBlank { detection.id.toString() }}",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = TextDarkCharcoal
                    )
                    Text(
                        text = "Confidence: ${(detection.confidence * 100).toInt()}%",
                        style = MaterialTheme.typography.bodySmall,
                        color = TextMediumGray
                    )
                    Text(
                        text = detection.address,
                        style = MaterialTheme.typography.labelSmall,
                        color = TextDisabled,
                        maxLines = 1
                    )
                }

                IconButton(onClick = onRemove) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = "Remove Detection", tint = SeverityHigh)
                }
            }

            Spacer(Modifier.height(12.dp))
            HorizontalDivider(color = SurfaceLight)
            Spacer(Modifier.height(8.dp))

            // Severity Selector
            Text(
                text = "Severity (Manual Override):",
                style = MaterialTheme.typography.labelMedium,
                fontWeight = FontWeight.Bold,
                color = TextDarkCharcoal
            )
            Spacer(Modifier.height(6.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                listOf("LOW", "MEDIUM", "HIGH", "CRITICAL").forEach { sev ->
                    val isSelected = currentSeverity == sev
                    FilterChip(
                        selected = isSelected,
                        onClick = { onSeverityChanged(sev) },
                        label = { Text(sev, fontSize = 11.sp, fontWeight = FontWeight.Bold) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = when(sev) {
                                "CRITICAL" -> SeverityCritical
                                "HIGH" -> SeverityHigh
                                "MEDIUM" -> SeverityMedium
                                else -> SeverityLow
                            },
                            selectedLabelColor = Color.White
                        )
                    )
                }
            }
        }
    }
}
