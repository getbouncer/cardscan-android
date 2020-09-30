package com.getbouncer.scan.framework.time

import androidx.annotation.CheckResult

object Clock {
    fun markNow(): ClockMark = PreciseClockMark(System.nanoTime())
}

/**
 * Convert a milliseconds since epoch timestamp to a clock mark.
 */
fun Long.asEpochMillisecondsClockMark(): ClockMark = AbsoluteClockMark(this)

/**
 * A marked point in time.
 */
interface ClockMark {
    fun elapsedSince(): Duration

    fun toMillisecondsSinceEpoch(): Long
}

/**
 * A clock mark based on milliseconds since epoch. This is precise to the nearest millisecond.
 */
private class AbsoluteClockMark(private val millisecondsSinceEpoch: Long) : ClockMark {
    override fun elapsedSince(): Duration = (System.currentTimeMillis() - millisecondsSinceEpoch).milliseconds

    override fun toMillisecondsSinceEpoch(): Long = millisecondsSinceEpoch

    override fun equals(other: Any?): Boolean =
        this === other || when (other) {
            is AbsoluteClockMark -> millisecondsSinceEpoch == other.millisecondsSinceEpoch
            is ClockMark -> toMillisecondsSinceEpoch() == other.toMillisecondsSinceEpoch()
            else -> false
        }

    override fun hashCode(): Int {
        return millisecondsSinceEpoch.hashCode()
    }

    override fun toString(): String {
        return "AbsoluteClockMark(${elapsedSince()} ago)"
    }
}

/**
 * A precise clock mark that is not bound to epoch seconds. This is precise to the nearest nanosecond.
 */
private class PreciseClockMark(private val originMark: Long) : ClockMark {
    override fun elapsedSince(): Duration = (System.nanoTime() - originMark).nanoseconds

    override fun toMillisecondsSinceEpoch(): Long = System.currentTimeMillis() - elapsedSince().inMilliseconds.toLong()

    override fun equals(other: Any?): Boolean =
        this === other || when (other) {
            is PreciseClockMark -> originMark == other.originMark
            is ClockMark -> toMillisecondsSinceEpoch() == other.toMillisecondsSinceEpoch()
            else -> false
        }

    override fun hashCode(): Int {
        return originMark.hashCode()
    }

    override fun toString(): String {
        return "PreciseClockMark(${elapsedSince()} ago)"
    }
}

/**
 * Measure the amount of time a process takes.
 *
 * TODO: use contracts when they are no longer experimental
 */
@CheckResult
inline fun <T> measureTime(block: () -> T): Pair<Duration, T> {
    // contract { callsInPlace(block, EXACTLY_ONCE) }
    val mark = Clock.markNow()
    val result = block()
    return mark.elapsedSince() to result
}
