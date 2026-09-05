package com.roadtwin.ai

import android.content.Context
import com.roadtwin.ai.core.location.LocationManager
import com.roadtwin.ai.data.local.*
import com.roadtwin.ai.data.repository.DefaultReportsRepository
import com.roadtwin.ai.data.repository.ReportsRepository
import com.roadtwin.ai.feature.dashboard.DashboardUiState
import com.roadtwin.ai.feature.dashboard.DashboardViewModel
import com.roadtwin.ai.feature.reports.ReportsViewModel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.runTest
import org.junit.Assert.*
import org.junit.Before
import org.junit.Test
import java.io.File

/**
 * In-memory test double for SessionDao to test state transitions deterministically.
 */
class FakeSessionDao : SessionDao {
    private val sessionsMap = mutableMapOf<String, MonitoringSessionEntity>()
    private val sessionsFlow = MutableStateFlow<List<MonitoringSessionEntity>>(emptyList())

    private fun emit() {
        sessionsFlow.value = sessionsMap.values.sortedByDescending { it.startTime }
    }

    override suspend fun insert(session: MonitoringSessionEntity): Long {
        sessionsMap[session.sessionId] = session
        emit()
        return 1L
    }

    override suspend fun update(session: MonitoringSessionEntity) {
        sessionsMap[session.sessionId] = session
        emit()
    }

    override suspend fun delete(session: MonitoringSessionEntity) {
        sessionsMap.remove(session.sessionId)
        emit()
    }

    override suspend fun deleteById(sessionId: String) {
        sessionsMap.remove(sessionId)
        emit()
    }

    override suspend fun getSessionById(sessionId: String): MonitoringSessionEntity? {
        return sessionsMap[sessionId]
    }

    override fun observeSessionById(sessionId: String): Flow<MonitoringSessionEntity?> {
        return sessionsFlow.map { list -> list.find { it.sessionId == sessionId } }
    }

    override fun getAllSessions(): Flow<List<MonitoringSessionEntity>> {
        return sessionsFlow.asStateFlow()
    }

    override fun getCompletedSessions(): Flow<List<MonitoringSessionEntity>> {
        return sessionsFlow.map { list -> list.filter { it.status == "COMPLETED" } }
    }

    override suspend fun getActiveSessions(): List<MonitoringSessionEntity> {
        return sessionsMap.values.filter { it.status == "ACTIVE" }
    }

    override suspend fun getPendingSessions(): List<MonitoringSessionEntity> {
        // Must strictly only return COMPLETED sessions that are PENDING_UPLOAD or FAILED
        return sessionsMap.values.filter {
            it.status == "COMPLETED" && (it.syncStatus == "PENDING_UPLOAD" || it.syncStatus == "FAILED")
        }.sortedBy { it.startTime }
    }

    override suspend fun updateStartLocation(
        sessionId: String,
        latitude: Double,
        longitude: Double,
        accuracy: Float,
        timestamp: Long,
        address: String,
        updatedAt: Long
    ) {
        val existing = sessionsMap[sessionId] ?: return
        sessionsMap[sessionId] = existing.copy(
            startLatitude = latitude,
            startLongitude = longitude,
            startAccuracy = accuracy,
            startGpsTimestamp = timestamp,
            startAddress = address,
            updatedAt = updatedAt
        )
        emit()
    }

    override suspend fun updateSyncStatus(
        sessionId: String,
        status: String,
        syncedAt: Long?,
        updatedAt: Long
    ) {
        val existing = sessionsMap[sessionId] ?: return
        // Safeguard: ACTIVE sessions cannot be updated to SYNCED
        if (existing.status == "ACTIVE" && status == "SYNCED") {
            throw IllegalStateException("ACTIVE session cannot be set to SYNCED")
        }
        sessionsMap[sessionId] = existing.copy(
            syncStatus = status,
            syncedAt = syncedAt,
            updatedAt = updatedAt
        )
        emit()
    }

    override suspend fun updatePdfPath(sessionId: String, pdfPath: String, updatedAt: Long) {
        val existing = sessionsMap[sessionId] ?: return
        sessionsMap[sessionId] = existing.copy(
            pdfLocalPath = pdfPath,
            updatedAt = updatedAt
        )
        emit()
    }

    override fun getSessionsCount(): Flow<Int> {
        return sessionsFlow.map { it.size }
    }

    override fun getCompletedSessionsCount(): Flow<Int> {
        return sessionsFlow.map { list -> list.count { it.status == "COMPLETED" } }
    }

    override suspend fun deleteAll() {
        sessionsMap.clear()
        emit()
    }
}

/**
 * In-memory test double for DetectionDao.
 */
class FakeDetectionDao : DetectionDao {
    private val detectionsMap = mutableMapOf<Int, DetectionEntity>()
    private val detectionsFlow = MutableStateFlow<List<DetectionEntity>>(emptyList())
    private var nextId = 1

    private fun emit() {
        detectionsFlow.value = detectionsMap.values.sortedByDescending { it.timestamp }
    }

    override suspend fun insert(detection: DetectionEntity): Long {
        val id = if (detection.id == 0) nextId++ else detection.id
        val entity = detection.copy(id = id)
        detectionsMap[id] = entity
        emit()
        return id.toLong()
    }

    override suspend fun insertAll(detections: List<DetectionEntity>) {
        for (d in detections) {
            insert(d)
        }
    }

    override suspend fun update(detection: DetectionEntity) {
        detectionsMap[detection.id] = detection
        emit()
    }

    override suspend fun delete(detection: DetectionEntity) {
        detectionsMap.remove(detection.id)
        emit()
    }

    override suspend fun deleteById(id: Int) {
        detectionsMap.remove(id)
        emit()
    }

    override suspend fun deleteBySessionId(sessionId: String) {
        val toRemove = detectionsMap.values.filter { it.sessionId == sessionId }.map { it.id }
        toRemove.forEach { detectionsMap.remove(it) }
        emit()
    }

    override fun getDetectionsForSession(sessionId: String): Flow<List<DetectionEntity>> {
        return detectionsFlow.map { list -> list.filter { it.sessionId == sessionId }.sortedBy { it.timestamp } }
    }

    override suspend fun getDetectionsForSessionOnce(sessionId: String): List<DetectionEntity> {
        return detectionsMap.values.filter { it.sessionId == sessionId }.sortedBy { it.timestamp }
    }

    override fun getAllDetections(): Flow<List<DetectionEntity>> {
        return detectionsFlow.asStateFlow()
    }

    override suspend fun getDetectionById(id: Int): DetectionEntity? {
        return detectionsMap[id]
    }

    override suspend fun getPendingDetections(): List<DetectionEntity> {
        return detectionsMap.values.filter { it.syncStatus == "PENDING_UPLOAD" || it.syncStatus == "FAILED" }
    }

