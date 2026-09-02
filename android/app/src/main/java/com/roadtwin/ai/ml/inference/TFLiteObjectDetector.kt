package com.roadtwin.ai.ml.inference

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.android.gms.tasks.Tasks
import com.google.android.gms.tflite.java.TfLite
import com.roadtwin.ai.ml.model.ModelConfig
import com.roadtwin.ai.ml.postprocessing.NMS
import org.tensorflow.lite.InterpreterApi
import java.io.FileInputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.MappedByteBuffer
import java.nio.channels.FileChannel

private const val TAG = "TFLiteObjectDetector"

private data class LetterboxTransform(
    val scale: Float,
    val padX: Float,
    val padY: Float,
    val originalWidth: Int,
    val originalHeight: Int
)

class TFLiteObjectDetector : ObjectDetector {
    private var interpreter: InterpreterApi? = null
    private var isInitialized = false

    var lastInferenceDurationMs: Long = 0L
        private set

    override fun init(context: Context): Boolean {
        try {
            Log.d(TAG, "Initializing Google Play Services TFLite runtime...")
            val initTask = TfLite.initialize(context)
            Tasks.await(initTask)

            val modelBuffer = loadModelFile(context, ModelConfig.MODEL_NAME)

            val options = InterpreterApi.Options().apply {
                setRuntime(InterpreterApi.Options.TfLiteRuntime.FROM_SYSTEM_ONLY)
                setNumThreads(4)
            }

            val activeInterpreter = InterpreterApi.create(modelBuffer, options)
            interpreter = activeInterpreter

            val inputTensor = activeInterpreter.getInputTensor(0)
            val outputTensor = activeInterpreter.getOutputTensor(0)
            if (inputTensor != null && outputTensor != null) {
                val inputShape = inputTensor.shape()
                val outputShape = outputTensor.shape()
                Log.d(TAG, "TFLite Verified Tensors:")
                Log.d(TAG, "  Input  0: shape=${inputShape.contentToString()}, type=${inputTensor.dataType()}")
                Log.d(TAG, "  Output 0: shape=${outputShape.contentToString()}, type=${outputTensor.dataType()}")
            }

            isInitialized = true
            Log.d(TAG, "TFLite runtime initialized: ${ModelConfig.MODEL_NAME} (${ModelConfig.MODEL_VERSION})")
            return true
        } catch (e: IOException) {
            Log.e(TAG, "Error loading model file: ${ModelConfig.MODEL_NAME}", e)
            isInitialized = false
            return false
        } catch (e: Exception) {
            Log.e(TAG, "Unexpected error initializing interpreter", e)
            isInitialized = false
            return false
        }
    }

    private fun loadModelFile(context: Context, modelName: String): MappedByteBuffer {
        val fileDescriptor = context.assets.openFd(modelName)
        val inputStream = FileInputStream(fileDescriptor.fileDescriptor)
        val fileChannel = inputStream.channel
        val startOffset = fileDescriptor.startOffset
        val declaredLength = fileDescriptor.declaredLength
        return fileChannel.map(FileChannel.MapMode.READ_ONLY, startOffset, declaredLength)
    }

    override fun detect(bitmap: Bitmap): List<DetectionResult> {
        val tflite = interpreter
        if (!isInitialized || tflite == null) {
            Log.w(TAG, "Detector not initialized. Skipping inference.")
            return emptyList()
        }

        val startTime = System.currentTimeMillis()

        // 1. Letterbox: resize preserving aspect ratio + pad to 416x416 square
        val transform = computeLetterboxTransform(bitmap.width, bitmap.height)
        val letterboxed = applyLetterbox(bitmap, transform)

        // 2. Preprocess: pixels -> normalized [0,1] floats in NCHW planar order
        val inputBuffer = buildNchwInputBuffer(letterboxed)

        // 3. Output container: [1, 300, 6] = [x1, y1, x2, y2, confidence, classId]
        val output = Array(1) { Array(ModelConfig.OUTPUT_MAX_BOXES) { FloatArray(ModelConfig.OUTPUT_BOX_ELEMENTS) } }

        try {
            tflite.run(inputBuffer, output)
        } catch (e: Exception) {
            Log.e(TAG, "Inference execution failed", e)
            return emptyList()
        }

        lastInferenceDurationMs = System.currentTimeMillis() - startTime

        // 4. Filter by confidence and map boxes back to original coordinate space
        val candidates = mutableListOf<DetectionResult>()
        for (box in output[0]) {
            val confidence = box[4]
            if (confidence < ModelConfig.confidenceThreshold) continue

            val classIdx = box[5].toInt().coerceIn(0, ModelConfig.CLASS_NAMES.lastIndex.coerceAtLeast(0))
            val boundingBox = mapModelBoxToOriginal(box[0], box[1], box[2], box[3], transform)

            candidates.add(
                DetectionResult(
                    boundingBox = boundingBox,
                    confidence = confidence,
                    classIndex = classIdx,
                    label = ModelConfig.CLASS_NAMES.getOrElse(classIdx) { "pothole" }
                )
            )
        }

        // 5. IoU Dedup and limit
        return NMS.performNMS(candidates, ModelConfig.nmsIouThreshold)
            .sortedByDescending { it.confidence }
            .take(ModelConfig.MAX_DETECTIONS)
    }

