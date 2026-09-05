package com.roadtwin.ai.feature.camera

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.location.Location
import android.media.AudioManager
import android.media.ToneGenerator
import android.util.Log
import androidx.annotation.OptIn
import androidx.camera.core.ExperimentalGetImage
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.roadtwin.ai.core.location.LocationManager
import com.roadtwin.ai.data.local.MonitoringSessionEntity
import com.roadtwin.ai.data.repository.ReportsRepository
import com.roadtwin.ai.ml.inference.DetectionResult
import com.roadtwin.ai.ml.inference.ObjectDetector
import com.roadtwin.ai.ml.inference.TFLiteObjectDetector
import com.roadtwin.ai.ml.model.ModelConfig
import com.roadtwin.ai.ml.tracking.DetectionTracker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream

private const val TAG = "CameraViewModel"

sealed interface ModelLoadState {
    object Idle : ModelLoadState
    object Loading : ModelLoadState
    object Success : ModelLoadState
    data class Error(val message: String) : ModelLoadState
}

class CameraViewModel(
    private val repository: ReportsRepository,
    private val detector: ObjectDetector = TFLiteObjectDetector(),
    private val tracker: DetectionTracker = DetectionTracker()
) : ViewModel(), ImageAnalysis.Analyzer {

    private val _modelLoadState = MutableStateFlow<ModelLoadState>(ModelLoadState.Idle)
    val modelLoadState: StateFlow<ModelLoadState> = _modelLoadState.asStateFlow()

    private val _detections = MutableStateFlow<List<DetectionResult>>(emptyList())
    val detections: StateFlow<List<DetectionResult>> = _detections.asStateFlow()

    private val _frameWidth = MutableStateFlow(0)
    val frameWidth: StateFlow<Int> = _frameWidth.asStateFlow()

    private val _frameHeight = MutableStateFlow(0)
    val frameHeight: StateFlow<Int> = _frameHeight.asStateFlow()

    // Session State
    private val _currentSessionId = MutableStateFlow<String?>(null)
    val currentSessionId: StateFlow<String?> = _currentSessionId.asStateFlow()

    private val _sessionDistanceKm = MutableStateFlow(0.0)
    val sessionDistanceKm: StateFlow<Double> = _sessionDistanceKm.asStateFlow()

    private val _potholeCount = MutableStateFlow(0)
    val potholeCount: StateFlow<Int> = _potholeCount.asStateFlow()

    private val _isMuted = MutableStateFlow(false)
    val isMuted: StateFlow<Boolean> = _isMuted.asStateFlow()

    private val _lastLatencyMs = MutableStateFlow(0L)
    val lastLatencyMs: StateFlow<Long> = _lastLatencyMs.asStateFlow()

    private val _navigateToSummary = MutableSharedFlow<String>(extraBufferCapacity = 1)
    val navigateToSummary: SharedFlow<String> = _navigateToSummary.asSharedFlow()

    private val _gpsStatus = MutableStateFlow<String>("Waiting for GPS location...")
    val gpsStatus: StateFlow<String> = _gpsStatus.asStateFlow()

    private val _isLocationEnabled = MutableStateFlow<Boolean>(true)
    val isLocationEnabled: StateFlow<Boolean> = _isLocationEnabled.asStateFlow()

    private val _liveSpeedKmh = MutableStateFlow<Int>(0)
    val liveSpeedKmh: StateFlow<Int> = _liveSpeedKmh.asStateFlow()

    private val _liveFps = MutableStateFlow<Int>(10)
    val liveFps: StateFlow<Int> = _liveFps.asStateFlow()

    private val _isFlashOn = MutableStateFlow<Boolean>(false)
    val isFlashOn: StateFlow<Boolean> = _isFlashOn.asStateFlow()

    private val _zoomRatio = MutableStateFlow<Float>(1.0f)
    val zoomRatio: StateFlow<Float> = _zoomRatio.asStateFlow()

    private var fpsFrameCount = 0
    private var fpsWindowStart = System.currentTimeMillis()

    // Real-Time Pipeline Telemetry Counters
    val totalFramesAnalyzed = java.util.concurrent.atomic.AtomicLong(0)
    val rawYoloDetections = java.util.concurrent.atomic.AtomicLong(0)
    val detectionsAfterConfidenceFilter = java.util.concurrent.atomic.AtomicLong(0)
    val trackerCandidates = java.util.concurrent.atomic.AtomicLong(0)
    val confirmedTracks = java.util.concurrent.atomic.AtomicLong(0)
    val duplicateSuppressedDetections = java.util.concurrent.atomic.AtomicLong(0)
    val detectionRecordsInserted = java.util.concurrent.atomic.AtomicLong(0)

    private var appContext: Context? = null
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var isStartLocationSet = false
    private var toneGenerator: ToneGenerator? = null
    private val isMonitoringActive = java.util.concurrent.atomic.AtomicBoolean(true)

    var targetFps = 10
    private var lastAnalysisTimestamp = 0L

    fun initialize(context: Context) {
        isMonitoringActive.set(true)
        appContext = context.applicationContext
        if (locationManager == null) {
            locationManager = LocationManager(context.applicationContext)
        }
        try {
            toneGenerator = ToneGenerator(AudioManager.STREAM_NOTIFICATION, 70)
        } catch (e: Exception) {
            Log.w(TAG, "ToneGenerator init failed: ${e.message}")
        }

        // Check location services state
        val isLocEnabled = locationManager?.isLocationServicesEnabled() == true
        _isLocationEnabled.value = isLocEnabled
        if (!isLocEnabled) {
            _gpsStatus.value = "Location is turned off"
        }

        // Initialize ML Model
        if (_modelLoadState.value !is ModelLoadState.Success) {
            _modelLoadState.value = ModelLoadState.Loading
            viewModelScope.launch(Dispatchers.IO) {
                val success = detector.init(context)
                if (success) {
                    _modelLoadState.value = ModelLoadState.Success
                } else {
                    _modelLoadState.value = ModelLoadState.Error(
                        "Failed to load YOLO26n model file '${ModelConfig.MODEL_NAME}'"
                    )
                }
            }
        }

        // Start New Session immediately with clean non-hardcoded state
        if (_currentSessionId.value == null) {
            viewModelScope.launch(Dispatchers.IO) {
                isStartLocationSet = false
                _sessionDistanceKm.value = 0.0
                if (!isLocEnabled) {
                    _gpsStatus.value = "Location is turned off"
                } else {
                    _gpsStatus.value = "Waiting for GPS location..."
                }

                val session = repository.startNewSession(
                    startLatitude = 0.0,
                    startLongitude = 0.0,
                    startAccuracy = null,
                    startGpsTimestamp = null,
                    startAddress = "Waiting for GPS..."
                )
                _currentSessionId.value = session.sessionId
                Log.d(TAG, "Initialized monitoring session: ${session.sessionId}")

                // Start observing continuous location updates
                startDistanceTracking()
            }
        }
    }

    fun refreshLocationStatus() {
        val locManager = locationManager ?: return
        val isEnabled = locManager.isLocationServicesEnabled()
        _isLocationEnabled.value = isEnabled
        if (!isEnabled) {
            _gpsStatus.value = "Location is turned off"
        } else {
            if (lastLocation == null) {
                _gpsStatus.value = "Waiting for GPS..."
            }
            viewModelScope.launch(Dispatchers.IO) {
                val loc = locManager.getCurrentLocation() ?: locManager.getLastKnownLocation()
                if (loc != null) {
                    lastLocation = loc
                    _gpsStatus.value = "GPS Locked (±${String.format(java.util.Locale.US, "%.1f", loc.accuracy)}m)"
                    if (loc.hasSpeed()) {
                        _liveSpeedKmh.value = (loc.speed * 3.6f).toInt()
                    }
                }
            }
        }
    }

    private fun startDistanceTracking() {
        val locManager = locationManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            locManager.getLocationUpdates(1500L).collect { location ->
                if (!locManager.isLocationServicesEnabled()) {
                    _isLocationEnabled.value = false
                    _gpsStatus.value = "Location is turned off"
                    return@collect
                }

                _isLocationEnabled.value = true
                _gpsStatus.value = "GPS Locked (±${String.format(java.util.Locale.US, "%.1f", location.accuracy)}m)"

                if (location.hasSpeed()) {
                    _liveSpeedKmh.value = (location.speed * 3.6f).toInt()
                }

                val sessionId = _currentSessionId.value
                if (sessionId != null) {
                    if (!isStartLocationSet && location.latitude != 0.0 && location.longitude != 0.0) {
                        // First valid real GPS fix acquired
                        isStartLocationSet = true
                        lastLocation = location
                        val address = locManager.getAddressFromLocation(location.latitude, location.longitude)
                        repository.updateSessionStartLocation(
                            sessionId = sessionId,
                            latitude = location.latitude,
                            longitude = location.longitude,
                            accuracy = location.accuracy,
                            timestamp = location.time,
                            address = address
                        )
                        Log.i(TAG, "Recorded FIRST REAL GPS start location for session $sessionId: (${location.latitude}, ${location.longitude}, ±${location.accuracy}m)")
                    } else if (isStartLocationSet) {
                        val prev = lastLocation
                        if (prev != null) {
                            val delta = LocationManager.calculateDistanceKm(
                                prev.latitude, prev.longitude,
                                location.latitude, location.longitude
                            )
                            if (delta > 0.002) { // Filter tiny GPS jitter (2 meters)
                                _sessionDistanceKm.value += delta
                                repository.addRoutePoint(
                                    sessionId = sessionId,
                                    latitude = location.latitude,
                                    longitude = location.longitude,
                                    accuracy = location.accuracy,
                                    timestamp = location.time
                                )
                                lastLocation = location
                            }
                        } else {
                            lastLocation = location
                        }
                    }
                }
            }
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    fun toggleFlash() {
        _isFlashOn.value = !_isFlashOn.value
    }

    fun setZoomRatio(ratio: Float) {
        _zoomRatio.value = ratio
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isMonitoringActive.get()) {
            imageProxy.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        val frameIntervalMs = 1000L / targetFps

        // Real-time FPS measurement window (1 second)
        fpsFrameCount++
        if (currentTime - fpsWindowStart >= 1000L) {
            val delta = currentTime - fpsWindowStart
            val measuredFps = if (delta > 0) ((fpsFrameCount * 1000L) / delta).toInt() else targetFps
            _liveFps.value = measuredFps.coerceIn(1, 30)
            fpsFrameCount = 0
            fpsWindowStart = currentTime
        }

        // Frame rate throttling to reduce battery and CPU load
        if (currentTime - lastAnalysisTimestamp < frameIntervalMs) {
            imageProxy.close()
            return
        }

        if (_modelLoadState.value !is ModelLoadState.Success) {
            imageProxy.close()
            return
        }

        lastAnalysisTimestamp = currentTime
        val frameNum = totalFramesAnalyzed.incrementAndGet()

        viewModelScope.launch(Dispatchers.Default) {
            try {
                val bitmap = imageProxy.toBitmap()
                val rotationDegrees = imageProxy.imageInfo.rotationDegrees
                val processedBitmap = if (rotationDegrees != 0) {
                    rotateBitmap(bitmap, rotationDegrees)
                } else {
                    bitmap
                }

                _frameWidth.value = processedBitmap.width
                _frameHeight.value = processedBitmap.height

                val inferenceStart = System.currentTimeMillis()
                val results = detector.detect(processedBitmap)
                _lastLatencyMs.value = System.currentTimeMillis() - inferenceStart

                detectionsAfterConfidenceFilter.addAndGet(results.size.toLong())
                _detections.value = results

                if (frameNum % 30 == 0L) {
                    Log.i(TAG, "[Pipeline Telemetry] Frames: ${totalFramesAnalyzed.get()}, DetectionsFiltered: ${detectionsAfterConfidenceFilter.get()}, Confirmed: ${confirmedTracks.get()}, Suppressed: ${duplicateSuppressedDetections.get()}, Inserted: ${detectionRecordsInserted.get()}")
                }

                // Feed results to multi-frame motion tracker
                tracker.update(results) { track, confirmedDetection ->
                    confirmedTracks.incrementAndGet()
                    handleConfirmedPothole(track.id, confirmedDetection, processedBitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing camera frame", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun handleConfirmedPothole(trackId: Int, confirmedDetection: DetectionResult, bitmap: Bitmap) {
        val context = appContext ?: return
        val locManager = locationManager ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                // Wait briefly for sessionId if session is still starting up
                var retries = 5
                while (_currentSessionId.value == null && retries > 0) {
                    kotlinx.coroutines.delay(200L)
                    retries--
                }

                val sessionId = _currentSessionId.value
                if (sessionId == null) {
                    Log.w(TAG, "[Pipeline Telemetry] Unable to record pothole: active sessionId is null")
                    return@launch
                }

                val isLocEnabled = locManager.isLocationServicesEnabled()
                _isLocationEnabled.value = isLocEnabled
                if (!isLocEnabled) {
                    _gpsStatus.value = "Location is turned off"
                    Log.w(TAG, "[Pipeline Telemetry] Location services disabled. Skipping detection record to avoid fake coordinates.")
                    return@launch
                }

                val loc = lastLocation ?: locManager.getCurrentLocation() ?: locManager.getLastKnownLocation()
                val lat = loc?.latitude ?: 0.0
                val lon = loc?.longitude ?: 0.0
                val accuracy = loc?.accuracy ?: 0.0f

                if (lat == 0.0 && lon == 0.0) {
                    Log.w(TAG, "[Pipeline Telemetry] No valid GPS fix yet. Skipping detection record to avoid fake 0,0 coordinates.")
                    return@launch
                }

                // Check duplicate suppression
                if (tracker.isDuplicateLocation(trackId, lat, lon)) {
                    duplicateSuppressedDetections.incrementAndGet()
                    Log.d(TAG, "[Pipeline Telemetry] Suppressed duplicate pothole detection for track #$trackId at ($lat, $lon)")
                    return@launch
                }

                // Play audio alert if not muted
                if (!_isMuted.value) {
                    try {
                        toneGenerator?.startTone(ToneGenerator.TONE_PROP_BEEP, 150)
                    } catch (e: Exception) {
                        Log.w(TAG, "Failed to play beep: ${e.message}")
                    }
                }

                // Geocode address
                val address = locManager.getAddressFromLocation(lat, lon)

                // Save confirmed detection JPEG locally
                val detectionSeq = (System.currentTimeMillis() % 100000).toString().padStart(5, '0')
                val detectionId = "D-$detectionSeq"
                val imagePath = saveDetectionImage(bitmap, context, sessionId, detectionId)

                val severity = estimateSeverity(confirmedDetection)

                // Record in Room Database
                repository.recordDetection(
                    sessionId = sessionId,
                    latitude = lat,
                    longitude = lon,
                    gpsAccuracy = accuracy,
                    address = address,
                    confidence = confirmedDetection.confidence,
                    severity = severity,
                    imagePath = imagePath,
                    modelName = ModelConfig.MODEL_NAME,
                    modelVersion = ModelConfig.MODEL_VERSION,
                    modelInputSize = "${ModelConfig.INPUT_WIDTH}x${ModelConfig.INPUT_HEIGHT}",
                    modelPrecision = ModelConfig.MODEL_PRECISION_TYPE,
                    confidenceThreshold = ModelConfig.confidenceThreshold
                )

                detectionRecordsInserted.incrementAndGet()
                _potholeCount.value += 1
                Log.i(TAG, "[Pipeline Telemetry] Recorded confirmed pothole $detectionId in session $sessionId ($severity). Total Inserted: ${detectionRecordsInserted.get()}")
            } catch (e: Exception) {
                Log.e(TAG, "Error handling confirmed pothole", e)
            }
        }
    }

    private fun saveDetectionImage(bitmap: Bitmap, context: Context, sessionId: String, detectionId: String): String {
        val sessionDir = File(context.filesDir, "roadtwin/reports/$sessionId")
        if (!sessionDir.exists()) {
            sessionDir.mkdirs()
        }
        val file = File(sessionDir, "$detectionId.jpg")
        FileOutputStream(file).use { out ->
            bitmap.compress(Bitmap.CompressFormat.JPEG, 85, out)
        }
        return file.absolutePath
    }

    private fun estimateSeverity(detection: DetectionResult): String {
        val box = detection.boundingBox
        val area = (box.right - box.left) * (box.bottom - box.top)
        val conf = detection.confidence
        return when {
            area > 0.15f || conf > 0.90f -> "CRITICAL"
            area > 0.08f || conf > 0.75f -> "HIGH"
            area > 0.03f || conf > 0.55f -> "MEDIUM"
            else -> "LOW"
        }
    }

    fun stopMonitoring() {
        Log.i(TAG, "STOP_BUTTON_CLICKED")
        isMonitoringActive.set(false)
        val sessionId = _currentSessionId.value
        val locManager = locationManager

        viewModelScope.launch(Dispatchers.IO) {
            if (sessionId != null) {
                val endLoc = locManager?.getCurrentLocation() ?: lastLocation
                val endLat = endLoc?.latitude ?: 0.0
                val endLon = endLoc?.longitude ?: 0.0
                val endAccuracy = endLoc?.accuracy
                val endTimestamp = endLoc?.time
                val endAddr = if (endLat != 0.0 && endLon != 0.0) {
                    locManager?.getAddressFromLocation(endLat, endLon) ?: "GPS location recorded"
                } else {
                    ""
                }

                repository.finishSession(
                    sessionId = sessionId,
                    endLatitude = endLat,
                    endLongitude = endLon,
                    endAccuracy = endAccuracy,
                    endGpsTimestamp = endTimestamp,
                    endAddress = endAddr,
                    distanceKm = _sessionDistanceKm.value
                )

                Log.i(TAG, "Finalized session $sessionId in Room -> COMPLETED (Potholes: ${_potholeCount.value}, Distance: ${_sessionDistanceKm.value} km, End: ($endLat, $endLon))")
                _currentSessionId.value = null
                _navigateToSummary.emit(sessionId)
            } else {
                Log.w(TAG, "stopMonitoring: sessionId was null, cleaning up active sessions and navigating")
                repository.cleanupAbandonedSessions()
                _navigateToSummary.emit("")
            }
        }
    }

    private fun rotateBitmap(bitmap: Bitmap, degrees: Int): Bitmap {
        val matrix = Matrix().apply { postRotate(degrees.toFloat()) }
        return Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
    }

    override fun onCleared() {
        super.onCleared()
        tracker.clear()
        detector.close()
        try {
            toneGenerator?.release()
        } catch (e: Exception) {
            // ignore
        }
    }
}
