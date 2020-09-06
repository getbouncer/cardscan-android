package com.getbouncer.scan.payment.ml.ssd

import com.getbouncer.scan.framework.ml.hardNonMaximumSuppression
import com.getbouncer.scan.framework.ml.ssd.ClassifierScores
import com.getbouncer.scan.framework.ml.ssd.RectForm
import com.getbouncer.scan.framework.ml.ssd.toRectF
import com.getbouncer.scan.framework.util.filterByIndexes
import com.getbouncer.scan.framework.util.filteredIndexes
import com.getbouncer.scan.framework.util.transpose
import kotlin.math.abs
import com.getbouncer.scan.payment.card.QUICK_READ_GROUP_LENGTH
import com.getbouncer.scan.payment.card.QUICK_READ_LENGTH

internal data class OcrFeatureMapSizes(
    val layerOneWidth: Int,
    val layerOneHeight: Int,
    val layerTwoWidth: Int,
    val layerTwoHeight: Int
)

/**
 * The model outputs a particular location or a particular class of each prior before moving
 * on to the next prior. For instance, the model will output probabilities for background
 * class corresponding to all priors before outputting the probability of next class for the
 * first prior. This method serves to rearrange the output if you are using outputs from
 * multiple layers If you use outputs from single layer use the method defined above
 *
 * TODO: simplify this
 */
internal fun rearrangeOCRArray(
    locations: Array<FloatArray>,
    featureMapSizes: OcrFeatureMapSizes,
    numberOfPriors: Int,
    locationsPerPrior: Int
): Array<FloatArray> {
    val totalLocationsForAllLayers =
        featureMapSizes.layerOneWidth * featureMapSizes.layerOneHeight * numberOfPriors * locationsPerPrior +
            featureMapSizes.layerTwoWidth * featureMapSizes.layerTwoHeight * numberOfPriors * locationsPerPrior
    val rearranged = Array(1) { FloatArray(totalLocationsForAllLayers) }
    val featureMapHeights = arrayOf(
        featureMapSizes.layerOneHeight,
        featureMapSizes.layerTwoHeight
    )
    val featureMapWidths = arrayOf(
        featureMapSizes.layerOneWidth,
        featureMapSizes.layerTwoWidth
    )
    val heightIterator = featureMapHeights.iterator()
    val widthIterator = featureMapWidths.iterator()
    var offset = 0

    while (heightIterator.hasNext() && widthIterator.hasNext()) {
        val height = heightIterator.next()
        val width = widthIterator.next()
        val totalNumberOfLocationsForThisLayer = height * width * numberOfPriors * locationsPerPrior
        val stepsForLoop = height - 1
        var j: Int
        var i = 0
        var step = 0
        while (i < totalNumberOfLocationsForThisLayer) {
            while (step < height) {
                j = step
                while (j < totalNumberOfLocationsForThisLayer - stepsForLoop + step) {
                    rearranged[0][offset + i] = locations[0][offset + j]
                    i++
                    j += height
                }
                step++
            }
            offset += totalNumberOfLocationsForThisLayer
        }
    }
    return rearranged
}

/**
 * Applies non-maximum suppression to each class. Picks out the remaining boxes, the class
 * probabilities for classes that are kept, and composes all the information.
 */
fun extractPredictions(
    scores: Array<ClassifierScores>,
    boxes: Array<RectForm>,
    probabilityThreshold: Float,
    intersectionOverUnionThreshold: Float,
    limit: Int?,
    classifierToLabel: (Int) -> Int = { it }
): List<DetectionBox> {
    val predictions = mutableListOf<DetectionBox>()

    val classifiersScores = scores.transpose()

    for (classifier in 1 until classifiersScores.size) { // skip the background classifier (index = 0)
        val classifierScores = classifiersScores[classifier]
        val filteredIndexes = classifierScores.filteredIndexes { it >= probabilityThreshold }

        if (filteredIndexes.isNotEmpty()) {
            val filteredScores = classifierScores.filterByIndexes(filteredIndexes)
            val filteredBoxes = boxes.filterByIndexes(filteredIndexes)

            val indexes =
                hardNonMaximumSuppression(
                    boxes = filteredBoxes,
                    probabilities = filteredScores,
                    iouThreshold = intersectionOverUnionThreshold,
                    limit = limit
                )
            for (index in indexes) {
                predictions.add(
                    DetectionBox(
                        rect = filteredBoxes[index].toRectF(),
                        confidence = filteredScores[index],
                        label = classifierToLabel(classifier)
                    )
                )
            }
        }
    }

    return predictions
}

/**
 * Filter out boxes that are outside of the same vertical line. This is done to exclude
 */

fun determineLayoutAndFilter(detectedBoxes: List<DetectionBox>, verticalOffset: Float ): List<DetectionBox> {

    if (detectedBoxes.isEmpty()) {
        return detectedBoxes
    }

    // calculate the median center and height of each digit in the image
    val centers = detectedBoxes.map { it.rect.centerY() }.sorted()
    val heights = detectedBoxes.map { it.rect.height() }.sorted()

    val medianCenter = centers.elementAt(centers.size / 2)
    val medianHeight = heights.elementAt(heights.size / 2)
    val aggregateDeviation = centers.map { abs(it - medianCenter) }.sum()

    return if (aggregateDeviation > verticalOffset * medianHeight && detectedBoxes.size == QUICK_READ_LENGTH ) {
        return detectedBoxes.sortedBy { it.rect.centerY() }
            .chunked(QUICK_READ_GROUP_LENGTH)
            .map { it.sortedBy { detectionBox -> detectionBox.rect.left }}.flatten()
    }
    else {
        detectedBoxes.filter { abs(it.rect.centerY() - medianCenter) <= medianHeight }
    }


}
