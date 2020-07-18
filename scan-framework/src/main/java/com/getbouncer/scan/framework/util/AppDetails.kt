package com.getbouncer.scan.framework.util

import android.content.Context
import com.getbouncer.scan.framework.BuildConfig

data class AppDetails(
    val appPackageName: String?,
    val applicationId: String,
    val libraryPackageName: String,
    val sdkVersion: String,
    val sdkVersionCode: Int,
    val sdkFlavor: String,
    val isDebugBuild: Boolean
) {
    companion object {
        fun fromContext(context: Context) = AppDetails(
            appPackageName = getAppPackageName(context),
            applicationId = getApplicationId(),
            libraryPackageName = getLibraryPackageName(),
            sdkVersion = getSdkVersion(),
            sdkVersionCode = getSdkVersionCode(),
            sdkFlavor = getSdkFlavor(),
            isDebugBuild = isDebugBuild()
        )
    }
}

fun getAppPackageName(context: Context): String? = context.applicationContext.packageName

private fun getApplicationId(): String = "" // no longer available in later versions of gradle.

fun getLibraryPackageName(): String = BuildConfig.LIBRARY_PACKAGE_NAME

fun getSdkVersion(): String = BuildConfig.VERSION_NAME

private fun getSdkVersionCode(): Int = BuildConfig.VERSION_CODE

fun getSdkFlavor(): String = BuildConfig.BUILD_TYPE

private fun isDebugBuild(): Boolean = BuildConfig.DEBUG
