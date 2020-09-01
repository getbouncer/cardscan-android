package com.getbouncer.scan.payment.ml.ssd

import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import com.getbouncer.scan.framework.util.maxAspectRatioInSize
import com.getbouncer.scan.framework.util.scaleAndCenterWithin
import com.getbouncer.scan.framework.util.scaled
import com.getbouncer.scan.framework.util.size
import com.getbouncer.scan.payment.crop
import com.getbouncer.scan.payment.size
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

internal fun calculateObjectDetectionFromCardFinder(previewImage: Size, cardFinder: Rect): Rect {
    val objectDetectionSquareSize = maxAspectRatioInSize(previewImage, 1F)
    return Rect(
        /* left */ max(0, cardFinder.centerX() - objectDetectionSquareSize.width / 2),
        /* top */ max(0, cardFinder.centerY() - objectDetectionSquareSize.height / 2),
        /* right */ min(previewImage.width, cardFinder.centerX() + objectDetectionSquareSize.width / 2),
        /* bottom */ min(previewImage.height, cardFinder.centerY() + objectDetectionSquareSize.height / 2)
    )
}

/**
 * Calculate what portion of the full image should be cropped for object detection based on
 * the position of card finder within the preview image.
 */
private fun calculateObjectDetectionImageCrop(
    fullImage: Bitmap,
    previewSize: Size,
    cardFinder: Rect
): Rect {
    require(
        cardFinder.left >= 0 &&
            cardFinder.right <= previewSize.width &&
            cardFinder.top >= 0 &&
            cardFinder.bottom <= previewSize.height
    ) { "Card finder is outside preview image bounds" }

    // Calculate the object detection square based on the card finder, limited by the preview
    val objectDetectionSquare =
        calculateObjectDetectionFromCardFinder(
            previewSize,
            cardFinder
        )

    val scaledPreviewImage = previewSize.scaleAndCenterWithin(fullImage.size())
    val previewScale = scaledPreviewImage.width().toFloat() / previewSize.width

    // Scale the objectDetectionSquare to match the scaledPreviewImage
    val scaledObjectDetectionSquare = Rect(
        (objectDetectionSquare.left * previewScale).roundToInt(),
        (objectDetectionSquare.top * previewScale).roundToInt(),
        (objectDetectionSquare.right * previewScale).roundToInt(),
        (objectDetectionSquare.bottom * previewScale).roundToInt()
    )

    // Position the scaledObjectDetectionSquare on the fullImage
    return Rect(
        max(0, scaledObjectDetectionSquare.left + scaledPreviewImage.left),
        max(0, scaledObjectDetectionSquare.top + scaledPreviewImage.top),
        min(fullImage.width, scaledObjectDetectionSquare.right + scaledPreviewImage.left),
        min(fullImage.height, scaledObjectDetectionSquare.bottom + scaledPreviewImage.top)
    )
}

/**
 * Calculate what portion of the full image should be cropped for object detection based on
 * the position of card finder within the preview image.
 */
fun cropImageForObjectDetect(
    fullImage: Bitmap,
    previewSize: Size,
    cardFinder: Rect
): Bitmap = fullImage.crop(
    calculateObjectDetectionImageCrop(
        fullImage,
        previewSize,
        cardFinder
    )
)

fun calculateCardFinderCoordinatesFromObjectDetection(rect: RectF, previewImage: Size, cardFinder: Rect): RectF {
    val objectDetection =
        calculateObjectDetectionFromCardFinder(
            previewImage,
            cardFinder
        )
    val scaled = rect.scaled(objectDetection.size())
    return RectF(
        /* left */ (scaled.left - (objectDetection.width() / 2 - cardFinder.width() / 2)) / cardFinder.width(),
        /* top */ (scaled.top - (objectDetection.height() / 2 - cardFinder.height() / 2)) / cardFinder.height(),
        /* right */ (scaled.right - (objectDetection.width() / 2 - cardFinder.width() / 2)) / cardFinder.width(),
        /* bottom */ (scaled.bottom - (objectDetection.height() / 2 - cardFinder.height() / 2)) / cardFinder.height()
    )
}
