package com.example.pullupchecker.camera

import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.util.concurrent.ExecutorService

class CameraPipeline(
    private val lifecycleOwner: LifecycleOwner,
    private val cameraProviderFutureFactory: () -> com.google.common.util.concurrent.ListenableFuture<ProcessCameraProvider>
) {
    private var isBound = false
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraProvider: ProcessCameraProvider? = null

    fun bind(
        previewView: PreviewView,
        executor: ExecutorService,
        frameListener: (ImageProxy) -> Unit,
        onError: (Throwable) -> Unit
    ) {
        if (isBound) return

        val cameraProviderFuture = cameraProviderFutureFactory()
        cameraProviderFuture.addListener({
            try {
                cameraProvider = cameraProviderFuture.get()
                val provider = cameraProvider ?: return@addListener

                val preview = Preview.Builder().build().also {
                    it.setSurfaceProvider(previewView.surfaceProvider)
                }

                val analysisUseCase = ImageAnalysis.Builder()
                    .setTargetRotation(previewView.display.rotation)
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                    .also { analysis ->
                        analysis.setAnalyzer(executor) { imageProxy ->
                            frameListener(imageProxy)
                        }
                    }
                imageAnalysis = analysisUseCase

                provider.unbindAll()
                provider.bindToLifecycle(
                    lifecycleOwner,
                    CameraSelector.DEFAULT_BACK_CAMERA,
                    preview,
                    analysisUseCase
                )
                isBound = true
            } catch (t: Throwable) {
                onError(t)
            }
        }, ContextCompat.getMainExecutor(previewView.context))
    }

    fun unbind() {
        try {
            imageAnalysis?.clearAnalyzer()
            cameraProvider?.unbindAll()
        } catch (t: Throwable) {
            Log.w("CameraPipeline", "Failed to unbind cleanly", t)
        } finally {
            imageAnalysis = null
            cameraProvider = null
            isBound = false
        }
    }
}