    private fun computeLetterboxTransform(originalWidth: Int, originalHeight: Int): LetterboxTransform {
        val scale = minOf(
            ModelConfig.INPUT_WIDTH.toFloat() / originalWidth,
            ModelConfig.INPUT_HEIGHT.toFloat() / originalHeight
        )
        val scaledWidth = originalWidth * scale
        val scaledHeight = originalHeight * scale
        val padX = (ModelConfig.INPUT_WIDTH - scaledWidth) / 2f
        val padY = (ModelConfig.INPUT_HEIGHT - scaledHeight) / 2f
        return LetterboxTransform(scale, padX, padY, originalWidth, originalHeight)
    }

    private fun applyLetterbox(bitmap: Bitmap, transform: LetterboxTransform): Bitmap {
        val letterboxed = Bitmap.createBitmap(
            ModelConfig.INPUT_WIDTH,
            ModelConfig.INPUT_HEIGHT,
            Bitmap.Config.ARGB_8888
        )
        val canvas = Canvas(letterboxed)
        canvas.drawColor(
            Color.rgb(ModelConfig.LETTERBOX_FILL, ModelConfig.LETTERBOX_FILL, ModelConfig.LETTERBOX_FILL)
        )
        val scaledWidth = (bitmap.width * transform.scale)
        val scaledHeight = (bitmap.height * transform.scale)
        val scaled = Bitmap.createScaledBitmap(
            bitmap,
            scaledWidth.toInt().coerceAtLeast(1),
            scaledHeight.toInt().coerceAtLeast(1),
            true
        )
        canvas.drawBitmap(scaled, transform.padX, transform.padY, Paint(Paint.FILTER_BITMAP_FLAG))
        return letterboxed
    }

    private fun buildNchwInputBuffer(letterboxed: Bitmap): ByteBuffer {
        val w = ModelConfig.INPUT_WIDTH
        val h = ModelConfig.INPUT_HEIGHT
        val pixels = IntArray(w * h)
        letterboxed.getPixels(pixels, 0, w, 0, 0, w, h)

        val buffer = ByteBuffer.allocateDirect(4 * 3 * w * h).apply { order(ByteOrder.nativeOrder()) }
        for (channelShift in intArrayOf(16, 8, 0)) {
            for (pixel in pixels) {
                val value = ((pixel shr channelShift) and 0xFF) / 255.0f
                buffer.putFloat(value)
            }
        }
        buffer.rewind()
        return buffer
    }

    private fun mapModelBoxToOriginal(
        x1: Float, y1: Float, x2: Float, y2: Float,
        transform: LetterboxTransform
    ): RectF {
        val origX1 = (x1 - transform.padX) / transform.scale
        val origY1 = (y1 - transform.padY) / transform.scale
        val origX2 = (x2 - transform.padX) / transform.scale
        val origY2 = (y2 - transform.padY) / transform.scale

        return RectF(
            (origX1 / transform.originalWidth).coerceIn(0f, 1f),
            (origY1 / transform.originalHeight).coerceIn(0f, 1f),
            (origX2 / transform.originalWidth).coerceIn(0f, 1f),
            (origY2 / transform.originalHeight).coerceIn(0f, 1f)
        )
    }

    override fun close() {
        interpreter?.close()
        interpreter = null
        isInitialized = false
    }
}
