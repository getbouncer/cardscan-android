package com.getbouncer.scan.framework.ml

import android.content.Context
import android.util.Log
import com.getbouncer.scan.framework.Analyzer
import com.getbouncer.scan.framework.AnalyzerFactory
import com.getbouncer.scan.framework.Config
import com.getbouncer.scan.framework.FetchedData
import com.getbouncer.scan.framework.Loader
import com.getbouncer.scan.framework.time.Timer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import org.tensorflow.lite.nnapi.NnApiDelegate
import java.io.Closeable
import java.nio.ByteBuffer

/**
 * A TensorFlowLite analyzer uses an [Interpreter] to analyze data.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
abstract class TensorFlowLiteAnalyzer<Input, MLInput, Output, MLOutput>(
    private val tfInterpreter: Interpreter,
    private val delegate: NnApiDelegate? = null,
) : Analyzer<Input, Any, Output>, Closeable {

    protected abstract suspend fun interpretMLOutput(data: Input, mlOutput: MLOutput): Output

    protected abstract suspend fun transformData(data: Input): MLInput

    protected abstract suspend fun executeInference(tfInterpreter: Interpreter, data: MLInput): MLOutput

    private val loggingTimer by lazy {
        Timer.newInstance(Config.logTag, this::class.java.simpleName)
    }

    override suspend fun analyze(data: Input, state: Any): Output {
        val mlInput = loggingTimer.measureSuspend("transform") {
            transformData(data)
        }

        val mlOutput = loggingTimer.measureSuspend("infer") {
            executeInference(tfInterpreter, mlInput)
        }

        return loggingTimer.measureSuspend("interpret") {
            interpretMLOutput(data, mlOutput)
        }
    }

    override fun close() {
        tfInterpreter.close()
        delegate?.close()
    }
}

/**
 * A factory that creates tensorflow models as analyzers.
 */
@Deprecated(
    message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan",
    replaceWith = ReplaceWith("StripeCardScan"),
)
abstract class TFLAnalyzerFactory<Input, Output, AnalyzerType : Analyzer<Input, Any, Output>>(
    private val context: Context,
    private val fetchedModel: FetchedData,
) : AnalyzerFactory<Input, Any, Output, AnalyzerType> {
    protected abstract val tfOptions: Interpreter.Options

    private val loader by lazy { Loader(context) }

    private val loadModelMutex = Mutex()

    private var loadedModel: ByteBuffer? = null

    protected suspend fun createInterpreter(): Interpreter? =
        createInterpreter(fetchedModel)

    private suspend fun createInterpreter(fetchedModel: FetchedData): Interpreter? = try {
        loadModel(fetchedModel)?.let { Interpreter(it, tfOptions) }
    } catch (t: Throwable) {
        Log.e(Config.logTag, "Error occurred while loading model ${fetchedModel.modelClass} version ${fetchedModel.modelVersion}", t)
        null
    }.apply {
        if (this == null) {
            Log.w(Config.logTag, "Unable to load model ${fetchedModel.modelClass} version ${fetchedModel.modelVersion}")
        }
    }

    private suspend fun loadModel(fetchedModel: FetchedData): ByteBuffer? = loadModelMutex.withLock {
        loadedModel ?: run { loader.loadData(fetchedModel) }
    }
}