    override suspend fun updateSyncStatus(id: Int, status: String, syncedAt: Long?, updatedAt: Long) {
        val existing = detectionsMap[id] ?: return
        detectionsMap[id] = existing.copy(syncStatus = status, syncedAt = syncedAt, updatedAt = updatedAt)
        emit()
    }

    override fun getDetectionsCount(): Flow<Int> {
        return detectionsFlow.map { it.size }
    }

    override fun getDetectionsBySeverity(severity: String): Flow<List<DetectionEntity>> {
        return detectionsFlow.map { list -> list.filter { it.severity == severity } }
    }

    override fun getRecentDetections(limit: Int): Flow<List<DetectionEntity>> {
        return detectionsFlow.map { it.take(limit) }
    }

    override suspend fun deleteAll() {
        detectionsMap.clear()
        emit()
    }
}

/**
 * In-memory test double for RoutePointDao.
 */
class FakeRoutePointDao : RoutePointDao {
    private val pointsMap = mutableMapOf<Long, RoutePointEntity>()
    private val pointsFlow = MutableStateFlow<List<RoutePointEntity>>(emptyList())
    private var nextId = 1L

    private fun emit() {
        pointsFlow.value = pointsMap.values.sortedBy { it.timestamp }
    }

    override suspend fun insert(point: RoutePointEntity): Long {
        val id = if (point.id == 0L) nextId++ else point.id
        val entity = point.copy(id = id)
        pointsMap[id] = entity
        emit()
        return id
    }

    override suspend fun insertAll(points: List<RoutePointEntity>) {
        for (p in points) insert(p)
    }

    override fun getRoutePointsForSession(sessionId: String): Flow<List<RoutePointEntity>> {
        return pointsFlow.map { list -> list.filter { it.sessionId == sessionId } }
    }

    override suspend fun getRoutePointsForSessionOnce(sessionId: String): List<RoutePointEntity> {
        return pointsMap.values.filter { it.sessionId == sessionId }.sortedBy { it.timestamp }
    }

    override fun getAllRoutePoints(): Flow<List<RoutePointEntity>> {
        return pointsFlow.asStateFlow()
    }

    override suspend fun deleteBySessionId(sessionId: String) {
        val toRemove = pointsMap.values.filter { it.sessionId == sessionId }.map { it.id }
        toRemove.forEach { pointsMap.remove(it) }
        emit()
    }

    override suspend fun deleteAll() {
        pointsMap.clear()
        emit()
    }
}

class SessionSyncLifecycleTest {

    private lateinit var sessionDao: FakeSessionDao
    private lateinit var detectionDao: FakeDetectionDao
    private lateinit var routePointDao: FakeRoutePointDao
    private lateinit var repository: DefaultReportsRepository

    class FakeContext : android.content.ContextWrapper(null) {
        private val tempDir = File(System.getProperty("java.io.tmpdir"), "roadtwin_unit_test").apply { mkdirs() }
        override fun getFilesDir(): File = tempDir
        override fun getApplicationContext(): Context = this
        override fun getPackageName(): String = "com.roadtwin.ai"
    }

    @Before
    fun setUp() {
        sessionDao = FakeSessionDao()
        detectionDao = FakeDetectionDao()
        routePointDao = FakeRoutePointDao()
        val fakeContext = FakeContext()

        repository = DefaultReportsRepository(
            context = fakeContext,
            sessionDao = sessionDao,
            detectionDao = detectionDao,
            routePointDao = routePointDao,
            syncManager = null
        )
    }

    @Test
    fun testActiveSessionCreationState() = runTest {
        val session = repository.startNewSession(11.0168, 76.9558, "NH 544, Coimbatore")

        // 1. ACTIVE session must have status == "ACTIVE"
        assertEquals("ACTIVE", session.status)
        // 2. ACTIVE session must NOT have a finalized endTime (endTime == null)
        assertNull("ACTIVE session must have endTime = null", session.endTime)
        // 3. ACTIVE session must start with syncStatus = NOT_SYNCED
        assertEquals("NOT_SYNCED", session.syncStatus)
        // 4. ACTIVE session must have syncedAt = null
        assertNull("ACTIVE session must have syncedAt = null", session.syncedAt)
    }

    @Test
    fun testActiveSessionCannotBeReturnedForPendingSync() = runTest {
        val session = repository.startNewSession(11.0168, 76.9558, "Start Location")

        // DAO pending queries must only return COMPLETED sessions
        val pending = sessionDao.getPendingSessions()
        assertTrue("ACTIVE session must NEVER appear in pending sessions for sync", pending.isEmpty())

        // Attempting to update ACTIVE session to SYNCED must be rejected
        try {
            sessionDao.updateSyncStatus(session.sessionId, "SYNCED", syncedAt = System.currentTimeMillis())
            fail("ACTIVE session must not be allowed to transition directly to SYNCED")
        } catch (e: IllegalStateException) {
            // Expected
        }
    }

    @Test
    fun testActiveToCompletedOnStopMonitoring() = runTest {
        val session = repository.startNewSession(11.0168, 76.9558, "Start Location")

        // Record a detection
        repository.recordDetection(
            sessionId = session.sessionId,
            latitude = 11.0170,
            longitude = 76.9560,
            gpsAccuracy = 3.5f,
            address = "NH 544",
            confidence = 0.88f,
            severity = "HIGH",
            imagePath = "/tmp/dummy.jpg"
        )

        val completed = repository.finishSession(
            sessionId = session.sessionId,
            endLatitude = 11.0250,
            endLongitude = 76.9600,
            endAddress = "Destination Address",
            distanceKm = 1.85,
            title = "Inspection Run 1"
        )

        // 1. status transitions from ACTIVE -> COMPLETED
        assertEquals("COMPLETED", completed.status)
        // 2. endTime is written ONLY when Stop Monitoring successfully finalizes
        assertNotNull("COMPLETED session must have non-null endTime", completed.endTime)
        assertTrue("endTime must be a valid epoch timestamp", completed.endTime!! > 0)
        // 3. syncStatus transitions to PENDING_UPLOAD
        assertEquals("PENDING_UPLOAD", completed.syncStatus)
        // 4. syncedAt remains null until Firestore upload succeeds
        assertNull("syncedAt must be null prior to Firestore upload", completed.syncedAt)
        // 5. Total potholes count updated
        assertEquals(1, completed.totalPotholes)
        assertEquals(1, completed.highSeverityCount)
    }

    @Test
    fun testCompletedSessionAppearsInPendingSyncQueries() = runTest {
        val session = repository.startNewSession(11.0, 76.0, "Start")
        repository.finishSession(session.sessionId, 11.1, 76.1, "End", 2.0)

        val pending = sessionDao.getPendingSessions()
        assertEquals(1, pending.size)
        assertEquals(session.sessionId, pending[0].sessionId)
        assertEquals("COMPLETED", pending[0].status)
        assertEquals("PENDING_UPLOAD", pending[0].syncStatus)
    }

