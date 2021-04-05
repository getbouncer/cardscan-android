package com.getbouncer.scan.payment.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Log
import android.util.Size
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.framework.UpdatingModelWebFetcher
import com.getbouncer.scan.framework.ml.TFLAnalyzerFactory
import com.getbouncer.scan.framework.ml.TensorFlowLiteAnalyzer
import com.getbouncer.scan.framework.ml.hardNonMaximumSuppression
import com.getbouncer.scan.framework.ml.ssd.rectForm
import com.getbouncer.scan.framework.util.maxAspectRatioInSize
import com.getbouncer.scan.framework.util.scaleAndCenterWithin
import com.getbouncer.scan.framework.util.size
import com.getbouncer.scan.payment.crop
import com.getbouncer.scan.payment.hasOpenGl31
import com.getbouncer.scan.payment.ml.ssd.DetectionBox
import com.getbouncer.scan.payment.ml.yolo.processYoloLayer
import com.getbouncer.scan.payment.scale
import com.getbouncer.scan.payment.size
import com.getbouncer.scan.payment.toRGBByteBuffer
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import kotlin.math.abs
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

private val TRAINED_IMAGE_SIZE = Size(416, 416)

private const val YOLO_POST_PROCESS_CONFIDENCE_THRESHOLD = 0.5f
private val YOLO_ANCHORS = arrayOf(
    arrayOf(
        Pair(81, 82),
        Pair(135, 169),
        Pair(344, 319)
    ),
    arrayOf(
        Pair(10, 14),
        Pair(23, 27),
        Pair(37, 58)
    )
)

/**
 * Model returns the following 4 classes:
 * 0. MM/YY Expiration Date
 * 1: Mostly letters - At most 1 non-character and more letters than other characters
 * 2: Mostly numbers - At most 2 non-numbers and more numbers than other characters
 * 3: Mixed - mix of numbers and characters (any remaining cases)
 **/
private enum class LABELS {
    EXPIRATION_DATE,
    LETTERS,
    NUMBERS,
    MIXED
}
private val NUM_CLASS = LABELS.values().size

private val LAYER_1_SIZE = Size(13, 13)
private val LAYER_2_SIZE = Size(26, 26)

// A YOLO3 constant derived from NUM_CLASS
private val DIM_Z = (NUM_CLASS + 5) * 3

private const val BOX_TOP_DELTA_THRESHOLD = 0.4F
private const val HEIGHT_RATIO_THRESHOLD = 0.3F

