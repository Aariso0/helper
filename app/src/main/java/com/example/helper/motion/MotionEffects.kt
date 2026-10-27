package com.example.helper.motion

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.keyframes
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.PressInteraction
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.composed
import androidx.compose.ui.graphics.graphicsLayer

/**
 * Press feedback: scale down on press, spring back on release.
 * Keep off tall list rows (use sparingly).
 */
fun Modifier.pressScale(
    enabled: Boolean = true,
    downScale: Float = 0.97f,
): Modifier = composed {
    if (!enabled) return@composed this
    val scale = remember { Animatable(1f) }
    val interactionSource = remember { MutableInteractionSource() }
    LaunchedEffect(interactionSource) {
        interactionSource.interactions.collect { interaction ->
            when (interaction) {
                is PressInteraction.Press -> {
                    scale.animateTo(downScale, Motion.Spatial.Fast)
                }
                is PressInteraction.Release -> {
                    scale.animateTo(1f, Motion.Spatial.Pop)
                }
                is PressInteraction.Cancel -> {
                    scale.animateTo(1f, Motion.Spatial.Pop)
                }
            }
        }
    }
    this.clickable(interactionSource = interactionSource, indication = null) {}
        .graphicsLayer {
            scaleX = scale.value
            scaleY = scale.value
        }
}

/** Pop scale on change (icon/check). */
fun Modifier.popOnChange(trigger: Any?, targetScale: Float = 1.06f): Modifier = composed {
    val scale = remember { Animatable(1f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
        scale.snapTo(0.96f)
        scale.animateTo(targetScale, Motion.Spatial.Pop)
        scale.animateTo(1f, Motion.Spatial.Pop)
    }
    graphicsLayer {
        scaleX = scale.value
        scaleY = scale.value
    }
}

/** Error shake (duration-based, not a spring). */
fun Modifier.shake(trigger: Any?): Modifier = composed {
    val offsetX = remember { Animatable(0f) }
    LaunchedEffect(trigger) {
        if (trigger == null) return@LaunchedEffect
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
