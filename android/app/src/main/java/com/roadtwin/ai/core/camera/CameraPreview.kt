package com.roadtwin.ai.core.camera

import android.content.Context
import android.util.Log
import android.util.Size
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

private const val TAG = "CameraPreview"

@Composable
fun CameraPreview(
    modifier: Modifier = Modifier,
    cameraSelector: CameraSelector = CameraSelector.DEFAULT_BACK_CAMERA,
    analyzer: ImageAnalysis.Analyzer? = null,
    isFlashOn: Boolean = false,
    zoomRatio: Float = 1.0f,
    onPreviewViewCreated: (PreviewView) -> Unit = {}
) {
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val cameraExecutor = remember { Executors.newSingleThreadExecutor() }
    var activeCamera by remember { mutableStateOf<Camera?>(null) }

    val previewView = remember {
        PreviewView(context).apply {
            scaleType = PreviewView.ScaleType.FILL_CENTER
        }
    }

    LaunchedEffect(isFlashOn, activeCamera) {
        try {
            activeCamera?.cameraControl?.enableTorch(isFlashOn)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to toggle torch: ${e.message}")
        }
    }

    LaunchedEffect(zoomRatio, activeCamera) {
        try {
            activeCamera?.cameraControl?.setZoomRatio(zoomRatio)
        } catch (e: Exception) {
            Log.w(TAG, "Failed to set zoom: ${e.message}")
        }
    }

    AndroidView(
        factory = {
            onPreviewViewCreated(previewView)
            previewView
        },
        modifier = modifier.fillMaxSize()
    )

    DisposableEffect(lifecycleOwner, cameraSelector, analyzer) {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)
        cameraProviderFuture.addListener({
            val cameraProvider = cameraProviderFuture.get()

            // Setup Preview Use Case
            val preview = Preview.Builder().build().also {
                it.surfaceProvider = previewView.surfaceProvider
            }

            // Setup ImageAnalysis Use Case if analyzer is provided
            val imageAnalysis = if (analyzer != null) {
                ImageAnalysis.Builder()
                    .setTargetResolution(Size(640, 480))
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also {
                        it.setAnalyzer(cameraExecutor, analyzer)
                    }
            } else {
                null
            }

            try {
                // Must unbind all use cases before rebinding
                cameraProvider.unbindAll()

                // Bind use cases to lifecycle
                activeCamera = if (imageAnalysis != null) {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview,
                        imageAnalysis
                    )
                } else {
                    cameraProvider.bindToLifecycle(
                        lifecycleOwner,
                        cameraSelector,
                        preview
                    )
                }
            } catch (exc: Exception) {
                Log.e(TAG, "Use case binding failed", exc)
            }
        }, ContextCompat.getMainExecutor(context))

        onDispose {
            cameraExecutor.shutdown()
        }
    }
}
