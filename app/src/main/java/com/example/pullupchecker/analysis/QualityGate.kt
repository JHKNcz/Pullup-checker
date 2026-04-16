package com.example.pullupchecker.analysis

class QualityGate(private val config: AnalysisConfig) {
    private var stableFrames = 0

    fun shouldCommitFrame(motionConfidence: Float): Boolean {
        if (motionConfidence >= config.minMotionConfidence) {
            stableFrames++
        } else {
            stableFrames = 0
        }
        return stableFrames >= config.minStableFramesForRepCommit
    }

    fun reset() {
        stableFrames = 0
    }
}
