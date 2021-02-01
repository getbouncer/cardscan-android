package com.getbouncer.scan.framework

import android.os.Build

object Scan {

    /**
     * Determine if the device is running an ARM architecture.
     */
    fun isDeviceArchitectureArm(): Boolean {
        val arch = System.getProperty("os.arch") ?: ""
        return "86" !in arch
    }

    /**
     * Determine the architecture of the device.
     */
    fun getDeviceArchitecture(): String? {
        // From https://stackoverflow.com/questions/11989629/api-call-to-get-processor-architecture

        // Note that we cannot use System.getProperty("os.arch") since that may give e.g. "aarch64"
        // while a 64-bit runtime may not be installed (like on the Samsung Galaxy S5 Neo).
        // Instead we search through the supported abi:s on the device, see:
        // http://developer.android.com/ndk/guides/abis.html

        // Note that we search for abi:s in preferred order (the ordering of the
        // Build.SUPPORTED_ABIS list) to avoid e.g. installing arm on an x86 system where arm
        // emulation is available.
        for (androidArch in Build.SUPPORTED_ABIS) {
            when (androidArch) {
                "arm64-v8a" -> return "aarch64"
                "armeabi-v7a" -> return "arm"
                "x86_64" -> return "x86_64"
                "x86" -> return "i686"
            }
        }
        return null
    }
}
