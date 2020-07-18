@file:JvmName("Coroutine")
package com.getbouncer.scan.framework.interop

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.runBlocking
import kotlin.coroutines.Continuation
import kotlin.coroutines.CoroutineContext

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
