/*
 * Copyright 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.getbouncer.scan.camera.extension

import android.annotation.SuppressLint
import android.app.Activity
import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.PointF
import android.graphics.Rect
import android.graphics.SurfaceTexture
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCaptureSession
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraDevice
import android.hardware.camera2.CameraManager
import android.hardware.camera2.CameraMetadata
import android.hardware.camera2.CaptureRequest
import android.hardware.camera2.TotalCaptureResult
import android.hardware.camera2.params.MeteringRectangle
import android.hardware.camera2.params.StreamConfigurationMap
import android.media.ImageReader
import android.os.Build
import android.os.Handler
import android.os.HandlerThread
import android.util.Log
import android.util.Size
import android.view.Surface
import android.view.TextureView
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.OnLifecycleEvent
import com.getbouncer.scan.camera.CameraAdapter
import com.getbouncer.scan.camera.CameraErrorListener
import com.getbouncer.scan.camera.CameraPreviewImage
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.framework.image.getRenderScript
import com.getbouncer.scan.framework.image.isSupportedFormat
import com.getbouncer.scan.framework.image.rotate
import com.getbouncer.scan.framework.image.toBitmap
import com.getbouncer.scan.framework.util.scale
import com.getbouncer.scan.framework.util.scaleAndCenterSurrounding
import com.getbouncer.scan.framework.util.size
import com.getbouncer.scan.framework.util.toRectF
import java.util.Locale
import java.util.concurrent.Semaphore
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import kotlin.math.max

/**
 * For tap to focus.
 */
private const val FOCUS_TOUCH_SIZE = 150

/**
 * The default image format. This is not necessarily the fastest to process, but most supported.
 */
const val DEFAULT_IMAGE_FORMAT = ImageFormat.YUV_420_888

/**
 * Unable to open the camera.
 */
@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
class CameraDeviceCallbackOpenException(val cameraId: String, val errorCode: Int) : Exception() {
    override fun toString(): String {
        return "CameraDeviceCallbackOpenException(cameraId='$cameraId', errorCode=$errorCode)"
    }
}

/**
 * Unable to configure the camera.
 */
@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
class CameraConfigurationFailedException(val cameraId: String) : Exception() {
    override fun toString(): String {
        return "CameraConfigurationFailedException(cameraId='$cameraId')"
    }
}

/**
 * A [CameraAdapter] that uses android's Camera 2 APIs to show previews and process images.
 */
