package com.karelisio.ruban.ui.screens

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.navigation.NavGraphBuilder
import androidx.navigation.toRoute
import com.karelisio.ruban.ui.components.EmptyState
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.nav.screen
import com.karelisio.ruban.ui.screens.budget.YearRecapScreen
import com.karelisio.ruban.ui.screens.events.EventDetailScreen
import com.karelisio.ruban.ui.screens.events.EventEditScreen
import com.karelisio.ruban.ui.screens.home.HomeScreen
import com.karelisio.ruban.ui.screens.inspire.InspireScreen
import com.karelisio.ruban.ui.screens.received.ReceivedEditScreen
import com.karelisio.ruban.ui.screens.received.ReceivedScreen
import com.karelisio.ruban.ui.screens.settings.BackupScreen
import com.karelisio.ruban.ui.screens.settings.SettingsScreen
import com.karelisio.ruban.ui.screens.ideas.IdeaDetailScreen
import com.karelisio.ruban.ui.screens.ideas.IdeaEditScreen
import com.karelisio.ruban.ui.screens.people.PersonDetailScreen
import com.karelisio.ruban.ui.screens.people.PersonEditScreen

/** Registers every destination of the app. */
object Screens {
    fun register(builder: NavGraphBuilder) = with(builder) {
        screen<Route.Home> { HomeScreen(it.toRoute<Route.Home>().tab) }
        screen<Route.Onboarding> { Placeholder("Bienvenue") }
        screen<Route.PersonDetail> { PersonDetailScreen() }
        screen<Route.PersonEdit> { PersonEditScreen() }
        screen<Route.IdeaDetail> { IdeaDetailScreen() }
        screen<Route.IdeaEdit> { IdeaEditScreen() }
        screen<Route.EventDetail> { EventDetailScreen() }
        screen<Route.EventEdit> { EventEditScreen() }
        screen<Route.YearRecap> { YearRecapScreen() }
        screen<Route.Inspire> { InspireScreen() }
        screen<Route.Received> { ReceivedScreen() }
        screen<Route.ReceivedEdit> { ReceivedEditScreen() }
        screen<Route.Settings> { SettingsScreen() }
        screen<Route.Permissions> { Placeholder("Autorisations") }
        screen<Route.Backup> { BackupScreen() }
    }
}

@Composable
private fun Placeholder(title: String) {
    val nav = LocalNav.current
    Scaffold(topBar = {
        TopAppBar(title = { Text(title) }, navigationIcon = {
            IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") }
        })
    }) { p -> EmptyState("🎀", title, "Bientôt ici.", Modifier.fillMaxSize().padding(p)) }
}
