package com.example.helper.motion

import android.os.SystemClock
import android.util.Log
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavController

/**
 * Debug-only tracing for the "back animation changes after a while" bug hunt.
 *
 * Every call site is wrapped in `if (BuildConfig.DEBUG)`, so R8 removes all of
 * this from release builds. One tag, one line per event, so the decisive tests
 * can be read straight out of logcat:
 *
 *   adb logcat -v time MotionDiag:D ActivityTaskManager:I WindowManager:I *:S
 *
 * The four NavHost lambdas identify the path:
 *  - ENTER / EXIT / POP_ENTER / POP_EXIT  -> app lambda ran (non-predictive).
 *  - PREDICTIVE_POP_ENTER / _EXIT        -> Navigation-Compose predictive path.
 *  - no lambda at all                    -> system handled back without the app.
 */
object MotionDiag {
    private const val TAG = "MotionDiag"

    /** Plain holder, not Compose State: reading it in a transition lambda must not recompose. */
    class Clock {
        var lastDestinationChangeAt: Long = 0L
        var lastRoute: String? = null
    }

    private fun route(entry: NavBackStackEntry?): String =
        entry?.destination?.route ?: "<none>"

    private fun since(clock: Clock): String {
        val at = clock.lastDestinationChangeAt
        return if (at == 0L) "n/a" else "${SystemClock.elapsedRealtime() - at}ms"
    }

    private fun stack(controller: NavController): String =
        controller.currentBackStack.value.joinToString(">") { route(it) }

    fun transition(
        kind: String,
        clock: Clock,
        from: NavBackStackEntry?,
        to: NavBackStackEntry?,
        extra: String = "",
    ) {
        Log.d(
            TAG,
            "$kind from=${route(from)} to=${route(to)} " +
                "t=${SystemClock.elapsedRealtime()} sinceScreenEntered=${since(clock)} $extra",
        )
    }

    fun destinationChanged(controller: NavController, clock: Clock, destinationRoute: String) {
        val now = SystemClock.elapsedRealtime()
        val previous = clock.lastRoute
        Log.d(
            TAG,
            "DEST_CHANGED from=$previous to=$destinationRoute t=$now " +
                "stackSize=${controller.currentBackStack.value.size} stack=[${stack(controller)}] " +
                "stayed=${if (clock.lastDestinationChangeAt == 0L) "n/a" else "${now - clock.lastDestinationChangeAt}ms"}",
        )
        clock.lastDestinationChangeAt = now
        clock.lastRoute = destinationRoute
    }

    /** For screen-level AnimatedContent / AnimatedVisibility transition lambdas. */
    fun local(kind: String, label: String) {
        Log.d(TAG, "$kind scope=$label t=${SystemClock.elapsedRealtime()}")
    }
}
