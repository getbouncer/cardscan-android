package com.getbouncer.scan.framework.util

import com.getbouncer.scan.framework.time.Duration
import kotlinx.coroutines.delay

private const val DEFAULT_RETRIES = 3

suspend fun <T> retry(
    retryDelay: Duration,
    times: Int = DEFAULT_RETRIES,
    excluding: List<Class<out Throwable>> = emptyList(),
    task: suspend () -> T
): T {
    var exception: Throwable? = null
    for (attempt in 1..times) {
        try {
            return task()
        } catch (t: Throwable) {
            exception = t
            if (t.javaClass in excluding) {
                throw t
            }
            if (attempt < times) {
                delay(retryDelay.inMilliseconds.toLong())
            }
        }
    }

    if (exception != null) {
        throw exception
    } else {
        // This code should never be reached
        throw UnexpectedRetryException()
    }
}

/**
 * This exception should never be thrown, and therefore can be private.
 */
private class UnexpectedRetryException : Exception()
