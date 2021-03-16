package com.getbouncer.scan.payment.ml

import android.content.Context
import android.graphics.Rect
import android.util.Size
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.framework.ResourceFetcher
import com.getbouncer.scan.framework.TrackedImage
import com.getbouncer.scan.framework.UpdatingResourceFetcher
import com.getbouncer.scan.framework.ml.TFLAnalyzerFactory
import com.getbouncer.scan.framework.ml.TensorFlowLiteAnalyzer
import com.getbouncer.scan.framework.ml.ssd.adjustLocations
import com.getbouncer.scan.framework.ml.ssd.softMax
import com.getbouncer.scan.framework.ml.ssd.toRectForm
import com.getbouncer.scan.framework.util.intersectionWith
import com.getbouncer.scan.framework.util.projectRegionOfInterest
import com.getbouncer.scan.framework.util.reshape
import com.getbouncer.scan.framework.util.toRect
import com.getbouncer.scan.payment.R
import com.getbouncer.scan.payment.crop
import com.getbouncer.scan.payment.hasOpenGl31
import com.getbouncer.scan.payment.ml.ssd.DetectionBox
import com.getbouncer.scan.payment.ml.ssd.OcrFeatureMapSizes
import com.getbouncer.scan.payment.ml.ssd.combinePriors
import com.getbouncer.scan.payment.ml.ssd.determineLayoutAndFilter
import com.getbouncer.scan.payment.ml.ssd.extractPredictions
import com.getbouncer.scan.payment.ml.ssd.rearrangeOCRArray
import com.getbouncer.scan.payment.scale
import com.getbouncer.scan.payment.size
import com.getbouncer.scan.payment.toRGBByteBuffer
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

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
    TensorFlowLiteAnalyzer<SSDOcr.Input, Array<ByteBuffer>, SSDOcr.Prediction, Map<Int, Array<FloatArray>>>(
        interpreter
    ) {

    data class Input(val fullImage: TrackedImage, val previewSize: Size, val cardFinder: Rect)

    data class Prediction(val pan: String, val detectedBoxes: List<DetectionBox>)

    companion object {
        /**
         * Calculate the crop from the full image for the credit card based on the view finder
         * within the preview size.
         *
         * Note: This algorithm makes some assumptions:
         * 1. the previewImage and the fullImage are centered relative to each other.
         * 2. the fullImage circumscribes the previewImage. I.E. they share at least one field of
         *    view, and the previewImage's fields of view are smaller than or the same size as the
         *    fullImage's
         * 3. the fullImage and the previewImage have the same orientation
         */
        fun cropImage(input: Input): TrackedImage {
            require(
                input.cardFinder.left >= 0 &&
                    input.cardFinder.right <= input.previewSize.width &&
                    input.cardFinder.top >= 0 &&
                    input.cardFinder.bottom <= input.previewSize.height
            ) { "Card finder is outside preview image bounds ${input.cardFinder} ${input.previewSize}" }

            // Scale the cardFinder to match the full image
            val projectedViewFinder = input
                .previewSize
                .projectRegionOfInterest(
                    toSize = input.fullImage.image.size(),
                    regionOfInterest = input.cardFinder
                )
                .intersectionWith(input.fullImage.image.size().toRect())

            return TrackedImage(
                image = input.fullImage.image.crop(projectedViewFinder),
                tracker = input.fullImage.tracker,
            )
        }
    }

    override suspend fun transformData(data: Input): Array<ByteBuffer> = arrayOf(
        cropImage(data)
            .image
            .scale(Factory.TRAINED_IMAGE_SIZE)
            .toRGBByteBuffer(mean = IMAGE_MEAN, std = IMAGE_STD).also {
                data.fullImage.tracker.trackResult("ocr_image_transform")
            }
    )

    override suspend fun interpretMLOutput(
        data: Input,
        mlOutput: Map<Int, Array<FloatArray>>,
    ): Prediction {
        val outputClasses = mlOutput[0] ?: arrayOf(FloatArray(NUM_CLASS))
        val outputLocations = mlOutput[1] ?: arrayOf(FloatArray(NUM_LOC))

        val boxes = rearrangeOCRArray(
            locations = outputLocations,
            featureMapSizes = FEATURE_MAP_SIZES,
            numberOfPriors = NUM_OF_PRIORS_PER_ACTIVATION,
            locationsPerPrior = NUM_OF_COORDINATES,
        ).reshape(NUM_OF_COORDINATES)
        boxes.adjustLocations(
            priors = PRIORS,
            centerVariance = CENTER_VARIANCE,
            sizeVariance = SIZE_VARIANCE,
        )
        boxes.forEach { it.toRectForm() }

        val scores = rearrangeOCRArray(
            locations = outputClasses,
            featureMapSizes = FEATURE_MAP_SIZES,
            numberOfPriors = NUM_OF_PRIORS_PER_ACTIVATION,
            locationsPerPrior = NUM_OF_CLASSES,
        ).reshape(NUM_OF_CLASSES)
        scores.forEach { it.softMax() }

        val detectedBoxes = determineLayoutAndFilter(
            extractPredictions(
                scores = scores,
                boxes = boxes,
                probabilityThreshold = PROB_THRESHOLD,
                intersectionOverUnionThreshold = IOU_THRESHOLD,
                limit = LIMIT,
                classifierToLabel = { if (it == 10) 0 else it },
            ),
            VERTICAL_THRESHOLD,
        )

        val predictedNumber = detectedBoxes.map { it.label }.joinToString("")

        data.fullImage.tracker.trackResult("ocr_prediction_complete")
        return Prediction(predictedNumber, detectedBoxes)
    }

    override suspend fun executeInference(
        tfInterpreter: Interpreter,
        data: Array<ByteBuffer>,
    ): Map<Int, Array<FloatArray>> {
        val mlOutput = mapOf(
            0 to arrayOf(FloatArray(NUM_CLASS)),
            1 to arrayOf(FloatArray(NUM_LOC)),
        )

        tfInterpreter.runForMultipleInputsOutputs(data, mlOutput)
        return mlOutput
    }

    /**
     * A factory for creating instances of this analyzer.
     */
    class Factory(
        context: Context,
        fetchedModel: FetchedData,
        threads: Int = DEFAULT_THREADS,
    ) : TFLAnalyzerFactory<Input, Prediction, SSDOcr>(context, fetchedModel) {
        companion object {
            private const val USE_GPU = false
            private const val DEFAULT_THREADS = 3

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
    class ModelFetcher(context: Context) : ResourceFetcher() {
        override val assetFileName: String = "mb2_brex_metal_synthetic_svhnextra_epoch_3_5_98_8.tflite"
        override val modelVersion: String = "3.5.98.8"
        override val hash: String = "a4739fa49caa3ff88e7ff1145c9334ee4cbf64354e91131d02d98d7bfd4c35cf"
        override val hashAlgorithm: String = "SHA-256"
        override val modelClass: String = "ocr"
        override val modelFrameworkVersion: Int = 1
    }
}
