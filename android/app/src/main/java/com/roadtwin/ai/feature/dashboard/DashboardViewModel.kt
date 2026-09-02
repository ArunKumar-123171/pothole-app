package com.roadtwin.ai.feature.dashboard

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadtwin.ai.data.local.DetectionEntity
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import com.roadtwin.ai.data.repository.ReportsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class DashboardUiState(
    val totalPotholes: Int = 0,
    val sessionsCount: Int = 0,
    val pendingUploads: Int = 0,
    val syncedReports: Int = 0,
    val highSeverityCount: Int = 0,
    val roadHealthScore: Int? = null, // null when no reports exist
    val recentSessions: List<MonitoringSessionEntity> = emptyList(),
    val recentDetections: List<DetectionEntity> = emptyList(),
    val isLoading: Boolean = false
)

class DashboardViewModel(
    private val repository: ReportsRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(DashboardUiState())
    val uiState: StateFlow<DashboardUiState> = combine(
        repository.getAllSessions(),
        repository.getAllDetections()
    ) { sessions, detections ->
        val totalPotholes = detections.size
        val pending = sessions.count { it.syncStatus == "PENDING_UPLOAD" || it.syncStatus == "FAILED" }
        val synced = sessions.count { it.syncStatus == "SYNCED" }
        val highCritical = detections.count { it.severity == "HIGH" || it.severity == "CRITICAL" }

        // Dynamic Road Health Calculation based on actual recorded potholes
        val healthScore = if (totalPotholes > 0) {
            val critical = detections.count { it.severity == "CRITICAL" }
            val high = detections.count { it.severity == "HIGH" }
            val medium = detections.count { it.severity == "MEDIUM" }
            val low = detections.count { it.severity == "LOW" }
            val deduction = (critical * 12) + (high * 8) + (medium * 4) + (low * 1)
            (100 - deduction).coerceIn(10, 100)
        } else {
            null // "No road health data available"
        }

        DashboardUiState(
            totalPotholes = totalPotholes,
            sessionsCount = sessions.size,
            pendingUploads = pending,
            syncedReports = synced,
            highSeverityCount = highCritical,
            roadHealthScore = healthScore,
            recentSessions = sessions.take(5),
            recentDetections = detections.take(5),
            isLoading = false
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = DashboardUiState()
    )

    init {
        // Sync pending metadata on launch if network is available
        viewModelScope.launch {
            repository.syncPendingReports()
        }
    }
}
