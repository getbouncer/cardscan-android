package com.getbouncer.scan.camera.extension

import android.app.Activity
import android.graphics.Bitmap
import android.graphics.PointF
import android.os.Build
import android.os.Handler
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.ViewGroup
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.DisplayOrientedMeteringPointFactory
import androidx.camera.core.FocusMeteringAction
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.core.TorchState
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.getbouncer.scan.camera.CameraAdapter
import com.getbouncer.scan.camera.CameraErrorListener
import com.getbouncer.scan.camera.CameraPreviewImage
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.framework.image.getRenderScript
import com.getbouncer.scan.framework.image.rotate
import com.getbouncer.scan.framework.image.size
import com.getbouncer.scan.framework.util.aspectRatio
import com.getbouncer.scan.framework.util.centerOn
import com.getbouncer.scan.framework.util.minAspectRatioSurroundingSize
import com.getbouncer.scan.framework.util.size
import com.getbouncer.scan.framework.util.toRect
import java.util.concurrent.Executor
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

internal class CameraAdapterImpl(
    private val activity: Activity,
    private val previewView: ViewGroup,
    private val minimumResolution: Size,
    private val cameraErrorListener: CameraErrorListener,
) : CameraAdapter<CameraPreviewImage<Bitmap>>() {

    override val implementationName: String = "CameraX"

    private var lensFacing: Int = CameraSelector.LENS_FACING_BACK

    private val mainThreadHandler = Handler(activity.mainLooper)

    private var preview: Preview? = null
    private var imageAnalyzer: ImageAnalysis? = null
    private var camera: Camera? = null
    private val cameraProviderFuture = ProcessCameraProvider.getInstance(activity)
    private lateinit var lifecycleOwner: LifecycleOwner

    private val cameraListeners = mutableListOf<(Camera) -> Unit>()

    /** Blocking camera operations are performed using this executor */
    private lateinit var cameraExecutor: ExecutorService

    private val display by lazy {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            activity.display
        } else {
            null
        } ?: @Suppress("Deprecation") activity.windowManager.defaultDisplay
    }

    private val displayRotation by lazy { display.rotation }
    private val displayMetrics by lazy { DisplayMetrics().also { display.getRealMetrics(it) } }
    private val displaySize by lazy { Size(displayMetrics.widthPixels, displayMetrics.heightPixels) }

    private val previewTextureView by lazy { PreviewView(activity) }

    override fun withFlashSupport(task: (Boolean) -> Unit) {
        withCamera { task(it.cameraInfo.hasFlashUnit()) }
    }

    override fun setTorchState(on: Boolean) {
        camera?.cameraControl?.enableTorch(on)
    }

    override fun isTorchOn(): Boolean =
        camera?.cameraInfo?.torchState?.value == TorchState.ON

    override fun withSupportsMultipleCameras(task: (Boolean) -> Unit) {
        withCameraProvider {
            task(hasBackCamera(it) && hasFrontCamera(it))
        }
    }

    override fun changeCamera() {
        withCameraProvider {
            lensFacing = when {
                lensFacing == CameraSelector.LENS_FACING_BACK && hasFrontCamera(it) -> CameraSelector.LENS_FACING_FRONT
                lensFacing == CameraSelector.LENS_FACING_FRONT && hasBackCamera(it) -> CameraSelector.LENS_FACING_BACK
                hasBackCamera(it) -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera(it) -> CameraSelector.LENS_FACING_FRONT
                else -> CameraSelector.LENS_FACING_BACK
            }

            bindCameraUseCases(it)
        }
    }

    override fun getCurrentCamera(): Int = lensFacing

    override fun setFocus(point: PointF) {
        camera?.let { cam ->
            val meteringPointFactory = DisplayOrientedMeteringPointFactory(
                display,
                cam.cameraInfo,
                displaySize.width.toFloat(),
                displaySize.height.toFloat(),
            )
            val action = FocusMeteringAction.Builder(meteringPointFactory.createPoint(point.x, point.y)).build()
            cam.cameraControl.startFocusAndMetering(action)
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        // Initialize our background executor
        cameraExecutor = Executors.newSingleThreadExecutor()

        previewView.post {
            previewView.removeAllViews()
            previewView.addView(previewTextureView)

            previewTextureView.layoutParams.apply {
                width = ViewGroup.LayoutParams.MATCH_PARENT
                height = ViewGroup.LayoutParams.MATCH_PARENT
            }

            previewTextureView.requestLayout()

            setUpCamera()
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroy() {
        withCameraProvider {
            it.unbindAll()
            cameraExecutor.shutdown()
        }
    }

    override fun unbindFromLifecycle(lifecycleOwner: LifecycleOwner) {
        super.unbindFromLifecycle(lifecycleOwner)
        withCameraProvider { cameraProvider ->
            preview?.let { preview ->
                cameraProvider.unbind(preview)
            }
        }
    }

    private fun setUpCamera() {
        withCameraProvider {
            lensFacing = when {
                hasBackCamera(it) -> CameraSelector.LENS_FACING_BACK
                hasFrontCamera(it) -> CameraSelector.LENS_FACING_FRONT
                else -> {
                    mainThreadHandler.post {
                        cameraErrorListener.onCameraUnsupportedError(IllegalStateException("No camera is available"))
                    }
                    CameraSelector.LENS_FACING_BACK
                }
            }

            bindCameraUseCases(it)
        }
    }

    @Synchronized
    private fun bindCameraUseCases(cameraProvider: ProcessCameraProvider) {
        // CameraSelector
        val cameraSelector = CameraSelector.Builder().requireLensFacing(lensFacing).build()

        preview = Preview.Builder()
            .setTargetRotation(displayRotation)
            .setTargetResolution(minimumResolution.resolutionToSize(displaySize))
            .build()

        imageAnalyzer = ImageAnalysis.Builder()
            .setTargetRotation(displayRotation)
            .setTargetResolution(minimumResolution.resolutionToSize(displaySize))
            .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
            .setImageQueueDepth(1)
            .build()
            .also { analysis ->
                analysis.setAnalyzer(
                    cameraExecutor,
                    { image ->
                        val bitmap = image.toBitmap(getRenderScript(activity))
                            .rotate(image.imageInfo.rotationDegrees.toFloat())
                        image.close()
                        sendImageToStream(
                            CameraPreviewImage(
                                TrackedImage(bitmap, Stats.trackRepeatingTask("image_analysis")),
                                minAspectRatioSurroundingSize(previewView.size(), bitmap.size().aspectRatio()).centerOn(displaySize.toRect())
                            )
                        )
                    }
                )
            }

        cameraProvider.unbindAll()

        try {
            val newCamera = cameraProvider.bindToLifecycle(lifecycleOwner, cameraSelector, preview, imageAnalyzer)
            notifyCameraListeners(newCamera)
            camera = newCamera

            preview?.setSurfaceProvider(previewTextureView.surfaceProvider)
        } catch (t: Throwable) {
            Log.e(Config.logTag, "Use case camera binding failed", t)
            mainThreadHandler.post { cameraErrorListener.onCameraOpenError(t) }
        }
    }

    private fun notifyCameraListeners(camera: Camera) {
        val listenerIterator = cameraListeners.iterator()
        while (listenerIterator.hasNext()) {
            listenerIterator.next()(camera)
            listenerIterator.remove()
        }
    }

    @Synchronized
    private fun <T> withCamera(task: (Camera) -> T) {
        val camera = this.camera
        if (camera != null) {
            task(camera)
        } else {
            cameraListeners.add { task(it) }
        }
    }

    override fun bindToLifecycle(lifecycleOwner: LifecycleOwner) {
        super.bindToLifecycle(lifecycleOwner)
        this.lifecycleOwner = lifecycleOwner
    }

    /** Returns true if the device has an available back camera. False otherwise */
    private fun hasBackCamera(cameraProvider: ProcessCameraProvider): Boolean =
        cameraProvider.hasCamera(CameraSelector.DEFAULT_BACK_CAMERA)

    /** Returns true if the device has an available front camera. False otherwise */
    private fun hasFrontCamera(cameraProvider: ProcessCameraProvider): Boolean =
        cameraProvider.hasCamera(CameraSelector.DEFAULT_FRONT_CAMERA)

    /**
     * Run a task with the camera provider.
     */
    private fun withCameraProvider(
        executor: Executor = ContextCompat.getMainExecutor(activity),
        task: (ProcessCameraProvider) -> Unit,
    ) {
        cameraProviderFuture.addListener({ task(cameraProviderFuture.get()) }, executor)
    }
}