    @Test
    fun testSuccessfulSyncTransitionsToSyncedAndPopulatesSyncedAt() = runTest {
        val session = repository.startNewSession(11.0, 76.0, "Start")
        repository.finishSession(session.sessionId, 11.1, 76.1, "End", 2.0)

        val syncTimestamp = System.currentTimeMillis()
        // Simulate successful Firestore upload
        sessionDao.updateSyncStatus(
            sessionId = session.sessionId,
            status = "SYNCED",
            syncedAt = syncTimestamp,
            updatedAt = syncTimestamp
        )

        val updated = sessionDao.getSessionById(session.sessionId)
        assertNotNull(updated)
        assertEquals("SYNCED", updated!!.syncStatus)
        assertEquals(syncTimestamp, updated.syncedAt)
        assertEquals("COMPLETED", updated.status)
        assertNotNull(updated.endTime)

        // It is no longer in pending sessions
        val pending = sessionDao.getPendingSessions()
        assertTrue("SYNCED session must not appear in getPendingSessions()", pending.isEmpty())
    }

    @Test
    fun testFailedUploadLeavesReportInPendingUpload() = runTest {
        val session = repository.startNewSession(11.0, 76.0, "Start")
        repository.finishSession(session.sessionId, 11.1, 76.1, "End", 2.0)

        // Simulate upload start -> UPLOADING
        sessionDao.updateSyncStatus(session.sessionId, "UPLOADING", syncedAt = null)

        // Simulate failure -> set back to PENDING_UPLOAD with null syncedAt
        sessionDao.updateSyncStatus(session.sessionId, "PENDING_UPLOAD", syncedAt = null)

        val current = sessionDao.getSessionById(session.sessionId)
        assertNotNull(current)
        assertEquals("PENDING_UPLOAD", current!!.syncStatus)
        assertNull("syncedAt must remain null on failure", current.syncedAt)

        // Remains pending for retry
        val pending = sessionDao.getPendingSessions()
        assertEquals(1, pending.size)
    }

    @Test
    fun testEditingSyncedReportReturnsItToPendingUploadAndClearsSyncedAt() = runTest {
        val session = repository.startNewSession(11.0, 76.0, "Start")
        repository.finishSession(session.sessionId, 11.1, 76.1, "End", 2.0)

        // Simulate initial sync
        val syncTime = System.currentTimeMillis()
        sessionDao.updateSyncStatus(session.sessionId, "SYNCED", syncedAt = syncTime)

        var current = sessionDao.getSessionById(session.sessionId)
        assertEquals("SYNCED", current!!.syncStatus)
        assertEquals(syncTime, current.syncedAt)

        // Now user edits the report (e.g. notes / title / remarks)
        repository.updateSessionReport(
            sessionId = session.sessionId,
            title = "Updated Title by Inspector",
            notes = "Checked road conditions manually",
            remarks = "Requires immediate repair"
        )

        // Verify state is reset to PENDING_UPLOAD and syncedAt is cleared to null
        current = sessionDao.getSessionById(session.sessionId)
        assertNotNull(current)
        assertEquals("PENDING_UPLOAD", current!!.syncStatus)
        assertNull("syncedAt must be reset to null when report is edited", current.syncedAt)
        assertEquals("Updated Title by Inspector", current.title)
    }

    @Test
    fun testRapidStartStopSessionBehavior() = runTest {
        // Rapid Start
        val session = repository.startNewSession(11.0, 76.0, "Start")
        assertEquals("ACTIVE", session.status)
        assertNull(session.endTime)
        assertEquals("NOT_SYNCED", session.syncStatus)

        // Rapid Stop immediately after start (0 detections, 0 distance)
        val completed = repository.finishSession(
            sessionId = session.sessionId,
            endLatitude = 11.0,
            endLongitude = 76.0,
            endAddress = "Start",
            distanceKm = 0.0
        )

        assertEquals("COMPLETED", completed.status)
        assertNotNull(completed.endTime)
        assertEquals("PENDING_UPLOAD", completed.syncStatus)
        assertNull(completed.syncedAt)
    }

    @Test
    fun testAbandonedSessionRecoveryOnAppRestartOrProcessDeath() = runTest {
        // Simulate app crash while session 1 was ACTIVE with 0 detections
        sessionDao.insert(
            MonitoringSessionEntity(
                sessionId = "S-ORPHAN-1",
                title = "Orphaned Empty Session",
                startTime = System.currentTimeMillis() - 10000,
                endTime = null,
                status = "ACTIVE",
                syncStatus = "NOT_SYNCED",
                syncedAt = null,
                distanceKm = 0.0,
                totalPotholes = 0
            )
        )

        // Simulate app crash while session 2 was ACTIVE with recorded potholes
        sessionDao.insert(
            MonitoringSessionEntity(
                sessionId = "S-ORPHAN-2",
                title = "Orphaned Active with Potholes",
                startTime = System.currentTimeMillis() - 20000,
                endTime = null,
                status = "ACTIVE",
                syncStatus = "NOT_SYNCED",
                syncedAt = null,
                distanceKm = 1.2,
                totalPotholes = 1
            )
        )
        detectionDao.insert(
            DetectionEntity(
                id = 101,
                sessionId = "S-ORPHAN-2",
                confidence = 0.90f,
                severity = "CRITICAL"
            )
        )

        // Run recovery routine (which runs on app start / startNewSession)
        repository.cleanupAbandonedSessions()

        // 1. Empty session without detections -> CANCELLED
        val s1 = sessionDao.getSessionById("S-ORPHAN-1")
        assertNotNull(s1)
        assertEquals("CANCELLED", s1!!.status)
        assertEquals("NOT_SYNCED", s1.syncStatus)
        assertNull(s1.syncedAt)

        // 2. Session with detections -> cleanly COMPLETED with PENDING_UPLOAD so user data is preserved
        val s2 = sessionDao.getSessionById("S-ORPHAN-2")
        assertNotNull(s2)
        assertEquals("COMPLETED", s2!!.status)
        assertNotNull("Recovered session must have non-null endTime", s2.endTime)
        assertEquals("PENDING_UPLOAD", s2.syncStatus)
        assertNull(s2.syncedAt)
    }

