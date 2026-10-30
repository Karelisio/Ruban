package com.karelisio.ruban.ui.screens.people

import android.content.Context
import android.net.Uri
import android.provider.ContactsContract
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.container
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.ui.components.EmptyState
import com.karelisio.ruban.ui.permissions.AppPermission
import com.karelisio.ruban.ui.permissions.PermissionExplainer
import com.karelisio.ruban.ui.permissions.rememberPermissionState
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.plural
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class ContactBirthday(
    val lookupKey: String,
    val name: String,
    val month: Int,
    val day: Int,
    val year: Int?,
    val photoUri: String?,
)

object ContactsBirthdays {
    private val full = Regex("""^(\d{4})-(\d{2})-(\d{2}).*""")
    private val noYear = Regex("""^-{1,2}(\d{2})-?(\d{2}).*""")
    private val slash = Regex("""^(\d{1,2})/(\d{1,2})(?:/(\d{4}))?$""")

    /** Parses the formats used by contact apps: 1990-05-12, --05-12, 12/05/1990. */
    fun parse(raw: String): Triple<Int, Int, Int?>? {
        full.matchEntire(raw)?.let { m ->
            val (y, mo, d) = m.destructured
            return Triple(mo.toInt(), d.toInt(), y.toInt().takeIf { it > 1900 })
        }
        noYear.matchEntire(raw)?.let { m ->
            val (mo, d) = m.destructured
            return Triple(mo.toInt(), d.toInt(), null)
        }
        slash.matchEntire(raw)?.let { m ->
            val (d, mo, y) = m.destructured
            return Triple(mo.toInt(), d.toInt(), y.toIntOrNull())
        }
        return null
    }

    fun query(context: Context): List<ContactBirthday> {
        val projection = arrayOf(
            ContactsContract.Data.LOOKUP_KEY,
            ContactsContract.Data.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Event.START_DATE,
            ContactsContract.Data.PHOTO_URI,
        )
        val selection = "${ContactsContract.Data.MIMETYPE} = ? AND ${ContactsContract.CommonDataKinds.Event.TYPE} = ?"
        val args = arrayOf(ContactsContract.CommonDataKinds.Event.CONTENT_ITEM_TYPE, ContactsContract.CommonDataKinds.Event.TYPE_BIRTHDAY.toString())
        val result = mutableMapOf<String, ContactBirthday>()
        context.contentResolver.query(ContactsContract.Data.CONTENT_URI, projection, selection, args, ContactsContract.Data.DISPLAY_NAME)?.use { c ->
            while (c.moveToNext()) {
                val key = c.getString(0) ?: continue
                val name = c.getString(1) ?: continue
                val date = c.getString(2)?.let(::parse) ?: continue
                if (date.first !in 1..12 || date.second !in 1..31) continue
                result.putIfAbsent(key, ContactBirthday(key, name, date.first, date.second, date.third, c.getString(3)))
            }
        }
        return result.values.sortedBy { it.name.lowercase() }
    }
}

/** Bottom sheet listing contacts with a birthday, ready to be imported. */
@Composable
fun ContactsImportSheet(onDismiss: () -> Unit) {
    val context = LocalContext.current
    val container = context.container
    val scope = rememberCoroutineScope()
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val permission = rememberPermissionState(AppPermission.CONTACTS)
    var contacts by remember { mutableStateOf<List<ContactBirthday>?>(null) }
    val selected = remember { mutableStateListOf<String>() }
    var known by remember { mutableStateOf(emptySet<String>()) }
    var importing by remember { mutableStateOf(false) }

    LaunchedEffect(permission.granted) {
        if (!permission.granted) return@LaunchedEffect
        val list = withContext(Dispatchers.IO) { runCatching { ContactsBirthdays.query(context) }.getOrDefault(emptyList()) }
        known = container.persons.all().mapNotNull { it.contactLookupKey }.toSet()
        contacts = list
        selected.clear()
        selected.addAll(list.map { it.lookupKey }.filter { it !in known })
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet) {
        if (!permission.granted) {
            PermissionExplainer(AppPermission.CONTACTS, onLater = onDismiss)
            return@ModalBottomSheet
        }
        val list = contacts
        Column(Modifier.fillMaxWidth().padding(bottom = 16.dp)) {
            Text(
                "Importer les anniversaires",
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.padding(horizontal = 24.dp),
            )
            when {
                list == null -> Box(Modifier.fillMaxWidth().padding(48.dp), contentAlignment = Alignment.Center) { LoadingIndicator() }
                list.isEmpty() -> EmptyState("📇", "Aucun anniversaire", "Tes contacts n'ont pas encore de date d'anniversaire.")
                else -> {
                    Text(
                        "${list.size.plural("contact")} avec un anniversaire",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(horizontal = 24.dp, vertical = 4.dp),
                    )
                    Row(Modifier.padding(horizontal = 12.dp)) {
                        TextButton(onClick = { selected.clear(); selected.addAll(list.map { it.lookupKey }) }) { Text("Tout") }
                        TextButton(onClick = { selected.clear() }) { Text("Aucun") }
                    }
                    LazyColumn(Modifier.heightIn(max = 460.dp), contentPadding = PaddingValues(horizontal = 12.dp)) {
                        items(list, key = { it.lookupKey }) { c ->
                            val checked = c.lookupKey in selected
                            Row(
                                Modifier
                                    .fillMaxWidth()
                                    .clip(MaterialTheme.shapes.medium)
                                    .clickable { if (checked) selected.remove(c.lookupKey) else selected.add(c.lookupKey) }
                                    .padding(horizontal = 8.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Checkbox(checked, null)
                                Spacer(Modifier.width(8.dp))
                                Column(Modifier.weight(1f)) {
                                    Text(c.name, style = MaterialTheme.typography.titleMedium)
                                    Text(
                                        "🎂 ${Dates.dayMonth(c.month, c.day)}${c.year?.let { " $it" } ?: ""}" +
                                            if (c.lookupKey in known) " · déjà dans Ruban" else "",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            }
                        }
                    }
                    Button(
                        onClick = {
                            importing = true
                            scope.launch {
                                importContacts(context, list.filter { it.lookupKey in selected })
                                importing = false
                                sheet.hide()
                                onDismiss()
                            }
                        },
                        enabled = selected.isNotEmpty() && !importing,
                        shapes = ButtonDefaults.shapes(),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 8.dp),
                    ) { Text(if (importing) "Import…" else "Importer ${selected.size.plural("proche")}") }
                }
            }
        }
    }
}

suspend fun importContacts(context: Context, contacts: List<ContactBirthday>) {
    val c = context.container
    for (contact in contacts) {
        val existing = c.persons.byContactKey(contact.lookupKey)
        if (existing != null) {
            c.persons.save(existing.copy(birthdayMonth = contact.month, birthdayDay = contact.day, birthYear = contact.year ?: existing.birthYear))
        } else {
            val photo = contact.photoUri?.let { c.photos.importUri(Uri.parse(it)) }
            c.persons.save(
                Person(
                    name = contact.name,
                    photo = photo,
                    birthdayMonth = contact.month,
                    birthdayDay = contact.day,
                    birthYear = contact.year,
                    contactLookupKey = contact.lookupKey,
                ),
            )
        }
    }
}
