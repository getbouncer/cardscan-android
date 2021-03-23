package com.getbouncer.scan.payment.ocr

import android.content.Context
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.payment.ModelManager

object ExpiryDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = ExpiryDetect.ModelFetcher(context)
}
