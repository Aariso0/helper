package com.example.helper.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import com.example.helper.BuildConfig

/**
 * A full-screen compose flow (homework draft, announcement draft) layered over
 * the list it was started from. It pops in like a pushed window and shrinks
 * away on close, while the list underneath stays composed so its scroll
 * position survives.
 *
 * The list is not a nav destination, so NavHost's own push/pop transitions
 * cannot cover it; this is the local equivalent for those in-screen takeovers.
 */
@Composable
fun ScreenTakeover(
    visible: Boolean,
    modifier: Modifier = Modifier,
    base: @Composable () -> Unit,
    overlay: @Composable () -> Unit,
) {
    val reduceMotion = LocalReduceMotion.current
    LaunchedEffect(visible) {
        if (BuildConfig.DEBUG) {
            MotionDiag.local("TAKEOVER_VISIBILITY_CHANGED", "visible=$visible")
        }
    }
    Box(modifier = modifier.fillMaxSize()) {
        base()
        AnimatedVisibility(
            visible = visible,
            enter = HyprMotion.popinIn(reduceMotion),
            exit = HyprMotion.popinOut(reduceMotion),
        ) {
            overlay()
        }
    }
}
