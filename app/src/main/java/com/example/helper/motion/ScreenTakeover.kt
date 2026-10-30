package com.example.helper.motion

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier

/**
 * A full-screen compose flow (homework draft, announcement draft) layered over
 * the list it was started from. It slides in from the right like a pushed
 * route and slides back out to the right on close — a left-to-right back —
 * while the list underneath stays composed, so its scroll position survives.
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
    Box(modifier = modifier.fillMaxSize()) {
        base()
        AnimatedVisibility(
            visible = visible,
            enter = slideInHorizontally(Motion.Spatial.FastOffset) { fullWidth -> fullWidth } +
                fadeIn(Motion.Effects.Fast),
            exit = slideOutHorizontally(Motion.Spatial.FastOffset) { fullWidth -> fullWidth } +
                fadeOut(Motion.Effects.Fast),
        ) {
            overlay()
        }
    }
}
