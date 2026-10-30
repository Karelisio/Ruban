package com.karelisio.ruban.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.LocalTextStyle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.util.Money
import kotlinx.coroutines.delay
import kotlin.math.roundToLong

/** Remembers when a list first appeared so only the initial items cascade in. */
class CascadeState {
    val startedAt: Long = System.currentTimeMillis()
}

val LocalCascade = staticCompositionLocalOf { CascadeState() }

/**
 * Staggered entrance: items fade and spring up one after the other when the screen opens.
 * Items composed later (while scrolling) appear without delay.
 */
@Composable
fun Modifier.cascade(index: Int, state: CascadeState = LocalCascade.current): Modifier {
    if (LocalReduceMotion.current) return this
    val age = System.currentTimeMillis() - state.startedAt
    val animate = remember { age < 700 && index < 14 }
    if (!animate) return this
    val progress = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        delay(index * 45L)
        progress.animateTo(1f, spring(dampingRatio = 0.72f, stiffness = Spring.StiffnessLow))
    }
    return graphicsLayer {
        val p = progress.value
        alpha = p.coerceIn(0f, 1f)
        translationY = (1f - p) * 56.dp.toPx()
        scaleX = 0.94f + 0.06f * p
        scaleY = 0.94f + 0.06f * p
    }
}

/** Springy squash when pressed. */
@Composable
fun Modifier.pressBounce(interaction: MutableInteractionSource, pressedScale: Float = 0.95f): Modifier {
    val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(
        if (pressed) pressedScale else 1f,
        spring(dampingRatio = 0.45f, stiffness = Spring.StiffnessMedium),
        label = "pressBounce",
    )
    return graphicsLayer { scaleX = scale; scaleY = scale }
}

/** Text whose characters roll vertically when they change (counters, countdowns). */
@Composable
fun RollingText(text: String, modifier: Modifier = Modifier, style: TextStyle = LocalTextStyle.current, color: Color = Color.Unspecified) {
    if (LocalReduceMotion.current) {
        Text(text, modifier, style = style, color = color)
        return
    }
    Row(modifier) {
        // Align from the end so that units keep their place when the number grows.
        text.forEachIndexed { index, char ->
            AnimatedContent(
                targetState = char,
                transitionSpec = {
                    val up = targetState > initialState
                    (slideInVertically(spring(0.8f, Spring.StiffnessMediumLow)) { if (up) it else -it } + fadeIn(tween(150)))
                        .togetherWith(slideOutVertically(spring(0.8f, Spring.StiffnessMediumLow)) { if (up) -it else it } + fadeOut(tween(120)))
                },
                label = "rolling-$index",
            ) { c -> Text(c.toString(), style = style, color = color) }
        }
    }
}

/** Animated money amount, counting up/down to [cents]. */
@Composable
fun AnimatedMoney(
    cents: Long,
    modifier: Modifier = Modifier,
    currency: String = "EUR",
    style: TextStyle = LocalTextStyle.current,
    color: Color = Color.Unspecified,
) {
    val reduce = LocalReduceMotion.current
    val anim = remember { Animatable(if (reduce) cents.toFloat() else 0f) }
    LaunchedEffect(cents, reduce) {
        if (reduce) anim.snapTo(cents.toFloat())
        else anim.animateTo(cents.toFloat(), spring(dampingRatio = 1f, stiffness = Spring.StiffnessVeryLow))
    }
    Text(Money.format((anim.value / 100f).roundToLong() * 100, currency), modifier, style = style, color = color)
}

/** Value that ticks every [periodMs], for live countdowns. */
@Composable
fun rememberNow(periodMs: Long = 1_000): Long {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(periodMs) {
        while (true) {
            delay(periodMs - System.currentTimeMillis() % periodMs)
            now = System.currentTimeMillis()
        }
    }
    return now
}

object Haptics {
    @Composable
    fun rememberPerformer(): (HapticFeedbackType) -> Unit {
        val haptic = LocalHapticFeedback.current
        return remember(haptic) { { type -> haptic.performHapticFeedback(type) } }
    }
}
