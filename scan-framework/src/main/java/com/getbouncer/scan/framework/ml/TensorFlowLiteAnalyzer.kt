package com.getbouncer.scan.framework.ml

import android.content.Context
import android.util.Log
import com.getbouncer.scan.framework.*
import com.getbouncer.scan.framework.time.Timer
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import org.tensorflow.lite.Interpreter
import java.nio.ByteBuffer

/**
 * A TensorFlowLite analyzer uses an [Interpreter] to analyze data.
 */
abstract class TensorFlowLiteAnalyzer<Input, MLInput, Output, MLOutput>(
    private val tfInterpreter: Interpreter,
    private val debug: Boolean = false
) : Analyzer<Input, Unit, Output> {

    protected abstract suspend fun buildEmptyMLOutput(): MLOutput

    protected abstract suspend fun interpretMLOutput(data: Input, mlOutput: MLOutput): Output

    protected abstract suspend fun transformData(data: Input): MLInput

    protected abstract suspend fun executeInference(tfInterpreter: Interpreter, data: MLInput, mlOutput: MLOutput)

    private val loggingTimer by lazy {
        Timer.newInstance(Config.logTag, "$name ${this::class.java.simpleName}", enabled = debug)
    }

    override suspend fun analyze(data: Input, state: Unit): Output {
        val mlInput = loggingTimer.measureSuspend("transform") {
            transformData(data)
        }

        val mlOutput = loggingTimer.measureSuspend("prepare") {
            buildEmptyMLOutput()
        }

        loggingTimer.measureSuspend("infer") {
            executeInference(tfInterpreter, mlInput, mlOutput)
        }

        return loggingTimer.measureSuspend("interpret") {
            interpretMLOutput(data, mlOutput)
        }
    }

    fun close() = tfInterpreter.close()
}

/**
 * A factory that creates tensorflow models as analyzers.
 */
abstract class TFLAnalyzerFactory<Output : Analyzer<*, *, *>>(
    private val context: Context,
    private val fetchedModel: FetchedData
) : AnalyzerFactory<Output> {
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
        if (fetchedModel is FetchedFile) {
            try {
                fetchedModel.file?.delete()
            } catch (t: Throwable) {
                Log.w(Config.logTag, "Unable to delete file ${fetchedModel.file}")
            }
        }
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
