package com.karelisio.ruban.ui.screens.settings

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Backup
import androidx.compose.material.icons.rounded.Fingerprint
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.NotificationsOff
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Redeem
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Shield
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeFlexibleTopAppBar
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.material3.rememberTopAppBarState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.BuildConfig
import com.karelisio.ruban.data.settings.Settings
import com.karelisio.ruban.rubanViewModel
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.nav.Route
import com.karelisio.ruban.ui.permissions.findActivity
import com.karelisio.ruban.ui.screens.events.offsetChoices
import com.karelisio.ruban.ui.screens.events.offsetLabel
import com.karelisio.ruban.ui.security.Biometric
import com.karelisio.ruban.ui.update.UpdateSettingsItem
import com.karelisio.ruban.util.Dates
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class SettingsViewModel(private val c: AppContainer) : ViewModel() {
    val settings = c.settings.settings.stateIn(viewModelScope, SharingStarted.Eagerly, Settings())
    fun run(block: suspend com.karelisio.ruban.data.settings.SettingsRepository.() -> Unit) = viewModelScope.launch { c.settings.block() }
}

@Composable
fun SettingsScreen() {
    val vm = rubanViewModel { SettingsViewModel(this) }
    val s by vm.settings.collectAsStateWithLifecycle()
    val nav = LocalNav.current
    val context = LocalContext.current
    val snackbar = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior(rememberTopAppBarState())
    var pickTime by rememberSaveable { mutableStateOf(false) }

    if (pickTime) {
        val state = rememberTimePickerState(s.reminderHour, s.reminderMinute, is24Hour = true)
        androidx.compose.material3.AlertDialog(
            onDismissRequest = { pickTime = false },
            title = { Text("Heure des rappels") },
            text = { TimePicker(state) },
            confirmButton = { TextButton(onClick = { vm.run { setReminderTime(state.hour, state.minute) }; pickTime = false }) { Text("OK") } },
            dismissButton = { TextButton(onClick = { pickTime = false }) { Text("Annuler") } },
        )
    }

    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeFlexibleTopAppBar(
                title = { Text("Réglages") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
                scrollBehavior = scroll,
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        Column(Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            SettingsGroup("Apparence") {
                item { f, l ->
                    SettingsSwitch(f, l, Icons.Rounded.Palette, "Couleurs de mon fond d'écran", "Sinon : framboise et touche dorée", s.dynamicColor, 0) {
                        vm.run { setDynamicColor(it) }
                    }
                }
            }

            SettingsGroup("Rappels") {
                item { f, l ->
                    SettingsItem(f, l, Icons.Rounded.Schedule, "Heure des rappels", "%02d:%02d".format(s.reminderHour, s.reminderMinute), 1, onClick = { pickTime = true })
                }
                item { f, l ->
                    SettingsItem(f, l, Icons.Rounded.Schedule, "Rappels par défaut", "Modifiables pour chaque événement", 2)
                }
                item { _, _ ->
                    FlowRow(Modifier.padding(horizontal = 8.dp, vertical = 6.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        offsetChoices.forEach { d ->
                            val on = d in s.reminderOffsets
                            FilterChip(on, { vm.run { setReminderOffsets(if (on) s.reminderOffsets - d else s.reminderOffsets + d) } }, { Text(offsetLabel(d)) })
                        }
                    }
                }
            }

            SettingsGroup("Prix") {
                item { f, l ->
                    SettingsSwitch(
                        f, l, Icons.AutoMirrored.Rounded.TrendingDown, "Alertes de baisse de prix",
                        if (s.lastPriceCheckAt > 0) "Dernière vérification : ${Dates.fullMillis(s.lastPriceCheckAt)}" else "Vérification toutes les 24 h",
                        s.priceAlerts, 3,
                    ) { vm.run { setPriceAlerts(it) } }
                }
            }

            SettingsGroup("Discrétion") {
                item { f, l ->
                    SettingsSwitch(f, l, Icons.Rounded.Fingerprint, "Verrouiller Ruban", "Empreinte, visage ou code de l'appareil", s.biometricLock, 4) { enable ->
                        val activity = context.findActivity() as? FragmentActivity ?: return@SettingsSwitch
                        if (!Biometric.available(context)) {
                            scope.launch { snackbar.showSnackbar("Configure d'abord un verrouillage d'écran sur le téléphone.") }
                            return@SettingsSwitch
                        }
                        Biometric.authenticate(
                            activity,
                            title = if (enable) "Activer le verrou" else "Désactiver le verrou",
                            onSuccess = { vm.run { setBiometricLock(enable) } },
                        )
                    }
                }
                item { f, l ->
                    SettingsSwitch(f, l, Icons.Rounded.NotificationsOff, "Notifications neutres", "« Rappel Ruban », sans nom de cadeau", s.neutralNotifications, 5) {
                        vm.run { setNeutralNotifications(it) }
                    }
                }
                item { f, l ->
                    SettingsSwitch(f, l, Icons.Rounded.VisibilityOff, "Masquer l'aperçu", "Écran caché dans les applis récentes, captures bloquées", s.secureScreen, 6) {
                        vm.run { setSecureScreen(it) }
                    }
                }
            }

            SettingsGroup("Données") {
                item { f, l -> SettingsItem(f, l, Icons.Rounded.Backup, "Sauvegarde chiffrée", "Exporter, importer, sauvegarde automatique", 7, onClick = { nav.go(Route.Backup) }) }
                item { f, l -> SettingsItem(f, l, Icons.Rounded.Redeem, "Cadeaux reçus", "Qui m'a offert quoi", 8, onClick = { nav.go(Route.Received) }) }
                item { f, l -> SettingsItem(f, l, Icons.Rounded.Shield, "Autorisations", "État de chaque autorisation", 9, onClick = { nav.go(Route.Permissions) }) }
            }

            SettingsGroup("Ruban") {
                item { f, l -> UpdateSettingsItem(f, l) }
                item { f, l ->
                    SettingsItem(
                        f, l, Icons.Rounded.Info, "Version ${BuildConfig.VERSION_NAME}",
                        "100 % local : sans compte, sans pub, sans pistage. Polices Fraunces et Outfit (OFL).", 10,
                    )
                }
            }
            Spacer(Modifier.height(48.dp))
        }
    }
}
