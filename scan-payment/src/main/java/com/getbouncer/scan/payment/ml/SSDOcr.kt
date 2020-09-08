package com.getbouncer.scan.payment.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.util.Size
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.framework.UpdatingResourceFetcher
import com.getbouncer.scan.framework.ml.TFLAnalyzerFactory
import com.getbouncer.scan.framework.ml.TensorFlowLiteAnalyzer
import com.getbouncer.scan.framework.ml.ssd.adjustLocations
import com.getbouncer.scan.framework.ml.ssd.softMax
import com.getbouncer.scan.framework.ml.ssd.toRectForm
import com.getbouncer.scan.framework.time.ClockMark
import com.getbouncer.scan.framework.util.reshape
import com.getbouncer.scan.framework.util.scaleAndCenterWithin
import com.getbouncer.scan.payment.R
import com.getbouncer.scan.payment.crop
import com.getbouncer.scan.payment.hasOpenGl31
import com.getbouncer.scan.payment.ml.ssd.DetectionBox
import com.getbouncer.scan.payment.ml.ssd.OcrFeatureMapSizes
import com.getbouncer.scan.payment.ml.ssd.combinePriors
import com.getbouncer.scan.payment.ml.ssd.rearrangeOCRArray
import com.getbouncer.scan.payment.ml.ssd.extractPredictions
import com.getbouncer.scan.payment.ml.ssd.determineLayoutAndFilter
import com.getbouncer.scan.payment.scale
import com.getbouncer.scan.payment.size
import com.getbouncer.scan.payment.toRGBByteBuffer
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** Training images are normalized with mean 127.5 and std 128.5. */
private const val IMAGE_MEAN = 127.5f
private const val IMAGE_STD = 128.5f

/**
 * We use the output from last two layers with feature maps 19x19 and 10x10
 * and for each feature map activation we have 6 priors, so total priors are
 * 19x19x6 + 10x10x6 = 2766
 */
private const val NUM_OF_PRIORS = 3420

/**
 * For each activation in our feature map, we have predictions for 6 bounding boxes
 * of different aspect ratios
 */
private const val NUM_OF_PRIORS_PER_ACTIVATION = 3

/**
 * We can detect a total of 10 numbers (0 - 9) plus the background class
 */
private const val NUM_OF_CLASSES = 11

/**
 * Each prior or bounding box can be represented by 4 coordinates
 * XMin, YMin, XMax, YMax.
 */
private const val NUM_OF_COORDINATES = 4

/**
 * Represents the total number of data points for locations
 */
private const val NUM_LOC = NUM_OF_COORDINATES * NUM_OF_PRIORS

/**
 * Represents the total number of data points for classes
 */
private const val NUM_CLASS = NUM_OF_CLASSES * NUM_OF_PRIORS

private const val PROB_THRESHOLD = 0.50f
private const val IOU_THRESHOLD = 0.50f
private const val CENTER_VARIANCE = 0.1f
private const val SIZE_VARIANCE = 0.2f
private const val VERTICAL_THRESHOLD = 2.0f
private const val LIMIT = 20

private val FEATURE_MAP_SIZES =
    OcrFeatureMapSizes(
        layerOneWidth = 38,
        layerOneHeight = 24,
        layerTwoWidth = 19,
        layerTwoHeight = 12
    )

/**
 * This value should never change, and is thread safe.
 */
private val PRIORS = combinePriors()

/**
 * This model performs SSD OCR recognition on a card.
 */
