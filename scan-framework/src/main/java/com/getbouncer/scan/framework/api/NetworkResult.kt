package com.getbouncer.scan.framework.api

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
sealed class NetworkResult<Success, Error>(open val responseCode: Int) {
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    data class Success<Success>(override val responseCode: Int, val body: Success) : NetworkResult<Success, Nothing>(responseCode)
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    data class Error<Error>(override val responseCode: Int, val error: Error) : NetworkResult<Nothing, Error>(responseCode)
    @Deprecated(
        message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
        replaceWith = ReplaceWith("StripeCardScan"),
    )
    data class Exception(override val responseCode: Int, val exception: Throwable) : NetworkResult<Nothing, Nothing>(responseCode)
}
