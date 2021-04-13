package com.getbouncer.scan.camera

import android.content.Context
import android.graphics.ImageFormat
import android.media.Image
import android.renderscript.RenderScript
import android.util.Size
import androidx.annotation.CheckResult
import androidx.camera.core.ImageProxy
import com.getbouncer.scan.framework.exception.ImageTypeNotSupportedException
import com.getbouncer.scan.framework.image.NV21Image
import com.getbouncer.scan.framework.image.getRenderScript
import com.getbouncer.scan.framework.image.rotate
import com.getbouncer.scan.framework.image.toBitmap
import com.getbouncer.scan.framework.image.yuvPlanesToNV21
import com.getbouncer.scan.framework.util.mapArray
import com.getbouncer.scan.framework.util.mapToIntArray
import com.getbouncer.scan.framework.util.toByteArray

/**
 * Convert an ImageProxy to a bitmap.
 */
@CheckResult
fun ImageProxy.toBitmap(renderScript: RenderScript) = NV21Image(
    width,
    height,
    when (format) {
        ImageFormat.NV21 -> planes[0].buffer.toByteArray()
        ImageFormat.YUV_420_888 -> yuvPlanesToNV21(
            width,
            height,
            planes.mapArray { it.buffer },
            planes.mapToIntArray { it.rowStride },
            planes.mapToIntArray { it.pixelStride },
        )
        else -> throw ImageTypeNotSupportedException(format)
    }
).toBitmap(renderScript)

/**
 * Get the size of an image.
 */
@CheckResult
fun ImageProxy.size() = Size(width, height)

/**
 * build an adapter from [ImageProxy] to [Bitmap] from a [Context].
 */
fun buildBitmapImageProxyAdapter(context: Context) = { image: ImageProxy ->
    image.toBitmap(getRenderScript(context))
        .rotate(image.imageInfo.rotationDegrees.toFloat())
}

/**
 * build an adapter from [Image] to [Bitmap] from a [Context].
 */
fun buildBitmapImageAdapter(context: Context) = { image: Image, rotationDegrees: Float ->
    image.toBitmap(getRenderScript(context)).rotate(rotationDegrees)
}

/**
 * build an adapter from [NV21Image] to [Bitmap] from a [Context].
 */
fun buildBitmapNV21ImageAdapter(context: Context) = { image: NV21Image, rotationDegrees: Float ->
    image.toBitmap(getRenderScript(context)).rotate(rotationDegrees)
}
