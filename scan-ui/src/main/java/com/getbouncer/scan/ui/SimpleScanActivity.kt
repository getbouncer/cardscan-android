package com.getbouncer.scan.ui

import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Typeface
import android.os.Bundle
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.core.content.ContextCompat
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.util.getSdkVersion
import com.getbouncer.scan.ui.util.asRect
import com.getbouncer.scan.ui.util.dpToPixels
import com.getbouncer.scan.ui.util.fadeIn
import com.getbouncer.scan.ui.util.fadeOut
import com.getbouncer.scan.ui.util.getColorByRes
import com.getbouncer.scan.ui.util.hide
import com.getbouncer.scan.ui.util.setAnimated
import com.getbouncer.scan.ui.util.setDrawable
import com.getbouncer.scan.ui.util.setVisible
import kotlinx.coroutines.flow.Flow

abstract class SimpleScanActivity : ScanActivity() {

    abstract class ScanState {
        object NotFound : ScanState()
        object FoundShort : ScanState()
        object FoundLong : ScanState()
        object Correct : ScanState()
        object Wrong : ScanState()
    }

    companion object {
        private const val LOGO_WIDTH_DP = 100
    }

    protected open val layout: ConstraintLayout by lazy { ConstraintLayout(this) }

    /**
     * The frame where the camera preview will be displayed. This is usually the full screen.
     */
    override val previewFrame: FrameLayout by lazy { FrameLayout(this) }

    /**
     * The text view that displays the cardholder name once a card has been scanned.
     */
    protected open val cardNameTextView: TextView by lazy { TextView(this) }

    /**
     * The text view that displays the card number once a card has been scanned.
     */
    protected open val cardNumberTextView: TextView by lazy { TextView(this) }

    /**
     * The view that the user can tap to close the scan window.
     */
    protected open val closeButtonView: View by lazy { ImageView(this) }

    /**
     * The view that a user can tap to turn on the flashlight.
     */
    protected open val torchButtonView: View by lazy { ImageView(this) }

    /**
     * The text view that informs the user what to do.
     */
    protected open val instructionsTextView: TextView by lazy { TextView(this) }

    /**
     * The icon used to display a lock to indicate that the scanned card is secure.
     */
    protected open val securityIconView: ImageView by lazy { ImageView(this) }

    /**
     * The text view used to inform the user that the scanned card is secure.
     */
    protected open val securityTextView: TextView by lazy { TextView(this) }

    /**
     * The background that draws the user focus to the view finder.
     */
    protected open val viewFinderBackgroundView: ViewFinderBackground by lazy { ViewFinderBackground(this) }

    /**
     * The view finder window view.
     */
    protected open val viewFinderWindowView: View by lazy { View(this) }

    /**
     * The border around the view finder.
     */
    protected open val viewFinderBorderView: ImageView by lazy { ImageView(this) }

    /**
     * The image view that shows the currently processing frame
     */
    protected open val debugImageView: ImageView by lazy { ImageView(this) }

    /**
     * The overlay that shows details about the currently processing frame.
     */
    private val debugOverlayView: DebugOverlay by lazy { DebugOverlay(this) }

    private val logoView: ImageView by lazy { ImageView(this) }

    private val versionTextView: TextView by lazy { TextView(this) }

    /**
     * Determine whether to show the logo at the top of the screen.
     */
    protected var displayCardScanLogo: Boolean = true

    /**
     * The aspect ratio of the view finder.
     */
    protected open val viewFinderAspectRatio = "H,200:126"

    /**
     * Determine if the flashlight is supported.
     */
    protected var isFlashlightSupported: Boolean? = null

    /**
     * Determine if the background is dark. This is used to set light background vs dark background
     * text and images.
     */
    protected open fun isBackgroundDark(): Boolean =
        viewFinderBackgroundView.getBackgroundLuminance() > 127

    @SuppressLint("ClickableViewAccessibility")
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        addUiComponents()
        setupUiComponents()
        setupUiConstraints()

        setupLogoUi()
        setupLogoConstraints()

        setupVersionUi()
        setupVersionConstraints()

        closeButtonView.setOnClickListener { userCancelScan() }
        torchButtonView.setOnClickListener { toggleFlashlight() }

        viewFinderBorderView.setOnTouchListener { _, e ->
            setFocus(PointF(e.x + viewFinderBorderView.left, e.y + viewFinderBorderView.top))
            true
        }

