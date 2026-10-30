package com.karelisio.ruban.data.repo

import com.karelisio.ruban.data.db.RubanDatabase
import com.karelisio.ruban.data.model.Idea
import com.karelisio.ruban.data.model.PricePoint
import com.karelisio.ruban.data.net.LinkExtractor

data class PriceChange(val idea: Idea, val before: Long?, val after: Long)

/** Re-reads the price of an idea's page and records it in the history. */
class PriceTracker(private val db: RubanDatabase, private val links: LinkExtractor) {

    suspend fun check(idea: Idea): PriceChange? {
        val url = idea.url ?: return null
        val (cents, currency) = links.fetchPrice(url) ?: return null
        if (currency != null && currency != idea.currency) return null
        val prices = db.prices()
        val history = prices.forIdea(idea.id)
        val previous = history.lastOrNull()?.priceCents ?: idea.priceCents
        val now = System.currentTimeMillis()
        val stale = history.lastOrNull()?.let { now - it.checkedAt > 7 * 86_400_000L } ?: true
        if (previous != cents || stale) {
            prices.insert(PricePoint(ideaId = idea.id, priceCents = cents, currency = idea.currency, checkedAt = now))
        }
        db.ideas().update(idea.copy(priceCents = cents, lastPriceCheckAt = now))
        return PriceChange(idea, previous, cents)
    }
}
