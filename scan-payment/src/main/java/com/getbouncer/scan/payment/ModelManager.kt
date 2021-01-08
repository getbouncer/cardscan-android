package com.getbouncer.scan.payment

import android.content.Context
import com.getbouncer.scan.framework.util.cacheFirstResultSuspend
import com.getbouncer.scan.payment.ml.AlphabetDetect
import com.getbouncer.scan.payment.ml.CardDetect
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.payment.ml.TextDetect

object ModelManager {

    private val ssdOcrFetcher = cacheFirstResultSuspend { context: Context, forImmediateUse: Boolean ->
        SSDOcr.ModelFetcher(context).fetchData(forImmediateUse)
    }

    private val cardDetectModel = cacheFirstResultSuspend { context: Context, forImmediateUse: Boolean ->
        CardDetect.ModelFetcher(context).fetchData(forImmediateUse)
    }

    private val textDetectorModel = cacheFirstResultSuspend { context: Context, forImmediateUse: Boolean ->
        TextDetect.ModelFetcher(context).fetchData(forImmediateUse)
    }

    private val alphabetDetectorModel = cacheFirstResultSuspend { context: Context, forImmediateUse: Boolean ->
        AlphabetDetect.ModelFetcher(context).fetchData(forImmediateUse)
    }

    private val expiryDetectorModel = cacheFirstResultSuspend { context: Context, forImmediateUse: Boolean ->
        ExpiryDetect.ModelFetcher(context).fetchData(forImmediateUse)
    }
}