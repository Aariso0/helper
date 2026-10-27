package com.example.helper.motion

import android.content.res.Resources
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext

/**
 * M3-expressive spring tokens (reproduced; no MotionScheme dependency).
 * Spatial springs move things; effects springs fade/colour things (damping 1.0).
 */
object Motion {
    object Spatial {
        /** Pushes, list placement, icon pops */
        val Fast = spring<Float>(dampingRatio = 0.6f, stiffness = 800f)
        val FastInt = spring<Int>(dampingRatio = 0.6f, stiffness = 800f)
        val FastOffset = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.6f, stiffness = 800f)
        val FastSize = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = 0.6f, stiffness = 800f)

        /** Tab/content swaps, sheet content, cards */
        val Default = spring<Float>(dampingRatio = 0.8f, stiffness = 380f)
        val DefaultInt = spring<Int>(dampingRatio = 0.8f, stiffness = 380f)
        val DefaultOffset = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.8f, stiffness = 380f)
        val DefaultSize = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = 0.8f, stiffness = 380f)

        /** Shared-element bounds, bar hide/show */
        val Slow = spring<Float>(dampingRatio = 0.8f, stiffness = 200f)
        val SlowInt = spring<Int>(dampingRatio = 0.8f, stiffness = 200f)
        val SlowOffset = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.8f, stiffness = 200f)
        val SlowSize = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = 0.8f, stiffness = 200f)

        /** Small taps only: chips, counters, checkmarks */
        val Pop = spring<Float>(dampingRatio = 0.55f, stiffness = 900f)
        val PopInt = spring<Int>(dampingRatio = 0.55f, stiffness = 900f)
        val PopOffset = spring<androidx.compose.ui.unit.IntOffset>(dampingRatio = 0.55f, stiffness = 900f)
        val PopSize = spring<androidx.compose.ui.unit.IntSize>(dampingRatio = 0.55f, stiffness = 900f)
    }

    object Effects {
        /** Colour, alpha on tap feedback */
        val Fast = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 3800f)
        val FastInt = spring<Int>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 3800f)

        /** Scrims, crossfades, row backgrounds */
        val Default = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)
        val DefaultInt = spring<Int>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 1600f)

        /** Large area fades, empty-state fade */
        val Slow = spring<Float>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)
        val SlowInt = spring<Int>(dampingRatio = Spring.DampingRatioNoBouncy, stiffness = 800f)
    }
}

/**
 * Whether motion is enabled. Respects the system animator scale; when 0 the app
 * should cut instantly (see reduce-motion guidance).
 */
val LocalMotionEnabled = compositionLocalOf { true }

@Composable
fun ProvideMotion(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val enabled = remember(context) {
        val scale = try {
            val resolver = context.contentResolver
            android.provider.Settings.Global.getFloat(
                resolver,
                android.provider.Settings.Global.ANIMATOR_DURATION_SCALE,
                1f,
            )
        } catch (_: Throwable) {
            1f
        }
        scale != 0f
    }
    CompositionLocalProvider(LocalMotionEnabled provides enabled) {
        content()
    }
}

fun <T> ifMotion(enabled: Boolean, value: T, fallback: T): T = if (enabled) value else fallback
