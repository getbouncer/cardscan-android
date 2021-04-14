package com.getbouncer.scan.camera

import android.graphics.ImageFormat
import android.renderscript.RenderScript
import androidx.annotation.CheckResult
import androidx.camera.core.ImageProxy
import com.getbouncer.scan.framework.exception.ImageTypeNotSupportedException
import com.getbouncer.scan.framework.image.NV21Image
import com.getbouncer.scan.framework.image.yuvPlanesToNV21
import com.getbouncer.scan.framework.util.mapArray
import com.getbouncer.scan.framework.util.mapToIntArray
import com.getbouncer.scan.framework.util.toByteArray

/**
 * Convert an ImageProxy to a bitmap.
 */
@CheckResult
internal fun ImageProxy.toBitmap(renderScript: RenderScript) = NV21Image(
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
