package com.karelisio.ruban.domain

import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.data.model.Person
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.MonthDay
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters

/** A dated instance of an [Event]. */
data class Occurrence(
    val event: Event,
    val person: Person?,
    val date: LocalDate,
) {
    val title: String get() = EventDates.titleOf(event, person)
    val emoji: String get() = event.emoji ?: event.kind.emoji
    val key: String get() = "${event.id}-${date.year}"

    /** Age reached (birthdays) or years celebrated (anniversaries). */
    val yearsCount: Int? get() = when (event.kind) {
        EventKind.BIRTHDAY -> person?.birthYear?.let { date.year - it }?.takeIf { it > 0 }
        EventKind.CUSTOM -> if (event.recurring) event.year?.let { date.year - it }?.takeIf { it > 0 } else null
        else -> null
    }

    fun daysFrom(today: LocalDate): Long = ChronoUnit.DAYS.between(today, date)
}

object EventDates {

    /** Western Easter Sunday (anonymous Gregorian algorithm). */
    fun easter(year: Int): LocalDate {
        val a = year % 19
        val b = year / 100
        val c = year % 100
        val d = b / 4
        val e = b % 4
        val f = (b + 8) / 25
        val g = (b - f + 1) / 3
        val h = (19 * a + b - d - g + 15) % 30
        val i = c / 4
        val k = c % 4
        val l = (32 + 2 * e + 2 * i - h - k) % 7
        val m = (a + 11 * h + 22 * l) / 451
        val month = (h + l - 7 * m + 114) / 31
        val day = ((h + l - 7 * m + 114) % 31) + 1
        return LocalDate.of(year, month, day)
    }

    fun pentecost(year: Int): LocalDate = easter(year).plusDays(49)

    /** French Mother's day: last Sunday of May, moved to the first Sunday of June when it is Pentecost. */
    fun mothersDay(year: Int): LocalDate {
        val lastSundayOfMay = LocalDate.of(year, 5, 31).with(TemporalAdjusters.previousOrSame(DayOfWeek.SUNDAY))
        return if (lastSundayOfMay == pentecost(year)) {
            LocalDate.of(year, 6, 1).with(TemporalAdjusters.nextOrSame(DayOfWeek.SUNDAY))
        } else lastSundayOfMay
    }

    /** French Father's day: third Sunday of June. */
    fun fathersDay(year: Int): LocalDate =
        LocalDate.of(year, 6, 1).with(TemporalAdjusters.dayOfWeekInMonth(3, DayOfWeek.SUNDAY))

    private fun safeDate(year: Int, month: Int, day: Int): LocalDate {
        val md = MonthDay.of(month, day)
        return if (md.isValidYear(year)) md.atYear(year) else LocalDate.of(year, month, 28) // 29 Feb
    }

    /** Date of [event] during [year], or null if it does not happen that year. */
    fun dateIn(event: Event, person: Person?, year: Int): LocalDate? = when (event.kind) {
        EventKind.BIRTHDAY -> person?.let { p ->
            val m = p.birthdayMonth ?: return@let null
            val d = p.birthdayDay ?: return@let null
            safeDate(year, m, d)
        }
        EventKind.NAME_DAY -> person?.let { p ->
            val m = p.nameDayMonth ?: return@let null
            val d = p.nameDayDay ?: return@let null
            safeDate(year, m, d)
        }
        EventKind.CHRISTMAS -> LocalDate.of(year, 12, 25)
        EventKind.VALENTINE -> LocalDate.of(year, 2, 14)
        EventKind.MOTHERS_DAY -> mothersDay(year)
        EventKind.FATHERS_DAY -> fathersDay(year)
        EventKind.CUSTOM -> {
            val m = event.month
            val d = event.day
            when {
                m == null || d == null -> null
                event.recurring -> safeDate(year, m, d).takeIf { event.year == null || year >= event.year }
                event.year == year -> safeDate(year, m, d)
                else -> null
            }
        }
    }

    /** Next occurrence on or after [from]. */
    fun next(event: Event, person: Person?, from: LocalDate): LocalDate? {
        for (y in from.year..from.year + 1) {
            val date = dateIn(event, person, y) ?: continue
            if (!date.isBefore(from)) return date
        }
        return if (!event.recurring && event.kind == EventKind.CUSTOM) null
        else dateIn(event, person, from.year + 2)?.takeIf { !it.isBefore(from) }
    }

    /** Most recent occurrence strictly before [from]. */
    fun previous(event: Event, person: Person?, from: LocalDate): LocalDate? {
        for (y in from.year downTo from.year - 1) {
            val date = dateIn(event, person, y) ?: continue
            if (date.isBefore(from)) return date
        }
        return null
    }

    /** Whether a shared holiday makes sense for [person] (no Mother's day for a friend…). */
    fun fits(event: Event, person: Person): Boolean = when (event.kind) {
        EventKind.MOTHERS_DAY, EventKind.FATHERS_DAY -> person.relation == com.karelisio.ruban.data.model.Relation.PARENT ||
            person.relation == com.karelisio.ruban.data.model.Relation.GRANDPARENT
        EventKind.VALENTINE -> person.relation == com.karelisio.ruban.data.model.Relation.PARTNER
        else -> true
    }

    fun titleOf(event: Event, person: Person?): String = when (event.kind) {
        EventKind.BIRTHDAY -> "Anniversaire ${person?.name?.let { "de $it" } ?: ""}".trim()
        EventKind.NAME_DAY -> "Fête ${person?.name?.let { "de $it" } ?: ""}".trim()
        else -> event.title
    }

    /** All occurrences between [from] (inclusive) and [to] (inclusive). */
    fun occurrences(events: List<Event>, persons: Map<Long, Person>, from: LocalDate, to: LocalDate): List<Occurrence> =
        buildList {
            for (event in events) {
                val person = event.personId?.let(persons::get)
                if (event.personId != null && person == null) continue
                for (year in from.year..to.year) {
                    val date = dateIn(event, person, year) ?: continue
                    if (!date.isBefore(from) && !date.isAfter(to)) add(Occurrence(event, person, date))
                }
            }
        }.sortedBy { it.date }

    /** One upcoming occurrence per event, sorted by date. */
    fun upcoming(events: List<Event>, persons: Map<Long, Person>, today: LocalDate): List<Occurrence> =
        events.mapNotNull { event ->
            val person = event.personId?.let(persons::get)
            if (event.personId != null && person == null) return@mapNotNull null
            next(event, person, today)?.let { Occurrence(event, person, it) }
        }.sortedBy { it.date }

    fun parseOffsets(raw: String?): List<Int>? =
        raw?.split(',')?.mapNotNull { it.trim().toIntOrNull() }?.filter { it >= 0 }?.distinct()?.sortedDescending()
}
