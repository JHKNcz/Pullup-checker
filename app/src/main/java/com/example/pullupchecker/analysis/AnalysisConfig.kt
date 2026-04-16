package com.example.pullupchecker.analysis

enum class ThresholdProfile {
    STRICT,
    BALANCED,
    PERMISSIVE
}

data class AnalysisConfig(
    val profile: ThresholdProfile = ThresholdProfile.BALANCED,
    val minVisibilityConfidence: Float = 0.5f,
    val minMotionConfidence: Float = 0.4f,
    val minStableFramesForRepCommit: Int = 3
)

data class RepSummary(
    val repIndex: Int,
    val exerciseType: String,
    val qualityScore: Int,
    val peakPowerWatts: Double,
    val errors: List<String>
)
