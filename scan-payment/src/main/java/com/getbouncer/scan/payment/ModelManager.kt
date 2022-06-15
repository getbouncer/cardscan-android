package com.getbouncer.scan.payment

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.framework.Fetcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
abstract class ModelManager {
    private lateinit var fetcher: Fetcher
    private val fetcherMutex = Mutex()

    private var onFetch: ((success: Boolean) -> Unit)? = null

    suspend fun fetchModel(context: Context, forImmediateUse: Boolean, isOptional: Boolean = false): FetchedData {
        fetcherMutex.withLock {
            if (!this::fetcher.isInitialized) {
                fetcher = getModelFetcher(context.applicationContext)
            }
        }
        return fetcher.fetchData(forImmediateUse, isOptional).also {
            onFetch?.invoke(it.successfullyFetched)
        }
    }

    suspend fun isReady() = fetcherMutex.withLock {
        if (this::fetcher.isInitialized) fetcher.isCached() else false
    }

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    abstract fun getModelFetcher(context: Context): Fetcher
}
