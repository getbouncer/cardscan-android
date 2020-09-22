@file:JvmName("CardScan")
package com.getbouncer.cardscan.ui

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.fragment.app.Fragment
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.ui.ScanActivity
import com.getbouncer.scan.ui.ScanActivity.Companion.isAnalyzerFailure
import com.getbouncer.scan.ui.ScanActivity.Companion.isCameraError
import com.getbouncer.scan.ui.ScanActivity.Companion.isUserCanceled
import com.getbouncer.scan.ui.ScanActivity.Companion.scanId
import kotlinx.android.parcel.Parcelize

private const val REQUEST_CODE = 21521 // "bou"

const val PARAM_ENABLE_ENTER_MANUALLY = "enableEnterManually"
const val PARAM_DISPLAY_CARD_PAN = "displayCardPan"
const val PARAM_DISPLAY_CARD_SCAN_LOGO = "displayCardScanLogo"
const val PARAM_DISPLAY_CARDHOLDER_NAME = "displayCardholderName"
const val PARAM_ENABLE_EXPIRY_EXTRACTION = "enableExpiryExtraction"
const val PARAM_ENABLE_NAME_EXTRACTION = "enableNameExtraction"

const val CANCELED_REASON_ENTER_MANUALLY = 3

const val RESULT_SCANNED_CARD = "scannedCard"

interface CardScanActivityResultHandler {
    /**
     * A payment card was successfully scanned.
     */
    fun cardScanned(scanId: String?, scanResult: CardScanActivityResult)

    /**
     * The user requested to enter payment card details manually.
     */
    fun enterManually(scanId: String?)

    /**
     * The user canceled the scan.
     */
    fun userCanceled(scanId: String?)

    /**
     * The scan failed because of a camera error.
     */
    fun cameraError(scanId: String?)

    /**
     * The scan failed to analyze images from the camera.
     */
    fun analyzerFailure(scanId: String?)

    /**
     * The scan was canceled due to unknown reasons.
     */
    fun canceledUnknown(scanId: String?)
}

@Parcelize
data class CardScanActivityResult(
    val pan: String?,
    val expiryDay: String?,
    val expiryMonth: String?,
    val expiryYear: String?,
    val networkName: String?,
    val cvc: String?,
    val cardholderName: String?,
    val errorString: String?
) : Parcelable

/**
 * Warm up the analyzers for card scanner. This method is optional, but will increase the
 * speed at which the scan occurs.
 *
 * @param context: A context to use for warming up the analyzers.
 */
fun warmUp(context: Context, apiKey: String, initializeNameAndExpiryExtraction: Boolean) {
    CardScanFlow.warmUp(context, apiKey, initializeNameAndExpiryExtraction)
}

/**
 * Start the card scanner activity.
 *
 * @param activity: The activity launching card scan.
 * @param apiKey: The bouncer API key used to run scanning.
 * @param enableEnterCardManually: If true, show a button to enter the card manually.
 * @param enableExpiryExtraction: If true, attempt to extract the card expiry.
 * @param enableNameExtraction: If true, attempt to extract the cardholder name.
 * @param displayCardPan: If true, display the card pan once the card has started to scan.
 * @param displayCardholderName: If true, display the name of the card owner if extracted.
 * @param displayCardScanLogo: If true, display the cardscan.io logo at the top of the
 *     screen.
 * @param enableDebug: If true, enable debug views in card scan.
 */
@JvmOverloads
fun start(
    activity: Activity,
    apiKey: String,
    enableEnterCardManually: Boolean = false,
    enableExpiryExtraction: Boolean = false,
    enableNameExtraction: Boolean = false,
    displayCardPan: Boolean = true,
    displayCardholderName: Boolean = true,
    displayCardScanLogo: Boolean = true,
    enableDebug: Boolean = Config.isDebug
) {
    activity.startActivityForResult(
        buildIntent(
            context = activity,
            apiKey = apiKey,
            enableEnterCardManually = enableEnterCardManually,
            enableExpiryExtraction = enableExpiryExtraction,
            enableNameExtraction = enableNameExtraction,
            displayCardPan = displayCardPan,
            displayCardholderName = displayCardholderName,
            displayCardScanLogo = displayCardScanLogo,
            enableDebug = enableDebug
        ),
        REQUEST_CODE
    )
}

