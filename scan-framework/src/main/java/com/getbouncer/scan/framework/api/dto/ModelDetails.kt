package com.getbouncer.scan.framework.api.dto

import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

@Serializable
data class ModelDetailsRequest(
    @SerialName("platform") val platform: String,
    @SerialName("model_class") val modelClass: String,
    @SerialName("model_framework_version") val modelFrameworkVersion: Int,
    @SerialName("cached_model_hash") val cachedModelHash: String?,
    @SerialName("cached_model_hash_algorithm") val cachedModelHashAlgorithm: String?,
    @SerialName("beta_opt_in") val betaOptIn: Boolean?,
)

@Serializable
data class ModelDetailsResponse(
    @SerialName("model_url") val url: String?,
    @SerialName("model_version") val modelVersion: String,
    @SerialName("model_hash") val hash: String,
    @SerialName("model_hash_algorithm") val hashAlgorithm: String,
    @SerialName("query_again_after_ms") val queryAgainAfterMs: Long? = 0,
)
