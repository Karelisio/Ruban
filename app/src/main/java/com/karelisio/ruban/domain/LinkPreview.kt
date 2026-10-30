package com.karelisio.ruban.domain

/** What could be read from a product page. */
data class LinkPreview(
    val title: String? = null,
    val imageUrl: String? = null,
    val priceCents: Long? = null,
    val currency: String? = null,
    val siteName: String? = null,
    val canonicalUrl: String? = null,
) {
    val isEmpty: Boolean get() = title == null && imageUrl == null && priceCents == null

    companion object {
        private val urlRegex = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)

        fun findUrl(text: String): String? = urlRegex.find(text)?.value?.trimEnd('.', ',', ')', ']', '!', '?')

        /** "1 299,99", "29.90", "29,90 €", "1,299.00" → cents. */
        fun parsePrice(raw: String?): Long? {
            if (raw.isNullOrBlank()) return null
            val s = raw.replace(' ', ' ').replace(Regex("[^0-9.,]"), "")
            if (s.isEmpty()) return null
            val lastDot = s.lastIndexOf('.')
            val lastComma = s.lastIndexOf(',')
            val decimalSep = maxOf(lastDot, lastComma)
            val normalized = if (decimalSep >= 0 && s.length - decimalSep - 1 in 1..2) {
                s.substring(0, decimalSep).replace(Regex("[.,]"), "") + "." + s.substring(decimalSep + 1)
            } else s.replace(Regex("[.,]"), "")
            val value = normalized.toBigDecimalOrNull() ?: return null
            return value.movePointRight(2).toLong().takeIf { it in 1..100_000_000 }
        }

        fun normalizeCurrency(raw: String?): String? = when (val r = raw?.trim()?.uppercase()) {
            null, "" -> null
            "€", "EURO", "EUROS" -> "EUR"
            "$", "US$" -> "USD"
            "£" -> "GBP"
            else -> r.takeIf { it.length == 3 && it.all(Char::isLetter) }
        }
    }
}
