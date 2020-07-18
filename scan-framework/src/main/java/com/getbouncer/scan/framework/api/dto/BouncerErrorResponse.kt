package com.getbouncer.scan.framework.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class BouncerErrorResponse(
    @SerialName("status") val status: String,
    @SerialName("error_code") val errorCode: String,
    @SerialName("error_message") val errorMessage: String,
    @SerialName("error_payload") val errorPayload: String?
)
