package com.example.helper.theme

import androidx.compose.ui.unit.dp

/**
 * Sizing tokens for the fewest-taps design rules (README rules 1 & 4): rows
 * are big targets, primary actions sit at the bottom of the screen within
 * thumb reach. Screens read these instead of repeating magic numbers.
 */
object ShellDimens {
    /** Minimum interactive target (Android accessibility guidance: 48dp). */
    val minTouchTarget = 48.dp

    /** Height of a navigable content row (class rows, link rows). */
    val rowMinHeight = 64.dp

    /** Bottom-anchored primary action button height. */
    val primaryActionHeight = 56.dp

    /** Standard horizontal screen padding. */
    val screenPadding = 16.dp

    /** Vertical gap between page sections. */
    val sectionSpacing = 24.dp
}
