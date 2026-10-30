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
