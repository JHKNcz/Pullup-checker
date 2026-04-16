package com.example.pullupchecker

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.Editable
import android.text.TextWatcher
import android.widget.Toast
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.ImageProxy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.example.pullupchecker.analysis.AnalysisConfig
import com.example.pullupchecker.analysis.ThresholdProfile
import com.example.pullupchecker.camera.CameraPipeline
import com.example.pullupchecker.databinding.ActivityMainBinding
import com.example.pullupchecker.diagnostics.AppLogger
import com.example.pullupchecker.ml.EngineState
import com.example.pullupchecker.ml.PoseLandmarkerEngine
import com.example.pullupchecker.storage.SessionStore
import com.example.pullupchecker.storage.SessionSummary
import com.example.pullupchecker.ui.MetricsFormatter
import com.example.pullupchecker.ui.ScreenState
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {

    private lateinit var viewBinding: ActivityMainBinding
    private lateinit var cameraExecutor: ExecutorService
    private val poseAnalyzer = PoseAnalyzer(
        AnalysisConfig(profile = ThresholdProfile.BALANCED)
    )
    private val poseEngine = PoseLandmarkerEngine()
    private lateinit var cameraPipeline: CameraPipeline
    private lateinit var sessionStore: SessionStore
    private var screenState = ScreenState()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        viewBinding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(viewBinding.root)
        sessionStore = SessionStore(this)
        cameraPipeline = CameraPipeline(this) { ProcessCameraProvider.getInstance(this) }
        cameraExecutor = Executors.newSingleThreadExecutor()
        setupPoseLandmarker()

        if (allPermissionsGranted()) {
            startCamera()
        } else {
            requestPermissions()
        }

        // Setup weight input listener
        setupWeightInput()

        viewBinding.recordButton.text = "Reset session"
        viewBinding.recordButton.setOnClickListener {
            poseAnalyzer.reset()
            viewBinding.sessionHistoryText.text = "Session reset"
            Toast.makeText(this, "Session reset", Toast.LENGTH_SHORT).show()
        }
        viewBinding.recordButton.setOnLongClickListener {
            screenState = screenState.copy(diagnosticsEnabled = !screenState.diagnosticsEnabled)
            renderDiagnosticsState()
            true
        }
        renderSessionHistory()
    }

    private fun setupWeightInput() {
        viewBinding.weightInput.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                val weightText = s?.toString() ?: "75"
                val weight = weightText.toDoubleOrNull() ?: 75.0
                poseAnalyzer.setUserWeight(weight)
            }
        })
    }

    private fun setupPoseLandmarker() {
        poseEngine.initialize(
            context = this,
            onResult = { result ->
                runOnUiThread {
                    result.landmarks().firstOrNull()?.let { rawLandmarks ->
                        val landmarks = poseAnalyzer.correctLandmarks(rawLandmarks)
                        val analysisResult = poseAnalyzer.analyze(landmarks)

                        viewBinding.overlay.setResults(
                            landmarks,
                            analysisResult.elbowAngle,
                            analysisResult.exerciseType
                        )
                        viewBinding.overlay.updateStatus(analysisResult.currentStatus)

                        viewBinding.metricsText.text = MetricsFormatter.formatMetrics(analysisResult)
                        viewBinding.metricsText.textSize = 18f
                        viewBinding.metricsText.setTextColor(analysisResult.currentStatus.color)

                        viewBinding.powerText.text = "Power %.0f W".format(analysisResult.currentPowerWatts)
                        viewBinding.powerHpText.text = "HP %.2f".format(analysisResult.currentPowerHP)
                        viewBinding.peakPowerText.text = "Peak %.0f W | Vel %.2f".format(
                            analysisResult.peakPowerWatts,
                            analysisResult.velocity
                        )

                        val powerColor = when {
                            analysisResult.currentPowerWatts > 400 -> 0xFFFF0000.toInt()
                            analysisResult.currentPowerWatts > 200 -> 0xFFFFAA00.toInt()
                            analysisResult.currentPowerWatts > 50 -> 0xFF00FF00.toInt()
                            else -> 0xFFAAAAAA.toInt()
                        }
                        viewBinding.powerText.setTextColor(powerColor)
                        viewBinding.sessionHistoryText.text = buildCurrentRepHistoryText()
                    } ?: run {
                        viewBinding.overlay.clear()
                        viewBinding.statusText.text = "No pose detected"
                    }
                }
            }
        ) { errorMessage ->
            AppLogger.model("MediaPipe engine failed: $errorMessage")
            runOnUiThread {
                updateEngineState(EngineState.FAILED)
                viewBinding.statusText.text = "Model error: $errorMessage"
                Toast.makeText(this, "Model initialization failed", Toast.LENGTH_LONG).show()
            }
        }
        updateEngineState(poseEngine.state)
    }

    private fun startCamera() {
        cameraPipeline.bind(
            previewView = viewBinding.viewFinder,
            executor = cameraExecutor,
            frameListener = { imageProxy -> processImage(imageProxy) },
            onError = { error ->
                AppLogger.camera("Camera binding failed", error)
                runOnUiThread {
                    viewBinding.statusText.text = "Camera bind failed"
                }
            }
        )
    }

    private fun processImage(imageProxy: ImageProxy) {
        try {
            if (poseEngine.state != EngineState.READY) {
                imageProxy.close()
                return
            }
            val bitmap = imageProxy.toBitmap()
            poseEngine.detect(bitmap, System.currentTimeMillis())
        } catch (t: Throwable) {
            AppLogger.analysis("Failed to process image frame", t)
        } finally {
            imageProxy.close()
        }
    }

    private fun requestPermissions() {
        activityResultLauncher.launch(REQUIRED_PERMISSIONS)
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
    }
    
    private val activityResultLauncher =
        registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { permissions ->
            var permissionGranted = true
            permissions.entries.forEach {
                if (it.key in REQUIRED_PERMISSIONS && !it.value)
                    permissionGranted = false
            }
            if (!permissionGranted) {
                Toast.makeText(baseContext, "Permission request denied", Toast.LENGTH_SHORT).show()
                viewBinding.statusText.text = "Permission denied"
            } else {
                startCamera()
            }
        }

    override fun onPause() {
        super.onPause()
        cameraPipeline.unbind()
    }

    override fun onResume() {
        super.onResume()
        if (allPermissionsGranted()) startCamera()
    }

    override fun onDestroy() {
        saveSessionSummary()
        cameraPipeline.unbind()
        super.onDestroy()
        cameraExecutor.shutdown()
        poseEngine.close()
    }

    companion object {
        private const val TAG = "PullupChecker"
        private val REQUIRED_PERMISSIONS =
            mutableListOf (
                Manifest.permission.CAMERA
            ).apply {
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }

    private fun updateEngineState(state: EngineState) {
        screenState = screenState.copy(engineState = state)
        viewBinding.statusText.text = when (state) {
            EngineState.INITIALIZING -> "Initializing model..."
            EngineState.READY -> "Active"
            EngineState.FAILED -> "Degraded: model unavailable"
        }
    }

    private fun buildCurrentRepHistoryText(): String {
        val reps = poseAnalyzer.getRepSummaries().takeLast(5)
        if (reps.isEmpty()) return "Session history: no reps yet"
        return reps.joinToString(
            prefix = "Session history:\n",
            separator = "\n"
        ) { rep ->
            "#${rep.repIndex} ${rep.exerciseType} | Q${rep.qualityScore} | Peak %.0fW".format(rep.peakPowerWatts)
        }
    }

    private fun renderSessionHistory() {
        val history = sessionStore.loadSessionHistory().takeLast(3)
        if (history.isEmpty()) {
            viewBinding.sessionHistoryText.text = "No saved sessions"
            return
        }
        viewBinding.sessionHistoryText.text = history.joinToString(
            prefix = "Recent sessions:\n",
            separator = "\n"
        ) { "Reps ${it.totalReps}, Peak %.0fW".format(it.peakPowerWatts) }
    }

    private fun saveSessionSummary() {
        val summaries = poseAnalyzer.getRepSummaries()
        if (summaries.isEmpty()) return
        sessionStore.saveSessionSummary(
            SessionSummary(
                timestamp = System.currentTimeMillis(),
                totalReps = summaries.size,
                peakPowerWatts = summaries.maxOf { it.peakPowerWatts }
            )
        )
    }

    private fun renderDiagnosticsState() {
        val mode = if (screenState.diagnosticsEnabled) "ON" else "OFF"
        Toast.makeText(this, "Diagnostics $mode", Toast.LENGTH_SHORT).show()
        viewBinding.statusText.text = "${viewBinding.statusText.text} | diag:$mode"
    }
}
