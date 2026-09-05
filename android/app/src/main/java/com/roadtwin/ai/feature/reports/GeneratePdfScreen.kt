package com.roadtwin.ai.feature.reports

import android.content.Intent
import android.graphics.BitmapFactory
import android.widget.Toast
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import com.roadtwin.ai.core.pdf.PdfReportGenerator
import com.roadtwin.ai.core.theme.*
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.*

@Composable
fun GeneratePdfScreen(
    sessionId: String,
    onBack: () -> Unit
) {
    val context = LocalContext.current
    val app = context.applicationContext as RoadTwinApplication
    val scope = rememberCoroutineScope()

    val sessionFlow = remember(sessionId) { app.reportsRepository.getSessionById(sessionId) }
    val session by sessionFlow.collectAsStateWithLifecycle(initialValue = null)

    val detectionsFlow = remember(sessionId) { app.reportsRepository.getDetectionsForSession(sessionId) }
    val detections by detectionsFlow.collectAsStateWithLifecycle(initialValue = emptyList())

    var isGeneratingPdf by remember { mutableStateOf(false) }
    var isSyncing by remember { mutableStateOf(false) }

    val currentSession = session
    val firstDetection = detections.firstOrNull()

    Scaffold(
        topBar = {
            Surface(
                modifier = Modifier.fillMaxWidth(),
                color = BackgroundWhite,
                border = BorderStroke(1.dp, CardBorderColor)
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
                        text = "Generate PDF",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = TextNavy
                    )
                }
            }
        },
        containerColor = BackgroundLight
    ) { innerPadding ->
        if (currentSession == null) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center
            ) {
                CircularProgressIndicator(color = RoadTwinBlue)
            }
        } else {
            val dateSdf = remember { SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()) }
            val formattedDate = remember(currentSession.startTime) { dateSdf.format(Date(currentSession.startTime)) }

            val firstImageFile = firstDetection?.let { File(it.imagePath) }

            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .padding(horizontal = 20.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Spacer(Modifier.height(4.dp))

                // PDF Document Preview Card
                RoadTwinCard(
                    modifier = Modifier.fillMaxWidth(),
                    cornerRadius = 16.dp,
                    borderColor = CardBorderColor
                ) {
                    Column(
                        modifier = Modifier.padding(18.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        // Document Header
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(
                                imageVector = Icons.Default.Description,
                                contentDescription = null,
                                tint = RoadTwinBlue,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "RoadTwin AI",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = RoadTwinBlue
                            )
                        }
                        Text(
                            text = "Pothole Inspection Report",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextMediumGray,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            fontSize = 12.sp
                        )

                        HorizontalDivider(color = SurfaceVariantLight, thickness = 1.dp)

                        // Sample Document Preview Thumbnail
                        Box(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                                .clip(RoundedCornerShape(10.dp))
                                .background(SurfaceLight),
                            contentAlignment = Alignment.Center
                        ) {
                            if (firstImageFile != null && firstImageFile.exists()) {
                                val bitmap = remember(firstDetection.imagePath) {
                                    BitmapFactory.decodeFile(firstImageFile.absolutePath)
                                }
                                if (bitmap != null) {
                                    Image(
                                        bitmap = bitmap.asImageBitmap(),
                                        contentDescription = "Pothole Preview",
                                        modifier = Modifier.fillMaxSize(),
                                        contentScale = androidx.compose.ui.layout.ContentScale.Crop
                                    )
                                } else {
                                    Icon(Icons.Default.Image, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(40.dp))
                                }
                            } else {
                                Icon(Icons.Default.Image, contentDescription = null, tint = TextDisabled, modifier = Modifier.size(40.dp))
                            }
                        }

                        // Report Header Details
                        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(
                                text = currentSession.startAddress.ifBlank { "Road Inspection Segment" },
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Bold,
                                color = TextNavy
                            )
                            Text(
                                text = formattedDate,
                                style = MaterialTheme.typography.bodySmall,
                                color = TextMediumGray,
                                fontSize = 12.sp
                            )
                        }

                        HorizontalDivider(color = SurfaceVariantLight, thickness = 1.dp)

                        // Summary Statistics Table Preview
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            PdfPreviewRow("Total Potholes", "${currentSession.totalPotholes}")
                            PdfPreviewRow("Distance", String.format(Locale.US, "%.2f km", currentSession.distanceKm))
                            PdfPreviewRow("Critical", "${currentSession.criticalSeverityCount}")
                            PdfPreviewRow("High", "${currentSession.highSeverityCount}")
                            PdfPreviewRow("Medium", "${currentSession.mediumSeverityCount}")
                            PdfPreviewRow("Low", "${currentSession.lowSeverityCount}")
                        }

                        Spacer(Modifier.height(4.dp))
                        Text(
                            text = "Smarter Roads • Safer Journeys",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextDisabled,
                            modifier = Modifier.align(Alignment.CenterHorizontally),
                            fontSize = 10.sp
                        )
                    }
                }

                // Action Buttons
                RoadTwinButton(
                    text = if (isGeneratingPdf) "Generating PDF..." else "Share / Save PDF",
                    icon = Icons.Default.Share,
                    containerColor = RoadTwinBlue,
                    enabled = !isGeneratingPdf,
                    onClick = {
                        scope.launch {
                            isGeneratingPdf = true
                            try {
                                val path = app.reportsRepository.generateSessionPdf(sessionId)
                                val shareIntent = PdfReportGenerator.getSharePdfIntent(context, path)
                                context.startActivity(Intent.createChooser(shareIntent, "Share Report PDF"))
                            } catch (e: Exception) {
                                Toast.makeText(context, "Failed to generate PDF: ${e.message}", Toast.LENGTH_LONG).show()
                            } finally {
                                isGeneratingPdf = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                RoadTwinButton(
                    text = if (isSyncing) "Syncing to Firebase..." else "Send to Firebase",
                    icon = Icons.Default.CloudUpload,
                    containerColor = RoadTwinGreen,
                    enabled = !isSyncing,
                    onClick = {
                        scope.launch {
                            isSyncing = true
                            try {
                                app.reportsRepository.syncPendingReports()
                                Toast.makeText(context, "Session metadata synced with Firestore!", Toast.LENGTH_SHORT).show()
                            } catch (e: Exception) {
                                Toast.makeText(context, "Sync error: ${e.message}", Toast.LENGTH_SHORT).show()
                            } finally {
                                isSyncing = false
                            }
                        }
                    },
                    modifier = Modifier.fillMaxWidth()
                )

                Spacer(Modifier.height(20.dp))
            }
        }
    }
}

@Composable
private fun PdfPreviewRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(text = label, style = MaterialTheme.typography.bodySmall, color = TextMediumGray, fontSize = 12.sp)
        Text(text = value, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Bold, color = TextNavy, fontSize = 12.sp)
    }
}