        setContentView(layout)
    }

    override fun onPause() {
        super.onPause()
        viewFinderBackgroundView.clearOnDrawListener()
    }

    override fun onResume() {
        super.onResume()
        scanState = ScanState.NotFound
        viewFinderBackgroundView.setOnDrawListener { setupUiComponents() }
    }

    /**
     * Add the UI components to the root view.
     */
    protected open fun addUiComponents() {
        listOf(
            previewFrame,
            viewFinderBackgroundView,
            viewFinderWindowView,
            viewFinderBorderView,
            securityIconView,
            securityTextView,
            instructionsTextView,
            closeButtonView,
            torchButtonView,
            cardNameTextView,
            cardNumberTextView,
            debugImageView,
            debugOverlayView,
            logoView,
            versionTextView,
        ).forEach {
            it.id = View.generateViewId()
            layout.addView(it)
        }
    }

    protected open fun setupUiComponents() {
        setupCloseButtonViewUi()
        setupTorchButtonViewUi()
        setupInstructionsViewUi()
        setupSecurityNoticeUi()
        setupCardDetailsUi()
        setupDebugUi()
    }

    protected open fun setupCloseButtonViewUi() {
        when (val view = closeButtonView) {
            is ImageView -> {
                view.contentDescription = resources.getString(R.string.bouncer_close_button_description)
                if (isBackgroundDark()) {
                    view.setDrawable(R.drawable.bouncer_close_button_dark)
                } else {
                    view.setDrawable(R.drawable.bouncer_close_button_light)
                }
            }
            is TextView -> {
                view.text = resources.getString(R.string.bouncer_close_button_description)
                if (isBackgroundDark()) {
                    view.setTextColor(getColorByRes(R.color.bouncerCloseButtonDarkColor))
                } else {
                    view.setTextColor(getColorByRes(R.color.bouncerCloseButtonLightColor))
                }
            }
        }
    }

    protected open fun setupTorchButtonViewUi() {
        torchButtonView.setVisible(isFlashlightSupported ?: false)
        when (val view = torchButtonView) {
            is ImageView -> {
                view.contentDescription = resources.getString(R.string.bouncer_torch_button_description)
                if (isBackgroundDark()) {
                    if (isFlashlightOn) {
                        view.setDrawable(R.drawable.bouncer_flash_on_dark)
                    } else {
                        view.setDrawable(R.drawable.bouncer_flash_off_dark)
                    }
                } else {
                    if (isFlashlightOn) {
                        view.setDrawable(R.drawable.bouncer_flash_on_light)
                    } else {
                        view.setDrawable(R.drawable.bouncer_flash_off_light)
                    }
                }
            }
            is TextView -> {
                view.text = resources.getString(R.string.bouncer_torch_button_description)
                if (isBackgroundDark()) {
                    view.setTextColor(getColorByRes(R.color.bouncerFlashButtonDarkColor))
                } else {
                    view.setTextColor(getColorByRes(R.color.bouncerFlashButtonLightColor))
                }
            }
        }
    }

    protected open fun setupInstructionsViewUi() {
        instructionsTextView.textSize = resources.getDimension(R.dimen.bouncerInstructionsTextSize)
        instructionsTextView.typeface = Typeface.DEFAULT_BOLD
        instructionsTextView.gravity = Gravity.CENTER

        if (isBackgroundDark()) {
            instructionsTextView.setTextColor(getColorByRes(R.color.bouncerInstructionsColorDark))
        } else {
            instructionsTextView.setTextColor(getColorByRes(R.color.bouncerInstructionsColorLight))
        }
    }

    protected open fun setupSecurityNoticeUi() {
        securityTextView.textSize = resources.getDimension(R.dimen.bouncerSecurityTextSize)
        securityIconView.contentDescription = resources.getString(R.string.bouncer_security_description)

        if (isBackgroundDark()) {
            securityTextView.setTextColor(getColorByRes(R.color.bouncerSecurityColorDark))
            securityIconView.setDrawable(R.drawable.bouncer_lock_dark)
        } else {
            securityTextView.setTextColor(getColorByRes(R.color.bouncerSecurityColorLight))
            securityIconView.setDrawable(R.drawable.bouncer_lock_light)
        }
    }

    protected open fun setupCardDetailsUi() {
        cardNumberTextView.setTextColor(getColorByRes(R.color.bouncerCardPanColor))
        cardNumberTextView.textSize = resources.getDimension(R.dimen.bouncerPanTextSize)
        cardNumberTextView.gravity = Gravity.CENTER
        cardNumberTextView.typeface = Typeface.DEFAULT_BOLD
        cardNumberTextView.setShadowLayer(resources.getDimension(R.dimen.bouncerPanStrokeSize), 0F, 0F, getColorByRes(R.color.bouncerCardPanOutlineColor))

        cardNameTextView.setTextColor(getColorByRes(R.color.bouncerCardNameColor))
        cardNameTextView.textSize = resources.getDimension(R.dimen.bouncerNameTextSize)
        cardNameTextView.gravity = Gravity.CENTER
        cardNameTextView.typeface = Typeface.DEFAULT_BOLD
        cardNameTextView.setShadowLayer(resources.getDimension(R.dimen.bouncerNameStrokeSize), 0F, 0F, getColorByRes(R.color.bouncerCardNameOutlineColor))
    }

    protected open fun setupDebugUi() {
        debugImageView.contentDescription = resources.getString(R.string.bouncer_debug_description)
        debugImageView.setVisible(Config.isDebug)
        debugOverlayView.setVisible(Config.isDebug)
    }

    private fun setupLogoUi() {
        if (isBackgroundDark()) {
            logoView.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.bouncer_logo_dark_background)
            )
        } else {
            logoView.setImageDrawable(
                ContextCompat.getDrawable(this, R.drawable.bouncer_logo_light_background)
            )
        }

        logoView.contentDescription = resources.getString(R.string.bouncer_cardscan_logo)
        logoView.setVisible(displayCardScanLogo)
    }

    private fun setupVersionUi() {
        versionTextView.text = getSdkVersion()
        versionTextView.textSize = resources.getDimension(R.dimen.bouncerSecurityTextSize)
        versionTextView.setVisible(Config.isDebug)

        if (isBackgroundDark()) {
            versionTextView.setTextColor(getColorByRes(R.color.bouncerSecurityColorDark))
        }
    }

    protected open fun setupUiConstraints() {
        setupCloseButtonViewConstraints()
        setupTorchButtonViewConstraints()
        setupViewFinderConstraints()
        setupInstructionsViewConstraints()
        setupSecurityNoticeConstraints()
        setupCardDetailsConstraints()
        setupDebugConstraints()
    }

    protected open fun setupCloseButtonViewConstraints() {
        ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            closeButtonView.layoutParams = this
        }
    }

    protected open fun setupTorchButtonViewConstraints() {
        ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            torchButtonView.layoutParams = this
        }
    }

    protected open fun setupViewFinderConstraints() {
        ConstraintLayout.LayoutParams(0, 0).apply {
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            viewFinderBackgroundView.layoutParams = this
        }

        ConstraintLayout.LayoutParams(0, 0).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerViewFinderMargin)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerViewFinderMargin)
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerViewFinderMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerViewFinderMargin)

            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID

            verticalBias = resources.getDimension(R.dimen.bouncerViewFinderVerticalBias)
            horizontalBias = resources.getDimension(R.dimen.bouncerViewFinderHorizontalBias)

            dimensionRatio = viewFinderAspectRatio

            viewFinderWindowView.layoutParams = this
            viewFinderBorderView.layoutParams = this
        }
    }

    protected open fun setupInstructionsViewConstraints() {
        ConstraintLayout.LayoutParams(
            0, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            bottomToTop = viewFinderWindowView.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID

            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerInstructionsMargin)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerInstructionsMargin)
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerInstructionsMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerInstructionsMargin)

            instructionsTextView.layoutParams = this
        }
    }

    protected open fun setupSecurityNoticeConstraints() {
        ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            0, // height
        ).apply {
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerSecurityIconMargin)
            horizontalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED

            topToTop = securityTextView.id
            bottomToBottom = securityTextView.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToStart = securityTextView.id

            securityIconView.layoutParams = this
        }

        ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerSecurityMargin)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerSecurityMargin)

            securityTextView.layoutParams = this
        }
    }

    protected open fun setupCardDetailsConstraints() {
        ConstraintLayout.LayoutParams(
            0, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerCardDetailsMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerCardDetailsMargin)
            verticalChainStyle = ConstraintLayout.LayoutParams.CHAIN_PACKED

            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToTop = cardNameTextView.id
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID

            cardNumberTextView.layoutParams = this
        }

        ConstraintLayout.LayoutParams(
            0, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerCardDetailsMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerCardDetailsMargin)

            topToBottom = cardNumberTextView.id
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID

            cardNameTextView.layoutParams = this
        }
    }

    protected open fun setupDebugConstraints() {
        ConstraintLayout.LayoutParams(
            resources.getDimensionPixelSize(R.dimen.bouncerDebugWindowWidth), // width
            0, // height
        ).apply {
            dimensionRatio = viewFinderAspectRatio

            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID

            debugImageView.layoutParams = this
            debugOverlayView.layoutParams = this
        }

    }

    private fun setupLogoConstraints() {
        ConstraintLayout.LayoutParams(
            dpToPixels(LOGO_WIDTH_DP),           // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            topToTop = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
            logoView.layoutParams = this
        }
    }

    private fun setupVersionConstraints() {
        ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {

        }
    }

    private var scanStatePrevious: ScanState? = null
    protected var scanState: ScanState = ScanState.NotFound
        private set

    /**
     * Change the state of the scanner.
     */
    protected fun changeScanState(newState: ScanState): Boolean {
        if (newState == scanStatePrevious) {
            return false
        }

        scanState = newState
        displayState(newState, scanStatePrevious)
        scanStatePrevious = newState
        return true
    }

    protected open fun displayState(newState: ScanState, previousState: ScanState?) {
        when (newState) {
            is ScanState.NotFound -> {
                viewFinderBackgroundView.setBackgroundColor(getColorByRes(R.color.bouncerNotFoundBackground))
                viewFinderWindowView.setBackgroundResource(R.drawable.bouncer_card_background_not_found)
                viewFinderBorderView.setAnimated(R.drawable.bouncer_card_border_not_found)
                instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
                cardNumberTextView.hide()
                cardNameTextView.hide()
            }
            is ScanState.FoundShort -> {
                viewFinderBackgroundView.setBackgroundColor(getColorByRes(R.color.bouncerFoundBackground))
                viewFinderWindowView.setBackgroundResource(R.drawable.bouncer_card_background_found)
                viewFinderBorderView.setAnimated(R.drawable.bouncer_card_border_found)
                instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
                instructionsTextView.fadeIn()
            }
            is ScanState.FoundLong -> {
                viewFinderBackgroundView.setBackgroundColor(getColorByRes(R.color.bouncerFoundBackground))
                viewFinderWindowView.setBackgroundResource(R.drawable.bouncer_card_background_found)
                viewFinderBorderView.setAnimated(R.drawable.bouncer_card_border_found_long)
                instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
                instructionsTextView.fadeIn()
            }
            is ScanState.Correct -> {
                viewFinderBackgroundView.setBackgroundColor(getColorByRes(R.color.bouncerCorrectBackground))
                viewFinderWindowView.setBackgroundResource(R.drawable.bouncer_card_background_correct)
                viewFinderBorderView.setAnimated(R.drawable.bouncer_card_border_correct)
                instructionsTextView.fadeOut()
            }
            is ScanState.Wrong -> {
                viewFinderBackgroundView.setBackgroundColor(getColorByRes(R.color.bouncerWrongBackground))
                viewFinderWindowView.setBackgroundResource(R.drawable.bouncer_card_background_wrong)
                viewFinderBorderView.setAnimated(R.drawable.bouncer_card_border_wrong)
                instructionsTextView.setText(R.string.bouncer_scanned_wrong_card)
            }
        }
    }

    override fun onFlashlightStateChanged(flashlightOn: Boolean) {
        setupUiComponents()
    }

    override fun prepareCamera(onCameraReady: () -> Unit) {
        previewFrame.post {
            viewFinderBackgroundView.setViewFinderRect(viewFinderWindowView.asRect())
            onCameraReady()
        }
    }

    override fun onFlashSupported(supported: Boolean) {
        isFlashlightSupported = supported
        torchButtonView.setVisible(supported)
    }

    override fun onCameraStreamAvailable(cameraStream: Flow<Bitmap>) {
        TODO("Not yet implemented")
    }

    override fun onInvalidApiKey() {
        TODO("Not yet implemented")
    }
}