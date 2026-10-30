package com.karelisio.ruban.ui.screens.gift

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.graphics.shapes.Morph
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.ui.components.emoji
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.ui.theme.MorphShape

/** idée → achetée → emballée → offerte, each step a shape that blooms when reached. */
@Composable
fun StatusStepper(status: GiftStatus, onSelect: (GiftStatus) -> Unit, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val progress by animateFloatAsState(
        status.ordinal / (GiftStatus.entries.size - 1f),
        spring(0.75f, Spring.StiffnessLow),
        label = "track",
    )
    Box(modifier.fillMaxWidth()) {
        // Track behind the nodes.
        Canvas(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 40.dp)) {
            val y = size.height / 2
            val w = 6.dp.toPx()
            drawLine(c.surfaceContainerHighest, Offset(0f, y), Offset(size.width, y), w, StrokeCap.Round)
            drawLine(c.primary, Offset(0f, y), Offset(size.width * progress, y), w, StrokeCap.Round)
        }
        Row(Modifier.fillMaxWidth()) {
            GiftStatus.entries.forEach { s ->
                StepNode(s, reached = s <= status, current = s == status, onClick = { onSelect(s) }, modifier = Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun StepNode(step: GiftStatus, reached: Boolean, current: Boolean, onClick: () -> Unit, modifier: Modifier) {
    val c = MaterialTheme.colorScheme
    val haptic = LocalHapticFeedback.current
    val reduce = LocalReduceMotion.current
    val morph = remember { Morph(MaterialShapes.Circle, MaterialShapes.Cookie9Sided) }
    val bloom = remember { Animatable(if (current) 1f else 0f) }
    LaunchedEffect(current) { bloom.animateTo(if (current) 1f else 0f, spring(0.4f, Spring.StiffnessLow)) }
    val scale by animateFloatAsState(if (current) 1.18f else if (reached) 1f else 0.86f, spring(0.45f, Spring.StiffnessMediumLow), label = "scale")
    val bg by animateColorAsState(if (reached) c.primary else c.surfaceContainerHighest, label = "bg")
    val fg by animateColorAsState(if (reached) c.onPrimary else c.onSurfaceVariant, label = "fg")
    val spin = if (current && !reduce) {
        rememberInfiniteTransition(label = "spin").animateFloat(0f, 360f, infiniteRepeatable(tween(12_000, easing = LinearEasing)), label = "r").value
    } else 0f
    val interaction = remember { MutableInteractionSource() }
    Column(
        modifier.clickable(interaction, indication = null) {
            haptic.performHapticFeedback(if (current) HapticFeedbackType.ContextClick else HapticFeedbackType.ToggleOn)
            onClick()
        },
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(56.dp).graphicsLayer { scaleX = scale; scaleY = scale }, contentAlignment = Alignment.Center) {
            Box(
                Modifier
                    .size(48.dp)
                    .graphicsLayer { rotationZ = spin }
                    .clip(MorphShape(morph, bloom.value))
                    .background(bg),
            )
            Text(step.emoji, fontSize = 20.sp, color = fg)
        }
        Spacer(Modifier.height(4.dp))
        Text(
            step.label,
            style = MaterialTheme.typography.labelMedium,
            color = if (current) c.primary else c.onSurfaceVariant,
        )
    }
}
