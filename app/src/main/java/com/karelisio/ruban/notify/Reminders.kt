package com.karelisio.ruban.notify

import android.app.AlarmManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.core.app.NotificationCompat
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.IntentRouter
import com.karelisio.ruban.container
import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.settings.Settings
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.Occurrence
import com.karelisio.ruban.domain.Prep
import com.karelisio.ruban.domain.Preparation
import com.karelisio.ruban.util.Dates
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId

data class PlannedReminder(val occurrence: Occurrence, val offsetDays: Int, val at: LocalDateTime) {
    val millis: Long get() = at.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli()
}

object ReminderPlanner {
    /** Every reminder of the next ~13 months, sorted. */
    fun plan(events: List<Event>, persons: Map<Long, Person>, settings: Settings, today: LocalDate): List<PlannedReminder> {
        val horizon = today.plusDays(400)
        return events.filter { it.remindersEnabled }.flatMap { event ->
            val person = event.personId?.let(persons::get)
            if (event.personId != null && person == null) return@flatMap emptyList()
            val offsets = EventDates.parseOffsets(event.reminderOffsets) ?: settings.reminderOffsets
            EventDates.occurrences(listOf(event), persons, today, horizon).flatMap { occ ->
                offsets.map { offset ->
                    PlannedReminder(occ, offset, occ.date.minusDays(offset.toLong()).atTime(settings.reminderHour, settings.reminderMinute))
                }
            }
        }.sortedBy { it.at }
    }
}

/** Schedules a single exact alarm for the next reminder; the receiver posts it and schedules the following one. */
class ReminderScheduler(private val context: Context, private val c: AppContainer) {
    private val alarms = context.getSystemService(AlarmManager::class.java)

    private fun pendingIntent(): PendingIntent = PendingIntent.getBroadcast(
        context, 1,
        Intent(context, ReminderReceiver::class.java).setAction(ReminderReceiver.ACTION_FIRE),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private suspend fun planned(): List<PlannedReminder> {
        val settings = c.settings.current()
        val persons = c.persons.all().associateBy { it.id }
        return ReminderPlanner.plan(c.events.all(), persons, settings, LocalDate.now())
    }

    suspend fun reschedule() {
        val now = System.currentTimeMillis()
        val next = planned().firstOrNull { it.millis > now }
        val pi = pendingIntent()
        if (next == null) {
            alarms.cancel(pi)
            return
        }
        if (alarms.canScheduleExactAlarms()) {
            alarms.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, next.millis, pi)
        } else {
            // Without the exact alarm permission: best effort within a 15 min window.
            alarms.setWindow(AlarmManager.RTC_WAKEUP, next.millis, 15 * 60_000L, pi)
        }
    }

    /** Posts the reminders that became due since the last run, then schedules the next alarm. */
    suspend fun fire() {
        val settings = c.settings.current()
        val now = System.currentTimeMillis()
        val since = maxOf(settings.lastReminderAt, now - 6 * 3_600_000L)
        val due = planned().filter { it.millis in (since + 1)..(now + 60_000) }
        if (due.isNotEmpty()) {
            val ideas = c.ideas.allWithPeople()
            due.forEach { notify(it, Preparation.of(it.occurrence, ideas), settings.neutralNotifications) }
        }
        c.settings.setLastReminderAt(now)
        reschedule()
    }

    private fun notify(r: PlannedReminder, prep: com.karelisio.ruban.domain.PrepState, neutral: Boolean) {
        val o = r.occurrence
        val whenText = when (r.offsetDays) {
            0 -> "aujourd'hui"
            1 -> "demain"
            else -> "dans ${r.offsetDays} jours"
        }
        val title = if (neutral) "Rappel Ruban" else "${o.emoji} ${o.title} $whenText"
        val status = when (prep.prep) {
            Prep.READY -> if (neutral) "Un cadeau est prêt." else "Cadeau prêt : ${prep.headline?.idea?.title}"
            Prep.IDEA -> if (neutral) "Une idée est déjà notée." else "Idée trouvée : ${prep.headline?.idea?.title}"
            Prep.NOTHING -> "Rien encore… Ouvre Ruban pour trouver l'inspiration."
            Prep.DONE -> "Déjà offert, bravo !"
        }
        val body = if (neutral) "${o.title} $whenText · $status" else "${Dates.weekday(o.date)} · $status"
        val id = (o.event.id * 100 + r.offsetDays).toInt()
        val builder = Notifications.builder(context, Notifications.CHANNEL_REMINDERS)
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setVisibility(if (neutral) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(Notifications.pending(context, id, IntentRouter.openEvent(context, o.event.id)))
        if (prep.prep == Prep.NOTHING || prep.prep == Prep.IDEA) {
            val add = Intent(IntentRouter.newIdea(context)).putExtra(IntentRouter.EXTRA_EVENT_ID, o.event.id)
            builder.addAction(0, "Noter une idée", Notifications.pending(context, id + 50_000, add))
        }
        Notifications.post(context, id, builder)
    }
}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val result = goAsync()
        CoroutineScope(SupervisorJob() + Dispatchers.Default).launch {
            try {
                val scheduler = context.container.reminders
                if (intent.action == ACTION_FIRE) scheduler.fire() else scheduler.reschedule()
            } finally {
                result.finish()
            }
        }
    }

    companion object {
        const val ACTION_FIRE = "com.karelisio.ruban.action.REMINDER"
    }
}
