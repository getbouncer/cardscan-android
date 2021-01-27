package com.getbouncer.cardscan.ui.local

import android.app.Activity
import android.content.Context
import android.content.Intent
import android.os.Parcelable
import androidx.fragment.app.Fragment
import com.getbouncer.cardscan.ui.local.result.MainLoopAggregator
import com.getbouncer.scan.framework.AggregateResultListener
import com.getbouncer.scan.framework.AnalyzerLoopErrorListener
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.Stats
import com.getbouncer.scan.payment.card.getCardIssuer
import kotlinx.parcelize.Parcelize

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
    val networkName: String?,
) : Parcelable

open class CardScanActivity :
    CardScanBaseActivity(),
    AggregateResultListener<MainLoopAggregator.InterimResult, String>,
    AnalyzerLoopErrorListener {

    companion object {
        const val REQUEST_CODE = 21520 // "bou"

        const val PARAM_ENABLE_ENTER_MANUALLY = "enableEnterManually"

        const val RESULT_INSTANCE_ID = "instanceId"
        const val RESULT_SCAN_ID = "scanId"

        const val RESULT_SCANNED_CARD = "scannedCard"

        const val RESULT_CANCELED_REASON = "canceledReason"
        const val CANCELED_REASON_USER = -1
        const val CANCELED_REASON_CAMERA_ERROR = -2
        const val CANCELED_REASON_ANALYZER_FAILURE = -3
        const val CANCELED_REASON_ENTER_MANUALLY = -4

        private fun getCanceledReason(data: Intent?): Int =
            data?.getIntExtra(RESULT_CANCELED_REASON, Int.MIN_VALUE) ?: Int.MIN_VALUE

        private fun Intent?.isUserCanceled(): Boolean = getCanceledReason(this) == CANCELED_REASON_USER
        private fun Intent?.isCameraError(): Boolean = getCanceledReason(this) == CANCELED_REASON_CAMERA_ERROR
        private fun Intent?.isAnalyzerFailure(): Boolean = getCanceledReason(this) == CANCELED_REASON_ANALYZER_FAILURE
        private fun Intent?.isEnterCardManually(): Boolean = getCanceledReason(this) == CANCELED_REASON_ENTER_MANUALLY

        private fun Intent?.instanceId(): String? = this?.getStringExtra(RESULT_INSTANCE_ID)
        private fun Intent?.scanId(): String? = this?.getStringExtra(RESULT_SCAN_ID)

        /**
         * Start the card scanner activity.
         *
         * @param activity: The activity launching card scan.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         */
        @JvmStatic
        @JvmOverloads
        fun start(
            activity: Activity,
            apiKey: String,
            enableEnterCardManually: Boolean = false,
        ) {
            val intent = buildIntent(
                context = activity,
                apiKey = apiKey,
                enableEnterCardManually = enableEnterCardManually,
            )

            activity.startActivityForResult(intent, REQUEST_CODE)
        }

        /**
         * Start the card scanner activity.
         *
         * @param fragment: The fragment launching card scan.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         */
        @JvmStatic
        @JvmOverloads
        fun start(
            fragment: Fragment,
            apiKey: String,
            enableEnterCardManually: Boolean = false,
        ) {
            val context = fragment.context ?: return
            val intent = buildIntent(
                context = context,
                apiKey = apiKey,
                enableEnterCardManually = enableEnterCardManually,
            )

            fragment.startActivityForResult(intent, REQUEST_CODE)
        }

        /**
         * Build an intent that can be used to start the card scanner activity.
         *
         * @param context: The activity used to build the intent.
         * @param apiKey: The bouncer API key used to run scanning.
         * @param enableEnterCardManually: If true, show a button to enter the card manually.
         */
        @JvmStatic
        @JvmOverloads
        fun buildIntent(
            context: Context,
            apiKey: String,
            enableEnterCardManually: Boolean = false,
        ): Intent {
            Config.apiKey = apiKey

            return Intent(context, CardScanActivity::class.java)
                .putExtra(PARAM_ENABLE_ENTER_MANUALLY, enableEnterCardManually)
        }

        @JvmStatic
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
                    data.isEnterCardManually() -> handler.enterManually(data.scanId())
                }
            }
        }

        /**
         * A helper method to determine if an activity result came from card scan.
         */
        @JvmStatic
        fun isScanResult(requestCode: Int) = REQUEST_CODE == requestCode
    }

    override val scanFlow: CardScanFlow by lazy {
        CardScanFlow(this, this)
    }

    override val enableEnterCardManually: Boolean by lazy {
        intent.getBooleanExtra(PARAM_ENABLE_ENTER_MANUALLY, false)
    }

    override val resultListener = object : CardScanResultListener {
        override fun cardScanned(pan: String) {
            val intent = Intent()
                .putExtra(RESULT_SCANNED_CARD, CardScanActivityResult(pan, getCardIssuer(pan).displayName))
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_OK, intent)
        }

        override fun enterManually() {
            val intent = Intent()
                .putExtra(RESULT_CANCELED_REASON, CANCELED_REASON_ENTER_MANUALLY)
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_CANCELED, intent)
        }

        override fun userCanceled() {
            val intent = Intent()
                .putExtra(RESULT_CANCELED_REASON, CANCELED_REASON_USER)
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_CANCELED, intent)
        }

        override fun cameraError(cause: Throwable?) {
            val intent = Intent()
                .putExtra(RESULT_CANCELED_REASON, CANCELED_REASON_CAMERA_ERROR)
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_CANCELED, intent)
        }

        override fun analyzerFailure(cause: Throwable?) {
            val intent = Intent()
                .putExtra(RESULT_CANCELED_REASON, CANCELED_REASON_ANALYZER_FAILURE)
                .putExtra(RESULT_INSTANCE_ID, Stats.instanceId)
                .putExtra(RESULT_SCAN_ID, Stats.scanId)
            setResult(Activity.RESULT_CANCELED, intent)
        }
    }
}
