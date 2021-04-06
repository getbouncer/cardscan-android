package com.getbouncer.scan.payment

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Rect
import android.os.Build
import android.util.Log
import android.util.Size
import androidx.annotation.CheckResult
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.util.centerOn
import com.getbouncer.scan.framework.util.intersectionWith
import com.getbouncer.scan.framework.util.maxAspectRatioInSize
import com.getbouncer.scan.framework.util.move
import com.getbouncer.scan.framework.util.projectRegionOfInterest
import com.getbouncer.scan.framework.util.resizeRegion
import com.getbouncer.scan.framework.util.size
import com.getbouncer.scan.framework.util.toRect
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

private const val DIM_PIXEL_SIZE = 3
private const val NUM_BYTES_PER_CHANNEL = 4 // Float.size / Byte.size

data class ImageTransformValues(val red: Float, val green: Float, val blue: Float)

/**
 * Get a rect indicating what part of the preview is actually visible on screen. This assumes that the preview
 * is the same size or larger than the screen in both dimensions.
 */
private fun getVisiblePreview(previewBounds: Rect) = Size(
    previewBounds.right + previewBounds.left,
    previewBounds.bottom + previewBounds.top,
)

/**
 * Crop the preview image from the camera based on the view finder's position in the preview bounds.
 *
 * Note: This algorithm makes some assumptions:
 * 1. the previewBounds and the cameraPreviewImage are centered relative to each other.
 * 2. the previewBounds circumscribes the cameraPreviewImage. I.E. they share at least one field of
 *    view, and the cameraPreviewImage's fields of view are smaller than or the same size as the
 *    previewBounds's
 * 3. the previewBounds and the cameraPreviewImage have the same orientation
 */
fun cropCameraPreviewToViewFinder(
    cameraPreviewImage: Bitmap,
    previewBounds: Rect,
    viewFinder: Rect,
): Bitmap {
    require(
        viewFinder.left >= previewBounds.left &&
            viewFinder.right <= previewBounds.right &&
            viewFinder.top >= previewBounds.top &&
            viewFinder.bottom <= previewBounds.bottom
    ) { "View finder $viewFinder is outside preview image bounds $previewBounds" }

    // Scale the cardFinder to match the full image
    val projectedViewFinder = previewBounds
        .projectRegionOfInterest(
            toSize = cameraPreviewImage.size(),
            regionOfInterest = viewFinder
        )
        .intersectionWith(cameraPreviewImage.size().toRect())

    return cameraPreviewImage.crop(projectedViewFinder)
}

/**
 * Crop the preview image from the camera based on a square surrounding the view finder's position in the preview
 * bounds.
 *
 * Note: This algorithm makes some assumptions:
 * 1. the previewBounds and the cameraPreviewImage are centered relative to each other.
 * 2. the previewBounds circumscribes the cameraPreviewImage. I.E. they share at least one field of
 *    view, and the cameraPreviewImage's fields of view are smaller than or the same size as the
 *    previewBounds's
 * 3. the previewBounds and the cameraPreviewImage have the same orientation
 */
fun cropCameraPreviewToSquare(
    cameraPreviewImage: Bitmap,
    previewBounds: Rect,
    viewFinder: Rect,
): Bitmap {
    require(
        viewFinder.left >= previewBounds.left &&
            viewFinder.right <= previewBounds.right &&
            viewFinder.top >= previewBounds.top &&
            viewFinder.bottom <= previewBounds.bottom
    ) { "Card finder is outside preview image bounds" }

    val visiblePreview = getVisiblePreview(previewBounds)
    val squareViewFinder = maxAspectRatioInSize(visiblePreview, 1F).centerOn(viewFinder)

    // calculate the projected squareViewFinder
    val projectedSquare = previewBounds
        .projectRegionOfInterest(cameraPreviewImage.size(), squareViewFinder)
        .intersectionWith(cameraPreviewImage.size().toRect())

    return cameraPreviewImage.crop(projectedSquare)
}

/**
 * Convert a bitmap to an RGB byte buffer for use in TensorFlow Lite ML models.
 */
@CheckResult
fun Bitmap.toRGBByteBuffer(mean: Float = 0F, std: Float = 255F): ByteBuffer = this.toRGBByteBuffer(
    ImageTransformValues(mean, mean, mean),
    ImageTransformValues(std, std, std)
)

/**
 * Convert a bitmap to an RGB byte buffer for use in TensorFlow Lite ML models.
 */
@CheckResult
fun Bitmap.toRGBByteBuffer(mean: ImageTransformValues, std: ImageTransformValues): ByteBuffer {
    val argb = IntArray(width * height).also { getPixels(it, 0, width, 0, 0, width, height) }

    val rgbFloat =
        ByteBuffer.allocateDirect(this.width * this.height * DIM_PIXEL_SIZE * NUM_BYTES_PER_CHANNEL)
    rgbFloat.order(ByteOrder.nativeOrder())

    argb.forEach {
        // ignore the alpha value ((it shr 24 and 0xFF) - mean.alpha) / std.alpha)
        rgbFloat.putFloat(((it shr 16 and 0xFF) - mean.red) / std.red)
        rgbFloat.putFloat(((it shr 8 and 0xFF) - mean.green) / std.green)
        rgbFloat.putFloat(((it and 0xFF) - mean.blue) / std.blue)
    }

    rgbFloat.rewind()
    return rgbFloat
}

