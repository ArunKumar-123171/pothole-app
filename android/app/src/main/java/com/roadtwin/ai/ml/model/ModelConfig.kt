package com.roadtwin.ai.ml.model

object ModelConfig {
    const val MODEL_NAME = "pothole_detector.tflite"
    const val MODEL_VERSION = "RoadTwin-YOLO26n-416-FP32"

    // Resolution the model was trained/exported with
    const val INPUT_WIDTH = 416
    const val INPUT_HEIGHT = 416

    // End-to-end NMS-free YOLO26 output tensor: [1, 300, 6] = [x1, y1, x2, y2, confidence, classId]
    const val OUTPUT_MAX_BOXES = 300
    const val OUTPUT_BOX_ELEMENTS = 6

    // Detection & Tracking Parameters
    var confidenceThreshold: Float = 0.40f
    var nmsIouThreshold: Float = 0.45f
    var minStableFrames: Int = 3
    var cooldownDistanceMeters: Double = 3.0 // 3m spatial radius
    var cooldownTimeMs: Long = 3000L // 3s temporal cooldown
    const val MAX_DETECTIONS = 10

    val CLASS_NAMES = listOf("pothole")
    const val LETTERBOX_FILL = 114

    // Documented Research Experiment Evaluation Metrics
    const val BENCHMARK_PRECISION = "80.44%"
    const val BENCHMARK_RECALL = "69.72%"
    const val BENCHMARK_MAP_50 = "77.66%"
    const val BENCHMARK_MAP_50_95 = "46.08%"
    const val MODEL_PARAMS = "2.375M"
    const val MODEL_GFLOPS = "~5.3 GFLOPs"
    const val MODEL_PRECISION_TYPE = "FP32"
}
