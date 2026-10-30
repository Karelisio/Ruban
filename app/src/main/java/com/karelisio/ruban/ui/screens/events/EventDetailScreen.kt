package com.karelisio.ruban.ui.screens.events

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
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
import com.karelisio.ruban.data.repo.todayFlow
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.Occurrence
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.AnimatedMoney
import com.karelisio.ruban.ui.components.IdeaCard
import com.karelisio.ruban.ui.components.LiveCountdown
import com.karelisio.ruban.ui.components.MoneyField
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.components.SectionHeader
import com.karelisio.ruban.ui.components.ShapeBadge
import com.karelisio.ruban.ui.components.SharedKeys
import com.karelisio.ruban.ui.components.cascade
import com.karelisio.ruban.ui.components.sharedBounds
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.permissions.AppPermission
import com.karelisio.ruban.ui.permissions.PermissionBanner
import com.karelisio.ruban.ui.screens.home.AgendaItem
import com.karelisio.ruban.ui.screens.home.PrepChip
import com.karelisio.ruban.ui.screens.home.agendaItem
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

val offsetChoices = listOf(60, 30, 14, 7, 3, 1, 0)

fun offsetLabel(days: Int) = when (days) {
    0 -> "Le jour J"
    1 -> "J-1"
    else -> "J-$days"
}

data class EventDetailUi(
    val event: Event? = null,
    val item: AgendaItem? = null,
    val globalOffsets: List<Int> = emptyList(),
    val loaded: Boolean = false,
)

class EventDetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val id = handle.toRoute<Route.EventDetail>().id

    val ui = combine(c.events.observe(id), c.persons.observeAll(), c.ideas.observeAll(), c.settings.settings, todayFlow()) { event, persons, ideas, settings, today ->
        if (event == null) return@combine EventDetailUi(loaded = true)
        val person = event.personId?.let { pid -> persons.firstOrNull { it.id == pid } }
        val date = EventDates.next(event, person, today) ?: EventDates.previous(event, person, today)
        EventDetailUi(
            event = event,
            item = date?.let { agendaItem(Occurrence(event, person, it), ideas) },
            globalOffsets = settings.reminderOffsets,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventDetailUi())

    fun update(block: Event.() -> Event) = viewModelScope.launch {
        ui.value.event?.let { c.events.save(it.block()) }
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        ui.value.event?.let { c.events.delete(it) }
        onDone()
    }
}

