package com.getbouncer.scan.framework.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
data class BouncerErrorResponse(
    @SerialName("status") val status: String,
    @SerialName("error_code") val errorCode: String,
    @SerialName("error_message") val errorMessage: String,
    @SerialName("error_payload") val errorPayload: String?
)
