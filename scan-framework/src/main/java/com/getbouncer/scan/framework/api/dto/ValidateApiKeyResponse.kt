package com.getbouncer.scan.framework.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ValidateApiKeyResponse(
    @SerialName("is_valid_api_key") val isApiKeyValid: Boolean,
    @SerialName("invalid_key_reason") val keyInvalidReason: String?
)
