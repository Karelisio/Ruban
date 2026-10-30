package com.karelisio.ruban.ui.screens.inspire

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.VectorConverter
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.Undo
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SnackbarResult
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.BudgetTier
import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.Idea
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.Priority
import com.karelisio.ruban.data.model.SuggestionType
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.Inspiration
import com.karelisio.ruban.domain.InspirationFilter
import com.karelisio.ruban.domain.Interests
import com.karelisio.ruban.domain.Scored
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.EmptyState
import com.karelisio.ruban.ui.components.Pill
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.ui.theme.PolygonShape
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import java.time.LocalDate
import kotlin.math.abs

data class InspireUi(
    val person: Person? = null,
    val event: Event? = null,
    val deck: List<Scored> = emptyList(),
    val filter: InspirationFilter = InspirationFilter(),
    val loaded: Boolean = false,
)

private data class LastAction(val card: Scored, val kept: Boolean, val ideaId: Long?)

class InspireViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val route = handle.toRoute<Route.Inspire>()
    val ui = MutableStateFlow(InspireUi())
    private var last: LastAction? = null

    init { reload() }

    fun reload(filter: InspirationFilter = ui.value.filter) = viewModelScope.launch {
        val person = c.persons.get(route.personId) ?: return@launch
        val event = route.eventId.takeIf { it != 0L }?.let { c.events.get(it) }
        val seen = c.suggestions.verdicts(person.id).map { it.suggestionId }.toSet()
        val deck = Inspiration.rank(c.suggestions.all(), person, event?.kind, filter, seen)
        ui.value = InspireUi(person, event, deck, filter, true)
    }

    fun setFilter(filter: InspirationFilter) = reload(filter)

    /** Returns the created idea title when kept. */
    fun decide(card: Scored, keep: Boolean) {
        val person = ui.value.person ?: return
        ui.value = ui.value.copy(deck = ui.value.deck.filterNot { it.suggestion.id == card.suggestion.id })
        viewModelScope.launch {
            c.suggestions.setVerdict(person.id, card.suggestion.id, keep)
            var ideaId: Long? = null
            if (keep) {
                val event = ui.value.event
                val date = event?.let { EventDates.next(it, person, LocalDate.now()) }
                ideaId = c.ideas.save(
                    Idea(
                        title = card.suggestion.title,
                        notes = card.suggestion.description,
                        priority = Priority.NICE,
                        suggestionId = card.suggestion.id,
                        eventId = event?.id,
                        occasionYear = date?.year,
                    ),
                    listOf(person.id),
                )
            }
            last = LastAction(card, keep, ideaId)
        }
    }

    fun undo() {
        val action = last ?: return
        val person = ui.value.person ?: return
        last = null
        ui.value = ui.value.copy(deck = listOf(action.card) + ui.value.deck)
        viewModelScope.launch {
            c.suggestions.undo(person.id, action.card.suggestion.id)
            action.ideaId?.let { id -> c.ideas.get(id)?.let { c.ideas.delete(it) } }
        }
    }

    val canUndo: Boolean get() = last != null

    fun resetSkipped() = viewModelScope.launch {
        ui.value.person?.let { c.suggestions.resetSkipped(it.id) }
        reload()
    }
}

@Stable
class SwipeState {
    val offset = Animatable(Offset.Zero, Offset.VectorConverter)
    var width = 1f

    val progress: Float get() = (offset.value.x / (width * THRESHOLD)).coerceIn(-1f, 1f)

    suspend fun fling(right: Boolean) {
        offset.animateTo(Offset(if (right) width * 1.6f else -width * 1.6f, offset.value.y + 80f), tween(320))
    }

    suspend fun reset() = offset.animateTo(Offset.Zero, spring(0.6f, Spring.StiffnessMedium))

    companion object {
        const val THRESHOLD = 0.32f
    }
}

