package com.getbouncer.scan.framework.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelSignedUrlResponse(
    @SerialName("model_url") val modelUrl: String
)
