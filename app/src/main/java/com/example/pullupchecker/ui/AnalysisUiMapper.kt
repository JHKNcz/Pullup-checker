package com.example.pullupchecker.ui

import com.example.pullupchecker.AnalysisResult
import java.util.Locale

object AnalysisUiMapper {
    fun map(result: AnalysisResult): AnalysisUiModel {
        val metricsText = MetricsFormatter.formatMetrics(result)
        val statusColor = result.currentStatus.color
        val statusText = "[${statusColorLabel(statusColor)}] ${result.currentStatus.message} - ${result.feedbackMessage}"
        val powerText = String.format(
            Locale.US,
            "Power: %.1f W (%.2f hp) | Peak: %.1f W",
            result.currentPowerWatts,
            result.currentPowerHP,
            result.peakPowerWatts
        )

        return AnalysisUiModel(
            metricsText = metricsText,
            statusText = statusText,
            statusColor = statusColor,
            powerText = powerText
        )
    }

    private fun statusColorLabel(color: Int): String = when (color) {
        android.graphics.Color.GREEN -> "GREEN"
        android.graphics.Color.YELLOW -> "YELLOW"
        android.graphics.Color.RED -> "RED"
        else -> "GRAY"
    }
}
