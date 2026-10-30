package com.karelisio.ruban.data.repo

import androidx.room.withTransaction
import com.karelisio.ruban.data.db.RubanDatabase
import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.model.Idea
import com.karelisio.ruban.data.model.IdeaWithPeople
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.ReceivedGift
import com.karelisio.ruban.domain.EventDates
import com.karelisio.ruban.domain.Occurrence
import com.karelisio.ruban.domain.TextMatch
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flow
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime

/** Emits today's date, then again right after each midnight. */
fun todayFlow(): Flow<LocalDate> = flow {
    while (true) {
        emit(LocalDate.now())
        val now = LocalDateTime.now()
        val nextMidnight = now.toLocalDate().plusDays(1).atStartOfDay()
        delay(Duration.between(now, nextMidnight).toMillis() + 1_000)
    }
}.distinctUntilChanged()

class PersonRepository(private val db: RubanDatabase, private val photos: PhotoStore) {
    private val dao = db.persons()

    fun observeAll() = dao.observeAll()
    fun observe(id: Long) = dao.observe(id)
    suspend fun get(id: Long) = dao.get(id)
    suspend fun all() = dao.all()
    suspend fun byContactKey(key: String) = dao.byContactKey(key)

    /** Inserts or updates [person] and keeps its birthday / name-day events in sync. */
    suspend fun save(person: Person): Long = db.withTransaction {
        val id = if (person.id == 0L) dao.insert(person) else person.id.also { dao.update(person) }
        val saved = person.copy(id = id)
        syncEvent(saved, EventKind.BIRTHDAY, saved.birthdayMonth != null && saved.birthdayDay != null)
        syncEvent(saved, EventKind.NAME_DAY, saved.nameDayMonth != null && saved.nameDayDay != null)
        id
    }

    private suspend fun syncEvent(person: Person, kind: EventKind, wanted: Boolean) {
        val events = db.events()
        val existing = events.forPerson(person.id, kind)
        when {
            wanted && existing == null -> events.insert(Event(kind = kind, title = kind.label, personId = person.id))
            !wanted && existing != null -> events.delete(existing)
        }
    }

    suspend fun delete(person: Person) {
        dao.delete(person)
        photos.delete(person.photo)
    }
}

class EventRepository(private val db: RubanDatabase) {
    private val dao = db.events()

    fun observeAll() = dao.observeAll()
    fun observe(id: Long) = dao.observe(id)
    fun observeForPerson(personId: Long) = dao.observeForPerson(personId)
    suspend fun get(id: Long) = dao.get(id)
    suspend fun all() = dao.all()

    /** One upcoming occurrence per event, refreshed every day. */
    fun observeUpcoming(): Flow<List<Occurrence>> =
        combine(dao.observeAll(), db.persons().observeAll(), todayFlow()) { events, persons, today ->
            EventDates.upcoming(events, persons.associateBy { it.id }, today)
        }

    suspend fun save(event: Event): Long =
        if (event.id == 0L) dao.insert(event) else event.id.also { dao.update(event) }

    suspend fun delete(event: Event) = dao.delete(event)

    /** Creates the recurring French holidays once. */
    suspend fun seedHolidays() {
        listOf(EventKind.CHRISTMAS, EventKind.VALENTINE, EventKind.MOTHERS_DAY, EventKind.FATHERS_DAY).forEach { kind ->
            if (dao.countKind(kind) == 0) dao.insert(Event(kind = kind, title = kind.label))
        }
    }
}

data class AlreadyGiven(val person: Person, val idea: Idea)

class IdeaRepository(private val db: RubanDatabase, private val photos: PhotoStore) {
    private val dao = db.ideas()

    fun observeAll() = dao.observeAll()
    fun observe(id: Long) = dao.observe(id)
    fun observeForPerson(personId: Long) = dao.observeForPerson(personId)
    suspend fun get(id: Long) = dao.getWithPeople(id)
    suspend fun forEvent(eventId: Long) = dao.forEvent(eventId)
    suspend fun allWithPeople() = dao.allWithPeople()

    suspend fun save(idea: Idea, personIds: Collection<Long>): Long = db.withTransaction {
        val stamped = idea.copy(updatedAt = System.currentTimeMillis())
        val id = if (idea.id == 0L) dao.insert(stamped) else idea.id.also { dao.update(stamped) }
        dao.replaceLinks(id, if (idea.forMe) emptyList() else personIds)
        id
    }

    /** Moves an idea along the gift pipeline, keeping the matching timestamps. */
    suspend fun setStatus(id: Long, status: GiftStatus) {
        val idea = dao.get(id) ?: return
        val now = System.currentTimeMillis()
        dao.update(
            idea.copy(
                status = status,
                boughtAt = if (status >= GiftStatus.BOUGHT) idea.boughtAt ?: now else null,
                wrappedAt = if (status >= GiftStatus.WRAPPED) idea.wrappedAt ?: now else null,
                givenAt = if (status >= GiftStatus.GIVEN) idea.givenAt ?: now else null,
                updatedAt = now,
            ),
        )
    }

    suspend fun update(idea: Idea) = dao.update(idea.copy(updatedAt = System.currentTimeMillis()))

    suspend fun delete(item: IdeaWithPeople) {
        dao.delete(item.idea.id)
        photos.delete(item.idea.image)
        photos.delete(item.idea.momentPhoto)
    }

    /** Gifts already given to any of [personIds] that look like [title] / [url]. */
    suspend fun alreadyGiven(personIds: Collection<Long>, title: String, url: String?, excludeId: Long = 0): List<AlreadyGiven> =
        personIds.flatMap { pid ->
            dao.givenTo(pid)
                .filter { it.idea.id != excludeId }
                .filter { (url != null && it.idea.url == url) || TextMatch.similar(it.idea.title, title) }
                .mapNotNull { given -> given.people.firstOrNull { it.id == pid }?.let { AlreadyGiven(it, given.idea) } }
        }
}

class ReceivedRepository(private val db: RubanDatabase, private val photos: PhotoStore) {
    private val dao = db.received()
    fun observeAll() = dao.observeAll()
    suspend fun get(id: Long) = dao.get(id)
    suspend fun save(gift: ReceivedGift): Long = dao.upsert(gift).let { if (gift.id != 0L) gift.id else it }
    suspend fun delete(gift: ReceivedGift) {
        dao.delete(gift)
        photos.delete(gift.photo)
    }
}
