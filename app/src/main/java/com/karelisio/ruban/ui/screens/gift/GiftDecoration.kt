package com.karelisio.ruban.ui.screens.gift

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Box
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.draw.drawWithContent
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.unit.dp
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import kotlin.math.hypot

/**
 * Signature animation #2 ("l'emballage"): when an idea becomes "bought" a ribbon wraps the card and
 * ties itself into a bow; once "wrapped" the card is dressed in a paper pattern in the theme colours.
 */
@Composable
fun GiftDecoration(
    status: GiftStatus,
    ideaId: Long,
    modifier: Modifier = Modifier,
    ribbonX: Float = 50f,
    content: @Composable () -> Unit,
) {
    val reduce = LocalReduceMotion.current
    val ribbonTarget = if (status == GiftStatus.BOUGHT || status == GiftStatus.WRAPPED) 1f else 0f
    val paperTarget = if (status == GiftStatus.WRAPPED) 1f else 0f
    val ribbon = remember(ideaId) { Animatable(ribbonTarget) }
    val bow = remember(ideaId) { Animatable(ribbonTarget) }
    val paper = remember(ideaId) { Animatable(paperTarget) }

    LaunchedEffect(ribbonTarget, reduce) {
        if (reduce) {
            ribbon.snapTo(ribbonTarget); bow.snapTo(ribbonTarget); return@LaunchedEffect
        }
        if (ribbonTarget == 1f && ribbon.value < 1f) {
            bow.snapTo(0f)
            ribbon.animateTo(1f, tween(750, easing = FastOutSlowInEasing))
            bow.animateTo(1f, spring(dampingRatio = 0.32f, stiffness = Spring.StiffnessMediumLow))
        } else if (ribbonTarget == 0f) {
            bow.animateTo(0f, tween(180))
            ribbon.animateTo(0f, tween(380))
        }
    }
    LaunchedEffect(paperTarget, reduce) {
        if (reduce) paper.snapTo(paperTarget) else paper.animateTo(paperTarget, tween(850, easing = FastOutSlowInEasing))
    }

    val colors = MaterialTheme.colorScheme
    Box(
        modifier
            .drawBehind {
                if (paper.value > 0f) drawWrappingPaper(paper.value, colors, ideaId)
                if (ribbon.value > 0f) drawRibbonBands(ribbon.value, colors, ribbonX.dp.toPx())
            }
            .drawWithContent {
                drawContent()
                if (bow.value > 0.01f) {
                    val y = 14.dp.toPx()
                    drawBow(Offset(ribbonX.dp.toPx(), y), 15.dp.toPx(), bow.value, colors.primary, androidx.compose.ui.graphics.lerp(colors.tertiary, colors.tertiaryContainer, 0.35f))
                }
            },
    ) { content() }
}

/** Paper revealed by a circular wipe from the top-left corner: stripes and dots in theme colours. */
fun DrawScope.drawWrappingPaper(progress: Float, colors: ColorScheme, seed: Long) {
    val radius = hypot(size.width, size.height) * progress
    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(Offset.Zero, radius)) }
    clipPath(circle) {
        drawRect(colors.primaryContainer.copy(alpha = 0.55f))
        val step = 26.dp.toPx()
        val stripe = 7.dp.toPx()
        val stripeColor = colors.primary.copy(alpha = 0.10f)
        rotate(-35f, Offset(size.width / 2, size.height / 2)) {
            var x = -size.width
            while (x < size.width * 2) {
                drawRect(stripeColor, Offset(x, -size.height), Size(stripe, size.height * 3))
                x += step
            }
        }
        val dot = colors.tertiary.copy(alpha = 0.35f)
        val r = 2.2.dp.toPx()
        var row = 0
        var y = step / 2
        while (y < size.height) {
            var x = if (row % 2 == 0) step / 2 else step
            x += (seed % 7).toFloat()
            while (x < size.width) {
                drawCircle(dot, r, Offset(x, y))
                x += step
            }
            y += step / 1.4f
            row++
        }
    }
}

/** Vertical band then horizontal band, drawn progressively. */
fun DrawScope.drawRibbonBands(progress: Float, colors: ColorScheme, x: Float) {
    val band = 9.dp.toPx()
    val color = colors.primary.copy(alpha = 0.85f)
    val v = (progress / 0.5f).coerceIn(0f, 1f)
    val h = ((progress - 0.5f) / 0.5f).coerceIn(0f, 1f)
    drawRect(color, Offset(x - band / 2, 0f), Size(band, size.height * v))
    if (h > 0f) {
        val y = size.height / 2
        drawRect(color.copy(alpha = 0.13f), Offset(0f, y - band / 2), Size(size.width * h, band))
    }
}

/** A bow: two rounded loops, a knot and two tails, popping with [scale]. */
fun DrawScope.drawBow(center: Offset, size: Float, scale: Float, ribbon: Color, knot: Color) {
    scale(scale, scale, pivot = center) {
        val stroke = Stroke(width = size * 0.26f, cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round)
        val loops = Path().apply {
            moveTo(center.x, center.y)
            cubicTo(center.x - size * 0.6f, center.y - size * 0.9f, center.x - size * 1.35f, center.y - size * 0.55f, center.x - size * 1.1f, center.y + size * 0.05f)
            cubicTo(center.x - size * 0.9f, center.y + size * 0.5f, center.x - size * 0.35f, center.y + size * 0.25f, center.x, center.y)
            cubicTo(center.x + size * 0.35f, center.y + size * 0.25f, center.x + size * 0.9f, center.y + size * 0.5f, center.x + size * 1.1f, center.y + size * 0.05f)
            cubicTo(center.x + size * 1.35f, center.y - size * 0.55f, center.x + size * 0.6f, center.y - size * 0.9f, center.x, center.y)
        }
        val tails = Path().apply {
            moveTo(center.x, center.y)
            quadraticTo(center.x - size * 0.3f, center.y + size * 0.6f, center.x - size * 0.6f, center.y + size * 1.05f)
            moveTo(center.x, center.y)
            quadraticTo(center.x + size * 0.3f, center.y + size * 0.6f, center.x + size * 0.6f, center.y + size * 1.05f)
        }
        drawPath(tails, ribbon, style = stroke)
        drawPath(loops, ribbon, style = stroke)
        drawCircle(knot, radius = size * 0.24f, center = center)
    }
}
