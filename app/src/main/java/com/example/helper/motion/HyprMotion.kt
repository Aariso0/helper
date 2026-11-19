package com.example.helper.motion

import android.provider.Settings
import androidx.compose.animation.BoundsTransform
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.Easing
import androidx.compose.animation.core.FiniteAnimationSpec
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.keyframes
import androidx.compose.animation.core.snap
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.lazy.LazyItemScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.platform.LocalContext
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** Hyprland curves, copied from its default config. */
object HyprEasing {
    val EaseOutQuint = CubicBezierEasing(0.23f, 1f, 0.32f, 1f)
    val EaseInOutCubic = CubicBezierEasing(0.65f, 0.05f, 0.36f, 1f)
    val AlmostLinear = CubicBezierEasing(0.5f, 0.5f, 0.75f, 1f)
    val Quick = CubicBezierEasing(0.15f, 0f, 0.1f, 1f)
    val Linear = LinearEasing
    val Overshoot = CubicBezierEasing(0.05f, 0.9f, 0.1f, 1.1f) // unused: no bounce by default
}

/**
 * True when the system asks apps to remove animations. Read through
 * [ProvideHyprMotion], which maps `ANIMATOR_DURATION_SCALE == 0` to `true`.
 */
val LocalReduceMotion = compositionLocalOf { false }

@Composable
fun ProvideHyprMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val reduce = remember(context) {
        runCatching {
            Settings.Global.getFloat(
                context.contentResolver,
                Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        }.getOrDefault(1f) == 0f
    }
    CompositionLocalProvider(LocalReduceMotion provides reduce) {
        content()
    }
}

/**
 * The app's single source of animation specs: Hyprland's leaves, curves, springs
 * and ready-made transitions. No other file may contain a `tween`, `spring` or
 * hardcoded animation duration.
 */
object HyprMotion {
    /** Global speed multiplier. 1f = exact Hyprland timings. */
    var SPEED_SCALE = 1f

    /** Hyprland "speed" is in deciseconds (1.0 = 100 ms). */
    fun ms(speed: Float): Int = (speed * 100f * SPEED_SCALE).roundToInt().coerceAtLeast(1)

    /**
     * Hyprland spring (mass, stiffness, dampening) -> Compose spring.
     * Compose springs assume mass 1, so ratio = c / (2 * sqrt(k * m)).
     * easy: k = 71.2633, c = 15.8273644 -> ratio ~= 0.937 (no visible bounce).
     */
    fun dampingRatio(stiffness: Float, dampening: Float, mass: Float = 1f): Float =
        dampening / (2f * sqrt(stiffness * mass))

    private const val EASY_STIFFNESS = 71.2633f
    private val EASY_RATIO = dampingRatio(EASY_STIFFNESS, 15.8273644f)
    private val RUBBER_RATIO = dampingRatio(70f, 10f) // accent only

    /** Settling time of a fixed-ratio spring scales with 1/sqrt(stiffness). */
    private fun scaledStiffness(stiffness: Float): Float =
        (stiffness * SPEED_SCALE * SPEED_SCALE).coerceAtLeast(1f)

    private fun <T> spec(speed: Float, easing: Easing, reduce: Boolean): FiniteAnimationSpec<T> =
        if (reduce) snap() else tween(ms(speed), easing = easing)

    // ---- Springs (movement / layout) ----
    fun <T> easySpring(visibilityThreshold: T? = null, reduce: Boolean = false): FiniteAnimationSpec<T> =
        if (reduce) {
            snap()
        } else {
            spring(
                dampingRatio = EASY_RATIO,
                stiffness = scaledStiffness(EASY_STIFFNESS),
                visibilityThreshold = visibilityThreshold,
            )
        }

    fun <T> rubberSpring(visibilityThreshold: T? = null, reduce: Boolean = false): FiniteAnimationSpec<T> =
        if (reduce) {
            snap()
        } else {
            spring(
                dampingRatio = RUBBER_RATIO,
                stiffness = scaledStiffness(70f),
                visibilityThreshold = visibilityThreshold,
            )
        }

    // ---- Tween specs, one per Hyprland leaf ----
    fun <T> windowsSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(4.79f, HyprEasing.EaseOutQuint, reduce)

    fun <T> windowsInSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(4.1f, HyprEasing.EaseOutQuint, reduce)

    fun <T> windowsOutSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(1.49f, HyprEasing.Linear, reduce)

    fun <T> fadeInSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(1.73f, HyprEasing.AlmostLinear, reduce)

    fun <T> fadeOutSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(1.46f, HyprEasing.AlmostLinear, reduce)

