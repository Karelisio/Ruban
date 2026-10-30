package com.karelisio.ruban.ui.nav

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import com.karelisio.ruban.ui.components.LocalNavAnimatedScope
import com.karelisio.ruban.ui.components.LocalSharedTransitionScope
import com.karelisio.ruban.ui.screens.Screens

/** Thin wrapper around the NavController, available everywhere through [LocalNav]. */
class Nav(val controller: NavHostController) {
    fun go(route: Route) = controller.navigate(route) { launchSingleTop = true }
    fun back() {
        if (!controller.popBackStack()) controller.navigate(Route.Home())
    }
    fun replace(route: Route) {
        controller.popBackStack()
        go(route)
    }
    fun home() = controller.navigate(Route.Home()) {
        popUpTo(0) { inclusive = true }
        launchSingleTop = true
    }
}

val LocalNav = staticCompositionLocalOf<Nav> { error("No Nav provided") }

private typealias Scope = AnimatedContentTransitionScope<NavBackStackEntry>

private val enter: Scope.() -> EnterTransition = {
    fadeIn(tween(260, delayMillis = 60)) + scaleIn(spring(0.85f, Spring.StiffnessMediumLow), initialScale = 0.94f)
}
private val exit: Scope.() -> ExitTransition = {
    fadeOut(tween(180)) + scaleOut(tween(260), targetScale = 1.03f)
}
private val popEnter: Scope.() -> EnterTransition = {
    fadeIn(tween(260, delayMillis = 40)) + scaleIn(spring(0.85f, Spring.StiffnessMediumLow), initialScale = 1.04f)
}
// Shrinks with the predictive back gesture.
private val popExit: Scope.() -> ExitTransition = {
    fadeOut(tween(220)) + scaleOut(tween(300), targetScale = 0.88f)
}

inline fun <reified T : Any> NavGraphBuilder.screen(noinline content: @Composable (NavBackStackEntry) -> Unit) {
    composable<T> { entry ->
        CompositionLocalProvider(LocalNavAnimatedScope provides this) { content(entry) }
    }
}

@Composable
fun RubanNavHost(nav: NavHostController, start: Route, modifier: Modifier = Modifier) {
    val wrapper = remember(nav) { Nav(nav) }
    SharedTransitionLayout(modifier) {
        CompositionLocalProvider(LocalSharedTransitionScope provides this, LocalNav provides wrapper) {
            NavHost(
                navController = nav,
                startDestination = start,
                enterTransition = enter,
                exitTransition = exit,
                popEnterTransition = popEnter,
                popExitTransition = popExit,
            ) {
                Screens.register(this)
            }
        }
    }
}
