package com.getbouncer.scan.payment

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.util.Size
import androidx.annotation.CheckResult
import com.getbouncer.scan.framework.util.centerOn
import com.getbouncer.scan.framework.util.expandToAspectRatio
import com.getbouncer.scan.framework.util.intersection
import com.getbouncer.scan.framework.util.projectRegionOfInterest
import com.getbouncer.scan.framework.util.rect
import com.getbouncer.scan.framework.util.relative
import com.getbouncer.scan.framework.util.size
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.IntBuffer
import kotlin.math.roundToInt

private const val DIM_PIXEL_SIZE = 3
private const val NUM_BYTES_PER_CHANNEL = 4 // Float.size / Byte.size

data class ImageTransformValues(val red: Float, val green: Float, val blue: Float)

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

    return if (configInfo.reqGlEsVersion != ConfigurationInfo.GL_ES_VERSION_UNDEFINED) {
        configInfo.reqGlEsVersion >= openGlVersion
    } else {
        false
    }
}

/**
 * Fragments the image into multiple segments and places them in new segments.
 */
@CheckResult
fun Bitmap.rearrangeBySegments(
    fromSegments: Map<Rect, Rect>,
    futureImageSize: Size
): Bitmap {
    val current = this
    val result = Bitmap.createBitmap(futureImageSize.width, futureImageSize.height, this.config)
    val canvas = Canvas(result)

    for ((from, to) in fromSegments) {
        val image = current.crop(from).scale(to.size())
        canvas.drawBitmap(
            image,
            to.left.toFloat(),
            to.top.toFloat(),
            null
        )
    }

    return result
}

fun Bitmap.resizeRegion(
    originalCenterRect: Rect,
    futureCenterRect: Rect,
    futureImageSize: Size
): Map<Rect, Rect> = mapOf(
    Rect(
        0,
        0,
        originalCenterRect.left,
        originalCenterRect.top
    ) to Rect(
        0,
        0,
        futureCenterRect.left,
        futureCenterRect.top
    ),
    Rect(
        originalCenterRect.left,
        0,
        originalCenterRect.right,
        originalCenterRect.top
    ) to Rect(
        futureCenterRect.left,
        0,
        futureCenterRect.right,
        futureCenterRect.top
    ),
    Rect(
        originalCenterRect.right,
        0,
        this.width,
        originalCenterRect.top
    ) to Rect(
        futureCenterRect.right,
        0,
        futureImageSize.width,
        futureCenterRect.top
    ),
    Rect(
        0,
        originalCenterRect.top,
        originalCenterRect.left,
        originalCenterRect.bottom
    ) to Rect(
        0,
        futureCenterRect.top,
        futureCenterRect.left,
        futureCenterRect.bottom
    ),
    Rect(
        originalCenterRect.left,
        originalCenterRect.top,
        originalCenterRect.right,
        originalCenterRect.bottom
    ) to Rect(
        futureCenterRect.left,
        futureCenterRect.top,
        futureCenterRect.right,
        futureCenterRect.bottom
    ),
    Rect(
        originalCenterRect.right,
        originalCenterRect.top,
        this.width,
        originalCenterRect.bottom
    ) to Rect(
        futureCenterRect.right,
        futureCenterRect.top,
        futureImageSize.width,
        futureCenterRect.bottom
    ),
    Rect(
        0,
        originalCenterRect.bottom,
        originalCenterRect.left,
        this.height
    ) to Rect(
        0,
        futureCenterRect.bottom,
        futureCenterRect.left,
        futureImageSize.height
    ),
    Rect(
        originalCenterRect.left,
        originalCenterRect.bottom,
        originalCenterRect.right,
        this.height
    ) to Rect(
        futureCenterRect.left,
        futureCenterRect.bottom,
        futureCenterRect.right,
        futureImageSize.height
    ),
    Rect(
        originalCenterRect.right,
        originalCenterRect.bottom,
        this.width,
        this.height
    ) to Rect(
        futureCenterRect.right,
        futureCenterRect.bottom,
        futureImageSize.width,
        futureImageSize.height
    )
)

/**
 * Crops a piece from the center of the source image, resizing that to fit the new center dimension,
 * and squeezes the remainder of the image into a border. The returned image is guaranteed to be
 * square.
 */
@CheckResult
fun Bitmap.zoom(
    previewSize: Size,
    cardFinder: Rect,
    originalCenterSize: Size,
    futureCenterRect: Rect,
    futureImageSize: Size
): Bitmap {
    // Transforms the viewfinder from the preview to the image size
    val projectedViewFinder = previewSize.projectRegionOfInterest(toSize = this.size(), regionOfInterest = cardFinder)
    // Creates a square version of the viewfinder
    val projectedViewFinderSquare = Size(projectedViewFinder.width(), projectedViewFinder.width())
    // Creates a rect of a certain aspect ratio surrounding the view finder
    val aspectRatioCrop = projectedViewFinderSquare.centerOn(projectedViewFinder).expandToAspectRatio(9, 16)
    // Crops the image and fills the rest of the aspect-ratio image with gray
    val croppedImage = this.cropWithFill(aspectRatioCrop, aspectRatioCrop.intersection(this.size().rect()))
    // Finds the center rectangle of the newly cropped image
    val originalCenterRect = originalCenterSize.centerOn(croppedImage.size().rect())
    // Produces a map of rects to rects which are used to map segments of the old image onto the new one
    val regionMap = croppedImage.resizeRegion(originalCenterRect, futureCenterRect, futureImageSize)
    // construct the bitmap from the region map
    return croppedImage.rearrangeBySegments(regionMap, futureImageSize)
}

/**
 * Crops and image using originalImageRect and places it on finalImageRect, which is filled with
 * gray for the best results
 */
fun Bitmap.cropWithFill(finalImageRect: Rect, originalImageRect: Rect): Bitmap {
    val result = Bitmap.createBitmap(finalImageRect.width(), finalImageRect.height(), this.config)
    val canvas = Canvas(result)

    val paint = Paint()
    paint.setColor(Color.GRAY)
    paint.setStyle(Paint.Style.FILL)
    canvas.drawPaint(paint)

    canvas.drawBitmap(
        this.crop(originalImageRect),
        originalImageRect.relative(finalImageRect).left.toFloat(),
        originalImageRect.relative(finalImageRect).top.toFloat(),
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

@CheckResult
fun Bitmap.rect(): Rect = Rect(0, 0, this.width, this.height)

fun Bitmap.scale(size: Size, filter: Boolean = false): Bitmap =
    if (size.width == width && size.height == height) {
        this
    } else {
        Bitmap.createScaledBitmap(this, size.width, size.height, filter)
    }
