package com.karelisio.ruban.domain

import java.text.Normalizer

object TextMatch {
    private val stopWords = setOf(
        "le", "la", "les", "un", "une", "des", "de", "du", "d", "l", "et", "en", "pour", "avec", "a", "au", "aux", "the", "of",
    )

    fun normalize(text: String): String =
        Normalizer.normalize(text.lowercase(), Normalizer.Form.NFD)
            .replace(Regex("\\p{M}+"), "")
            .replace(Regex("[^a-z0-9]+"), " ")
            .trim()

    fun tokens(text: String): Set<String> =
        normalize(text).split(' ').filter { it.length > 1 && it !in stopWords }.toSet()

    /** Loose similarity used to detect gifts that were already given. */
    fun similar(a: String, b: String): Boolean {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return false
        if (na == nb || (na.length >= 5 && nb.contains(na)) || (nb.length >= 5 && na.contains(nb))) return true
        val ta = tokens(a)
        val tb = tokens(b)
        if (ta.isEmpty() || tb.isEmpty()) return false
        val common = ta.intersect(tb).size.toFloat()
        return common / minOf(ta.size, tb.size) >= 0.75f && common >= 2 || common / ta.union(tb).size >= 0.6f
    }

    fun matches(query: String, vararg fields: String): Boolean {
        val q = normalize(query)
        if (q.isEmpty()) return true
        return fields.any { normalize(it).contains(q) }
    }
}
