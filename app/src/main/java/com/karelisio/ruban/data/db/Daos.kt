package com.karelisio.ruban.data.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import androidx.room.Upsert
import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.data.model.GiftStatus
import com.karelisio.ruban.data.model.Idea
import com.karelisio.ruban.data.model.IdeaPerson
import com.karelisio.ruban.data.model.IdeaWithPeople
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.PricePoint
import com.karelisio.ruban.data.model.ReceivedGift
import com.karelisio.ruban.data.model.Suggestion
import com.karelisio.ruban.data.model.SuggestionVerdict
import kotlinx.coroutines.flow.Flow

@Dao
interface PersonDao {
    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE")
    fun observeAll(): Flow<List<Person>>

    @Query("SELECT * FROM persons ORDER BY name COLLATE NOCASE")
    suspend fun all(): List<Person>

    @Query("SELECT * FROM persons WHERE id = :id")
    fun observe(id: Long): Flow<Person?>

    @Query("SELECT * FROM persons WHERE id = :id")
    suspend fun get(id: Long): Person?

    @Query("SELECT * FROM persons WHERE contactLookupKey = :key LIMIT 1")
    suspend fun byContactKey(key: String): Person?

    @Insert suspend fun insert(person: Person): Long
    @Update suspend fun update(person: Person)
    @Delete suspend fun delete(person: Person)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<Person>)
    @Query("DELETE FROM persons") suspend fun clear()
}

@Dao
interface EventDao {
    @Query("SELECT * FROM events")
    fun observeAll(): Flow<List<Event>>

    @Query("SELECT * FROM events")
    suspend fun all(): List<Event>

    @Query("SELECT * FROM events WHERE id = :id")
    fun observe(id: Long): Flow<Event?>

    @Query("SELECT * FROM events WHERE id = :id")
    suspend fun get(id: Long): Event?

    @Query("SELECT * FROM events WHERE personId = :personId AND kind = :kind LIMIT 1")
    suspend fun forPerson(personId: Long, kind: EventKind): Event?

    @Query("SELECT * FROM events WHERE personId = :personId")
    fun observeForPerson(personId: Long): Flow<List<Event>>

    @Query("SELECT COUNT(*) FROM events WHERE kind = :kind")
    suspend fun countKind(kind: EventKind): Int

    @Insert suspend fun insert(event: Event): Long
    @Update suspend fun update(event: Event)
    @Delete suspend fun delete(event: Event)

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<Event>)
    @Query("DELETE FROM events") suspend fun clear()
}

@Dao
interface IdeaDao {
    @Transaction
    @Query("SELECT * FROM ideas ORDER BY updatedAt DESC")
    fun observeAll(): Flow<List<IdeaWithPeople>>

    @Transaction
    @Query("SELECT * FROM ideas WHERE id = :id")
    fun observe(id: Long): Flow<IdeaWithPeople?>

    @Transaction
    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun getWithPeople(id: Long): IdeaWithPeople?

    @Query("SELECT * FROM ideas WHERE id = :id")
    suspend fun get(id: Long): Idea?

    @Transaction
    @Query("SELECT ideas.* FROM ideas INNER JOIN idea_person ON ideas.id = idea_person.ideaId WHERE idea_person.personId = :personId ORDER BY ideas.updatedAt DESC")
    fun observeForPerson(personId: Long): Flow<List<IdeaWithPeople>>

    @Transaction
    @Query("SELECT ideas.* FROM ideas INNER JOIN idea_person ON ideas.id = idea_person.ideaId WHERE idea_person.personId = :personId AND ideas.status = 'GIVEN'")
    suspend fun givenTo(personId: Long): List<IdeaWithPeople>

    @Transaction
    @Query("SELECT * FROM ideas WHERE eventId = :eventId")
    suspend fun forEvent(eventId: Long): List<IdeaWithPeople>

    @Transaction
    @Query("SELECT * FROM ideas")
    suspend fun allWithPeople(): List<IdeaWithPeople>