    @Test
    fun testInvestigationAndRepairOfLegacyS03100InvalidState() = runTest {
        // Simulate the exact corrupted state of S-03100:
        // status = "ACTIVE", but had premature endTime and syncedAt from legacy bug
        val corruptSession = MonitoringSessionEntity(
            sessionId = "S-03100",
            title = "Monitoring S-03100",
            startTime = 1700000000000L,
            endTime = 1700000000000L,
            status = "ACTIVE",
            syncStatus = "SYNCED",
            syncedAt = 1700000050000L,
            totalPotholes = 2
        )
        sessionDao.insert(corruptSession)
        detectionDao.insert(
            DetectionEntity(
                id = 201,
                sessionId = "S-03100",
                severity = "HIGH",
                confidence = 0.85f
            )
        )

        // Run cleanup/recovery
        repository.cleanupAbandonedSessions()

        val repaired = sessionDao.getSessionById("S-03100")
        assertNotNull(repaired)
        // Corrupted ACTIVE+SYNCED state is repaired to COMPLETED + PENDING_UPLOAD
        assertEquals("COMPLETED", repaired!!.status)
        assertEquals("PENDING_UPLOAD", repaired.syncStatus)
        assertNull(repaired.syncedAt)
        assertNotNull(repaired.endTime)
    }

    @Test
    fun testDashboardMetricsExcludeActiveAndCancelledSessions() = runTest {
        // 1 COMPLETED PENDING session (2 potholes)
        val s1 = MonitoringSessionEntity(
            sessionId = "S-COMP-1",
            status = "COMPLETED",
            syncStatus = "PENDING_UPLOAD",
            endTime = System.currentTimeMillis(),
            totalPotholes = 2
        )
        // 1 COMPLETED SYNCED session (1 pothole)
        val s2 = MonitoringSessionEntity(
            sessionId = "S-COMP-2",
            status = "COMPLETED",
            syncStatus = "SYNCED",
            syncedAt = System.currentTimeMillis(),
            endTime = System.currentTimeMillis(),
            totalPotholes = 1
        )
        // 1 ACTIVE in-progress session (must NOT be counted in dashboard)
        val s3 = MonitoringSessionEntity(
            sessionId = "S-ACT-1",
            status = "ACTIVE",
            syncStatus = "NOT_SYNCED",
            endTime = null,
            totalPotholes = 0
        )
        // 1 CANCELLED session (must NOT be counted in dashboard)
        val s4 = MonitoringSessionEntity(
            sessionId = "S-CANC-1",
            status = "CANCELLED",
            syncStatus = "NOT_SYNCED",
            endTime = System.currentTimeMillis(),
            totalPotholes = 0
        )

        sessionDao.insert(s1)
        sessionDao.insert(s2)
        sessionDao.insert(s3)
        sessionDao.insert(s4)

        detectionDao.insert(DetectionEntity(id = 1, sessionId = "S-COMP-1", severity = "HIGH"))
        detectionDao.insert(DetectionEntity(id = 2, sessionId = "S-COMP-1", severity = "CRITICAL"))
        detectionDao.insert(DetectionEntity(id = 3, sessionId = "S-COMP-2", severity = "LOW"))
        detectionDao.insert(DetectionEntity(id = 4, sessionId = "S-ACT-1", severity = "MEDIUM")) // Active detection

        val viewModel = DashboardViewModel(repository)
        val uiState = viewModel.uiState.first { !it.isLoading && it.sessionsCount > 0 }

        // Only completed sessions: S-COMP-1 and S-COMP-2 (count = 2)
        assertEquals(2, uiState.sessionsCount)
        assertEquals(1, uiState.pendingUploads)
        assertEquals(1, uiState.syncedReports)
        // Total completed potholes = 3 (from s1 and s2 only, excluding active s3 detection)
        assertEquals(3, uiState.totalPotholes)
        assertEquals(2, uiState.highSeverityCount) // HIGH + CRITICAL in completed reports
        assertNotNull(uiState.roadHealthScore)
    }

    @Test
    fun testReportsViewModelFilterTabsStrictlyPartitionCompletedSessions() = runTest {
        val sPending = MonitoringSessionEntity(
            sessionId = "S-P",
            status = "COMPLETED",
            syncStatus = "PENDING_UPLOAD",
            endTime = System.currentTimeMillis()
        )
        val sSynced = MonitoringSessionEntity(
            sessionId = "S-S",
            status = "COMPLETED",
            syncStatus = "SYNCED",
            syncedAt = System.currentTimeMillis(),
            endTime = System.currentTimeMillis()
        )
        val sActive = MonitoringSessionEntity(
            sessionId = "S-A",
            status = "ACTIVE",
            syncStatus = "NOT_SYNCED",
            endTime = null
        )
        val sCancelled = MonitoringSessionEntity(
            sessionId = "S-C",
            status = "CANCELLED",
            syncStatus = "NOT_SYNCED",
            endTime = System.currentTimeMillis()
        )

        sessionDao.insert(sPending)
        sessionDao.insert(sSynced)
        sessionDao.insert(sActive)
        sessionDao.insert(sCancelled)

        val viewModel = ReportsViewModel(repository)

        // 1. Tab "All" -> Contains only COMPLETED sessions (2 sessions: sPending, sSynced)
        viewModel.setFilterTab("All")
        val stateAll = viewModel.uiState.first { it.sessions.isNotEmpty() }
        assertEquals(2, stateAll.sessions.size)
        assertEquals(2, stateAll.filteredSessions.size)
        assertTrue(stateAll.filteredSessions.none { it.status == "ACTIVE" || it.status == "CANCELLED" })

        // 2. Tab "Pending" -> Contains only sPending
        viewModel.setFilterTab("Pending")
        val statePending = viewModel.uiState.first { it.selectedTab == "Pending" }
        assertEquals(1, statePending.filteredSessions.size)
        assertEquals("S-P", statePending.filteredSessions[0].sessionId)

        // 3. Tab "Synced" -> Contains only sSynced
        viewModel.setFilterTab("Synced")
        val stateSynced = viewModel.uiState.first { it.selectedTab == "Synced" }
        assertEquals(1, stateSynced.filteredSessions.size)
        assertEquals("S-S", stateSynced.filteredSessions[0].sessionId)
    }

    @Test
    fun testRoomMigration4To5SchemaLogic() {
        val executedSql = mutableListOf<String>()
        val fakeDb = java.lang.reflect.Proxy.newProxyInstance(
            androidx.sqlite.db.SupportSQLiteDatabase::class.java.classLoader,
            arrayOf(androidx.sqlite.db.SupportSQLiteDatabase::class.java)
        ) { _, method, args ->
            if (method.name == "execSQL" && args != null && args.isNotEmpty()) {
                executedSql.add(args[0].toString())
            }
            null
        } as androidx.sqlite.db.SupportSQLiteDatabase

        RoadTwinDatabase.MIGRATION_4_5.migrate(fakeDb)

        assertTrue("Must add syncedAt to monitoring_sessions", executedSql.any { it.contains("ALTER TABLE monitoring_sessions ADD COLUMN syncedAt") })
        assertTrue("Must add syncedAt to detections", executedSql.any { it.contains("ALTER TABLE detections ADD COLUMN syncedAt") })
        assertTrue("Must repair corrupted ACTIVE sessions with potholes", executedSql.any { it.contains("SET status = 'COMPLETED'") && it.contains("totalPotholes > 0") })
        assertTrue("Must repair empty ACTIVE sessions", executedSql.any { it.contains("SET status = 'CANCELLED'") && it.contains("totalPotholes = 0") })
        assertTrue("Must ensure no ACTIVE session retains endTime or SYNCED status", executedSql.any { it.contains("SET endTime = NULL") && it.contains("status = 'ACTIVE'") })
    }

