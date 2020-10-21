package com.getbouncer.scan.payment

import androidx.annotation.Keep

@Keep
data class FrameDetails(
    val panSideConfidence: Float,
    val noPanSideConfidence: Float,
    val noCardConfidence: Float,
    val hasPan: Boolean,
)
