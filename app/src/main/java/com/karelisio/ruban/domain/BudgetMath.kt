package com.karelisio.ruban.domain

import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.model.IdeaWithPeople
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.util.Dates
import java.time.LocalDate

data class PersonBudget(val person: Person, val spent: Long, val planned: Long, val budget: Long?, val gifts: Int)
data class OccasionBudget(val occurrence: Occurrence, val spent: Long, val planned: Long, val budget: Long?)
data class KindTotal(val kind: EventKind?, val label: String, val emoji: String, val spent: Long)

data class YearBudget(
    val year: Int,
    val spent: Long,
    val planned: Long,
    val budget: Long,
    val byPerson: List<PersonBudget>,
    val byOccasion: List<OccasionBudget>,
    val byKind: List<KindTotal>,
    val byMonth: List<Long>,
    val giftsGiven: Int,
    val topReaction: String?,
)

object BudgetMath {
    /** Year an idea counts for: its occasion, else when it was given / bought / noted. */
    fun yearOf(item: IdeaWithPeople): Int = item.idea.occasionYear
        ?: Dates.fromMillis(item.idea.givenAt ?: item.idea.boughtAt ?: item.idea.createdAt).year

    private fun monthOf(item: IdeaWithPeople, events: Map<Long, Event>, persons: Map<Long, Person>): Int {
        val idea = item.idea
        val event = idea.eventId?.let(events::get)
        val year = idea.occasionYear
        if (event != null && year != null) {
            EventDates.dateIn(event, event.personId?.let(persons::get), year)?.let { return it.monthValue }
        }
        return Dates.fromMillis(idea.givenAt ?: idea.boughtAt ?: idea.createdAt).monthValue
    }

    fun years(ideas: List<IdeaWithPeople>, today: LocalDate): List<Int> =
        (ideas.filter { !it.idea.forMe }.map(::yearOf) + today.year).distinct().sortedDescending()

    fun compute(
        year: Int,
        ideas: List<IdeaWithPeople>,
        events: List<Event>,
        persons: List<Person>,
    ): YearBudget {
        val eventsById = events.associateBy { it.id }
        val personsById = persons.associateBy { it.id }
        val inYear = ideas.filter { !it.idea.forMe && yearOf(it) == year }
        val spent = inYear.sumOf { it.idea.spentCents }
        val planned = inYear.sumOf { it.idea.priceCents ?: 0 }

        val byPerson = persons.map { p ->
            val mine = inYear.filter { i -> i.people.any { it.id == p.id } }
            PersonBudget(
                person = p,
                spent = mine.sumOf { it.idea.spentCents / it.people.size.coerceAtLeast(1) },
                planned = mine.sumOf { (it.idea.priceCents ?: 0) / it.people.size.coerceAtLeast(1) },
                budget = p.yearlyBudgetCents,
                gifts = mine.count { it.idea.status == GiftStatus.GIVEN },
            )
        }.filter { it.planned > 0 || it.budget != null }.sortedByDescending { maxOf(it.spent, it.planned) }

        val occurrences = EventDates.occurrences(events, personsById, LocalDate.of(year, 1, 1), LocalDate.of(year, 12, 31))
        val byOccasion = occurrences.map { o ->
            val linked = inYear.filter { it.idea.eventId == o.event.id }
            OccasionBudget(o, linked.sumOf { it.idea.spentCents }, linked.sumOf { it.idea.priceCents ?: 0 }, o.event.budgetCents)
        }.filter { it.planned > 0 || it.budget != null }

        val byKind = inYear.filter { it.idea.spentCents > 0 }
            .groupBy { it.idea.eventId?.let(eventsById::get)?.kind }
            .map { (kind, list) ->
                KindTotal(kind, kind?.label ?: "Sans occasion", kind?.emoji ?: "✨", list.sumOf { it.idea.spentCents })
            }
            .sortedByDescending { it.spent }

        val byMonth = MutableList(12) { 0L }
        inYear.filter { it.idea.spentCents > 0 }.forEach { byMonth[monthOf(it, eventsById, personsById) - 1] += it.idea.spentCents }

        val budget = byOccasion.sumOf { it.budget ?: 0 } + persons.sumOf { it.yearlyBudgetCents ?: 0 }
        val given = inYear.filter { it.idea.status == GiftStatus.GIVEN }
        return YearBudget(
            year = year,
            spent = spent,
            planned = planned,
            budget = budget,
            byPerson = byPerson,
            byOccasion = byOccasion,
            byKind = byKind,
            byMonth = byMonth,
            giftsGiven = given.size,
            topReaction = given.mapNotNull { it.idea.reactionEmoji }.groupingBy { it }.eachCount().maxByOrNull { it.value }?.key,
        )
    }
}
