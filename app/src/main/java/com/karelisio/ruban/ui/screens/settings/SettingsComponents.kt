package com.karelisio.ruban.ui.screens.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.ui.components.ShapeBadge

class SettingsGroupScope {
    internal val items = mutableListOf<@Composable (Boolean, Boolean) -> Unit>()

    fun item(content: @Composable (first: Boolean, last: Boolean) -> Unit) {
        items += content
    }
}

/** Expressive segmented list: items in one group, large outer and small inner corners. */
@Composable
fun SettingsGroup(title: String, modifier: Modifier = Modifier, content: SettingsGroupScope.() -> Unit) {
    val scope = SettingsGroupScope().apply(content)
    Column(modifier.padding(horizontal = 16.dp)) {
        Text(title, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(start = 8.dp, top = 20.dp, bottom = 8.dp))
        Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
            scope.items.forEachIndexed { i, item -> item(i == 0, i == scope.items.lastIndex) }
        }
    }
}

private fun groupShape(first: Boolean, last: Boolean) = RoundedCornerShape(
    topStart = if (first) 24.dp else 6.dp,
    topEnd = if (first) 24.dp else 6.dp,
    bottomStart = if (last) 24.dp else 6.dp,
    bottomEnd = if (last) 24.dp else 6.dp,
)

@Composable
fun SettingsItem(
    first: Boolean,
    last: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String? = null,
    iconIndex: Int = 0,
    onClick: (() -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
) {
    Surface(
        Modifier.fillMaxWidth().clip(groupShape(first, last)).then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier),
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 14.dp), verticalAlignment = Alignment.CenterVertically) {
            ShapeBadge(size = 40.dp, polygonIndex = iconIndex, container = MaterialTheme.colorScheme.secondaryContainer) {
                Icon(icon, null, Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSecondaryContainer)
            }
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium)
                if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if (trailing != null) {
                Spacer(Modifier.width(8.dp))
                Box { trailing() }
            }
        }
    }
}

@Composable
fun SettingsSwitch(
    first: Boolean,
    last: Boolean,
    icon: ImageVector,
    title: String,
    subtitle: String?,
    checked: Boolean,
    iconIndex: Int = 0,
    onChange: (Boolean) -> Unit,
) {
    val haptic = LocalHapticFeedback.current
    val toggle = { v: Boolean ->
        haptic.performHapticFeedback(if (v) HapticFeedbackType.ToggleOn else HapticFeedbackType.ToggleOff)
        onChange(v)
    }
    SettingsItem(first, last, icon, title, subtitle, iconIndex, onClick = { toggle(!checked) }) {
        Switch(checked, { toggle(it) })
    }
}
