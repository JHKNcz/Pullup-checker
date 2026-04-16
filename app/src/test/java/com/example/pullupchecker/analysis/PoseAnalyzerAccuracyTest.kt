package com.example.pullupchecker.analysis

import com.example.pullupchecker.PoseAnalyzer
import com.example.pullupchecker.PullupPhase
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class PoseAnalyzerAccuracyTest {

    @Test
    fun shouldNotEnterConcentricFromSingleVelocitySpike() {
        val analyzer = PoseAnalyzer()
        val base = poseFrame()

        analyzer.analyze(base)
        Thread.sleep(20)
        analyzer.analyze(base.withArms(165f))
        Thread.sleep(20)

        val spike = base.withShoulders(y = 0.05f)
        val result = analyzer.analyze(spike)

        assertNotEquals(PullupPhase.CONCENTRIC, result.phase)
    }

    @Test
    fun shouldIgnoreLowVisibilityFramesForRepCommit() {
        val analyzer = PoseAnalyzer(
            AnalysisConfig(minVisibilityConfidence = 0.9f)
        )
        val lowVisibility = poseFrame(visibility = 0.1f)

        repeat(10) { analyzer.analyze(lowVisibility) }
        val result = analyzer.analyze(lowVisibility)

        assertTrue(analyzer.getRepSummaries().isEmpty())
        assertEquals("Tracking unstable", result.feedbackMessage)
    }

    @Test
    fun shouldResetRepPeakBetweenReps() {
        val analyzer = PoseAnalyzer()
        val frame = poseFrame()

        repeat(5) {
            analyzer.analyze(frame.withArms(170f))
            Thread.sleep(15)
            analyzer.analyze(frame.withShoulders(0.1f).withArms(95f))
            Thread.sleep(15)
            analyzer.analyze(frame.withShoulders(0.3f).withArms(170f))
            Thread.sleep(15)
        }

        val reps = analyzer.getRepSummaries()
        assumeTrue(reps.size >= 2)
        assertTrue(reps[1].peakPowerWatts <= reps[0].peakPowerWatts * 1.5)
    }

    @Test
    fun shouldRequireVelocityAndElbowDeltaForConcentricEntry() {
        val analyzer = PoseAnalyzer()
        val frame = poseFrame()

        analyzer.analyze(frame.withArms(170f))
        Thread.sleep(20)
        analyzer.analyze(frame.withArms(170f))
        Thread.sleep(20)

        val noisyUpwardButStraightArms = frame.withShoulders(0.10f).withArms(170f)
        val result = analyzer.analyze(noisyUpwardButStraightArms)

        assertNotEquals(PullupPhase.CONCENTRIC, result.phase)
    }

    @Test
    fun shouldRequireMinimumDurationBeforeRepCommit() {
        val analyzer = PoseAnalyzer()
        val frame = poseFrame()

        analyzer.analyze(frame.withArms(170f))
        Thread.sleep(20)
        analyzer.analyze(frame.withArms(150f))
        Thread.sleep(20)
        analyzer.analyze(frame.withShoulders(0.08f).withArms(95f)) // enter concentric quickly
        Thread.sleep(30) // below min concentric duration window
        analyzer.analyze(frame.withShoulders(0.30f).withArms(170f)) // fast eccentric completion

        assertTrue(analyzer.getRepSummaries().isEmpty())
    }

    private fun poseFrame(visibility: Float = 1f): List<NormalizedLandmark> {
        val lm = MutableList(33) { landmark(0.5f, 0.5f, visibility) }
        lm[11] = landmark(0.42f, 0.26f, visibility) // left shoulder
        lm[12] = landmark(0.58f, 0.26f, visibility) // right shoulder
        lm[13] = landmark(0.38f, 0.38f, visibility) // left elbow
        lm[14] = landmark(0.62f, 0.38f, visibility) // right elbow
        lm[15] = landmark(0.36f, 0.50f, visibility) // left wrist
        lm[16] = landmark(0.64f, 0.50f, visibility) // right wrist
        lm[23] = landmark(0.45f, 0.62f, visibility) // left hip
        lm[24] = landmark(0.55f, 0.62f, visibility) // right hip
        lm[7] = landmark(0.44f, 0.18f, visibility) // left ear
        lm[8] = landmark(0.56f, 0.18f, visibility) // right ear
        return lm
    }

    private fun List<NormalizedLandmark>.withShoulders(y: Float): List<NormalizedLandmark> {
        val copy = toMutableList()
        copy[11] = landmark(copy[11].x(), y, visibilityOf(copy[11]))
        copy[12] = landmark(copy[12].x(), y, visibilityOf(copy[12]))
        return copy
    }

    private fun List<NormalizedLandmark>.withArms(elbowAngleHint: Float): List<NormalizedLandmark> {
        val copy = toMutableList()
        if (elbowAngleHint >= 160f) {
            copy[13] = landmark(0.40f, 0.38f, visibilityOf(copy[13]))
            copy[14] = landmark(0.60f, 0.38f, visibilityOf(copy[14]))
            copy[15] = landmark(0.39f, 0.52f, visibilityOf(copy[15]))
            copy[16] = landmark(0.61f, 0.52f, visibilityOf(copy[16]))
        } else {
            copy[13] = landmark(0.40f, 0.35f, visibilityOf(copy[13]))
            copy[14] = landmark(0.60f, 0.35f, visibilityOf(copy[14]))
            copy[15] = landmark(0.46f, 0.34f, visibilityOf(copy[15]))
            copy[16] = landmark(0.54f, 0.34f, visibilityOf(copy[16]))
        }
        return copy
    }

    private fun landmark(x: Float, y: Float, visibility: Float = 1f): NormalizedLandmark =
        NormalizedLandmark.create(x, y, 0f, java.util.Optional.of(visibility), java.util.Optional.of(1f))

    private fun visibilityOf(landmark: NormalizedLandmark): Float =
        landmark.visibility().orElse(1f)
}

