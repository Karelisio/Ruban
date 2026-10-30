package com.karelisio.ruban.ui.screens.received

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
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
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.ReceivedGift
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.EmptyState
import com.karelisio.ruban.ui.components.FormField
import com.karelisio.ruban.ui.components.LocalImage
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.components.ShapeBadge
import com.karelisio.ruban.ui.components.cascade
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.screens.people.FormSection
import com.karelisio.ruban.util.Dates
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate

data class ReceivedRow(val gift: ReceivedGift, val from: Person?)

class ReceivedViewModel(private val c: AppContainer) : ViewModel() {
    val onlyToThank = MutableStateFlow(false)
    val rows = combine(c.received.observeAll(), c.persons.observeAll(), onlyToThank) { gifts, persons, only ->
        val byId = persons.associateBy { it.id }
        gifts.filter { !only || !it.thanked }.map { ReceivedRow(it, it.fromPersonId?.let(byId::get)) }
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun toggleThanked(gift: ReceivedGift) = viewModelScope.launch { c.received.save(gift.copy(thanked = !gift.thanked)) }
}

@Composable
fun ReceivedScreen() {
    val vm = rubanViewModel { ReceivedViewModel(this) }
    val rows by vm.rows.collectAsStateWithLifecycle()
    val only by vm.onlyToThank.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val haptic = LocalHapticFeedback.current

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Cadeaux reçus") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(onClick = { nav.go(Route.ReceivedEdit()) }, icon = { Icon(Icons.Rounded.Add, null) }, text = { Text("Ajouter") })
        },
    ) { padding ->
        val list = rows ?: return@Scaffold
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 96.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item(key = "filters") {
                Row(Modifier.padding(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(!only, { vm.onlyToThank.value = false }, { Text("Tous") })
                    FilterChip(only, { vm.onlyToThank.value = true }, { Text("À remercier") })
                }
            }
            if (list.isEmpty()) item(key = "empty") {
                EmptyState(
                    "🎀", if (only) "Tout le monde est remercié" else "Rien de noté",
                    "Note ce qu'on t'offre : tu sauras qui remercier, et quoi rendre l'an prochain.",
                )
            }
            list.groupBy { LocalDate.ofEpochDay(it.gift.receivedOn).year }.forEach { (year, items) ->
                item(key = "year-$year") {
                    Text("$year", Modifier.padding(start = 20.dp, top = 12.dp), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                }
                items.forEachIndexed { i, row ->
                    item(key = row.gift.id) {
                        ReceivedCard(
                            row,
                            onClick = { nav.go(Route.ReceivedEdit(row.gift.id)) },
                            onThanked = {
                                haptic.performHapticFeedback(if (row.gift.thanked) HapticFeedbackType.ToggleOff else HapticFeedbackType.Confirm)
                                vm.toggleThanked(row.gift)
                            },
                            modifier = Modifier.padding(horizontal = 16.dp).animateItem().cascade(i),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ReceivedCard(row: ReceivedRow, onClick: () -> Unit, onThanked: () -> Unit, modifier: Modifier) {
    val g = row.gift
    val bg by animateColorAsState(if (g.thanked) MaterialTheme.colorScheme.surfaceContainerLow else MaterialTheme.colorScheme.secondaryContainer, label = "bg")
    Surface(modifier.fillMaxWidth().clip(MaterialTheme.shapes.large).clickable(onClick = onClick), color = bg) {
        Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
            if (row.from != null) PersonAvatar(row.from, 48.dp)
            else ShapeBadge(size = 48.dp, polygonIndex = g.id.toInt()) { Text(g.occasion?.emoji ?: "🎁", fontSize = 22.sp) }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(g.title, style = MaterialTheme.typography.titleMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(
                    listOfNotNull(
                        "De ${row.from?.name ?: g.fromName.ifBlank { "?" }}",
                        g.occasion?.label ?: g.occasionLabel.ifBlank { null },
                        Dates.dayMonth(LocalDate.ofEpochDay(g.receivedOn)),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                Checkbox(g.thanked, { onThanked() })
                Text(if (g.thanked) "Remercié" else "Merci ?", style = MaterialTheme.typography.labelSmall)
            }
        }
    }
}

class ReceivedEditViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val id = handle.toRoute<Route.ReceivedEdit>().id
    val form = MutableStateFlow(ReceivedGift(title = "", receivedOn = LocalDate.now().toEpochDay()))
    val persons = c.persons.observeAll().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    private var originalPhoto: String? = null

    init {
        if (id != 0L) viewModelScope.launch { c.received.get(id)?.let { form.value = it; originalPhoto = it.photo } }
    }

    fun update(block: ReceivedGift.() -> ReceivedGift) { form.value = form.value.block() }

    fun setPhoto(uri: android.net.Uri) = viewModelScope.launch {
        val name = c.photos.importUri(uri) ?: return@launch
        form.value.photo?.takeIf { it != originalPhoto }?.let(c.photos::delete)
        update { copy(photo = name) }
    }

    suspend fun save() {
        if (originalPhoto != null && originalPhoto != form.value.photo) c.photos.delete(originalPhoto)
        c.received.save(form.value.copy(title = form.value.title.trim()))
    }

    suspend fun delete() { if (form.value.id != 0L) c.received.delete(form.value) }
}

@Composable
fun ReceivedEditScreen() {
    val vm = rubanViewModel { ReceivedEditViewModel(this, it) }
    val gift by vm.form.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    var pickDate by rememberSaveable { mutableStateOf(false) }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setPhoto) }
    val date = LocalDate.ofEpochDay(gift.receivedOn)

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.toPickerMillis(date))
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { vm.update { copy(receivedOn = Dates.fromPickerMillis(it).toEpochDay()) } }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (gift.id == 0L) "Cadeau reçu" else "Modifier") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.Rounded.Close, "Fermer") } },
                actions = {
                    if (gift.id != 0L) IconButton(onClick = { scope.launch { vm.delete(); nav.back() } }) { Icon(Icons.Rounded.Delete, "Supprimer") }
                    Button(
                        onClick = { scope.launch { vm.save(); nav.back() } },
                        enabled = gift.title.isNotBlank(),
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
            FormField(gift.title, { v -> vm.update { copy(title = v) } }, "Qu'as-tu reçu ?")
            FormSection("De la part de") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(persons, key = { it.id }) { p ->
                        FilterChip(
                            selected = gift.fromPersonId == p.id,
                            onClick = { vm.update { copy(fromPersonId = if (fromPersonId == p.id) null else p.id) } },
                            label = { Text(p.name) },
                            leadingIcon = { PersonAvatar(p, 24.dp) },
                        )
                    }
                }
                if (gift.fromPersonId == null) {
                    FormField(gift.fromName, { v -> vm.update { copy(fromName = v) } }, "Ou son nom", capitalization = KeyboardCapitalization.Words)
                }
            }
            FormSection("Quand et pourquoi") {
                OutlinedButton(onClick = { pickDate = true }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.CalendarMonth, null)
                    Spacer(Modifier.width(8.dp))
                    Text(Dates.full(date))
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(EventKind.BIRTHDAY, EventKind.CHRISTMAS, EventKind.NAME_DAY, EventKind.VALENTINE, EventKind.MOTHERS_DAY, EventKind.FATHERS_DAY, EventKind.CUSTOM).forEach { k ->
                        FilterChip(gift.occasion == k, { vm.update { copy(occasion = if (occasion == k) null else k) } }, { Text("${k.emoji} ${k.label}") })
                    }
                }
                if (gift.occasion == EventKind.CUSTOM) FormField(gift.occasionLabel, { v -> vm.update { copy(occasionLabel = v) } }, "Quelle occasion ?")
            }
            FormSection("Remerciements") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Remercié", style = MaterialTheme.typography.titleMedium)
                        Text("Pour rendre la pareille l'an prochain", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(gift.thanked, { v -> vm.update { copy(thanked = v) } })
                }
                FormField(gift.notes, { v -> vm.update { copy(notes = v) } }, "Notes", singleLine = false, minLines = 2)
            }
            if (gift.photo != null) {
                LocalImage(gift.photo, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large).clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            } else {
                OutlinedButton(onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }, shapes = ButtonDefaults.shapes()) {
                    Icon(Icons.Rounded.AddAPhoto, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Ajouter une photo")
                }
            }
            Spacer(Modifier.height(40.dp))
        }
    }
}
