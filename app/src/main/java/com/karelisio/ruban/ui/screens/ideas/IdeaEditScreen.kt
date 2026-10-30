package com.karelisio.ruban.ui.screens.ideas

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.PhotoLibrary
import androidx.compose.material.icons.rounded.WarningAmber
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ButtonGroupDefaults
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.ToggleButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.ruban.container
import com.karelisio.ruban.data.model.Priority
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.components.Dropdown
import com.karelisio.ruban.ui.components.FormField
import com.karelisio.ruban.ui.components.LocalImage
import com.karelisio.ruban.ui.components.MoneyField
import com.karelisio.ruban.ui.components.PersonAvatar
import com.karelisio.ruban.ui.nav.CaptureStart
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.permissions.AppPermission
import com.karelisio.ruban.ui.permissions.PermissionExplainer
import com.karelisio.ruban.ui.permissions.rememberPermissionState
import com.karelisio.ruban.ui.screens.people.FormSection
import com.karelisio.ruban.util.Dates
import kotlinx.coroutines.launch
import java.io.File

private val currencies = listOf("EUR", "USD", "GBP", "CHF", "CAD")

@Composable
fun IdeaEditScreen() {
    val vm = rubanViewModel { IdeaEditViewModel(this, it) }
    val idea by vm.form.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val persons by vm.persons.collectAsStateWithLifecycle()
    val occasions by vm.occasions.collectAsStateWithLifecycle()
    val duplicates by vm.duplicates.collectAsStateWithLifecycle()
    val link by vm.link.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val isNew = idea.id == 0L

    var voice by rememberSaveable { mutableStateOf(vm.startWith == CaptureStart.VOICE) }
    var cameraExplainer by rememberSaveable { mutableStateOf(false) }
    var cameraFile by remember { mutableStateOf<File?>(null) }

    val gallery = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri -> uri?.let(vm::setImage) }
    val camera = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        val file = cameraFile
        if (ok && file != null) vm.setImageFile(file) else file?.delete()
        cameraFile = null
    }
    val cameraPermission = rememberPermissionState(AppPermission.CAMERA)
    fun openCamera() {
        if (!cameraPermission.granted) {
            cameraExplainer = true
            return
        }
        val (file, uri) = context.container.photos.newCameraTarget()
        cameraFile = file
        camera.launch(uri)
    }
    fun openGallery() = gallery.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    LaunchedEffect(Unit) {
        when (vm.startWith) {
            CaptureStart.CAMERA -> openCamera()
            CaptureStart.GALLERY -> openGallery()
        }
    }

    fun close() {
        vm.discard()
        nav.back()
    }
    BackHandler { close() }

    if (voice) VoiceCaptureSheet(onResult = { vm.appendDictation(it); voice = false }, onDismiss = { voice = false })
    if (cameraExplainer) {
        androidx.compose.material3.ModalBottomSheet(onDismissRequest = { cameraExplainer = false }) {
            PermissionExplainer(AppPermission.CAMERA, onGranted = { cameraExplainer = false; openCamera() }, onLater = { cameraExplainer = false })
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (isNew) "Nouvelle idée" else "Modifier l'idée") },
                navigationIcon = { IconButton(onClick = ::close) { Icon(Icons.Rounded.Close, "Fermer") } },
                actions = {
                    Button(
                        onClick = {
                            scope.launch {
                                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                                val id = vm.save()
                                if (isNew) nav.replace(Route.IdeaDetail(id)) else nav.back()
                            }
                        },
                        enabled = idea.title.isNotBlank(),
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
            // Picture
            AnimatedContent(idea.image, transitionSpec = { fadeIn() togetherWith fadeOut() }, label = "image") { image ->
                if (image != null) {
                    Box(Modifier.fillMaxWidth().aspectRatio(16f / 10f).clip(MaterialTheme.shapes.extraLarge)) {
                        LocalImage(image, Modifier.fillMaxSize())
                        FilledIconButton(
                            onClick = vm::removeImage,
                            modifier = Modifier.align(Alignment.TopEnd).padding(8.dp),
                            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f), contentColor = MaterialTheme.colorScheme.onSurface),
                        ) { Icon(Icons.Rounded.Close, "Retirer la photo") }
                    }
                } else {
                    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        CaptureButton(Icons.Rounded.CameraAlt, "Photo", Modifier.weight(1f), ::openCamera)
                        CaptureButton(Icons.Rounded.PhotoLibrary, "Galerie", Modifier.weight(1f), ::openGallery)
                        CaptureButton(Icons.Rounded.Mic, "Dicter", Modifier.weight(1f)) { voice = true }
                    }
                }
            }

            OutlinedTextField(
                value = idea.title,
                onValueChange = { v -> vm.update { copy(title = v) } },
                label = { Text("L'idée") },
                placeholder = { Text("Un carnet de croquis, un cours de poterie…") },
                textStyle = MaterialTheme.typography.titleLarge,
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                trailingIcon = { IconButton(onClick = { voice = true }) { Icon(Icons.Rounded.Mic, "Dicter") } },
            )

            AnimatedVisibility(duplicates.isNotEmpty(), enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
                Surface(shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.errorContainer) {
                    Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Rounded.WarningAmber, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                        Spacer(Modifier.width(12.dp))
                        Column {
                            duplicates.distinctBy { it.person.id to it.idea.id }.take(3).forEach { d ->
                                Text(
                                    "Déjà offert à ${d.person.name}" + (d.idea.givenAt?.let { " le ${Dates.fullMillis(it)}" } ?: ""),
                                    style = MaterialTheme.typography.titleSmall,
                                    color = MaterialTheme.colorScheme.onErrorContainer,
                                )
                                Text("« ${d.idea.title} »", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onErrorContainer)
                            }
                        }
                    }
                }
            }

            FormSection("Pour qui ?") {
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(end = 8.dp)) {
                    item(key = "me") {
                        FilterChip(
                            selected = idea.forMe,
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.setForMe(!idea.forMe) },
                            label = { Text("🙋 Pour moi") },
                        )
                    }
                    items(persons, key = { it.id }) { p ->
                        FilterChip(
                            selected = p.id in selected,
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.togglePerson(p.id) },
                            label = { Text(p.name) },
                            leadingIcon = { PersonAvatar(p, 24.dp) },
                        )
                    }
                }
                Text(
                    when {
                        idea.forMe -> "Une envie à toi : à glisser dans la conversation quand on te demande une idée."
                        selected.isEmpty() -> "Sans proche, l'idée reste « à attribuer »."
                        else -> "${selected.size} proche${if (selected.size > 1) "s" else ""} sélectionné${if (selected.size > 1) "s" else ""}"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!idea.forMe) FormSection("Pour quelle occasion ?") {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(selected = idea.eventId == null, onClick = { vm.setOccasion(null) }, label = { Text("Sans occasion") })
                    occasions.forEach { o ->
                        FilterChip(
                            selected = idea.eventId == o.eventId,
                            onClick = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.setOccasion(o) },
                            label = { Text("${o.emoji} ${o.label} · ${Dates.dayMonth(o.date)}") },
                        )
                    }
                }
            }

            FormSection("Détails") {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    MoneyField(idea.priceCents, { v -> vm.update { copy(priceCents = v) } }, "Prix", Modifier.weight(1f), idea.currency)
                    Dropdown(idea.currency, currencies, { cur -> vm.update { copy(currency = cur) } }, Modifier.width(110.dp))
                }
                Text("Priorité", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(ButtonGroupDefaults.ConnectedSpaceBetween)) {
                    Priority.entries.forEachIndexed { i, p ->
                        ToggleButton(
                            checked = idea.priority == p,
                            onCheckedChange = { haptic.performHapticFeedback(HapticFeedbackType.SegmentTick); vm.update { copy(priority = p) } },
                            modifier = Modifier.weight(1f).semantics { role = Role.RadioButton },
                            contentPadding = PaddingValues(horizontal = 6.dp),
                            shapes = when (i) {
                                0 -> ButtonGroupDefaults.connectedLeadingButtonShapes()
                                Priority.entries.lastIndex -> ButtonGroupDefaults.connectedTrailingButtonShapes()
                                else -> ButtonGroupDefaults.connectedMiddleButtonShapes()
                            },
                        ) { Text("${p.emoji} ${p.label}", maxLines = 1) }
                    }
                }
                FormField(
                    idea.url.orEmpty(),
                    { v -> vm.update { copy(url = v.trim().ifBlank { null }) } },
                    "Lien",
                    keyboardType = KeyboardType.Uri,
                    capitalization = KeyboardCapitalization.None,
                    leading = { Icon(Icons.Rounded.Link, null) },
                    trailing = {
                        val url = idea.url
                        if (url != null && url.startsWith("http")) {
                            if (link == LinkState.Loading) LoadingIndicator(Modifier.size(36.dp))
                            else IconButton(onClick = { vm.fetchLink(url) }) { Icon(Icons.Rounded.CloudDownload, "Récupérer les infos du lien") }
                        }
                    },
                    supporting = when (link) {
                        LinkState.Loading -> "Lecture de la page…"
                        LinkState.Blocked -> "Ce site ne se laisse pas lire : complète à la main, le lien est gardé."
                        is LinkState.Done -> (link as LinkState.Done).preview.siteName?.let { "Infos récupérées depuis $it" } ?: "Infos récupérées ✨"
                        LinkState.Idle -> null
                    },
                )
                FormField(idea.notes, { v -> vm.update { copy(notes = v) } }, "Notes (taille, couleur, boutique…)", singleLine = false, minLines = 3)
            }

            if (idea.url != null) FormSection("Alerte prix") {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Surveiller le prix", style = MaterialTheme.typography.titleMedium)
                        Text("Vérifié chaque jour tant que l'idée n'est pas achetée.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                    Switch(idea.priceTracking, { v -> vm.update { copy(priceTracking = v) } })
                }
                if (idea.priceTracking) {
                    com.karelisio.ruban.ui.permissions.PermissionBanner(AppPermission.NOTIFICATIONS, "Active les notifications pour être prévenu des baisses.")
                    MoneyField(
                        idea.priceAlertCents, { v -> vm.update { copy(priceAlertCents = v) } },
                        "Me prévenir sous", currency = idea.currency,
                        supporting = "Optionnel : sinon, à chaque baisse",
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}

@Composable
private fun CaptureButton(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, modifier: Modifier, onClick: () -> Unit) {
    FilledTonalButton(
        onClick = onClick,
        modifier = modifier.height(88.dp),
        shapes = ButtonDefaults.shapes(),
        contentPadding = PaddingValues(8.dp),
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(icon, null, Modifier.size(28.dp))
            Spacer(Modifier.height(6.dp))
            Text(label)
        }
    }
}