    fun <T> fadeSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(3.03f, HyprEasing.Quick, reduce)

    fun <T> layersInSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(4.0f, HyprEasing.EaseOutQuint, reduce)

    fun <T> layersOutSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(1.5f, HyprEasing.Linear, reduce)

    fun <T> workspacesInSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(1.21f, HyprEasing.AlmostLinear, reduce)

    fun <T> workspacesOutSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(1.94f, HyprEasing.AlmostLinear, reduce)

    fun <T> borderSpec(reduce: Boolean = false): FiniteAnimationSpec<T> =
        spec(5.39f, HyprEasing.EaseOutQuint, reduce)

    /** Error shake has no settle to model, so it stays keyframed — but the clock is here. */
    fun shakeSpec(reduce: Boolean = false): FiniteAnimationSpec<Float> =
        if (reduce) {
            snap()
        } else {
            keyframes {
                durationMillis = ms(2.8f)
                0f at 0
                (-4f) at (ms(0.4f))
                4f at (ms(0.8f))
                (-3f) at (ms(1.2f))
                3f at (ms(1.6f))
                (-2f) at (ms(2.0f))
                2f at (ms(2.4f))
                0f at (ms(2.8f))
            }
        }

    const val POPIN_SCALE = 0.87f
    const val SLIDEFADE_FRACTION = 0.20f

    // ---- Ready-made transitions. The caller passes the reduce-motion flag so
    // ---- non-composable hosts (NavHost transition lambdas) can use them too.

    /** windowsIn: popin 87% + fade. */
    fun popinIn(reduce: Boolean = false): EnterTransition {
        if (reduce) return EnterTransition.None
        return scaleIn(windowsInSpec(reduce), initialScale = POPIN_SCALE) +
            fadeIn(fadeInSpec(reduce))
    }

    /** windowsOut: popin 87% reversed, fast and linear. */
    fun popinOut(reduce: Boolean = false): ExitTransition {
        if (reduce) return ExitTransition.None
        return scaleOut(windowsOutSpec(reduce), targetScale = POPIN_SCALE) +
            fadeOut(fadeOutSpec(reduce))
    }

    /** layersIn / layersOut: pure fade (menus, popups, scrims). */
    fun layerIn(reduce: Boolean = false): EnterTransition =
        if (reduce) EnterTransition.None else fadeIn(layersInSpec(reduce))

    fun layerOut(reduce: Boolean = false): ExitTransition =
        if (reduce) ExitTransition.None else fadeOut(layersOutSpec(reduce))

    /** workspaces: fade between peers. */
    fun workspaceIn(reduce: Boolean = false): EnterTransition =
        if (reduce) EnterTransition.None else fadeIn(workspacesInSpec(reduce))

    fun workspaceOut(reduce: Boolean = false): ExitTransition =
        if (reduce) ExitTransition.None else fadeOut(workspacesOutSpec(reduce))

    /** workspaces slidefade 20%: forward = content moves left. */
    fun slideFadeIn(forward: Boolean, reduce: Boolean = false): EnterTransition {
        if (reduce) return EnterTransition.None
        return slideInHorizontally(windowsSpec(reduce)) { width ->
            (width * SLIDEFADE_FRACTION).toInt() * if (forward) 1 else -1
        } + fadeIn(workspacesInSpec(reduce))
    }

    fun slideFadeOut(forward: Boolean, reduce: Boolean = false): ExitTransition {
        if (reduce) return ExitTransition.None
        return slideOutHorizontally(windowsSpec(reduce)) { width ->
            (width * SLIDEFADE_FRACTION).toInt() * if (forward) -1 else 1
        } + fadeOut(workspacesOutSpec(reduce))
    }

    /** Shared-element bounds: soft critically-damped spring instead of M3's default. */
    @Composable
    fun rememberBoundsTransform(): BoundsTransform {
        val reduce = LocalReduceMotion.current
        return remember(reduce) {
            BoundsTransform { _, _ -> easySpring<Rect>(reduce = reduce) }
        }
    }
}

/** One line per keyed LazyColumn item: placement = windowsMove, fades = layers. */
@Composable
fun LazyItemScope.hyprAnimateItem(): Modifier {
    val reduce = LocalReduceMotion.current
    return Modifier.animateItem(
        placementSpec = HyprMotion.easySpring(reduce = reduce),
        fadeInSpec = HyprMotion.fadeInSpec<Float>(reduce),
        fadeOutSpec = HyprMotion.fadeOutSpec<Float>(reduce),
    )
}
