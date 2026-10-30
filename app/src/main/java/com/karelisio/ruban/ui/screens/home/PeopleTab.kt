package com.karelisio.ruban.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.PersonAdd
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ripple
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.domain.Occurrence
import com.karelisio.ruban.domain.TextMatch
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.CountdownBadge
import com.karelisio.ruban.ui.components.EmptyState
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.components.SearchField
import com.karelisio.ruban.ui.components.SharedKeys
import com.karelisio.ruban.ui.components.cascade
import com.karelisio.ruban.ui.components.pressBounce
import com.karelisio.ruban.ui.components.sharedBounds
import com.karelisio.ruban.ui.components.sharedElement
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.screens.people.ContactsImportSheet
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.plural
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class PersonRow(val person: Person, val next: Occurrence?, val ideas: Int, val ready: Int)

data class PeopleUi(val rows: List<PersonRow> = emptyList(), val query: String = "", val total: Int = 0, val loaded: Boolean = false)

class PeopleViewModel(c: AppContainer) : ViewModel() {
    val query = MutableStateFlow("")

    val ui = combine(c.persons.observeAll(), c.events.observeUpcoming(), c.ideas.observeAll(), query) { persons, upcoming, ideas, q ->
        val nextByPerson = upcoming.filter { it.person != null }.groupBy { it.person!!.id }.mapValues { it.value.minBy { o -> o.date } }
        val rows = persons
            .filter { TextMatch.matches(q, it.name, it.relation.label, it.notes, *it.interests.toTypedArray()) }
            .map { p ->
                val mine = ideas.filter { i -> i.people.any { it.id == p.id } }
                PersonRow(
                    person = p,
                    next = nextByPerson[p.id],
                    ideas = mine.count { it.idea.status == GiftStatus.IDEA },
                    ready = mine.count { it.idea.status == GiftStatus.BOUGHT || it.idea.status == GiftStatus.WRAPPED },
                )
            }
            .sortedWith(compareBy<PersonRow, LocalDate?>(nullsLast()) { it.next?.date }.thenBy { it.person.name.lowercase() })
        PeopleUi(rows, q, persons.size, true)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), PeopleUi())
}

@Composable
internal fun PeopleTab(padding: PaddingValues) {
    val vm = rubanViewModel { PeopleViewModel(this) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    var importing by rememberSaveable { mutableStateOf(false) }

    if (importing) ContactsImportSheet(onDismiss = { importing = false })

    if (ui.loaded && ui.total == 0) {
        Column(Modifier.fillMaxSize().padding(padding), horizontalAlignment = Alignment.CenterHorizontally) {
            EmptyState(
                "🫶", "Tes proches",
                "Ajoute les personnes à qui tu aimes offrir : leurs goûts, leurs tailles, leurs dates importantes.",
                actionLabel = "Ajouter un proche", actionIcon = Icons.Rounded.PersonAdd,
                onAction = { nav.go(Route.PersonEdit()) },
            )
            FilledTonalButton(onClick = { importing = true }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.Contacts, null)
                Spacer(Modifier.width(8.dp))
                Text("Importer les anniversaires")
            }
        }
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search") {
            Row(Modifier.padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                SearchField(ui.query, { vm.query.value = it }, "Rechercher un proche", Modifier.weight(1f))
                Spacer(Modifier.width(8.dp))
                androidx.compose.material3.FilledTonalIconButton(onClick = { importing = true }) {
                    Icon(Icons.Rounded.Contacts, "Importer depuis les contacts")
                }
            }
        }
        items(ui.rows.size, key = { ui.rows[it].person.id }) { index ->
            PersonCard(ui.rows[index], Modifier.padding(horizontal = 16.dp).animateItem().cascade(index)) {
                nav.go(Route.PersonDetail(ui.rows[index].person.id))
            }
        }
    }
}

@Composable
private fun PersonCard(row: PersonRow, modifier: Modifier, onClick: () -> Unit) {
    val p = row.person
    val interaction = remember { MutableInteractionSource() }
    val today = remember { LocalDate.now() }
    Surface(
        modifier.fillMaxWidth().pressBounce(interaction, 0.97f).sharedBounds(SharedKeys.person(p.id)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.clickable(interaction, ripple(), onClick = onClick).padding(14.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            PersonAvatar(p, 58.dp, Modifier.sharedElement(SharedKeys.avatar(p.id)))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(p.name, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    "${p.relation.emoji} ${p.relation.label}" + when {
                        row.ready > 0 -> " · ${row.ready.plural("cadeau prêt", "cadeaux prêts")}"
                        row.ideas > 0 -> " · ${row.ideas.plural("idée")}"
                        else -> ""
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.next?.let { o ->
                    Text(
                        "${o.emoji} ${o.event.kind.label} · ${Dates.dayMonth(o.date)}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
            row.next?.let { CountdownBadge(it.daysFrom(today)) }
        }
    }
}
