package com.getbouncer.scan.payment.ocr

import android.content.Context
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.payment.ModelManager

object AlphabetDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = AlphabetDetect.ModelFetcher(context)
}
