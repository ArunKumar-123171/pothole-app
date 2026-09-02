package com.roadtwin.ai.ml.inference

import android.graphics.RectF

data class DetectionResult(
    val boundingBox: RectF, // Coordinates normalized between 0.0 and 1.0 relative to image size
    val confidence: Float,
    val classIndex: Int,
    val label: String
)
