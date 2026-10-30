package com.karelisio.ruban.ui.screens.settings

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.ui.components.ShapeBadge
import com.karelisio.ruban.ui.components.cascade
import com.karelisio.ruban.ui.nav.LocalNav
import com.karelisio.ruban.ui.permissions.AppPermission
import com.karelisio.ruban.ui.permissions.DrawnCheck
import com.karelisio.ruban.ui.permissions.rememberPermissionState

/** Every permission with its live state (refreshed when coming back from system settings). */
@Composable
fun PermissionsScreen() {
    val nav = LocalNav.current
    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Autorisations") },
                navigationIcon = { IconButton(onClick = nav::back) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Retour") } },
            )
        },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp, start = 16.dp, end = 16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            item {
                Text(
                    "Ruban ne demande que ce qui sert. Tu peux changer d'avis à tout moment.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            items(AppPermission.entries.size) { i -> PermissionRow(AppPermission.entries[i], Modifier.cascade(i)) }
        }
    }
}

@Composable
private fun PermissionRow(permission: AppPermission, modifier: Modifier) {
    val state = rememberPermissionState(permission)
    Surface(modifier.fillMaxWidth(), shape = MaterialTheme.shapes.large, color = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                ShapeBadge(
                    size = 44.dp,
                    polygonIndex = permission.ordinal,
                    container = if (state.granted) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.secondaryContainer,
                ) { Icon(permission.icon, null, Modifier.size(22.dp)) }
                Spacer(Modifier.width(14.dp))
                Text(permission.title, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                AnimatedContent(
                    state.granted,
                    transitionSpec = { scaleIn(spring(0.45f, Spring.StiffnessMediumLow)) + fadeIn() togetherWith fadeOut() },
                    label = "granted",
                ) { granted ->
                    if (granted) DrawnCheck(MaterialTheme.colorScheme.tertiary, Modifier.size(32.dp))
                    else FilledTonalButton(onClick = state::request, shapes = ButtonDefaults.shapes()) {
                        Text(if (permission.viaSettings) "Réglages" else "Autoriser")
                    }
                }
            }
            Text(
                permission.why,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp),
            )
        }
    }
}
