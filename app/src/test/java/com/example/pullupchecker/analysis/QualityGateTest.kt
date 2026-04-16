package com.example.pullupchecker.analysis

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class QualityGateTest {
    @Test
    fun shouldRequireStableFramesBeforeCommit() {
        val gate = QualityGate(
            AnalysisConfig(
                minMotionConfidence = 0.5f,
                minStableFramesForRepCommit = 3
            )
        )

        assertFalse(gate.shouldCommitFrame(0.2f))
        assertFalse(gate.shouldCommitFrame(0.7f))
        assertFalse(gate.shouldCommitFrame(0.8f))
        assertTrue(gate.shouldCommitFrame(0.9f))
    }

    @Test
    fun shouldResetStabilityOnLowConfidence() {
        val gate = QualityGate(
            AnalysisConfig(
                minMotionConfidence = 0.5f,
                minStableFramesForRepCommit = 2
            )
        )

        assertFalse(gate.shouldCommitFrame(0.7f))
        assertTrue(gate.shouldCommitFrame(0.7f))
        assertFalse(gate.shouldCommitFrame(0.1f))
        assertFalse(gate.shouldCommitFrame(0.7f))
        assertTrue(gate.shouldCommitFrame(0.7f))
    }

    @Test
    fun shouldRemainCommittedWhileConfidenceStaysHigh() {
        val gate = QualityGate(
            AnalysisConfig(
                minMotionConfidence = 0.4f,
                minStableFramesForRepCommit = 2
            )
        )

        assertFalse(gate.shouldCommitFrame(0.5f))
        assertTrue(gate.shouldCommitFrame(0.5f))
        assertTrue(gate.shouldCommitFrame(0.6f))
        assertTrue(gate.shouldCommitFrame(0.8f))
    }
}