class SSDOcr private constructor(interpreter: Interpreter) :
    TensorFlowLiteAnalyzer<SSDOcr.Input, Array<ByteBuffer>, SSDOcr.Prediction, Map<Int, Array<FloatArray>>>(interpreter) {

    data class Input(val fullImage: Bitmap, val previewSize: Size, val cardFinder: Rect, val capturedAt: ClockMark)

    data class Prediction(val pan: String, val detectedBoxes: List<DetectionBox>)

    companion object {
        /**
         * Calculate the crop from the [fullImage] for the credit card based on the [cardFinder] within the [previewImage].
         *
         * Note: This algorithm makes some assumptions:
         * 1. the previewImage and the fullImage are centered relative to each other.
         * 2. the fullImage circumscribes the previewImage. I.E. they share at least one field of view, and the previewImage's
         *    fields of view are smaller than or the same size as the fullImage's
         * 3. the fullImage and the previewImage have the same orientation
         */
        fun cropImage(input: Input): Bitmap {
            require(
                input.cardFinder.left >= 0 &&
                    input.cardFinder.right <= input.previewSize.width &&
                    input.cardFinder.top >= 0 &&
                    input.cardFinder.bottom <= input.previewSize.height
            ) { "Card finder is outside preview image bounds" }

            // Scale the previewImage to match the fullImage
            val scaledPreviewImage = input.previewSize.scaleAndCenterWithin(input.fullImage.size())
            val previewScale = scaledPreviewImage.width().toFloat() / input.previewSize.width

            // Scale the cardFinder to match the scaledPreviewImage
            val scaledCardFinder = Rect(
                (input.cardFinder.left * previewScale).roundToInt(),
                (input.cardFinder.top * previewScale).roundToInt(),
                (input.cardFinder.right * previewScale).roundToInt(),
                (input.cardFinder.bottom * previewScale).roundToInt()
            )

            // Position the scaledCardFinder on the fullImage
            val cropRect = Rect(
                max(0, scaledCardFinder.left + scaledPreviewImage.left),
                max(0, scaledCardFinder.top + scaledPreviewImage.top),
                min(input.fullImage.width, scaledCardFinder.right + scaledPreviewImage.left),
                min(input.fullImage.height, scaledCardFinder.bottom + scaledPreviewImage.top)
            )

            return input.fullImage.crop(cropRect)
        }
    }

    /**
     * The model reshapes all the data to 1 x [All Data Points]
     */
    override suspend fun buildEmptyMLOutput(): Map<Int, Array<FloatArray>> = mapOf(
        0 to arrayOf(FloatArray(NUM_CLASS)),
        1 to arrayOf(FloatArray(NUM_LOC))
    )

    override suspend fun transformData(data: Input): Array<ByteBuffer> = arrayOf(
        cropImage(data)
            .scale(Factory.TRAINED_IMAGE_SIZE)
            .toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD)
    )

    override suspend fun interpretMLOutput(
        data: Input,
        mlOutput: Map<Int, Array<FloatArray>>
    ): Prediction {
        val outputClasses = mlOutput[0] ?: arrayOf(FloatArray(NUM_CLASS))
        val outputLocations = mlOutput[1] ?: arrayOf(FloatArray(NUM_LOC))

        val boxes = rearrangeOCRArray(
            locations = outputLocations,
            featureMapSizes = FEATURE_MAP_SIZES,
            numberOfPriors = NUM_OF_PRIORS_PER_ACTIVATION,
            locationsPerPrior = NUM_OF_COORDINATES
        ).reshape(NUM_OF_COORDINATES)
        boxes.adjustLocations(
            priors = PRIORS,
            centerVariance = CENTER_VARIANCE,
            sizeVariance = SIZE_VARIANCE
        )
        boxes.forEach { it.toRectForm() }

        val scores = rearrangeOCRArray(
            locations = outputClasses,
            featureMapSizes = FEATURE_MAP_SIZES,
            numberOfPriors = NUM_OF_PRIORS_PER_ACTIVATION,
            locationsPerPrior = NUM_OF_CLASSES
        ).reshape(NUM_OF_CLASSES)
        scores.forEach { it.softMax() }

        val detectedBoxes = determineLayoutAndFilter(
            extractPredictions(
                scores = scores,
                boxes = boxes,
                probabilityThreshold = PROB_THRESHOLD,
                intersectionOverUnionThreshold = IOU_THRESHOLD,
                limit = LIMIT,
                classifierToLabel = { if (it == 10) 0 else it }
            ).sortedBy { it.rect.left },
            VERTICAL_THRESHOLD
        )

        val predictedNumber = detectedBoxes.map { it.label }.joinToString("")
        return Prediction(predictedNumber, detectedBoxes)
    }

    override suspend fun executeInference(
        tfInterpreter: Interpreter,
        data: Array<ByteBuffer>,
        mlOutput: Map<Int, Array<FloatArray>>
    ) = tfInterpreter.runForMultipleInputsOutputs(data, mlOutput)

    /**
     * A factory for creating instances of this analyzer.
     */
    class Factory(
        context: Context,
        fetchedModel: FetchedData,
        threads: Int = DEFAULT_THREADS
    ) : TFLAnalyzerFactory<SSDOcr>(context, fetchedModel) {
        companion object {
            private const val USE_GPU = false
            private const val DEFAULT_THREADS = 2

            val TRAINED_IMAGE_SIZE = Size(600, 375)
        }

        override val tfOptions: Interpreter.Options = Interpreter
            .Options()
            .setUseNNAPI(USE_GPU && hasOpenGl31(context.applicationContext))
            .setNumThreads(threads)

        override suspend fun newInstance(): SSDOcr? = createInterpreter()?.let { SSDOcr(it) }
    }

    /**
     * A fetcher for downloading model data.
     */
    class ModelFetcher(context: Context) : UpdatingResourceFetcher(context) {
        override val resource: Int = R.raw.darknite_1_1_1_16
        override val resourceModelVersion: String = "darknite_1_1_1_16"
        override val resourceModelHash: String = "8d8e3f79aa0783ab0cfa5c8d65d663a9da6ba99401efb2298aaaee387c3b00d6"
        override val resourceModelHashAlgorithm: String = "SHA-256"
        override val modelClass: String = "ocr"
        override val modelFrameworkVersion: Int = 1
    }
}
