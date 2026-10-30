package com.karelisio.ruban.ui.screens.gift

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.translate
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.util.Vibes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlin.math.cos
import kotlin.math.sin
import kotlin.random.Random

private class Confetto(
    val angle: Float,
    val speed: Float,
    val spin: Float,
    val color: Color,
    val w: Float,
    val h: Float,
    val round: Boolean,
    val drift: Float,
)

/**
 * Signature animation #3 ("le déballage"): the bow unties, the lid jumps off
 * and discreet confetti in the theme colours rain down, with a small vibration.
 */
@Composable
fun UnwrapCelebration(onFinished: () -> Unit, modifier: Modifier = Modifier) {
    val reduce = LocalReduceMotion.current
    val haptic = LocalHapticFeedback.current
    val context = androidx.compose.ui.platform.LocalContext.current
    val c = MaterialTheme.colorScheme
    val untie = remember { Animatable(0f) }
    val lid = remember { Animatable(0f) }
    val burst = remember { Animatable(0f) }
    val fade = remember { Animatable(1f) }
    val appear = remember { Animatable(0f) }
    val palette = listOf(c.primary, c.tertiary, c.secondary, c.primaryContainer, c.tertiaryContainer)
    val confetti = remember {
        val rnd = Random(42)
        List(70) {
            Confetto(
                angle = (-90f + (rnd.nextFloat() - 0.5f) * 140f),
                speed = 0.55f + rnd.nextFloat() * 0.75f,
                spin = (rnd.nextFloat() - 0.5f) * 900f,
                color = palette[it % palette.size],
                w = 5f + rnd.nextFloat() * 6f,
                h = 8f + rnd.nextFloat() * 10f,
                round = rnd.nextFloat() < 0.3f,
                drift = (rnd.nextFloat() - 0.5f) * 0.4f,
            )
        }
    }

    LaunchedEffect(Unit) {
        if (reduce) {
            haptic.performHapticFeedback(HapticFeedbackType.Confirm)
            onFinished()
            return@LaunchedEffect
        }
        appear.animateTo(1f, spring(0.55f, Spring.StiffnessMediumLow))
        untie.animateTo(1f, tween(520, easing = FastOutSlowInEasing))
        Vibes.unwrap(context)
        coroutineScope {
            launch { lid.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
            launch { burst.animateTo(1f, tween(2200, easing = LinearEasing)) }
            launch {
                delay(1700)
                fade.animateTo(0f, tween(600))
            }
        }
        onFinished()
    }

    Canvas(modifier.fillMaxSize()) {
        val alpha = fade.value
        val center = Offset(size.width / 2, size.height * 0.42f)
        val box = 120.dp.toPx() * (0.6f + 0.4f * appear.value)
        drawRect(Color.Black.copy(alpha = 0.25f * alpha * appear.value))

        // Confetti: burst out of the box, then fall softly.
        if (burst.value > 0f) {
            val t = burst.value * 2.2f
            confetti.forEach { p ->
                val rad = Math.toRadians(p.angle.toDouble())
                val v = p.speed * size.height * 0.9f
                val x = center.x + cos(rad).toFloat() * v * t * 0.6f + p.drift * size.width * t * 0.3f
                val y = center.y - box * 0.3f + sin(rad).toFloat() * v * t * 0.6f + 0.5f * size.height * 0.9f * t * t
                if (y > size.height + 40f) return@forEach
                rotate(p.spin * t, Offset(x, y)) {
                    val col = p.color.copy(alpha = alpha)
                    if (p.round) drawCircle(col, p.w * 0.7f, Offset(x, y))
                    else drawRect(col, Offset(x - p.w / 2, y - p.h / 2), Size(p.w, p.h))
                }
            }
        }

        // Box body.
        val bodyTop = center.y - box * 0.25f
        drawRoundRect(
            lerp(c.primary, c.primaryContainer, 0.15f).copy(alpha = alpha),
            Offset(center.x - box / 2, bodyTop),
            Size(box, box * 0.8f),
            CornerRadius(box * 0.12f),
        )
        // Vertical ribbon on the body (retracts while untying).
        val ribbonW = box * 0.16f
        val bodyRibbonH = box * 0.8f * (1f - untie.value)
        drawRect(c.tertiary.copy(alpha = alpha), Offset(center.x - ribbonW / 2, bodyTop), Size(ribbonW, bodyRibbonH))

        // Lid, jumping off and spinning away.
        val lidLift = lid.value
        translate(
            left = lidLift * box * 0.9f,
            top = -lidLift * size.height * 0.35f + lidLift * lidLift * size.height * 0.25f,
        ) {
            rotate(lidLift * 38f, Offset(center.x, bodyTop)) {
                drawLid(center.x, bodyTop, box, c.primary.copy(alpha = alpha), c.tertiary.copy(alpha = alpha), untie.value)
            }
        }
    }
}

private fun DrawScope.drawLid(cx: Float, bodyTop: Float, box: Float, color: Color, ribbon: Color, untie: Float) {
    val lidH = box * 0.24f
    val lidW = box * 1.12f
    drawRoundRect(color, Offset(cx - lidW / 2, bodyTop - lidH), Size(lidW, lidH), CornerRadius(box * 0.08f))
    val ribbonW = box * 0.16f
    drawRect(ribbon, Offset(cx - ribbonW / 2, bodyTop - lidH), Size(ribbonW, lidH * (1f - untie)))
    // Bow loosening: loops shrink and rotate open.
    if (untie < 1f) {
        val bowSize = box * 0.22f * (1f - untie)
        val center = Offset(cx, bodyTop - lidH)
        rotate(-25f * untie, center) {
            drawBow(center, bowSize, 1f, ribbon, lerp(ribbon, Color.White, 0.35f))
        }
    }
}
