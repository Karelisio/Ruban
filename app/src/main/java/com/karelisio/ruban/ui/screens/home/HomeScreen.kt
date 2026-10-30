package com.karelisio.ruban.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.People
import androidx.compose.material.icons.outlined.Savings
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Lightbulb
import androidx.compose.material.icons.rounded.People
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Savings
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.ShortNavigationBar
import androidx.compose.material3.ShortNavigationBarItem
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalHapticFeedback
import com.karelisio.ruban.ui.components.CascadeState
import com.karelisio.ruban.ui.components.LocalCascade
import com.karelisio.ruban.ui.nav.HomeTab
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route

private data class TabSpec(val tab: HomeTab, val label: String, val title: String, val icon: ImageVector, val selectedIcon: ImageVector)

private val tabs = listOf(
    TabSpec(HomeTab.IDEAS, "Idées", "Idées cadeaux", Icons.Outlined.Lightbulb, Icons.Rounded.Lightbulb),
    TabSpec(HomeTab.PEOPLE, "Proches", "Mes proches", Icons.Outlined.People, Icons.Rounded.People),
    TabSpec(HomeTab.EVENTS, "Agenda", "À venir", Icons.Outlined.CalendarMonth, Icons.Rounded.CalendarMonth),
    TabSpec(HomeTab.BUDGET, "Budget", "Budget", Icons.Outlined.Savings, Icons.Rounded.Savings),
)

@Composable
fun HomeScreen(initialTab: Int) {
    val nav = LocalNav.current
    val haptic = LocalHapticFeedback.current
    var current by rememberSaveable { mutableIntStateOf(initialTab.coerceIn(0, tabs.lastIndex)) }
    val spec = tabs[current]
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())

    BackHandler(enabled = current != 0) { current = 0 }

    Scaffold(
        modifier = Modifier.fillMaxSize().nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = {
                    AnimatedContent(spec.title, transitionSpec = { fadeIn(tween(220)) togetherWith fadeOut(tween(120)) }, label = "title") {
                        Text(it)
                    }
                },
                subtitle = { HomeSubtitle(spec.tab) },
                actions = {
                    IconButton(onClick = { nav.go(Route.Received) }) { Icon(Icons.Rounded.Redeem, "Cadeaux reçus") }
                    IconButton(onClick = { nav.go(Route.Settings) }) { Icon(Icons.Rounded.Settings, "Réglages") }
                },
                scrollBehavior = scroll,
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface,
                    scrolledContainerColor = MaterialTheme.colorScheme.surfaceContainer,
                ),
            )
        },
        bottomBar = {
            ShortNavigationBar {
                tabs.forEachIndexed { index, t ->
                    ShortNavigationBarItem(
                        selected = index == current,
                        onClick = {
                            if (index != current) haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            current = index
                        },
                        icon = { Icon(if (index == current) t.selectedIcon else t.icon, null) },
                        label = { Text(t.label) },
                    )
                }
            }
        },
        floatingActionButton = { HomeFab(spec.tab) },
    ) { padding ->
        AnimatedContent(
            targetState = spec.tab,
            transitionSpec = {
                (fadeIn(tween(220, delayMillis = 60)) + scaleIn(spring(0.9f, Spring.StiffnessMediumLow), initialScale = 0.96f))
                    .togetherWith(fadeOut(tween(120)) + scaleOut(tween(180), targetScale = 0.98f))
            },
            label = "tab",
        ) { tab ->
            val cascade = remember(tab) { CascadeState() }
            CompositionLocalProvider(LocalCascade provides cascade) {
                HomeTabContent(tab, padding)
            }
        }
    }
}

@Composable
internal fun HomeTabContent(tab: HomeTab, padding: PaddingValues) {
    when (tab) {
        HomeTab.IDEAS -> IdeasTab(padding)
        HomeTab.PEOPLE -> PeopleTab(padding)
        HomeTab.EVENTS -> EventsTab(padding)
        HomeTab.BUDGET -> BudgetTab(padding)
    }
}
