package com.getbouncer.scan.payment.ml

import android.content.Context
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.payment.ModelManager

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
object AlphabetDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = AlphabetDetect.ModelFetcher(context)
}
