package com.getbouncer.scan.payment.ml

import android.content.Context
import android.util.Log
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Fetcher
import com.getbouncer.scan.framework.UpdatingModelWebFetcher
import com.getbouncer.scan.framework.UpdatingResourceFetcher
import com.getbouncer.scan.framework.assetFileExists
import com.getbouncer.scan.payment.ModelManager

private const val OCR_ASSET_FULL = "darknite_1_1_1_16.tflite"
private const val OCR_ASSET_MINIMAL = "mb2_brex_metal_synthetic_svhnextra_epoch_3_5_98_8.tflite"

object SSDOcrModelManager : ModelManager() {
    override fun getModelFetcher(context: Context): Fetcher = when {
        assetFileExists(context, OCR_ASSET_FULL) -> {
            Log.d(Config.logTag, "Full ocr available in assets")
            object : UpdatingResourceFetcher(context) {
                override val assetFileName: String = OCR_ASSET_FULL
                override val resourceModelVersion: String = "1.1.1.16"
                override val resourceModelHash: String = "8d8e3f79aa0783ab0cfa5c8d65d663a9da6ba99401efb2298aaaee387c3b00d6"
                override val resourceModelHashAlgorithm: String = "SHA-256"
                override val modelClass: String = "ocr"
                override val modelFrameworkVersion: Int = 1
            }
        }
        assetFileExists(context, OCR_ASSET_MINIMAL) -> {
            Log.d(Config.logTag, "Minimal ocr available in assets")
            object : UpdatingResourceFetcher(context) {
                override val assetFileName: String = OCR_ASSET_MINIMAL
                override val resourceModelVersion: String = "3.5.98.8"
                override val resourceModelHash: String = "a4739fa49caa3ff88e7ff1145c9334ee4cbf64354e91131d02d98d7bfd4c35cf"
                override val resourceModelHashAlgorithm: String = "SHA-256"
                override val modelClass: String = "ocr"
                override val modelFrameworkVersion: Int = 1
            }
        }
        else -> {
            Log.d(Config.logTag, "No ocr available in assets")
            object : UpdatingModelWebFetcher(context) {
                override val defaultModelFileName: String = "darknite_1_1_1_16.tflite"
                override val defaultModelVersion: String = "1.1.1.16"
                override val defaultModelHash: String = "8d8e3f79aa0783ab0cfa5c8d65d663a9da6ba99401efb2298aaaee387c3b00d6"
                override val defaultModelHashAlgorithm: String = "SHA-256"
                override val modelClass: String = "ocr"
                override val modelFrameworkVersion: Int = 1
            }
        }
    }
}
