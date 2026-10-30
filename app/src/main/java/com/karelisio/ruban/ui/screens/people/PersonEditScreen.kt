package com.karelisio.ruban.ui.screens.people

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import androidx.navigation.toRoute
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.Relation
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.DayMonthField
import com.karelisio.ruban.ui.components.FormField
import com.karelisio.ruban.ui.components.MoneyField
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.components.SharedKeys
import com.karelisio.ruban.ui.components.TagInput
import com.karelisio.ruban.ui.components.sharedElement
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch

class PersonEditViewModel(private val c: AppContainer, handle: SavedStateHandle) : ViewModel() {
    private val id = handle.toRoute<Route.PersonEdit>().id
    val form = MutableStateFlow(Person(name = ""))
    val loaded = MutableStateFlow(id == 0L)
    private var originalPhoto: String? = null

    init {
        if (id != 0L) viewModelScope.launch {
            c.persons.get(id)?.let {
                form.value = it
                originalPhoto = it.photo
            }
            loaded.value = true
        }
    }

    fun update(block: Person.() -> Person) {
        form.value = form.value.block()
    }

    fun setPhoto(uri: android.net.Uri) = viewModelScope.launch {
        val name = c.photos.importUri(uri) ?: return@launch
        val previous = form.value.photo
        if (previous != null && previous != originalPhoto) c.photos.delete(previous)
        update { copy(photo = name) }
    }

    suspend fun save(): Long {
        val person = form.value.copy(name = form.value.name.trim())
        if (originalPhoto != null && originalPhoto != person.photo) c.photos.delete(originalPhoto)
        return c.persons.save(person)
    }
}

@Composable
fun PersonEditScreen() {
    val vm = rubanViewModel { PersonEditViewModel(this, it) }
    val person by vm.form.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setPhoto) }
    val isNew = person.id == 0L

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nouveau proche" else "Modifier") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.Rounded.Close, "Fermer") } },
                actions = {
                    Button(
                        onClick = {
                            scope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                val id = vm.save()
                                if (isNew) nav.replace(Route.PersonDetail(id)) else nav.back()
                            }
                        },
                        enabled = person.name.isNotBlank(),
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.padding(end = 8.dp),
                    ) { Text("Enregistrer") }
                },
            )
        },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .imePadding()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            Box(Modifier.fillMaxWidth().padding(top = 8.dp), contentAlignment = Alignment.Center) {
                Box(
                    Modifier.clickable { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                ) {
                    PersonAvatar(person.copy(name = person.name.ifBlank { "?" }), 128.dp, Modifier.sharedElement(SharedKeys.avatar(person.id)))
                    Box(
                        Modifier
                            .align(Alignment.BottomEnd)
                            .size(42.dp)
                            .clip(CircleShape)
                            .background(MaterialTheme.colorScheme.primary),
                        contentAlignment = Alignment.Center,
                    ) { Icon(Icons.Rounded.AddAPhoto, "Choisir une photo", tint = MaterialTheme.colorScheme.onPrimary, modifier = Modifier.size(22.dp)) }
                }
            }
            FormField(person.name, { v -> vm.update { copy(name = v) } }, "Prénom ou surnom", capitalization = KeyboardCapitalization.Words)

            FormSection("Relation") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Relation.entries.forEach { r ->
                        FilterChip(
                            selected = person.relation == r,
                            onClick = {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                vm.update { copy(relation = r) }
                            },
                            label = { Text("${r.emoji} ${r.label}") },
                        )
                    }
                }
            }

            FormSection("Dates importantes") {
                DayMonthField(
                    "🎂 Anniversaire", person.birthdayMonth, person.birthdayDay,
                    onChange = { m, d -> vm.update { copy(birthdayMonth = m, birthdayDay = d) } },
                    year = person.birthYear,
                    onYearChange = { y -> vm.update { copy(birthYear = y) } },
                )
                DayMonthField(
                    "🌸 Fête", person.nameDayMonth, person.nameDayDay,
                    onChange = { m, d -> vm.update { copy(nameDayMonth = m, nameDayDay = d) } },
                )
                if (person.nameDayMonth == null) {
                    NameDays.find(person.name)?.let { (m, d) ->
                        androidx.compose.material3.AssistChip(
                            onClick = { vm.update { copy(nameDayMonth = m, nameDayDay = d) } },
                            label = { Text("Fêté le ${com.karelisio.ruban.util.Dates.dayMonth(m, d)} ? Ajouter") },
                        )
                    }
                }
            }

            FormSection("Goûts") {
                TagInput(person.interests, { t -> vm.update { copy(interests = t) } }, "Centres d'intérêt")
                FormField(person.favoriteBrands, { v -> vm.update { copy(favoriteBrands = v) } }, "Marques préférées")
                FormField(person.favoriteColors, { v -> vm.update { copy(favoriteColors = v) } }, "Couleurs préférées")
            }

            FormSection("Tailles et pointures") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FormField(person.clothingSize, { v -> vm.update { copy(clothingSize = v) } }, "Vêtements", Modifier.weight(1f))
                    FormField(person.shoeSize, { v -> vm.update { copy(shoeSize = v) } }, "Chaussures", Modifier.weight(1f))
                    FormField(person.ringSize, { v -> vm.update { copy(ringSize = v) } }, "Bague", Modifier.weight(1f))
                }
            }

            FormSection("À savoir") {
                FormField(person.avoid, { v -> vm.update { copy(avoid = v) } }, "À éviter (allergies, phobies…)", singleLine = false, minLines = 2)
                FormField(person.doNotGive, { v -> vm.update { copy(doNotGive = v) } }, "À ne pas offrir", singleLine = false, minLines = 2)
                MoneyField(person.yearlyBudgetCents, { v -> vm.update { copy(yearlyBudgetCents = v) } }, "Budget annuel", supporting = "Optionnel : total prévu sur l'année")
                FormField(person.notes, { v -> vm.update { copy(notes = v) } }, "Notes libres", singleLine = false, minLines = 3)
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
fun FormSection(title: String, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            content()
        }
    }
}
