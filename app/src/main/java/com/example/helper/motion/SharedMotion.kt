package com.example.helper.motion

import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier

val LocalSharedTransitionScope = staticCompositionLocalOf<SharedTransitionScope?> { null }

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun SharedMotionHost(content: @Composable () -> Unit) {
    SharedTransitionLayout {
        CompositionLocalProvider(LocalSharedTransitionScope provides this) {
            content()
        }
    }
}

/**
 * Tags this element as the shared half of a navigation transition.
 *
 * Feature screens receive the destination's [AnimatedVisibilityScope] from
 * the nav host and pass it here; when either the host or the scope is missing
 * the modifier is the identity, so screens stay usable in previews and tests.
 *
 * Put this after size/padding and before the clip (see the motion card): the
 * bounds are measured from the sized element, and clipping first would cut
 * the animation off at the old shape.
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun Modifier.sharedElementIfPossible(
    key: Any,
    animatedVisibilityScope: AnimatedVisibilityScope?,
): Modifier {
    val sharedScope = LocalSharedTransitionScope.current
    if (animatedVisibilityScope == null || sharedScope == null) return this
    return with(sharedScope) {
        this@sharedElementIfPossible.sharedElement(
            sharedContentState = rememberSharedContentState(key = key),
            animatedVisibilityScope = animatedVisibilityScope,
        )
    }
}