/**
 * Convert an RGB byte buffer to a bitmap. This is primarily used in testing.
 */
@CheckResult
fun ByteBuffer.rbgaToBitmap(size: Size, mean: Float = 0F, std: Float = 255F): Bitmap {
    this.rewind()
    check(this.limit() == size.width * size.height) { "ByteBuffer limit does not match expected size" }
    val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
    val rgba = IntBuffer.allocate(size.width * size.height)
    while (this.hasRemaining()) {
        rgba.put(
            (0xFF shl 24) + // set 0xFF for the alpha value
                (((this.float * std) + mean).roundToInt()) +
                (((this.float * std) + mean).roundToInt() shl 8) +
                (((this.float * std) + mean).roundToInt() shl 16)
        )
    }
    rgba.rewind()
    bitmap.copyPixelsFromBuffer(rgba)
    return bitmap
}

/**
 * Determine if the device supports OpenGL version 3.1.
 */
@CheckResult
fun hasOpenGl31(context: Context): Boolean {
    val openGlVersion = 0x00030001
    val activityManager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
    val configInfo = activityManager.deviceConfigurationInfo

    val isSupported = if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
        configInfo.reqGlEsVersion >= openGlVersion && Build.VERSION.SDK_INT >= Build.VERSION_CODES.P
    } else {
        false
    }

    Log.d(Config.logTag, "OpenGL is supported? $isSupported")
    return isSupported
}

/**
 * Fragments the image into multiple segments and places them in new segments.
 */
@CheckResult
fun Bitmap.rearrangeBySegments(
    segmentMap: Map<Rect, Rect>
): Bitmap {
    if (segmentMap.isEmpty()) {
        return Bitmap.createBitmap(0, 0, this.config)
    }
    val newImageDimensions = segmentMap.values.reduce { a, b ->
        Rect(
            min(a.left, b.left),
            min(a.top, b.top),
            max(a.right, b.right),
            max(a.bottom, b.bottom)
        )
    }
    val newImageSize = newImageDimensions.size()
    val result = Bitmap.createBitmap(newImageSize.width, newImageSize.height, this.config)
    val canvas = Canvas(result)

    segmentMap.forEach { entry ->
        val from = entry.key
        val to = entry.value.move(-newImageDimensions.left, -newImageDimensions.top)

        val segment = this.crop(from).scale(to.size())
        canvas.drawBitmap(
            segment,
            to.left.toFloat(),
            to.top.toFloat(),
            null
        )
    }

    return result
}

/**
 * Crops a piece from the center of the source image, resizing that to fit the new center dimension,
 * and squeezes the remainder of the image into a border. The returned image is guaranteed to be
 * square.
 */
@CheckResult
fun Bitmap.zoom(
    originalRegion: Rect,
    newRegion: Rect,
    newImageSize: Size
): Bitmap {
    // Produces a map of rects to rects which are used to map segments of the old image onto the new one
    val regionMap = this.size().resizeRegion(originalRegion, newRegion, newImageSize)
    // construct the bitmap from the region map
    return this.rearrangeBySegments(regionMap)
}

/**
 * Crops and image using originalImageRect and places it on finalImageRect, which is filled with
 * gray for the best results
 */
@CheckResult
fun Bitmap.cropWithFill(cropRegion: Rect): Bitmap {
    val intersectionRegion = this.size().toRect().intersectionWith(cropRegion)
    val result = Bitmap.createBitmap(cropRegion.width(), cropRegion.height(), this.config)
    val canvas = Canvas(result)

    canvas.drawColor(Color.GRAY)

    val croppedImage = this.crop(intersectionRegion)

    canvas.drawBitmap(
        croppedImage,
        croppedImage.size().toRect(),
        intersectionRegion.move(-cropRegion.left, -cropRegion.top),
        null
    )

    return result
}

/**
 * Crop an image to a given rectangle. The rectangle must not exceed the bounds of the image.
 */
@CheckResult
fun Bitmap.crop(crop: Rect): Bitmap {
    require(crop.left < crop.right && crop.top < crop.bottom) { "Cannot use negative crop" }
    require(crop.left >= 0 && crop.top >= 0 && crop.bottom <= this.height && crop.right <= this.width) {
        "Crop is outside the bounds of the image"
    }
    return Bitmap.createBitmap(this, crop.left, crop.top, crop.width(), crop.height())
}

/**
 * Get the size of a bitmap.
 */
@CheckResult
fun Bitmap.size(): Size = Size(this.width, this.height)

fun Bitmap.scale(size: Size, filter: Boolean = false): Bitmap =
    if (size.width == width && size.height == height) {
        this
    } else {
        Bitmap.createScaledBitmap(this, size.width, size.height, filter)
    }
