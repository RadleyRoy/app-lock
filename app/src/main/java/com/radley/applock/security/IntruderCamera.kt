package com.radley.applock.security

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.util.Log
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import java.io.File
import kotlin.coroutines.resume
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * Takes a single front-camera frame with no preview surface, for the intruder log.
 *
 * Note that Android 14+ shows its green camera indicator whenever this runs. That is enforced
 * by the OS and is not something this app should try to work around — the settings copy says
 * so plainly instead.
 */
class IntruderCamera(private val context: Context) {

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) ==
            PackageManager.PERMISSION_GRANTED

    /** @return true if a photo was written to [outputFile]. */
    suspend fun capture(lifecycleOwner: LifecycleOwner, outputFile: File): Boolean {
        if (!hasPermission()) return false

        return suspendCancellableCoroutine { continuation ->
            val providerFuture = ProcessCameraProvider.getInstance(context)

            providerFuture.addListener({
                val provider = runCatching { providerFuture.get() }.getOrNull()
                if (provider == null) {
                    continuation.resume(false)
                    return@addListener
                }

                val imageCapture = ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .build()

                val bound = runCatching {
                    provider.unbindAll()
                    // No Preview use case: nothing should appear on screen, and binding one
                    // would need a surface the lock screen deliberately does not have.
                    provider.bindToLifecycle(
                        lifecycleOwner,
                        CameraSelector.DEFAULT_FRONT_CAMERA,
                        imageCapture,
                    )
                }.isSuccess

                if (!bound) {
                    continuation.resume(false)
                    return@addListener
                }

                imageCapture.takePicture(
                    ImageCapture.OutputFileOptions.Builder(outputFile).build(),
                    ContextCompat.getMainExecutor(context),
                    object : ImageCapture.OnImageSavedCallback {
                        override fun onImageSaved(results: ImageCapture.OutputFileResults) {
                            runCatching { provider.unbindAll() }
                            if (continuation.isActive) continuation.resume(true)
                        }

                        override fun onError(exception: ImageCaptureException) {
                            Log.w(TAG, "Intruder capture failed", exception)
                            runCatching { provider.unbindAll() }
                            if (continuation.isActive) continuation.resume(false)
                        }
                    },
                )
            }, ContextCompat.getMainExecutor(context))
        }
    }

    private companion object {
        const val TAG = "IntruderCamera"
    }
}
