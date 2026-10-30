package com.karelisio.ruban.ui.screens.ideas

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
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
import androidx.compose.ui.platform.LocalContext
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
import com.karelisio.ruban.data.model.PricePoint
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.AnimatedMoney
import com.karelisio.ruban.ui.components.LineChart
import com.karelisio.ruban.ui.components.LocalImage
import com.karelisio.ruban.ui.components.PeopleStack
import com.karelisio.ruban.ui.components.PriorityTag
import com.karelisio.ruban.ui.components.SharedKeys
import com.karelisio.ruban.ui.components.ShapeBadge
import com.karelisio.ruban.ui.components.recipientsLabel
import com.karelisio.ruban.ui.components.sharedBounds
import com.karelisio.ruban.ui.components.sharedElement
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.screens.gift.GiftDecoration
import com.karelisio.ruban.ui.screens.gift.StatusStepper
import com.karelisio.ruban.ui.screens.gift.UnwrapCelebration
import com.karelisio.ruban.ui.screens.home.occasionLabel
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.Money
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class IdeaDetailUi(
    val item: IdeaWithPeople? = null,
    val occasion: String? = null,
    val prices: List<PricePoint> = emptyList(),
    val loaded: Boolean = false,
)

class IdeaDetailViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    val id = handle.toRoute<Route.IdeaDetail>().id

    val ui = combine(c.ideas.observe(id), c.events.observeAll(), c.persons.observeAll(), c.db.prices().observe(id)) { item, events, persons, prices ->
        IdeaDetailUi(
            item = item,
            occasion = item?.let { occasionLabel(it.idea, events.associateBy { e -> e.id }, persons.associateBy { p -> p.id }) },
            prices = prices,
            loaded = true,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), IdeaDetailUi())

    fun setStatus(status: GiftStatus) = viewModelScope.launch { c.ideas.setStatus(id, status) }

    val checking = kotlinx.coroutines.flow.MutableStateFlow(false)

    fun checkPrice(onResult: (String) -> Unit) = viewModelScope.launch {
        val idea = c.ideas.get(id)?.idea ?: return@launch
        checking.value = true
        val change = c.prices.check(idea)
        checking.value = false
        onResult(
            when {
                change == null -> "Prix introuvable sur la page"
                change.before != null && change.after < change.before -> "Bonne nouvelle : le prix a baissé !"
                change.before != null && change.after > change.before -> "Le prix a augmenté"
                else -> "Prix inchangé"
            },
        )
    }

    fun markGiven(result: GivenResult) = viewModelScope.launch {
        val idea = c.ideas.get(id)?.idea ?: return@launch
        c.ideas.update(
            idea.copy(
                status = GiftStatus.GIVEN,
                boughtAt = idea.boughtAt ?: result.givenAt,
                wrappedAt = idea.wrappedAt ?: result.givenAt,
                givenAt = result.givenAt,
                eventId = result.eventId ?: idea.eventId,
                occasionYear = result.occasionYear ?: idea.occasionYear,
                reactionEmoji = result.emoji,
                reactionNote = result.note,
                momentPhoto = result.photo ?: idea.momentPhoto,
            ),
        )
    }

    fun delete(onDone: () -> Unit) = viewModelScope.launch {
        ui.value.item?.let { c.ideas.delete(it) }
        onDone()
    }
}