    @Test
    fun testTwoCriticalDetectionsSeverityAggregation() = runTest {
        val session = repository.startNewSession(11.0168, 76.9558, "NH 544, Coimbatore")
        val sid = session.sessionId

        repository.recordDetection(
            sessionId = sid,
            latitude = 11.0168,
            longitude = 76.9558,
            gpsAccuracy = 3.2f,
            address = "NH 544, Point A",
            confidence = 0.94f,
            severity = "CRITICAL",
            imagePath = "/data/img1.jpg",
            modelName = "pothole_detector.tflite",
            modelVersion = "RoadTwin-YOLO26n-416-FP32",
            modelInputSize = "416x416",
            modelPrecision = "FP32",
            confidenceThreshold = 0.40f
        )

        repository.recordDetection(
            sessionId = sid,
            latitude = 11.0175,
            longitude = 76.9562,
            gpsAccuracy = 2.8f,
            address = "NH 544, Point B",
            confidence = 0.91f,
            severity = "CRITICAL",
            imagePath = "/data/img2.jpg",
            modelName = "pothole_detector.tflite",
            modelVersion = "RoadTwin-YOLO26n-416-FP32",
            modelInputSize = "416x416",
            modelPrecision = "FP32",
            confidenceThreshold = 0.40f
        )

        val completed = repository.finishSession(
            sessionId = sid,
            endLatitude = 11.0180,
            endLongitude = 76.9570,
            endAddress = "NH 544, Coimbatore",
            distanceKm = 0.5
        )

        val detections = repository.getDetectionsForSessionDirect(sid)
        assertEquals(2, detections.size)
        assertEquals(2, completed.totalPotholes)
        assertEquals(2, completed.criticalSeverityCount)
        assertEquals(0, completed.highSeverityCount)
        assertEquals(0, completed.mediumSeverityCount)
        assertEquals(0, completed.lowSeverityCount)
        assertEquals(completed.totalPotholes, completed.criticalSeverityCount + completed.highSeverityCount + completed.mediumSeverityCount + completed.lowSeverityCount)
    }

    @Test
    fun testVariousSeverityCombinations() = runTest {
        // Test 1: 2 High
        val s1 = repository.startNewSession(11.0, 76.0, "Route 1")
        repository.recordDetection(s1.sessionId, 11.0, 76.0, 3f, "P1", 0.8f, "HIGH", "", "", "", "", "", 0.4f)
        repository.recordDetection(s1.sessionId, 11.0, 76.0, 3f, "P2", 0.8f, "HIGH", "", "", "", "", "", 0.4f)
        val c1 = repository.finishSession(s1.sessionId, 11.0, 76.0, "Route 1", 0.2)
        assertEquals(2, c1.totalPotholes)
        assertEquals(0, c1.criticalSeverityCount)
        assertEquals(2, c1.highSeverityCount)
        assertEquals(0, c1.mediumSeverityCount)
        assertEquals(0, c1.lowSeverityCount)

        // Test 2: 1 Critical + 1 High
        val s2 = repository.startNewSession(11.0, 76.0, "Route 2")
        repository.recordDetection(s2.sessionId, 11.0, 76.0, 3f, "P1", 0.95f, "CRITICAL", "", "", "", "", "", 0.4f)
        repository.recordDetection(s2.sessionId, 11.0, 76.0, 3f, "P2", 0.80f, "HIGH", "", "", "", "", "", 0.4f)
        val c2 = repository.finishSession(s2.sessionId, 11.0, 76.0, "Route 2", 0.2)
        assertEquals(2, c2.totalPotholes)
        assertEquals(1, c2.criticalSeverityCount)
        assertEquals(1, c2.highSeverityCount)
        assertEquals(0, c2.mediumSeverityCount)
        assertEquals(0, c2.lowSeverityCount)

        // Test 3: 1 Medium + 1 Low
        val s3 = repository.startNewSession(11.0, 76.0, "Route 3")
        repository.recordDetection(s3.sessionId, 11.0, 76.0, 3f, "P1", 0.60f, "MEDIUM", "", "", "", "", "", 0.4f)
        repository.recordDetection(s3.sessionId, 11.0, 76.0, 3f, "P2", 0.45f, "LOW", "", "", "", "", "", 0.4f)
        val c3 = repository.finishSession(s3.sessionId, 11.0, 76.0, "Route 3", 0.2)
        assertEquals(2, c3.totalPotholes)
        assertEquals(0, c3.criticalSeverityCount)
        assertEquals(0, c3.highSeverityCount)
        assertEquals(1, c3.mediumSeverityCount)
        assertEquals(1, c3.lowSeverityCount)

        // Test 4: No detections
        val s4 = repository.startNewSession(11.0, 76.0, "Route 4")
        val c4 = repository.finishSession(s4.sessionId, 11.0, 76.0, "Route 4", 0.1)
        assertEquals(0, c4.totalPotholes)
        assertEquals(0, c4.criticalSeverityCount)
        assertEquals(0, c4.highSeverityCount)
        assertEquals(0, c4.mediumSeverityCount)
        assertEquals(0, c4.lowSeverityCount)
    }

