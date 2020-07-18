@file:JvmName("BouncerApi")
package com.getbouncer.scan.framework.api

import android.content.Context
import com.getbouncer.scan.framework.api.dto.AppInfo
import com.getbouncer.scan.framework.api.dto.BouncerErrorResponse
import com.getbouncer.scan.framework.api.dto.ClientDevice
import com.getbouncer.scan.framework.api.dto.ModelSignedUrlResponse
import com.getbouncer.scan.framework.api.dto.ModelUpgradeResponse
import com.getbouncer.scan.framework.api.dto.ScanStatistics
import com.getbouncer.scan.framework.api.dto.StatsPayload
import com.getbouncer.scan.framework.api.dto.ValidateApiKeyResponse
import com.getbouncer.scan.framework.util.AppDetails
import com.getbouncer.scan.framework.util.Device
import com.getbouncer.scan.framework.util.getAppPackageName
import com.getbouncer.scan.framework.util.getPlatform
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val STATS_PATH = "/scan_stats"
private const val API_KEY_VALIDATION_PATH = "/v1/api_key/validate"
private const val MODEL_SIGNED_URL_PATH = "/v1/signed_url/model/%s/%s/android/%s"
private const val MODEL_UPGRADE_PATH = "/v1/model/%s/%s/%s?client_name=%s"

const val ERROR_CODE_NOT_AUTHENTICATED = "not_authenticated"

/**
 * Upload stats data to bouncer servers.
 */
fun uploadScanStats(
    context: Context,
    instanceId: String,
    scanId: String?,
    device: Device,
    appDetails: AppDetails,
    scanStatistics: ScanStatistics
) = GlobalScope.launch(Dispatchers.IO) {
    postData(
        context = context,
        path = STATS_PATH,
        data = StatsPayload(
            instanceId = instanceId,
            scanId = scanId,
            device = ClientDevice.fromDevice(device),
            app = AppInfo.fromAppDetails(appDetails),
            scanStats = scanStatistics
        ),
        requestSerializer = StatsPayload.serializer()
    )
}

/**
 * Validate an API key.
 */
suspend fun validateApiKey(context: Context): NetworkResult<out ValidateApiKeyResponse, out BouncerErrorResponse> =
    withContext(Dispatchers.IO) {
        getForResult(
            context = context,
            path = API_KEY_VALIDATION_PATH,
            responseSerializer = ValidateApiKeyResponse.serializer(),
            errorSerializer = BouncerErrorResponse.serializer()
        )
    }

/**
 * Get a signed URL for a model.
 */
suspend fun getModelSignedUrl(
    context: Context,
    modelClass: String,
    modelVersion: String,
    modelFileName: String
): NetworkResult<out ModelSignedUrlResponse, out BouncerErrorResponse> =
    withContext(Dispatchers.IO) {
        getForResult(
            context = context,
            path = MODEL_SIGNED_URL_PATH.format(modelClass, modelVersion, modelFileName),
            responseSerializer = ModelSignedUrlResponse.serializer(),
            errorSerializer = BouncerErrorResponse.serializer()
        )
    }

/**
 * Get an upgrade path for a model.
 */
suspend fun getModelUpgradePath(
    context: Context,
    modelClass: String,
    modelFrameworkVersion: Int
): NetworkResult<out ModelUpgradeResponse, out BouncerErrorResponse> =
    withContext(Dispatchers.IO) {
        getForResult(
            context = context,
            path = MODEL_UPGRADE_PATH.format(getPlatform(), modelClass, modelFrameworkVersion, getAppPackageName(context)),
            responseSerializer = ModelUpgradeResponse.serializer(),
            errorSerializer = BouncerErrorResponse.serializer()
        )
    }
