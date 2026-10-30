package com.karelisio.ruban.ui.screens.home

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.ArrowForward
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.ripple
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.repo.todayFlow
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.Occurrence
import com.karelisio.ruban.domain.Prep
import com.karelisio.ruban.domain.PrepState
import com.karelisio.ruban.domain.Preparation
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.CountdownBadge
import com.karelisio.ruban.ui.components.EmptyState
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.components.ShapeBadge
import com.karelisio.ruban.ui.components.SharedKeys
import com.karelisio.ruban.ui.components.cascade
import com.karelisio.ruban.ui.components.pressBounce
import com.karelisio.ruban.ui.components.sharedBounds
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.theme.PolygonShape
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.FR
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle

data class AgendaItem(val occurrence: Occurrence, val prep: PrepState, val planned: Long, val spent: Long)

data class EventsUi(
    val today: LocalDate = LocalDate.now(),
    val upcoming: List<AgendaItem> = emptyList(),
    val byDate: Map<LocalDate, List<AgendaItem>> = emptyMap(),
    val loaded: Boolean = false,
)

fun agendaItem(o: Occurrence, ideas: List<com.karelisio.ruban.data.model.IdeaWithPeople>): AgendaItem {
    val prep = Preparation.of(o, ideas)
    val linked = ideas.filter { it.idea.eventId == o.event.id && (it.idea.occasionYear ?: o.date.year) == o.date.year }
    return AgendaItem(
        occurrence = o,
        prep = prep,
        planned = linked.sumOf { it.idea.priceCents ?: 0 },
        spent = linked.filter { it.idea.status >= GiftStatus.BOUGHT }.sumOf { it.idea.priceCents ?: 0 },
    )
}

class EventsViewModel(c: AppContainer) : ViewModel() {
    val ui = combine(c.events.observeAll(), c.persons.observeAll(), c.ideas.observeAll(), todayFlow()) { events, persons, ideas, today ->
        val byId = persons.associateBy { it.id }
        val from = today.withDayOfMonth(1).minusMonths(1)
        val all = EventDates.occurrences(events, byId, from, today.plusMonths(18)).map { agendaItem(it, ideas) }
        EventsUi(
            today = today,
            upcoming = all.filter { !it.occurrence.date.isBefore(today) && it.occurrence.date.isBefore(today.plusDays(366)) },
            byDate = all.groupBy { it.occurrence.date },
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), EventsUi())
}

@Composable
internal fun EventsTab(padding: PaddingValues) {
    val vm = rubanViewModel { EventsViewModel(this) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    var calendar by rememberSaveable { mutableStateOf(false) }
    val haptic = LocalHapticFeedback.current

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
            ToggleButton(
                checked = !calendar,
                onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); calendar = false },
                modifier = Modifier.weight(1f),
                shapes = ButtonGroupDefaults.connectedLeadingButtonShapes(),
            ) {
                Icon(Icons.Rounded.ViewAgenda, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("À venir")
            }
            ToggleButton(
                checked = calendar,
                onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); calendar = true },
                modifier = Modifier.weight(1f),
                shapes = ButtonGroupDefaults.connectedTrailingButtonShapes(),
            ) {
                Icon(Icons.Rounded.CalendarMonth, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Calendrier")
            }
        }
        AnimatedContent(calendar, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "agendaMode") { cal ->
            val bottom = padding.calculateBottomPadding() + 96.dp
            if (cal) CalendarView(ui, bottom) else UpcomingList(ui, bottom)
        }
    }
}

@Composable
private fun UpcomingList(ui: EventsUi, bottom: androidx.compose.ui.unit.Dp) {
    if (ui.loaded && ui.upcoming.isEmpty()) {
        EmptyState("📅", "Rien à l'horizon", "Ajoute l'anniversaire d'un proche ou un événement : Ruban te préviendra à temps.")
        return
    }
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom, top = 8.dp)) {
        agendaItems(ui.upcoming, ui.today, groupByMonth = true)
    }
}

fun LazyListScope.agendaItems(items: List<AgendaItem>, today: LocalDate, groupByMonth: Boolean) {
    var index = 0
    val groups = if (groupByMonth) items.groupBy { YearMonth.from(it.occurrence.date) } else mapOf(null to items)
    groups.forEach { (month, list) ->
        if (month != null) item(key = "month-$month") {
            Text(
                "${Dates.monthName(month.monthValue).replaceFirstChar { it.uppercase() }} ${month.year}",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
                modifier = Modifier.padding(start = 20.dp, top = 16.dp, bottom = 8.dp).animateItem(),
            )
        }
        list.forEach { item ->
            val i = index++
            item(key = item.occurrence.key) {
                AgendaCard(item, today, Modifier.padding(horizontal = 16.dp, vertical = 5.dp).animateItem().cascade(i))
            }
        }
    }
}

@Composable
fun AgendaCard(item: AgendaItem, today: LocalDate, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    val o = item.occurrence
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier.fillMaxWidth().pressBounce(interaction, 0.97f).sharedBounds(SharedKeys.event(o.event.id)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(
            Modifier.clickable(interaction, ripple()) { nav.go(Route.EventDetail(o.event.id)) }.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            val person = o.person
            if (person != null) {
                Box {
                    PersonAvatar(person, 52.dp)
                    Text(o.emoji, fontSize = 18.sp, modifier = Modifier.align(Alignment.BottomEnd))
                }
            } else {
                ShapeBadge(size = 52.dp, polygonIndex = o.event.id.toInt() + 3, container = MaterialTheme.colorScheme.secondaryContainer) {
                    Text(o.emoji, fontSize = 24.sp)
                }
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(o.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    Dates.weekday(o.date) + (o.yearsCount?.let { " · ${it} ans" } ?: ""),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.height(4.dp))
                PrepChip(item.prep.prep)
            }
            CountdownBadge(o.daysFrom(today).coerceAtLeast(0))
        }
    }
}

