package com.getbouncer.scan.payment

import android.content.Context
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.payment.ml.AlphabetDetect
import com.getbouncer.scan.payment.ml.CardDetect
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.payment.ml.TextDetect

object ScanPaymentModelManager {

    private lateinit var ssdOcrFetcher: SSDOcr.ModelFetcher
    suspend fun getSsdOcr(context: Context, forImmediateUse: Boolean, isOptional: Boolean = false): FetchedData {
        if (!this::ssdOcrFetcher.isInitialized) {
            ssdOcrFetcher = SSDOcr.ModelFetcher(context.applicationContext)
        }
        return ssdOcrFetcher.fetchData(forImmediateUse, isOptional)
    }

    private lateinit var cardDetectFetcher: CardDetect.ModelFetcher
    suspend fun getCardDetect(context: Context, forImmediateUse: Boolean, isOptional: Boolean = false): FetchedData {
        if (!this::cardDetectFetcher.isInitialized) {
            cardDetectFetcher = CardDetect.ModelFetcher(context.applicationContext)
        }
        return cardDetectFetcher.fetchData(forImmediateUse, isOptional)
    }

    private lateinit var textDetectFetcher: TextDetect.ModelFetcher
    suspend fun getTextDetect(context: Context, forImmediateUse: Boolean, isOptional: Boolean = true): FetchedData {
        if (!this::textDetectFetcher.isInitialized) {
            textDetectFetcher = TextDetect.ModelFetcher(context.applicationContext)
        }
        return textDetectFetcher.fetchData(forImmediateUse, isOptional)
    }

    private lateinit var alphabetDetectFetcher: AlphabetDetect.ModelFetcher
    suspend fun getAlphabetDetect(context: Context, forImmediateUse: Boolean, isOptional: Boolean = true): FetchedData {
        if (!this::alphabetDetectFetcher.isInitialized) {
            alphabetDetectFetcher = AlphabetDetect.ModelFetcher(context.applicationContext)
        }
        return alphabetDetectFetcher.fetchData(forImmediateUse, isOptional)
    }

    private lateinit var expiryDetectFetcher: ExpiryDetect.ModelFetcher
    suspend fun getExpiryDetect(context: Context, forImmediateUse: Boolean, isOptional: Boolean = true): FetchedData {
        if (!this::expiryDetectFetcher.isInitialized) {
            expiryDetectFetcher = ExpiryDetect.ModelFetcher(context.applicationContext)
        }
        return expiryDetectFetcher.fetchData(forImmediateUse, isOptional)
    }
}
