package com.karelisio.ruban.domain

import com.karelisio.ruban.data.model.BudgetTier
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.Relation
import com.karelisio.ruban.data.model.Suggestion
import com.karelisio.ruban.data.model.SuggestionType
import java.time.LocalDate
import kotlin.random.Random

data class InspirationFilter(
    val budget: BudgetTier? = null,
    val types: Set<SuggestionType> = emptySet(),
)

data class Scored(val suggestion: Suggestion, val score: Float, val matched: List<String>)

/** Ranks the offline bank for one person, occasion and budget. */
object Inspiration {

    private fun relationMatches(person: Relation, wanted: List<com.karelisio.ruban.data.model.Relation>): Boolean =
        wanted.isEmpty() || person in wanted ||
            (person == Relation.FAMILY && wanted.any { it in setOf(Relation.PARENT, Relation.SIBLING, Relation.GRANDPARENT, Relation.FAMILY) }) ||
            (person == Relation.OTHER && Relation.FRIEND in wanted)

    fun rank(
        bank: List<Suggestion>,
        person: Person,
        occasion: EventKind?,
        filter: InspirationFilter,
        exclude: Set<String>,
        today: LocalDate = LocalDate.now(),
    ): List<Scored> {
        val interests = person.interests.mapNotNull { Interests.resolve(it)?.id ?: it }.toSet()
        val avoidTokens = TextMatch.tokens(person.avoid + " " + person.doNotGive).filter { it.length > 3 }.toSet()
        // Stable shuffle per person and day: suggestions vary, but not on every recomposition.
        val random = Random(person.id * 31 + today.toEpochDay())

        return bank.asSequence()
            .filter { it.id !in exclude }
            .filter { filter.budget == null || it.budget == filter.budget }
            .filter { filter.types.isEmpty() || it.type in filter.types }
            .filter { relationMatches(person.relation, it.relations) }
            .filter { s -> occasion == null || s.occasions.isEmpty() || occasion in s.occasions }
            .filter { s -> avoidTokens.isEmpty() || TextMatch.tokens(s.title).none { it in avoidTokens } }
            .map { s ->
                val matched = s.interests.filter { it in interests }
                var score = matched.size * 3f
                if (s.interests.isEmpty()) score += 0.8f
                if (s.relations.isNotEmpty()) score += 1.5f
                if (occasion != null && occasion in s.occasions) score += 2f
                if (interests.isNotEmpty() && matched.isEmpty() && s.interests.isNotEmpty()) score -= 1.5f
                score += random.nextFloat() * 1.2f
                Scored(s, score, matched)
            }
            .sortedByDescending { it.score }
            .toList()
    }

    fun emojiFor(s: Suggestion): String =
        s.interests.firstNotNullOfOrNull { Interests.get(it)?.emoji } ?: s.type.emoji
}
