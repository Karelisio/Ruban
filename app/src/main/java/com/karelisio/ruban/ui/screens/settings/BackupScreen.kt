package com.karelisio.ruban.ui.screens.settings

import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.FolderOpen
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.Visibility
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.ruban.container
import com.karelisio.ruban.data.backup.SecretBox
import com.karelisio.ruban.data.backup.WrongPasswordException
import com.karelisio.ruban.data.settings.Settings
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.plural
import com.karelisio.ruban.work.WorkScheduler
import kotlinx.coroutines.launch
import java.time.LocalDate

private const val MIN_PASSWORD = 8

@Composable
fun BackupScreen() {
    val context = LocalContext.current
    val c = context.container
    val nav = LocalNav.current
    val scope = rememberCoroutineScope()
    val haptic = LocalHapticFeedback.current
    val snackbar = remember { SnackbarHostState() }
    val settings by c.settings.settings.collectAsStateWithLifecycle(Settings())
    var busy by remember { mutableStateOf(false) }
    var exportPassword by rememberSaveable { mutableStateOf("") }
    var exportConfirm by rememberSaveable { mutableStateOf("") }
    var importUri by rememberSaveable { mutableStateOf<String?>(null) }
    var autoFolder by rememberSaveable { mutableStateOf<String?>(null) }

    fun report(message: String) = scope.launch { snackbar.showSnackbar(message) }

    val exporter = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/octet-stream")) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        busy = true
        scope.launch {
            runCatching {
                context.contentResolver.openOutputStream(uri)!!.use { c.backup.export(it, exportPassword.toCharArray()) }
            }.onSuccess {
                haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                report("Sauvegarde créée : ${it.persons.plural("proche")}, ${it.ideas.plural("idée")}, ${it.photos.plural("photo")}")
                exportPassword = ""; exportConfirm = ""
            }.onFailure { report("Échec de l'export : ${it.message}") }
            busy = false
        }
    }
    val importer = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> importUri = uri?.toString() }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        uri ?: return@rememberLauncherForActivityResult
        context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        autoFolder = uri.toString()
    }

    importUri?.let { uri ->
        PasswordDialog(
            title = "Importer une sauvegarde",
            text = "Toutes les données actuelles seront remplacées par celles de la sauvegarde.",
            confirm = "Importer",
            onDismiss = { importUri = null },
        ) { password ->
            importUri = null
            busy = true
            scope.launch {
                runCatching { context.contentResolver.openInputStream(Uri.parse(uri))!!.use { c.backup.import(it, password.toCharArray()) } }
                    .onSuccess {
                        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
                        report("Restauré : ${it.persons.plural("proche")}, ${it.ideas.plural("idée")}")
                    }
                    .onFailure { report(if (it is WrongPasswordException) "Mot de passe incorrect" else "Import impossible : ${it.message}") }
                busy = false
            }
        }
    }
    autoFolder?.let { folder ->
        PasswordDialog(
            title = "Mot de passe des sauvegardes auto",
            text = "Il sera conservé chiffré sur ce téléphone. Note-le : il faudra le saisir pour restaurer.",
            confirm = "Activer",
            requireConfirm = true,
            onDismiss = { autoFolder = null },
        ) { password ->
            autoFolder = null
            scope.launch {
                c.settings.setAutoBackup(true, folder, SecretBox.seal(password))
                WorkScheduler.setAutoBackup(context, true)
                report("Sauvegarde automatique activée")
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Sauvegarde") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                actions = { if (busy) LoadingIndicator(Modifier.size(40.dp).padding(end = 8.dp)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(
            Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                "Un seul fichier chiffré (AES-256-GCM) avec toutes tes données et photos. Sans le mot de passe, personne ne peut le lire — pas même toi.",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            BackupCard("Exporter", Icons.Rounded.Upload) {
                PasswordField(exportPassword, { exportPassword = it }, "Mot de passe")
                PasswordField(exportConfirm, { exportConfirm = it }, "Confirmer le mot de passe")
                val valid = exportPassword.length >= MIN_PASSWORD && exportPassword == exportConfirm
                Text(
                    when {
                        exportPassword.isNotEmpty() && exportPassword.length < MIN_PASSWORD -> "Au moins $MIN_PASSWORD caractères"
                        exportConfirm.isNotEmpty() && exportPassword != exportConfirm -> "Les mots de passe ne correspondent pas"
                        else -> "Garde ce mot de passe précieusement."
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = if (valid || exportPassword.isEmpty()) MaterialTheme.colorScheme.onSurfaceVariant else MaterialTheme.colorScheme.error,
                )
                Button(
                    onClick = { exporter.launch("ruban-${LocalDate.now()}.ruban") },
                    enabled = valid && !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choisir où enregistrer") }
            }
            BackupCard("Importer", Icons.Rounded.Download) {
                Text("Remplace les données actuelles par celles d'un fichier .ruban.", style = MaterialTheme.typography.bodyMedium)
                FilledTonalButton(
                    onClick = { importer.launch(arrayOf("*/*")) },
                    enabled = !busy,
                    shapes = ButtonDefaults.shapes(),
                    modifier = Modifier.fillMaxWidth(),
                ) { Text("Choisir un fichier") }
            }
            BackupCard("Sauvegarde automatique", Icons.Rounded.FolderOpen) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Chaque semaine", style = MaterialTheme.typography.titleMedium)
                        Text(
                            settings.autoBackupFolder?.let { DocumentFile.fromTreeUri(context, Uri.parse(it))?.name }?.let { "Dans « $it »" }
                                ?: "Dans un dossier de ton choix (4 dernières gardées)",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Switch(settings.autoBackup, { enable ->
                        if (enable) folderPicker.launch(null)
                        else scope.launch {
                            c.settings.setAutoBackup(false, null, null)
                            WorkScheduler.setAutoBackup(context, false)
                        }
                    })
                }
                if (settings.autoBackup) {
                    Text(
                        if (settings.lastAutoBackupAt > 0) "Dernière : ${Dates.fullMillis(settings.lastAutoBackupAt)}" else "Pas encore de sauvegarde",
                        style = MaterialTheme.typography.bodySmall,
                    )
                    FilledTonalButton(
                        onClick = {
                            busy = true
                            scope.launch {
                                val ok = runCatching { c.backup.autoBackup() }.getOrDefault(false)
                                report(if (ok) "Sauvegarde effectuée" else "Sauvegarde impossible : vérifie le dossier")
                                busy = false
                            }
                        },
                        enabled = !busy,
                        shapes = ButtonDefaults.shapes(),
                    ) { Text("Sauvegarder maintenant") }
                }
            }
            Spacer(Modifier.height(32.dp))
        }
    }
}

@Composable
private fun BackupCard(title: String, icon: androidx.compose.ui.graphics.vector.ImageVector, content: @Composable ColumnScope.() -> Unit) {
    Surface(shape = MaterialTheme.shapes.extraLarge, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleLarge)
            }
            content()
        }
    }
}

@Composable
private fun PasswordField(value: String, onChange: (String) -> Unit, label: String) {
    var visible by remember { mutableStateOf(false) }
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(label) },
        singleLine = true,
        modifier = Modifier.fillMaxWidth(),
        shape = MaterialTheme.shapes.medium,
        leadingIcon = { Icon(Icons.Rounded.Lock, null) },
        trailingIcon = {
            IconButton(onClick = { visible = !visible }) {
                Icon(if (visible) Icons.Rounded.VisibilityOff else Icons.Rounded.Visibility, if (visible) "Masquer" else "Afficher")
            }
        },
        visualTransformation = if (visible) VisualTransformation.None else PasswordVisualTransformation(),
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
    )
}

@Composable
private fun PasswordDialog(
    title: String,
    text: String,
    confirm: String,
    requireConfirm: Boolean = false,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
) {
    var password by remember { mutableStateOf("") }
    var again by remember { mutableStateOf("") }
    val valid = password.length >= MIN_PASSWORD && (!requireConfirm || password == again)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text(text)
                PasswordField(password, { password = it }, "Mot de passe")
                if (requireConfirm) PasswordField(again, { again = it }, "Confirmer")
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(password) }, enabled = valid) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Annuler") } },
    )
}
