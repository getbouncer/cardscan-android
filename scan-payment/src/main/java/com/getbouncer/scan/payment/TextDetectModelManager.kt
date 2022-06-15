package com.getbouncer.scan.payment

import android.content.Context
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.payment.ml.TextDetect

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
object TextDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = TextDetect.ModelFetcher(context)
}
