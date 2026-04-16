package com.example.pullupchecker.ui

import com.example.pullupchecker.AnalysisResult

object MetricsFormatter {
    fun formatMetrics(result: AnalysisResult): String {
        val phaseName = result.phase.displayName
        val muscleScore = result.muscleScore
        val typeName = result.exerciseType.displayName

        return """
            # REPS: ${result.repCount}

            $typeName
            Phase: $phaseName
            Feedback: ${result.feedbackMessage}

            Elbow: %.0f deg | Tilt: %.1f deg
            Lats: ${muscleScore.lats}%% | Biceps: ${muscleScore.biceps}%%
        """.trimIndent().format(result.elbowAngle, result.shoulderSymmetryAngle)
    }
}
