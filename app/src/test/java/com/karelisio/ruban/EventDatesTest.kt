package com.karelisio.ruban

import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.domain.EventDates
import org.junit.Assert.assertEquals
import org.junit.Test
import java.time.LocalDate

class EventDatesTest {
    @Test fun easter() {
        assertEquals(LocalDate.of(2024, 3, 31), EventDates.easter(2024))
        assertEquals(LocalDate.of(2025, 4, 20), EventDates.easter(2025))
        assertEquals(LocalDate.of(2026, 4, 5), EventDates.easter(2026))
    }

    @Test fun mothersDay() {
        assertEquals(LocalDate.of(2025, 5, 25), EventDates.mothersDay(2025))
        assertEquals(LocalDate.of(2026, 5, 31), EventDates.mothersDay(2026))
        // 2024: last Sunday of May (26) is not Pentecost (19 May).
        assertEquals(LocalDate.of(2024, 5, 26), EventDates.mothersDay(2024))
        // 2031: Pentecost falls on 1 June? Easter 13 Apr → Pentecost 1 Jun; last Sunday of May = 25 May.
        assertEquals(LocalDate.of(2031, 5, 25), EventDates.mothersDay(2031))
        // 2027: Easter 28 Mar → Pentecost 16 May; last Sunday of May = 30 May.
        assertEquals(LocalDate.of(2027, 5, 30), EventDates.mothersDay(2027))
        // 2032: Easter 28 Mar → Pentecost 16 May; 2038: Easter 25 Apr → Pentecost 13 Jun.
        // 2015: Easter 5 Apr → Pentecost 24 May = last Sunday of May → moved to 31 May.
        assertEquals(LocalDate.of(2015, 5, 31), EventDates.mothersDay(2015))
    }

    @Test fun fathersDay() {
        assertEquals(LocalDate.of(2025, 6, 15), EventDates.fathersDay(2025))
        assertEquals(LocalDate.of(2026, 6, 21), EventDates.fathersDay(2026))
    }

    @Test fun birthdayNextOccurrenceAndLeapYear() {
        val p = Person(id = 1, name = "Léa", birthdayMonth = 2, birthdayDay = 29, birthYear = 2000)
        val e = Event(id = 1, kind = EventKind.BIRTHDAY, title = "", personId = 1)
        assertEquals(LocalDate.of(2027, 2, 28), EventDates.next(e, p, LocalDate.of(2026, 10, 30)))
        assertEquals(LocalDate.of(2028, 2, 29), EventDates.next(e, p, LocalDate.of(2027, 3, 1)))
    }

    @Test fun oneOffCustomEvent() {
        val e = Event(id = 2, kind = EventKind.CUSTOM, title = "Crémaillère", month = 11, day = 14, year = 2026, recurring = false)
        assertEquals(LocalDate.of(2026, 11, 14), EventDates.next(e, null, LocalDate.of(2026, 10, 30)))
        assertEquals(null, EventDates.next(e, null, LocalDate.of(2026, 11, 15)))
    }
}
