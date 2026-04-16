package com.example.pullupchecker.ui

import android.graphics.Color
import com.example.pullupchecker.AnalysisResult
import com.example.pullupchecker.ExerciseType
import com.example.pullupchecker.FormStatus
import com.example.pullupchecker.MuscleScore
import com.example.pullupchecker.PullupPhase
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class AnalysisUiMapperTest {
    @Test
    fun shouldMapBadSymmetryFeedbackToRedStatusString() {
        val result = AnalysisResult(
            phase = PullupPhase.CONCENTRIC,
            exerciseType = ExerciseType.PULL_UP,
            repCount = 4,
            currentStatus = FormStatus.BAD,
            feedbackMessage = "Srovnej se!",
            elbowAngle = 95.0,
            shoulderSymmetryAngle = 19.2,
            currentPowerWatts = 420.0,
            currentPowerHP = 0.56,
            peakPowerWatts = 610.0,
            velocity = 0.72,
            muscleScore = MuscleScore(78, 22, 10, 92)
        )

        val uiModel = AnalysisUiMapper.map(result)

        assertEquals(Color.RED, uiModel.statusColor)
        assertEquals("[RED] Chyba - Srovnej se!", uiModel.statusText)
        assertEquals("Power: 420.0 W (0.56 hp) | Peak: 610.0 W", uiModel.powerText)
        assertTrue(uiModel.metricsText.contains("Feedback: Srovnej se!"))
    }

    @Test
    fun shouldIncludeColorLabelForWarningStatus() {
        val result = AnalysisResult(
            phase = PullupPhase.ECCENTRIC,
            exerciseType = ExerciseType.CHIN_UP,
            repCount = 2,
            currentStatus = FormStatus.WARNING,
            feedbackMessage = "Pozor na tempo",
            elbowAngle = 122.0,
            shoulderSymmetryAngle = 3.4,
            currentPowerWatts = 175.3,
            currentPowerHP = 0.24,
            peakPowerWatts = 400.0,
            velocity = -0.21,
            muscleScore = MuscleScore(60, 40, 18, 88)
        )

        val uiModel = AnalysisUiMapper.map(result)

        assertEquals(Color.YELLOW, uiModel.statusColor)
        assertEquals("[YELLOW] Pozor - Pozor na tempo", uiModel.statusText)
    }
}
