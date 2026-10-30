package com.karelisio.ruban.ui.screens.events

import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.FormField
import com.karelisio.ruban.ui.components.MoneyField
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.screens.people.FormSection
import com.karelisio.ruban.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

private val emojis = listOf("🎉", "💞", "🏡", "🎓", "💍", "👶", "🥂", "🎊", "🌴", "🏆", "🎶", "🍾", "🕯️", "🎈", "🧑‍🍳", "🚗")

private val presets = listOf(
    "💞" to "Anniversaire de couple",
    "🏡" to "Crémaillère",
    "💍" to "Mariage",
    "👶" to "Naissance",
    "🎓" to "Diplôme",
    "🥂" to "Départ en retraite",
)

class EventEditViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val route = handle.toRoute<Route.EventEdit>()
    val form = MutableStateFlow(
        Event(
            kind = EventKind.CUSTOM, title = "", emoji = "🎉",
            personId = route.personId.takeIf { it != 0L },
            month = LocalDate.now().monthValue, day = LocalDate.now().dayOfMonth, year = LocalDate.now().year,
        ),
    )
    val persons = c.persons.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    init {
        if (route.id != 0L) viewModelScope.launch { c.events.get(route.id)?.let { form.value = it } }
    }

    fun update(block: Event.() -> Event) {
        form.value = form.value.block()
    }

    suspend fun save(): Long = c.events.save(form.value.copy(title = form.value.title.trim()))
}

@Composable
fun EventEditScreen() {
    val vm = rubanViewModel { EventEditViewModel(this, it) }
    val event by vm.form.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val date = LocalDate.of(event.year ?: LocalDate.now().year, event.month ?: 1, event.day ?: 1)

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.toPickerMillis(date))
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { Dates.fromPickerMillis(it) }?.let { d ->
                        vm.update { copy(month = d.monthValue, day = d.dayOfMonth, year = d.year) }
                    }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (event.id == 0L) "Nouvel événement" else "Modifier") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.Rounded.Close, "Fermer") } },
                actions = {
                    Button(
                        onClick = {
                            scope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                val id = vm.save()
                                if (event.id == 0L) nav.replace(Route.EventDetail(id)) else nav.back()
                            }
                        },
                        enabled = event.title.isNotBlank(),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Enregistrer") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            if (event.id == 0L) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { (e, t) ->
                        FilterChip(event.title == t, { vm.update { copy(title = t, emoji = e) } }, { Text("$e $t") })
                    }
                }
            }
            FormField(event.title, { v -> vm.update { copy(title = v) } }, "Nom de l'événement")
            FormSection("Symbole") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    emojis.forEach { e ->
                        val selected = event.emoji == e
                        val scale by animateFloatAsState(if (selected) 1.2f else 1f, spring(0.4f, Spring.StiffnessMedium), label = "emoji")
                        Box(
                            Modifier
                                .size(44.dp)
                                .graphicsLayer { scaleX = scale; scaleY = scale }
                                .clip(CircleShape)
                                .background(if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                                .clickable { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.update { copy(emoji = e) } },
                            contentAlignment = Alignment.Center,
                        ) { Text(e, fontSize = 22.sp) }
                    }
                }
            }
            FormSection("Date") {
                OutlinedButton(onClick = { pickDate = true }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(Dates.weekday(date) + " " + date.year)
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Chaque année", style = MaterialTheme.typography.titleMedium)
                        Text(
                            if (event.recurring) "Ruban comptera les années depuis ${event.year ?: date.year}." else "Une seule fois.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(event.recurring, { v -> vm.update { copy(recurring = v) } })
                }
            }
            if (persons.isNotEmpty()) FormSection("Avec qui ?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(event.personId == null, { vm.update { copy(personId = null) } }, { Text("Personne en particulier") })
                    persons.forEach { p ->
                        FilterChip(
                            selected = event.personId == p.id,
                            onClick = { vm.update { copy(personId = p.id) } },
                            label = { Text(p.name) },
                            leadingIcon = { PersonAvatar(p, 24.dp) },
                        )
                    }
                }
            }
            FormSection("Budget") {
                MoneyField(event.budgetCents, { v -> vm.update { copy(budgetCents = v) } }, "Budget pour l'occasion")
            }
            FormField(event.notes, { v -> vm.update { copy(notes = v) } }, "Notes", singleLine = false, minLines = 2)
            Spacer(Modifier.height(40.dp))
        }
    }
}
