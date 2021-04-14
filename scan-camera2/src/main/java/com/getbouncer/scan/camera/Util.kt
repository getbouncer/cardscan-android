package com.getbouncer.scan.camera

import android.util.Size
import android.util.SizeF
import android.view.Surface
import androidx.annotation.CheckResult

/**
 * Calculate how much an image must scale in X and Y to match a view size.
 */
@CheckResult
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
 * Convert a resolution to a size on the screen.
 */
@CheckResult
internal fun Size.resolutionToSize(
    @RotationValue displayRotation: Int,
    sensorRotationDegrees: Int
) = if (areScreenAndSensorPerpendicular(displayRotation, sensorRotationDegrees)) {
    Size(this.height, this.width)
} else {
    this
}

/**
 * Determines if the dimensions are swapped given the phone's current rotation.
 *
 * @param displayRotation The current rotation of the display
 *
 * @return true if the dimensions are swapped, false otherwise.
 */
@CheckResult
internal fun areScreenAndSensorPerpendicular(
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
 * Determine how much to rotate the image from the camera given the orientation of the
 * display and the orientation of the camera sensor.
 *
 * @param displayOrientation: The enum value of the display rotation (e.g. Surface.ROTATION_0)
 * @param sensorRotationDegrees: The rotation of the sensor in degrees
 *
 * @return the difference in degrees.
 */
@CheckResult
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
