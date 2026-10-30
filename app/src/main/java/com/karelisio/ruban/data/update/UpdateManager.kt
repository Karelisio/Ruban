package com.karelisio.ruban.data.update

import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInstaller
import android.os.Build
import com.karelisio.ruban.BuildConfig
import com.karelisio.ruban.container
import com.karelisio.ruban.data.settings.SettingsRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File

@Serializable
private data class GhAsset(val name: String, @SerialName("browser_download_url") val url: String, val size: Long = 0)

@Serializable
private data class GhRelease(
    @SerialName("tag_name") val tag: String,
    val name: String? = null,
    val body: String? = null,
    val draft: Boolean = false,
    val prerelease: Boolean = false,
    val assets: List<GhAsset> = emptyList(),
)

data class UpdateInfo(val version: String, val changelog: String, val apkUrl: String, val size: Long)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val info: UpdateInfo) : UpdateState
    data class Downloading(val info: UpdateInfo, val progress: Float) : UpdateState
    data class Installing(val info: UpdateInfo) : UpdateState
    data class Failed(val message: String, val info: UpdateInfo? = null) : UpdateState
}

/** Checks GitHub releases, downloads the signed APK and installs it with PackageInstaller. */
class UpdateManager(
    private val context: Context,
    private val http: OkHttpClient,
    private val settings: SettingsRepository,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state

    /** At most every 6 h unless [force]. */
    suspend fun check(force: Boolean = false): UpdateState {
        val now = System.currentTimeMillis()
        val current = settings.current()
        if (!force && now - current.lastUpdateCheckAt < CHECK_INTERVAL) return _state.value
        if (_state.value is UpdateState.Downloading || _state.value is UpdateState.Installing) return _state.value
        _state.value = UpdateState.Checking
        val result = withContext(Dispatchers.IO) {
            runCatching {
                val request = Request.Builder()
                    .url("https://api.github.com/repos/${BuildConfig.UPDATE_REPO}/releases/latest")
                    .header("Accept", "application/vnd.github+json")
                    .header("User-Agent", "Ruban/${BuildConfig.VERSION_NAME}")
                    .build()
                http.newCall(request).execute().use { r ->
                    if (r.code == 404) return@runCatching UpdateState.UpToDate
                    if (!r.isSuccessful) error("GitHub a répondu ${r.code}")
                    val release = json.decodeFromString<GhRelease>(r.body.string())
                    val apk = release.assets.firstOrNull { it.name.endsWith(".apk") }
                    val version = release.tag.removePrefix("v")
                    if (release.draft || release.prerelease || apk == null || !Versions.isNewer(version, BuildConfig.VERSION_NAME)) {
                        UpdateState.UpToDate
                    } else {
                        UpdateState.Available(UpdateInfo(version, release.body.orEmpty().trim(), apk.url, apk.size))
                    }
                }
            }.getOrElse { UpdateState.Failed(it.message ?: "Vérification impossible") }
        }
        settings.setLastUpdateCheckAt(now)
        _state.value = result
        return result
    }

    suspend fun downloadAndInstall(info: UpdateInfo) {
        _state.value = UpdateState.Downloading(info, 0f)
        val file = runCatching { download(info) }.getOrElse {
            _state.value = UpdateState.Failed("Téléchargement interrompu", info)
            return
        }
        _state.value = UpdateState.Installing(info)
        runCatching { install(file) }.onFailure { _state.value = UpdateState.Failed("Installation impossible : ${it.message}", info) }
    }

    private suspend fun download(info: UpdateInfo): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "updates").apply { deleteRecursively(); mkdirs() }
        val target = File(dir, "ruban-${info.version}.apk")
        val request = Request.Builder().url(info.apkUrl).header("User-Agent", "Ruban/${BuildConfig.VERSION_NAME}").build()
        http.newCall(request).execute().use { r ->
            if (!r.isSuccessful) error("HTTP ${r.code}")
            val total = r.body.contentLength().takeIf { it > 0 } ?: info.size
            r.body.byteStream().use { input ->
                target.outputStream().use { out ->
                    val buffer = ByteArray(64 * 1024)
                    var read: Int
                    var done = 0L
                    var lastEmit = 0f
                    while (input.read(buffer).also { read = it } >= 0) {
                        out.write(buffer, 0, read)
                        done += read
                        val p = if (total > 0) done.toFloat() / total else 0f
                        if (p - lastEmit >= 0.01f) {
                            lastEmit = p
                            _state.value = UpdateState.Downloading(info, p.coerceIn(0f, 1f))
                        }
                    }
                }
            }
        }
        target
    }

    private fun install(apk: File) {
        val installer = context.packageManager.packageInstaller
        val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
            setAppPackageName(context.packageName)
            if (Build.VERSION.SDK_INT >= 31) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
        }
        val sessionId = installer.createSession(params)
        installer.openSession(sessionId).use { session ->
            session.openWrite("ruban.apk", 0, apk.length()).use { out ->
                apk.inputStream().use { it.copyTo(out) }
                session.fsync(out)
            }
            val intent = Intent(context, InstallReceiver::class.java).setAction(InstallReceiver.ACTION)
            val pending = PendingIntent.getBroadcast(context, sessionId, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_MUTABLE)
            session.commit(pending.intentSender)
        }
    }

    internal fun onInstallResult(status: Int, message: String?) {
        val info = (_state.value as? UpdateState.Installing)?.info
        when (status) {
            PackageInstaller.STATUS_SUCCESS -> _state.value = UpdateState.UpToDate
            PackageInstaller.STATUS_PENDING_USER_ACTION -> Unit
            PackageInstaller.STATUS_FAILURE_ABORTED -> _state.value = info?.let { UpdateState.Available(it) } ?: UpdateState.Idle
            else -> _state.value = UpdateState.Failed(message ?: "Installation refusée", info)
        }
    }

    fun dismiss() {
        if (_state.value !is UpdateState.Downloading && _state.value !is UpdateState.Installing) _state.value = UpdateState.Idle
    }

    companion object {
        private const val CHECK_INTERVAL = 6 * 3_600_000L
    }
}

/** Receives PackageInstaller results; launches the system confirmation when needed. */
class InstallReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val status = intent.getIntExtra(PackageInstaller.EXTRA_STATUS, PackageInstaller.STATUS_FAILURE)
        if (status == PackageInstaller.STATUS_PENDING_USER_ACTION) {
            @Suppress("DEPRECATION")
            val confirm = if (Build.VERSION.SDK_INT >= 33) intent.getParcelableExtra(Intent.EXTRA_INTENT, Intent::class.java)
            else intent.getParcelableExtra(Intent.EXTRA_INTENT)
            confirm?.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)?.let(context::startActivity)
        }
        context.container.updates.onInstallResult(status, intent.getStringExtra(PackageInstaller.EXTRA_STATUS_MESSAGE))
    }

    companion object {
        const val ACTION = "com.karelisio.ruban.action.INSTALL_RESULT"
    }
}

object Versions {
    /** Semantic comparison of "1.2.10" vs "1.2.9" (suffixes like -dev are ignored). */
    fun isNewer(candidate: String, current: String): Boolean {
        fun parts(v: String) = v.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(candidate)
        val b = parts(current)
        for (i in 0 until maxOf(a.size, b.size)) {
            val x = a.getOrElse(i) { 0 }
            val y = b.getOrElse(i) { 0 }
            if (x != y) return x > y
        }
        return current.contains('-') && !candidate.contains('-')
    }
}
