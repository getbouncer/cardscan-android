package com.getbouncer.scan.payment.ocr

import android.content.Context
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.payment.ModelManager

object SSDOcrModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = SSDOcr.ModelFetcher(context)
}
