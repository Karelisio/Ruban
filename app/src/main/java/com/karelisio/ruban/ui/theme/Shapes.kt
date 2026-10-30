package com.karelisio.ruban.ui.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.Shapes
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Matrix
import androidx.compose.ui.graphics.Outline
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.graphics.shapes.Cubic
import androidx.graphics.shapes.Morph
import androidx.graphics.shapes.RoundedPolygon

val RubanShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(14.dp),
    medium = RoundedCornerShape(20.dp),
    large = RoundedCornerShape(28.dp),
    extraLarge = RoundedCornerShape(36.dp),
)

fun List<Cubic>.toPath(path: Path = Path()): Path {
    path.rewind()
    forEachIndexed { i, c ->
        if (i == 0) path.moveTo(c.anchor0X, c.anchor0Y)
        path.cubicTo(c.control0X, c.control0Y, c.control1X, c.control1Y, c.anchor1X, c.anchor1Y)
    }
    path.close()
    return path
}

private fun Path.fitInto(size: Size, bounds: FloatArray, rotation: Float): Path {
    val w = (bounds[2] - bounds[0]).coerceAtLeast(0.0001f)
    val h = (bounds[3] - bounds[1]).coerceAtLeast(0.0001f)
    val m = Matrix()
    m.translate(size.width / 2f, size.height / 2f)
    if (rotation != 0f) m.rotateZ(rotation)
    m.scale(size.width / w, size.height / h)
    m.translate(-(bounds[0] + w / 2f), -(bounds[1] + h / 2f))
    transform(m)
    return this
}

/** A [Shape] drawing a (Material) [RoundedPolygon], stretched to the layout bounds. */
class PolygonShape(private val polygon: RoundedPolygon, private val rotation: Float = 0f) : Shape {
    private val bounds = polygon.calculateBounds()
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline =
        Outline.Generic(polygon.cubics.toPath().fitInto(size, bounds, rotation))
}

/** A [Shape] morphing between two polygons according to [progress] (0 = start, 1 = end). */
class MorphShape(private val morph: Morph, private val progress: Float, private val rotation: Float = 0f) : Shape {
    override fun createOutline(size: Size, layoutDirection: LayoutDirection, density: Density): Outline {
        val bounds = morph.calculateBounds()
        return Outline.Generic(morph.asCubics(progress).toPath().fitInto(size, bounds, rotation))
    }
}

/** Playful shapes used for avatars; picked deterministically per person. */
object RubanPolygons {
    val avatars: List<RoundedPolygon> by lazy {
        listOf(
            MaterialShapes.Cookie9Sided, MaterialShapes.Clover4Leaf, MaterialShapes.Sunny,
            MaterialShapes.Flower, MaterialShapes.Puffy, MaterialShapes.SoftBurst,
            MaterialShapes.Cookie7Sided, MaterialShapes.Gem, MaterialShapes.Pentagon,
            MaterialShapes.Cookie6Sided, MaterialShapes.Arch, MaterialShapes.PuffyDiamond,
        )
    }

    fun forId(id: Long): RoundedPolygon = avatars[(id.mod(avatars.size.toLong())).toInt()]
}