    @Query("SELECT * FROM ideas WHERE status = 'IDEA' AND priceTracking = 1 AND url IS NOT NULL")
    suspend fun trackable(): List<Idea>

    @Query("SELECT * FROM ideas") suspend fun all(): List<Idea>
    @Query("SELECT * FROM idea_person") suspend fun allLinks(): List<IdeaPerson>

    @Insert suspend fun insert(idea: Idea): Long
    @Update suspend fun update(idea: Idea)
    @Query("DELETE FROM ideas WHERE id = :id") suspend fun delete(id: Long)

    @Query("UPDATE ideas SET status = :status, updatedAt = :now WHERE id = :id")
    suspend fun setStatus(id: Long, status: GiftStatus, now: Long = System.currentTimeMillis())

    @Insert(onConflict = OnConflictStrategy.IGNORE) suspend fun link(links: List<IdeaPerson>)
    @Query("DELETE FROM idea_person WHERE ideaId = :ideaId") suspend fun unlinkAll(ideaId: Long)

    @Transaction
    suspend fun replaceLinks(ideaId: Long, personIds: Collection<Long>) {
        unlinkAll(ideaId)
        link(personIds.map { IdeaPerson(ideaId, it) })
    }

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<Idea>)
    @Query("DELETE FROM ideas") suspend fun clear()
}

@Dao
interface PriceDao {
    @Query("SELECT * FROM price_points WHERE ideaId = :ideaId ORDER BY checkedAt")
    fun observe(ideaId: Long): Flow<List<PricePoint>>

    @Query("SELECT * FROM price_points ORDER BY checkedAt")
    fun observeAll(): Flow<List<PricePoint>>

    @Query("SELECT * FROM price_points WHERE ideaId = :ideaId ORDER BY checkedAt")
    suspend fun forIdea(ideaId: Long): List<PricePoint>

    @Insert suspend fun insert(point: PricePoint): Long
    @Query("SELECT * FROM price_points") suspend fun all(): List<PricePoint>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<PricePoint>)
    @Query("DELETE FROM price_points") suspend fun clear()
}

@Dao
interface ReceivedDao {
    @Query("SELECT * FROM received_gifts ORDER BY receivedOn DESC")
    fun observeAll(): Flow<List<ReceivedGift>>

    @Query("SELECT * FROM received_gifts WHERE id = :id")
    suspend fun get(id: Long): ReceivedGift?

    @Upsert suspend fun upsert(gift: ReceivedGift): Long
    @Delete suspend fun delete(gift: ReceivedGift)

    @Query("SELECT * FROM received_gifts") suspend fun all(): List<ReceivedGift>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<ReceivedGift>)
    @Query("DELETE FROM received_gifts") suspend fun clear()
}

@Dao
interface SuggestionDao {
    @Query("SELECT COUNT(*) FROM suggestions") suspend fun count(): Int
    @Query("SELECT * FROM suggestions") suspend fun all(): List<Suggestion>
    @Query("SELECT * FROM suggestions WHERE id = :id") suspend fun get(id: String): Suggestion?
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertAll(items: List<Suggestion>)
    @Query("DELETE FROM suggestions WHERE id NOT IN (:keep)") suspend fun deleteOthers(keep: List<String>)

    @Query("SELECT * FROM suggestion_verdicts WHERE personId = :personId")
    suspend fun verdicts(personId: Long): List<SuggestionVerdict>

    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun setVerdict(verdict: SuggestionVerdict)
    @Query("DELETE FROM suggestion_verdicts WHERE personId = :personId AND kept = 0") suspend fun resetSkipped(personId: Long)
    @Query("DELETE FROM suggestion_verdicts WHERE personId = :personId AND suggestionId = :suggestionId") suspend fun deleteVerdict(personId: Long, suggestionId: String)

    @Query("SELECT * FROM suggestion_verdicts") suspend fun allVerdicts(): List<SuggestionVerdict>
    @Insert(onConflict = OnConflictStrategy.REPLACE) suspend fun insertVerdicts(items: List<SuggestionVerdict>)
    @Query("DELETE FROM suggestion_verdicts") suspend fun clearVerdicts()
}
