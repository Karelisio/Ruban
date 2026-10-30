package com.karelisio.ruban.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExposedDropdownMenuAnchorType
import androidx.compose.material3.ExposedDropdownMenu
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.InputChip
import androidx.compose.material3.InputChipDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.domain.Interests
import com.karelisio.ruban.domain.TextMatch
import com.karelisio.ruban.util.Dates
import com.karelisio.ruban.util.Money

@Composable
fun SearchField(value: String, onValueChange: (String) -> Unit, placeholder: String, modifier: Modifier = Modifier) {
    TextField(
        value = value,
        onValueChange = onValueChange,
        modifier = modifier.fillMaxWidth(),
        placeholder = { Text(placeholder) },
        leadingIcon = { Icon(Icons.Rounded.Search, null) },
        trailingIcon = {
            AnimatedVisibility(value.isNotEmpty(), enter = scaleIn() + fadeIn(), exit = scaleOut() + fadeOut()) {
                IconButton(onClick = { onValueChange("") }) { Icon(Icons.Rounded.Close, "Effacer") }
            }
        },
        singleLine = true,
        shape = CircleShape,
        colors = TextFieldDefaults.colors(
            focusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedIndicatorColor = androidx.compose.ui.graphics.Color.Transparent,
            unfocusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            focusedContainerColor = MaterialTheme.colorScheme.surfaceContainerHighest,
        ),
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
    )
}

@Composable
fun FormField(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    singleLine: Boolean = true,
    minLines: Int = 1,
    keyboardType: KeyboardType = KeyboardType.Text,
    capitalization: KeyboardCapitalization = KeyboardCapitalization.Sentences,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    supporting: String? = null,
    placeholder: String? = null,
) {
    OutlinedTextField(
        value = value,
        onValueChange = onValueChange,
        label = { Text(label) },
        modifier = modifier.fillMaxWidth(),
        singleLine = singleLine,
        minLines = minLines,
        leadingIcon = leading,
        trailingIcon = trailing,
        placeholder = placeholder?.let { { Text(it) } },
        supportingText = supporting?.let { { Text(it) } },
        shape = MaterialTheme.shapes.medium,
        keyboardOptions = KeyboardOptions(keyboardType = keyboardType, capitalization = capitalization, imeAction = if (singleLine) ImeAction.Next else ImeAction.Default),
    )
}

@Composable
fun MoneyField(cents: Long?, onChange: (Long?) -> Unit, label: String, modifier: Modifier = Modifier, currency: String = "EUR", supporting: String? = null) {
    var text by rememberSaveable(cents == null) { mutableStateOf(Money.input(cents)) }
    FormField(
        value = text,
        onValueChange = {
            text = it.filter { ch -> ch.isDigit() || ch == ',' || ch == '.' }
            onChange(Money.parse(text))
        },
        label = label,
        modifier = modifier,
        keyboardType = KeyboardType.Decimal,
        trailing = { Text(Money.symbol(currency), Modifier.padding(end = 12.dp), style = MaterialTheme.typography.titleMedium) },
        supporting = supporting,
    )
}

/** Day + month dropdowns, with an optional year. */
@Composable
fun DayMonthField(
    label: String,
    month: Int?,
    day: Int?,
    onChange: (month: Int?, day: Int?) -> Unit,
    modifier: Modifier = Modifier,
    year: Int? = null,
    onYearChange: ((Int?) -> Unit)? = null,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(Modifier.height(6.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
            val maxDay = when (month) { 2 -> 29; 4, 6, 9, 11 -> 30; else -> 31 }
            Dropdown(
                value = day?.toString() ?: "Jour",
                options = (1..maxDay).map { it.toString() },
                onSelect = { onChange(month ?: 1, it.toInt()) },
                modifier = Modifier.weight(0.8f),
            )
            Dropdown(
                value = month?.let { Dates.monthName(it) } ?: "Mois",
                options = (1..12).map { Dates.monthName(it) },
                onSelect = { name ->
                    val m = (1..12).first { Dates.monthName(it) == name }
                    val maxD = when (m) { 2 -> 29; 4, 6, 9, 11 -> 30; else -> 31 }
                    onChange(m, (day ?: 1).coerceAtMost(maxD))
                },
                modifier = Modifier.weight(1.3f),
            )
            if (onYearChange != null) {
                var yearText by rememberSaveable { mutableStateOf(year?.toString() ?: "") }
                OutlinedTextField(
                    value = yearText,
                    onValueChange = { t ->
                        yearText = t.filter(Char::isDigit).take(4)
                        onYearChange(yearText.toIntOrNull()?.takeIf { it in 1900..2100 })
                    },
                    placeholder = { Text("Année") },
                    modifier = Modifier.weight(1f),
                    singleLine = true,
                    shape = MaterialTheme.shapes.medium,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                )
            }
            if (month != null || day != null) {
                IconButton(onClick = { onChange(null, null); onYearChange?.invoke(null) }) { Icon(Icons.Rounded.Close, "Effacer") }
            }
        }
    }
}

@Composable
fun Dropdown(value: String, options: List<String>, onSelect: (String) -> Unit, modifier: Modifier = Modifier) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(expanded, { expanded = it }, modifier) {
        OutlinedTextField(
            value = value,
            onValueChange = {},
            readOnly = true,
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier.menuAnchor(ExposedDropdownMenuAnchorType.PrimaryNotEditable).fillMaxWidth(),
        )
        ExposedDropdownMenu(expanded, { expanded = false }) {
            options.forEach { option ->
                DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false })
            }
        }
    }
}

/** Free tags with suggestions from the interest taxonomy. */
@Composable
fun TagInput(tags: List<String>, onChange: (List<String>) -> Unit, label: String, modifier: Modifier = Modifier) {
    var text by rememberSaveable { mutableStateOf("") }
    fun add(raw: String) {
        val t = raw.trim()
        if (t.isEmpty()) return
        val id = Interests.resolve(t)?.id ?: t
        if (tags.none { it.equals(id, true) }) onChange(tags + id)
        text = ""
    }
    Column(modifier) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            tags.forEach { tag ->
                InputChip(
                    selected = true,
                    onClick = { onChange(tags - tag) },
                    label = { Text("${Interests.emoji(tag)} ${Interests.label(tag)}") },
                    trailingIcon = { Icon(Icons.Rounded.Close, "Retirer", Modifier.size(InputChipDefaults.IconSize)) },
                )
            }
        }
        OutlinedTextField(
            value = text,
            onValueChange = { v -> if (v.endsWith(",")) add(v.dropLast(1)) else text = v },
            label = { Text(label) },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            shape = MaterialTheme.shapes.medium,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { add(text) }),
        )
        val suggestions = remember(text, tags) {
            Interests.all.filter { it.id !in tags && (text.isBlank() || TextMatch.normalize(it.label).contains(TextMatch.normalize(text))) }
                .take(if (text.isBlank()) 12 else 8)
        }
        Spacer(Modifier.height(6.dp))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            suggestions.forEach { s ->
                SuggestionChip(onClick = { add(s.id) }, label = { Text("${s.emoji} ${s.label}") })
            }
        }
    }
}

@Composable
fun Pill(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier
            .clip(CircleShape)
            .background(MaterialTheme.colorScheme.secondaryContainer)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSecondaryContainer,
    )
}
