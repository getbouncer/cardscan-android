package com.getbouncer.cardscan.ui

import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.activity.ComponentActivity
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.ActivityResultRegistry
import androidx.activity.result.contract.ActivityResultContract
import androidx.fragment.app.Fragment
import com.getbouncer.cardscan.ui.exception.UnknownScanException
import com.getbouncer.scan.ui.CancellationReason
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.parcelize.Parcelize

@Parcelize
internal data class CardScanSheetParams(
    val apiKey: String,
    val enableEnterManually: Boolean,
    val enableNameExtraction: Boolean,
    val enableExpiryExtraction: Boolean,
) : Parcelable

sealed interface CardScanSheetResult : Parcelable {

    @Parcelize
    data class Completed(
        val scannedCard: ScannedCard,
    ) : CardScanSheetResult

    @Parcelize
    data class Canceled(
        val reason: CancellationReason,
    ) : CardScanSheetResult

    @Parcelize
    data class Failed(val error: Throwable) : CardScanSheetResult
}

class CardScanSheet private constructor(private val apiKey: String) {

    private lateinit var launcher: ActivityResultLauncher<CardScanSheetParams>

    /**
     * Callback to notify when scanning finishes and a result is available.
     */
    fun interface CardScanResultCallback {
        fun onCardScanSheetResult(cardScanSheetResult: CardScanSheetResult)
    }

    companion object {
        /**
         * Create a [CardScanSheet] instance with [ComponentActivity].
         *
         * This API registers an [ActivityResultLauncher] into the
         * [ComponentActivity], it must be called before the [ComponentActivity]
         * is created (in the onCreate method).
         */
        @JvmStatic
        @JvmOverloads
        fun create(
            from: ComponentActivity,
            apiKey: String,
            cardScanResultCallback: CardScanResultCallback,
            registry: ActivityResultRegistry = from.activityResultRegistry,
        ) = CardScanSheet(apiKey).apply {
            launcher = from.registerForActivityResult(
                activityResultContract,
                registry,
                cardScanResultCallback::onCardScanSheetResult,
            )
        }

        @JvmStatic
        @JvmOverloads
        fun create(
            from: Fragment,
            apiKey: String,
            cardScanResultCallback: CardScanResultCallback,
            registry: ActivityResultRegistry? = null,
        ) = CardScanSheet(apiKey).apply {
            launcher = if (registry != null) {
                from.registerForActivityResult(
                    activityResultContract,
                    registry,
                    cardScanResultCallback::onCardScanSheetResult,
                )
            } else {
                from.registerForActivityResult(
                    activityResultContract,
                    cardScanResultCallback::onCardScanSheetResult,
                )
            }
        }

        private val activityResultContract =
            object : ActivityResultContract<CardScanSheetParams, CardScanSheetResult>() {
                override fun createIntent(
                    context: Context,
                    input: CardScanSheetParams,
                ) = this@Companion.createIntent(context, input)

                override fun parseResult(
                    resultCode: Int,
                    intent: Intent?,
                ) = this@Companion.parseResult(intent)
            }

        private fun createIntent(context: Context, input: CardScanSheetParams) =
            Intent(context, CardScanActivity::class.java)
                .putExtra(INTENT_PARAM_REQUEST, input)

        private fun parseResult(intent: Intent?): CardScanSheetResult =
            intent?.getParcelableExtra(INTENT_PARAM_RESULT)
                ?: CardScanSheetResult.Failed(
                    UnknownScanException("No data in the result intent")
                )

        /**
         * Determine if the scan is supported
         */
        @JvmStatic
        fun isSupported(context: Context) = CardScanFlow.isSupported(context)

        /**
         * Determine if the scan models are available (have been warmed up)
         */
        @JvmStatic
        fun isScanReady() = CardScanFlow.isScanReady()

        /**
         * Warm up the analyzers and call [onPrepared] once the scan is ready.
         *
         * @param context: A context to use for warming up the analyzers.
         * @param apiKey: the API key used to warm up the ML models
         * @param initializeNameAndExpiryExtraction: if true, include name and expiry extraction
         * @param onPrepared: called once the scan is ready
         */
        @JvmStatic
        fun prepareScan(
            context: Context,
            apiKey: String,
            initializeNameAndExpiryExtraction: Boolean,
            onPrepared: () -> Unit,
        ) = GlobalScope.launch {
            CardScanFlow.prepareScan(context, apiKey, initializeNameAndExpiryExtraction, false)
        }.invokeOnCompletion { onPrepared() }
    }

    /**
     * Present the CardScan flow.
     * Results will be returned in the callback function.
     */
    fun present(
        enableEnterManually: Boolean,
        enableNameExtraction: Boolean,
        enableExpiryExtraction: Boolean,
    ) {
        launcher.launch(
            CardScanSheetParams(
                apiKey = apiKey,
                enableEnterManually = enableEnterManually,
                enableNameExtraction = enableNameExtraction,
                enableExpiryExtraction = enableExpiryExtraction,
            )
        )
    }
}
