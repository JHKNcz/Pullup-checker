package com.example.pullupchecker

import android.graphics.Color
import com.example.pullupchecker.analysis.AnalysisConfig
import com.example.pullupchecker.analysis.LandmarkPreprocessor
import com.example.pullupchecker.analysis.PhaseEstimator
import com.example.pullupchecker.analysis.PhaseFeatures
import com.example.pullupchecker.analysis.PowerEstimator
import com.example.pullupchecker.analysis.PowerSample
import com.example.pullupchecker.analysis.QualityGate
import com.example.pullupchecker.analysis.RepSummary
import com.example.pullupchecker.analysis.ThresholdProfile
import com.example.pullupchecker.diagnostics.AppLogger
import com.google.mediapipe.tasks.components.containers.NormalizedLandmark
import kotlin.math.abs
import kotlin.math.acos
import kotlin.math.atan2
import kotlin.math.sin
import kotlin.math.cos
import kotlin.math.sqrt

// --- ENUMS & DATA CLASSES ---

enum class PullupPhase(val displayName: String) {
    SETUP("Kalibrace"),        // 0
    DEAD_HANG("Visím"),        // 1
    SCAPULA_RETRACT("Lopatky"), // 2
    CONCENTRIC("Nahoru"),      // 3
    PEAK("Vrchol"),            // 4
    ECCENTRIC("Dolů"),         // 5
    RESET("Reset")             // 6
}

enum class ExerciseType(val displayName: String) {
    DETECTING("Zjišťuji..."),
    PULL_UP("Shyb (Nadhmat)"),
    CHIN_UP("Chin-up (Podhmat)"),
    NEUTRAL("Neutrál")
}

enum class FormStatus(val color: Int, val message: String) {
    PERFECT(Color.GREEN, "Perfect"),
    WARNING(Color.YELLOW, "Pozor"),
    BAD(Color.RED, "Chyba"),
    NEUTRAL(Color.GRAY, "...")
}

data class MuscleScore(
    val lats: Int,
    val biceps: Int,
    val traps: Int,
    val core: Int
)

data class AnalysisResult(
    val phase: PullupPhase,
    val exerciseType: ExerciseType,
    val repCount: Int,
    val currentStatus: FormStatus,
    val feedbackMessage: String,
    val elbowAngle: Double,
    val shoulderSymmetryAngle: Double,
    val currentPowerWatts: Double,
    val currentPowerHP: Double,
    val peakPowerWatts: Double,
    val velocity: Double,
    val muscleScore: MuscleScore
)

// --- ANALYZER CLASS ---

