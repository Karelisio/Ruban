package com.karelisio.ruban.ui.components

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathMeasure
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.drawscope.withTransform
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.launch

/** The Ruban bow (same drawing as the launcher icon), in a 108×108 design space. */
private fun bowPath(): Path = Path().apply {
    moveTo(40f, 79f)
    cubicTo(45f, 70f, 50f, 62f, 54f, 54f)
    cubicTo(46f, 44f, 33f, 32f, 26.5f, 39.5f)
    cubicTo(20.5f, 46.5f, 30f, 58.5f, 54f, 54f)
    cubicTo(78f, 58.5f, 87.5f, 46.5f, 81.5f, 39.5f)
    cubicTo(75f, 32f, 62f, 44f, 54f, 54f)
    cubicTo(58f, 62f, 63f, 70f, 68f, 79f)
}

/**
 * Draws the ribbon bow. [progress] in 0..1 unrolls the ribbon; [knot] in 0..1(+overshoot) pops the knot.
 */
@Composable
fun RibbonKnot(
    modifier: Modifier = Modifier,
    progress: Float = 1f,
    knot: Float = 1f,
    ribbon: Color = MaterialTheme.colorScheme.primary,
    knotColor: Color = MaterialTheme.colorScheme.tertiary,
    wiggle: Float = 0f,
) {
    val path = remember { bowPath() }
    val measure = remember { PathMeasure().apply { setPath(path, false) } }
    val segment = remember { Path() }
    Canvas(modifier) {
        val s = minOf(size.width, size.height) / 72f
        withTransform({
            translate((size.width - 108f * s) / 2f, (size.height - 108f * s) / 2f)
            scale(s, s, Offset.Zero)
            rotate(wiggle, Offset(54f, 54f))
        }) {
            segment.rewind()
            measure.getSegment(0f, measure.length * progress.coerceIn(0f, 1f), segment, true)
            drawPath(segment, ribbon, style = Stroke(width = 8f, cap = StrokeCap.Round, join = StrokeJoin.Round))
            if (knot > 0f) {
                scale(knot, knot, Offset(54f, 54f)) {
                    drawCircle(knotColor, radius = 6.6f, center = Offset(54f, 54f))
                }
            }
        }
    }
}

/** Signature animation #1: the ribbon unrolls, ties itself and the knot springs in. */
@Composable
fun AnimatedRibbonKnot(modifier: Modifier = Modifier, onFinished: () -> Unit = {}) {
    val reduce = LocalReduceMotion.current
    val unroll = remember { Animatable(if (reduce) 1f else 0f) }
    val knot = remember { Animatable(if (reduce) 1f else 0f) }
    val wiggle = remember { Animatable(0f) }
    LaunchedEffect(Unit) {
        if (!reduce) {
            unroll.animateTo(1f, tween(900, easing = FastOutSlowInEasing))
            coroutineScope {
                launch { knot.animateTo(1f, spring(dampingRatio = 0.3f, stiffness = Spring.StiffnessMediumLow)) }
                launch {
                    wiggle.animateTo(-8f, tween(120))
                    wiggle.animateTo(0f, spring(dampingRatio = 0.25f, stiffness = Spring.StiffnessLow))
                }
            }
        }
        onFinished()
    }
    RibbonKnot(modifier, unroll.value, knot.value, wiggle = wiggle.value)
}
