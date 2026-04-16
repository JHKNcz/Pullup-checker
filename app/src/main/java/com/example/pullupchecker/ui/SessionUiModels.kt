package com.example.pullupchecker.ui

import com.example.pullupchecker.analysis.RepSummary
import com.example.pullupchecker.ml.EngineState

data class ScreenState(
    val engineState: EngineState = EngineState.INITIALIZING,
    val diagnosticsEnabled: Boolean = false,
    val repHistory: List<RepSummary> = emptyList()
)
