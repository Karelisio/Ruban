package com.karelisio.ruban.ui.screens.home

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.model.IdeaWithPeople
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.PricePoint
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.TextMatch
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.EmptyState
import com.karelisio.ruban.ui.components.IdeaCard
import com.karelisio.ruban.ui.components.SearchField
import com.karelisio.ruban.ui.components.cascade
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

enum class IdeaFilter(val label: String) {
    ACTIVE("En cours"),
    UNASSIGNED("À attribuer"),
    FOR_ME("Pour moi"),
    BOUGHT("Achetées"),
    WRAPPED("Emballées"),
    GIVEN("Offertes"),
    ;

    fun accepts(item: IdeaWithPeople): Boolean = when (this) {
        ACTIVE -> item.idea.status != GiftStatus.GIVEN && !item.idea.forMe
        UNASSIGNED -> item.unassigned && item.idea.status != GiftStatus.GIVEN
        FOR_ME -> item.idea.forMe
        BOUGHT -> item.idea.status == GiftStatus.BOUGHT
        WRAPPED -> item.idea.status == GiftStatus.WRAPPED
        GIVEN -> item.idea.status == GiftStatus.GIVEN
    }
}

data class IdeaRow(val item: IdeaWithPeople, val occasion: String?, val prices: List<PricePoint>)

data class IdeasUi(
    val rows: List<IdeaRow> = emptyList(),
    val counts: Map<IdeaFilter, Int> = emptyMap(),
    val filter: IdeaFilter = IdeaFilter.ACTIVE,
    val query: String = "",
    val total: Int = 0,
    val loaded: Boolean = false,
)

fun occasionLabel(idea: com.karelisio.ruban.data.model.Idea, events: Map<Long, Event>, persons: Map<Long, Person>): String? {
    val event = idea.eventId?.let(events::get) ?: return idea.occasionLabel?.takeIf { it.isNotBlank() }
    val person = event.personId?.let(persons::get)
    val year = idea.occasionYear
    val date = year?.let { EventDates.dateIn(event, person, it) }
    val title = EventDates.titleOf(event, person)
    return "${event.emoji ?: event.kind.emoji} $title" + (date?.let { " · ${Dates.dayMonth(it)}" } ?: "")
}

class IdeasViewModel(c: AppContainer) : ViewModel() {
    val filter = MutableStateFlow(IdeaFilter.ACTIVE)
    val query = MutableStateFlow("")

    private val prices = c.db.prices().observeAll()

    val ui = combine(
        c.ideas.observeAll(),
        c.events.observeAll(),
        c.persons.observeAll(),
        prices,
        combine(filter, query) { f, q -> f to q },
    ) { ideas, events, persons, points, (f, q) ->
        val eventsById = events.associateBy { it.id }
        val personsById = persons.associateBy { it.id }
        val pricesByIdea = points.groupBy { it.ideaId }
        val searched = ideas.filter { TextMatch.matches(q, it.idea.title, it.idea.notes, *it.people.map(Person::name).toTypedArray()) }
        IdeasUi(
            rows = searched.filter(f::accepts)
                .sortedWith(compareBy<IdeaWithPeople> { it.idea.status == GiftStatus.GIVEN }.thenBy { it.idea.priority }.thenByDescending { it.idea.updatedAt })
                .map { IdeaRow(it, occasionLabel(it.idea, eventsById, personsById), pricesByIdea[it.idea.id].orEmpty()) },
            counts = IdeaFilter.entries.associateWith { fl -> searched.count(fl::accepts) },
            filter = f,
            query = q,
            total = ideas.size,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdeasUi())
}

@Composable
internal fun IdeasTab(padding: PaddingValues) {
    val vm = rubanViewModel { IdeasViewModel(this) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val haptic = LocalHapticFeedback.current

    if (ui.loaded && ui.total == 0) {
        EmptyState(
            "💡", "Ta première idée",
            "Note au fil de l'eau ce qui ferait plaisir à tes proches. Partage un lien depuis n'importe quelle appli, Ruban s'occupe du reste.",
            Modifier.fillMaxSize().padding(padding),
            actionLabel = "Noter une idée", actionIcon = Icons.Rounded.Add,
            onAction = { nav.go(Route.IdeaEdit()) },
        )
        return
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item(key = "search") {
            SearchField(ui.query, { vm.query.value = it }, "Rechercher une idée", Modifier.padding(horizontal = 16.dp))
        }
        item(key = "filters") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(IdeaFilter.entries, key = { it.name }) { f ->
                    val count = ui.counts[f] ?: 0
                    FilterChip(
                        selected = ui.filter == f,
                        onClick = {
                            haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                            vm.filter.value = f
                        },
                        label = { Text(if (count > 0) "${f.label} · $count" else f.label) },
                        colors = FilterChipDefaults.filterChipColors(),
                    )
                }
            }
        }
        if (ui.rows.isEmpty()) {
            item(key = "empty") {
                EmptyState("🔎", "Rien par ici", "Aucune idée ne correspond à ce filtre.")
            }
        }
        items(ui.rows.size, key = { ui.rows[it].item.idea.id }) { index ->
            val row = ui.rows[index]
            IdeaCard(
                row.item,
                onClick = { nav.go(Route.IdeaDetail(row.item.idea.id)) },
                occasion = row.occasion,
                prices = row.prices,
                modifier = Modifier
                    .padding(horizontal = 16.dp)
                    .animateItem()
                    .cascade(index),
            )
        }
        item { Spacer(Modifier.fillMaxWidth().height(8.dp)) }
    }
}
