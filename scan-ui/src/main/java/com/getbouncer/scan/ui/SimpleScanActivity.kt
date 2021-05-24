package com.getbouncer.scan.ui

import android.annotation.SuppressLint
import android.content.res.Resources
import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.Typeface
import android.os.Bundle
import android.util.Size
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.TextView
import androidx.constraintlayout.widget.ConstraintLayout
import androidx.constraintlayout.widget.ConstraintSet
import androidx.core.content.ContextCompat
import com.getbouncer.scan.camera.CameraPreviewImage
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.util.getSdkVersion
import com.getbouncer.scan.ui.util.asRect
import com.getbouncer.scan.ui.util.dpToPixels
import com.getbouncer.scan.ui.util.getColorByRes
import com.getbouncer.scan.ui.util.getDrawableByRes
import com.getbouncer.scan.ui.util.getFloatResource
import com.getbouncer.scan.ui.util.hide
import com.getbouncer.scan.ui.util.setDrawable
import com.getbouncer.scan.ui.util.setTextSizeByRes
import com.getbouncer.scan.ui.util.setVisible
import com.getbouncer.scan.ui.util.show
import com.getbouncer.scan.ui.util.startAnimation
import kotlinx.coroutines.flow.Flow
import kotlin.math.min
import kotlin.math.roundToInt

abstract class SimpleScanActivity : ScanActivity() {

    /**
     * The state of the scan flow. This can be expanded if [displayState] is overridden to handle
     * the added states.
     */
    abstract class ScanState(val isFinal: Boolean) {
        object NotFound : ScanState(isFinal = false)
        object FoundShort : ScanState(isFinal = false)
        object FoundLong : ScanState(isFinal = false)
        object Correct : ScanState(isFinal = true)
        object Wrong : ScanState(isFinal = false)
    }

    companion object {
        private const val LOGO_WIDTH_DP = 100
    }

    /**
     * The main layout used to render the scan view.
     */
    protected open val layout: ConstraintLayout by lazy { ConstraintLayout(this) }

    /**
     * The frame where the camera preview will be displayed. This is usually the full screen.
     */
    override val previewFrame: ViewGroup by lazy { FrameLayout(this) }

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
     * The view that a user can tap to swap cameras.
     */
    protected open val swapCameraButtonView: View by lazy { ImageView(this) }

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
    protected open val debugOverlayView: DebugOverlay by lazy { DebugOverlay(this) }

    private val logoView: ImageView by lazy { ImageView(this) }

    protected open val versionTextView: TextView by lazy { TextView(this) }

    /**
     * The aspect ratio of the view finder.
     */
    protected open val viewFinderAspectRatio = "200:126"

    /**
     * Determine if the flashlight is supported.
     */
    protected var isFlashlightSupported: Boolean? = null

    /**
     * Determine if multiple cameras are available.
     */
    protected var hasMultipleCameras: Boolean? = null

    /**
     * The flow used to scan an item.
     */
    protected abstract val scanFlow: ScanFlow

    /**
     * Determine if the background is dark. This is used to set light background vs dark background
     * text and images.
     */
    protected open fun isBackgroundDark(): Boolean =
        viewFinderBackgroundView.getBackgroundLuminance() < 128

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
        swapCameraButtonView.setOnClickListener { toggleCamera() }

        viewFinderBorderView.setOnTouchListener { _, e ->
            setFocus(PointF(e.x + viewFinderWindowView.left, e.y + viewFinderWindowView.top))
            true
        }

