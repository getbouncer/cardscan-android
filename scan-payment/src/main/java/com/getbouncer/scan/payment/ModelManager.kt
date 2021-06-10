package com.getbouncer.scan.payment

import android.content.Context
import androidx.annotation.VisibleForTesting
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.framework.Fetcher
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

abstract class ModelManager {

    private lateinit var fetcher: Fetcher
    private val fetcherMutex = Mutex()
    private var successfullyFetched = false

    suspend fun fetchModel(context: Context, forImmediateUse: Boolean, isOptional: Boolean = false): FetchedData {
        fetcherMutex.withLock {
            if (!this::fetcher.isInitialized) {
                fetcher = getModelFetcher(context.applicationContext)
            }
        }
        return fetcher.fetchData(forImmediateUse, isOptional).also { successfullyFetched = it.successfullyFetched }
    }

    fun isReady() = successfullyFetched

    @VisibleForTesting(otherwise = VisibleForTesting.PROTECTED)
    abstract fun getModelFetcher(context: Context): Fetcher
}
