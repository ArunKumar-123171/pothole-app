package com.roadtwin.ai.feature.reports

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import com.roadtwin.ai.data.repository.ReportsRepository
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch

data class ReportsUiState(
    val sessions: List<MonitoringSessionEntity> = emptyList(),
    val filteredSessions: List<MonitoringSessionEntity> = emptyList(),
    val selectedTab: String = "All",
    val isSyncing: Boolean = false
)

class ReportsViewModel(
    private val repository: ReportsRepository
) : ViewModel() {

    private val _selectedTab = MutableStateFlow("All")
    private val _isSyncing = MutableStateFlow(false)

    val uiState: StateFlow<ReportsUiState> = combine(
        repository.getAllSessions(),
        _selectedTab,
        _isSyncing
    ) { sessions, tab, syncing ->
        // Reports history strictly lists finalized COMPLETED sessions
        val completedSessions = sessions.filter { it.status == "COMPLETED" }
        val filtered = when (tab.lowercase()) {
            "all" -> completedSessions
            "pending" -> completedSessions.filter { it.syncStatus == "PENDING_UPLOAD" || it.syncStatus == "FAILED" }
            "synced" -> completedSessions.filter { it.syncStatus == "SYNCED" }
            else -> completedSessions
        }
        ReportsUiState(
            sessions = completedSessions,
            filteredSessions = filtered,
            selectedTab = tab,
            isSyncing = syncing
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = ReportsUiState()
    )

    fun setFilterTab(tab: String) {
        _selectedTab.value = tab
    }

    fun syncNow() {
        viewModelScope.launch {
            _isSyncing.value = true
            repository.syncPendingReports()
            _isSyncing.value = false
        }
    }
}
