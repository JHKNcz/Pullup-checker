package com.example.pullupchecker.analysis

import com.google.mediapipe.tasks.components.containers.NormalizedLandmark

data class PreprocessedFrame(
    val landmarks: List<NormalizedLandmark>,
    val valid: Boolean,
    val reason: String?
)

class LandmarkPreprocessor(
    private val config: AnalysisConfig
) {
    private val criticalIndices = listOf(11, 12, 13, 14, 15, 16, 23, 24)

    fun preprocess(landmarks: List<NormalizedLandmark>): PreprocessedFrame {
        if (landmarks.size < 25) {
            return PreprocessedFrame(landmarks, false, "Tracking unstable")
        }

        val threshold = config.minVisibilityConfidence
        val criticalVisible = criticalIndices.all { idx ->
            landmarks[idx].visibility().orElse(0f) >= threshold
        }

        if (!criticalVisible) {
            return PreprocessedFrame(landmarks, false, "Tracking unstable")
        }

        return PreprocessedFrame(landmarks, true, null)
    }
}

