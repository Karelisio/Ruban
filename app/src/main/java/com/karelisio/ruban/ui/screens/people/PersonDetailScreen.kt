package com.karelisio.ruban.ui.screens.people

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AutoAwesome
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.model.IdeaWithPeople
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.ReceivedGift
import com.karelisio.ruban.data.repo.todayFlow
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.Interests
import com.karelisio.ruban.domain.Occurrence
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.AnimatedMoney
import com.karelisio.ruban.ui.components.CountdownBadge
import com.karelisio.ruban.ui.components.IdeaCard
import com.karelisio.ruban.ui.components.LiveCountdown
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.components.Pill
import com.karelisio.ruban.ui.components.SectionHeader
import com.karelisio.ruban.ui.components.SharedKeys
import com.karelisio.ruban.ui.components.cascade
import com.karelisio.ruban.ui.components.sharedBounds
import com.karelisio.ruban.ui.components.sharedElement
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class PersonDetailUi(
    val person: Person? = null,
    val upcoming: List<Occurrence> = emptyList(),
    val ideas: List<IdeaWithPeople> = emptyList(),
    val given: List<IdeaWithPeople> = emptyList(),
    val received: List<ReceivedGift> = emptyList(),
    val spentThisYear: Long = 0,
    val plannedThisYear: Long = 0,
    val loaded: Boolean = false,
)

class PersonDetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val id = handle.toRoute<Route.PersonDetail>().id

    val ui = combine(
        c.persons.observe(id),
        c.events.observeForPerson(id),
        c.events.observeAll(),
        c.ideas.observeForPerson(id),
        combine(c.received.observeAll(), todayFlow()) { r, t -> r to t },
    ) { person, own, all, ideas, (received, today) ->
        if (person == null) return@combine PersonDetailUi(loaded = true)
        val year = today.year
        val ownIds = own.map { it.id }.toSet()
        // Own events (birthday, name day, custom events linked to the person) + holidays.
        val relevant = all.filter { it.id in ownIds || it.personId == null }
        val upcoming = EventDates.upcoming(relevant, mapOf(person.id to person), today).take(4)
        val given = ideas.filter { it.idea.status == GiftStatus.GIVEN }
        val thisYear = ideas.filter { (it.idea.occasionYear ?: year) == year }
        PersonDetailUi(
            person = person,
            upcoming = upcoming,
            ideas = ideas.filter { it.idea.status != GiftStatus.GIVEN },
            given = given.sortedByDescending { it.idea.givenAt ?: 0 },
            received = received.filter { it.fromPersonId == id },
            spentThisYear = thisYear.sumOf { it.idea.spentCents / it.people.size.coerceAtLeast(1) },
            plannedThisYear = thisYear.filter { it.idea.status != GiftStatus.GIVEN }.sumOf { (it.idea.priceCents ?: 0) / it.people.size.coerceAtLeast(1) },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PersonDetailUi())

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        ui.value.person?.let { c.persons.delete(it) }
        onDone()
    }
}

