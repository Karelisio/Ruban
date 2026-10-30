package com.karelisio.ruban.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.Image
import androidx.glance.ImageProvider
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.appWidgetBackground
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.karelisio.ruban.IntentRouter
import com.karelisio.ruban.R
import com.karelisio.ruban.container
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.Prep
import com.karelisio.ruban.domain.Preparation
import com.karelisio.ruban.ui.nav.CaptureStart
import com.karelisio.ruban.util.Dates
import java.time.LocalDate

private data class WidgetEvent(val id: Long, val emoji: String, val title: String, val date: LocalDate, val days: Long, val prep: Prep)

private suspend fun loadEvents(context: Context, count: Int): List<WidgetEvent> {
    val c = context.container
    val today = LocalDate.now()
    val persons = c.persons.all().associateBy { it.id }
    val ideas = c.ideas.allWithPeople()
    return EventDates.upcoming(c.events.all(), persons, today).take(count).map { o ->
        WidgetEvent(o.event.id, o.emoji, o.title, o.date, o.daysFrom(today), Preparation.of(o, ideas).prep)
    }
}

private fun countdownText(days: Long) = when (days) {
    0L -> "Aujourd'hui"
    1L -> "Demain"
    else -> "J-$days"
}

/** Next event with a countdown and whether an idea is found. Resizable from 2×1 to 4×3. */
class NextEventWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(SMALL, MEDIUM, LARGE))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val events = runCatching { loadEvents(context, 3) }.getOrDefault(emptyList())
        provideContent { GlanceTheme { NextEventContent(context, events) } }
    }

    companion object {
        val SMALL = DpSize(110.dp, 48.dp)
        val MEDIUM = DpSize(200.dp, 110.dp)
        val LARGE = DpSize(250.dp, 200.dp)
    }
}

@Composable
private fun NextEventContent(context: Context, events: List<WidgetEvent>) {
    val size = LocalSize.current
    val next = events.firstOrNull()
    val open = if (next != null) actionStartActivity(IntentRouter.openEvent(context, next.id)) else actionStartActivity(IntentRouter.openTab(context, 2))
    Box(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(GlanceTheme.colors.widgetBackground)
            .clickable(open)
            .padding(14.dp),
    ) {
        if (next == null) {
            Column(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically, horizontalAlignment = Alignment.CenterHorizontally) {
                Text("🎀", style = TextStyle(fontSize = 26.sp))
                Text("Rien à l'horizon", style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 14.sp))
            }
            return@Box
        }
        when {
            size.height < NextEventWidget.MEDIUM.height || size.width < NextEventWidget.MEDIUM.width -> SmallEvent(next)
            size.height < NextEventWidget.LARGE.height -> MediumEvent(next)
            else -> Column(GlanceModifier.fillMaxSize()) {
                MediumEvent(next)
                events.drop(1).forEach { e ->
                    Spacer(GlanceModifier.height(8.dp))
                    Row(
                        GlanceModifier.fillMaxWidth().cornerRadius(16.dp).background(GlanceTheme.colors.secondaryContainer).padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(e.emoji, style = TextStyle(fontSize = 16.sp))
                        Spacer(GlanceModifier.width(8.dp))
                        Text(e.title, maxLines = 1, modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontSize = 13.sp))
                        Text(countdownText(e.days), style = TextStyle(color = GlanceTheme.colors.onSecondaryContainer, fontWeight = FontWeight.Bold, fontSize = 13.sp))
                    }
                }
            }
        }
    }
}

@Composable
private fun SmallEvent(e: WidgetEvent) {
    Row(GlanceModifier.fillMaxSize(), verticalAlignment = Alignment.CenterVertically) {
        Text(e.emoji, style = TextStyle(fontSize = 22.sp))
        Spacer(GlanceModifier.width(8.dp))
        Column {
            Text(countdownText(e.days), style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 18.sp))
            Text(e.title, maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 12.sp))
        }
    }
}

