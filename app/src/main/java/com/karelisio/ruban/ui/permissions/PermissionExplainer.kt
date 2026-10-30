package com.karelisio.ruban.ui.permissions

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.ui.theme.MorphShape

/** Icon floating in a slowly turning scalloped shape; morphs into a drawn check mark once [done]. */
@Composable
fun AnimatedPermissionIcon(icon: ImageVector, done: Boolean, modifier: Modifier = Modifier, size: Dp = 132.dp) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "permIcon")
    val spin by t.animateFloat(0f, 360f, infiniteRepeatable(tween(14_000, easing = LinearEasing)), label = "spin")
    val bob by t.animateFloat(-1f, 1f, infiniteRepeatable(tween(1800, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "bob")
    val morph = remember { androidx.graphics.shapes.Morph(MaterialShapes.Cookie9Sided, MaterialShapes.Circle) }
    val morphProgress = remember { Animatable(if (done) 1f else 0f) }
    LaunchedEffect(done) { morphProgress.animateTo(if (done) 1f else 0f, spring(0.6f, Spring.StiffnessLow)) }
    val c = MaterialTheme.colorScheme
    val container = if (done) c.tertiaryContainer else c.primaryContainer
    Box(
        modifier.size(size).graphicsLayer { if (!reduce) translationY = bob * 6.dp.toPx() },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { if (!reduce) rotationZ = spin }
                .clip(MorphShape(morph, morphProgress.value))
                .background(container),
        )
        AnimatedContent(
            done,
            transitionSpec = { scaleIn(spring(0.45f, Spring.StiffnessMediumLow)) + fadeIn() togetherWith scaleOut() + fadeOut() },
            label = "permCheck",
        ) { granted ->
            if (granted) DrawnCheck(c.onTertiaryContainer, Modifier.size(size * 0.45f))
            else Icon(icon, null, Modifier.size(size * 0.4f), tint = c.onPrimaryContainer)
        }
    }
}

/** Check mark stroked progressively. */
@Composable
fun DrawnCheck(color: Color, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val p = remember { Animatable(if (reduce) 1f else 0f) }
    val haptic = LocalHapticFeedback.current
    LaunchedEffect(Unit) {
        haptic.performHapticFeedback(HapticFeedbackType.Confirm)
        p.animateTo(1f, tween(420, delayMillis = 80, easing = FastOutSlowInEasing))
    }
    Canvas(modifier) {
        val path = Path().apply {
            moveTo(size.width * 0.18f, size.height * 0.52f)
            lineTo(size.width * 0.42f, size.height * 0.75f)
            lineTo(size.width * 0.84f, size.height * 0.28f)
        }
        val m = PathMeasure().apply { setPath(path, false) }
        val seg = Path()
        m.getSegment(0f, m.length * p.value, seg, true)
        drawPath(seg, color, style = Stroke(size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
    }
}

/**
 * Explains why a feature needs [permission], with a button to grant it.
 * Shown inline whenever a feature hits a refused permission.
 */
@Composable
fun PermissionExplainer(
    permission: AppPermission,
    modifier: Modifier = Modifier,
    onGranted: () -> Unit = {},
    onLater: (() -> Unit)? = null,
) {
    val state = rememberPermissionState(permission) { if (it) onGranted() }
    Column(
        modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        AnimatedPermissionIcon(permission.icon, state.granted)
        Spacer(Modifier.height(8.dp))
        Text(permission.title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Text(
            permission.why,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(4.dp))
        if (!state.granted) {
            Button(onClick = state::request, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 28.dp, vertical = 14.dp)) {
                Text(if (permission.viaSettings) "Ouvrir les réglages" else "Autoriser")
            }
            onLater?.let { TextButton(onClick = it) { Text("Plus tard") } }
        } else {
            Text("C'est tout bon ✨", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.tertiary)
        }
    }
}

