package com.karelisio.ruban.domain

/** Interest tags shared by people profiles and the suggestion bank. */
object Interests {
    data class Tag(val id: String, val label: String, val emoji: String)

    val all: List<Tag> = listOf(
        Tag("cuisine", "Cuisine", "🍳"),
        Tag("patisserie", "Pâtisserie", "🧁"),
        Tag("gastronomie", "Gastronomie", "🍽️"),
        Tag("vin", "Vin", "🍷"),
        Tag("biere", "Bière", "🍺"),
        Tag("cocktails", "Cocktails", "🍸"),
        Tag("cafe", "Café", "☕"),
        Tag("the", "Thé", "🍵"),
        Tag("chocolat", "Chocolat", "🍫"),
        Tag("lecture", "Lecture", "📚"),
        Tag("bd", "BD & mangas", "💬"),
        Tag("ecriture", "Écriture", "✍️"),
        Tag("papeterie", "Papeterie", "📓"),
        Tag("musique", "Musique", "🎵"),
        Tag("vinyles", "Vinyles", "💿"),
        Tag("cinema", "Cinéma & séries", "🎬"),
        Tag("theatre", "Spectacles", "🎭"),
        Tag("jeux_video", "Jeux vidéo", "🎮"),
        Tag("jeux_societe", "Jeux de société", "🎲"),
        Tag("puzzles", "Puzzles & casse-têtes", "🧩"),
        Tag("lego", "Construction & LEGO", "🧱"),
        Tag("tech", "High-tech", "📱"),
        Tag("photo", "Photo", "📷"),
        Tag("art", "Art & dessin", "🎨"),
        Tag("couture", "Couture & tricot", "🧶"),
        Tag("bricolage", "Bricolage", "🛠️"),
        Tag("deco", "Déco & maison", "🛋️"),
        Tag("jardinage", "Jardinage", "🌱"),
        Tag("plantes", "Plantes d'intérieur", "🪴"),
        Tag("mode", "Mode", "👗"),
        Tag("beaute", "Beauté & soins", "💄"),
        Tag("bijoux", "Bijoux", "💍"),
        Tag("bien_etre", "Bien-être & yoga", "🧘"),
        Tag("sport", "Sport & fitness", "🏋️"),
        Tag("running", "Course à pied", "👟"),
        Tag("velo", "Vélo", "🚲"),
        Tag("randonnee", "Randonnée & nature", "🥾"),
        Tag("montagne", "Montagne & ski", "🏔️"),
        Tag("mer", "Mer & plage", "🌊"),
        Tag("camping", "Camping & bivouac", "🏕️"),
        Tag("voyage", "Voyage", "✈️"),
        Tag("football", "Football", "⚽"),
        Tag("tennis", "Raquettes", "🎾"),
        Tag("golf", "Golf", "⛳"),
        Tag("peche", "Pêche", "🎣"),
        Tag("danse", "Danse", "💃"),
        Tag("animaux", "Animaux", "🐾"),
        Tag("ecologie", "Écologie & zéro déchet", "♻️"),
        Tag("sciences", "Sciences", "🔬"),
        Tag("astronomie", "Astronomie", "🔭"),
        Tag("histoire", "Histoire & patrimoine", "🏛️"),
        Tag("langues", "Langues", "🗣️"),
        Tag("auto", "Auto & moto", "🏍️"),
        Tag("enfance", "Éveil & jeux d'enfant", "🧸"),
        Tag("bebe", "Bébé", "🍼"),
        Tag("parentalite", "Jeunes parents", "👶"),
        Tag("gaming_retro", "Rétro & vintage", "📼"),
        Tag("collection", "Collection", "🗃️"),
        Tag("spiritualite", "Méditation & spiritualité", "🕯️"),
        Tag("teletravail", "Bureau & télétravail", "💻"),
    )

    private val byId = all.associateBy { it.id }
    private val byNormalizedLabel = all.associateBy { TextMatch.normalize(it.label) }

    fun get(id: String): Tag? = byId[id]

    /** Resolves a free tag typed by the user to a known interest when possible. */
    fun resolve(tag: String): Tag? =
        byId[tag] ?: byNormalizedLabel[TextMatch.normalize(tag)]
            ?: all.firstOrNull { TextMatch.normalize(it.label).startsWith(TextMatch.normalize(tag)) && tag.length >= 4 }

    fun label(tag: String): String = get(tag)?.label ?: tag
    fun emoji(tag: String): String = get(tag)?.emoji ?: "🏷️"
}
