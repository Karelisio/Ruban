package com.karelisio.ruban.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipRect
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import com.karelisio.ruban.ui.theme.LocalReduceMotion

/** Smooth line chart that draws itself in. */
@Composable
fun LineChart(
    values: List<Float>,
    modifier: Modifier = Modifier,
    color: Color,
    strokeWidth: Dp = 3.dp,
    fill: Boolean = false,
    showLastDot: Boolean = true,
) {
    val reduce = LocalReduceMotion.current
    val progress = remember(values) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(900, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        if (values.size < 2) return@Canvas
        val min = values.min()
        val max = values.max()
        val range = (max - min).takeIf { it > 0f } ?: 1f
        val pad = strokeWidth.toPx()
        val w = size.width - pad * 2
        val h = size.height - pad * 2
        val pts = values.mapIndexed { i, v ->
            Offset(pad + w * i / (values.size - 1), pad + h - (v - min) / range * h)
        }
        val path = Path().apply {
            moveTo(pts[0].x, pts[0].y)
            for (i in 1 until pts.size) {
                val p0 = pts[i - 1]
                val p1 = pts[i]
                val mx = (p0.x + p1.x) / 2
                cubicTo(mx, p0.y, mx, p1.y, p1.x, p1.y)
            }
        }
        val p = progress.value
        val measure = PathMeasure().apply { setPath(path, false) }
        val partial = Path()
        measure.getSegment(0f, measure.length * p, partial, true)
        if (fill) {
            val area = Path().apply {
                addPath(path)
                lineTo(pts.last().x, size.height)
                lineTo(pts.first().x, size.height)
                close()
            }
            clipRect(right = pad + w * p + pad) {
                drawPath(area, Brush.verticalGradient(listOf(color.copy(alpha = 0.28f), color.copy(alpha = 0f))))
            }
        }
        drawPath(partial, color, style = Stroke(strokeWidth.toPx(), cap = StrokeCap.Round, join = StrokeJoin.Round))
        if (showLastDot && p >= 0.999f) {
            drawCircle(color, radius = strokeWidth.toPx() * 1.6f, center = pts.last())
        }
    }
}

data class BarEntry(val label: String, val value: Float, val color: Color, val secondary: Float? = null)

/** Horizontal bars growing with a spring, used by the yearly recap. */
@Composable
fun BarChart(entries: List<BarEntry>, modifier: Modifier = Modifier, trackColor: Color) {
    val reduce = LocalReduceMotion.current
    val progress = remember(entries) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(entries) {
        progress.animateTo(1f, androidx.compose.animation.core.spring(0.7f, androidx.compose.animation.core.Spring.StiffnessVeryLow))
    }
    Canvas(modifier) {
        if (entries.isEmpty()) return@Canvas
        val max = entries.maxOf { maxOf(it.value, it.secondary ?: 0f) }.takeIf { it > 0f } ?: 1f
        val rowH = size.height / entries.size
        val barH = rowH * 0.56f
        entries.forEachIndexed { i, e ->
            val top = i * rowH + (rowH - barH) / 2
            val r = androidx.compose.ui.geometry.CornerRadius(barH / 2)
            drawRoundRect(trackColor, Offset(0f, top), androidx.compose.ui.geometry.Size(size.width, barH), r)
            e.secondary?.let {
                drawRoundRect(e.color.copy(alpha = 0.3f), Offset(0f, top), androidx.compose.ui.geometry.Size(size.width * (it / max) * progress.value, barH), r)
            }
            val w = (size.width * (e.value / max) * progress.value).coerceAtLeast(barH)
            drawRoundRect(e.color, Offset(0f, top), androidx.compose.ui.geometry.Size(w, barH), r)
        }
    }
}

data class Segment(val value: Float, val color: Color)

