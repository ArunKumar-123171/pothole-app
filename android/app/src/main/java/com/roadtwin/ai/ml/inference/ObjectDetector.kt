package com.roadtwin.ai.ml.inference

import android.content.Context
import android.graphics.Bitmap

interface ObjectDetector {
    /**
     * Initializes and loads the TFLite/LiteRT model file.
     * Returns true if loaded successfully, false otherwise.
     */
    fun init(context: Context): Boolean

    /**
     * Runs object detection on the provided input image.
     */
    fun detect(bitmap: Bitmap): List<DetectionResult>

    /**
     * Closes and releases interpreter resources.
     */
    fun close()
}
