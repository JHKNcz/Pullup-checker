package com.example.pullupchecker.analysis

import kotlin.math.abs
import kotlin.math.roundToInt

data class PowerSample(
    val timestampMs: Long,
    val shoulderY: Double,
    val torsoLength: Double,
    val userWeightKg: Double
)

data class PowerState(
    val velocityBodyUnitsPerSec: Double,
    val smoothedVelocityBodyUnitsPerSec: Double,
    val currentPowerWatts: Double,
    val displayPowerWatts: Double,
    val peakPowerWatts: Double,
    val repPeakPowerWatts: Double
)

class PowerEstimator(
    private val gravity: Double = 9.81,
    private val pullDistanceMeters: Double = 0.55,
    private val minFrameDeltaSec: Double = 0.008,
    private val maxFrameDeltaSec: Double = 0.120,
    private val maxVerticalSpeedMps: Double = 2.5,
    private val displayWindowSize: Int = 8
) {
    private val maxBodyUnitsPerSec = maxVerticalSpeedMps / pullDistanceMeters
    private val displaySamples = ArrayDeque<Double>()

    private var lastTimestampMs = 0L
    private var lastShoulderY = 0.0
    private var smoothedVelocity = 0.0
    private var currentVelocity = 0.0
    private var currentPower = 0.0
    private var peakPower = 0.0
    private var repPeakPower = 0.0

    fun resetSession() {
        lastTimestampMs = 0L
        lastShoulderY = 0.0
        smoothedVelocity = 0.0
        currentVelocity = 0.0
        currentPower = 0.0
        peakPower = 0.0
        repPeakPower = 0.0
        displaySamples.clear()
    }

    fun resetRep() {
        repPeakPower = 0.0
        displaySamples.clear()
    }

    fun update(sample: PowerSample): PowerState {
        if (lastTimestampMs == 0L) {
            lastTimestampMs = sample.timestampMs
            lastShoulderY = sample.shoulderY
            return buildState()
        }

        val deltaSec = (sample.timestampMs - lastTimestampMs) / 1000.0
        if (sample.torsoLength <= 0.001 || deltaSec !in minFrameDeltaSec..maxFrameDeltaSec) {
            invalidateCurrentSample(sample)
            return buildState()
        }

        val deltaYNorm = lastShoulderY - sample.shoulderY
        val rawVelocity = (deltaYNorm / sample.torsoLength) / deltaSec

        if (!rawVelocity.isFinite() || abs(rawVelocity) > maxBodyUnitsPerSec) {
            invalidateCurrentSample(sample)
            return buildState()
        }

        smoothedVelocity = smooth(smoothedVelocity, rawVelocity)
        currentVelocity = smoothedVelocity
        currentPower = if (currentVelocity > 0.08) {
            val verticalSpeed = (currentVelocity * pullDistanceMeters).coerceAtMost(maxVerticalSpeedMps)
            (sample.userWeightKg * gravity * verticalSpeed).coerceAtLeast(0.0)
        } else {
            0.0
        }

        pushDisplaySample(currentPower)
        val display = weightedMovingAverage()
        if (display > peakPower) peakPower = display
        if (display > repPeakPower) repPeakPower = display

        lastTimestampMs = sample.timestampMs
        lastShoulderY = sample.shoulderY
        return buildState()
    }

    private fun invalidateCurrentSample(sample: PowerSample) {
        currentVelocity = 0.0
        smoothedVelocity = 0.0
        currentPower = 0.0
        pushDisplaySample(0.0)
        lastTimestampMs = sample.timestampMs
        lastShoulderY = sample.shoulderY
    }

    private fun buildState(): PowerState {
        val display = weightedMovingAverage()
        return PowerState(
            velocityBodyUnitsPerSec = currentVelocity,
            smoothedVelocityBodyUnitsPerSec = smoothedVelocity,
            currentPowerWatts = round1(currentPower),
            displayPowerWatts = round1(display),
            peakPowerWatts = round1(peakPower),
            repPeakPowerWatts = round1(repPeakPower)
        )
    }

    private fun smooth(prev: Double, curr: Double): Double = 0.3 * curr + 0.7 * prev

    private fun pushDisplaySample(sample: Double) {
        displaySamples.addLast(sample)
        while (displaySamples.size > displayWindowSize) {
            displaySamples.removeFirst()
        }
    }

    private fun weightedMovingAverage(): Double {
        if (displaySamples.isEmpty()) return 0.0
        var weightedSum = 0.0
        var weightTotal = 0.0
        var idx = 1
        for (sample in displaySamples) {
            val weight = idx.toDouble()
            weightedSum += sample * weight
            weightTotal += weight
            idx++
        }
        return if (weightTotal > 0.0) weightedSum / weightTotal else 0.0
    }

    private fun round1(value: Double): Double = (value * 10.0).roundToInt() / 10.0
}
