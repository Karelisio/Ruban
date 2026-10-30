package com.karelisio.ruban.ui.screens.budget

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.domain.BudgetMath
import com.karelisio.ruban.domain.YearBudget
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.AnimatedMoney
import com.karelisio.ruban.ui.components.BarChart
import com.karelisio.ruban.ui.components.BarEntry
import com.karelisio.ruban.ui.components.ColumnChart
import com.karelisio.ruban.ui.components.DonutChart
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.components.RollingText
import com.karelisio.ruban.ui.components.Segment
import com.karelisio.ruban.ui.components.cascade
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.theme.CounterStyle
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.Money
import com.karelisio.ruban.util.plural
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import java.time.LocalDate

data class RecapUi(val data: YearBudget? = null, val received: Int = 0, val thanked: Int = 0)

class YearRecapViewModel(c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val year = handle.toRoute<Route.YearRecap>().year
    val ui = combine(c.ideas.observeAll(), c.events.observeAll(), c.persons.observeAll(), c.received.observeAll()) { ideas, events, persons, received ->
        val mine = received.filter { LocalDate.ofEpochDay(it.receivedOn).year == year }
        RecapUi(BudgetMath.compute(year, ideas, events, persons), mine.size, mine.count { it.thanked })
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), RecapUi())
}

@Composable
fun YearRecapScreen() {
    val vm = rubanViewModel { YearRecapViewModel(this, it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val c = MaterialTheme.colorScheme
    val palette = listOf(c.primary, c.tertiary, c.secondary, c.error, c.primaryContainer, c.tertiaryContainer)

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Récap ${vm.year}") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = c.surface),
            )
        },
    ) { padding ->
        val data = ui.data ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 32.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item(key = "hero") {
                Surface(Modifier.fillMaxWidth().cascade(0), shape = MaterialTheme.shapes.extraLarge, color = c.primaryContainer) {
                    Column(Modifier.padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("Ton année en cadeaux", style = MaterialTheme.typography.titleMedium, color = c.onPrimaryContainer)
                        Spacer(Modifier.height(8.dp))
                        AnimatedMoney(data.spent, style = CounterStyle.copy(fontSize = 56.sp), color = c.onPrimaryContainer)
                        Spacer(Modifier.height(4.dp))
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            RollingText(data.giftsGiven.toString(), style = MaterialTheme.typography.headlineSmall, color = c.onPrimaryContainer)
                            Text(
                                " " + if (data.giftsGiven > 1) "cadeaux offerts" else "cadeau offert",
                                style = MaterialTheme.typography.titleMedium,
                                color = c.onPrimaryContainer,
                            )
                        }
                    }
                }
            }

            item(key = "months") {
                RecapCard("Mois par mois", Modifier.cascade(1)) {
                    val values = data.byMonth.map { it.toFloat() }
                    val max = values.indices.maxByOrNull { values[it] } ?: -1
                    ColumnChart(values, Modifier.fillMaxWidth().height(140.dp), color = c.primary.copy(alpha = 0.32f), highlight = if (values.any { it > 0 }) max else -1, highlightColor = c.primary)
                    Row(Modifier.fillMaxWidth()) {
                        (1..12).forEach { m ->
                            Text(Dates.monthShort(m).take(1).uppercase(), Modifier.weight(1f), textAlign = TextAlign.Center, style = MaterialTheme.typography.labelSmall, color = c.onSurfaceVariant)
                        }
                    }
                    if (max >= 0 && values[max] > 0) {
                        Text(
                            "Le mois le plus généreux : ${Dates.monthName(max + 1)} (${Money.format(data.byMonth[max])})",
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                }
            }

            if (data.byPerson.isNotEmpty()) item(key = "people") {
                val top = data.byPerson.filter { it.spent > 0 }.take(6)
                RecapCard("Par proche", Modifier.cascade(2)) {
                    if (top.isEmpty()) {
                        Text("Aucune dépense encore cette année.", color = c.onSurfaceVariant)
                    } else {
                        Row {
                            Column(Modifier.width(132.dp), verticalArrangement = Arrangement.SpaceEvenly) {
                                top.forEach { p ->
                                    Row(Modifier.height(40.dp), verticalAlignment = Alignment.CenterVertically) {
                                        PersonAvatar(p.person, 28.dp)
                                        Spacer(Modifier.width(8.dp))
                                        Text(p.person.name, style = MaterialTheme.typography.labelLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                    }
                                }
                            }
                            BarChart(
                                top.mapIndexed { i, p -> BarEntry(p.person.name, p.spent.toFloat(), palette[i % palette.size], p.budget?.toFloat()) },
                                Modifier.weight(1f).height((40 * top.size).dp),
                                trackColor = c.surfaceContainerHighest,
                            )
                        }
                        Text(
                            top.joinToString(" · ") { "${it.person.name} ${Money.format(it.spent)}" },
                            style = MaterialTheme.typography.bodySmall,
                            color = c.onSurfaceVariant,
                        )
                    }
                }
            }

            if (data.byKind.isNotEmpty()) item(key = "kinds") {
                RecapCard("Par occasion", Modifier.cascade(3)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        DonutChart(
                            data.byKind.mapIndexed { i, k -> Segment(k.spent.toFloat(), palette[i % palette.size]) },
                            Modifier.size(140.dp),
                            trackColor = c.surfaceContainerHighest,
                        )
                        Spacer(Modifier.width(16.dp))
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            data.byKind.forEachIndexed { i, k ->
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Box(Modifier.size(12.dp).clip(CircleShape).background(palette[i % palette.size]))
                                    Spacer(Modifier.width(8.dp))
                                    Text("${k.emoji} ${k.label}", style = MaterialTheme.typography.labelLarge, modifier = Modifier.weight(1f, fill = false))
                                    Spacer(Modifier.width(6.dp))
                                    Text(Money.format(k.spent), style = MaterialTheme.typography.labelMedium, color = c.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            data.topReaction?.let { emoji ->
                item(key = "reaction") {
                    RecapCard("Réaction la plus fréquente", Modifier.cascade(4)) {
                        BouncyEmoji(emoji)
                    }
                }
            }

            if (ui.received > 0) item(key = "received") {
                RecapCard("Et toi, tu as reçu", Modifier.cascade(5)) {
                    Text(ui.received.plural("cadeau"), style = MaterialTheme.typography.headlineMedium)
                    Text(
                        if (ui.thanked == ui.received) "Tout le monde a été remercié 💌"
                        else "${(ui.received - ui.thanked).plural("remerciement")} encore à faire",
                        style = MaterialTheme.typography.bodyLarge,
                        color = c.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun RecapCard(title: String, modifier: Modifier = Modifier, content: @Composable ColumnScope.() -> Unit) {
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}

@Composable
private fun BouncyEmoji(emoji: String) {
    val reduce = LocalReduceMotion.current
    val scale = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { scale.animateTo(1f, spring(0.3f, Spring.StiffnessLow)) }
    Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
        Text(emoji, fontSize = 72.sp, modifier = Modifier.graphicsLayer { scaleX = scale.value; scaleY = scale.value }, color = Color.Unspecified)
    }
}
