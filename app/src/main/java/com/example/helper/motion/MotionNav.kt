package com.example.helper.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.example.helper.nav.Routes

object MotionNav {
    fun isTabRoot(entry: NavBackStackEntry): Boolean = Routes.isTabRoot(entry.destination.route)

    fun enter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
    ): EnterTransition {
        return if (isTabRoot(from) && isTabRoot(to)) {
            slideInHorizontally(
                animationSpec = Motion.Spatial.FastOffset,
                initialOffsetX = { fullWidth -> (fullWidth * 0.3f).toInt() },
            ) + fadeIn(Motion.Effects.Fast)
        } else {
            slideInHorizontally(
                animationSpec = Motion.Spatial.FastOffset,
                initialOffsetX = { fullWidth -> (fullWidth * 0.3f).toInt() },
            ) + fadeIn(Motion.Effects.Fast)
        }
    }

    fun exit(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
    ): ExitTransition {
        return if (isTabRoot(from) && isTabRoot(to)) {
            slideOutHorizontally(
                animationSpec = Motion.Spatial.FastOffset,
                targetOffsetX = { fullWidth -> -(fullWidth * 0.3f).toInt() },
            ) + fadeOut(Motion.Effects.Fast)
        } else {
            slideOutHorizontally(
                animationSpec = Motion.Spatial.FastOffset,
                targetOffsetX = { fullWidth -> -(fullWidth * 0.3f).toInt() },
            ) + fadeOut(Motion.Effects.Fast)
        }
    }

    fun popEnter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
    ): EnterTransition {
        return slideInHorizontally(
            animationSpec = Motion.Spatial.FastOffset,
            initialOffsetX = { fullWidth -> -(fullWidth * 0.3f).toInt() },
        ) + fadeIn(Motion.Effects.Fast)
    }

    fun popExit(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
    ): ExitTransition {
        return slideOutHorizontally(
            animationSpec = Motion.Spatial.FastOffset,
            targetOffsetX = { fullWidth -> (fullWidth * 0.3f).toInt() },
        ) + fadeOut(Motion.Effects.Fast)
    }
}


