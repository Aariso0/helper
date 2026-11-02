package com.example.helper.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.interaction.InteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Press feedback: scale down while pressed, spring back on release.
 *
 * Takes the [InteractionSource] of the caller's own clickable — it never adds
 * one. A modifier that installs its own `clickable` silently swallows the tap
 * (and doubles the press state), which is why this one only reads.
 *
 * Scale is written in a `graphicsLayer` lambda, so it costs draw only and
 * never re-lays-out a list row. Keep it off tall scrolling rows.
 */
fun Modifier.pressScale(
    interactionSource: InteractionSource,
    enabled: Boolean = true,
    downScale: Float = 0.97f,
): Modifier = composed {
    if (!enabled) return@composed this
    val pressed by interactionSource.collectIsPressedAsState()
    val scale by animateFloatAsState(
        targetValue = if (pressed) downScale else 1f,
        animationSpec = if (pressed) Motion.Spatial.Fast else Motion.Spatial.Pop,
        label = "pressScale",
    )
    graphicsLayer {
        scaleX = scale
        scaleY = scale
    }
}

/**
 * Pop scale when [trigger] changes value.
 *
 * The first composition records the trigger without animating: an entrance
 * pop on every screen that happens to start with a non-null trigger reads as
 * a glitch, and the motion card forbids animating first composition.
 */
fun Modifier.popOnChange(trigger: Any?, targetScale: Float = 1.06f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    var lastTrigger by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger == null || trigger == lastTrigger) return@LaunchedEffect
        lastTrigger = trigger
        scale.snapTo(0.96f)
        scale.animateTo(targetScale, Motion.Spatial.Pop)
        scale.animateTo(1f, Motion.Spatial.Pop)
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Error shake (duration-based, not a spring: a shake has no settle to model). */
fun Modifier.shake(trigger: Any?): Modifier = composed {
    val offsetX = remember { Animatable(0f) }
    var lastTrigger by remember { mutableStateOf(trigger) }
    LaunchedEffect(trigger) {
        if (trigger == null || trigger == lastTrigger) return@LaunchedEffect
        lastTrigger = trigger
        offsetX.animateTo(
            targetValue = 0f,
            animationSpec = keyframes {
                durationMillis = 280
                0f at 0
                (-4f) at 40
                4f at 80
                (-3f) at 120
                3f at 160
                (-2f) at 200
                2f at 240
                0f at 280
            },
        )
    }
    graphicsLayer { translationX = offsetX.value }
}
