@file:JvmName("SSD")

package com.getbouncer.cardscan.base.ssd

import android.graphics.RectF
import com.getbouncer.cardscan.base.ssd.domain.ClassifierScores
import com.getbouncer.cardscan.base.ssd.domain.RectForm
import com.getbouncer.cardscan.base.ssd.domain.toRectF
import com.getbouncer.cardscan.base.util.filterByIndexes
import com.getbouncer.cardscan.base.util.filteredIndexes
import com.getbouncer.cardscan.base.util.transpose
import org.json.JSONException
import org.json.JSONObject

data class DetectionBox(

    /**
     * The rectangle percentage of the original image.
     */
    val rect: RectF,

    /**
     * The size of the original image.
     */
    val imageSize: Size,

    /**
     * Confidence value that the label applies to the rectangle.
     */
    val confidence: Float,

    /**
     * The label for this box.
     */
    val label: Int
) {
    fun toJson(): JSONObject {
        return try {
            val result = JSONObject()
            result.put("x_min", this.rect.left / imageSize.width)
            result.put("y_min", this.rect.top / imageSize.height)
            result.put("width", (this.rect.width()) / imageSize.width)
            result.put("height", (this.rect.height()) / imageSize.height)
            result.put("label", this.label)
            result.put("confidence", this.confidence.toDouble())
            result
        } catch (je: JSONException) {
            je.printStackTrace()
            JSONObject()
        }
    }
}

data class OcrFeatureMapSizes(
    val layerOneWidth: Int,
    val layerOneHeight: Int,
    val layerTwoWidth: Int,
    val layerTwoHeight: Int
)

/**
 * TODO: simplify this
 */
fun rearrangeOCRArray(
    locations: Array<FloatArray>,
    featureMapSizes: OcrFeatureMapSizes,
    numberOfPriors: Int,
    locationsPerPrior: Int
): Array<FloatArray> {
    val totalLocationsForAllLayers = (
        (
            featureMapSizes.layerOneWidth
            * featureMapSizes.layerOneHeight
            * numberOfPriors
            * locationsPerPrior
        ) + (
            featureMapSizes.layerTwoWidth
            * featureMapSizes.layerTwoHeight
            * numberOfPriors
            * locationsPerPrior
        )
    )
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
 * The model outputs a particular location or a particular class of each prior before moving
 * on to the next prior. For instance, the model will output probabilities for background
 * class corresponding to all priors before outputting the probability of next class for the
 * first prior. This method serves to rearrange the output if you are using outputs from
 * multiple layers If you use outputs from single layer use the method defined above
 *
 * TODO: simplify this
 */
fun rearrangeArray(
    locations: Array<FloatArray>,
    featureMapSizes: IntArray,
    numberOfPriors: Int,
    locationsPerPrior: Int
): Array<FloatArray> {
    val totalLocationsForAllLayers = featureMapSizes.map { it * it * numberOfPriors * locationsPerPrior }.sum()
    val rearranged = FloatArray(totalLocationsForAllLayers)

    var offset = 0

    for (steps in featureMapSizes) {
        val totalNumberOfLocationsForThisLayer = steps * steps * numberOfPriors * locationsPerPrior
        val stepsForLoop = steps - 1
        var j: Int
        var i = 0
        var step = 0

        while (i < totalNumberOfLocationsForThisLayer) {
            while (step < steps) {

                j = step

                while (j < totalNumberOfLocationsForThisLayer - stepsForLoop + step) {
                    rearranged[offset + i] = locations[0][offset + j]
                    i++
                    j += steps
                }

                step++
            }

            offset += totalNumberOfLocationsForThisLayer
        }
    }

    return arrayOf(rearranged)
}

/**
 * Applies non-maximum suppression to each class. Picks out the remaining boxes, the class
 * probabilities for classes that are kept, and composes all the information.
 */
fun extractPredictions(
        scores: Array<ClassifierScores>,
        boxes: Array<RectForm>,
        imageSize: Size,
        probabilityThreshold: Float,
        intersectionOverUnionThreshold: Float,
        limit: Int?,
        classifierToLabel: (Int) -> Int = { it }
): List<DetectionBox> {
    val predictions = mutableListOf<DetectionBox>()

    val classifiersScores = scores.transpose()

    for (classifier in 1 until classifiersScores.size) {  // skip the background classifier (index = 0)
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
                        imageSize = imageSize,
                        confidence = filteredScores[index],
                        label = classifierToLabel(classifier)
                    )
                )
            }
        }
    }

    return predictions
}