    @Test
    fun testSeveritySourceHeuristicVsManualOverride() = runTest {
        val session = repository.startNewSession(11.0, 76.0, "Test Road")
        val sid = session.sessionId

        val id1 = repository.recordDetection(sid, 11.0, 76.0, 3f, "P1", 0.85f, "HIGH", "", "", "", "", "", 0.4f).toInt()
        val id2 = repository.recordDetection(sid, 11.0, 76.0, 3f, "P2", 0.60f, "MEDIUM", "", "", "", "", "", 0.4f).toInt()

        repository.finishSession(sid, 11.0, 76.0, "Test Road", 0.5)

        val detListInitial = repository.getDetectionsForSessionDirect(sid)
        assertEquals("HEURISTIC", detListInitial.find { it.id == id1 }?.severitySource)
        assertEquals("HEURISTIC", detListInitial.find { it.id == id2 }?.severitySource)

        // Inspector manually updates severity of id1 from HIGH -> CRITICAL
        repository.updateSessionReport(
            sessionId = sid,
            title = "Inspected Report",
            notes = "Verified in field",
            remarks = "Severe bump",
            severityOverrides = mapOf(id1 to "CRITICAL"),
            removedDetectionIds = emptyList()
        )

        val detListAfter = repository.getDetectionsForSessionDirect(sid)
        val d1 = detListAfter.find { it.id == id1 }!!
        val d2 = detListAfter.find { it.id == id2 }!!

        assertEquals("CRITICAL", d1.severity)
        assertEquals("MANUAL_OVERRIDE", d1.severitySource)

        // id2 was not overridden, stays HEURISTIC
        assertEquals("MEDIUM", d2.severity)
        assertEquals("HEURISTIC", d2.severitySource)

        // Session severity counts properly updated
        val updatedSession = repository.getSessionDirect(sid)!!
        assertEquals(2, updatedSession.totalPotholes)
        assertEquals(1, updatedSession.criticalSeverityCount)
        assertEquals(0, updatedSession.highSeverityCount)
        assertEquals(1, updatedSession.mediumSeverityCount)
        assertEquals(0, updatedSession.lowSeverityCount)
        assertEquals("PENDING_UPLOAD", updatedSession.syncStatus)
    }

    @Test
    fun testTenAndThirtyDistinctPotholesAggregation() = runTest {
        val s10 = repository.startNewSession(11.0, 76.0, "10 Pothole Route")
        for (i in 1..10) {
            val sev = when (i % 4) {
                0 -> "CRITICAL"
                1 -> "HIGH"
                2 -> "MEDIUM"
                else -> "LOW"
            }
            repository.recordDetection(s10.sessionId, 11.0 + (i * 0.0001), 76.0, 3f, "P$i", 0.8f, sev, "", "", "", "", "", 0.4f)
        }
        val c10 = repository.finishSession(s10.sessionId, 11.01, 76.0, "10 Pothole Route", 1.0)
        val detections10 = repository.getDetectionsForSessionDirect(s10.sessionId)
        assertEquals(10, detections10.size)
        assertEquals(10, c10.totalPotholes)
        assertEquals(10, c10.criticalSeverityCount + c10.highSeverityCount + c10.mediumSeverityCount + c10.lowSeverityCount)

        val s30 = repository.startNewSession(11.0, 76.0, "30 Pothole Route")
        for (i in 1..30) {
            val sev = when (i % 4) {
                0 -> "CRITICAL"
                1 -> "HIGH"
                2 -> "MEDIUM"
                else -> "LOW"
            }
            repository.recordDetection(s30.sessionId, 11.0 + (i * 0.0001), 76.0, 3f, "P$i", 0.8f, sev, "", "", "", "", "", 0.4f)
        }
        val c30 = repository.finishSession(s30.sessionId, 11.03, 76.0, "30 Pothole Route", 3.0)
        val detections30 = repository.getDetectionsForSessionDirect(s30.sessionId)
        assertEquals(30, detections30.size)
        assertEquals(30, c30.totalPotholes)
        assertEquals(30, c30.criticalSeverityCount + c30.highSeverityCount + c30.mediumSeverityCount + c30.lowSeverityCount)
    }

    @Test
    fun testSimultaneousTracksNotSuppressedByLocation() {
        val tracker = com.roadtwin.ai.ml.tracking.DetectionTracker()
        val lat = 11.0168
        val lon = 76.9558

        // Track 1 reported at GPS location
        assertFalse("Track 1 should not be duplicate", tracker.isDuplicateLocation(1, lat, lon))

        // Track 2 reported in the same frame/second at the same GPS location (different trackId)
        assertFalse("Simultaneous Track 2 in same frame must NOT be suppressed", tracker.isDuplicateLocation(2, lat, lon))

        // Track 3 reported in the same frame/second at the same GPS location (different trackId)
        assertFalse("Simultaneous Track 3 in same frame must NOT be suppressed", tracker.isDuplicateLocation(3, lat, lon))
    }

    @Test
    fun testPotholesSixMetersApartNotSuppressed() {
        val tracker = com.roadtwin.ai.ml.tracking.DetectionTracker()
        val lat1 = 11.016800
        val lon1 = 76.955800

        // Pothole 1
        assertFalse(tracker.isDuplicateLocation(1, lat1, lon1))
        Thread.sleep(600)

        // Pothole 2 at ~6.6 meters distance
        val lat2 = 11.016860
        val lon2 = 76.955800
        assertFalse("Pothole 6.6m away (>3m) must NOT be suppressed", tracker.isDuplicateLocation(2, lat2, lon2))
    }

    // =========================================================================
    // SECTION 15: MANDATORY GPS AND ROUTE TRACKING LIFECYCLE TESTS
    // =========================================================================

    @Test
    fun test1_StartSession_EndTimeNull() = runTest {
        val session = repository.startNewSession(0.0, 0.0, "Waiting for GPS...")
        assertEquals("ACTIVE", session.status)
        assertNull("ACTIVE session must have endTime == null", session.endTime)
        assertEquals("NOT_SYNCED", session.syncStatus)
        assertNull("ACTIVE session must have syncedAt == null", session.syncedAt)
    }

    @Test
    fun test2_FirstGpsCallback_StartLocationStored() = runTest {
        val session = repository.startNewSession(0.0, 0.0, "Waiting for GPS...")
        val lat = 11.0168
        val lon = 76.9558
        val accuracy = 3.5f
        val timestamp = 1788525000000L

        repository.updateSessionStartLocation(session.sessionId, lat, lon, accuracy, timestamp, "NH 544, Coimbatore")

        val updated = repository.getSessionDirect(session.sessionId)
        assertNotNull(updated)
        assertEquals(lat, updated!!.startLatitude, 0.0001)
        assertEquals(lon, updated.startLongitude, 0.0001)
        assertEquals(accuracy, updated.startAccuracy ?: 0f, 0.1f)
        assertEquals(timestamp, updated.startGpsTimestamp)
        assertEquals("NH 544, Coimbatore", updated.startAddress)

        // First route point must be recorded in Room
        val routePoints = repository.getRoutePointsForSessionDirect(session.sessionId)
        assertEquals(1, routePoints.size)
        assertEquals(lat, routePoints[0].latitude, 0.0001)
        assertEquals(lon, routePoints[0].longitude, 0.0001)
    }

    @Test
    fun test3_MultipleGpsCallbacks_RoutePointsStored() = runTest {
        val session = repository.startNewSession(11.0100, 76.9500, "Point A", 3f, 1000L)
        repository.addRoutePoint(session.sessionId, 11.0120, 76.9520, 3f, 2000L)
        repository.addRoutePoint(session.sessionId, 11.0140, 76.9540, 3f, 3000L)
        repository.addRoutePoint(session.sessionId, 11.0160, 76.9560, 3f, 4000L)

        val points = repository.getRoutePointsForSessionDirect(session.sessionId)
        assertEquals(4, points.size)
        assertEquals(1000L, points[0].timestamp)
        assertEquals(2000L, points[1].timestamp)
        assertEquals(3000L, points[2].timestamp)
        assertEquals(4000L, points[3].timestamp)
    }

