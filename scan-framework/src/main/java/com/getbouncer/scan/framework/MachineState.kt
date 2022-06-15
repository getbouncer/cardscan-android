package com.getbouncer.scan.framework

import android.util.Log
import com.getbouncer.scan.framework.time.Clock
import com.getbouncer.scan.framework.time.ClockMark

@Deprecated(message = "Replaced by stripe card scan. See https://github.com/stripe/stripe-android/tree/master/stripecardscan")
abstract class MachineState {

    /**
     * Keep track of when this state was reached
     */
    protected open val reachedStateAt: ClockMark = Clock.markNow()

    override fun toString(): String = "${this::class.java.simpleName}(reachedStateAt=$reachedStateAt)"

    init {
        if (Config.isDebug) Log.d(Config.logTag, "${this::class.java.simpleName} machine state reached")
    }
}
