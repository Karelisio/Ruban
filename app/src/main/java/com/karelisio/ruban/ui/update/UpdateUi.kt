package com.karelisio.ruban.ui.update

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.LinearWavyProgressIndicator
import androidx.compose.material3.LoadingIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.karelisio.ruban.BuildConfig
import com.karelisio.ruban.container
import com.karelisio.ruban.data.update.UpdateInfo
import com.karelisio.ruban.data.update.UpdateState
import com.karelisio.ruban.ui.components.AnimatedRibbonKnot
import com.karelisio.ruban.ui.components.RollingText
import com.karelisio.ruban.ui.permissions.AppPermission
import com.karelisio.ruban.ui.permissions.PermissionExplainer
import com.karelisio.ruban.ui.permissions.Permissions
import com.karelisio.ruban.ui.screens.settings.SettingsItem
import kotlinx.coroutines.launch

/** Bottom sheet driven by the update state: changelog, animated download, install. */
@Composable
fun UpdateSheetHost() {
    val context = LocalContext.current
    val c = context.container
    val state by c.updates.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    var needsPermission by remember { mutableStateOf(false) }

    val info: UpdateInfo = when (val s = state) {
        is UpdateState.Available -> s.info
        is UpdateState.Downloading -> s.info
        is UpdateState.Installing -> s.info
        is UpdateState.Failed -> s.info ?: return
        else -> return
    }
    val settings by c.settings.settings.collectAsStateWithLifecycle(null)
    if (state is UpdateState.Available && settings?.dismissedUpdate == info.version) return

    fun start() {
        if (!Permissions.isGranted(context, AppPermission.INSTALL_APKS)) {
            needsPermission = true
            return
        }
        scope.launch { c.updates.downloadAndInstall(info) }
    }

    ModalBottomSheet(onDismissRequest = {
        if (state is UpdateState.Available) scope.launch { c.settings.setDismissedUpdate(info.version) }
        c.updates.dismiss()
    }) {
        if (needsPermission) {
            PermissionExplainer(AppPermission.INSTALL_APKS, onGranted = { needsPermission = false; start() }, onLater = { needsPermission = false })
            return@ModalBottomSheet
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                AnimatedRibbonKnot(Modifier.size(64.dp))
                Spacer(Modifier.width(12.dp))
                Column {
                    Text("Ruban ${info.version}", style = MaterialTheme.typography.headlineSmall)
                    Text("Tu as la ${BuildConfig.VERSION_NAME}", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (info.changelog.isNotBlank()) {
                Column(Modifier.heightIn(max = 280.dp).verticalScroll(rememberScrollState())) {
                    info.changelog.lines().filter { it.isNotBlank() }.forEach { line ->
                        val clean = line.trim().removePrefix("#").trim()
                        val bullet = clean.startsWith("- ") || clean.startsWith("* ")
                        Text(
                            if (bullet) "•  " + clean.drop(2) else clean,
                            style = if (line.trim().startsWith("#")) MaterialTheme.typography.titleSmall else MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.padding(vertical = 2.dp),
                        )
                    }
                }
            }
            AnimatedContent(state, transitionSpec = { fadeIn() togetherWith fadeOut() }, contentKey = { it::class }, label = "update") { s ->
                when (s) {
                    is UpdateState.Downloading -> Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        LinearWavyProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text("Téléchargement… ", style = MaterialTheme.typography.bodyMedium)
                            RollingText("${(s.progress * 100).toInt()} %", style = MaterialTheme.typography.titleMedium)
                        }
                    }
                    is UpdateState.Installing -> Row(verticalAlignment = Alignment.CenterVertically) {
                        LoadingIndicator(Modifier.size(40.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Installation…", style = MaterialTheme.typography.bodyMedium)
                    }
                    else -> Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (s is UpdateState.Failed) Text(s.message, color = MaterialTheme.colorScheme.error, style = MaterialTheme.typography.bodyMedium)
                        Button(onClick = ::start, shapes = ButtonDefaults.shapes(), modifier = Modifier.fillMaxWidth().height(56.dp)) {
                            Text(if (s is UpdateState.Failed) "Réessayer" else "Mettre à jour")
                        }
                        TextButton(
                            onClick = {
                                scope.launch { c.settings.setDismissedUpdate(info.version) }
                                c.updates.dismiss()
                            },
                            modifier = Modifier.fillMaxWidth(),
                        ) { Text("Plus tard") }
                    }
                }
            }
        }
    }
}

@Composable
fun UpdateSettingsItem(first: Boolean, last: Boolean) {
    val context = LocalContext.current
    val c = context.container
    val state by c.updates.state.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    SettingsItem(
        first, last, Icons.Rounded.SystemUpdate, "Mises à jour",
        when (state) {
            UpdateState.Checking -> "Recherche…"
            UpdateState.UpToDate -> "Ruban est à jour"
            is UpdateState.Available -> "Nouvelle version disponible"
            is UpdateState.Downloading -> "Téléchargement en cours"
            is UpdateState.Failed -> (state as UpdateState.Failed).message
            else -> "Rechercher une nouvelle version"
        },
        11,
        onClick = {
            scope.launch {
                c.settings.setDismissedUpdate(null)
                c.updates.check(force = true)
            }
        },
    ) {
        if (state == UpdateState.Checking) Box(Modifier.size(36.dp)) { LoadingIndicator() }
    }
}
