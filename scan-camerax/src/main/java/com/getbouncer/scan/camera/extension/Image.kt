package com.getbouncer.scan.camera.extension

import android.graphics.ImageFormat
import android.renderscript.RenderScript
import androidx.annotation.CheckResult
import androidx.camera.core.ImageProxy
import com.getbouncer.scan.framework.exception.ImageTypeNotSupportedException
import com.getbouncer.scan.framework.image.NV21Image
import com.getbouncer.scan.framework.image.yuvPlanesToNV21Fast
import com.getbouncer.scan.framework.util.mapArray
import com.getbouncer.scan.framework.util.mapToIntArray
import com.getbouncer.scan.framework.util.toByteArray

/**
 * Convert an ImageProxy to a bitmap.
 */
@CheckResult
internal fun ImageProxy.toBitmap(renderScript: RenderScript) = when (format) {
    ImageFormat.NV21 -> NV21Image(width, height, planes[0].buffer.toByteArray()).toBitmap(renderScript)
    ImageFormat.YUV_420_888 -> NV21Image(
        width,
        height,
        yuvPlanesToNV21Fast(
            width,
            height,
            planes.mapArray { it.buffer },
            planes.mapToIntArray { it.rowStride },
            planes.mapToIntArray { it.pixelStride },
        ),
    ).toBitmap(renderScript)
    else -> throw ImageTypeNotSupportedException(format)
}