@Composable
fun EventDetailScreen() {
    val vm = rubanViewModel { EventDetailViewModel(this, it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var editBudget by rememberSaveable { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val event = ui.event

    if (editBudget && event != null) {
        var value by rememberSaveable { mutableStateOf(event.budgetCents) }
        AlertDialog(
            onDismissRequest = { editBudget = false },
            title = { Text("Budget") },
            text = { MoneyField(value, { value = it }, "Budget pour cet événement") },
            confirmButton = { TextButton(onClick = { vm.update { copy(budgetCents = value) }; editBudget = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { editBudget = false }) { Text("Annuler") } },
        )
    }
    if (confirmDelete && event != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer « ${event.title} » ?") },
            text = {
                Text(
                    if (event.kind.isPersonal) "Retire la date de la fiche du proche pour qu'elle ne revienne pas."
                    else "Les idées liées resteront dans Ruban, sans occasion.",
                )
            },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { vm.delete { nav.back() } } }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }

    Scaffold(
        modifier = Modifier.sharedBounds(SharedKeys.event(vm.id)),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    if (event?.kind == EventKind.CUSTOM) {
                        IconButton(onClick = { nav.go(Route.EventEdit(vm.id)) }) { Icon(Icons.Rounded.Edit, "Modifier") }
                    }
                    if (event != null && !event.kind.isPersonal) {
                        IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Supprimer") }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        val item = ui.item
        if (event == null || item == null) return@Scaffold
        val o = item.occurrence
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp)) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    val person = o.person
                    Box {
                        if (person != null) PersonAvatar(person, 112.dp)
                        else ShapeBadge(size = 112.dp, polygonIndex = event.id.toInt() + 3, container = MaterialTheme.colorScheme.secondaryContainer, spin = true) {
                            Text(o.emoji, fontSize = 52.sp)
                        }
                        if (person != null) Text(o.emoji, fontSize = 34.sp, modifier = Modifier.align(Alignment.BottomEnd))
                    }
                    Spacer(Modifier.height(12.dp))
                    Text(o.title, style = MaterialTheme.typography.headlineMedium, textAlign = TextAlign.Center)
                    Text(
                        Dates.weekday(o.date) + " " + o.date.year + (o.yearsCount?.let { " · $it ans" } ?: ""),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Spacer(Modifier.height(16.dp))
                    LiveCountdown(o.date, style = MaterialTheme.typography.displayLarge)
                    Spacer(Modifier.height(12.dp))
                    PrepChip(item.prep.prep)
                    Spacer(Modifier.height(16.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { nav.go(Route.IdeaEdit(eventId = event.id, personId = person?.id ?: 0)) }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Idée")
                        }
                        if (person != null) {
                            FilledTonalButton(onClick = { nav.go(Route.Inspire(person.id, event.id)) }, shapes = ButtonDefaults.shapes()) {
                                Icon(Icons.Rounded.AutoAwesome, null)
                                Spacer(Modifier.width(6.dp))
                                Text("Inspire-moi")
                            }
                        }
                    }
                }
            }

            item(key = "budget") {
                Surface(
                    onClick = { editBudget = true },
                    modifier = Modifier.fillMaxWidth().padding(16.dp),
                    shape = MaterialTheme.shapes.extraLarge,
                    color = MaterialTheme.colorScheme.surfaceContainerLow,
                ) {
                    Column(Modifier.padding(20.dp)) {
                        Text("Budget ${o.date.year}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                        Row(verticalAlignment = Alignment.Bottom) {
                            AnimatedMoney(item.spent, style = MaterialTheme.typography.headlineLarge)
                            Text(
                                event.budgetCents?.let { " / ${Money.format(it)}" } ?: "  · touche pour fixer un budget",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        val budget = event.budgetCents
                        if (budget != null && budget > 0) {
                            Spacer(Modifier.height(10.dp))
                            LinearWavyProgressIndicator(
                                progress = { (item.spent.toFloat() / budget).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth(),
                                color = if (item.spent > budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                            )
                        }
                        if (item.planned > item.spent) {
                            Spacer(Modifier.height(6.dp))
                            Text(
                                "Prévu en tout : ${Money.format(item.planned)}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }

            item(key = "ideasHeader") { SectionHeader("Idées et cadeaux") }
            if (item.prep.ideas.isEmpty()) item(key = "noIdeas") {
                Text(
                    "Rien pour l'instant. Ajoute une idée ou laisse Ruban t'inspirer.",
                    Modifier.padding(horizontal = 20.dp),
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            item.prep.ideas.forEachIndexed { i, idea ->
                item(key = "idea-${idea.idea.id}") {
                    IdeaCard(idea, { nav.go(Route.IdeaDetail(idea.idea.id)) }, Modifier.padding(horizontal = 16.dp, vertical = 5.dp).cascade(i))
                }
            }

            item(key = "reminders") { RemindersSection(event, ui.globalOffsets, vm::update) }
        }
    }
}

@Composable
private fun RemindersSection(event: Event, global: List<Int>, update: (Event.() -> Event) -> Unit) {
    val custom = EventDates.parseOffsets(event.reminderOffsets)
    val active = custom ?: global
    Column {
        SectionHeader("Rappels")
        Surface(Modifier.fillMaxWidth().padding(horizontal = 16.dp), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Me rappeler cet événement", Modifier.weight(1f), style = MaterialTheme.typography.titleMedium)
                    Switch(event.remindersEnabled, { v -> update { copy(remindersEnabled = v) } })
                }
                if (event.remindersEnabled) {
                    PermissionBanner(AppPermission.NOTIFICATIONS, "Les notifications sont coupées : aucun rappel ne s'affichera.")
                    PermissionBanner(AppPermission.EXACT_ALARMS, "Sans alarmes exactes, les rappels peuvent arriver en retard.")
                    PermissionBanner(AppPermission.BATTERY, "L'optimisation de batterie peut bloquer les rappels.")
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("Réglage général", style = MaterialTheme.typography.bodyLarge)
                            Text(global.joinToString(", ") { offsetLabel(it) }, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(custom == null, { useGlobal -> update { copy(reminderOffsets = if (useGlobal) null else global.joinToString(",")) } })
                    }
                    if (custom != null) {
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            offsetChoices.forEach { d ->
                                FilterChip(
                                    selected = d in active,
                                    onClick = {
                                        val next = if (d in active) active - d else active + d
                                        update { copy(reminderOffsets = next.sortedDescending().joinToString(",")) }
                                    },
                                    label = { Text(offsetLabel(d)) },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
