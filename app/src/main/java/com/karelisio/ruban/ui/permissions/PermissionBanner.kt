package com.karelisio.ruban.ui.permissions

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

/** Small warning shown where a feature is limited by a refused permission; opens the animated explainer. */
@Composable
fun PermissionBanner(permission: AppPermission, message: String, modifier: Modifier = Modifier) {
    val state = rememberPermissionState(permission)
    var sheet by rememberSaveable { mutableStateOf(false) }
    if (sheet) {
        ModalBottomSheet(onDismissRequest = { sheet = false }) {
            PermissionExplainer(permission, onGranted = { sheet = false }, onLater = { sheet = false })
        }
    }
    AnimatedVisibility(!state.granted, modifier, enter = expandVertically() + fadeIn(), exit = shrinkVertically() + fadeOut()) {
        Surface(
            onClick = { sheet = true },
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.errorContainer,
        ) {
            Row(Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(permission.icon, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                Spacer(Modifier.width(12.dp))
                Text(message, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                Text("Activer", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}