@Composable
fun InspireScreen() {
    val vm = rubanViewModel { InspireViewModel(this, it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val person = ui.person

    fun decide(card: Scored, keep: Boolean) {
        haptic.performHapticFeedback(if (keep) HapticFeedbackType.Confirm else HapticFeedbackType.Reject)
        vm.decide(card, keep)
        if (keep) scope.launch {
            snackbar.currentSnackbarData?.dismiss()
            val result = snackbar.showSnackbar("Ajoutée aux idées de ${person?.name}", actionLabel = "Annuler", withDismissAction = true)
            if (result == SnackbarResult.ActionPerformed) vm.undo()
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text("Inspire-moi")
                        person?.let {
                            Text(
                                "Pour ${it.name}" + (ui.event?.let { e -> " · ${EventDates.titleOf(e, it)}" } ?: ""),
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding)) {
            Filters(ui.filter, vm::setFilter)
            if (person != null && person.interests.isEmpty()) {
                Surface(
                    onClick = { nav.go(Route.PersonEdit(person.id)) },
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                    shape = MaterialTheme.shapes.large,
                    color = MaterialTheme.colorScheme.secondaryContainer,
                ) {
                    Text(
                        "💡 Ajoute les centres d'intérêt de ${person.name} pour des suggestions sur mesure.",
                        Modifier.padding(12.dp),
                        style = MaterialTheme.typography.bodyMedium,
                    )
                }
            }
            Box(Modifier.weight(1f).fillMaxWidth().padding(horizontal = 20.dp, vertical = 12.dp), contentAlignment = Alignment.Center) {
                if (ui.loaded && ui.deck.isEmpty()) {
                    EmptyState(
                        "🎐", "Tu as tout vu !",
                        "Change les filtres ou revois les idées passées.",
                        actionLabel = "Revoir les idées passées", onAction = vm::resetSkipped,
                    )
                } else {
                    CardStack(ui.deck, onDecision = ::decide, haptic = haptic)
                }
            }
            val top = ui.deck.firstOrNull()
            Row(
                Modifier.fillMaxWidth().padding(bottom = 24.dp),
                horizontalArrangement = Arrangement.spacedBy(20.dp, Alignment.CenterHorizontally),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalIconButton(
                    onClick = { top?.let { decide(it, false) } },
                    enabled = top != null,
                    modifier = Modifier.size(68.dp),
                    shape = PolygonShape(MaterialShapes.Cookie9Sided),
                ) { Icon(Icons.Rounded.Close, "Passer", Modifier.size(30.dp)) }
                IconButton(onClick = vm::undo, enabled = vm.canUndo) { Icon(Icons.AutoMirrored.Rounded.Undo, "Annuler") }
                FilledIconButton(
                    onClick = { top?.let { decide(it, true) } },
                    enabled = top != null,
                    modifier = Modifier.size(80.dp),
                    shape = PolygonShape(MaterialShapes.Cookie12Sided),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.primary),
                ) { Icon(Icons.Rounded.Favorite, "Garder", Modifier.size(34.dp)) }
            }
        }
    }
}

@Composable
private fun Filters(filter: InspirationFilter, onChange: (InspirationFilter) -> Unit) {
    LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        item { FilterChip(filter.budget == null, { onChange(filter.copy(budget = null)) }, { Text("Tous budgets") }) }
        items(BudgetTier.entries) { tier ->
            FilterChip(filter.budget == tier, { onChange(filter.copy(budget = if (filter.budget == tier) null else tier)) }, { Text(tier.label) })
        }
        items(SuggestionType.entries) { type ->
            val selected = type in filter.types
            FilterChip(selected, { onChange(filter.copy(types = if (selected) filter.types - type else filter.types + type)) }, { Text("${type.emoji} ${type.label}") })
        }
    }
}

@Composable
private fun CardStack(deck: List<Scored>, onDecision: (Scored, Boolean) -> Unit, haptic: HapticFeedback) {
    val visible = deck.take(3)
    val top = visible.firstOrNull() ?: return
    val swipe = remember(top.suggestion.id) { SwipeState() }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current
    BoxWithConstraints(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        swipe.width = with(density) { maxWidth.toPx() }
        val pull = abs(swipe.progress)
        visible.asReversed().forEach { card ->
            val depth = visible.indexOf(card)
            key(card.suggestion.id) {
                if (depth == 0) {
                    var crossed = false
                    SuggestionCard(
                        card,
                        swipe.progress,
                        Modifier
                            .graphicsLayer {
                                translationX = swipe.offset.value.x
                                translationY = swipe.offset.value.y
                                rotationZ = swipe.offset.value.x / swipe.width * 16f
                            }
                            .pointerInput(card.suggestion.id) {
                                detectDragGestures(
                                    onDragEnd = {
                                        scope.launch {
                                            val p = swipe.progress
                                            if (abs(p) >= 1f) {
                                                swipe.fling(p > 0)
                                                onDecision(card, p > 0)
                                            } else swipe.reset()
                                        }
                                    },
                                    onDragCancel = { scope.launch { swipe.reset() } },
                                ) { change, drag ->
                                    change.consume()
                                    scope.launch { swipe.offset.snapTo(swipe.offset.value + drag) }
                                    val nowCrossed = abs(swipe.progress) >= 1f
                                    if (nowCrossed != crossed) {
                                        crossed = nowCrossed
                                        haptic.performHapticFeedback(HapticFeedbackType.SegmentFrequentTick)
                                    }
                                }
                            },
                    )
                } else {
                    val scale = 1f - 0.06f * depth + 0.06f * pull
                    SuggestionCard(
                        card, 0f,
                        Modifier.graphicsLayer {
                            scaleX = scale; scaleY = scale
                            translationY = (depth - pull) * 18.dp.toPx()
                            alpha = if (depth == 2) pull.coerceAtLeast(0.6f) else 1f
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun SuggestionCard(card: Scored, progress: Float, modifier: Modifier) {
    val s = card.suggestion
    val c = MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val spin = if (!reduce) rememberInfiniteTransition(label = "card").animateFloat(0f, 360f, infiniteRepeatable(tween(20_000, easing = LinearEasing)), label = "spin").value else 0f
    Surface(modifier.fillMaxWidth().height(470.dp), shape = MaterialTheme.shapes.extraLarge, color = c.surfaceContainerLow, shadowElevation = 6.dp) {
        Box {
            Column {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(220.dp)
                        .background(Brush.linearGradient(listOf(c.primaryContainer, c.tertiaryContainer))),
                    contentAlignment = Alignment.Center,
                ) {
                    Box(
                        Modifier.size(150.dp).graphicsLayer { rotationZ = spin }.clip(PolygonShape(MaterialShapes.Flower)).background(c.surface.copy(alpha = 0.55f)),
                    )
                    Text(Inspiration.emojiFor(s), fontSize = 72.sp)
                }
                Column(Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(s.title, style = MaterialTheme.typography.headlineSmall)
                    Text(s.description, style = MaterialTheme.typography.bodyLarge, color = c.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Pill(s.budget.label)
                        Pill("${s.type.emoji} ${s.type.label}")
                    }
                    if (card.matched.isNotEmpty()) {
                        Text(
                            "Parce qu'il ou elle aime : " + card.matched.joinToString(", ") { Interests.label(it).lowercase() },
                            style = MaterialTheme.typography.labelLarge,
                            color = c.primary,
                        )
                    }
                }
            }
            // Verdict stamps fading in while dragging.
            AnimatedVisibility(progress > 0.15f, Modifier.align(Alignment.TopStart).padding(20.dp), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Stamp("GARDER", c.tertiary, -12f, progress)
            }
            AnimatedVisibility(progress < -0.15f, Modifier.align(Alignment.TopEnd).padding(20.dp), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                Stamp("PASSER", c.error, 12f, -progress)
            }
        }
    }
}

@Composable
private fun Stamp(text: String, color: androidx.compose.ui.graphics.Color, rotation: Float, strength: Float) {
    Text(
        text,
        Modifier
            .graphicsLayer { rotationZ = rotation; alpha = strength.coerceIn(0f, 1f) }
            .clip(MaterialTheme.shapes.small)
            .background(MaterialTheme.colorScheme.surface)
            .padding(horizontal = 14.dp, vertical = 6.dp),
        style = MaterialTheme.typography.titleLarge,
        color = color,
        textAlign = TextAlign.Center,
    )
}
