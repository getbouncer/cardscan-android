package com.getbouncer.scan.framework

object Scan {

    /**
     * Determine if the scan is supported on this device.
     */
    fun isSupportedWithMinimalTensorflow(): Boolean {
        val arch = System.getProperty("os.arch") ?: ""
        return "86" !in arch
    }
}
