package com.karelisio.ruban.ui.components

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.material3.MaterialShapes
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.background
import com.karelisio.ruban.ui.theme.LocalReduceMotion
import com.karelisio.ruban.ui.theme.PolygonShape
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

/** Compact "J-12" badge in a scalloped shape; gently breathes when the date is close. */
@Composable
fun CountdownBadge(days: Long, modifier: Modifier = Modifier, size: Dp = 52.dp) {
    val c = MaterialTheme.colorScheme
    val close = days <= 7
    val reduce = LocalReduceMotion.current
    val t = rememberInfiniteTransition(label = "badge")
    val pulse = if (close && !reduce) t.animateFloat(1f, 1.07f, infiniteRepeatable(tween(900, easing = FastOutSlowInEasing), RepeatMode.Reverse), label = "pulse").value else 1f
    val spin = if (!reduce) t.animateFloat(0f, 360f, infiniteRepeatable(tween(if (close) 9_000 else 30_000, easing = LinearEasing)), label = "spin").value else 0f
    val container = if (close) c.tertiaryContainer else c.secondaryContainer
    val content = if (close) c.onTertiaryContainer else c.onSecondaryContainer
    Box(modifier.size(size).graphicsLayer { scaleX = pulse; scaleY = pulse }, contentAlignment = Alignment.Center) {
        Box(
            Modifier
                .matchParentSize()
                .graphicsLayer { rotationZ = spin }
                .clip(PolygonShape(MaterialShapes.Cookie9Sided))
                .background(container),
        )
        Text(
            when (days) {
                0L -> "Auj."
                1L -> "Dem."
                else -> "J-$days"
            },
            style = MaterialTheme.typography.labelLarge,
            color = content,
        )
    }
}

/** Living countdown: days, then hours/minutes/seconds ticking during the last 48 hours. */
@Composable
fun LiveCountdown(date: LocalDate, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.displaySmall) {
    val now = rememberNow(1_000)
    val target = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    val diff = target - now
    val today = LocalDateTime.now().toLocalDate()
    val c = MaterialTheme.colorScheme
    Column(modifier, horizontalAlignment = Alignment.CenterHorizontally) {
        when {
            date == today -> {
                RollingText("C'est aujourd'hui", style = style, color = c.primary)
            }
            diff in 1..(48L * 3_600_000) -> {
                val h = diff / 3_600_000
                val m = (diff / 60_000) % 60
                val s = (diff / 1_000) % 60
                Row(horizontalArrangement = Arrangement.Center) {
                    RollingText("%02d:%02d:%02d".format(h, m, s), style = style, color = c.primary)
                }
                Text("avant le grand jour", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            }
            else -> {
                val days = java.time.temporal.ChronoUnit.DAYS.between(today, date)
                RollingText(days.toString(), style = style, color = c.primary)
                Text(if (days > 1) "jours restants" else "jour restant", style = MaterialTheme.typography.labelLarge, color = c.onSurfaceVariant)
            }
        }
    }
}
