package com.roadtwin.ai

import android.app.Application
import com.roadtwin.ai.data.local.RoadTwinDatabase
import com.roadtwin.ai.data.remote.FirebaseSyncManager
import com.roadtwin.ai.data.repository.DefaultReportsRepository
import com.roadtwin.ai.data.repository.ReportsRepository
import com.roadtwin.ai.data.sync.SyncWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class RoadTwinApplication : Application() {
    /** Process-lifetime scope for background tasks that outlive any single screen. */
    val applicationScope = CoroutineScope(SupervisorJob())

    lateinit var database: RoadTwinDatabase
        private set
    lateinit var reportsRepository: ReportsRepository
        private set
    lateinit var syncManager: FirebaseSyncManager
        private set

    override fun onCreate() {
        super.onCreate()
        database = RoadTwinDatabase.getDatabase(this)
        syncManager = FirebaseSyncManager(
            this,
            database.sessionDao(),
            database.detectionDao(),
            database.routePointDao()
        )
        reportsRepository = DefaultReportsRepository(
            this,
            database.sessionDao(),
            database.detectionDao(),
            database.routePointDao(),
            syncManager
        )

        // Clean up any abandoned active sessions from previous crashes/process kills
        applicationScope.launch {
            reportsRepository.cleanupAbandonedSessions()
        }

        // Schedule periodic background metadata synchronization with network constraints
        SyncWorker.schedulePeriodicSync(this)
    }
}
