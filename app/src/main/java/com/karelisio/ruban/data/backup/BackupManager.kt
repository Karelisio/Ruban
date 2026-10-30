package com.karelisio.ruban.data.backup

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import androidx.room.withTransaction
import com.karelisio.ruban.AppContainer
import com.karelisio.ruban.data.model.Event
import com.karelisio.ruban.data.model.Idea
import com.karelisio.ruban.data.model.IdeaPerson
import com.karelisio.ruban.data.model.Person
import com.karelisio.ruban.data.model.PricePoint
import com.karelisio.ruban.data.model.ReceivedGift
import com.karelisio.ruban.data.model.SuggestionVerdict
import com.karelisio.ruban.data.repo.PhotoStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.ByteArrayInputStream
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.time.LocalDate
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

@Serializable
data class BackupData(
    val format: Int = 1,
    val exportedAt: Long,
    val persons: List<Person>,
    val events: List<Event>,
    val ideas: List<Idea>,
    val links: List<IdeaPerson>,
    val prices: List<PricePoint>,
    val received: List<ReceivedGift>,
    val verdicts: List<SuggestionVerdict>,
)

data class BackupSummary(val persons: Int, val ideas: Int, val photos: Int)

/** Encrypted export / import of everything (data + photos) in a single `.ruban` file. */
class BackupManager(private val context: Context, private val c: AppContainer) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true }

    suspend fun export(out: OutputStream, password: CharArray): BackupSummary = withContext(Dispatchers.IO) {
        val db = c.db
        val data = BackupData(
            exportedAt = System.currentTimeMillis(),
            persons = db.persons().all(),
            events = db.events().all(),
            ideas = db.ideas().all(),
            links = db.ideas().allLinks(),
            prices = db.prices().all(),
            received = db.received().all(),
            verdicts = db.suggestions().allVerdicts(),
        )
        val photos = c.photos.dir.listFiles().orEmpty().filter { it.isFile }
        BackupCrypto.encrypt(out, password).use { encrypted ->
            ZipOutputStream(encrypted).use { zip ->
                zip.putNextEntry(ZipEntry(DATA))
                zip.write(json.encodeToString(BackupData.serializer(), data).toByteArray())
                zip.closeEntry()
                photos.forEach { f ->
                    zip.putNextEntry(ZipEntry("${PhotoStore.PHOTO_DIR}/${f.name}"))
                    f.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
            }
        }
        BackupSummary(data.persons.size, data.ideas.size, photos.size)
    }

    /** Replaces all current data with the backup content. */
    suspend fun import(input: InputStream, password: CharArray): BackupSummary = withContext(Dispatchers.IO) {
        val plain = BackupCrypto.decrypt(input, password)
        val staging = File(context.cacheDir, "restore").apply { deleteRecursively(); mkdirs() }
        var data: BackupData? = null
        ZipInputStream(ByteArrayInputStream(plain)).use { zip ->
            while (true) {
                val entry = zip.nextEntry ?: break
                val name = entry.name
                when {
                    name == DATA -> data = json.decodeFromString(BackupData.serializer(), zip.readBytes().decodeToString())
                    name.startsWith("${PhotoStore.PHOTO_DIR}/") && !name.contains("..") -> {
                        File(staging, name.substringAfterLast('/')).outputStream().use { zip.copyTo(it) }
                    }
                }
            }
        }
        val backup = data ?: throw IllegalArgumentException("Sauvegarde incomplète")
        val db = c.db
        db.withTransaction {
            db.suggestions().clearVerdicts()
            db.prices().clear()
            db.received().clear()
            db.ideas().clear()
            db.events().clear()
            db.persons().clear()
            db.persons().insertAll(backup.persons)
            db.events().insertAll(backup.events)
            db.ideas().insertAll(backup.ideas)
            db.ideas().link(backup.links)
            db.prices().insertAll(backup.prices)
            db.received().insertAll(backup.received)
            // Verdicts reference the bundled suggestions: keep only the known ones.
            val known = db.suggestions().all().map { it.id }.toSet()
            db.suggestions().insertVerdicts(backup.verdicts.filter { it.suggestionId in known })
        }
        c.photos.dir.listFiles()?.forEach { it.delete() }
        val restored = staging.listFiles().orEmpty()
        restored.forEach { it.copyTo(File(c.photos.dir, it.name), overwrite = true) }
        staging.deleteRecursively()
        c.reminders.reschedule()
        BackupSummary(backup.persons.size, backup.ideas.size, restored.size)
    }

    /** Weekly automatic backup into the folder chosen by the user; keeps the 4 most recent files. */
    suspend fun autoBackup(): Boolean = withContext(Dispatchers.IO) {
        val settings = c.settings.current()
        if (!settings.autoBackup) return@withContext false
        val folder = settings.autoBackupFolder?.let { DocumentFile.fromTreeUri(context, Uri.parse(it)) } ?: return@withContext false
        val password = settings.autoBackupSecret?.let(SecretBox::open) ?: return@withContext false
        val file = folder.createFile("application/octet-stream", "ruban-auto-${LocalDate.now()}.ruban") ?: return@withContext false
        context.contentResolver.openOutputStream(file.uri)?.use { export(it, password.toCharArray()) } ?: return@withContext false
        folder.listFiles()
            .filter { it.name?.startsWith("ruban-auto-") == true }
            .sortedByDescending { it.name }
            .drop(4)
            .forEach { it.delete() }
        c.settings.setLastAutoBackupAt(System.currentTimeMillis())
        true
    }

    companion object {
        private const val DATA = "data.json"
        const val EXTENSION = "ruban"
    }
}
