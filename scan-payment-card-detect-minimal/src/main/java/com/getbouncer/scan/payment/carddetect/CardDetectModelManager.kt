package com.getbouncer.scan.payment.carddetect

import android.content.Context
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.payment.ModelManager

object CardDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = CardDetect.ModelFetcher(context)
}
