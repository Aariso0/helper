package com.example.helper.motion

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.navigation.NavBackStackEntry
import com.example.helper.nav.Routes

/**
 * Transition choices for [com.example.helper.nav.AppNavHost]. All specs come
 * from [HyprMotion]; this object only decides which Hyprland leaf applies.
 *
 *  - a detail route pushes in as a window (popin 87%) while the old screen
 *    fades out fast; back reverses it, with the popped screen shrinking away
 *    for predictive back.
 *  - tab ⇄ tab is a peer swap: both sides only fade (workspaces). Sliding or
 *    zooming siblings would lie about a back stack that does not exist.
 */
object MotionNav {
    fun isTabRoot(entry: NavBackStackEntry): Boolean = Routes.isTabRoot(entry.destination.route)

    private fun isTabSwitch(from: NavBackStackEntry, to: NavBackStackEntry): Boolean =
        isTabRoot(from) && isTabRoot(to)

    fun enter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
        reduceMotion: Boolean,
    ): EnterTransition =
        if (isTabSwitch(from, to)) HyprMotion.workspaceIn(reduceMotion)
        else if (to.destination.route == Routes.STUDENT) HyprMotion.quickPopinIn(reduceMotion)
        else HyprMotion.popinIn(reduceMotion)

    fun exit(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
        reduceMotion: Boolean,
    ): ExitTransition = HyprMotion.workspaceOut(reduceMotion)

    fun popEnter(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
        reduceMotion: Boolean,
    ): EnterTransition = HyprMotion.workspaceIn(reduceMotion)

    fun popExit(
        scope: AnimatedContentTransitionScope<NavBackStackEntry>,
        from: NavBackStackEntry,
        to: NavBackStackEntry,
        reduceMotion: Boolean,
    ): ExitTransition =
        if (isTabSwitch(from, to)) HyprMotion.workspaceOut(reduceMotion)
        else HyprMotion.popinOut(reduceMotion)
}
