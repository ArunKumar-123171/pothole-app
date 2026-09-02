package com.roadtwin.ai.ml.postprocessing

import android.graphics.RectF
import com.roadtwin.ai.ml.inference.DetectionResult

object NMS {
    /**
     * Calculates the Intersection over Union (IoU) of two bounding boxes.
     */
    fun calculateIoU(box1: RectF, box2: RectF): Float {
        val left = maxOf(box1.left, box2.left)
        val top = maxOf(box1.top, box2.top)
        val right = minOf(box1.right, box2.right)
        val bottom = minOf(box1.bottom, box2.bottom)

        if (left >= right || top >= bottom) return 0f

        val intersectionArea = (right - left) * (bottom - top)
        val box1Area = (box1.right - box1.left) * (box1.bottom - box1.top)
        val box2Area = (box2.right - box2.left) * (box2.bottom - box2.top)
        val unionArea = box1Area + box2Area - intersectionArea

        return if (unionArea > 0f) intersectionArea / unionArea else 0f
    }

    /**
     * Filters candidate detections based on confidence score and overlaps.
     */
    fun performNMS(
        detections: List<DetectionResult>,
        iouThreshold: Float
    ): List<DetectionResult> {
        val sorted = detections.sortedByDescending { it.confidence }
        val result = mutableListOf<DetectionResult>()
        val active = BooleanArray(sorted.size) { true }

        for (i in sorted.indices) {
            if (!active[i]) continue
            val current = sorted[i]
            result.add(current)
            for (j in i + 1 until sorted.size) {
                if (active[j]) {
                    val iou = calculateIoU(current.boundingBox, sorted[j].boundingBox)
                    if (iou > iouThreshold) {
                        active[j] = false
                    }
                }
            }
        }
        return result
    }
}
