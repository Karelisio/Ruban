package com.karelisio.ruban.data.model

enum class Relation(val label: String, val emoji: String) {
    PARTNER("Conjoint·e", "💞"),
    PARENT("Parent", "🏡"),
    CHILD("Enfant", "🧸"),
    SIBLING("Frère / sœur", "🤝"),
    GRANDPARENT("Grand-parent", "🌿"),
    FAMILY("Famille", "🌳"),
    FRIEND("Ami·e", "✨"),
    COLLEAGUE("Collègue", "💼"),
    OTHER("Autre", "🎈"),
}

enum class Priority(val label: String, val emoji: String) {
    STRONG("Envie forte", "🔥"),
    NICE("Sympa", "🙂"),
    MAYBE("À voir", "🤔"),
}

enum class GiftStatus(val label: String) {
    IDEA("Idée"),
    BOUGHT("Achetée"),
    WRAPPED("Emballée"),
    GIVEN("Offerte");

    val next: GiftStatus? get() = entries.getOrNull(ordinal + 1)
    val previous: GiftStatus? get() = entries.getOrNull(ordinal - 1)
}

enum class EventKind(val label: String, val emoji: String, val isPersonal: Boolean, val isFixedHoliday: Boolean) {
    BIRTHDAY("Anniversaire", "🎂", true, false),
    NAME_DAY("Fête", "🌸", true, false),
    CHRISTMAS("Noël", "🎄", false, true),
    VALENTINE("Saint-Valentin", "💝", false, true),
    MOTHERS_DAY("Fête des mères", "💐", false, true),
    FATHERS_DAY("Fête des pères", "👔", false, true),
    CUSTOM("Événement", "🎉", false, false),
}

/** Budget brackets used by the suggestion bank. */
enum class BudgetTier(val label: String, val maxCents: Long) {
    SMALL("Moins de 20 €", 20_00),
    MEDIUM("20 à 50 €", 50_00),
    LARGE("50 à 150 €", 150_00),
    PREMIUM("Plus de 150 €", Long.MAX_VALUE);

    companion object {
        fun forCents(cents: Long): BudgetTier = entries.first { cents <= it.maxCents }
    }
}

enum class SuggestionType(val label: String, val emoji: String) {
    OBJECT("Objet", "🎁"),
    EXPERIENCE("Expérience", "🎟️"),
    HANDMADE("Fait main", "🧶"),
    SUBSCRIPTION("Abonnement", "📬"),
}
