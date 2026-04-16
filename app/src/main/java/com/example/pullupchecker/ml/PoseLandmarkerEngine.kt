package com.example.pullupchecker.ml

import android.content.Context
import android.util.Log
import com.google.mediapipe.framework.image.BitmapImageBuilder
import com.google.mediapipe.tasks.core.BaseOptions
import com.google.mediapipe.tasks.vision.core.RunningMode
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker
import com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarker.PoseLandmarkerOptions

enum class EngineState {
    INITIALIZING,
    READY,
    FAILED
}

class PoseLandmarkerEngine {
    private var poseLandmarker: PoseLandmarker? = null
    var state: EngineState = EngineState.INITIALIZING
        private set

    fun initialize(
        context: Context,
        onResult: (com.google.mediapipe.tasks.vision.poselandmarker.PoseLandmarkerResult) -> Unit,
        onError: (String) -> Unit
    ) {
        state = EngineState.INITIALIZING
        val options = PoseLandmarkerOptions.builder()
            .setBaseOptions(
                BaseOptions.builder()
                    .setModelAssetPath("pose_landmarker_full.task")
                    .build()
            )
            .setRunningMode(RunningMode.LIVE_STREAM)
            .setResultListener { result, _ -> onResult(result) }
            .setErrorListener { e ->
                state = EngineState.FAILED
                onError(e.message ?: "Unknown MediaPipe error")
            }
            .build()

        try {
            poseLandmarker = PoseLandmarker.createFromOptions(context, options)
            state = EngineState.READY
        } catch (t: Throwable) {
            state = EngineState.FAILED
            onError(t.message ?: "Failed to initialize pose landmarker")
        }
    }

    fun detect(bitmap: android.graphics.Bitmap, timestampMs: Long) {
        if (state != EngineState.READY) return
        val mpImage = BitmapImageBuilder(bitmap).build()
        try {
            poseLandmarker?.detectAsync(mpImage, timestampMs)
        } catch (t: Throwable) {
            Log.e("PoseLandmarkerEngine", "detectAsync failed", t)
            state = EngineState.FAILED
        }
    }

    fun close() {
        try {
            poseLandmarker?.close()
        } finally {
            poseLandmarker = null
        }
    }
}
