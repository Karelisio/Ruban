package com.karelisio.ruban.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.spring
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.model.IdeaWithPeople
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.PricePoint
import com.karelisio.ruban.data.model.Priority
import com.karelisio.ruban.ui.screens.gift.GiftDecoration
import com.karelisio.ruban.util.Money

@Composable
fun statusColors(status: GiftStatus): ToneColors {
    val c = MaterialTheme.colorScheme
    return when (status) {
        GiftStatus.IDEA -> ToneColors(c.surfaceContainerHighest, c.onSurfaceVariant)
        GiftStatus.BOUGHT -> ToneColors(c.secondaryContainer, c.onSecondaryContainer)
        GiftStatus.WRAPPED -> ToneColors(c.primaryContainer, c.onPrimaryContainer)
        GiftStatus.GIVEN -> ToneColors(c.tertiaryContainer, c.onTertiaryContainer)
    }
}

val GiftStatus.emoji: String
    get() = when (this) {
        GiftStatus.IDEA -> "💡"
        GiftStatus.BOUGHT -> "🛍️"
        GiftStatus.WRAPPED -> "🎁"
        GiftStatus.GIVEN -> "🎉"
    }

@Composable
fun StatusPill(status: GiftStatus, modifier: Modifier = Modifier) {
    val tone = statusColors(status)
    val bg by animateColorAsState(tone.container, label = "pillBg")
    val fg by animateColorAsState(tone.content, label = "pillFg")
    Box(modifier.clip(CircleShape).background(bg).padding(horizontal = 10.dp, vertical = 4.dp)) {
        AnimatedContent(
            status,
            transitionSpec = {
                (scaleIn(spring(0.5f, Spring.StiffnessMedium), initialScale = 0.6f) + fadeIn())
                    .togetherWith(scaleOut(targetScale = 1.3f) + fadeOut())
            },
            label = "pill",
        ) { s -> Text("${s.emoji} ${s.label}", style = MaterialTheme.typography.labelMedium, color = fg) }
    }
}

@Composable
fun PriorityTag(priority: Priority, modifier: Modifier = Modifier) {
    val c = MaterialTheme.colorScheme
    val color = when (priority) {
        Priority.STRONG -> c.error
        Priority.NICE -> c.primary
        Priority.MAYBE -> c.outline
    }
    Row(modifier, verticalAlignment = Alignment.CenterVertically) {
        Text(priority.emoji, fontSize = 12.sp)
        Spacer(Modifier.width(4.dp))
        Text(priority.label, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Overlapping avatars. */
@Composable
fun PeopleStack(people: List<Person>, size: Dp = 26.dp, max: Int = 4, modifier: Modifier = Modifier) {
    Row(modifier) {
        people.take(max).forEachIndexed { i, p ->
            PersonAvatar(
                p, size,
                Modifier
                    .offset(x = (-8 * i).dp)
                    .border(2.dp, MaterialTheme.colorScheme.surfaceContainerLow, CircleShape),
            )
        }
        if (people.size > max) {
            Box(
                Modifier.offset(x = (-8 * max).dp).size(size).clip(CircleShape).background(MaterialTheme.colorScheme.secondaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text("+${people.size - max}", style = MaterialTheme.typography.labelSmall) }
        }
    }
}

fun IdeaWithPeople.recipientsLabel(): String = when {
    idea.forMe -> "Pour moi"
    people.isEmpty() -> "À attribuer"
    people.size == 1 -> people.first().name
    people.size == 2 -> "${people[0].name} et ${people[1].name}"
    else -> "${people[0].name} et ${people.size - 1} autres"
}

@Composable
fun IdeaCard(
    item: IdeaWithPeople,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    occasion: String? = null,
    prices: List<PricePoint> = emptyList(),
) {
    val idea = item.idea
    val interaction = remember { MutableInteractionSource() }
    Surface(
        modifier = modifier
            .fillMaxWidth()
            .pressBounce(interaction, 0.97f)
            .sharedBounds(SharedKeys.idea(idea.id)),
        shape = MaterialTheme.shapes.large,
        color = MaterialTheme.colorScheme.surfaceContainerLow,
    ) {
        GiftDecoration(status = idea.status, ideaId = idea.id) {
            Row(
                Modifier
                    .clickable(interaction, indication = androidx.compose.material3.ripple(), onClick = onClick)
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                if (idea.image != null) {
                    LocalImage(
                        idea.image,
                        Modifier
                            .size(76.dp)
                            .sharedElement(SharedKeys.ideaImage(idea.id))
                            .clip(RoundedCornerShape(20.dp)),
                    )
                } else {
                    ShapeBadge(size = 64.dp, polygonIndex = idea.id.toInt(), container = statusColors(idea.status).container) {
                        Text(idea.priority.emoji.takeIf { idea.status == GiftStatus.IDEA } ?: idea.status.emoji, fontSize = 26.sp)
                    }
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text(idea.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        if (item.people.isNotEmpty()) {
                            PeopleStack(item.people, 22.dp, 3)
                            Spacer(Modifier.width(2.dp))
                        }
                        Text(
                            item.recipientsLabel(),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    if (occasion != null) {
                        Text(occasion, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.primary, maxLines = 1)
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        StatusPill(idea.status)
                        if (idea.status == GiftStatus.IDEA) {
                            Spacer(Modifier.width(8.dp))
                            PriorityTag(idea.priority)
                        }
                    }
                }
                Column(horizontalAlignment = Alignment.End) {
                    if (idea.priceCents != null) {
                        AnimatedMoney(idea.priceCents, currency = idea.currency, style = MaterialTheme.typography.titleMedium)
                    }
                    if (prices.size >= 2) {
                        Spacer(Modifier.height(6.dp))
                        PriceSparkline(prices, Modifier.width(64.dp).height(24.dp))
                    }
                }
            }
        }
    }
}

/** Tiny animated price chart for cards. */
@Composable
fun PriceSparkline(points: List<PricePoint>, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.primary) {
    val values = points.map { it.priceCents.toFloat() }
    val dropped = values.size >= 2 && values.last() < values.first()
    LineChart(values, modifier, if (dropped) MaterialTheme.colorScheme.tertiary else color, strokeWidth = 2.dp, fill = true)
}

fun formatPrice(cents: Long?, currency: String): String = cents?.let { Money.format(it, currency) } ?: "—"
