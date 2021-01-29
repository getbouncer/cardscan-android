package com.getbouncer.scan.payment

import android.content.Context
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.payment.ml.ExpiryDetect

object ExpiryDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = ExpiryDetect.ModelFetcher(context)
}
