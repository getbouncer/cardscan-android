package com.getbouncer.scan.camera

import android.content.Context
import android.content.pm.PackageManager
import android.graphics.PointF
import android.graphics.Rect
import android.util.Log
import android.util.Size
import android.util.SizeF
import android.view.Surface
import androidx.annotation.IntDef
import androidx.annotation.MainThread
import androidx.camera.core.AspectRatio
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.OnLifecycleEvent
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.TrackedImage
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.channels.ClosedSendChannelException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.runBlocking
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Valid integer rotation values.
 */
@IntDef(
    Surface.ROTATION_0,
    Surface.ROTATION_90,
    Surface.ROTATION_180,
    Surface.ROTATION_270
)
@Retention(AnnotationRetention.SOURCE)
private annotation class RotationValue

/**
 * A list of supported camera adapter types. If you create a new adapter, create a new object that
 * subclasses this class.
 */
interface CameraApi {
    object Camera1 : CameraApi
    object Camera2 : CameraApi
    object CameraX : CameraApi
}

data class CameraPreviewImage<ImageBase>(
    val image: TrackedImage<ImageBase>,
    val previewImageBounds: Rect,
)

private const val RATIO_4_3_VALUE = 4.0 / 3.0
private const val RATIO_16_9_VALUE = 16.0 / 9.0

abstract class CameraAdapter<CameraOutput> : LifecycleObserver {

    // TODO: change this to be a channelFlow once it's no longer experimental, add some capacity and use a backpressure drop strategy
    private val imageChannel = Channel<CameraOutput>(capacity = Channel.RENDEZVOUS)
    private var lifecyclesBound = 0

    companion object {

        /**
         * Determine if the device supports the camera features used by this SDK.
         */
        @JvmStatic
        fun isCameraSupported(context: Context): Boolean =
            context.packageManager.hasSystemFeature(PackageManager.FEATURE_CAMERA).also {
                if (!it) Log.e(Config.logTag, "System feature 'FEATURE_CAMERA' is unavailable")
            }

        /**
         * Determine how much to rotate the image from the camera given the orientation of the
         * display and the orientation of the camera sensor.
         *
         * @param displayOrientation: The enum value of the display rotation (e.g. Surface.ROTATION_0)
         * @param sensorRotationDegrees: The rotation of the sensor in degrees
         *
         * @return the difference in degrees.
         */
        internal fun calculateImageRotationDegrees(
            @RotationValue displayOrientation: Int,
            sensorRotationDegrees: Int
        ) = (
            (
                when (displayOrientation) {
                    Surface.ROTATION_0 -> sensorRotationDegrees
                    Surface.ROTATION_90 -> sensorRotationDegrees - 90
                    Surface.ROTATION_180 -> sensorRotationDegrees - 180
                    Surface.ROTATION_270 -> sensorRotationDegrees - 270
                    else -> 0
                } % 360
                ) + 360
            ) % 360

        /**
         * Determine the surface rotation from a degrees rotation. Snap to 90 degree increments.
         *
         * @param degrees: the degrees of rotation to convert
         *
         * @return the degrees, snapped to 90, as a [Surface] rotation value.
         */
        @RotationValue
        internal fun Int.degreesToSurfaceRotation(): Int =
            when (if (this % 360 < 0) this % 360 + 360 else this % 360) {
                in 45 until 135 -> Surface.ROTATION_90
                in 135 until 225 -> Surface.ROTATION_180
                in 225 until 315 -> Surface.ROTATION_270
                else -> Surface.ROTATION_0
            }

        /**
         * Calculate degrees from a [RotationValue].
         */
        internal fun Int.rotationToDegrees(): Int = this * 90

        /**
         * Convert a size on the screen to a resolution.
         */
        internal fun Size.toResolution(): Size = Size(
            /* width */
            max(width, height),
            /* height */
            min(width, height),
        )

        /**
         * Convert a resolution to a size on the screen.
         */
        internal fun Size.resolutionToSize(
            @RotationValue displayRotation: Int,
            sensorRotationDegrees: Int
        ) = if (areScreenAndSensorPerpendicular(displayRotation, sensorRotationDegrees)) {
            Size(this.height, this.width)
        } else {
            this
        }

        /**
         * Convert a resolution to a size on the screen based only on the display size.
         */
        internal fun Size.resolutionToSize(displaySize: Size) = when {
            displaySize.width >= displaySize.height -> Size(
                /* width */
                max(width, height),
                /* height */
                min(width, height),
            )
            else -> Size(
                /* width */
                min(width, height),
                /* height */
                max(width, height),
            )
        }

        /**
         * Calculate how much an image must scale in X and Y to match a view size.
         */
        internal fun calculatePreviewScale(
            viewSize: Size,
            imageSize: Size,
            @RotationValue displayRotation: Int,
            sensorRotationDegrees: Int
        ) = if (areScreenAndSensorPerpendicular(displayRotation, sensorRotationDegrees)) {
            SizeF(viewSize.height.toFloat() / imageSize.height, viewSize.width.toFloat() / imageSize.width)
        } else {
            SizeF(viewSize.width.toFloat() / imageSize.width, viewSize.height.toFloat() / imageSize.height)
        }

        /**
         * Determines if the dimensions are swapped given the phone's current rotation.
         *
         * @param displayRotation The current rotation of the display
         *
         * @return true if the dimensions are swapped, false otherwise.
         */
        private fun areScreenAndSensorPerpendicular(
            @RotationValue displayRotation: Int,
            sensorRotationDegrees: Int
        ) = when (displayRotation) {
            Surface.ROTATION_0, Surface.ROTATION_180 -> {
                sensorRotationDegrees == 90 || sensorRotationDegrees == 270
            }
            Surface.ROTATION_90, Surface.ROTATION_270 -> {
                sensorRotationDegrees == 0 || sensorRotationDegrees == 180
            }
            else -> {
                false
            }
        }

        /**
         *  [androidx.camera.core.ImageAnalysis] requires enum value of
         *  [androidx.camera.core.AspectRatio]. Currently it has values of 4:3 & 16:9.
         *
         *  Detecting the most suitable ratio for dimensions provided in @params by counting absolute
         *  of preview ratio to one of the provided values.
         *
         *  @param width - preview width
         *  @param height - preview height
         *  @return suitable aspect ratio
         */
        private fun aspectRatioFrom(width: Int, height: Int): Int {
            val previewRatio = max(width, height).toDouble() / min(width, height)
            return if (abs(previewRatio - RATIO_4_3_VALUE) <= abs(previewRatio - RATIO_16_9_VALUE)) {
                AspectRatio.RATIO_4_3
            } else {
                AspectRatio.RATIO_16_9
            }
        }
    }