@Composable
fun PersonDetailScreen() {
    val vm = rubanViewModel { PersonDetailViewModel(this, it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val person = ui.person
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    val scope = rememberCoroutineScope()

    if (confirmDelete && person != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer ${person.name} ?") },
            text = { Text("Ses dates et son lien avec les idées seront supprimés. Les idées restent dans Ruban.") },
            confirmButton = {
                TextButton(onClick = { confirmDelete = false; scope.launch { vm.delete { nav.home() } } }) { Text("Supprimer") }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }

    Scaffold(
        modifier = Modifier.sharedBounds(SharedKeys.person(vm.id)),
        topBar = {
            TopAppBar(
                title = {},
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = {
                    IconButton(onClick = { nav.go(Route.PersonEdit(vm.id)) }) { Icon(Icons.Rounded.Edit, "Modifier") }
                    Box {
                        IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "Plus") }
                        DropdownMenu(menu, { menu = false }) {
                            DropdownMenuItem(
                                text = { Text("Supprimer") },
                                leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                                onClick = { menu = false; confirmDelete = true },
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
            )
        },
    ) { padding ->
        if (person == null) return@Scaffold
        val today = remember { LocalDate.now() }
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
        ) {
            item(key = "header") {
                Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    PersonAvatar(person, 144.dp, Modifier.sharedElement(SharedKeys.avatar(person.id)))
                    Spacer(Modifier.height(16.dp))
                    Text(person.name, style = MaterialTheme.typography.displaySmall, textAlign = TextAlign.Center)
                    Spacer(Modifier.height(8.dp))
                    Pill("${person.relation.emoji} ${person.relation.label}")
                    Spacer(Modifier.height(20.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { nav.go(Route.IdeaEdit(personId = person.id)) }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.Add, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Idée")
                        }
                        FilledTonalButton(onClick = { nav.go(Route.Inspire(person.id)) }, shapes = ButtonDefaults.shapes()) {
                            Icon(Icons.Rounded.AutoAwesome, null)
                            Spacer(Modifier.width(6.dp))
                            Text("Inspire-moi")
                        }
                    }
                }
            }

            ui.upcoming.firstOrNull()?.let { next ->
                item(key = "next") {
                    Surface(
                        modifier = Modifier.fillMaxWidth().padding(16.dp).cascade(0),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.primaryContainer,
                        onClick = { nav.go(Route.EventDetail(next.event.id)) },
                    ) {
                        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                            Column(Modifier.weight(1f)) {
                                Text("${next.emoji} ${next.title}", style = MaterialTheme.typography.titleLarge)
                                Text(
                                    Dates.weekday(next.date) + (next.yearsCount?.let { " · $it ans" } ?: ""),
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            }
                            LiveCountdown(next.date, style = MaterialTheme.typography.displaySmall)
                        }
                    }
                }
                if (ui.upcoming.size > 1) item(key = "others") {
                    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        ui.upcoming.drop(1).forEachIndexed { i, o ->
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.large)
                                    .background(MaterialTheme.colorScheme.surfaceContainerLow)
                                    .clickable { nav.go(Route.EventDetail(o.event.id)) }
                                    .padding(12.dp)
                                    .cascade(i + 1),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(o.emoji, fontSize = 22.sp)
                                Spacer(Modifier.width(12.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(o.title, style = MaterialTheme.typography.titleSmall)
                                    Text(Dates.full(o.date), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                                CountdownBadge(o.daysFrom(today), size = 44.dp)
                            }
                        }
                    }
                }
            }

            if (person.yearlyBudgetCents != null || ui.spentThisYear > 0) item(key = "budget") {
                BudgetStrip(person.yearlyBudgetCents, ui.spentThisYear, ui.plannedThisYear)
            }

            item(key = "ideasHeader") {
                SectionHeader("Idées pour ${person.name}") {
                    TextButton(onClick = { nav.go(Route.IdeaEdit(personId = person.id)) }) { Text("Ajouter") }
                }
            }
            if (ui.ideas.isEmpty()) item(key = "noIdeas") {
                Text(
                    "Pas encore d'idée. Laisse-toi inspirer ou note la prochaine envie entendue au détour d'une conversation.",
                    Modifier.padding(horizontal = 20.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            ui.ideas.forEachIndexed { i, item ->
                item(key = "idea-${item.idea.id}") {
                    IdeaCard(item, { nav.go(Route.IdeaDetail(item.idea.id)) }, Modifier.padding(horizontal = 16.dp, vertical = 5.dp).cascade(i + 2))
                }
            }

            item(key = "profile") { ProfileSection(person) }

            if (ui.given.isNotEmpty()) {
                item(key = "historyHeader") { SectionHeader("Déjà offert") }
                ui.given.forEach { g ->
                    item(key = "given-${g.idea.id}") {
                        HistoryRow(
                            emoji = g.idea.reactionEmoji ?: "🎁",
                            title = g.idea.title,
                            subtitle = g.idea.givenAt?.let { "Offert le ${Dates.fullMillis(it)}" } ?: "Offert",
                            onClick = { nav.go(Route.IdeaDetail(g.idea.id)) },
                        )
                    }
                }
            }
            if (ui.received.isNotEmpty()) {
                item(key = "receivedHeader") { SectionHeader("Reçu de ${person.name}") }
                ui.received.forEach { r ->
                    item(key = "received-${r.id}") {
                        HistoryRow(
                            emoji = if (r.thanked) "💌" else "🎀",
                            title = r.title,
                            subtitle = Dates.full(LocalDate.ofEpochDay(r.receivedOn)) + if (!r.thanked) " · à remercier" else "",
                            onClick = { nav.go(Route.ReceivedEdit(r.id)) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun BudgetStrip(budget: Long?, spent: Long, planned: Long) {
    Surface(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Column(Modifier.padding(16.dp)) {
            Text("Budget ${LocalDate.now().year}", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(verticalAlignment = Alignment.Bottom) {
                AnimatedMoney(spent, style = MaterialTheme.typography.headlineMedium)
                if (budget != null) {
                    Text(" / ", style = MaterialTheme.typography.titleMedium)
                    AnimatedMoney(budget, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (budget != null && budget > 0) {
                Spacer(Modifier.height(8.dp))
                LinearProgressIndicator(
                    progress = { (spent.toFloat() / budget).coerceIn(0f, 1f) },
                    modifier = Modifier.fillMaxWidth().height(10.dp),
                    color = if (spent > budget) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary,
                )
            }
            if (planned > 0) {
                Spacer(Modifier.height(6.dp))
                Text(
                    "+ ${com.karelisio.ruban.util.Money.format(planned)} d'idées en attente",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ProfileSection(person: Person) {
    val hasSizes = listOf(person.clothingSize, person.shoeSize, person.ringSize).any { it.isNotBlank() }
    val hasAnything = hasSizes || person.interests.isNotEmpty() || person.favoriteBrands.isNotBlank() ||
        person.favoriteColors.isNotBlank() || person.avoid.isNotBlank() || person.doNotGive.isNotBlank() || person.notes.isNotBlank()
    if (!hasAnything) return
    Column {
        SectionHeader("Profil")
        Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (person.interests.isNotEmpty()) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    person.interests.forEach { Pill("${Interests.emoji(it)} ${Interests.label(it)}") }
                }
            }
            if (hasSizes) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SizeTile("👕", "Vêtements", person.clothingSize, Modifier.weight(1f))
                    SizeTile("👟", "Chaussures", person.shoeSize, Modifier.weight(1f))
                    SizeTile("💍", "Bague", person.ringSize, Modifier.weight(1f))
                }
            }
            InfoLine("🏷️", "Marques", person.favoriteBrands)
            InfoLine("🎨", "Couleurs", person.favoriteColors)
            InfoLine("⚠️", "À éviter", person.avoid, warning = true)
            InfoLine("🚫", "À ne pas offrir", person.doNotGive, warning = true)
            InfoLine("📝", "Notes", person.notes)
        }
    }
}

@Composable
private fun SizeTile(emoji: String, label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(MaterialTheme.shapes.large)
            .background(MaterialTheme.colorScheme.surfaceContainerHigh)
            .padding(12.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text(emoji, fontSize = 20.sp)
        Text(value.ifBlank { "—" }, style = MaterialTheme.typography.titleLarge)
        Text(label, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun InfoLine(emoji: String, label: String, value: String, warning: Boolean = false) {
    if (value.isBlank()) return
    val c = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clip(MaterialTheme.shapes.large)
            .background(if (warning) c.errorContainer.copy(alpha = 0.6f) else c.surfaceContainerLow)
            .padding(14.dp),
    ) {
        Text(emoji, fontSize = 18.sp)
        Spacer(Modifier.width(12.dp))
        Column {
            Text(label, style = MaterialTheme.typography.labelMedium, color = if (warning) c.onErrorContainer else c.primary)
            Text(value, style = MaterialTheme.typography.bodyMedium, color = if (warning) c.onErrorContainer else c.onSurface)
        }
    }
}

@Composable
fun HistoryRow(emoji: String, title: String, subtitle: String, onClick: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(MaterialTheme.shapes.large)
            .clickable(onClick = onClick)
            .background(MaterialTheme.colorScheme.surfaceContainerLow)
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(40.dp).clip(CircleShape).background(MaterialTheme.colorScheme.tertiaryContainer), contentAlignment = Alignment.Center) {
            Text(emoji, fontSize = 20.sp)
        }
        Spacer(Modifier.width(12.dp))
        Column {
            Text(title, style = MaterialTheme.typography.titleSmall)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