/**
 * Start the card scanner activity.
 *
 * @param fragment: The fragment launching card scan.
 * @param apiKey: The bouncer API key used to run scanning.
 * @param enableEnterCardManually: If true, show a button to enter the card manually.
 * @param enableExpiryExtraction: If true, attempt to extract the card expiry.
 * @param enableNameExtraction: If true, attempt to extract the cardholder name.
 * @param displayCardPan: If true, display the card pan once the card has started to scan.
 * @param displayCardholderName: If true, display the name of the card owner if extracted.
 * @param displayCardScanLogo: If true, display the cardscan.io logo at the top of the
 *     screen.
 * @param enableDebug: If true, enable debug views in card scan.
 */
@JvmOverloads
fun start(
    fragment: Fragment,
    apiKey: String,
    enableEnterCardManually: Boolean = false,
    enableExpiryExtraction: Boolean = false,
    enableNameExtraction: Boolean = false,
    displayCardPan: Boolean = true,
    displayCardholderName: Boolean = false,
    displayCardScanLogo: Boolean = true,
    enableDebug: Boolean = Config.isDebug
) {
    val context = fragment.context ?: return
    fragment.startActivityForResult(
        buildIntent(
            context = context,
            apiKey = apiKey,
            enableEnterCardManually = enableEnterCardManually,
            enableExpiryExtraction = enableExpiryExtraction,
            enableNameExtraction = enableNameExtraction,
            displayCardPan = displayCardPan,
            displayCardholderName = displayCardholderName,
            displayCardScanLogo = displayCardScanLogo,
            enableDebug = enableDebug
        ),
        REQUEST_CODE
    )
}

/**
 * Build an intent that can be used to start the card scanner activity.
 *
 * @param context: The activity used to build the intent.
 * @param apiKey: The bouncer API key used to run scanning.
 * @param enableEnterCardManually: If true, show a button to enter the card manually.
 * @param enableExpiryExtraction: If true, attempt to extract the card expiry.
 * @param enableNameExtraction: If true, attempt to extract the cardholder name.
 * @param displayCardPan: If true, display the card pan once the card has started to scan.
 * @param displayCardholderName: If true, display the name of the card owner if extracted.
 * @param displayCardScanLogo: If true, display the cardscan.io logo at the top of the
 *     screen.
 * @param enableDebug: If true, enable debug views in card scan.
 */
@JvmOverloads
fun buildIntent(
    context: Context,
    apiKey: String,
    enableEnterCardManually: Boolean = false,
    enableExpiryExtraction: Boolean = false,
    enableNameExtraction: Boolean = false,
    displayCardPan: Boolean = true,
    displayCardholderName: Boolean = false,
    displayCardScanLogo: Boolean = true,
    enableDebug: Boolean = Config.isDebug
): Intent {
    Config.apiKey = apiKey
    Config.isDebug = enableDebug

    return Intent(context, CardScanActivity::class.java)
        .putExtra(PARAM_DISPLAY_CARD_SCAN_LOGO, displayCardScanLogo)
        .putExtra(PARAM_ENABLE_ENTER_MANUALLY, enableEnterCardManually)
        .putExtra(PARAM_ENABLE_EXPIRY_EXTRACTION, enableExpiryExtraction)
        .putExtra(PARAM_ENABLE_NAME_EXTRACTION, enableNameExtraction)
        .putExtra(PARAM_DISPLAY_CARD_PAN, displayCardPan)
        .putExtra(PARAM_DISPLAY_CARDHOLDER_NAME, displayCardholderName)
}

fun parseScanResult(
    resultCode: Int,
    data: Intent?,
    handler: CardScanActivityResultHandler
) {
    if (resultCode == Activity.RESULT_OK && data != null) {
        val scanResult: CardScanActivityResult? = data.getParcelableExtra(RESULT_SCANNED_CARD)
        if (scanResult != null) {
            handler.cardScanned(data.scanId(), scanResult)
        } else {
            handler.canceledUnknown(data.scanId())
        }
    } else if (resultCode == Activity.RESULT_CANCELED) {
        when {
            data.isUserCanceled() -> handler.userCanceled(data.scanId())
            data.isCameraError() -> handler.cameraError(data.scanId())
            data.isAnalyzerFailure() -> handler.analyzerFailure(data.scanId())
            ScanActivity.getCanceledReason(data) == CANCELED_REASON_ENTER_MANUALLY -> {
                handler.enterManually(data.scanId())
            }
        }
    }
}

/**
 * A helper method to determine if an activity result came from card scan.
 */
fun isScanResult(requestCode: Int) = REQUEST_CODE == requestCode
