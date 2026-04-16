package com.example.pullupchecker.analysis

data class PhaseFeatures(
    val elbowAngle: Double,
    val elbowFlexDelta: Double,
    val velocity: Double,
    val timestampMs: Long,
    val confidenceOk: Boolean
)

class PhaseEstimator(
    private val minConsecutiveFrames: Int = 2,
    private val minConcentricDurationMs: Long = 180L
) {
    private var concentricStableFrames = 0
    private var concentricStartedAt = 0L

    fun shouldEnterConcentric(features: PhaseFeatures): Boolean {
        val hasVelocity = features.velocity > 0.2
        val hasElbowFlex = features.elbowFlexDelta > 1.0
        val valid = features.confidenceOk && hasVelocity && hasElbowFlex
        concentricStableFrames = if (valid) concentricStableFrames + 1 else 0
        return concentricStableFrames >= minConsecutiveFrames
    }

    fun onConcentricStarted(timestampMs: Long) {
        concentricStartedAt = timestampMs
        concentricStableFrames = 0
    }

    fun canCommitRep(timestampMs: Long): Boolean {
        if (concentricStartedAt == 0L) return false
        return (timestampMs - concentricStartedAt) >= minConcentricDurationMs
    }

    fun reset() {
        concentricStableFrames = 0
        concentricStartedAt = 0L
    }
}