internal class CameraAdapterImpl(
    private val activity: Activity,
    private val previewView: ViewGroup,
    private val minimumResolution: Size,
    private val cameraErrorListener: CameraErrorListener,
) : CameraAdapter<CameraPreviewImage<Bitmap>>(), LifecycleObserver {

    override val implementationName: String = "Camera2"

    private val previewTextureView by lazy { TextureView(activity) }

    private val processingImage = AtomicBoolean(false)

    private val displayRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
        activity.display?.rotation
    } else {
        null
    } ?: @Suppress("Deprecation") activity.windowManager.defaultDisplay.rotation

    private lateinit var cameraId: String

    private var previewCaptureSession: CameraCaptureSession? = null

    private var cameraDevice: CameraDevice? = null

    private lateinit var previewSize: Size

    private lateinit var previewResolution: Size

    private var cameraThread: HandlerThread? = null

    private var cameraHandler: Handler? = null

    private var imageReader: ImageReader? = null

    private var sensorRotation = 0

    private val cameraOpenCloseLock = Semaphore(1)

    private var flashSupported = false

    private lateinit var previewRequestBuilder: CaptureRequest.Builder

    private var onInitializedFlashTask: ((Boolean) -> Unit)? = null

    private var autoFocusMode: Int = CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE

    private val mainThreadHandler = Handler(activity.mainLooper)

    private var focusPoint = PointF(previewView.width / 2F, previewView.height / 2F)

    private var currentCameraIndex = -1

    private lateinit var scaledPreviewSize: Rect

    private val previewSurfaceTextureListener = object : TextureView.SurfaceTextureListener {
        override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
            openCamera()
        }

        override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
            configureTransform(Size(width, height), previewSize)
        }

        override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean = true

        override fun onSurfaceTextureUpdated(surface: SurfaceTexture) { }
    }

    private val imageAvailableListener = ImageReader.OnImageAvailableListener { reader ->
        if (!processingImage.compareAndSet(false, true)) {
            return@OnImageAvailableListener
        }

        reader.acquireLatestImage()?.let {
            try {
                it.toBitmap(getRenderScript(activity)).rotate(calculateImageRotationDegrees(displayRotation, sensorRotation).toFloat())
            } catch (t: Throwable) {
                Log.e(Config.logTag, "Unable to convert image to bitmap: $t")
                null
            } finally {
                it.close()
            }
        }?.let {
            sendImageToStream(
                CameraPreviewImage(
                    TrackedImage(it, Stats.trackRepeatingTask("image_analysis")),
                    scaledPreviewSize,
                ),
            )
        }

        processingImage.set(false)
    }

    private val stateCallback = object : CameraDevice.StateCallback() {

        @Synchronized
        override fun onOpened(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            cameraDevice = camera
            createCameraPreviewSession(previewResolution)

            onInitializedFlashTask?.apply {
                mainThreadHandler.post { this(flashSupported) }
            }
        }

        override fun onDisconnected(camera: CameraDevice) {
            cameraOpenCloseLock.release()
            camera.close()
            cameraDevice = null
        }

        override fun onError(camera: CameraDevice, error: Int) {
            onDisconnected(camera)
            mainThreadHandler.post {
                cameraErrorListener.onCameraOpenError(CameraDeviceCallbackOpenException(camera.id, error))
            }
        }
    }

    @Synchronized
    override fun withFlashSupport(task: (Boolean) -> Unit) {
        if (::previewRequestBuilder.isInitialized) {
            mainThreadHandler.post { task(flashSupported) }
        } else {
            onInitializedFlashTask = task
        }
    }

    override fun setTorchState(on: Boolean) {
        if (!::previewRequestBuilder.isInitialized) {
            return
        }

        if (on) {
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_TORCH)
        } else {
            previewRequestBuilder.set(CaptureRequest.FLASH_MODE, CaptureRequest.FLASH_MODE_OFF)
        }
        previewCaptureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)
    }

    override fun isTorchOn() =
        if (::previewRequestBuilder.isInitialized) {
            previewRequestBuilder.get(CaptureRequest.FLASH_MODE) == CaptureRequest.FLASH_MODE_TORCH
        } else {
            false
        }

    @OnLifecycleEvent(Lifecycle.Event.ON_CREATE)
    fun onCreate() {
        previewView.removeAllViews()
        previewView.addView(previewTextureView)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_RESUME)
    fun onResume() {
        startCameraThread()

        if (previewTextureView.isAvailable) {
            openCamera()
        } else {
            previewTextureView.surfaceTextureListener = previewSurfaceTextureListener
        }
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    override fun onPause() {
        super.onPause()
        closeCamera()
        stopCameraThread()
    }

    /**
     * Sets up member variables related to camera.
     */
    private fun setUpCameraOutputs() {
        try {
            getCurrentCameraDetails()?.also { cameraDetails ->
                sensorRotation = cameraDetails.sensorRotation
                autoFocusMode = selectAutoFocusMode(cameraDetails.supportedAutoFocusModes)

                // Given a desired resolution, get a resolution and format that the camera supports.
                val previewFormatAndResolution = getOptimalPreviewResolution(
                    getCameraResolutions(cameraDetails.config),
                    minimumResolution
                )

                // rotate the preview resolution to match the orientation
                val previewFormat = previewFormatAndResolution.first
                previewResolution = previewFormatAndResolution.second
                previewSize = previewResolution.resolutionToSize(
                    displayRotation,
                    cameraDetails.sensorRotation,
                )
                Log.d(Config.logTag, "Camera2 API selected resolution $previewResolution with format $previewFormat")

                imageReader = ImageReader.newInstance(previewResolution.width, previewResolution.height, previewFormat, 1)
                    .apply {
                        setOnImageAvailableListener(imageAvailableListener, cameraHandler)
                    }

                previewTextureView.layoutParams.apply {
                    width = ViewGroup.LayoutParams.MATCH_PARENT
                    height = ViewGroup.LayoutParams.MATCH_PARENT
                }

                previewTextureView.requestLayout()

                // Check if the flash is supported.
                flashSupported = cameraDetails.flashAvailable

                cameraId = cameraDetails.cameraId
            }
        } catch (e: CameraAccessException) {
            mainThreadHandler.post { cameraErrorListener.onCameraAccessError(e) }
        } catch (e: NullPointerException) {
            // Currently an NPE is thrown when the Camera2API is used but not supported on the
            // device this code runs.
            mainThreadHandler.post { cameraErrorListener.onCameraUnsupportedError(e) }
        }
    }

    private fun selectAutoFocusMode(supportedAutoFocusModes: List<Int>): Int =
        when {
            CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE in supportedAutoFocusModes ->
                CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE
            CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO in supportedAutoFocusModes ->
                CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_VIDEO
            else -> CameraCharacteristics.CONTROL_AF_MODE_CONTINUOUS_PICTURE
        }

    private fun getCameraManager() = activity.getSystemService(Context.CAMERA_SERVICE) as CameraManager

    /**
     * Get a list of cameraIds and their configuration maps.
     */
    private val availableCameras: List<CameraDetails> by lazy {
        val manager = getCameraManager()
        manager.cameraIdList
            .map { it to manager.getCameraCharacteristics(it) }
            .mapNotNull {
                it.second.get(CameraCharacteristics.SCALER_STREAM_CONFIGURATION_MAP)?.run {
                    CameraDetails(
                        cameraId = it.first,
                        flashAvailable = it.second.get(CameraCharacteristics.FLASH_INFO_AVAILABLE) == true,
                        config = this,
                        sensorRotation = it.second.get(CameraCharacteristics.SENSOR_ORIENTATION) ?: 0,
                        supportedAutoFocusModes = it.second.get(CameraCharacteristics.CONTROL_AF_AVAILABLE_MODES)?.toList() ?: emptyList(),
                        lensFacing = it.second.get(CameraCharacteristics.LENS_FACING),
                    )
                }
            }
    }

    private val defaultCameraIndex by lazy {
        availableCameras.indexOfFirst {
            it.lensFacing != CameraCharacteristics.LENS_FACING_FRONT
        }
    }

    private fun getCurrentCameraDetails(): CameraDetails? = availableCameras.let {
        if (currentCameraIndex < 0) {
            currentCameraIndex = if (defaultCameraIndex >= 0) defaultCameraIndex else 0
        }

        if (currentCameraIndex < it.size) it[currentCameraIndex] else null
    }

    /**
     * Get a list of all the supported camera resolutions for each format. Output is in the format:
     *
     * ```
     * [
     *   (CameraFormat, Resolution),
     *   (CameraFormat, Resolution),
     *   ...
     * ]
     * ```
     *
     * Note that each format will likely have multiple resolutions. Available formats will be sorted
     * by preference (fastest first).
     */
    private fun getCameraResolutions(map: StreamConfigurationMap): List<Pair<Int, Size>> {
        val formats = map.outputFormats.filter { isSupportedFormat(it) }.sortedBy {
            when (it) {
                ImageFormat.NV21 -> 0
                ImageFormat.YUV_420_888 -> 1
                ImageFormat.JPEG -> 2
                ImageFormat.YUY2 -> 3
                else -> it
            }
        }
        val formatToOutputSizes = formats.map { format ->
            (map.getOutputSizes(format) ?: emptyArray())
                .asIterable()
                .map { format to it }
        }
        return formatToOutputSizes.flatten()
    }

    /**
     * Opens the camera specified by [cameraId].
     */
    @SuppressLint("MissingPermission")
    private fun openCamera() {
        setUpCameraOutputs()

        previewView.apply { configureTransform(size(), previewSize) }

        val manager = getCameraManager()
        try {
            // Wait for camera to open - 2.5 seconds is sufficient
            if (!cameraOpenCloseLock.tryAcquire(2500, TimeUnit.MILLISECONDS)) {
                mainThreadHandler.post { cameraErrorListener.onCameraOpenError(null) }
                return
            }
            manager.openCamera(cameraId, stateCallback, cameraHandler)
        } catch (e: CameraAccessException) {
            mainThreadHandler.post { cameraErrorListener.onCameraAccessError(e) }
        } catch (e: InterruptedException) {
            mainThreadHandler.post { cameraErrorListener.onCameraOpenError(e) }
        }
    }

    /**
     * Closes the current [CameraDevice].
     */
    private fun closeCamera() {
        try {
            cameraOpenCloseLock.acquire()
            previewCaptureSession?.close()
            previewCaptureSession = null
            cameraDevice?.close()
            cameraDevice = null
        } catch (e: InterruptedException) {
            mainThreadHandler.post {
                cameraErrorListener.onCameraOpenError(e)
            }
        } finally {
            cameraOpenCloseLock.release()
        }
    }

    /**
     * Starts a background thread and its [Handler].
     */
    private fun startCameraThread() {
        val thread = HandlerThread("CameraBackground").also { it.start() }
        cameraThread = thread
        cameraHandler = Handler(thread.looper)
    }

    /**
     * Stops the background thread and its [Handler].
     */
    private fun stopCameraThread() {
        cameraThread?.quitSafely()
        try {
            cameraThread?.join()
            cameraThread = null
            cameraHandler = null
        } catch (e: InterruptedException) {
            mainThreadHandler.post { cameraErrorListener.onCameraOpenError(e) }
        }
    }

    /**
     * Creates a new [CameraCaptureSession] for camera preview.
     */
    private fun createCameraPreviewSession(previewResolution: Size) {
        try {
            val previewTexture = previewTextureView.surfaceTexture

            // We configure the size of default buffer to be the size of camera preview we want.
            previewTexture?.setDefaultBufferSize(previewResolution.width, previewResolution.height)

            // This is the output Surface we need to start preview.
            val previewSurface = previewTexture?.let { Surface(it) }
            val imageReaderSurface = imageReader?.surface

            // We set up a CaptureRequest.Builder with the output Surface.
            previewRequestBuilder = cameraDevice!!.createCaptureRequest(
                CameraDevice.TEMPLATE_PREVIEW
            )

            previewSurface?.apply { previewRequestBuilder.addTarget(this) }
            imageReaderSurface?.apply { previewRequestBuilder.addTarget(this) }

            // Here, we create a CameraCaptureSession for camera preview.
            @Suppress("Deprecation") // SessionConfiguration is not available until API 28.
            cameraDevice?.createCaptureSession(
                listOfNotNull(imageReaderSurface, previewSurface),
                object : CameraCaptureSession.StateCallback() {

                    override fun onConfigured(cameraCaptureSession: CameraCaptureSession) {
                        // The camera is already closed
                        if (cameraDevice == null) return

                        // When the session is ready, we start displaying the preview.
                        previewCaptureSession = cameraCaptureSession
                        try {
                            // Auto focus should be continuous for camera preview. This does not
                            // work on samsung devices.
                            if (!Build.MANUFACTURER.toUpperCase(Locale.ROOT).contains("SAMSUNG")) {
                                previewRequestBuilder.set(
                                    CaptureRequest.CONTROL_AF_MODE,
                                    autoFocusMode
                                )
                            }

                            // Finally, we start displaying the camera preview.
                            val previewRequest = previewRequestBuilder.build()
                            previewCaptureSession?.setRepeatingRequest(previewRequest, null, cameraHandler)
                        } catch (e: CameraAccessException) {
                            // Ignore camera access errors, this occurs when the camera is closed and will fire again
                            // when the camera is opened.
                        }
                    }

                    override fun onConfigureFailed(session: CameraCaptureSession) {
                        mainThreadHandler.post {
                            cameraErrorListener.onCameraOpenError(
                                CameraConfigurationFailedException(
                                    session.device.id
                                )
                            )
                        }
                    }
                },
                null
            )
        } catch (e: CameraAccessException) {
            mainThreadHandler.post { cameraErrorListener.onCameraAccessError(e) }
        }
    }

    /**
     * Configures the necessary [android.graphics.Matrix] transformation to `textureView`.
     * This method should be called after the camera preview size is determined in
     * setUpCameraOutputs and also the size of `textureView` is fixed.
     *
     * @param viewSize The size of `textureView`
     */
    private fun configureTransform(viewSize: Size, imageSize: Size) {
        val matrix = Matrix()
        val viewRect = viewSize.toRectF()
        val bufferRect = imageSize.toRectF()

        val rotation = -(displayRotation.rotationToDegrees()).toFloat()
        val imageScale = calculatePreviewScale(
            viewSize = viewSize,
            imageSize = imageSize,
            displayRotation = displayRotation,
            sensorRotationDegrees = sensorRotation,
        )
        val finalScale = imageScale.scale(
            max(
                imageScale.width * imageSize.width / viewSize.width,
                imageScale.height * imageSize.height / viewSize.height,
            )
        )

        // TODO(awushensky): this breaks on rotation. See https://stackoverflow.com/questions/34536798/android-camera2-preview-is-rotated-90deg-while-in-landscape?rq=1
        bufferRect.offset(viewRect.centerX() - bufferRect.centerX(), viewRect.centerY() - bufferRect.centerY())
        matrix.setRectToRect(viewRect, bufferRect, Matrix.ScaleToFit.CENTER)
        matrix.postScale(finalScale.width, finalScale.height, viewRect.centerX(), viewRect.centerY())
        matrix.postRotate(rotation, viewRect.centerX(), viewRect.centerY())

        scaledPreviewSize = imageSize.scaleAndCenterSurrounding(viewSize)
        previewTextureView.setTransform(matrix)
    }

    override fun setFocus(point: PointF) {
        focusPoint = point
        updateFocus(point)
    }

    private fun updateFocus(point: PointF) {
        if (!::previewRequestBuilder.isInitialized) {
            return
        }

        previewCaptureSession?.stopRepeating()

        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_CANCEL)
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_OFF)
        previewCaptureSession?.capture(previewRequestBuilder.build(), null, cameraHandler)

        if (isMeteringAreaAFSupported()) {
            previewRequestBuilder.set(
                CaptureRequest.CONTROL_AF_REGIONS,
                arrayOf(
                    MeteringRectangle(
                        max(point.x.toInt() - FOCUS_TOUCH_SIZE, 0),
                        max(point.y.toInt() - FOCUS_TOUCH_SIZE, 0),
                        FOCUS_TOUCH_SIZE * 2,
                        FOCUS_TOUCH_SIZE * 2,
                        MeteringRectangle.METERING_WEIGHT_MAX - 1
                    )
                )
            )
        }

        previewRequestBuilder.set(CaptureRequest.CONTROL_MODE, CameraMetadata.CONTROL_MODE_AUTO)
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_MODE, CaptureRequest.CONTROL_AF_MODE_AUTO)
        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, CameraMetadata.CONTROL_AF_TRIGGER_START)
        previewRequestBuilder.setTag("FOCUS_TAG") // we'll capture this later for resuming the preview

        previewCaptureSession?.capture(
            previewRequestBuilder.build(),
            object : CameraCaptureSession.CaptureCallback() {
                override fun onCaptureCompleted(
                    session: CameraCaptureSession,
                    request: CaptureRequest,
                    result: TotalCaptureResult,
                ) {
                    super.onCaptureCompleted(session, request, result)

                    if (request.tag == "FOCUS_TAG") {
                        // the focus trigger is complete -
                        // resume repeating (preview surface will get frames), clear AF trigger
                        previewRequestBuilder.set(CaptureRequest.CONTROL_AF_TRIGGER, null)
                        previewCaptureSession?.setRepeatingRequest(previewRequestBuilder.build(), null, cameraHandler)
                    }
                }
            },
            cameraHandler
        )
    }

    private fun isMeteringAreaAFSupported(): Boolean {
        val manager = getCameraManager()
        val cameraCharacteristics = manager.getCameraCharacteristics(cameraId)
        return cameraCharacteristics.get(CameraCharacteristics.CONTROL_MAX_REGIONS_AF) ?: 0 >= 1
    }

    override fun withSupportsMultipleCameras(task: (Boolean) -> Unit) {
        task(availableCameras.size > 1)
    }

    override fun changeCamera() {
        onPause()

        currentCameraIndex++
        if (currentCameraIndex >= availableCameras.size) {
            currentCameraIndex = 0
        }

        onResume()
    }

    override fun getCurrentCamera(): Int = currentCameraIndex
}
