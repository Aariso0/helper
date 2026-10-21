package com.example.helper.theme

import androidx.compose.animation.core.CubicBezierEasing

/**
 * App-wide motion tokens (app-shell seam): durations and easings follow the
 * Material 3 motion spec so navigation and chrome move with one calm, purposeful
 * language — peer destinations fade through quickly, pushes move directionally,
 * the bottom bar slides instead of hard-cutting. Feature screens never invent
 * their own durations; they read these.
 *
 * Compose animations honor the system animator-duration scale automatically,
 * so users who enable "remove animations" in accessibility settings get
 * instant cuts with no extra code here.
 */
object Motion {
    /** M3 "short" — quick phase of a fade-through, fast exits. */
    object Duration {
        const val Short = 100
        const val Medium = 200
        const val Long = 300
    }

    object Easing {
        /** M3 standard (0.2, 0, 0, 1) — moving elements. */
        val Standard = CubicBezierEasing(0.2f, 0f, 0f, 1f)

        /** M3 decelerate (0, 0, 0, 1) — elements entering. */
        val Decelerate = CubicBezierEasing(0f, 0f, 0f, 1f)

        /** M3 accelerate (0.3, 0, 1, 1) — elements exiting. */
        val Accelerate = CubicBezierEasing(0.3f, 0f, 1f, 1f)
    }
}
