package com.getbouncer.scan.framework.api.dto

import androidx.annotation.RestrictTo
import com.getbouncer.scan.framework.util.Device
import com.getbouncer.scan.framework.util.DeviceIds
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
data class ClientDevice(
    @SerialName("ids") val ids: ClientDeviceIds,
    @SerialName("type") val name: String,
    @SerialName("boot_count") val bootCount: Int,
    @SerialName("locale") val locale: String?,
    @SerialName("carrier") val carrier: String?,
    @SerialName("network_operator") val networkOperator: String?,
    @SerialName("phone_type") val phoneType: Int?,
    @SerialName("phone_count") val phoneCount: Int,
    @SerialName("os_version") val osVersion: String,
    @SerialName("platform") val platform: String
) {
    companion object {
        @Deprecated(
            message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
            replaceWith = ReplaceWith("StripeCardScan"),
        )
        fun fromDevice(device: Device) = ClientDevice(
            ids = ClientDeviceIds.fromDeviceIds(device.ids),
            name = device.name,
            bootCount = device.bootCount,
            locale = device.locale,
            carrier = device.carrier,
            networkOperator = device.networkOperator,
            phoneType = device.phoneType,
            phoneCount = device.phoneCount,
            osVersion = device.osVersion.toString(),
            platform = device.platform
        )
    }
}

@Serializable
@RestrictTo(RestrictTo.Scope.LIBRARY_GROUP)
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
data class ClientDeviceIds(
    @SerialName("vendor_id") val androidId: String?
) {
    companion object {
        @Deprecated(
            message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
            replaceWith = ReplaceWith("StripeCardScan"),
        )
        fun fromDeviceIds(deviceIds: DeviceIds) = ClientDeviceIds(
            androidId = deviceIds.androidId
        )
    }
}
