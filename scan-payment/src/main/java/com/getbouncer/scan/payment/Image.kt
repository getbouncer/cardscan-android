package com.getbouncer.scan.payment

import android.app.ActivityManager
import android.content.Context
import android.content.pm.ConfigurationInfo
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Rect
import android.util.Size
import androidx.annotation.CheckResult
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
fun Bitmap.fragment(
    fromSegments: Array<Rect>,
    toSegments: Array<Rect>,
    toSize: Size
): Bitmap {
    require(fromSegments.size == toSegments.size) {
        "Number of source segments does not match number of destination segments"
    }

    val current = this
    val result = Bitmap.createBitmap(toSize.width, toSize.height, this.config)
    val canvas = Canvas(result)

    (0 until fromSegments.size).map {
        val image = current.crop(fromSegments[it]).scale(toSegments[it].size())
        canvas.drawBitmap(
            image,
            toSegments[it].left.toFloat(),
            toSegments[it].top.toFloat(),
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
    centerSize: Size,
    toCenterDimension: Int,
    toBorderWidth: Int
): Bitmap {
    val widthOffset = (centerSize.width / 2)
    val heightOffset = (centerSize.height / 2)
    val fromSegments = arrayOf(
        Rect(
            0,
            0,
            (this.width / 2) - widthOffset,
            (this.height / 2) - heightOffset
        ),
        Rect(
            (this.width / 2) - widthOffset,
            0,
            (this.width / 2) + widthOffset,
            this.height / 2 - heightOffset
        ),
        Rect(
            (this.width / 2) + widthOffset,
            0,
            this.width,
            (this.height / 2) - heightOffset
        ),
        Rect(
            0,
            (this.height / 2) - heightOffset,
            (this.width / 2) - widthOffset,
            (this.height / 2) + heightOffset
        ),
        Rect(
            (this.width / 2) - widthOffset,
            (this.height / 2) - heightOffset,
            (this.width / 2) + widthOffset,
            (this.height / 2) + heightOffset
        ),
        Rect(
            (this.width / 2) + widthOffset,
            (this.height / 2) - heightOffset,
            this.width,
            (this.height / 2) + heightOffset
        ),
        Rect(
            0,
            (this.height / 2) + heightOffset,
            (this.width / 2) - widthOffset,
            this.height
        ),
        Rect(
            (this.width / 2) - widthOffset,
            (this.height / 2) + heightOffset,
            (this.width / 2) + widthOffset,
            this.height
        ),
        Rect(
            (this.width / 2) + widthOffset,
            (this.height / 2) + heightOffset,
            this.width,
            this.height
        )
    )
    val toSegments = arrayOf(
        Rect(
            0,
            0,
            toBorderWidth,
            toBorderWidth
        ),
        Rect(
            toBorderWidth,
            0,
            toBorderWidth + toCenterDimension,
            toBorderWidth
        ),
        Rect(
            toBorderWidth + toCenterDimension,
            0,
            (2 * toBorderWidth + toCenterDimension),
            toBorderWidth
        ),
        Rect(
            0,
            toBorderWidth,
            toBorderWidth,
            toBorderWidth + toCenterDimension
        ),
        Rect(
            toBorderWidth,
            toBorderWidth,
            toBorderWidth + toCenterDimension,
            toBorderWidth + toCenterDimension
        ),
        Rect(
            toBorderWidth + toCenterDimension,
            112,
            (2 * toBorderWidth + toCenterDimension),
            toBorderWidth + toCenterDimension
        ),
        Rect(
            0,
            toBorderWidth + toCenterDimension,
            toBorderWidth,
            (2 * toBorderWidth + toCenterDimension)
        ),
        Rect(
            toBorderWidth,
            toBorderWidth + toCenterDimension,
            toBorderWidth + toCenterDimension,
            (2 * toBorderWidth + toCenterDimension)
        ),
        Rect(
            toBorderWidth + toCenterDimension,
            toBorderWidth + toCenterDimension,
            (2 * toBorderWidth + toCenterDimension),
            (2 * toBorderWidth + toCenterDimension)
        )
    )
    val toSize = Size((2 * toBorderWidth + toCenterDimension), (2 * toBorderWidth + toCenterDimension))

    return this.fragment(fromSegments, toSegments, toSize)
}

fun Bitmap.zoomOffset(
    centerSize: Size,
    previewSize: Size,
    cardFinder: Rect,
    toCenterDimension: Int,
    toBorderWidth: Int
): Bitmap {
    val newLeft = cardFinder.left * this.width / previewSize.height
    val newTop = cardFinder.top * this.height / previewSize.height
    val newRight = cardFinder.right * this.width / previewSize.width
    val newBottom = cardFinder.bottom * this.height / previewSize.height

    val scaledCardFinderRect = Rect(newLeft, newTop, newRight, newBottom)

    val x = scaledCardFinderRect.centerX()
    val y = scaledCardFinderRect.centerY()

    val totalHeight = this.height - centerSize.height
    val totalWidth = this.width - centerSize.width

    val toSize = Size((2 * toBorderWidth + toCenterDimension), (2 * toBorderWidth + toCenterDimension))
    var r1: Pair<Int, Int>
    val r2: Pair<Int, Int>
    val r3: Pair<Int, Int>
    val r4: Pair<Int, Int>
    var c1: Pair<Int, Int>
    val c2: Pair<Int, Int>
    val c3: Pair<Int, Int>
    val c4: Pair<Int, Int>
    var tr1: Pair<Int, Int>
    val tr2: Pair<Int, Int>
    val tr3: Pair<Int, Int>
    val tr4: Pair<Int, Int>
    var tc1: Pair<Int, Int>
    val tc2: Pair<Int, Int>
    val tc3: Pair<Int, Int>
    val tc4: Pair<Int, Int>

    if (x < this.width / 2) {
        if (y < this.height / 2) {
            r1 = Pair(0, y - centerSize.height / 2)
            r2 = Pair(y - centerSize.height / 2, y + centerSize.height / 2)
            r3 = Pair(y + centerSize.height / 2, y + centerSize.height / 2 + totalHeight / 2)
            r4 = Pair(y + centerSize.height / 2 + totalHeight / 2, this.height)
            c1 = Pair(0, x - centerSize.width / 2)
            c2 = Pair(x - centerSize.width / 2, x + centerSize.width / 2)
            c3 = Pair(x + centerSize.width / 2, x + centerSize.width / 2 + totalWidth / 2)
            c4 = Pair(x + centerSize.width / 2 + totalWidth / 2, this.width)
            tr1 = Pair(
                toBorderWidth - ((r1.second - r1.first) * toBorderWidth / (totalHeight / 2)),
                toBorderWidth
            )
            tr2 = Pair(toBorderWidth, toBorderWidth + toCenterDimension)
            tr3 = Pair(toBorderWidth + toCenterDimension, 2 * toBorderWidth + toCenterDimension)
            tr4 = Pair(
                0,
                toBorderWidth - ((r1.second - r1.first) * toBorderWidth / (totalHeight / 2))
            )
            tc1 = Pair(
                toBorderWidth - ((c1.second - c1.first) * toBorderWidth / (totalWidth / 2)),
                toBorderWidth
            )
            tc2 = Pair(toBorderWidth, toBorderWidth + toCenterDimension)
            tc3 = Pair(toBorderWidth + toCenterDimension, 2 * toBorderWidth + toCenterDimension)
            tc4 = Pair(
                0,
                toBorderWidth - ((c1.second - c1.first) * toBorderWidth / (totalWidth / 2))
            )
        } else {
            r1 = Pair(0, y - centerSize.height / 2 - totalHeight / 2)
            r2 = Pair(y - centerSize.height / 2 - totalHeight / 2, y - centerSize.height / 2)
            r3 = Pair(y - centerSize.height / 2, y + centerSize.height / 2)
            r4 = Pair(y + centerSize.height / 2, this.height)
            c1 = Pair(0, x - centerSize.width / 2)
            c2 = Pair(x - centerSize.width / 2, x + centerSize.width / 2)
            c3 = Pair(x + centerSize.width / 2, x + centerSize.width / 2 + totalWidth / 2)
            c4 = Pair(x + centerSize.width / 2 + totalWidth / 2, this.width)

            if (y == this.height / 2) {
                r1 = r2
            }

            tr1 = Pair(
                toBorderWidth + toCenterDimension + (r1.second - r1.first) * toBorderWidth /
                    (totalHeight / 2),
                (2 * toBorderWidth) + toCenterDimension
            )
            tr2 = Pair(0, toBorderWidth)
            tr3 = Pair(toBorderWidth, toBorderWidth + toCenterDimension)
            tr4 = Pair(
                toBorderWidth + toCenterDimension,
                toBorderWidth + toCenterDimension + (r1.second - r1.first) * toBorderWidth /
                    (totalHeight / 2)
            )
            tc1 = Pair(
                toBorderWidth - ((c1.second - c1.first) * toBorderWidth / (totalWidth / 2)),
                toBorderWidth
            )
            tc2 = Pair(toBorderWidth, toBorderWidth + toCenterDimension)
            tc3 = Pair(toBorderWidth + toCenterDimension, 2 * toBorderWidth + toCenterDimension)
            tc4 = Pair(
                0,
                toBorderWidth - ((c1.second - c1.first) * toBorderWidth / (totalWidth / 2))
            )

            if (y == this.height / 2) {
                tr1 = tr2
            }
        }
    } else {
        if (y < this.height / 2) {
            r1 = Pair(0, y - centerSize.height / 2)
            r2 = Pair(y - centerSize.height / 2, y + centerSize.height / 2)
            r3 = Pair(y + centerSize.height / 2, y + centerSize.height / 2 + totalHeight / 2)
            r4 = Pair(y + centerSize.height / 2 + totalHeight / 2, this.height)
            c1 = Pair(0, x - centerSize.width / 2 - totalWidth / 2)
            c2 = Pair(x - centerSize.width / 2 - totalWidth / 2, x - centerSize.width / 2)
            c3 = Pair(x - centerSize.width / 2, x + centerSize.width / 2)
            c4 = Pair(x + centerSize.width / 2, this.width)

            if (x == this.width / 2) {
                c1 = c2
            }

            tr1 = Pair(
                toBorderWidth - ((r1.second - r1.first) * toBorderWidth / (totalHeight / 2)),
                toBorderWidth
            )
            tr2 = Pair(toBorderWidth, toBorderWidth + toCenterDimension)
            tr3 = Pair(toBorderWidth + toCenterDimension, 2 * toBorderWidth + toCenterDimension)
            tr4 = Pair(
                0,
                toBorderWidth - ((r1.second - r1.first) * toBorderWidth / (totalHeight / 2))
            )
            tc1 = Pair(
                toBorderWidth + toCenterDimension + (c1.second - c1.first) * toBorderWidth /
                    (totalWidth / 2),
                (2 * toBorderWidth) + toCenterDimension
            )
            tc2 = Pair(0, toBorderWidth)
            tc3 = Pair(toBorderWidth, toBorderWidth + toCenterDimension)
            tc4 = Pair(
                toBorderWidth + toCenterDimension,
                toBorderWidth + toCenterDimension + (c1.second - c1.first) * toBorderWidth /
                    (totalWidth / 2)
            )
        } else {
            r1 = Pair(0, y - centerSize.height / 2 - totalHeight / 2)
            r2 = Pair(y - centerSize.height / 2 - totalHeight / 2, y - centerSize.height / 2)
            r3 = Pair(y - centerSize.height / 2, y + centerSize.height / 2)
            r4 = Pair(y + centerSize.height / 2, this.height)
            c1 = Pair(0, x - centerSize.width / 2 - totalWidth / 2)
            c2 = Pair(x - centerSize.width / 2 - totalWidth / 2, x - centerSize.width / 2)
            c3 = Pair(x - centerSize.width / 2, x + centerSize.width / 2)
            c4 = Pair(x + centerSize.width / 2, this.width)

            if (y == this.height / 2) {
                r1 = r2
            }

            if (x == this.width / 2) {
                c1 = c2
            }

            tr1 = Pair(
                toBorderWidth + toCenterDimension + (r1.second - r1.first) * toBorderWidth /
                    (totalHeight / 2),
                (2 * toBorderWidth) + toCenterDimension
            )
            tr2 = Pair(0, toBorderWidth)
            tr3 = Pair(toBorderWidth, toBorderWidth + toCenterDimension)
            tr4 = Pair(
                toBorderWidth + toCenterDimension,
                toBorderWidth + toCenterDimension + (r1.second - r1.first) * toBorderWidth /
                    (totalHeight / 2)
            )
            tc1 = Pair(
                toBorderWidth + toCenterDimension + (c1.second - c1.first) * toBorderWidth /
                    (totalWidth / 2),
                (2 * toBorderWidth) + toCenterDimension
            )
            tc2 = Pair(0, toBorderWidth)
            tc3 = Pair(toBorderWidth, toBorderWidth + toCenterDimension)
            tc4 = Pair(
                toBorderWidth + toCenterDimension,
                toBorderWidth + toCenterDimension + (c1.second - c1.first) * toBorderWidth /
                    (totalWidth / 2)
            )

            if (y == this.height / 2) {
                tr1 = tr2
            }
        }

        if (x == this.width / 2) {
            tc1 = tc2
        }
    }

    val fromSegments = arrayOf(
        Rect(c1.first, r1.first, c1.second, r1.second),
        Rect(c2.first, r1.first, c2.second, r1.second),
        Rect(c3.first, r1.first, c3.second, r1.second),
        Rect(c4.first, r1.first, c4.second, r1.second),
        Rect(c1.first, r2.first, c1.second, r2.second),
        Rect(c2.first, r2.first, c2.second, r2.second),
        Rect(c3.first, r2.first, c3.second, r2.second),
        Rect(c4.first, r2.first, c4.second, r2.second),
        Rect(c1.first, r3.first, c1.second, r3.second),
        Rect(c2.first, r3.first, c2.second, r3.second),
        Rect(c3.first, r3.first, c3.second, r3.second),
        Rect(c4.first, r3.first, c4.second, r3.second),
        Rect(c1.first, r4.first, c1.second, r4.second),
        Rect(c2.first, r4.first, c2.second, r4.second),
        Rect(c3.first, r4.first, c3.second, r4.second),
        Rect(c4.first, r4.first, c4.second, r4.second)
    )
    val toSegments = arrayOf(
        Rect(tc1.first, tr1.first, tc1.second, tr1.second),
        Rect(tc2.first, tr1.first, tc2.second, tr1.second),
        Rect(tc3.first, tr1.first, tc3.second, tr1.second),
        Rect(tc4.first, tr1.first, tc4.second, tr1.second),
        Rect(tc1.first, tr2.first, tc1.second, tr2.second),
        Rect(tc2.first, tr2.first, tc2.second, tr2.second),
        Rect(tc3.first, tr2.first, tc3.second, tr2.second),
        Rect(tc4.first, tr2.first, tc4.second, tr2.second),
        Rect(tc1.first, tr3.first, tc1.second, tr3.second),
        Rect(tc2.first, tr3.first, tc2.second, tr3.second),
        Rect(tc3.first, tr3.first, tc3.second, tr3.second),
        Rect(tc4.first, tr3.first, tc4.second, tr3.second),
        Rect(tc1.first, tr4.first, tc1.second, tr4.second),
        Rect(tc2.first, tr4.first, tc2.second, tr4.second),
        Rect(tc3.first, tr4.first, tc3.second, tr4.second),
        Rect(tc4.first, tr4.first, tc4.second, tr4.second)
    )
    return this.fragment(fromSegments, toSegments, toSize)
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
fun Bitmap.scale(size: Size, filter: Boolean = false): Bitmap =
    if (size.width == width && size.height == height) {
        this
    } else {
        Bitmap.createScaledBitmap(this, size.width, size.height, filter)
    }
