package com.getbouncer.scan.framework.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelInfoRequest(
    @SerialName("platform") val platform: String,
    @SerialName("model_class") val modelClass: String,
    @SerialName("model_framework_version") val modelFrameworkVersion: Int,
    @SerialName("cached_model_hash") val cachedModelHash: String?,
    @SerialName("cached_model_hash_algorithm") val cachedModelHashAlgorithm: String?,
)
