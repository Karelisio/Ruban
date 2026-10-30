package com.karelisio.ruban.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil3.compose.AsyncImage
import com.karelisio.ruban.container
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.ui.theme.DisplayFamily
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.ui.theme.PolygonShape
import com.karelisio.ruban.ui.theme.RubanPolygons

data class ToneColors(val container: Color, val content: Color)

/** Picks one of the theme's container tones deterministically. */
@Composable
fun toneFor(id: Long): ToneColors {
    val c = MaterialTheme.colorScheme
    return when ((id % 3 + 3) % 3) {
        0L -> ToneColors(c.primaryContainer, c.onPrimaryContainer)
        1L -> ToneColors(c.tertiaryContainer, c.onTertiaryContainer)
        else -> ToneColors(c.secondaryContainer, c.onSecondaryContainer)
    }
}

fun initials(name: String): String =
    name.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }.take(2).joinToString("") { it.first().uppercase() }

@Composable
fun PersonAvatar(person: Person, size: Dp, modifier: Modifier = Modifier, rotation: Float = 0f) {
    val shape = remember(person.id, rotation) { PolygonShape(RubanPolygons.forId(person.id), rotation) }
    val tone = toneFor(person.id)
    val photos = LocalContext.current.container.photos
    Box(
        modifier
            .size(size)
            .clip(shape)
            .background(tone.container),
        contentAlignment = Alignment.Center,
    ) {
        val photo = remember(person.photo) { photos.model(person.photo) }
        if (photo != null) {
            AsyncImage(model = photo, contentDescription = person.name, contentScale = ContentScale.Crop, modifier = Modifier.matchParentSize())
        } else {
            Text(
                initials(person.name).ifEmpty { "?" },
                color = tone.content,
                fontFamily = DisplayFamily,
                fontWeight = FontWeight.Bold,
                fontSize = (size.value * 0.36f).sp,
            )
        }
    }
}

/** Polygon "blob" behind an emoji or icon. */
@Composable
fun ShapeBadge(
    modifier: Modifier = Modifier,
    size: Dp = 48.dp,
    polygonIndex: Int = 0,
    container: Color = MaterialTheme.colorScheme.secondaryContainer,
    spin: Boolean = false,
    content: @Composable () -> Unit,
) {
    val polygon = RubanPolygons.avatars[polygonIndex.mod(RubanPolygons.avatars.size)]
    val reduce = LocalReduceMotion.current
    val rotation = if (spin && !reduce) {
        val t = rememberInfiniteTransition(label = "badgeSpin")
        t.animateFloat(0f, 360f, infiniteRepeatable(tween(16_000, easing = LinearEasing)), label = "rot").value
    } else 0f
    Box(modifier.size(size), contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = rotation }
                .clip(PolygonShape(polygon))
                .background(container),
        )
        content()
    }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, action: (@Composable () -> Unit)? = null) {
    Row(
        modifier.fillMaxWidth().padding(start = 20.dp, end = 12.dp, top = 20.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            title,
            style = MaterialTheme.typography.titleSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

/** Friendly empty state with a softly floating, rotating shape. */
@Composable
fun EmptyState(
    emoji: String,
    title: String,
    text: String,
    modifier: Modifier = Modifier,
    actionLabel: String? = null,
    actionIcon: ImageVector? = null,
    onAction: (() -> Unit)? = null,
) {
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "empty")
    val float by t.animateFloat(
        0f, 1f,
        infiniteRepeatable(tween(2400, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        label = "float",
    )
    val f = if (reduce) 0.5f else float
    Column(
        modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier.size(132.dp).graphicsLayer { translationY = (f - 0.5f) * 18f },
            contentAlignment = Alignment.Center,
        ) {
            Box(
                Modifier
                    .matchParentSize()
                    .graphicsLayer { rotationZ = f * 24f - 12f }
                    .clip(PolygonShape(MaterialShapes.Cookie9Sided))
                    .background(MaterialTheme.colorScheme.primaryContainer),
            )
            Text(emoji, fontSize = 56.sp)
        }
        Spacer(Modifier.height(24.dp))
        Text(title, style = MaterialTheme.typography.headlineSmall, textAlign = TextAlign.Center)
        Spacer(Modifier.height(8.dp))
        Text(
            text,
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
        if (actionLabel != null && onAction != null) {
            Spacer(Modifier.height(24.dp))
            Button(onClick = onAction, shapes = ButtonDefaults.shapes(), contentPadding = PaddingValues(horizontal = 24.dp, vertical = 14.dp)) {
                if (actionIcon != null) {
                    Icon(actionIcon, null, Modifier.size(20.dp))
                    Spacer(Modifier.width(8.dp))
                }
                Text(actionLabel)
            }
        }
    }
}

@Composable
fun LocalImage(name: String?, modifier: Modifier = Modifier, contentDescription: String? = null) {
    val photos = LocalContext.current.container.photos
    val file = remember(name) { photos.model(name) }
    if (file != null) {
        AsyncImage(model = file, contentDescription = contentDescription, contentScale = ContentScale.Crop, modifier = modifier)
    } else {
        Box(modifier.background(MaterialTheme.colorScheme.surfaceContainerHighest))
    }
}