class PoseAnalyzer(
    private var config: AnalysisConfig = AnalysisConfig()
) {

    // --- CONFIGURATION ---
    private val ROTATE_COORDINATES = true

    // --- CONSTANTS ---
    private var ELBOW_HANG_THRESHOLD = 160.0
    private var ELBOW_PEAK_THRESHOLD = 75.0
    private var SYMMETRY_BAD_THRESHOLD = 15.0 // Relaxed
    private val GRAVITY = 9.81
    private val HP_TO_WATTS = 745.7
    private val PULL_DISTANCE_METERS = 0.55
    
    // --- STATE ---
    private var currentPhase = PullupPhase.SETUP
    private var exerciseType = ExerciseType.DETECTING
    private var repCount = 0
    
    // Calibration (Learned values)
    private var calibrationSymmetryOffset = 0.0 // Corrects for tilted phone
    private var calibrationComplete = false
    private var calibrationSamples = 0
    private var baselineTorsoLength = 0.0
    private var baselineEarShoulderDist = 0.0
    
    // Smoothers
    private var smoothedElbowAngle = 0.0
    private var smoothedShoulderY = 0.0
    private var smoothedSymmetryVector = 0.0
    
    // Velocity tracking
    private var lastShoulderY = 0.0
    private var lastTimestamp = 0L
    private var currentVelocity = 0.0
    private var smoothedVelocity = 0.0
    private var previousElbowAngle = 0.0
    
    // Power & Rep metrics
    private var userWeightKg = 78.0 
    private var currentPowerWatts = 0.0
    private var peakPowerWatts = 0.0
    private var repPeakPowerWatts = 0.0
    private var currentRepErrors = mutableSetOf<String>()
    private val repSummaries = mutableListOf<RepSummary>()
    private val qualityGate = QualityGate(config)
    private var landmarkPreprocessor = LandmarkPreprocessor(config)
    private var motionConfidence = 0.0f
    private val powerEstimator = PowerEstimator(
        gravity = GRAVITY,
        pullDistanceMeters = PULL_DISTANCE_METERS
    )
    private val phaseEstimator = PhaseEstimator()
    
    // Advanced Heuristics State
    private var detectedElbowFlare = 0.0 // Degrees (Low=Chinup, High=Pullup)

    init {
        applyThresholdProfile(config.profile)
    }
    
    fun reset() {
        AppLogger.analysis(
            "reset() called; clearing session state (repCount=$repCount, peakPower=${"%.1f".format(peakPowerWatts)}W)"
        )
        repCount = 0
        currentPhase = PullupPhase.SETUP
        exerciseType = ExerciseType.DETECTING
        peakPowerWatts = 0.0
        repPeakPowerWatts = 0.0
        currentPowerWatts = 0.0
        currentRepErrors.clear()
        repSummaries.clear()
        qualityGate.reset()
        
        calibrationComplete = false
        calibrationSamples = 0
        calibrationSymmetryOffset = 0.0
        
        smoothedShoulderY = 0.0
        smoothedElbowAngle = 180.0
        motionConfidence = 0.0f
        currentVelocity = 0.0
        smoothedVelocity = 0.0
        previousElbowAngle = 0.0
        lastShoulderY = 0.0
        lastTimestamp = 0L
        powerEstimator.resetSession()
        phaseEstimator.reset()
    }
    
    fun setUserWeight(weight: Double) {
        userWeightKg = weight
    }

    fun setConfig(newConfig: AnalysisConfig) {
        config = newConfig
        applyThresholdProfile(newConfig.profile)
        landmarkPreprocessor = LandmarkPreprocessor(newConfig)
    }

    fun getRepSummaries(): List<RepSummary> = repSummaries.toList()
    fun isCoordinateRotationEnabled(): Boolean = ROTATE_COORDINATES

    fun correctLandmarks(landmarks: List<NormalizedLandmark>): List<NormalizedLandmark> {
        if (!ROTATE_COORDINATES) return landmarks
        return landmarks.map { 
            NormalizedLandmark.create(it.y(), it.x(), it.z(), it.visibility(), it.presence())
        }
    }

    fun analyze(landmarks: List<NormalizedLandmark>): AnalysisResult {
        val preprocessed = landmarkPreprocessor.preprocess(landmarks)
        if (!preprocessed.valid) {
            return neutralResult(preprocessed.reason ?: "Tracking unstable")
        }

        val validLandmarks = preprocessed.landmarks
        val currentTime = System.currentTimeMillis()
        
        // 1. Extract Landmarks
        val ls = validLandmarks[11] // Left Shoulder
        val rs = validLandmarks[12] // Right Shoulder
        val le = validLandmarks[13] // Left Elbow
        val re = validLandmarks[14]
        val lw = validLandmarks[15] // Wrist
        val rw = validLandmarks[16]
        val lh = validLandmarks[23] // Hip
        val lear = validLandmarks[7]
        val rear = validLandmarks[8]

        // 2. Metrics & Smoothing
        val rawElbow = (calculateAngle3Point(ls, le, lw) + calculateAngle3Point(rs, re, rw)) / 2.0
        val elbowBeforeSmoothing = if (previousElbowAngle == 0.0) rawElbow else previousElbowAngle
        smoothedElbowAngle = smooth(smoothedElbowAngle, rawElbow)
        val elbowFlexDelta = (elbowBeforeSmoothing - smoothedElbowAngle).coerceAtLeast(0.0)
        previousElbowAngle = smoothedElbowAngle
        
        val avgShoulderY = (ls.y() + rs.y()) / 2.0
        if (smoothedShoulderY == 0.0) smoothedShoulderY = avgShoulderY
        smoothedShoulderY = smooth(smoothedShoulderY, avgShoulderY)
        
        val currentTorsoLen = distance(ls, lh)
        if (baselineTorsoLength == 0.0 || currentPhase == PullupPhase.SETUP) {
            baselineTorsoLength = currentTorsoLen
        }

        val powerState = powerEstimator.update(
            PowerSample(
                timestampMs = currentTime,
                shoulderY = smoothedShoulderY,
                torsoLength = baselineTorsoLength,
                userWeightKg = userWeightKg
            )
        )
        currentVelocity = powerState.velocityBodyUnitsPerSec
        smoothedVelocity = powerState.smoothedVelocityBodyUnitsPerSec
        currentPowerWatts = powerState.displayPowerWatts
        peakPowerWatts = powerState.peakPowerWatts
        repPeakPowerWatts = powerState.repPeakPowerWatts
        lastTimestamp = currentTime
        lastShoulderY = smoothedShoulderY

        val rawSymmetry = calculateSymmetry(ls, rs)
        if (!calibrationComplete && currentPhase == PullupPhase.SETUP) {
            calibrationSymmetryOffset = (calibrationSymmetryOffset * calibrationSamples + rawSymmetry) / (calibrationSamples + 1)
            calibrationSamples++
            if (calibrationSamples > 30) calibrationComplete = true
        }
        val correctedSymmetry = circularAngleDelta(rawSymmetry, calibrationSymmetryOffset)
        motionConfidence = calculateMotionConfidence(smoothedElbowAngle, currentVelocity, correctedSymmetry)

        if (currentPhase in listOf(PullupPhase.DEAD_HANG, PullupPhase.SCAPULA_RETRACT)) {
             detectedElbowFlare = calculateElbowFlare(ls, rs, le, re)
             exerciseType = if (detectedElbowFlare > 45) ExerciseType.PULL_UP else ExerciseType.CHIN_UP
        }

        val earShoulderDist = (distance(lear, ls) + distance(rear, rs)) / 2.0

        updatePhase(
            elbowAngle = smoothedElbowAngle,
            elbowFlexDelta = elbowFlexDelta,
            velocity = currentVelocity,
            timestampMs = currentTime,
            confidenceOk = motionConfidence >= config.minMotionConfidence,
            earShoulderDist = earShoulderDist,
            allowCommit = qualityGate.shouldCommitFrame(motionConfidence)
        )

        var status = FormStatus.PERFECT
        var feedback = phaseFeedback(currentPhase)

        if ((currentPhase == PullupPhase.CONCENTRIC || currentPhase == PullupPhase.PEAK) 
            && baselineEarShoulderDist > 0 
            && earShoulderDist < baselineEarShoulderDist * 0.85) {
            status = FormStatus.BAD
            feedback = "Ramena dolů!"
            currentRepErrors.add("Shrugging")
        }
        
        // Symmetry Warning
        val shouldEnforceSymmetry = currentPhase in setOf(
            PullupPhase.DEAD_HANG,
            PullupPhase.SCAPULA_RETRACT,
            PullupPhase.CONCENTRIC,
            PullupPhase.PEAK,
            PullupPhase.ECCENTRIC
        )
        if (shouldEnforceSymmetry && abs(correctedSymmetry) > SYMMETRY_BAD_THRESHOLD) {
             status = FormStatus.BAD
             feedback = "Srovnej se!"
        }

        // Muscle Score (Continuous 0-100)
        val muscleScore = calculateContinuousMuscleScore(ls, rs, lw, rw)

        return AnalysisResult(
            phase = currentPhase,
            exerciseType = exerciseType,
            repCount = repCount,
            currentStatus = status,
            feedbackMessage = feedback,
            elbowAngle = smoothedElbowAngle,
            shoulderSymmetryAngle = correctedSymmetry,
            currentPowerWatts = currentPowerWatts,
            currentPowerHP = currentPowerWatts / HP_TO_WATTS,
            peakPowerWatts = peakPowerWatts,
            velocity = currentVelocity,
            muscleScore = muscleScore
        )
    }

    private fun updatePhase(
        elbowAngle: Double,
        elbowFlexDelta: Double,
        velocity: Double,
        timestampMs: Long,
        confidenceOk: Boolean,
        earShoulderDist: Double,
        allowCommit: Boolean
    ) {
        when (currentPhase) {
            PullupPhase.SETUP -> {
                if (elbowAngle > ELBOW_HANG_THRESHOLD) {
                    currentPhase = PullupPhase.DEAD_HANG
                    baselineEarShoulderDist = earShoulderDist
                }
            }
            PullupPhase.DEAD_HANG -> {
                if (elbowAngle < ELBOW_HANG_THRESHOLD - 5) currentPhase = PullupPhase.SCAPULA_RETRACT
            }
            PullupPhase.SCAPULA_RETRACT -> {
                val features = PhaseFeatures(
                    elbowAngle = elbowAngle,
                    elbowFlexDelta = elbowFlexDelta,
                    velocity = velocity,
                    timestampMs = timestampMs,
                    confidenceOk = confidenceOk
                )
                if (phaseEstimator.shouldEnterConcentric(features)) {
                    currentPhase = PullupPhase.CONCENTRIC
                    currentRepErrors.clear()
                    phaseEstimator.onConcentricStarted(timestampMs)
                    powerEstimator.resetRep()
                    repPeakPowerWatts = 0.0
                }
            }
            PullupPhase.CONCENTRIC -> {
                if (elbowAngle < ELBOW_PEAK_THRESHOLD || velocity < 0.1) {
                    if (elbowAngle < 90) currentPhase = PullupPhase.PEAK
                    else if (velocity < -0.1) {
                        currentPhase = PullupPhase.ECCENTRIC
                        currentRepErrors.add("Half-Rep")
                    }
                }
            }
            PullupPhase.PEAK -> {
                if (velocity < -0.2) currentPhase = PullupPhase.ECCENTRIC
            }
            PullupPhase.ECCENTRIC -> {
                if (elbowAngle > ELBOW_HANG_THRESHOLD - 5) {
                    currentPhase = PullupPhase.RESET
                    if (allowCommit && phaseEstimator.canCommitRep(timestampMs) && !currentRepErrors.contains("Half-Rep")) {
                        repCount++
                        repSummaries.add(
                            RepSummary(
                                repIndex = repCount,
                                exerciseType = exerciseType.name,
                                qualityScore = calculateQualityScore(),
                                peakPowerWatts = repPeakPowerWatts,
                                errors = currentRepErrors.toList()
                            )
                        )
                    }
                }
            }
            PullupPhase.RESET -> {
                currentPhase = PullupPhase.DEAD_HANG
            }
        }
    }

    private fun phaseFeedback(phase: PullupPhase): String = when (phase) {
        PullupPhase.SETUP -> "Kalibrace..." // Changed
        PullupPhase.DEAD_HANG -> "Visím"
        PullupPhase.SCAPULA_RETRACT -> "Lopatky"
        PullupPhase.CONCENTRIC -> "Táhni!"
        PullupPhase.PEAK -> "Drž!"
        PullupPhase.ECCENTRIC -> "Dolů"
        PullupPhase.RESET -> "Hotovo"
    }

    /**
     * Estimates "Elbow Flare" geometry:
     * Does the elbow point OUT (Pullup) or FORWARD (Chinup)?
     * Uses vector projection on torso width.
     */
    private fun calculateElbowFlare(ls: NormalizedLandmark, rs: NormalizedLandmark, le: NormalizedLandmark, re: NormalizedLandmark): Double {
        // Simple heuristic: Width of elbows vs Width of shoulders
        val shoulderWidth = distance(ls, rs)
        val elbowWidth = distance(le, re)
        
        if (shoulderWidth == 0.0) return 45.0
        
        // Ratio: 
        // If Elbows are wider than shoulders -> Flare is high (~90)
        // If Elbows are same width as shoulders -> Flare is low (~0)
        val ratio = elbowWidth / shoulderWidth
        
        // Map ratio to degrees roughly
        // Ratio 2.0 -> 90 deg
        // Ratio 1.0 -> 0 deg
        var flare = (ratio - 1.0) * 90.0
        return flare.coerceIn(0.0, 90.0)
    }

    private fun calculateContinuousMuscleScore(ls: NormalizedLandmark, rs: NormalizedLandmark, lw: NormalizedLandmark, rw: NormalizedLandmark): MuscleScore {
        val shoulderWidth = distance(ls, rs)
        val wristWidth = distance(lw, rw)
        val gripRatio = if (shoulderWidth > 0) wristWidth / shoulderWidth else 1.0
        
        // Lats love Wide Grip & High Flare
        // Biceps love Narrow Grip & Low Flare
        
        // Normalize Grip: 0.5 (Narrow) to 2.0 (Wide)
        val gripFactor = (gripRatio - 0.5) / 1.5 // 0.0 to 1.0
        
        val latsScore = (gripFactor * 100).coerceIn(0.0, 100.0)
        val bicepsScore = ((1.0 - gripFactor) * 100).coerceIn(0.0, 100.0)
        
        // Penalties
        var trapsScore = 10
        if (currentRepErrors.contains("Shrugging")) trapsScore = 80
        
        var coreScore = 95
        if (currentRepErrors.contains("Kipping")) coreScore = 40
        
        return MuscleScore(latsScore.toInt(), bicepsScore.toInt(), trapsScore, coreScore)
    }

    private fun smooth(prev: Double, curr: Double): Double = 0.3 * curr + 0.7 * prev

    private fun calculateQualityScore(): Int {
        var score = 100
        if (currentRepErrors.contains("Half-Rep")) score -= 35
        if (currentRepErrors.contains("Shrugging")) score -= 20
        if (currentRepErrors.contains("Kipping")) score -= 25
        return score.coerceIn(0, 100)
    }

    private fun calculateMotionConfidence(elbowAngle: Double, velocity: Double, symmetry: Double): Float {
        val elbowComponent = ((180.0 - elbowAngle).coerceIn(0.0, 120.0) / 120.0)
        val velocityComponent = (abs(velocity).coerceIn(0.0, 0.9) / 0.9)
        val symmetryPenalty = (abs(symmetry).coerceIn(0.0, 20.0) / 20.0)
        return (0.45 * elbowComponent + 0.45 * velocityComponent + 0.10 * (1.0 - symmetryPenalty)).toFloat()
    }

    private fun applyThresholdProfile(profile: ThresholdProfile) {
        when (profile) {
            ThresholdProfile.STRICT -> {
                ELBOW_HANG_THRESHOLD = 165.0
                ELBOW_PEAK_THRESHOLD = 70.0
                SYMMETRY_BAD_THRESHOLD = 10.0
            }
            ThresholdProfile.BALANCED -> {
                ELBOW_HANG_THRESHOLD = 160.0
                ELBOW_PEAK_THRESHOLD = 75.0
                SYMMETRY_BAD_THRESHOLD = 15.0
            }
            ThresholdProfile.PERMISSIVE -> {
                ELBOW_HANG_THRESHOLD = 155.0
                ELBOW_PEAK_THRESHOLD = 85.0
                SYMMETRY_BAD_THRESHOLD = 20.0
            }
        }
    }
    
    private fun calculateAngle3Point(a: NormalizedLandmark, b: NormalizedLandmark, c: NormalizedLandmark): Double {
        val ab0 = a.x() - b.x()
        val ab1 = a.y() - b.y()
        val cb0 = c.x() - b.x()
        val cb1 = c.y() - b.y()
        val dot = ab0 * cb0 + ab1 * cb1
        val magAb = sqrt(ab0*ab0 + ab1*ab1)
        val magCb = sqrt(cb0*cb0 + cb1*cb1)
        if (magAb * magCb == 0.0f) return 180.0
        return Math.toDegrees(acos((dot / (magAb * magCb)).toDouble().coerceIn(-1.0, 1.0)))
    }
    
    private fun calculateSymmetry(a: NormalizedLandmark, b: NormalizedLandmark): Double {
        val dx = b.x() - a.x()
        val dy = b.y() - a.y()
        return Math.toDegrees(atan2(dy.toDouble(), dx.toDouble()))
    }

    private fun circularAngleDelta(angle: Double, reference: Double): Double {
        val a = Math.toRadians(angle)
        val b = Math.toRadians(reference)
        val delta = atan2(sin(a - b), cos(a - b))
        return Math.toDegrees(delta)
    }
    
    private fun distance(a: NormalizedLandmark, b: NormalizedLandmark): Double {
        val dx = a.x() - b.x()
        val dy = a.y() - b.y()
        return sqrt((dx*dx + dy*dy).toDouble())
    }

    private fun neutralResult(message: String): AnalysisResult {
        return AnalysisResult(
            phase = currentPhase,
            exerciseType = exerciseType,
            repCount = repCount,
            currentStatus = FormStatus.NEUTRAL,
            feedbackMessage = message,
            elbowAngle = smoothedElbowAngle,
            shoulderSymmetryAngle = 0.0,
            currentPowerWatts = 0.0,
            currentPowerHP = 0.0,
            peakPowerWatts = peakPowerWatts,
            velocity = 0.0,
            muscleScore = MuscleScore(0, 0, 0, 0)
        )
    }

}
