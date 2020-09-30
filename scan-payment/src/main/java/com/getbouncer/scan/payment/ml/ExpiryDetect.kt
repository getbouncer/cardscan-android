package com.getbouncer.scan.payment.ml

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Rect
import android.graphics.RectF
import android.util.Size
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.framework.UpdatingModelWebFetcher
import com.getbouncer.scan.framework.ml.TFLAnalyzerFactory
import com.getbouncer.scan.framework.ml.TensorFlowLiteAnalyzer
import com.getbouncer.scan.framework.ml.greedyNonMaxSuppression
import com.getbouncer.scan.framework.util.indexOfMax
import com.getbouncer.scan.framework.util.scaled
import com.getbouncer.scan.payment.card.formatExpiry
import com.getbouncer.scan.payment.card.isValidExpiry
import com.getbouncer.scan.payment.card.isValidMonth
import com.getbouncer.scan.payment.crop
import com.getbouncer.scan.payment.hasOpenGl31
import com.getbouncer.scan.payment.scale
import com.getbouncer.scan.payment.size
import com.getbouncer.scan.payment.toRGBByteBuffer
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.nio.ByteBuffer
import kotlin.math.roundToInt

private val TRAINED_IMAGE_SIZE = Size(80, 36)

private const val NUM_CLASS = 11
private const val NUM_PREDICTIONS = 17
private const val BACKGROUND_CLASS = 10

private val ASPECT_RATIO = TRAINED_IMAGE_SIZE.height.toFloat() / TRAINED_IMAGE_SIZE.width.toFloat()

class ExpiryDetect private constructor(interpreter: Interpreter) :
    TensorFlowLiteAnalyzer<ExpiryDetect.Input, ByteBuffer,
        ExpiryDetect.Prediction,
        Array<Array<Array<FloatArray>>>>(interpreter) {

    data class Input(val image: Bitmap, val expiryBox: RectF)

    data class Prediction(val expiry: Expiry?)

    data class Expiry(val month: String, val year: String) : Comparable<Expiry> {
        override fun toString() = formatExpiry(
            day = null,
            month = month,
            year = year
        )

        override fun compareTo(other: Expiry): Int =
            (year.toIntOrNull() ?: 0) * 100 + (month.toIntOrNull() ?: 0)
                .compareTo((other.year.toIntOrNull() ?: 0) * 100 + (other.month.toIntOrNull() ?: 0))

        fun isValidExpiry() = isValidExpiry(null, month, year)
    }

    private data class Digit(val digit: Int, val confidence: Float)

    override suspend fun buildEmptyMLOutput() = arrayOf(arrayOf(Array(NUM_PREDICTIONS) { FloatArray(NUM_CLASS) }))

    override suspend fun interpretMLOutput(data: Input, mlOutput: Array<Array<Array<FloatArray>>>): Prediction {
        val output = mlOutput[0][0].mapNotNull {
            it.indexOfMax()?.let { maxIndex ->
                Digit(maxIndex, it[maxIndex])
            }
        }

        val (newDigits, confidence) = output.map {
            Pair(it.digit, it.confidence)
        }.unzip()

        val digits = greedyNonMaxSuppression(
            newDigits.toTypedArray(),
            confidence.toFloatArray(),
            BACKGROUND_CLASS
        ).filter { it != BACKGROUND_CLASS }

        return if (digits.size == 4 || (digits.size == 5 && digits[2] == 1)) {
            // process if we get exactly 4 digits, OR it's five digits and the middle prediction
            // is a 1 - this is because we sometimes mistake '/' for '1'
            val month = "${digits[0]}${digits[1]}"
            val year = "20${digits[digits.size - 2]}${digits[digits.size - 1]}"
            if (isValidMonth(month)) {
                Prediction(Expiry(month, year))
            } else {
                Prediction(null)
            }
        } else {
            Prediction(null)
        }
    }

    override suspend fun transformData(data: Input): ByteBuffer {
        val targetAspectRatio = ASPECT_RATIO
        val scaledRect = data.expiryBox.scaled(data.image.size())
        val scaledExpRectNewHeight = scaledRect.width() * targetAspectRatio

        val rect = Rect(
            scaledRect.left.roundToInt(),
            (scaledRect.centerY() - scaledExpRectNewHeight / 2).roundToInt(),
            scaledRect.right.roundToInt(),
            (scaledRect.centerY() + scaledExpRectNewHeight / 2).roundToInt()
        )

        return data.image
            .crop(rect)
            .scale(TRAINED_IMAGE_SIZE)
            .toRGBByteBuffer()
    }

    override suspend fun executeInference(
        tfInterpreter: Interpreter,
        data: ByteBuffer,
        mlOutput: Array<Array<Array<FloatArray>>>
    ) = tfInterpreter.run(data, mlOutput)

    /**
     * A factory for creating instances of this analyzer. This downloads the model from the web. If unable to download
     * from the web, this will throw a [FileNotFoundException].
     */
    class Factory(
        context: Context,
        fetchedModel: FetchedData,
        threads: Int = DEFAULT_THREADS
    ) : TFLAnalyzerFactory<ExpiryDetect>(context, fetchedModel) {
        companion object {
            private const val USE_GPU = false
            private const val DEFAULT_THREADS = 1
        }

        override val tfOptions: Interpreter.Options = Interpreter
            .Options()
            .setUseNNAPI(USE_GPU && hasOpenGl31(context))
            .setNumThreads(threads)

        override suspend fun newInstance(): ExpiryDetect? = createInterpreter()?.let { ExpiryDetect(it) }
    }

    /**
     * A fetcher for downloading model data.
     */
    class ModelFetcher(context: Context) : UpdatingModelWebFetcher(context) {
        override val defaultModelVersion: String = "0.0.1.16"
        override val defaultModelHash: String = "55eea0d57239a7e92904fb15209963f7236bd06919275bdeb0a765a94b559c97"
        override val defaultModelHashAlgorithm: String = "SHA-256"
        override val defaultModelFileName: String = "fourrecognize.tflite"
        override val modelClass: String = "four_recognize"
        override val modelFrameworkVersion: Int = 1
    }
}
