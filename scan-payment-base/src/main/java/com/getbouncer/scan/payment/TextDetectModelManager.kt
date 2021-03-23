package com.getbouncer.scan.payment

import android.content.Context
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.payment.ocr.TextDetect

object TextDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = TextDetect.ModelFetcher(context)
}
