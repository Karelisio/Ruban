package com.karelisio.ruban.data.db

import androidx.room.TypeConverter
import com.karelisio.ruban.data.model.EventKind
import com.karelisio.ruban.data.model.Relation
import kotlinx.serialization.json.Json

class Converters {
    private val json = Json { ignoreUnknownKeys = true }

    @TypeConverter fun stringsToJson(value: List<String>): String = json.encodeToString(value)
    @TypeConverter fun jsonToStrings(value: String): List<String> = json.decodeFromString(value)

    @TypeConverter fun kindsToJson(value: List<EventKind>): String = json.encodeToString(value)
    @TypeConverter fun jsonToKinds(value: String): List<EventKind> = json.decodeFromString(value)

    @TypeConverter fun relationsToJson(value: List<Relation>): String = json.encodeToString(value)
    @TypeConverter fun jsonToRelations(value: String): List<Relation> = json.decodeFromString(value)
}
