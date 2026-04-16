package com.example.pullupchecker.ui

import com.example.pullupchecker.AnalysisResult
import com.example.pullupchecker.ExerciseType
import com.example.pullupchecker.FormStatus
import com.example.pullupchecker.MuscleScore
import com.example.pullupchecker.PullupPhase
import org.junit.Assert.assertTrue
import org.junit.Test

class MetricsFormatterTest {
    @Test
    fun shouldIncludePrimaryFields() {
        val formatted = MetricsFormatter.formatMetrics(
            AnalysisResult(
                phase = PullupPhase.CONCENTRIC,
                exerciseType = ExerciseType.PULL_UP,
                repCount = 7,
                currentStatus = FormStatus.PERFECT,
                feedbackMessage = "Good form",
                elbowAngle = 92.0,
                shoulderSymmetryAngle = 2.5,
                currentPowerWatts = 240.0,
                currentPowerHP = 0.32,
                peakPowerWatts = 380.0,
                velocity = 0.62,
                muscleScore = MuscleScore(72, 28, 10, 95)
            )
        )

        assertTrue(formatted.contains("# REPS: 7"))
        assertTrue(formatted.contains("Feedback: Good form"))
        assertTrue(formatted.contains("Lats: 72%"))
    }
}
