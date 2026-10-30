package com.karelisio.ruban.ui.screens.home

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.EditNote
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FloatingActionButtonMenu
import androidx.compose.material3.FloatingActionButtonMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleFloatingActionButton
import androidx.compose.material3.ToggleFloatingActionButtonDefaults.animateIcon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.rememberVectorPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.EmptyState
import com.karelisio.ruban.ui.components.SharedKeys
import com.karelisio.ruban.ui.components.sharedBounds
import com.karelisio.ruban.ui.nav.CaptureStart
import com.karelisio.ruban.ui.nav.HomeTab
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class HomeSummary(
    val ideas: Int = 0,
    val ready: Int = 0,
    val people: Int = 0,
    val nextEvent: String? = null,
    val spentThisYear: Long = 0,
)

class HomeViewModel(c: AppContainer) : ViewModel() {
    val summary = combine(c.ideas.observeAll(), c.persons.observeAll(), c.events.observeUpcoming()) { ideas, persons, upcoming ->
        val year = LocalDate.now().year
        val next = upcoming.firstOrNull()
        HomeSummary(
            ideas = ideas.count { it.idea.status == GiftStatus.IDEA },
            ready = ideas.count { it.idea.status == GiftStatus.BOUGHT || it.idea.status == GiftStatus.WRAPPED },
            people = persons.size,
            nextEvent = next?.let { "${it.emoji} ${it.title} · ${Dates.relative(it.daysFrom(LocalDate.now())).lowercase()}" },
            spentThisYear = ideas.filter { (it.idea.occasionYear ?: Dates.fromMillis(it.idea.boughtAt ?: it.idea.createdAt).year) == year }
                .sumOf { it.idea.spentCents },
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), HomeSummary())
}

@Composable
internal fun HomeSubtitle(tab: HomeTab) {
    val vm = rubanViewModel { HomeViewModel(this) }
    val s by vm.summary.collectAsStateWithLifecycle()
    val text = when (tab) {
        HomeTab.IDEAS -> buildList {
            add(s.ideas.plural("idée"))
            if (s.ready > 0) add(s.ready.plural("cadeau prêt", "cadeaux prêts"))
        }.joinToString(" · ")
        HomeTab.PEOPLE -> s.people.plural("proche")
        HomeTab.EVENTS -> s.nextEvent ?: "Rien de prévu"
        HomeTab.BUDGET -> "Dépensé cette année : ${com.karelisio.ruban.util.Money.format(s.spentThisYear)}"
    }
    AnimatedContent(text, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "subtitle") { Text(it, maxLines = 1) }
}

@Composable
internal fun HomeFab(tab: HomeTab) {
    val nav = LocalNav.current
    AnimatedContent(
        tab,
        transitionSpec = {
            scaleIn(spring(0.6f, Spring.StiffnessMediumLow)) + fadeIn() togetherWith scaleOut() + fadeOut()
        },
        label = "fab",
    ) { t ->
        when (t) {
            HomeTab.IDEAS -> NewIdeaFabMenu()
            HomeTab.PEOPLE -> ExtendedFloatingActionButton(
                onClick = { nav.go(Route.PersonEdit()) },
                icon = { Icon(Icons.Rounded.PersonAdd, null) },
                text = { Text("Ajouter") },
            )
            HomeTab.EVENTS -> ExtendedFloatingActionButton(
                onClick = { nav.go(Route.EventEdit()) },
                icon = { Icon(Icons.Rounded.EventAvailable, null) },
                text = { Text("Événement") },
            )
            HomeTab.BUDGET -> Box {}
        }
    }
}

/** Expressive FAB menu: type, photograph, dictate or pick a picture. */
@Composable
private fun NewIdeaFabMenu() {
    val nav = LocalNav.current
    val haptic = LocalHapticFeedback.current
    var expanded by rememberSaveable { mutableStateOf(false) }
    BackHandler(expanded) { expanded = false }
    fun go(start: String?) {
        expanded = false
        nav.go(Route.IdeaEdit(startWith = start))
    }
    FloatingActionButtonMenu(
        expanded = expanded,
        button = {
            ToggleFloatingActionButton(
                modifier = Modifier
                    .semantics { contentDescription = "Nouvelle idée" }
                    .sharedBounds(SharedKeys.FAB),
                checked = expanded,
                onCheckedChange = {
                    haptic.performHapticFeedback(HapticFeedbackType.ContextClick)
                    expanded = it
                },
            ) {
                val painter = rememberVectorPainter(if (checkedProgress > 0.5f) Icons.Rounded.Close else Icons.Rounded.Add)
                Icon(painter, null, Modifier.animateIcon({ checkedProgress }))
            }
        },
    ) {
        FloatingActionButtonMenuItem(onClick = { go(null) }, icon = { Icon(Icons.Rounded.EditNote, null) }, text = { Text("Écrire") })
        FloatingActionButtonMenuItem(onClick = { go(CaptureStart.CAMERA) }, icon = { Icon(Icons.Rounded.CameraAlt, null) }, text = { Text("Photo") })
        FloatingActionButtonMenuItem(onClick = { go(CaptureStart.VOICE) }, icon = { Icon(Icons.Rounded.Mic, null) }, text = { Text("Dicter") })
        FloatingActionButtonMenuItem(onClick = { go(CaptureStart.GALLERY) }, icon = { Icon(Icons.Rounded.PhotoLibrary, null) }, text = { Text("Galerie") })
    }
}

@Composable
internal fun BudgetTab(padding: PaddingValues) =
    EmptyState("🪙", "Budget", "Suis ce que tu prévois et ce que tu dépenses.", Modifier.padding(padding))
