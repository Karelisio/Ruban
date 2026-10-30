package com.karelisio.ruban.ui.update

import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.runtime.Composable
import com.karelisio.ruban.ui.screens.settings.SettingsItem

@Composable
fun UpdateSettingsItem(first: Boolean, last: Boolean) {
    SettingsItem(first, last, Icons.Rounded.SystemUpdate, "Mises à jour", "Bientôt", 11)
}