@Composable
fun IdeaDetailScreen() {
    val vm = rubanViewModel { IdeaDetailViewModel(this, it) }
    val ui by vm.ui.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by rememberSaveable { mutableStateOf(false) }
    var givingSheet by rememberSaveable { mutableStateOf(false) }
    var celebrate by remember { mutableStateOf(false) }
    val checking by vm.checking.collectAsStateWithLifecycle()
    val snackbar = remember { androidx.compose.material3.SnackbarHostState() }
    val item = ui.item

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Supprimer cette idée ?") },
            text = { Text("Elle disparaîtra aussi de l'historique et du budget.") },
            confirmButton = { TextButton(onClick = { confirmDelete = false; scope.launch { vm.delete { nav.back() } } }) { Text("Supprimer") } },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Annuler") } },
        )
    }
    if (givingSheet && item != null) {
        MarkGivenSheet(
            item = item,
            onDismiss = { givingSheet = false },
            onConfirm = { result ->
                givingSheet = false
                vm.markGiven(result)
                celebrate = true
            },
        )
    }

    Box(Modifier.fillMaxSize()) {
        Scaffold(
            modifier = Modifier.sharedBounds(SharedKeys.idea(vm.id)),
            snackbarHost = { androidx.compose.material3.SnackbarHost(snackbar) },
            topBar = {
                TopAppBar(
                    title = {},
                    navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                    actions = {
                        IconButton(onClick = { nav.go(Route.IdeaEdit(id = vm.id)) }) { Icon(Icons.Rounded.Edit, "Modifier") }
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
            if (item == null) return@Scaffold
            val idea = item.idea
            Column(
                Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                // Hero card, dressed according to the status.
                Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                    GiftDecoration(idea.status, idea.id, ribbonX = 64f) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            if (idea.image != null) {
                                LocalImage(
                                    idea.image,
                                    Modifier
                                        .fillMaxWidth()
                                        .aspectRatio(4f / 3f)
                                        .sharedElement(SharedKeys.ideaImage(idea.id))
                                        .clip(MaterialTheme.shapes.large),
                                )
                                Spacer(Modifier.height(16.dp))
                            } else {
                                ShapeBadge(size = 96.dp, polygonIndex = idea.id.toInt(), container = MaterialTheme.colorScheme.primaryContainer, spin = true) {
                                    Text(idea.priority.emoji, fontSize = 40.sp)
                                }
                                Spacer(Modifier.height(12.dp))
                            }
                            Text(idea.title, style = MaterialTheme.typography.headlineMedium)
                            Spacer(Modifier.height(8.dp))
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                if (item.people.isNotEmpty()) {
                                    PeopleStack(item.people, 30.dp)
                                    Spacer(Modifier.width(4.dp))
                                }
                                Text(item.recipientsLabel(), style = MaterialTheme.typography.titleSmall)
                            }
                            ui.occasion?.let {
                                Spacer(Modifier.height(4.dp))
                                Text(it, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.primary)
                            }
                            Spacer(Modifier.height(8.dp))
                            if (idea.status == GiftStatus.IDEA) PriorityTag(idea.priority)
                        }
                    }
                }

                StatusStepper(
                    idea.status,
                    onSelect = { s -> if (s == GiftStatus.GIVEN && idea.status != GiftStatus.GIVEN) givingSheet = true else vm.setStatus(s) },
                )

                if (idea.status == GiftStatus.GIVEN) GivenCard(item)

                PriceCard(
                    idea.priceCents, idea.currency, ui.prices, idea.priceAlertCents, idea.url,
                    checking = checking,
                    onCheck = if (idea.status == GiftStatus.IDEA && idea.url != null) {
                        { vm.checkPrice { msg -> scope.launch { snackbar.showSnackbar(msg) } } }
                    } else null,
                ) { url ->
                    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url))) }
                }

                if (idea.notes.isNotBlank()) {
                    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
                        Column(Modifier.fillMaxWidth().padding(16.dp)) {
                            Text("Notes", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                            Spacer(Modifier.height(6.dp))
                            Text(idea.notes, style = MaterialTheme.typography.bodyLarge)
                        }
                    }
                }
                Text(
                    "Ajoutée le ${Dates.fullMillis(idea.createdAt)}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 32.dp),
                )
            }
        }
        if (celebrate) UnwrapCelebration(onFinished = { celebrate = false })
    }
}

@Composable
private fun GivenCard(item: IdeaWithPeople) {
    val idea = item.idea
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.tertiaryContainer) {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(idea.reactionEmoji ?: "🎉", fontSize = 40.sp)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Offert", style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
                    idea.givenAt?.let { Text("le ${Dates.fullMillis(it)}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onTertiaryContainer) }
                }
            }
            if (idea.reactionNote.isNotBlank()) {
                Spacer(Modifier.height(10.dp))
                Text("« ${idea.reactionNote} »", style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onTertiaryContainer)
            }
            if (idea.momentPhoto != null) {
                Spacer(Modifier.height(12.dp))
                LocalImage(idea.momentPhoto, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large))
            }
        }
    }
}

@Composable
private fun PriceCard(
    price: Long?,
    currency: String,
    history: List<PricePoint>,
    alert: Long?,
    url: String?,
    checking: Boolean,
    onCheck: (() -> Unit)?,
    openUrl: (String) -> Unit,
) {
    if (price == null && url == null) return
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Prix", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                    if (price != null) AnimatedMoney(price, currency = currency, style = MaterialTheme.typography.displaySmall)
                    else Text("—", style = MaterialTheme.typography.displaySmall)
                }
                if (onCheck != null) {
                    if (checking) androidx.compose.material3.LoadingIndicator(Modifier.size(40.dp))
                    else IconButton(onClick = onCheck) { Icon(Icons.Rounded.Refresh, "Vérifier le prix maintenant") }
                }
                if (url != null) {
                    FilledTonalButton(onClick = { openUrl(url) }, shapes = ButtonDefaults.shapes()) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text(runCatching { Uri.parse(url).host?.removePrefix("www.") }.getOrNull() ?: "Ouvrir")
                    }
                }
            }
            if (history.size >= 2) {
                val first = history.first().priceCents
                val last = history.last().priceCents
                Spacer(Modifier.height(12.dp))
                LineChart(
                    history.map { it.priceCents.toFloat() },
                    Modifier.fillMaxWidth().height(96.dp),
                    color = if (last < first) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary,
                    fill = true,
                )
                Row(Modifier.fillMaxWidth().padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(Dates.fullMillis(history.first().checkedAt), style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.weight(1f))
                    if (last < first) {
                        Icon(Icons.AutoMirrored.Rounded.TrendingDown, null, tint = MaterialTheme.colorScheme.tertiary, modifier = Modifier.size(16.dp))
                        Text(" −${Money.format(first - last, currency)}", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.tertiary)
                    }
                }
            }
            if (alert != null) {
                Spacer(Modifier.height(8.dp))
                Box(Modifier.clip(MaterialTheme.shapes.small).background(MaterialTheme.colorScheme.secondaryContainer).padding(horizontal = 10.dp, vertical = 4.dp)) {
                    Text("🔔 Alerte sous ${Money.format(alert, currency)}", style = MaterialTheme.typography.labelMedium)
                }
            }
        }
    }
}