class TextDetect private constructor(interpreter: Interpreter) :
    TensorFlowLiteAnalyzer<
        TextDetect.Input,
        Array<ByteBuffer>,
        TextDetect.Prediction,
        Map<Int, Array<Array<Array<FloatArray>>>>>(interpreter) {

    companion object {
        /**
         * Given a card finder region of a preview image, calculate the associated square.
         */
        private fun calculateSquareFromCardFinder(previewBounds: Rect, cardFinder: Rect): Rect {
            val squareSize = maxAspectRatioInSize(previewBounds.size(), 1F)
            return Rect(
                /* left */
                max(previewBounds.left, cardFinder.centerX() - squareSize.width / 2),
                /* top */
                max(previewBounds.top, cardFinder.centerY() - squareSize.height / 2),
                /* right */
                min(previewBounds.right, cardFinder.centerX() + squareSize.width / 2),
                /* bottom */
                min(previewBounds.bottom, cardFinder.centerY() + squareSize.height / 2)
            )
        }

        /**
         * Calculate what portion of the full image should be cropped based on the position of card finder within the
         * preview image.
         */
        private fun calculateCrop(fullImage: Size, previewBounds: Rect, cardFinder: Rect): Rect {
            require(
                cardFinder.left >= previewBounds.left &&
                    cardFinder.right <= previewBounds.right &&
                    cardFinder.top >= previewBounds.top &&
                    cardFinder.bottom <= previewBounds.bottom
            ) { "Card finder is outside preview image bounds" }

            // Calculate the card detection square based on the card finder, limited by the preview
            val square = calculateSquareFromCardFinder(previewBounds, cardFinder)

            val scaledPreviewImage = previewBounds.size().scaleAndCenterWithin(fullImage)
            val previewScale = scaledPreviewImage.width().toFloat() / previewBounds.width()

            // Scale the cardDetectionSquare to match the scaledPreviewImage
            val scaledSquare = Rect(
                (square.left * previewScale).roundToInt(),
                (square.top * previewScale).roundToInt(),
                (square.right * previewScale).roundToInt(),
                (square.bottom * previewScale).roundToInt()
            )

            // Position the scaledCardDetectionSquare on the fullImage
            return Rect(
                max(0, scaledSquare.left + scaledPreviewImage.left),
                max(0, scaledSquare.top + scaledPreviewImage.top),
                min(fullImage.width, scaledSquare.right + scaledPreviewImage.left),
                min(fullImage.height, scaledSquare.bottom + scaledPreviewImage.top),
            )
        }

        fun cropCameraPreview(
            cameraPreviewImage: Bitmap,
            previewBounds: Rect,
            cardFinder: Rect,
        ) = cameraPreviewImage.crop(calculateCrop(cameraPreviewImage.size(), previewBounds, cardFinder))

        /**
         * Convert a camera preview image into a CardDetect input
         */
        fun cameraPreviewToInput(
            cameraPreviewImage: TrackedImage<Bitmap>,
            previewBounds: Rect,
            cardFinder: Rect,
        ) = Input(
            TrackedImage(
                cropCameraPreview(
                    cameraPreviewImage = cameraPreviewImage.image,
                    previewBounds = previewBounds,
                    cardFinder = cardFinder,
                )
                    .scale(TRAINED_IMAGE_SIZE)
                    .toRGBByteBuffer()
                    .also { cameraPreviewImage.tracker.trackResult("text_detect_image_cropped") },
                cameraPreviewImage.tracker,
            )
        )
    }

    data class Input(
        val textDetectImage: TrackedImage<ByteBuffer>,
    )

    data class Prediction(
        val allObjects: List<DetectionBox>,
        val nameBoxes: List<DetectionBox>,
        val expiryBoxes: List<DetectionBox>
    )

    private data class MergedBox(val box: DetectionBox, val subBoxes: List<DetectionBox>)

    private fun postProcessYolo(rawMlOutput: Map<Int, Array<Array<Array<FloatArray>>>>): List<DetectionBox> {
        val results = mutableListOf<DetectionBox>()

        val layerZero = rawMlOutput[0]
        if (layerZero?.isNotEmpty() == true) {
            results.addAll(
                processYoloLayer(
                    layerZero.first(),
                    YOLO_ANCHORS[0],
                    TRAINED_IMAGE_SIZE,
                    NUM_CLASS,
                    YOLO_POST_PROCESS_CONFIDENCE_THRESHOLD
                )
            )
        } else {
            Log.w(Config.logTag, "Unable to resolve YOLO layer 0")
        }

        val layerOne = rawMlOutput[1]
        if (layerOne?.isNotEmpty() == true) {
            results.addAll(
                processYoloLayer(
                    layerOne.first(),
                    YOLO_ANCHORS[1],
                    TRAINED_IMAGE_SIZE,
                    NUM_CLASS,
                    YOLO_POST_PROCESS_CONFIDENCE_THRESHOLD
                )
            )
        } else {
            Log.w(Config.logTag, "Unable to resolve YOLO layer 1")
        }

        return results
    }

    override suspend fun interpretMLOutput(
        data: Input,
        mlOutput: Map<Int, Array<Array<Array<FloatArray>>>>
    ): Prediction {
        val outputBoxes = extractPredictions(postProcessYolo(mlOutput))
        val (panBoxes, nameBoxes) = getNameBox(outputBoxes) ?: (null to null)

        // add our merged pan and name boxes into the set of objects we return
        // for debugging purposes
        val allObjects = mutableListOf<DetectionBox>()
        allObjects.addAll(outputBoxes)
        if (nameBoxes != null && panBoxes != null) {
            allObjects.add(
                DetectionBox(
                    nameBoxes.box.rect,
                    nameBoxes.box.confidence,
                    nameBoxes.box.label + NUM_CLASS // making up a new label for debugging purposes
                )
            )
            allObjects.add(
                DetectionBox(
                    panBoxes.box.rect,
                    panBoxes.box.confidence,
                    panBoxes.box.label + NUM_CLASS // making up a new label for debugging purposes
                )
            )
        }

        return Prediction(
            allObjects,
            nameBoxes?.subBoxes ?: emptyList(),
            outputBoxes.filter { it.label == LABELS.EXPIRATION_DATE.ordinal }.sortedByDescending { it.confidence }.take(2)
        ).also {
            data.textDetectImage.tracker.trackResult("text_detect_prediction_complete")
        }
    }

    /**
     * Find all boxes that are "close" to the [originalBox], including itself. The returned list of boxes is sorted
     * from leftmost to rightmost (by the x value)
     *
     * A box is defined as close to the [originalBox] if:
     * 1. it is the [originalBox]
     * 2. it's not an Expiry box (Expiry boxes should never be merged)
     * 3. the top of the boxes are mostly aligned
     * 4. The height of the box is similar to the height of the original box
     */
    private fun getCloseBoxes(originalBox: DetectionBox, boxes: List<DetectionBox>): List<DetectionBox> {
        fun isSimilarYStart(it: DetectionBox) = abs(it.rect.top - originalBox.rect.top) <
            originalBox.rect.height() * BOX_TOP_DELTA_THRESHOLD
        fun isSimilarHeight(it: DetectionBox) = abs(1F - it.rect.height() / originalBox.rect.height()) < HEIGHT_RATIO_THRESHOLD
        return boxes.filter {
            (it == originalBox) || (it.label != LABELS.EXPIRATION_DATE.ordinal && isSimilarHeight(it)) && isSimilarYStart(it)
        }.sortedBy { it.rect.left }
    }

    /**
     * Run NMS on detection results
     */
    private fun extractPredictions(raw: List<DetectionBox>): List<DetectionBox> {
        val predictions = mutableListOf<DetectionBox>()

        val scores = raw.map { it.confidence }
        val boxes = raw.map { rectForm(it.rect.left, it.rect.top, it.rect.right, it.rect.bottom) }

        val indexes =
            hardNonMaximumSuppression(
                boxes = boxes.toTypedArray(),
                probabilities = scores.toFloatArray(),
                iouThreshold = 0.4f,
                limit = null
            )

        for (index in indexes) {
            predictions.add(
                raw[index]
            )
        }
        return predictions
    }

    /**
     * From a list of raw boxes, determine the likely PAN location and the name
     */
    private fun getNameBox(boxes: List<DetectionBox>): Pair<MergedBox, MergedBox?>? {
        val metaBoxes = mergeAllBoxes(boxes)
        val panMetaBox = getPanBox(metaBoxes) ?: return null
        return Pair(
            panMetaBox,
            metaBoxes.asSequence().filter {
                // filter for merged boxes that are classified as text
                it.box.label == LABELS.LETTERS.ordinal
            }.map {
                // gather the name score for this box
                Pair(predictionToNameScoreNew(it, panMetaBox), it)
            }.maxByOrNull {
                // get the box for the highest score
                it.first
            }?.second
        )
    }

    /**
     * Given a candidate box, returns the name score derived from its relationship with the panBox
     */
    private fun predictionToNameScoreNew(candidateBox: MergedBox, panBox: MergedBox): Float {
        val partialScores = arrayOf(
            ln(candidateBox.box.confidence),
            getXDistScore(candidateBox.box, panBox.box),
            getHeightScore(candidateBox.box, panBox.box),
            calculateNameWidthScore(candidateBox)
        )
        return partialScores.sum()
    }

    /**
     * Find the MergedBox who's the most like to be the PAN.
     * Returns the mergedbox that has the most number of merged digit boxes
     */
    private fun getPanBox(boxes: List<MergedBox>) = boxes.maxByOrNull { mergedBox ->
        mergedBox.subBoxes.filter {
            it.label == 2
        }.size
    }

    /**
     * Given a list of [DetectionBox], [boxes], returns a list of [MergedBox] where
     * each MergedBox consists of a sublist of [boxes] who are close enough in size and orientation.
     * The goal is to join words belonging to the same entity to the same [MergedBox]. For example,
     * we want to join the four separate detection things
     */
    private fun mergeAllBoxes(boxes: List<DetectionBox>): List<MergedBox> {
        val unmergedBoxes = boxes.toMutableList()
        val mergedBoxes = mutableListOf<MergedBox>()

        while (unmergedBoxes.isNotEmpty()) {
            val candidateBox = unmergedBoxes.first()
            val subBoxes = getCloseBoxes(candidateBox, unmergedBoxes)
            val metaConfidence = subBoxes.maxByOrNull { it.confidence }?.confidence ?: 0f
            mergedBoxes.add(
                MergedBox(
                    DetectionBox(
                        RectF(
                            subBoxes.first().rect.left,
                            subBoxes.first().rect.top,
                            subBoxes.last().rect.right,
                            subBoxes.last().rect.bottom
                        ),
                        metaConfidence,
                        subBoxes.first().label
                    ),
                    subBoxes
                )
            )
            unmergedBoxes.removeAll(subBoxes)
        }
        return mergedBoxes
    }

    /**
     * Calculates a component of the name score derived from the delta of rect.left the
     * top left corner of the proposed name box and the pan box
     */
    private fun getXDistScore(prediction: DetectionBox, panBox: DetectionBox): Float {
        val mean = -0.015011064206789725f
        val std = 0.45382757813736924f
        val panHeight = panBox.rect.height()
        val xDist = (panBox.rect.left - prediction.rect.left) / panHeight
        return (-1 / 2f) * ((xDist - mean) / std).pow(2)
    }

    /**
     * Calculates a component of the name score derived from the height ratio between the
     * proposed name box and the pan box
     */
    private fun getHeightScore(prediction: DetectionBox, panBox: DetectionBox): Float {
        val mean = 0.7697886777933562f
        val std = 0.16833893197497318f
        val panHeight = panBox.rect.height()
        val heightRatio = prediction.rect.height() / panHeight
        return (-1 / 2f) * ((heightRatio - mean) / std).pow(2) * 4
    }

    private fun calculateNameWidthScore(nameBox: MergedBox): Float {
        val mean = 8.616257541544856f
        val std = 4.095034614992819f
        val nameWidthRatio = nameBox.box.rect.width() / nameBox.box.rect.height()
        var nameWidthScore = (-0.5f) * ((nameWidthRatio - mean) / std).pow(2)

        // penalize names shorter than 6 characters
        val shortNamePenalty = max(6 - nameWidthRatio, 0f)
        nameWidthScore -= 10 * shortNamePenalty

        // penalize deviations from 2 to 3 boxes
        val numBoxPenalty = floor(abs(nameBox.subBoxes.size - 2.5f))
        nameWidthScore -= 2 * numBoxPenalty

        // penalize names longer than 25 characters
        val longNamePenalty = max(nameWidthRatio - 25, 0f)
        nameWidthScore -= 5 * longNamePenalty

        return nameWidthScore
    }

    override suspend fun transformData(data: Input): Array<ByteBuffer> = arrayOf(data.textDetectImage.image)

    override suspend fun executeInference(
        tfInterpreter: Interpreter,
        data: Array<ByteBuffer>,
    ): Map<Int, Array<Array<Array<FloatArray>>>> {
        val mlOutput = mapOf(
            0 to arrayOf(
                Array(LAYER_1_SIZE.width) {
                    Array(LAYER_1_SIZE.height) {
                        FloatArray(DIM_Z)
                    }
                }
            ),
            1 to arrayOf(
                Array(LAYER_2_SIZE.width) {
                    Array(LAYER_2_SIZE.height) {
                        FloatArray(DIM_Z)
                    }
                }
            )
        )

        tfInterpreter.runForMultipleInputsOutputs(data, mlOutput)
        return mlOutput
    }

    /**
     * A factory for creating instances of this analyzer. This downloads the model from the web. If unable to download
     * from the web, this will throw a [FileNotFoundException].
     */
    class Factory(
        context: Context,
        fetchedModel: FetchedData,
        threads: Int = DEFAULT_THREADS
    ) : TFLAnalyzerFactory<Input, Prediction, TextDetect>(context, fetchedModel) {
        companion object {
            private const val USE_GPU = false
            private const val DEFAULT_THREADS = 1
        }

        override val tfOptions: Interpreter.Options = Interpreter
            .Options()
            .setUseNNAPI(USE_GPU && hasOpenGl31(context))
            .setNumThreads(threads)

        override suspend fun newInstance(): TextDetect? = createInterpreter()?.let { TextDetect(it) }
    }

    /**
     * A fetcher for downloading model data.
     */
    class ModelFetcher(context: Context) : UpdatingModelWebFetcher(context) {
        override val modelClass: String = "text_detection"
        override val modelFrameworkVersion: Int = 1
        override val defaultModelVersion: String = "20.16"
        override val defaultModelFileName: String = "dlnm.tflite"
        override val defaultModelHash: String = "c84564bf856358fbb2995c962ef5dd4a892dcaa593b61bf540324475db26afef"
        override val defaultModelHashAlgorithm: String = "SHA-256"
    }
}
