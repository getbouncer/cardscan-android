package com.getbouncer.scan.payment.ml

import android.content.Context
import android.graphics.Bitmap
import android.util.Size
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.framework.UpdatingModelWebFetcher
import com.getbouncer.scan.framework.ml.TFLAnalyzerFactory
import com.getbouncer.scan.framework.ml.TensorFlowLiteAnalyzer
import com.getbouncer.scan.framework.util.indexOfMax
import com.getbouncer.scan.payment.hasOpenGl31
import com.getbouncer.scan.payment.scale
import com.getbouncer.scan.payment.toRGBByteBuffer
import org.tensorflow.lite.Interpreter
import java.io.FileNotFoundException
import java.nio.ByteBuffer

private val TRAINED_IMAGE_SIZE = Size(48, 48)

/**
 * model returns whether or not there is a screen present
 */
private const val NUM_CLASS = 27
class AlphabetDetect private constructor(interpreter: Interpreter) :
    TensorFlowLiteAnalyzer<AlphabetDetect.Input, ByteBuffer,
        AlphabetDetect.Prediction,
        Array<FloatArray>>(interpreter) {

    data class Input(val objDetectionImage: Bitmap)

    data class Prediction(val character: Char, val confidence: Float)

    override val name: String = "alphabet_recognize"

    override suspend fun buildEmptyMLOutput() = arrayOf(FloatArray(NUM_CLASS))

    override suspend fun interpretMLOutput(data: Input, mlOutput: Array<FloatArray>): Prediction {
        val prediction = mlOutput[0]
        val index = prediction.indexOfMax()
        val character = if (index != null && index > 0) {
            ('A'.toInt() - 1 + index).toChar()
        } else {
            ' '
        }
        val confidence = if (index != null) prediction[index] else 0F
        return Prediction(
            character, confidence
        )
    }

    override suspend fun transformData(data: Input): ByteBuffer = data.objDetectionImage
        .scale(TRAINED_IMAGE_SIZE)
        .toRGBByteBuffer()

    override suspend fun executeInference(
        tfInterpreter: Interpreter,
        data: ByteBuffer,
        mlOutput: Array<FloatArray>
    ) = tfInterpreter.run(data, mlOutput)

    /**
     * A factory for creating instances of this analyzer. This downloads the model from the web. If unable to download
     * from the web, this will throw a [FileNotFoundException].
     */
    class Factory(
        context: Context,
        fetchedModel: FetchedData,
        threads: Int = DEFAULT_THREADS
    ) : TFLAnalyzerFactory<AlphabetDetect>(context, fetchedModel) {
        companion object {
            private const val USE_GPU = false
            private const val DEFAULT_THREADS = 2
        }

        override val tfOptions: Interpreter.Options = Interpreter
            .Options()
            .setUseNNAPI(USE_GPU && hasOpenGl31(context))
            .setNumThreads(threads)

        override suspend fun newInstance(): AlphabetDetect? = createInterpreter()?.let { AlphabetDetect(it) }
    }

    /**
     * A fetcher for downloading model data.
     */
    class ModelFetcher(context: Context) : UpdatingModelWebFetcher(context) {
        override val defaultModelVersion: String = "4.147.0.94.16"
        override val defaultModelHash: String = "0693bf1962715e32f8d85ffefd8be9971d84ed554f25f4060aca2ca1f82c955b"
        override val defaultModelHashAlgorithm: String = "SHA-256"
        override val defaultModelFileName: String = "char_recognize.tflite"
        override val modelClass: String = "char_recognize"
        override val modelFrameworkVersion: Int = 1
    }
}
