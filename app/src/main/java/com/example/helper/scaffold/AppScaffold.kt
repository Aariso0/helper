package com.example.helper.scaffold

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.rememberNavController
import com.example.helper.nav.AppNavHost
import com.example.helper.nav.Routes
import com.example.helper.nav.navigateToTab
import com.example.helper.theme.Motion

/** Exactly the four README tabs — no settings, no profile, no extras (rule 3). */
private data class Tab(val route: String, val label: String, val icon: ImageVector)

private val tabs = listOf(
    Tab(Routes.TODAY, "Today", Icons.Filled.Home),
    Tab(Routes.STUDENTS, "Students", Icons.Filled.Person),
    Tab(Routes.HOMEWORK, "Homework", Icons.AutoMirrored.Filled.List),
    Tab(Routes.PARENTS, "Parents", Icons.Filled.Phone),
)

/**
 * The app's root: bottom NavigationBar (tab roots only — nested screens own
 * the full width) wrapping the [AppNavHost] route table. Zero content insets
 * here on purpose: every feature screen is a full Scaffold that handles the
 * system bars itself, so applying them twice would double-pad.
 */
@Composable
fun AppScaffold() {
    val navController = rememberNavController()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentRoute = backStackEntry?.destination?.route

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        bottomBar = {
            // The bar belongs to tab roots only; on detail screens it slides
            // down and shrinks away (anchored to the screen bottom) so the
            // content padding animates with it — no hard cut, no layout jump.
            AnimatedVisibility(
                visible = Routes.isTabRoot(currentRoute),
                enter = slideInVertically(tween(Motion.Duration.Medium, easing = Motion.Easing.Decelerate)) { it } +
                    expandVertically(tween(Motion.Duration.Medium, easing = Motion.Easing.Decelerate), expandFrom = Alignment.Bottom) +
                    fadeIn(tween(Motion.Duration.Medium, easing = Motion.Easing.Decelerate)),
                exit = slideOutVertically(tween(Motion.Duration.Medium, easing = Motion.Easing.Accelerate)) { it } +
                    shrinkVertically(tween(Motion.Duration.Medium, easing = Motion.Easing.Accelerate), shrinkTowards = Alignment.Bottom) +
                    fadeOut(tween(Motion.Duration.Short, easing = Motion.Easing.Accelerate)),
            ) {
                NavigationBar {
                    tabs.forEach { tab ->
                        NavigationBarItem(
                            selected = currentRoute == tab.route,
                            onClick = {
                                if (currentRoute != tab.route) {
                                    navController.navigateToTab(tab.route)
                                }
                            },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(tab.label) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        AppNavHost(
            navController = navController,
            modifier = Modifier.padding(innerPadding),
        )
    }
}
