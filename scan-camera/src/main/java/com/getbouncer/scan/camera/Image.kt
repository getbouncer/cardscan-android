package com.getbouncer.scan.camera

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.media.Image
import androidx.annotation.CheckResult
import com.getbouncer.scan.camera.exception.ImageTypeNotSupportedException
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

/**
 * Determine if this application supports an image format.
 */
@CheckResult
fun Image.isSupportedFormat() = isSupportedFormat(this.format)

/**
 * Determine if this application supports an image format.
 */
@CheckResult
fun isSupportedFormat(imageFormat: Int) = when (imageFormat) {
    ImageFormat.YUV_420_888, ImageFormat.JPEG -> true
    ImageFormat.NV21 -> false // this fails on older devices
    else -> false
}

/**
 * Convert an image to a bitmap for processing. This will throw an [ImageTypeNotSupportedException]
 * if the image type is not supported (see [isSupportedFormat]).
 */
@CheckResult
@Throws(ImageTypeNotSupportedException::class)
fun Image.toBitmap(
    crop: Rect = Rect(
        0,
        0,
        this.width,
        this.height
    ),
    quality: Int = 75
): Bitmap = when (this.format) {
    ImageFormat.NV21 -> planes[0].buffer.toByteArray().nv21ToYuv(width, height).toBitmap(crop, quality)
    ImageFormat.YUV_420_888 -> yuvToNV21Bytes().nv21ToYuv(width, height).toBitmap(crop, quality)
    ImageFormat.JPEG -> jpegToBitmap().crop(crop)
    else -> throw ImageTypeNotSupportedException(this.format)
}

/**
 * Convert a YuvImage to a bitmap.
 */
@CheckResult
fun YuvImage.toBitmap(
    crop: Rect = Rect(
        0,
        0,
        this.width,
        this.height
    ),
    quality: Int = 75
): Bitmap {
    val out = ByteArrayOutputStream()
    compressToJpeg(crop, quality, out)

    val imageBytes = out.toByteArray()
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

@CheckResult
private fun Image.jpegToBitmap(): Bitmap {
    check(format == ImageFormat.JPEG) { "Image is not in JPEG format" }

    val imageBuffer = planes[0].buffer
    val imageBytes = ByteArray(imageBuffer.remaining())
    imageBuffer.get(imageBytes)
    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
}

@CheckResult
private fun ByteBuffer.toByteArray(): ByteArray {
    val bytes = ByteArray(remaining())
    get(bytes)
    return bytes
}

/**
 * From https://stackoverflow.com/questions/52726002/camera2-captured-picture-conversion-from-yuv-420-888-to-nv21/52740776#52740776
 *
 * https://stackoverflow.com/questions/32276522/convert-nv21-byte-array-into-bitmap-readable-format
 */
@CheckResult
private fun Image.yuvToNV21Bytes(): ByteArray {
    val crop = this.cropRect
    val format = this.format
    val width = crop.width()
    val height = crop.height()
    val planes = this.planes
    val nv21Bytes = ByteArray(width * height * ImageFormat.getBitsPerPixel(format) / 8)
    val rowData = ByteArray(planes[0].rowStride)

    var channelOffset = 0
    var outputStride = 1

    for (i in planes.indices) {
        when (i) {
            0 -> {
                channelOffset = 0
                outputStride = 1
            }
            1 -> {
                channelOffset = width * height + 1
                outputStride = 2
            }
            2 -> {
                channelOffset = width * height
                outputStride = 2
            }
        }

        val buffer = planes[i].buffer
        val rowStride = planes[i].rowStride
        val pixelStride = planes[i].pixelStride
        val shift = if (i == 0) 0 else 1
        val w = width shr shift
        val h = height shr shift

        buffer.position(rowStride * (crop.top shr shift) + pixelStride * (crop.left shr shift))

        for (row in 0 until h) {
            var length: Int

            if (pixelStride == 1 && outputStride == 1) {
                length = w
                buffer.get(nv21Bytes, channelOffset, length)
                channelOffset += length
            } else {
                length = (w - 1) * pixelStride + 1
                buffer.get(rowData, 0, length)
                for (col in 0 until w) {
                    nv21Bytes[channelOffset] = rowData[col * pixelStride]
                    channelOffset += outputStride
                }
            }

            if (row < h - 1) {
                buffer.position(buffer.position() + rowStride - length)
            }
        }
    }

    return nv21Bytes
}

/**
 * Convert an NV21 byte array to a YuvImage.
 */
@CheckResult
fun ByteArray.nv21ToYuv(width: Int, height: Int) = YuvImage(
    this,
    ImageFormat.NV21,
    width,
    height,
    null
)

@CheckResult
fun Bitmap.crop(crop: Rect): Bitmap {
    require(crop.left < crop.right && crop.top < crop.bottom) { "Cannot use negative crop" }
    require(crop.left >= 0 && crop.top >= 0 && crop.bottom <= this.height && crop.right <= this.width) {
        "Crop is larger than source image"
    }
    return Bitmap.createBitmap(this, crop.left, crop.top, crop.width(), crop.height())
}

@CheckResult
fun Bitmap.rotate(rotationDegrees: Float): Bitmap = if (rotationDegrees != 0F) {
    val matrix = Matrix()
    matrix.postRotate(rotationDegrees)
    Bitmap.createBitmap(this, 0, 0, this.width, this.height, matrix, true)
} else {
    this
}

@CheckResult
fun Bitmap.scale(percentage: Float, filter: Boolean = false): Bitmap = if (percentage == 1F) {
    this
} else {
    Bitmap.createScaledBitmap(
        this,
        (width * percentage).toInt(),
        (height * percentage).toInt(),
        filter
    )
}
