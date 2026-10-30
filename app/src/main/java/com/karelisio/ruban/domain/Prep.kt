package com.karelisio.ruban.domain

import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.model.IdeaWithPeople

/** How ready we are for an occurrence: drives reminders, widget and agenda badges. */
enum class Prep(val label: String, val emoji: String) {
    READY("Cadeau prêt", "🎁"),
    IDEA("Idée trouvée", "💡"),
    NOTHING("Rien encore", "🫥"),
    DONE("Offert", "🎉"),
}

data class PrepState(val prep: Prep, val ideas: List<IdeaWithPeople>) {
    val headline: IdeaWithPeople? get() = ideas.firstOrNull()
}

object Preparation {
    /**
     * Ideas meant for [occurrence]: linked to that event (and year), or — for personal events —
     * ideas for that person without any occasion yet.
     */
    fun of(occurrence: Occurrence, ideas: List<IdeaWithPeople>): PrepState {
        val linked = ideas.filter { it.idea.eventId == occurrence.event.id && (it.idea.occasionYear == null || it.idea.occasionYear == occurrence.date.year) }
        val personId = occurrence.person?.id
        val loose = if (personId != null) {
            ideas.filter { it.idea.eventId == null && it.idea.status != GiftStatus.GIVEN && it.people.any { p -> p.id == personId } }
        } else emptyList()
        val all = (linked + loose).distinctBy { it.idea.id }
        val active = all.filter { it.idea.status != GiftStatus.GIVEN }
        val prep = when {
            linked.isNotEmpty() && linked.all { it.idea.status == GiftStatus.GIVEN } -> Prep.DONE
            active.any { it.idea.status == GiftStatus.BOUGHT || it.idea.status == GiftStatus.WRAPPED } -> Prep.READY
            active.isNotEmpty() -> Prep.IDEA
            else -> Prep.NOTHING
        }
        return PrepState(prep, active.sortedByDescending { it.idea.status })
    }
}
