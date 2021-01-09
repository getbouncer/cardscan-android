package com.getbouncer.scan.payment

import android.content.Context
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.payment.ml.AlphabetDetect
import com.getbouncer.scan.payment.ml.CardDetect
import com.getbouncer.scan.payment.ml.ExpiryDetect
import com.getbouncer.scan.payment.ml.SSDOcr
import com.getbouncer.scan.payment.ml.TextDetect
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

object ScanPaymentModelManager {

    private lateinit var ssdOcrFetcher: SSDOcr.ModelFetcher
    private val ssdOcrFetcherMutex = Mutex()

    suspend fun getSsdOcr(context: Context, forImmediateUse: Boolean, isOptional: Boolean = false): FetchedData {
        ssdOcrFetcherMutex.withLock {
            if (!this::ssdOcrFetcher.isInitialized) {
                ssdOcrFetcher = SSDOcr.ModelFetcher(context.applicationContext)
            }
        }
        return ssdOcrFetcher.fetchData(forImmediateUse, isOptional)
    }

    private lateinit var cardDetectFetcher: CardDetect.ModelFetcher
    private val cardDetectFetcherMutex = Mutex()

    suspend fun getCardDetect(context: Context, forImmediateUse: Boolean, isOptional: Boolean = false): FetchedData {
        cardDetectFetcherMutex.withLock {
            if (!this::cardDetectFetcher.isInitialized) {
                cardDetectFetcher = CardDetect.ModelFetcher(context.applicationContext)
            }
        }
        return cardDetectFetcher.fetchData(forImmediateUse, isOptional)
    }

    private lateinit var textDetectFetcher: TextDetect.ModelFetcher
    private val textDetectFetcherMutex = Mutex()

    suspend fun getTextDetect(context: Context, forImmediateUse: Boolean, isOptional: Boolean = true): FetchedData {
        textDetectFetcherMutex.withLock {
            if (!this::textDetectFetcher.isInitialized) {
                textDetectFetcher = TextDetect.ModelFetcher(context.applicationContext)
            }
        }
        return textDetectFetcher.fetchData(forImmediateUse, isOptional)
    }

    private lateinit var alphabetDetectFetcher: AlphabetDetect.ModelFetcher
    private val alphabetDetectFetcherMutex = Mutex()

    suspend fun getAlphabetDetect(context: Context, forImmediateUse: Boolean, isOptional: Boolean = true): FetchedData {
        alphabetDetectFetcherMutex.withLock {
            if (!this::alphabetDetectFetcher.isInitialized) {
                alphabetDetectFetcher = AlphabetDetect.ModelFetcher(context.applicationContext)
            }
        }
        return alphabetDetectFetcher.fetchData(forImmediateUse, isOptional)
    }

    private lateinit var expiryDetectFetcher: ExpiryDetect.ModelFetcher
    private val expiryDetectFetcherMutex = Mutex()

    suspend fun getExpiryDetect(context: Context, forImmediateUse: Boolean, isOptional: Boolean = true): FetchedData {
        expiryDetectFetcherMutex.withLock {
            if (!this::expiryDetectFetcher.isInitialized) {
                expiryDetectFetcher = ExpiryDetect.ModelFetcher(context.applicationContext)
            }
        }
        return expiryDetectFetcher.fetchData(forImmediateUse, isOptional)
    }
}