    protected fun sendImageToStream(image: CameraOutput) = try {
        imageChannel.offer(image)
    } catch (e: ClosedSendChannelException) {
        Log.w(Config.logTag, "Attempted to send image to closed channel")
    } catch (t: Throwable) {
        Log.e(Config.logTag, "Unable to send image to channel", t)
    }

    @OnLifecycleEvent(Lifecycle.Event.ON_DESTROY)
    fun onDestroyed() {
        runBlocking { imageChannel.close() }
    }

    /**
     * Bind this camera manager to a lifecycle.
     */
    open fun bindToLifecycle(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.addObserver(this)
        lifecyclesBound++
    }

    /**
     * Unbind this camera from a lifecycle. This will pause the camera.
     */
    open fun unbindFromLifecycle(lifecycleOwner: LifecycleOwner) {
        lifecycleOwner.lifecycle.removeObserver(this)

        lifecyclesBound--
        if (lifecyclesBound < 0) {
            Log.e(Config.logTag, "Bound lifecycle count $lifecyclesBound is below 0")
            lifecyclesBound = 0
        }

        this.onPause()
    }

    /**
     * Determine if the adapter is currently bound.
     */
    open fun isBoundToLifecycle() = lifecyclesBound > 0

    @OnLifecycleEvent(Lifecycle.Event.ON_PAUSE)
    open fun onPause() {
        // support OnPause events.
    }

    /**
     * Execute a task with flash support.
     */
    abstract fun withFlashSupport(task: (Boolean) -> Unit)

    /**
     * Turn the camera torch on or off.
     */
    abstract fun setTorchState(on: Boolean)

    /**
     * Determine if the torch is currently on.
     */
    abstract fun isTorchOn(): Boolean

    /**
     * Determine if the device has multiple cameras.
     */
    abstract fun withSupportsMultipleCameras(task: (Boolean) -> Unit)

    /**
     * Change to a new camera.
     */
    abstract fun changeCamera()

    /**
     * Set the focus on a particular point on the screen.
     */
    abstract fun setFocus(point: PointF)

    /**
     * Get the stream of images from the camera. This is a hot [Flow] of images with a back pressure strategy DROP.
     * Images that are not read from the flow are dropped. This flow is backed by a [Channel].
     */
    fun getImageStream(): Flow<CameraOutput> = imageChannel.receiveAsFlow()
}

interface CameraErrorListener {

    @MainThread
    fun onCameraOpenError(cause: Throwable?)

    @MainThread
    fun onCameraAccessError(cause: Throwable?)

    @MainThread
    fun onCameraUnsupportedError(cause: Throwable?)
}
