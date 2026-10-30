package com.karelisio.ruban.util

import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Currency
import java.util.Locale

val FR: Locale = Locale.FRANCE

object Money {
    fun format(cents: Long, currency: String = "EUR", forceDecimals: Boolean = false): String {
        val nf = NumberFormat.getCurrencyInstance(FR)
        runCatching { nf.currency = Currency.getInstance(currency) }
        val whole = cents % 100 == 0L && !forceDecimals
        nf.minimumFractionDigits = if (whole) 0 else 2
        nf.maximumFractionDigits = if (whole) 0 else 2
        return nf.format(cents / 100.0)
    }

    fun formatOrNull(cents: Long?, currency: String = "EUR"): String? = cents?.let { format(it, currency) }

    /** Parses "12", "12,5", "1 299,99 €", "12.50" into cents. */
    fun parse(text: String): Long? {
        val cleaned = text.replace(Regex("[^0-9,.]"), "")
        if (cleaned.isEmpty()) return null
        val lastSep = maxOf(cleaned.lastIndexOf(','), cleaned.lastIndexOf('.'))
        val normalized = if (lastSep >= 0 && cleaned.length - lastSep - 1 in 1..2) {
            cleaned.substring(0, lastSep).replace(Regex("[,.]"), "") + "." + cleaned.substring(lastSep + 1)
        } else cleaned.replace(Regex("[,.]"), "")
        return normalized.toBigDecimalOrNull()?.movePointRight(2)?.toLong()
    }

    /** Text suitable for an input field ("12,50"). */
    fun input(cents: Long?): String = cents?.let {
        if (it % 100 == 0L) (it / 100).toString() else "%d,%02d".format(it / 100, it % 100)
    } ?: ""

    fun symbol(currency: String): String = runCatching { Currency.getInstance(currency).getSymbol(FR) }.getOrDefault(currency)
}

object Dates {
    private val full = DateTimeFormatter.ofPattern("d MMMM yyyy", FR)
    private val dayMonth = DateTimeFormatter.ofPattern("d MMMM", FR)
    private val weekdayDayMonth = DateTimeFormatter.ofPattern("EEEE d MMMM", FR)

    fun full(date: LocalDate): String = date.format(full)
    fun dayMonth(date: LocalDate): String = date.format(dayMonth)
    fun weekday(date: LocalDate): String = date.format(weekdayDayMonth).replaceFirstChar { it.uppercase() }
    fun monthName(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.FULL, FR)
    fun monthShort(month: Int): String = java.time.Month.of(month).getDisplayName(TextStyle.SHORT, FR)
    fun dayMonth(month: Int, day: Int): String = "$day ${monthName(month)}"

    fun fromMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).toLocalDate()
    fun toMillis(date: LocalDate): Long = date.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
    fun fullMillis(millis: Long): String = full(fromMillis(millis))

    /** UTC millis used by Material date pickers. */
    fun toPickerMillis(date: LocalDate): Long = date.atStartOfDay(ZoneId.of("UTC")).toInstant().toEpochMilli()
    fun fromPickerMillis(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(ZoneId.of("UTC")).toLocalDate()

    fun relative(days: Long): String = when {
        days == 0L -> "Aujourd'hui"
        days == 1L -> "Demain"
        days == -1L -> "Hier"
        days < 0 -> "Il y a ${-days} jours"
        days < 14 -> "Dans $days jours"
        days < 60 -> "Dans ${days / 7} semaine${if (days / 7 > 1) "s" else ""}"
        else -> "Dans ${days / 30} mois"
    }
}

fun Int.plural(singular: String, plural: String = singular + "s") = "$this ${if (this > 1) plural else singular}"