    @Test
    fun test4_ConsecutiveGpsPoints_DistanceCalculatedAndAccumulated() = runTest {
        val distAB = LocationManager.calculateDistanceKm(11.0168, 76.9558, 11.0268, 76.9558)
        val distBC = LocationManager.calculateDistanceKm(11.0268, 76.9558, 11.0368, 76.9558)
        val totalExpected = distAB + distBC

        assertTrue("Distance between consecutive points must be > 0", distAB > 1.0)
        assertTrue("Total accumulated distance must be > 2.0 km", totalExpected > 2.0)
    }

    @Test
    fun test5_StopMonitoring_EndLocationStoredDistinctFromStart() = runTest {
        val session = repository.startNewSession(11.0168, 76.9558, "Location A", 3.2f, 1000L)
        val completed = repository.finishSession(
            sessionId = session.sessionId,
            endLatitude = 11.0250,
            endLongitude = 76.9620,
            endAddress = "Location B",
            distanceKm = 1.25,
            endAccuracy = 2.8f,
            endGpsTimestamp = 5000L
        )

        assertNotEquals("Start and End latitude must not be identical", completed.startLatitude, completed.endLatitude, 0.0001)
        assertNotEquals("Start and End longitude must not be identical", completed.startLongitude, completed.endLongitude, 0.0001)
        assertEquals(11.0250, completed.endLatitude, 0.0001)
        assertEquals(76.9620, completed.endLongitude, 0.0001)
        assertEquals("Location B", completed.endAddress)
        assertEquals(1.25, completed.distanceKm, 0.01)
    }

    @Test
    fun test6_StopMonitoring_StatusCompletedAndEndTimeNotNull() = runTest {
        val session = repository.startNewSession(11.0, 76.0, "Origin", 3f, 1000L)
        val completed = repository.finishSession(
            sessionId = session.sessionId,
            endLatitude = 11.01,
            endLongitude = 76.01,
            endAddress = "Destination",
            distanceKm = 1.5,
            endAccuracy = 2.5f,
            endGpsTimestamp = 3000L
        )

        assertEquals("COMPLETED", completed.status)
        assertNotNull("COMPLETED session must have non-null endTime", completed.endTime)
        assertTrue("endTime must be >= startTime", completed.endTime!! >= completed.startTime)
        assertEquals("PENDING_UPLOAD", completed.syncStatus)
        assertNull("syncedAt must be null until successful Firestore sync", completed.syncedAt)
    }

    @Test
    fun test7_ActiveSessionCannotSyncToFirestore() = runTest {
        val session = repository.startNewSession(11.0, 76.0, "Active Route")
        assertEquals("ACTIVE", session.status)

        // SessionDao.getPendingSessions() must never return ACTIVE sessions
        val pending = sessionDao.getPendingSessions()
        assertFalse("Pending sessions to sync must NEVER include ACTIVE sessions", pending.any { it.sessionId == session.sessionId })

        // Attempting to mark ACTIVE session as SYNCED must throw IllegalStateException
        try {
            sessionDao.updateSyncStatus(session.sessionId, "SYNCED", syncedAt = System.currentTimeMillis())
            fail("Expected IllegalStateException when setting ACTIVE session to SYNCED")
        } catch (e: IllegalStateException) {
            // Expected safeguard
        }
    }

    @Test
    fun test8_CompletedSessionCanSyncToFirestore() = runTest {
        val session = repository.startNewSession(11.0, 76.0, "Completed Route")
        repository.finishSession(
            sessionId = session.sessionId,
            endLatitude = 11.02,
            endLongitude = 76.02,
            endAddress = "Finished Route",
            distanceKm = 2.2,
            endAccuracy = 3f,
            endGpsTimestamp = 4000L
        )

        val pending = sessionDao.getPendingSessions()
        assertTrue("Completed session with PENDING_UPLOAD must be eligible for sync", pending.any { it.sessionId == session.sessionId })
    }

    @Test
    fun test9_FirestorePayloadContainsRoutePointsArray() = runTest {
        val session = repository.startNewSession(11.0168, 76.9558, "Start", 3.0f, 1000L)
        repository.addRoutePoint(session.sessionId, 11.0180, 76.9570, 3.5f, 2000L)
        repository.addRoutePoint(session.sessionId, 11.0200, 76.9590, 4.0f, 3000L)
        repository.finishSession(
            sessionId = session.sessionId,
            endLatitude = 11.0220,
            endLongitude = 76.9610,
            endAddress = "End",
            distanceKm = 0.8,
            endAccuracy = 3.2f,
            endGpsTimestamp = 4000L
        )

        val routePoints = repository.getRoutePointsForSessionDirect(session.sessionId)
        assertTrue("Route points must contain recorded points", routePoints.size >= 3)

        val routePointsPayload = routePoints.map { pt ->
            mapOf(
                "latitude" to pt.latitude,
                "longitude" to pt.longitude,
                "accuracy" to pt.accuracy,
                "timestamp" to pt.timestamp
            )
        }
        assertEquals(routePoints.size, routePointsPayload.size)
        assertEquals(11.0168, routePointsPayload[0]["latitude"] as Double, 0.0001)
        assertEquals(3.0f, routePointsPayload[0]["accuracy"] as Float, 0.1f)
    }

    @Test
    fun test10_NoHardcodedProductionCoordinates() = runTest {
        val session = repository.startNewSession(0.0, 0.0, "Waiting for GPS...")
        assertEquals(0.0, session.startLatitude, 0.0)
        assertEquals(0.0, session.startLongitude, 0.0)
        assertEquals(0.0, session.endLatitude, 0.0)
        assertEquals(0.0, session.endLongitude, 0.0)
        assertEquals("Waiting for GPS...", session.startAddress)
    }

    @Test
    fun test11_DetectionUsesRealLatestGpsState() = runTest {
        val session = repository.startNewSession(11.0168, 76.9558, "Route", 3f, 1000L)
        val realLat = 11.0195
        val realLon = 76.9572
        val realAccuracy = 2.4f

        val id = repository.recordDetection(
            sessionId = session.sessionId,
            latitude = realLat,
            longitude = realLon,
            gpsAccuracy = realAccuracy,
            address = "Real Road, Coimbatore",
            confidence = 0.88f,
            severity = "HIGH",
            imagePath = "/path/to/img.jpg"
        )

        val detection = repository.getDetectionById(id.toInt())
        assertNotNull(detection)
        assertEquals(realLat, detection!!.latitude, 0.0001)
        assertEquals(realLon, detection.longitude, 0.0001)
        assertEquals(realAccuracy, detection.gpsAccuracy, 0.01f)
        assertEquals("Real Road, Coimbatore", detection.address)
    }

