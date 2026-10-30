package com.karelisio.ruban.ui.screens.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Insights
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.domain.BudgetMath
import com.karelisio.ruban.domain.YearBudget
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.AnimatedMoney
import com.karelisio.ruban.ui.components.BudgetBar
import com.karelisio.ruban.ui.components.DonutChart
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.components.SectionHeader
import com.karelisio.ruban.ui.components.Segment
import com.karelisio.ruban.ui.components.cascade
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.theme.CounterStyle
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.Money
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class BudgetUi(val years: List<Int> = emptyList(), val data: YearBudget? = null)

class BudgetViewModel(c: AppContainer) : ViewModel() {
    val year = MutableStateFlow(LocalDate.now().year)
    val ui = combine(c.ideas.observeAll(), c.events.observeAll(), c.persons.observeAll(), year) { ideas, events, persons, y ->
        BudgetUi(BudgetMath.years(ideas, LocalDate.now()), BudgetMath.compute(y, ideas, events, persons))
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), BudgetUi())
}

@Composable
internal fun BudgetTab(padding: PaddingValues) {
    val vm = rubanViewModel { BudgetViewModel(this) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val selectedYear by vm.year.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val data = ui.data ?: return
    val c = MaterialTheme.colorScheme

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp),
    ) {
        if (ui.years.size > 1) item(key = "years") {
            LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                items(ui.years) { y -> FilterChip(y == selectedYear, { vm.year.value = y }, { Text("$y") }) }
            }
        }
        item(key = "overview") {
            Surface(
                Modifier.fillMaxWidth().padding(16.dp).cascade(0),
                shape = MaterialTheme.shapes.extraLarge,
                color = c.primaryContainer,
            ) {
                Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Dépensé en ${data.year}", style = MaterialTheme.typography.titleSmall, color = c.onPrimaryContainer)
                        AnimatedMoney(data.spent, style = CounterStyle, color = c.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Text(
                            "Prévu : ${Money.format(data.planned)}" + if (data.budget > 0) "\nBudgets fixés : ${Money.format(data.budget)}" else "",
                            style = MaterialTheme.typography.bodyMedium,
                            color = c.onPrimaryContainer,
                        )
                    }
                    Box(Modifier.size(112.dp), contentAlignment = Alignment.Center) {
                        val reference = maxOf(data.budget, data.planned, data.spent)
                        DonutChart(
                            listOf(
                                Segment(data.spent.toFloat(), c.primary),
                                Segment((data.planned - data.spent).coerceAtLeast(0).toFloat(), c.tertiary),
                                Segment((reference - maxOf(data.planned, data.spent)).coerceAtLeast(0).toFloat(), c.surface.copy(alpha = 0.5f)),
                            ),
                            Modifier.size(112.dp),
                            trackColor = c.surface.copy(alpha = 0.35f),
                            strokeWidth = 14.dp,
                        )
                        val pct = if (reference > 0) (data.spent * 100 / reference) else 0
                        Text("$pct %", style = MaterialTheme.typography.titleLarge, color = c.onPrimaryContainer)
                    }
                }
            }
        }
        item(key = "recap") {
            FilledTonalButton(
                onClick = { nav.go(Route.YearRecap(data.year)) },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.padding(horizontal = 16.dp).fillMaxWidth().cascade(1),
            ) {
                Icon(Icons.Rounded.Insights, null)
                Spacer(Modifier.width(8.dp))
                Text("Récap ${data.year}")
            }
        }

        if (data.byOccasion.isNotEmpty()) {
            item(key = "occHeader") { SectionHeader("Par occasion") }
            data.byOccasion.forEachIndexed { i, o ->
                item(key = "occ-${o.occurrence.key}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { nav.go(Route.EventDetail(o.occurrence.event.id)) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .cascade(i + 2),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(o.occurrence.emoji, fontSize = 26.sp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Row {
                                Text(o.occurrence.title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(Dates.dayMonth(o.occurrence.date), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                            }
                            Spacer(Modifier.height(6.dp))
                            BudgetBar(o.spent, o.planned, o.budget)
                        }
                    }
                }
            }
        }

        if (data.byPerson.isNotEmpty()) {
            item(key = "personHeader") { SectionHeader("Par proche") }
            data.byPerson.forEachIndexed { i, p ->
                item(key = "person-${p.person.id}") {
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .clip(MaterialTheme.shapes.large)
                            .clickable { nav.go(Route.PersonDetail(p.person.id)) }
                            .padding(horizontal = 20.dp, vertical = 10.dp)
                            .cascade(i + 4),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PersonAvatar(p.person, 40.dp)
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f)) {
                            Text(p.person.name, style = MaterialTheme.typography.titleSmall)
                            Spacer(Modifier.height(6.dp))
                            BudgetBar(p.spent, p.planned, p.budget)
                        }
                    }
                }
            }
        }

        if (data.planned == 0L && data.byOccasion.isEmpty()) item(key = "empty") {
            com.karelisio.ruban.ui.components.EmptyState(
                "🪙", "Rien de prévu en ${data.year}",
                "Ajoute un prix à tes idées et un budget à tes événements : Ruban fera les comptes.",
            )
        }
    }
}
