package com.example.helper.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.navigation.NavBackStackEntry
import com.example.helper.nav.Routes

/**
 * Transition specs for [com.example.helper.nav.AppNavHost].
 *
 * Two motions, chosen by what the navigation actually means:
 *  - tab ⇄ tab is a *fade-through*: the old screen shrinks to 0.92 while it
 *    fades, the new one fades in at full size. Tabs are siblings, so sliding
 *    them horizontally would lie about a stack that does not exist.
 *  - every other change is a *shared-axis push*: in from 30% of the width,
 *    out the other way, both fading, reversed on pop.
 *
 * All specs are springs, so a second tap mid-flight continues the velocity
 * instead of restarting. `slideInHorizontally` mirrors under RTL for free.
 */
object MotionNav {
    fun isTabRoot(entry: NavBackStackEntry): Boolean = Routes.isTabRoot(entry.destination.route)

    private fun isTabSwitch(from: NavBackStackEntry, to: NavBackStackEntry): Boolean =
        isTabRoot(from) && isTabRoot(to)

    fun enter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
    ): EnterTransition {
        if (isTabSwitch(from, to)) return tabEnter()
        return slideInHorizontally(
            animationSpec = Motion.Spatial.FastOffset,
            initialOffsetX = { fullWidth -> (fullWidth * 0.3f).toInt() },
        ) + fadeIn(Motion.Effects.Fast)
    }

    fun exit(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
    ): ExitTransition {
        if (isTabSwitch(from, to)) return tabExit()
        return slideOutHorizontally(
            animationSpec = Motion.Spatial.FastOffset,
            targetOffsetX = { fullWidth -> -(fullWidth * 0.3f).toInt() },
        ) + fadeOut(Motion.Effects.Fast)
    }

    fun popEnter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
    ): EnterTransition {
        if (isTabSwitch(from, to)) return tabEnter()
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
        if (isTabSwitch(from, to)) return tabExit()
        return slideOutHorizontally(
            animationSpec = Motion.Spatial.FastOffset,
            targetOffsetX = { fullWidth -> (fullWidth * 0.3f).toInt() },
        ) + fadeOut(Motion.Effects.Fast)
    }

    /** Incoming tab: fade only. A scale-in on arrival reads as a zoom, not a swap. */
    private fun tabEnter(): EnterTransition = fadeIn(Motion.Effects.Fast)

    /** Outgoing tab: recede to 0.92 while fading, so the swap has depth. */
    private fun tabExit(): ExitTransition =
        scaleOut(targetScale = 0.92f, animationSpec = Motion.Spatial.Fast) +
            fadeOut(Motion.Effects.Fast)
}
