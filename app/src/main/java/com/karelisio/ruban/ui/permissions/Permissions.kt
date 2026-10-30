package com.karelisio.ruban.ui.permissions

import android.Manifest
import android.app.Activity
import android.app.AlarmManager
import android.app.NotificationManager
import android.content.Context
import android.content.ContextWrapper
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.PowerManager
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Alarm
import androidx.compose.material.icons.rounded.BatteryChargingFull
import androidx.compose.material.icons.rounded.CameraAlt
import androidx.compose.material.icons.rounded.Contacts
import androidx.compose.material.icons.rounded.InstallMobile
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.NotificationsActive
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect

enum class AppPermission(
    val title: String,
    val why: String,
    val icon: ImageVector,
    val manifest: String?,
) {
    NOTIFICATIONS(
        "Notifications",
        "Pour te rappeler les anniversaires et les fêtes à l'avance, et te prévenir quand un prix baisse.",
        Icons.Rounded.NotificationsActive,
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.POST_NOTIFICATIONS else null,
    ),
    EXACT_ALARMS(
        "Alarmes exactes",
        "Pour que tes rappels arrivent pile à l'heure choisie, même quand le téléphone est en veille.",
        Icons.Rounded.Alarm,
        null,
    ),
    CONTACTS(
        "Contacts",
        "Pour importer en un geste les anniversaires déjà enregistrés dans tes contacts. Rien ne quitte ton téléphone.",
        Icons.Rounded.Contacts,
        Manifest.permission.READ_CONTACTS,
    ),
    MICROPHONE(
        "Micro",
        "Pour dicter une idée au vol, sans taper. La reconnaissance se fait par le service vocal de ton téléphone.",
        Icons.Rounded.Mic,
        Manifest.permission.RECORD_AUDIO,
    ),
    CAMERA(
        "Appareil photo",
        "Pour photographier une idée repérée en vitrine ou chez un ami.",
        Icons.Rounded.CameraAlt,
        Manifest.permission.CAMERA,
    ),
    BATTERY(
        "Activité en arrière-plan",
        "Certains téléphones (Honor, MagicOS…) coupent les applis en veille. Sans cette exception, rappels et suivi des prix peuvent ne jamais arriver.",
        Icons.Rounded.BatteryChargingFull,
        null,
    ),
    INSTALL_APKS(
        "Mises à jour",
        "Pour installer les nouvelles versions de Ruban directement depuis l'appli, sans passer par un store.",
        Icons.Rounded.InstallMobile,
        null,
    ),
    ;

    /** Granted through a system settings screen rather than a runtime dialog. */
    val viaSettings: Boolean get() = manifest == null && this != NOTIFICATIONS
}

object Permissions {
    fun isGranted(context: Context, permission: AppPermission): Boolean = when (permission) {
        AppPermission.NOTIFICATIONS ->
            context.getSystemService(NotificationManager::class.java).areNotificationsEnabled() &&
                (permission.manifest == null || granted(context, permission.manifest))
        AppPermission.EXACT_ALARMS -> context.getSystemService(AlarmManager::class.java).canScheduleExactAlarms()
        AppPermission.BATTERY -> context.getSystemService(PowerManager::class.java).isIgnoringBatteryOptimizations(context.packageName)
        AppPermission.INSTALL_APKS -> context.packageManager.canRequestPackageInstalls()
        else -> granted(context, permission.manifest!!)
    }

    private fun granted(context: Context, name: String) =
        ContextCompat.checkSelfPermission(context, name) == PackageManager.PERMISSION_GRANTED

    fun appDetails(context: Context) = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.parse("package:${context.packageName}"))

    /** Settings screen granting [permission] (or the app details screen as a fallback). */
    @Suppress("BatteryLife")
    fun settingsIntent(context: Context, permission: AppPermission): Intent {
        val pkg = Uri.parse("package:${context.packageName}")
        return when (permission) {
            AppPermission.EXACT_ALARMS -> Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM, pkg)
            AppPermission.BATTERY -> Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS, pkg)
            AppPermission.INSTALL_APKS -> Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, pkg)
            AppPermission.NOTIFICATIONS -> Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS)
                .putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
            else -> appDetails(context)
        }
    }

    fun launch(context: Context, intent: Intent) {
        runCatching { context.startActivity(intent) }
            .onFailure { runCatching { context.startActivity(appDetails(context)) } }
    }
}

fun Context.findActivity(): Activity? {
    var ctx = this
    while (ctx is ContextWrapper) {
        if (ctx is Activity) return ctx
        ctx = ctx.baseContext
    }
    return null
}

@Stable
class PermissionState(val permission: AppPermission, granted: Boolean, private val onRequest: () -> Unit) {
    var granted by mutableStateOf(granted)
        internal set
    fun request() = onRequest()
}

/** Permission state refreshed each time the screen resumes (e.g. coming back from system settings). */
@Composable
fun rememberPermissionState(permission: AppPermission, onResult: (Boolean) -> Unit = {}): PermissionState {
    val context = LocalContext.current
    var asked by remember { mutableStateOf(false) }
    lateinit var state: PermissionState
    val launcher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        state.granted = Permissions.isGranted(context, permission)
        if (!ok) {
            val activity = context.findActivity()
            val canAskAgain = activity != null && permission.manifest != null &&
                activity.shouldShowRequestPermissionRationale(permission.manifest)
            // Permanently denied: the only way left is the system settings.
            if (!canAskAgain && asked) Permissions.launch(context, Permissions.settingsIntent(context, permission))
        }
        asked = true
        onResult(state.granted)
    }
    state = remember(permission) {
        PermissionState(permission, Permissions.isGranted(context, permission)) {
            val manifest = permission.manifest
            if (manifest != null) launcher.launch(manifest)
            else Permissions.launch(context, Permissions.settingsIntent(context, permission))
        }
    }
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        val now = Permissions.isGranted(context, permission)
        if (now != state.granted) {
            state.granted = now
            onResult(now)
        }
    }
    return state
}