@Composable
private fun MediumEvent(e: WidgetEvent) {
    Column(GlanceModifier.fillMaxWidth()) {
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Box(
                GlanceModifier.size(44.dp).cornerRadius(22.dp).background(GlanceTheme.colors.primaryContainer),
                contentAlignment = Alignment.Center,
            ) { Text(e.emoji, style = TextStyle(fontSize = 22.sp)) }
            Spacer(GlanceModifier.width(10.dp))
            Column(GlanceModifier.defaultWeight()) {
                Text(e.title, maxLines = 2, style = TextStyle(color = GlanceTheme.colors.onSurface, fontWeight = FontWeight.Medium, fontSize = 15.sp))
                Text(Dates.weekday(e.date), maxLines = 1, style = TextStyle(color = GlanceTheme.colors.onSurfaceVariant, fontSize = 12.sp))
            }
        }
        Spacer(GlanceModifier.height(8.dp))
        Row(GlanceModifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Text(countdownText(e.days), modifier = GlanceModifier.defaultWeight(), style = TextStyle(color = GlanceTheme.colors.primary, fontWeight = FontWeight.Bold, fontSize = 30.sp))
            val (bg, fg) = when (e.prep) {
                Prep.READY, Prep.DONE -> GlanceTheme.colors.tertiaryContainer to GlanceTheme.colors.onTertiaryContainer
                Prep.IDEA -> GlanceTheme.colors.secondaryContainer to GlanceTheme.colors.onSecondaryContainer
                Prep.NOTHING -> GlanceTheme.colors.surfaceVariant to GlanceTheme.colors.onSurfaceVariant
            }
            Text(
                "${e.prep.emoji} ${e.prep.label.lowercase().replaceFirstChar { it.uppercase() }}",
                modifier = GlanceModifier.cornerRadius(14.dp).background(bg).padding(horizontal = 10.dp, vertical = 4.dp),
                style = TextStyle(color = fg, fontSize = 12.sp, fontWeight = FontWeight.Medium),
            )
        }
    }
}

/** One-tap "Nouvelle idée" button; wider sizes add photo and voice shortcuts. */
class NewIdeaWidget : GlanceAppWidget() {
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(56.dp, 56.dp), DpSize(180.dp, 56.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideContent { GlanceTheme { NewIdeaContent(context) } }
    }
}

@Composable
private fun NewIdeaContent(context: Context) {
    val wide = LocalSize.current.width >= 180.dp
    Row(
        GlanceModifier
            .fillMaxSize()
            .appWidgetBackground()
            .cornerRadius(android.R.dimen.system_app_widget_background_radius)
            .background(GlanceTheme.colors.primaryContainer)
            .padding(8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(
            GlanceModifier.defaultWeight().fillMaxSize().cornerRadius(20.dp).clickable(actionStartActivity(IntentRouter.newIdea(context))),
            verticalAlignment = Alignment.CenterVertically,
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Image(ImageProvider(R.drawable.ic_widget_add), "Nouvelle idée", GlanceModifier.size(24.dp), colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimaryContainer))
            if (wide) {
                Spacer(GlanceModifier.width(8.dp))
                Text("Nouvelle idée", style = TextStyle(color = GlanceTheme.colors.onPrimaryContainer, fontWeight = FontWeight.Bold, fontSize = 15.sp))
            }
        }
        if (wide) {
            WidgetIcon(R.drawable.ic_widget_camera, "Photo") { actionStartActivity(IntentRouter.newIdea(context, CaptureStart.CAMERA)) }
            WidgetIcon(R.drawable.ic_widget_mic, "Dicter") { actionStartActivity(IntentRouter.newIdea(context, CaptureStart.VOICE)) }
        }
    }
}

@Composable
private fun WidgetIcon(icon: Int, label: String, action: () -> androidx.glance.action.Action) {
    Box(
        GlanceModifier.size(44.dp).cornerRadius(22.dp).background(GlanceTheme.colors.primary).clickable(action()),
        contentAlignment = Alignment.Center,
    ) {
        Image(ImageProvider(icon), label, GlanceModifier.size(22.dp), colorFilter = androidx.glance.ColorFilter.tint(GlanceTheme.colors.onPrimary))
    }
}

class NextEventWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextEventWidget()
}

class NewIdeaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NewIdeaWidget()
}

object WidgetUpdater {
    suspend fun refresh(context: Context) {
        runCatching { NextEventWidget().updateAll(context) }
    }
}
