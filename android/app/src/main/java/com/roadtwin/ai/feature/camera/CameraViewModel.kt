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

    private var appContext: Context? = null
    private var locationManager: LocationManager? = null
    private var lastLocation: Location? = null
    private var toneGenerator: ToneGenerator? = null
    private val isMonitoringActive = java.util.concurrent.atomic.AtomicBoolean(true)

    var targetFps = 5
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

        // Start New Session if not already active
        if (_currentSessionId.value == null) {
            viewModelScope.launch(Dispatchers.IO) {
                val loc = locationManager?.getCurrentLocation()
                val startLat = loc?.latitude ?: 0.0
                val startLon = loc?.longitude ?: 0.0
                val startAddr = if (startLat != 0.0) {
                    locationManager?.getAddressFromLocation(startLat, startLon) ?: "Location pending"
                } else {
                    "Location pending"
                }

                val session = repository.startNewSession(startLat, startLon, startAddr)
                _currentSessionId.value = session.sessionId
                Log.d(TAG, "Initialized monitoring session: ${session.sessionId}")

                // Start observing location updates for live distance tracking
                startDistanceTracking()
            }
        }
    }

    private fun startDistanceTracking() {
        val locManager = locationManager ?: return
        viewModelScope.launch(Dispatchers.IO) {
            locManager.getLocationUpdates(2000L).collect { location ->
                val prev = lastLocation
                if (prev != null) {
                    val delta = LocationManager.calculateDistanceKm(
                        prev.latitude, prev.longitude,
                        location.latitude, location.longitude
                    )
                    if (delta > 0.001) { // Filter tiny GPS jitter
                        _sessionDistanceKm.value += delta
                    }
                }
                lastLocation = location
            }
        }
    }

    fun toggleMute() {
        _isMuted.value = !_isMuted.value
    }

    @OptIn(ExperimentalGetImage::class)
    override fun analyze(imageProxy: ImageProxy) {
        if (!isMonitoringActive.get()) {
            imageProxy.close()
            return
        }

        val currentTime = System.currentTimeMillis()
        val frameIntervalMs = 1000L / targetFps

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

                _detections.value = results

                // Feed results to multi-frame IoU tracker (requires 3 consecutive compatible frames)
                tracker.update(results) { confirmedDetection ->
                    handleConfirmedPothole(confirmedDetection, processedBitmap)
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error analyzing camera frame", e)
            } finally {
                imageProxy.close()
            }
        }
    }

    private fun handleConfirmedPothole(confirmedDetection: DetectionResult, bitmap: Bitmap) {
        val sessionId = _currentSessionId.value ?: return
        val context = appContext ?: return
        val locManager = locationManager ?: return

        viewModelScope.launch(Dispatchers.IO) {
            try {
                val loc = locManager.getCurrentLocation() ?: lastLocation
                val lat = loc?.latitude ?: 0.0
                val lon = loc?.longitude ?: 0.0
                val accuracy = loc?.accuracy ?: 0.0f

                // Check duplicate suppression
                if (lat != 0.0 && lon != 0.0) {
                    if (tracker.isDuplicateLocation(lat, lon)) {
                        Log.d(TAG, "Suppressed duplicate pothole detection at ($lat, $lon)")
                        return@launch
                    }
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
                val address = if (lat != 0.0) {
                    locManager.getAddressFromLocation(lat, lon)
                } else {
                    "Location unavailable"
                }

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

                _potholeCount.value += 1
                Log.d(TAG, "Recorded confirmed pothole $detectionId in session $sessionId ($severity)")
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
        val sessionId = _currentSessionId.value ?: return
        isMonitoringActive.set(false)
        val locManager = locationManager

        viewModelScope.launch(Dispatchers.IO) {
            val endLoc = locManager?.getCurrentLocation() ?: lastLocation
            val endLat = endLoc?.latitude ?: 0.0
            val endLon = endLoc?.longitude ?: 0.0
            val endAddr = if (endLat != 0.0) {
                locManager?.getAddressFromLocation(endLat, endLon) ?: "Location pending"
            } else {
                "Location pending"
            }

            repository.finishSession(
                sessionId = sessionId,
                endLatitude = endLat,
                endLongitude = endLon,
                endAddress = endAddr,
                distanceKm = _sessionDistanceKm.value
            )

            _currentSessionId.value = null
            _navigateToSummary.emit(sessionId)
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
