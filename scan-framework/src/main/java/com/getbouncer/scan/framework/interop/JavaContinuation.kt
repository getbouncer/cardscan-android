@file:JvmName("Coroutine")
package com.getbouncer.scan.framework.interop

import android.util.Log
import com.getbouncer.scan.framework.Config
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext
import kotlin.coroutines.resume
import kotlin.coroutines.resumeWithException

/**
 * A utility class for calling suspend functions from java. This allows listening to a suspend function with callbacks.
 */
abstract class JavaContinuation<in T> @JvmOverloads constructor(
    runOn: CoroutineContext = Dispatchers.Default,
    private val listenOn: CoroutineContext = Dispatchers.Main
) : Continuation<T> {
    override val context: CoroutineContext = runOn
    abstract fun onComplete(value: T)
    abstract fun onException(exception: Throwable)
    override fun resumeWith(result: Result<T>) = result.fold(
        onSuccess = {
            runBlocking(listenOn) {
                onComplete(it)
            }
        },
        onFailure = {
            runBlocking(listenOn) {
                onException(it)
            }
        }
    )
}

/**
 * An empty continuation for ignoring results.
 */
class EmptyJavaContinuation<in T> : JavaContinuation<T>() {
    override fun onComplete(value: T) { }
    override fun onException(exception: Throwable) {
        Log.e(Config.logTag, "Error in continuation", exception)
    }
}

/**
 * Resume a continuation with a value.
 */
fun <T> Continuation<T>.resumeJava(value: T) = this.resume(value)

/**
 * Resume a continuation with an exception.
 */
fun <T> Continuation<T>.resumeWithExceptionJava(exception: Throwable) = this.resumeWithException(exception)
