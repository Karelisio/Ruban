package com.karelisio.ruban.ui.screens.ideas

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.AddAPhoto
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.ruban.container
import com.karelisio.ruban.data.model.IdeaWithPeople
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.Occurrence
import com.karelisio.ruban.ui.components.FormField
import com.karelisio.ruban.ui.components.LocalImage
import com.karelisio.ruban.util.Dates
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.temporal.ChronoUnit

data class GivenResult(
    val givenAt: Long,
    val eventId: Long?,
    val occasionYear: Int?,
    val emoji: String?,
    val note: String,
    val photo: String?,
)

val reactions = listOf("😍", "🥹", "😊", "🙂", "😐", "😬")

@Composable
fun MarkGivenSheet(item: IdeaWithPeople, onDismiss: () -> Unit, onConfirm: (GivenResult) -> Unit) {
    val context = LocalContext.current
    val c = context.container
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var date by remember { mutableStateOf(LocalDate.now()) }
    var pickDate by remember { mutableStateOf(false) }
    var emoji by remember { mutableStateOf<String?>(null) }
    var note by remember { mutableStateOf("") }
    var photo by remember { mutableStateOf<String?>(null) }
    var eventId by remember { mutableStateOf(item.idea.eventId) }
    var year by remember { mutableStateOf(item.idea.occasionYear) }
    var options by remember { mutableStateOf(emptyList<Occurrence>()) }

    LaunchedEffect(date) {
        val people = item.people.associateBy { it.id }
        val events = c.events.all().filter { it.personId == null || it.personId in people }
        // Occurrences close to the chosen date (before or after).
        options = events.mapNotNull { e ->
            val p = e.personId?.let(people::get)
            listOfNotNull(EventDates.previous(e, p, date.plusDays(1)), EventDates.next(e, p, date))
                .minByOrNull { kotlin.math.abs(ChronoUnit.DAYS.between(date, it)) }
                ?.takeIf { kotlin.math.abs(ChronoUnit.DAYS.between(date, it)) <= 45 }
                ?.let { Occurrence(e, p, it) }
        }.sortedBy { kotlin.math.abs(ChronoUnit.DAYS.between(date, it.date)) }.take(6)
        if (eventId == null) options.firstOrNull()?.let { eventId = it.event.id; year = it.date.year }
    }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) scope.launch { photo?.let(c.photos::delete); photo = c.photos.importUri(uri) }
    }

    if (pickDate) {
        val state = rememberDatePickerState(initialSelectedDateMillis = Dates.toPickerMillis(date))
        DatePickerDialog(
            onDismissRequest = { pickDate = false },
            confirmButton = {
                TextButton(onClick = {
                    state.selectedDateMillis?.let { date = Dates.fromPickerMillis(it) }
                    pickDate = false
                }) { Text("OK") }
            },
            dismissButton = { TextButton(onClick = { pickDate = false }) { Text("Annuler") } },
        ) { DatePicker(state) }
    }

    ModalBottomSheet(onDismissRequest = { photo?.let(c.photos::delete); onDismiss() }, sheetState = sheet) {
        Column(
            Modifier.fillMaxWidth().verticalScroll(rememberScrollState()).padding(horizontal = 24.dp).padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text("C'est offert ! 🎉", style = MaterialTheme.typography.headlineMedium)
            OutlinedButton(onClick = { pickDate = true }, shapes = ButtonDefaults.shapes()) {
                Icon(Icons.Rounded.CalendarMonth, null)
                Spacer(Modifier.width(8.dp))
                Text(Dates.weekday(date))
            }
            if (options.isNotEmpty()) {
                Text("Pour quelle occasion ?", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    FilterChip(eventId == null, { eventId = null; year = null }, { Text("Pour le plaisir") })
                    options.forEach { o ->
                        FilterChip(
                            selected = eventId == o.event.id,
                            onClick = { eventId = o.event.id; year = o.date.year },
                            label = { Text("${o.emoji} ${o.title}") },
                        )
                    }
                }
            }
            Text("Sa réaction", style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                reactions.forEach { r ->
                    val selected = emoji == r
                    val scale by animateFloatAsState(if (selected) 1.3f else 1f, spring(0.35f, Spring.StiffnessMedium), label = "react")
                    Box(
                        Modifier
                            .size(48.dp)
                            .graphicsLayer { scaleX = scale; scaleY = scale }
                            .clip(CircleShape)
                            .background(if (selected) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainerHigh)
                            .clickable {
                                haptic.performHapticFeedback(HapticFeedbackType.SegmentTick)
                                emoji = if (selected) null else r
                            },
                        contentAlignment = Alignment.Center,
                    ) { Text(r, fontSize = 24.sp) }
                }
            }
            FormField(note, { note = it }, "Un mot sur ce moment", singleLine = false, minLines = 2)
            if (photo != null) {
                LocalImage(photo, Modifier.fillMaxWidth().aspectRatio(4f / 3f).clip(MaterialTheme.shapes.large).clickable {
                    picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                })
            } else {
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    shapes = ButtonDefaults.shapes(),
                ) {
                    Icon(Icons.Rounded.AddAPhoto, null)
                    Spacer(Modifier.width(8.dp))
                    Text("Photo du moment")
                }
            }
            Spacer(Modifier.height(4.dp))
            Button(
                onClick = {
                    onConfirm(GivenResult(Dates.toMillis(date), eventId, year, emoji, note.trim(), photo))
                },
                shapes = ButtonDefaults.shapes(),
                modifier = Modifier.fillMaxWidth().height(56.dp),
            ) { Text("Marquer comme offert", style = MaterialTheme.typography.titleMedium) }
        }
    }
}
