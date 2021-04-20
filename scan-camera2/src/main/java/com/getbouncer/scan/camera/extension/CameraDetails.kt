package com.getbouncer.scan.camera.extension

import android.hardware.camera2.params.StreamConfigurationMap

/**
 * Details about a camera.
 */
internal data class CameraDetails(
    val cameraId: String,
    val flashAvailable: Boolean,
    val config: StreamConfigurationMap,
    val sensorRotation: Int,
    val supportedAutoFocusModes: List<Int>,
    val lensFacing: Int?,
)
