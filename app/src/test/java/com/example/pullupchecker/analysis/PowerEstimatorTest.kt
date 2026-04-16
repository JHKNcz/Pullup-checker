package com.example.pullupchecker.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class PowerEstimatorTest {

    @Test
    fun shouldComputePowerFromSmoothedVelocityWindow() {
        val estimator = PowerEstimator(
            gravity = 10.0,
            pullDistanceMeters = 0.55,
            displayWindowSize = 3
        )

        estimator.update(sample(timestampMs = 1000, shoulderY = 0.50))
        val first = estimator.update(sample(timestampMs = 1020, shoulderY = 0.49))
        val second = estimator.update(sample(timestampMs = 1040, shoulderY = 0.47))

        assertEquals(66.0, first.displayPowerWatts, 0.1)
        assertEquals(140.8, second.displayPowerWatts, 0.1)
        assertEquals(140.8, second.repPeakPowerWatts, 0.1)
        assertEquals(140.8, second.peakPowerWatts, 0.1)
    }

    @Test
    fun shouldNotLeakPreviousRepSamplesIntoCurrentRepPeak() {
        val estimator = PowerEstimator(
            gravity = 10.0,
            pullDistanceMeters = 0.55,
            displayWindowSize = 5
        )

        estimator.update(sample(timestampMs = 1000, shoulderY = 0.50))
        estimator.update(sample(timestampMs = 1020, shoulderY = 0.46))
        val repOneEnd = estimator.update(sample(timestampMs = 1040, shoulderY = 0.40))
        val repOnePeak = repOneEnd.repPeakPowerWatts
        assertTrue(repOnePeak > 0.0)

        estimator.resetRep()

        estimator.update(sample(timestampMs = 1060, shoulderY = 0.399))
        val repTwoEnd = estimator.update(sample(timestampMs = 1080, shoulderY = 0.398))

        assertTrue(repTwoEnd.repPeakPowerWatts < repOnePeak)
        assertTrue(repTwoEnd.repPeakPowerWatts in 0.0..repTwoEnd.peakPowerWatts)
    }

    private fun sample(timestampMs: Long, shoulderY: Double): PowerSample =
        PowerSample(
            timestampMs = timestampMs,
            shoulderY = shoulderY,
            torsoLength = 1.0,
            userWeightKg = 80.0
        )
}
