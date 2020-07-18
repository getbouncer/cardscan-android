package com.getbouncer.scan.framework.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelUpgradeResponse(
    @SerialName("model_url") val url: String,
    @SerialName("model_version") val modelVersion: String,
    @SerialName("model_hash") val hash: String,
    @SerialName("model_hash_algorithm") val hashAlgorithm: String
)