@Composable
fun PrepChip(prep: Prep, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val (bg, fg) = when (prep) {
        Prep.READY -> c.tertiaryContainer to c.onTertiaryContainer
        Prep.IDEA -> c.secondaryContainer to c.onSecondaryContainer
        Prep.NOTHING -> c.surfaceContainerHighest to c.onSurfaceVariant
        Prep.DONE -> c.primaryContainer to c.onPrimaryContainer
    }
    Text(
        "${prep.emoji} ${prep.label}",
        modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 3.dp),
        style = MaterialTheme.typography.labelMedium,
        color = fg,
    )
}

@Composable
private fun CalendarView(ui: EventsUi, bottom: androidx.compose.ui.unit.Dp) {
    val start = remember { YearMonth.from(ui.today) }
    val pager = rememberPagerState(initialPage = 1) { 20 }
    val scope = rememberCoroutineScope()
    var selected by rememberSaveable { mutableStateOf<String?>(null) }
    val selectedDate = selected?.let(LocalDate::parse)

    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(bottom = bottom)) {
        item(key = "calendar") {
            val month = start.plusMonths((pager.currentPage - 1).toLong())
            Column {
                Row(Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage - 1).coerceAtLeast(0)) } }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Mois précédent")
                    }
                    AnimatedContent(month, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "month", modifier = Modifier.weight(1f)) { m ->
                        Text(
                            "${Dates.monthName(m.monthValue).replaceFirstChar { it.uppercase() }} ${m.year}",
                            style = MaterialTheme.typography.headlineSmall,
                            modifier = Modifier.fillMaxWidth(),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                    }
                    IconButton(onClick = { scope.launch { pager.animateScrollToPage((pager.currentPage + 1).coerceAtMost(19)) } }) {
                        Icon(Icons.AutoMirrored.Rounded.ArrowForward, "Mois suivant")
                    }
                }
                Row(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
                    DayOfWeek.entries.forEach { d ->
                        Text(
                            d.getDisplayName(TextStyle.NARROW, FR),
                            Modifier.weight(1f),
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                HorizontalPager(pager, Modifier.fillMaxWidth()) { page ->
                    MonthGrid(start.plusMonths((page - 1).toLong()), ui, selectedDate) { selected = it.toString() }
                }
            }
        }
        val month = start.plusMonths((pager.currentPage - 1).toLong())
        val items = if (selectedDate != null && YearMonth.from(selectedDate) == month) ui.byDate[selectedDate].orEmpty()
        else ui.byDate.filterKeys { YearMonth.from(it) == month }.toSortedMap().values.flatten()
        if (items.isEmpty()) item(key = "none") {
            Text(
                if (selectedDate != null) "Rien le ${Dates.dayMonth(selectedDate)}." else "Rien ce mois-ci.",
                Modifier.padding(24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        agendaItems(items, ui.today, groupByMonth = false)
    }
}

@Composable
private fun MonthGrid(month: YearMonth, ui: EventsUi, selected: LocalDate?, onSelect: (LocalDate) -> Unit) {
    val first = month.atDay(1)
    val lead = first.dayOfWeek.value - 1
    val days = month.lengthOfMonth()
    val cells = ((lead + days + 6) / 7) * 7
    val haptic = LocalHapticFeedback.current
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp)) {
        for (week in 0 until cells / 7) {
            Row(Modifier.fillMaxWidth()) {
                for (dow in 0 until 7) {
                    val dayNum = week * 7 + dow - lead + 1
                    Box(Modifier.weight(1f).aspectRatio(1f).padding(2.dp), contentAlignment = Alignment.Center) {
                        if (dayNum in 1..days) {
                            val date = month.atDay(dayNum)
                            val events = ui.byDate[date].orEmpty()
                            val isToday = date == ui.today
                            val isSelected = date == selected
                            val c = MaterialTheme.colorScheme
                            val bg by animateColorAsState(
                                when {
                                    isSelected -> c.primary
                                    isToday -> c.primaryContainer
                                    events.isNotEmpty() -> c.surfaceContainerHigh
                                    else -> androidx.compose.ui.graphics.Color.Transparent
                                },
                                label = "dayBg",
                            )
                            val shape = if (isToday || isSelected) PolygonShape(MaterialShapes.Cookie9Sided) else CircleShape
                            Column(
                                Modifier
                                    .fillMaxSize()
                                    .clip(shape)
                                    .background(bg)
                                    .clickable {
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                        onSelect(date)
                                    },
                                horizontalAlignment = Alignment.CenterHorizontally,
                                verticalArrangement = Arrangement.Center,
                            ) {
                                Text(
                                    "$dayNum",
                                    style = MaterialTheme.typography.labelLarge,
                                    color = when {
                                        isSelected -> c.onPrimary
                                        isToday -> c.onPrimaryContainer
                                        else -> c.onSurface
                                    },
                                )
                                if (events.isNotEmpty()) Text(events.first().occurrence.emoji, fontSize = 11.sp, lineHeight = 12.sp)
                            }
                        }
                    }
                }
            }
        }
    }
}
