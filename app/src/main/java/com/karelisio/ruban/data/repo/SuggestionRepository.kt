package com.karelisio.ruban.data.repo

import android.content.Context
import com.karelisio.ruban.data.db.RubanDatabase
import com.karelisio.ruban.data.model.Suggestion
import com.karelisio.ruban.data.model.SuggestionVerdict
import com.karelisio.ruban.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

@Serializable
private data class SuggestionBank(val version: Int, val items: List<Suggestion>)

/** Embedded, offline bank of gift ideas (assets/suggestions.json), copied into Room. */
class SuggestionRepository(
    private val context: Context,
    private val db: RubanDatabase,
    private val settings: SettingsRepository,
) {
    private val dao = db.suggestions()
    private val json = Json { ignoreUnknownKeys = true }

    /** Imports the bank when the embedded version changed. */
    suspend fun ensureLoaded() = withContext(Dispatchers.IO) {
        val bank = context.assets.open("suggestions.json").use { json.decodeFromString<SuggestionBank>(it.readBytes().decodeToString()) }
        if (settings.current().suggestionsVersion == bank.version && dao.count() == bank.items.size) return@withContext
        dao.insertAll(bank.items)
        dao.deleteOthers(bank.items.map { it.id })
        settings.setSuggestionsVersion(bank.version)
    }

    suspend fun all(): List<Suggestion> {
        if (dao.count() == 0) ensureLoaded()
        return dao.all()
    }

    suspend fun verdicts(personId: Long) = dao.verdicts(personId)
    suspend fun setVerdict(personId: Long, suggestionId: String, kept: Boolean) =
        dao.setVerdict(SuggestionVerdict(personId, suggestionId, kept))
    suspend fun resetSkipped(personId: Long) = dao.resetSkipped(personId)
    suspend fun undo(personId: Long, suggestionId: String) = dao.deleteVerdict(personId, suggestionId)
}
