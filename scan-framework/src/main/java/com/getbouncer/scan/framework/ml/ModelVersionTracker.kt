package com.getbouncer.scan.framework.ml

private val MODEL_MAP = mutableMapOf<String, MutableSet<Triple<String, Int, Boolean>>>()

/**
 * Details about a model loaded into memory.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
data class ModelLoadDetails(
    val modelClass: String,
    val modelVersion: String,
    val modelFrameworkVersion: Int,
    val success: Boolean
)

/**
 * When a ML model is loaded into memory, track the details of the model.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun trackModelLoaded(modelClass: String, modelVersion: String, modelFrameworkVersion: Int, success: Boolean) {
    MODEL_MAP.getOrPut(modelClass) { mutableSetOf() }.add(Triple(modelVersion, modelFrameworkVersion, success))
}

/**
 * Get the full list of models that were loaded into memory during this session.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
fun getLoadedModelVersions(): List<ModelLoadDetails> = MODEL_MAP.flatMap { entry ->
    entry.value.map {
        ModelLoadDetails(
            modelClass = entry.key,
            modelVersion = it.first,
            modelFrameworkVersion = it.second,
            success = it.third
        )
    }
}
