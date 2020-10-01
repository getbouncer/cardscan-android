@file:Suppress("deprecation")
package com.getbouncer.scan.camera.camera1

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.PointF
import android.graphics.Rect
import android.hardware.Camera
import android.hardware.Camera.AutoFocusCallback
import android.hardware.Camera.PreviewCallback
import android.util.DisplayMetrics
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.OnLifecycleEvent
import com.getbouncer.scan.camera.CameraAdapter
import com.getbouncer.scan.camera.CameraErrorListener
import com.getbouncer.scan.camera.nv21ToYuv
import com.getbouncer.scan.camera.rotate
import com.getbouncer.scan.camera.scale
import com.getbouncer.scan.camera.toBitmap
import com.getbouncer.scan.framework.Config
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.lang.ref.WeakReference
import java.util.ArrayList
import java.util.Random
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val ASPECT_TOLERANCE = 0.2

private val MAXIMUM_RESOLUTION = Size(1920, 1080)

/**
 * A [CameraAdapter] that uses android's Camera 1 APIs to show previews and process images.
 */
class Camera1Adapter(
    private val activity: Activity,
    private val previewView: ViewGroup,
    private val minimumResolution: Size,
    private val cameraErrorListener: CameraErrorListener,
    private val coroutineScope: CoroutineScope,
) : CameraAdapter<Bitmap>(), PreviewCallback {

    private var mCamera: Camera? = null
    private var cameraPreview: CameraPreview? = null
    private var mRotation = 0
    private var focusJob: Job? = null
    private var focusPoint = PointF(previewView.width / 2F, previewView.height / 2F)
    private var onCameraAvailableListener: WeakReference<((Camera) -> Unit)?> = WeakReference(null)

    override fun withFlashSupport(task: (Boolean) -> Unit) {
        mCamera?.let {
            task(isFlashSupported(it))
        } ?: run {
            onCameraAvailableListener = WeakReference { cam ->
                task(isFlashSupported(cam))
            }
        }
    }

    private fun isFlashSupported(camera: Camera) =
        camera.parameters?.supportedFlashModes?.contains(Camera.Parameters.FLASH_MODE_TORCH) == true

    override fun setTorchState(on: Boolean) {
        mCamera?.apply {
            val parameters = parameters
            if (on) {
                parameters.flashMode = Camera.Parameters.FLASH_MODE_TORCH
            } else {
                parameters.flashMode = Camera.Parameters.FLASH_MODE_OFF
            }
            setCameraParameters(this, parameters)
            startCameraPreview()
        }
    }

    override fun isTorchOn(): Boolean =
        mCamera?.parameters?.flashMode == Camera.Parameters.FLASH_MODE_TORCH

    override fun setFocus(point: PointF) {
        mCamera?.apply {
            focusPoint = point
            val parameters = parameters
            if (parameters.maxNumFocusAreas > 0) {
                val focusRect = Rect(
                    point.x.toInt() - 10,
                    point.y.toInt() - 10,
                    point.x.toInt() + 10,
                    point.y.toInt() + 10
                )
                val cameraFocusAreas: MutableList<Camera.Area> = ArrayList()
                cameraFocusAreas.add(Camera.Area(focusRect, 1000))
                parameters.focusAreas = cameraFocusAreas
                setCameraParameters(this, parameters)
            }
        }
    }

    override fun onPreviewFrame(bytes: ByteArray?, camera: Camera) {
        val imageWidth = camera.parameters.previewSize.width
        val imageHeight = camera.parameters.previewSize.height
        val scale = max(
            minimumResolution.width.toFloat() / imageWidth,
            minimumResolution.height.toFloat() / imageHeight
        )

        if (bytes != null) {
            try {
                val bitmap = bytes
                    .nv21ToYuv(imageWidth, imageHeight)
                    .toBitmap()
                    .scale(scale)
                    .rotate(mRotation.toFloat())
                sendImageToStream(bitmap)
            } catch (t: Throwable) {
                // ignore errors transforming the image (OOM, etc)
                Log.e(Config.logTag, "Exception caught during camera transform", t)
            } finally {
                camera.addCallbackBuffer(bytes)
            }
        } else {
            camera.addCallbackBuffer(ByteArray((imageWidth * imageHeight * 1.5).roundToInt()))
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    override fun onPause() {
        super.onPause()
        focusJob?.cancel()

        mCamera?.stopPreview()
        mCamera?.setPreviewCallbackWithBuffer(null)
        mCamera?.release()
        mCamera = null

        cameraPreview?.apply { holder.removeCallback(this) }
        cameraPreview = null
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        coroutineScope.launch(Dispatchers.Main) {
            try {
                var camera: Camera? = null
                try {
                    withContext(Dispatchers.IO) {
                        camera = Camera.open()
                    }
                } catch (t: Throwable) {
                    cameraErrorListener.onCameraOpenError(t)
                }
                onCameraOpen(camera)
            } catch (t: Throwable) {
                cameraErrorListener.onCameraOpenError(t)
            }
        }

        // For some devices (especially Samsung), we need to continuously refocus the camera.
        focusJob?.cancel()
        focusJob = coroutineScope.launch {
            while (isActive) {
                delay(5000)
                val variance = Random().nextFloat() - 0.5F
                val originalFocusPoint = focusPoint
                setFocus(
                    PointF(
                        focusPoint.x + variance,
                        focusPoint.y + variance
                    )
                )
                focusPoint = originalFocusPoint
            }
        }
    }

    private fun setCameraParameters(
        camera: Camera,
        parameters: Camera.Parameters
    ) {
        try {
            camera.parameters = parameters
        } catch (t: Throwable) {
            // ignore failure to set camera parameters
        }
    }

    private fun startCameraPreview() {
        coroutineScope.launch(Dispatchers.IO) {
            startCameraPreviewInternal(0, 5, null)
        }
    }

    private suspend fun startCameraPreviewInternal(
        attemptNumber: Int,
        maxAttempts: Int,
        previousThrowable: Throwable?
    ) {
        if (attemptNumber >= maxAttempts) {
            cameraErrorListener.onCameraOpenError(previousThrowable)
            return
        }
        try {
            mCamera?.startPreview()
        } catch (t: Throwable) {
            delay(500)
            startCameraPreviewInternal(attemptNumber + 1, maxAttempts, t)
        }
    }

    private suspend fun onCameraOpen(camera: Camera?) {
        if (camera == null) {
            withContext(Dispatchers.Main) {
                cameraPreview?.apply { holder.removeCallback(this) }
                cameraErrorListener.onCameraOpenError(null)
            }
        } else {
            mCamera = camera
            setCameraDisplayOrientation(activity, Camera.CameraInfo.CAMERA_FACING_BACK)
            setCameraPreviewFrame()
            // Create our Preview view and set it as the content of our activity.
            cameraPreview = CameraPreview(activity, this).apply {
                layoutParams = ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT)
            }
            withContext(Dispatchers.Main) {
                onCameraAvailableListener.get()?.let {
                    it(camera)
                }
                onCameraAvailableListener.clear()

                previewView.removeAllViews()
                previewView.addView(cameraPreview)
            }
        }
    }

    private fun setCameraPreviewFrame() {
        mCamera?.apply {
            val format = ImageFormat.NV21
            val parameters = parameters
            parameters.previewFormat = format
            val displayMetrics = DisplayMetrics()
            activity.windowManager.defaultDisplay.getRealMetrics(displayMetrics)
            val displayWidth = max(displayMetrics.heightPixels, displayMetrics.widthPixels)
            val displayHeight = min(displayMetrics.heightPixels, displayMetrics.widthPixels)
            val height: Int = minimumResolution.height
            val width = displayWidth * height / displayHeight
            getOptimalPreviewSize(parameters.supportedPreviewSizes, width, height)?.apply {
                parameters.setPreviewSize(this.width, this.height)
            }

            setCameraParameters(this, parameters)
        }
    }

    private fun getOptimalPreviewSize(
        sizes: List<Camera.Size>?,
        w: Int,
        h: Int
    ): Camera.Size? {
        val targetRatio = w.toDouble() / h
        if (sizes == null) {
            return null
        }
        var optimalSize: Camera.Size? = null

        // Find the smallest size that fits our tolerance and is at least as big as our target
        // height
        for (size in sizes) {
            val ratio = size.width.toDouble() / size.height
            if (abs(ratio - targetRatio) <= ASPECT_TOLERANCE) {
                if (size.height >= h) {
                    optimalSize = size
                }
            }
        }

        // Find the closest ratio that is still taller than our target height
        if (optimalSize == null) {
            var minDiffRatio = Double.MAX_VALUE
            for (size in sizes) {
                val ratio = size.width.toDouble() / size.height
                val ratioDiff = abs(ratio - targetRatio)
                if (size.height >= h && ratioDiff <= minDiffRatio &&
                    size.height <= MAXIMUM_RESOLUTION.height && size.width <= MAXIMUM_RESOLUTION.width
                ) {
                    optimalSize = size
                    minDiffRatio = ratioDiff
                }
            }
        }
        if (optimalSize == null) {
            // Find the smallest size that is at least as big as our target height
            for (size in sizes) {
                if (size.height >= h) {
                    optimalSize = size
                }
            }
        }

        return optimalSize
    }

    private fun setCameraDisplayOrientation(activity: Activity, cameraId: Int) {
        val camera = mCamera ?: return

        val info = Camera.CameraInfo()
        Camera.getCameraInfo(cameraId, info)
        val rotation = activity.windowManager.defaultDisplay.rotation
        var degrees = 0
        when (rotation) {
            Surface.ROTATION_0 -> degrees = 0
            Surface.ROTATION_90 -> degrees = 90
            Surface.ROTATION_180 -> degrees = 180
            Surface.ROTATION_270 -> degrees = 270
        }
        var result: Int
        if (info.facing == Camera.CameraInfo.CAMERA_FACING_FRONT) {
            result = (info.orientation + degrees) % 360
            result = (360 - result) % 360 // compensate for the mirror
        } else { // back-facing
            result = (info.orientation - degrees + 360) % 360
        }

        try {
            camera.stopPreview()
        } catch (e: java.lang.Exception) {
            // preview was already stopped
        }
        try {
            camera.setDisplayOrientation(result)
        } catch (t: Throwable) {
            cameraErrorListener.onCameraUnsupportedError(t)
        }
        startCameraPreview()
        mRotation = result
    }

    /** A basic Camera preview class  */
    @SuppressLint("ViewConstructor")
    private inner class CameraPreview(
        context: Context,
        private val mPreviewCallback: PreviewCallback
    ) : SurfaceView(context), AutoFocusCallback, SurfaceHolder.Callback {

        private var mHolder: SurfaceHolder = holder

        init {
            mHolder.addCallback(this)
            mCamera?.apply {
                val parameters = parameters
                val focusModes = parameters.supportedFocusModes
                if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_PICTURE
                } else if (focusModes.contains(Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO)) {
                    parameters.focusMode = Camera.Parameters.FOCUS_MODE_CONTINUOUS_VIDEO
                }

                parameters.setRecordingHint(true)
                setCameraParameters(this, parameters)
            }
        }

        override fun onAutoFocus(success: Boolean, camera: Camera) {}

        /**
         * The Surface has been created, now tell the camera where to draw the preview.
         */
        override fun surfaceCreated(holder: SurfaceHolder) {
            try {
                mCamera?.setPreviewDisplay(mHolder)
                mCamera?.setPreviewCallbackWithBuffer(mPreviewCallback)
                startCameraPreview()
            } catch (t: Throwable) {
                cameraErrorListener.onCameraOpenError(t)
            }
        }

        override fun surfaceDestroyed(holder: SurfaceHolder) {
            // empty. Take care of releasing the Camera preview in your activity.
        }

        override fun surfaceChanged(
            holder: SurfaceHolder,
            format: Int,
            w: Int,
            h: Int
        ) {
            // If your preview can change or rotate, take care of those events here.
            // Make sure to stop the preview before resizing or reformatting it.
            if (mHolder.surface == null) {
                // preview surface does not exist
                return
            }

            // stop preview before making changes
            try {
                mCamera?.stopPreview()
            } catch (t: Throwable) {
                // ignore: tried to stop a non-existent preview
            }

            // set preview size and make any resize, rotate or
            // reformatting changes here

            // start preview with new settings
            try {
                mCamera?.setPreviewDisplay(mHolder)
                val bufSize = w * h * ImageFormat.getBitsPerPixel(format) / 8
                for (i in 0..2) {
                    mCamera?.addCallbackBuffer(ByteArray(bufSize))
                }
                mCamera?.setPreviewCallbackWithBuffer(mPreviewCallback)
                startCameraPreview()
            } catch (t: Throwable) {
                cameraErrorListener.onCameraOpenError(t)
            }
        }
    }
}
