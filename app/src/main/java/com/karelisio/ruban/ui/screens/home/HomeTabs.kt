package com.karelisio.ruban.ui.screens.home

import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import com.karelisio.ruban.ui.components.EmptyState
import com.karelisio.ruban.ui.nav.HomeTab
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route

@Composable internal fun HomeSubtitle(tab: HomeTab) {}

@Composable internal fun HomeFab(tab: HomeTab) {
    val nav = LocalNav.current
    ExtendedFloatingActionButton(onClick = { nav.go(Route.IdeaEdit()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Nouvelle idée") })
}

@Composable internal fun IdeasTab(padding: PaddingValues) =
    EmptyState("💡", "Aucune idée", "Note ici tes idées de cadeaux.", Modifier.padding(padding))

@Composable internal fun PeopleTab(padding: PaddingValues) =
    EmptyState("🫶", "Aucun proche", "Ajoute les personnes à qui tu offres.", Modifier.padding(padding))

@Composable internal fun EventsTab(padding: PaddingValues) =
    EmptyState("📅", "Rien à l'horizon", "Les anniversaires et fêtes apparaîtront ici.", Modifier.padding(padding))

@Composable internal fun BudgetTab(padding: PaddingValues) =
    EmptyState("🪙", "Budget", "Suis ce que tu prévois et ce que tu dépenses.", Modifier.padding(padding))
