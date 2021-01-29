package com.getbouncer.scan.payment

import android.content.Context
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.payment.ml.AlphabetDetect

object AlphabetDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = AlphabetDetect.ModelFetcher(context)
}
