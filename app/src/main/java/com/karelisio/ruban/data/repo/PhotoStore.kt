package com.karelisio.ruban.data.repo

import android.content.Context
import android.graphics.Bitmap
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.core.content.FileProvider
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID
import kotlin.math.max

/** Stores every picture (people, ideas, moments) as a downscaled JPEG inside the app's private storage. */
class PhotoStore(private val context: Context) {

    val dir: File = File(context.filesDir, PHOTO_DIR).apply { mkdirs() }

    fun file(name: String): File = File(dir, name)

    /** Model usable by Coil. */
    fun model(name: String?): File? = name?.let(::file)?.takeIf { it.exists() }

    suspend fun importUri(uri: Uri): String? = withContext(Dispatchers.IO) {
        runCatching {
            val source = ImageDecoder.createSource(context.contentResolver, uri)
            save(decode(source))
        }.getOrNull()
    }

    suspend fun importBytes(bytes: ByteArray): String? = withContext(Dispatchers.IO) {
        runCatching { save(decode(ImageDecoder.createSource(java.nio.ByteBuffer.wrap(bytes)))) }.getOrNull()
    }

    suspend fun importFile(source: File): String? = withContext(Dispatchers.IO) {
        runCatching { save(decode(ImageDecoder.createSource(source))) }.getOrNull().also { source.delete() }
    }

    private fun decode(source: ImageDecoder.Source): Bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
        val longest = max(info.size.width, info.size.height)
        if (longest > MAX_SIZE) {
            val scale = MAX_SIZE.toFloat() / longest
            decoder.setTargetSize((info.size.width * scale).toInt(), (info.size.height * scale).toInt())
        }
        decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
    }

    private fun save(bitmap: Bitmap): String {
        val name = "${UUID.randomUUID()}.jpg"
        file(name).outputStream().use { bitmap.compress(Bitmap.CompressFormat.JPEG, 86, it) }
        bitmap.recycle()
        return name
    }

    /** Temporary file + content Uri for the camera (ActivityResultContracts.TakePicture). */
    fun newCameraTarget(): Pair<File, Uri> {
        val cache = File(context.cacheDir, "camera").apply { mkdirs() }
        val file = File(cache, "${UUID.randomUUID()}.jpg")
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", file)
        return file to uri
    }

    fun delete(name: String?) {
        if (name != null) file(name).delete()
    }

    companion object {
        const val PHOTO_DIR = "photos"
        private const val MAX_SIZE = 1600
    }
}