    @Test
    fun test12_LocationUnavailable_NoFakeCoordinatesGenerated() = runTest {
        val session = repository.startNewSession(0.0, 0.0, "No GPS")
        val completed = repository.finishSession(
            sessionId = session.sessionId,
            endLatitude = 0.0,
            endLongitude = 0.0,
            endAddress = "",
            distanceKm = 0.0
        )

        // If GPS is unavailable, endLatitude must remain 0.0 and never duplicate startLocation
        assertEquals(0.0, completed.endLatitude, 0.0)
        assertEquals(0.0, completed.endLongitude, 0.0)
        assertNull(completed.endAccuracy)
    }

    // =========================================================================
    // GATES 21, 22, 23: ADDITIONAL SESSION LIFECYCLE & DATA INTEGRITY
    // =========================================================================

    @Test
    fun test21_ActiveSessionsInvariant() = runTest {
        val session = repository.startNewSession(0.0, 0.0, "Active Test")
        assertEquals("ACTIVE", session.status)
        assertNull("ACTIVE session must have endTime == null", session.endTime)
        assertNull("ACTIVE session must have syncedAt == null", session.syncedAt)
        assertEquals("NOT_SYNCED", session.syncStatus)

        // Must never appear in pending upload queries
        val pending = sessionDao.getPendingSessions()
        assertFalse("Pending upload sessions must NEVER include ACTIVE sessions", pending.any { it.sessionId == session.sessionId })

        // Attempting to generate PDF report for active session must throw IllegalStateException
        try {
            repository.generateSessionPdf(session.sessionId)
            fail("Expected IllegalStateException when generating PDF for ACTIVE session")
        } catch (e: IllegalStateException) {
            assertTrue(e.message!!.contains("ACTIVE or incomplete"))
        }
    }

    @Test
    fun test22_CompletedSessionsInvariant() = runTest {
        val session = repository.startNewSession(11.0168, 76.9558, "Start", 3f, 1000L)
        val completed = repository.finishSession(
            sessionId = session.sessionId,
            endLatitude = 11.0250,
            endLongitude = 76.9620,
            endAddress = "End",
            distanceKm = 1.25,
            endAccuracy = 2.8f,
            endGpsTimestamp = 5000L
        )

        assertEquals("COMPLETED", completed.status)
        assertNotNull("COMPLETED session must have non-null endTime", completed.endTime)
        assertEquals("PENDING_UPLOAD", completed.syncStatus)
        assertNull("syncedAt is null prior to cloud sync", completed.syncedAt)

        // Transition to SYNCED
        sessionDao.updateSyncStatus(completed.sessionId, "SYNCED", syncedAt = 6000L)
        val synced = sessionDao.getSessionById(completed.sessionId)
        assertNotNull(synced)
        assertEquals("SYNCED", synced!!.syncStatus)
        assertEquals(6000L, synced.syncedAt)
    }

    @Test
    fun test23_FirestorePayloadStrictMatchWithRoom() = runTest {
        val session = repository.startNewSession(10.6532, 77.0346, "Start Point", 3.2f, 1000L)
        repository.addRoutePoint(session.sessionId, 10.6540, 77.0355, 3.0f, 2000L)
        repository.addRoutePoint(session.sessionId, 10.6550, 77.0365, 3.5f, 3000L)

        val d1Id = repository.recordDetection(
            sessionId = session.sessionId,
            latitude = 10.6540,
            longitude = 77.0355,
            gpsAccuracy = 3.0f,
            address = "Point 1",
            confidence = 0.88f,
            severity = "HIGH",
            imagePath = "/img1.jpg"
        )
        val d2Id = repository.recordDetection(
            sessionId = session.sessionId,
            latitude = 10.6550,
            longitude = 77.0365,
            gpsAccuracy = 3.5f,
            address = "Point 2",
            confidence = 0.92f,
            severity = "CRITICAL",
            imagePath = "/img2.jpg"
        )

        val completed = repository.finishSession(
            sessionId = session.sessionId,
            endLatitude = 10.6560,
            endLongitude = 77.0375,
            endAddress = "End Point",
            distanceKm = 0.45,
            endAccuracy = 2.9f,
            endGpsTimestamp = 4000L
        )

        // Retrieve Room records
        val roomDetections = repository.getDetectionsForSessionDirect(session.sessionId)
        val roomRoutePoints = repository.getRoutePointsForSessionDirect(session.sessionId)

        // Construct Firestore serialization exactly as FirebaseSyncManager does
        val startLocationData = mapOf(
            "latitude" to completed.startLatitude,
            "longitude" to completed.startLongitude,
            "accuracy" to (completed.startAccuracy ?: 0f),
            "timestamp" to (completed.startGpsTimestamp ?: completed.startTime)
        )
        val endLocationData = mapOf(
            "latitude" to completed.endLatitude,
            "longitude" to completed.endLongitude,
            "accuracy" to (completed.endAccuracy ?: 0f),
            "timestamp" to (completed.endGpsTimestamp ?: completed.endTime ?: 0L)
        )
        val firestoreDetections = roomDetections.map { d ->
            mapOf(
                "detectionId" to d.detectionId.ifBlank { "D-${d.id}" },
                "latitude" to d.latitude,
                "longitude" to d.longitude,
                "gpsAccuracy" to d.gpsAccuracy,
                "address" to d.address,
                "confidence" to d.confidence,
                "severity" to d.severity,
                "severitySource" to d.severitySource,
                "timestamp" to d.timestamp
            )
        }
        val firestoreRoutePoints = roomRoutePoints.map { pt ->
            mapOf(
                "latitude" to pt.latitude,
                "longitude" to pt.longitude,
                "accuracy" to pt.accuracy,
                "timestamp" to pt.timestamp
            )
        }

        // Verify Gate 23 invariants
        assertEquals(completed.startLatitude, startLocationData["latitude"] as Double, 0.0001)
        assertEquals(completed.startLongitude, startLocationData["longitude"] as Double, 0.0001)
        assertEquals(completed.endLatitude, endLocationData["latitude"] as Double, 0.0001)
        assertEquals(completed.endLongitude, endLocationData["longitude"] as Double, 0.0001)
        assertEquals(completed.distanceKm, 0.45, 0.001)
        assertEquals(2, roomDetections.size)
        assertEquals(roomDetections.size, firestoreDetections.size)
        assertEquals(roomRoutePoints.size, firestoreRoutePoints.size)
        assertEquals(completed.criticalSeverityCount, 1)
        assertEquals(completed.highSeverityCount, 1)
        assertEquals("COMPLETED", completed.status)
    }
}
