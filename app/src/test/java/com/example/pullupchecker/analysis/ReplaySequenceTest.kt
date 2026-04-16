package com.example.pullupchecker.analysis

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ReplaySequenceTest {

    @Test
    fun cleanSequenceShouldYieldExpectedRepCount() {
        val replay = loadReplay("replays/pullup_clean_sequence.json")
        assertEquals(replay.expectedRepCount, replay.observedRepCount)
        assertEquals(0, replay.falsePositiveReps)
    }

    @Test
    fun noisySequenceShouldNotProduceFalseRep() {
        val replay = loadReplay("replays/pullup_noisy_sequence.json")
        assertEquals(0, replay.expectedRepCount)
        assertEquals(0, replay.observedRepCount)
        assertEquals(0, replay.falsePositiveReps)
    }

    private fun loadReplay(resourcePath: String): ReplaySummary {
        val stream = javaClass.classLoader?.getResourceAsStream(resourcePath)
        checkNotNull(stream) { "Missing replay resource: $resourcePath" }
        val json = stream.bufferedReader().use { it.readText() }
        return ReplaySummary(
            expectedRepCount = extractInt(json, "expectedRepCount"),
            observedRepCount = extractInt(json, "observedRepCount"),
            falsePositiveReps = extractInt(json, "falsePositiveReps")
        )
    }

    private fun extractInt(json: String, key: String): Int {
        val regex = "\"$key\"\\s*:\\s*(\\d+)".toRegex()
        val value = regex.find(json)?.groupValues?.get(1)
        assertTrue("Missing key: $key", value != null)
        return value!!.toInt()
    }

    private data class ReplaySummary(
        val expectedRepCount: Int,
        val observedRepCount: Int,
        val falsePositiveReps: Int
    )
}

