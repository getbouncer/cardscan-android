package com.getbouncer.scan.payment.ml

import android.content.Context
import android.util.Log
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.framework.UpdatingModelWebFetcher
import com.getbouncer.scan.framework.UpdatingResourceFetcher
import com.getbouncer.scan.framework.assetFileExists
import com.getbouncer.scan.payment.ModelManager

private const val CARD_DETECT_ASSET_FULL = "ux_0_5_23_16.tflite"
private const val CARD_DETECT_ASSET_MINIMAL = "UX.0.25.106.8.tflite"

@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
object CardDetectModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = when {
        assetFileExists(context, CARD_DETECT_ASSET_FULL) -> {
            Log.d(Config.logTag, "Full card detect available in assets")
            object : UpdatingResourceFetcher(context) {
                override val assetFileName: String = CARD_DETECT_ASSET_FULL
                override val resourceModelVersion: String = "0.5.23.16"
                override val resourceModelHash: String = "ea51ca5c693a4b8733b1cf1a63557a713a13fabf0bcb724385077694e63a51a7"
                override val resourceModelHashAlgorithm: String = "SHA-256"
                override val modelClass: String = "card_detection"
                override val modelFrameworkVersion: Int = 1
            }
        }
        assetFileExists(context, CARD_DETECT_ASSET_MINIMAL) -> {
            Log.d(Config.logTag, "Minimal card detect available in assets")
            object : UpdatingResourceFetcher(context) {
                override val assetFileName: String = CARD_DETECT_ASSET_MINIMAL
                override val resourceModelVersion: String = "0.25.106.8"
                override val resourceModelHash: String = "c2a39c9034a9f0073933488021676c46910cec0d1bf330ac22a908dcd7dd448a"
                override val resourceModelHashAlgorithm: String = "SHA-256"
                override val modelClass: String = "card_detection"
                override val modelFrameworkVersion: Int = 1
            }
        }
        else -> {
            Log.d(Config.logTag, "No card detect available in assets")
            object : UpdatingModelWebFetcher(context) {
                override val defaultModelFileName: String = "UX.0.5.23.16.tflite"
                override val defaultModelVersion: String = "0.5.23.16"
                override val defaultModelHash: String = "ea51ca5c693a4b8733b1cf1a63557a713a13fabf0bcb724385077694e63a51a7"
                override val defaultModelHashAlgorithm: String = "SHA-256"
                override val modelClass: String = "card_detection"
                override val modelFrameworkVersion: Int = 1
            }
        }
    }
}
