package com.karelisio.ruban.data.model

import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.Junction
import androidx.room.PrimaryKey
import androidx.room.Relation as RoomRelation
import kotlinx.serialization.Serializable

/** Photos are stored as file names inside `filesDir/photos` (see PhotoStore). */
@Serializable
@Entity(tableName = "persons", indices = [Index("name")])
data class Person(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val name: String,
    val photo: String? = null,
    val relation: Relation = Relation.FRIEND,
    val birthdayMonth: Int? = null,
    val birthdayDay: Int? = null,
    val birthYear: Int? = null,
    val nameDayMonth: Int? = null,
    val nameDayDay: Int? = null,
    val clothingSize: String = "",
    val shoeSize: String = "",
    val ringSize: String = "",
    val interests: List<String> = emptyList(),
    val favoriteBrands: String = "",
    val favoriteColors: String = "",
    val avoid: String = "",
    val doNotGive: String = "",
    val notes: String = "",
    val yearlyBudgetCents: Long? = null,
    val contactLookupKey: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "events",
    indices = [Index("personId")],
    foreignKeys = [ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE)],
)
data class Event(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val kind: EventKind,
    val title: String,
    val personId: Long? = null,
    /** Month/day for recurring custom events; null for computed holidays and person-derived events. */
    val month: Int? = null,
    val day: Int? = null,
    /** Year of the first occurrence (anniversaries) or of the single occurrence (non recurring). */
    val year: Int? = null,
    val recurring: Boolean = true,
    val budgetCents: Long? = null,
    /** Comma separated day offsets, e.g. "30,7,1". Null = global setting. */
    val reminderOffsets: String? = null,
    val remindersEnabled: Boolean = true,
    val emoji: String? = null,
    val notes: String = "",
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "ideas",
    indices = [Index("eventId"), Index("status")],
    foreignKeys = [ForeignKey(Event::class, ["id"], ["eventId"], onDelete = ForeignKey.SET_NULL)],
)
data class Idea(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val notes: String = "",
    val url: String? = null,
    val image: String? = null,
    val priceCents: Long? = null,
    val currency: String = "EUR",
    val priority: Priority = Priority.NICE,
    val status: GiftStatus = GiftStatus.IDEA,
    val forMe: Boolean = false,
    val eventId: Long? = null,
    /** Year of the event occurrence this idea is planned for. */
    val occasionYear: Int? = null,
    /** Free occasion label when no event is linked ("Pour le plaisir"…). */
    val occasionLabel: String? = null,
    val priceAlertCents: Long? = null,
    val priceTracking: Boolean = true,
    val lastPriceCheckAt: Long? = null,
    val boughtAt: Long? = null,
    val wrappedAt: Long? = null,
    val givenAt: Long? = null,
    val reactionEmoji: String? = null,
    val reactionNote: String = "",
    val momentPhoto: String? = null,
    val suggestionId: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
) {
    /** Amount counted as spent once bought. */
    val spentCents: Long get() = if (status >= GiftStatus.BOUGHT) priceCents ?: 0 else 0
}

@Serializable
@Entity(
    tableName = "idea_person",
    primaryKeys = ["ideaId", "personId"],
    indices = [Index("personId")],
    foreignKeys = [
        ForeignKey(Idea::class, ["id"], ["ideaId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class IdeaPerson(val ideaId: Long, val personId: Long)

@Serializable
@Entity(
    tableName = "price_points",
    indices = [Index("ideaId")],
    foreignKeys = [ForeignKey(Idea::class, ["id"], ["ideaId"], onDelete = ForeignKey.CASCADE)],
)
data class PricePoint(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ideaId: Long,
    val priceCents: Long,
    val currency: String,
    val checkedAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(
    tableName = "received_gifts",
    indices = [Index("fromPersonId")],
    foreignKeys = [ForeignKey(Person::class, ["id"], ["fromPersonId"], onDelete = ForeignKey.SET_NULL)],
)
data class ReceivedGift(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val fromPersonId: Long? = null,
    val fromName: String = "",
    /** Epoch day. */
    val receivedOn: Long,
    val occasion: EventKind? = null,
    val occasionLabel: String = "",
    val notes: String = "",
    val photo: String? = null,
    val thanked: Boolean = false,
    val createdAt: Long = System.currentTimeMillis(),
)

@Serializable
@Entity(tableName = "suggestions")
data class Suggestion(
    @PrimaryKey val id: String,
    val title: String,
    val description: String = "",
    val interests: List<String> = emptyList(),
    val budget: BudgetTier,
    val occasions: List<EventKind> = emptyList(),
    val type: SuggestionType,
    val relations: List<Relation> = emptyList(),
)

@Serializable
@Entity(
    tableName = "suggestion_verdicts",
    primaryKeys = ["personId", "suggestionId"],
    indices = [Index("suggestionId")],
    foreignKeys = [
        ForeignKey(Person::class, ["id"], ["personId"], onDelete = ForeignKey.CASCADE),
        ForeignKey(Suggestion::class, ["id"], ["suggestionId"], onDelete = ForeignKey.CASCADE),
    ],
)
data class SuggestionVerdict(
    val personId: Long,
    val suggestionId: String,
    val kept: Boolean,
    val at: Long = System.currentTimeMillis(),
)

/** An idea with the people it is meant for. */
data class IdeaWithPeople(
    @Embedded val idea: Idea,
    @RoomRelation(
        parentColumn = "id",
        entityColumn = "id",
        associateBy = Junction(IdeaPerson::class, parentColumn = "ideaId", entityColumn = "personId"),
    )
    val people: List<Person>,
) {
    val unassigned: Boolean get() = people.isEmpty() && !idea.forMe
}