        displayState(scanState, scanStatePrevious)
        setContentView(layout)
    }

    override fun onPause() {
        viewFinderBackgroundView.clearOnDrawListener()
        super.onPause()
    }

    override fun onResume() {
        super.onResume()
        scanState = ScanState.NotFound
        viewFinderBackgroundView.setOnDrawListener { setupUiComponents() }
    }

    override fun onDestroy() {
        scanFlow.cancelFlow()
        super.onDestroy()
    }

    /**
     * Add the UI components to the root view.
     */
    protected open fun addUiComponents() {
        layout.id = View.generateViewId()

        appendUiComponents(
            previewFrame,
            viewFinderBackgroundView,
            viewFinderWindowView,
            viewFinderBorderView,
            securityIconView,
            securityTextView,
            instructionsTextView,
            closeButtonView,
            torchButtonView,
            swapCameraButtonView,
            cardNameTextView,
            cardNumberTextView,
            debugImageView,
            debugOverlayView,
            logoView,
            versionTextView,
        )
    }

    /**
     * Append additional UI elements to the view.
     */
    protected fun appendUiComponents(vararg components: View) {
        components.forEach {
            it.id = View.generateViewId()
            layout.addView(it)
        }
    }

    protected open fun setupUiComponents() {
        setupCloseButtonViewUi()
        setupTorchButtonViewUi()
        setupSwapCameraButtonViewUi()
        setupViewFinderViewUI()
        setupInstructionsViewUi()
        setupSecurityNoticeUi()
        setupCardDetailsUi()
        setupDebugUi()
    }

    protected open fun setupCloseButtonViewUi() {
        when (val view = closeButtonView) {
            is ImageView -> {
                view.contentDescription = getString(R.string.bouncer_close_button_description)
                if (isBackgroundDark()) {
                    view.setDrawable(R.drawable.bouncer_close_button_dark)
                } else {
                    view.setDrawable(R.drawable.bouncer_close_button_light)
                }
            }
            is TextView -> {
                view.text = getString(R.string.bouncer_close_button_description)
                if (isBackgroundDark()) {
                    view.setTextColor(getColorByRes(R.color.bouncerCloseButtonDarkColor))
                } else {
                    view.setTextColor(getColorByRes(R.color.bouncerCloseButtonLightColor))
                }
            }
        }
    }

    protected open fun setupTorchButtonViewUi() {
        torchButtonView.setVisible(isFlashlightSupported == true)
        when (val view = torchButtonView) {
            is ImageView -> {
                view.contentDescription = getString(R.string.bouncer_torch_button_description)
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
                view.text = getString(R.string.bouncer_torch_button_description)
                if (isBackgroundDark()) {
                    view.setTextColor(getColorByRes(R.color.bouncerFlashButtonDarkColor))
                } else {
                    view.setTextColor(getColorByRes(R.color.bouncerFlashButtonLightColor))
                }
            }
        }
    }

    protected open fun setupSwapCameraButtonViewUi() {
        swapCameraButtonView.setVisible(hasMultipleCameras == true)
        when (val view = swapCameraButtonView) {
            is ImageView -> {
                view.contentDescription = getString(R.string.bouncer_swap_camera_button_description)
                if (isBackgroundDark()) {
                    view.setDrawable(R.drawable.bouncer_camera_swap_dark)
                } else {
                    view.setDrawable(R.drawable.bouncer_camera_swap_light)
                }
            }
            is TextView -> {
                view.text = getString(R.string.bouncer_swap_camera_button_description)
                if (isBackgroundDark()) {
                    view.setTextColor(getColorByRes(R.color.bouncerCameraSwapButtonDarkColor))
                } else {
                    view.setTextColor(getColorByRes(R.color.bouncerCameraSwapButtonLightColor))
                }
            }
        }
    }

    protected open fun setupViewFinderViewUI() {
        viewFinderBorderView.background = getDrawableByRes(R.drawable.bouncer_card_border_not_found)
    }

    protected open fun setupInstructionsViewUi() {
        instructionsTextView.setTextSizeByRes(R.dimen.bouncerInstructionsTextSize)
        instructionsTextView.typeface = Typeface.DEFAULT_BOLD
        instructionsTextView.gravity = Gravity.CENTER

        if (isBackgroundDark()) {
            instructionsTextView.setTextColor(getColorByRes(R.color.bouncerInstructionsColorDark))
        } else {
            instructionsTextView.setTextColor(getColorByRes(R.color.bouncerInstructionsColorLight))
        }
    }

    protected open fun setupSecurityNoticeUi() {
        securityTextView.text = getString(R.string.bouncer_card_scan_security)
        securityTextView.setTextSizeByRes(R.dimen.bouncerSecurityTextSize)
        securityIconView.contentDescription = getString(R.string.bouncer_security_description)

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
        cardNumberTextView.setTextSizeByRes(R.dimen.bouncerPanTextSize)
        cardNumberTextView.gravity = Gravity.CENTER
        cardNumberTextView.typeface = Typeface.DEFAULT_BOLD
        cardNumberTextView.setShadowLayer(getFloatResource(R.dimen.bouncerPanStrokeSize), 0F, 0F, getColorByRes(R.color.bouncerCardPanOutlineColor))

        cardNameTextView.setTextColor(getColorByRes(R.color.bouncerCardNameColor))
        cardNameTextView.setTextSizeByRes(R.dimen.bouncerNameTextSize)
        cardNameTextView.gravity = Gravity.CENTER
        cardNameTextView.typeface = Typeface.DEFAULT_BOLD
        cardNameTextView.setShadowLayer(getFloatResource(R.dimen.bouncerNameStrokeSize), 0F, 0F, getColorByRes(R.color.bouncerCardNameOutlineColor))
    }

    protected open fun setupDebugUi() {
        debugImageView.contentDescription = getString(R.string.bouncer_debug_description)
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

        logoView.contentDescription = getString(R.string.bouncer_cardscan_logo)
        logoView.setVisible(Config.displayLogo)
    }

    private fun setupVersionUi() {
        versionTextView.text = getSdkVersion()
        versionTextView.setTextSizeByRes(R.dimen.bouncerSecurityTextSize)
        versionTextView.setVisible(Config.isDebug)

        if (isBackgroundDark()) {
            versionTextView.setTextColor(getColorByRes(R.color.bouncerSecurityColorDark))
        }
    }

    protected open fun setupUiConstraints() {
        setupPreviewFrameConstraints()
        setupCloseButtonViewConstraints()
        setupTorchButtonViewConstraints()
        setupSwapCameraButtonViewConstraints()
        setupViewFinderConstraints()
        setupInstructionsViewConstraints()
        setupSecurityNoticeConstraints()
        setupCardDetailsConstraints()
        setupDebugConstraints()
    }

    protected open fun setupPreviewFrameConstraints() {
        previewFrame.layoutParams = ConstraintLayout.LayoutParams(0, 0)
        previewFrame.constrainToParent()
    }

    protected open fun setupCloseButtonViewConstraints() {
        closeButtonView.layoutParams = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
        }

        closeButtonView.addConstraints {
            connect(it.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        }
    }

    protected open fun setupTorchButtonViewConstraints() {
        torchButtonView.layoutParams = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
        }

        torchButtonView.addConstraints {
            connect(it.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(it.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }

    protected open fun setupSwapCameraButtonViewConstraints() {
        swapCameraButtonView.layoutParams = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerButtonMargin)
        }

        swapCameraButtonView.addConstraints {
            connect(it.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
        }
    }

    protected open fun setupViewFinderConstraints() {
        viewFinderBackgroundView.layoutParams = ConstraintLayout.LayoutParams(0, 0)

        viewFinderBackgroundView.constrainToParent()

        val screenSize = Resources.getSystem().displayMetrics.let {
            Size(it.widthPixels, it.heightPixels)
        }
        val viewFinderMargin = (min(screenSize.width, screenSize.height) * getFloatResource(R.dimen.bouncerViewFinderMargin)).roundToInt()

        listOf(viewFinderWindowView, viewFinderBorderView).forEach { view ->
            view.layoutParams = ConstraintLayout.LayoutParams(0, 0).apply {
                topMargin = viewFinderMargin
                bottomMargin = viewFinderMargin
                marginStart = viewFinderMargin
                marginEnd = viewFinderMargin
            }

            view.constrainToParent()
            view.addConstraints {
                setVerticalBias(it.id, getFloatResource(R.dimen.bouncerViewFinderVerticalBias))
                setHorizontalBias(it.id, getFloatResource(R.dimen.bouncerViewFinderHorizontalBias))

                setDimensionRatio(it.id, viewFinderAspectRatio)
            }
        }
    }

    protected open fun setupInstructionsViewConstraints() {
        instructionsTextView.layoutParams = ConstraintLayout.LayoutParams(
            0, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerInstructionsMargin)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerInstructionsMargin)
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerInstructionsMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerInstructionsMargin)
        }

        instructionsTextView.addConstraints {
            connect(it.id, ConstraintSet.BOTTOM, viewFinderWindowView.id, ConstraintSet.TOP)
            connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(it.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }

    protected open fun setupSecurityNoticeConstraints() {
        securityIconView.layoutParams = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            0, // height
        ).apply {
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerSecurityIconMargin)
        }

        securityTextView.layoutParams = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerSecurityMargin)
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerSecurityMargin)
        }

        securityIconView.addConstraints {
            connect(it.id, ConstraintSet.TOP, securityTextView.id, ConstraintSet.TOP)
            connect(it.id, ConstraintSet.BOTTOM, securityTextView.id, ConstraintSet.BOTTOM)
            connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(it.id, ConstraintSet.END, securityTextView.id, ConstraintSet.START)

            setHorizontalChainStyle(it.id, ConstraintSet.CHAIN_PACKED)
        }

        securityTextView.addConstraints {
            connect(it.id, ConstraintSet.TOP, viewFinderWindowView.id, ConstraintSet.BOTTOM)
            connect(it.id, ConstraintSet.START, securityIconView.id, ConstraintSet.END)
            connect(it.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }

    protected open fun setupCardDetailsConstraints() {
        cardNumberTextView.layoutParams = ConstraintLayout.LayoutParams(
            0, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerCardDetailsMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerCardDetailsMargin)
        }

        cardNameTextView.layoutParams = ConstraintLayout.LayoutParams(
            0, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            marginStart = resources.getDimensionPixelSize(R.dimen.bouncerCardDetailsMargin)
            marginEnd = resources.getDimensionPixelSize(R.dimen.bouncerCardDetailsMargin)

            topToBottom = cardNumberTextView.id
            bottomToBottom = ConstraintLayout.LayoutParams.PARENT_ID
            startToStart = ConstraintLayout.LayoutParams.PARENT_ID
            endToEnd = ConstraintLayout.LayoutParams.PARENT_ID
        }

        cardNumberTextView.addConstraints {
            connect(it.id, ConstraintSet.TOP, viewFinderWindowView.id, ConstraintSet.TOP)
            connect(it.id, ConstraintSet.BOTTOM, cardNameTextView.id, ConstraintSet.TOP)
            connect(it.id, ConstraintSet.START, viewFinderWindowView.id, ConstraintSet.START)
            connect(it.id, ConstraintSet.END, viewFinderWindowView.id, ConstraintSet.END)

            setVerticalChainStyle(it.id, ConstraintSet.CHAIN_PACKED)
        }

        cardNameTextView.addConstraints {
            connect(it.id, ConstraintSet.TOP, cardNumberTextView.id, ConstraintSet.BOTTOM)
            connect(it.id, ConstraintSet.BOTTOM, viewFinderWindowView.id, ConstraintSet.BOTTOM)
            connect(it.id, ConstraintSet.START, viewFinderWindowView.id, ConstraintSet.START)
            connect(it.id, ConstraintSet.END, viewFinderWindowView.id, ConstraintSet.END)
        }
    }

    protected open fun setupDebugConstraints() {
        listOf(debugImageView, debugOverlayView).forEach { view ->
            view.layoutParams = ConstraintLayout.LayoutParams(
                resources.getDimensionPixelSize(R.dimen.bouncerDebugWindowWidth), // width
                0, // height
            )

            view.addConstraints {
                setDimensionRatio(it.id, viewFinderAspectRatio)

                connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
                connect(it.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            }
        }
    }

    private fun setupLogoConstraints() {
        logoView.layoutParams = ConstraintLayout.LayoutParams(
            dpToPixels(LOGO_WIDTH_DP), // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            topMargin = resources.getDimensionPixelSize(R.dimen.bouncerLogoMargin)
        }

        logoView.addConstraints {
            connect(it.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(it.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }

    private fun setupVersionConstraints() {
        versionTextView.layoutParams = ConstraintLayout.LayoutParams(
            ViewGroup.LayoutParams.WRAP_CONTENT, // width
            ViewGroup.LayoutParams.WRAP_CONTENT, // height
        ).apply {
            bottomMargin = resources.getDimensionPixelSize(R.dimen.bouncerLogoMargin)
        }

        versionTextView.addConstraints {
            connect(it.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(it.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }

    private var scanStatePrevious: ScanState? = null
    protected var scanState: ScanState = ScanState.NotFound
        private set

    /**
     * Change the state of the scanner.
     */
    protected fun changeScanState(newState: ScanState): Boolean {
        if (newState == scanStatePrevious || scanStatePrevious?.isFinal == true) {
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
                viewFinderBorderView.startAnimation(R.drawable.bouncer_card_border_not_found)
                instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
                cardNumberTextView.hide()
                cardNameTextView.hide()
            }
            is ScanState.FoundShort -> {
                viewFinderBackgroundView.setBackgroundColor(getColorByRes(R.color.bouncerFoundBackground))
                viewFinderWindowView.setBackgroundResource(R.drawable.bouncer_card_background_found)
                viewFinderBorderView.startAnimation(R.drawable.bouncer_card_border_found)
                instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
                instructionsTextView.show()
            }
            is ScanState.FoundLong -> {
                viewFinderBackgroundView.setBackgroundColor(getColorByRes(R.color.bouncerFoundBackground))
                viewFinderWindowView.setBackgroundResource(R.drawable.bouncer_card_background_found)
                viewFinderBorderView.startAnimation(R.drawable.bouncer_card_border_found_long)
                instructionsTextView.setText(R.string.bouncer_card_scan_instructions)
                instructionsTextView.show()
            }
            is ScanState.Correct -> {
                viewFinderBackgroundView.setBackgroundColor(getColorByRes(R.color.bouncerCorrectBackground))
                viewFinderWindowView.setBackgroundResource(R.drawable.bouncer_card_background_correct)
                viewFinderBorderView.startAnimation(R.drawable.bouncer_card_border_correct)
                instructionsTextView.hide()
            }
            is ScanState.Wrong -> {
                viewFinderBackgroundView.setBackgroundColor(getColorByRes(R.color.bouncerWrongBackground))
                viewFinderWindowView.setBackgroundResource(R.drawable.bouncer_card_background_wrong)
                viewFinderBorderView.startAnimation(R.drawable.bouncer_card_border_wrong)
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

    override fun onSupportsMultipleCameras(supported: Boolean) {
        hasMultipleCameras = supported
        swapCameraButtonView.setVisible(supported)
    }

    /**
     * Add constraints to a view.
     */
    protected inline fun <T : View> T.addConstraints(block: ConstraintSet.(view: T) -> Unit) {
        ConstraintSet().apply {
            clone(layout)
            block(this, this@addConstraints)
            applyTo(layout)
        }
    }

    /**
     * Constrain a view to the top, bottom, start, and end of its parent.
     */
    protected fun <T : View> T.constrainToParent() {
        addConstraints {
            connect(it.id, ConstraintSet.TOP, ConstraintSet.PARENT_ID, ConstraintSet.TOP)
            connect(it.id, ConstraintSet.BOTTOM, ConstraintSet.PARENT_ID, ConstraintSet.BOTTOM)
            connect(it.id, ConstraintSet.START, ConstraintSet.PARENT_ID, ConstraintSet.START)
            connect(it.id, ConstraintSet.END, ConstraintSet.PARENT_ID, ConstraintSet.END)
        }
    }

    /**
     * Once the camera stream is available, start processing images.
     */
    override fun onCameraStreamAvailable(cameraStream: Flow<CameraPreviewImage<Bitmap>>) {
        scanFlow.startFlow(
            context = this,
            imageStream = cameraStream,
            viewFinder = viewFinderWindowView.asRect(),
            lifecycleOwner = this,
            coroutineScope = this,
        )
    }

    override fun onInvalidApiKey() {
        scanFlow.cancelFlow()
    }
}