/** Ring chart whose segments sweep in with a spring. */
@Composable
fun DonutChart(segments: List<Segment>, modifier: Modifier = Modifier, trackColor: Color, strokeWidth: Dp = 18.dp, gapDegrees: Float = 6f) {
    val reduce = LocalReduceMotion.current
    val progress = remember(segments) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(segments) {
        progress.animateTo(1f, androidx.compose.animation.core.spring(0.8f, androidx.compose.animation.core.Spring.StiffnessVeryLow))
    }
    Canvas(modifier) {
        val stroke = strokeWidth.toPx()
        val arcSize = androidx.compose.ui.geometry.Size(size.minDimension - stroke, size.minDimension - stroke)
        val topLeft = Offset((size.width - arcSize.width) / 2, (size.height - arcSize.height) / 2)
        drawArc(trackColor, 0f, 360f, false, topLeft, arcSize, style = Stroke(stroke))
        val total = segments.sumOf { it.value.toDouble() }.toFloat()
        if (total <= 0f) return@Canvas
        var start = -90f
        val visible = segments.filter { it.value > 0f }
        val gap = if (visible.size > 1) gapDegrees else 0f
        visible.forEach { s ->
            val sweep = (360f * s.value / total - gap).coerceAtLeast(0.5f) * progress.value
            drawArc(s.color, start + gap / 2, sweep, false, topLeft, arcSize, style = Stroke(stroke, cap = StrokeCap.Round))
            start += 360f * s.value / total * progress.value
        }
    }
}

/** Vertical bars (e.g. spending per month) rising one after the other. */
@Composable
fun ColumnChart(values: List<Float>, modifier: Modifier = Modifier, color: Color, highlight: Int = -1, highlightColor: Color = color) {
    val reduce = LocalReduceMotion.current
    val progress = remember(values) { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(values) { progress.animateTo(1f, tween(1100, easing = FastOutSlowInEasing)) }
    Canvas(modifier) {
        if (values.isEmpty()) return@Canvas
        val max = values.max().takeIf { it > 0f } ?: 1f
        val slot = size.width / values.size
        val barW = slot * 0.58f
        values.forEachIndexed { i, v ->
            // Staggered: each bar starts a bit later.
            val local = ((progress.value * (values.size + 4) - i) / 5f).coerceIn(0f, 1f)
            val h = (size.height * (v / max) * local).coerceAtLeast(if (v > 0) barW / 2 else 3.dp.toPx())
            val x = i * slot + (slot - barW) / 2
            drawRoundRect(
                if (i == highlight) highlightColor else color.copy(alpha = if (v > 0) 1f else 0.25f),
                Offset(x, size.height - h),
                androidx.compose.ui.geometry.Size(barW, h),
                androidx.compose.ui.geometry.CornerRadius(barW / 2),
            )
        }
    }
}

/** Spent / planned / budget on one rounded track, with figures underneath. */
@Composable
fun BudgetBar(spent: Long, planned: Long, budget: Long?, modifier: Modifier = Modifier) {
    val c = androidx.compose.material3.MaterialTheme.colorScheme
    val reduce = LocalReduceMotion.current
    val reference = maxOf(budget ?: 0, planned, spent).coerceAtLeast(1).toFloat()
    val progress = remember { Animatable(if (reduce) 1f else 0f) }
    LaunchedEffect(Unit) { progress.animateTo(1f, androidx.compose.animation.core.spring(0.85f, androidx.compose.animation.core.Spring.StiffnessVeryLow)) }
    val over = budget != null && spent > budget
    androidx.compose.foundation.layout.Column(modifier) {
        Canvas(Modifier.fillMaxWidth().height(10.dp)) {
            val r = androidx.compose.ui.geometry.CornerRadius(size.height / 2)
            drawRoundRect(c.surfaceContainerHighest, cornerRadius = r)
            val p = progress.value
            drawRoundRect(c.tertiary.copy(alpha = 0.45f), size = androidx.compose.ui.geometry.Size(size.width * (planned / reference) * p, size.height), cornerRadius = r)
            drawRoundRect(if (over) c.error else c.primary, size = androidx.compose.ui.geometry.Size(size.width * (spent / reference) * p, size.height), cornerRadius = r)
            if (budget != null && budget > 0) {
                val x = size.width * (budget / reference)
                drawLine(c.onSurface, Offset(x, -2.dp.toPx()), Offset(x, size.height + 2.dp.toPx()), 2.dp.toPx(), StrokeCap.Round)
            }
        }
        androidx.compose.material3.Text(
            buildString {
                append(com.karelisio.ruban.util.Money.format(spent))
                if (planned > spent) append(" · prévu ${com.karelisio.ruban.util.Money.format(planned)}")
                if (budget != null) append(" · budget ${com.karelisio.ruban.util.Money.format(budget)}")
            },
            style = androidx.compose.material3.MaterialTheme.typography.labelSmall,
            color = if (over) c.error else c.onSurfaceVariant,
            modifier = androidx.compose.ui.Modifier.padding(top = 4.dp),
        )
    }
}

