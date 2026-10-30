package com.karelisio.ruban.work

import android.content.Context
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.karelisio.ruban.IntentRouter
import com.karelisio.ruban.container
import com.karelisio.ruban.data.model.Idea
import com.karelisio.ruban.notify.Notifications
import com.karelisio.ruban.util.Money
import kotlinx.coroutines.delay
import java.util.concurrent.TimeUnit

object WorkScheduler {
    private const val PRICE = "price-check"
    private const val PRICE_NOW = "price-check-now"
    private const val HOUSEKEEPING = "housekeeping"
    private const val BACKUP = "auto-backup"

    fun setAutoBackup(context: Context, enabled: Boolean) {
        val wm = WorkManager.getInstance(context)
        if (!enabled) {
            wm.cancelUniqueWork(BACKUP)
            return
        }
        wm.enqueueUniquePeriodicWork(
            BACKUP,
            ExistingPeriodicWorkPolicy.UPDATE,
            PeriodicWorkRequestBuilder<AutoBackupWorker>(7, TimeUnit.DAYS)
                .setConstraints(Constraints.Builder().setRequiresBatteryNotLow(true).build())
                .build(),
        )
    }

    /** Idempotent: called on every app start. */
    fun ensure(context: Context) {
        val wm = WorkManager.getInstance(context)
        wm.enqueueUniquePeriodicWork(
            PRICE,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<PriceCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setInitialDelay(2, TimeUnit.HOURS)
                .build(),
        )
        wm.enqueueUniquePeriodicWork(
            HOUSEKEEPING,
            ExistingPeriodicWorkPolicy.KEEP,
            PeriodicWorkRequestBuilder<HousekeepingWorker>(12, TimeUnit.HOURS).build(),
        )
    }

    fun checkPricesNow(context: Context) {
        WorkManager.getInstance(context).enqueueUniqueWork(
            PRICE_NOW,
            ExistingWorkPolicy.KEEP,
            OneTimeWorkRequestBuilder<PriceCheckWorker>()
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build(),
        )
    }
}

/** Every 24 h: re-reads the price of ideas not bought yet, keeps the history and warns on drops. */
class PriceCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        val settings = c.settings.current()
        if (!settings.priceAlerts) return Result.success()
        for (idea in c.db.ideas().trackable()) {
            val change = c.prices.check(idea) ?: continue
            val before = change.before
            if (before != null && change.after < before) maybeNotify(idea, before, change.after, settings.neutralNotifications)
            delay(1_500) // be gentle with shops
        }
        c.settings.setLastPriceCheckAt(System.currentTimeMillis())
        return Result.success()
    }

    private fun maybeNotify(idea: Idea, before: Long, after: Long, neutral: Boolean) {
        val threshold = idea.priceAlertCents
        // With a threshold: only when crossing it. Without: on every drop.
        if (threshold != null && !(after <= threshold && before > threshold)) return
        val ctx = applicationContext
        val title = if (neutral) "Rappel Ruban" else "📉 ${idea.title}"
        val text = if (neutral) "Un prix suivi vient de baisser."
        else "${Money.format(before, idea.currency)} → ${Money.format(after, idea.currency)}" +
            (threshold?.let { " · sous ton seuil de ${Money.format(it, idea.currency)}" } ?: "")
        val id = 200_000 + idea.id.toInt()
        Notifications.post(
            ctx, id,
            Notifications.builder(ctx, Notifications.CHANNEL_PRICES)
                .setContentTitle(title)
                .setContentText(text)
                .setVisibility(if (neutral) NotificationCompat.VISIBILITY_PRIVATE else NotificationCompat.VISIBILITY_PUBLIC)
                .setContentIntent(Notifications.pending(ctx, id, IntentRouter.openIdea(ctx, idea.id))),
        )
    }
}

/** Safety net for aggressive battery savers (MagicOS…): re-arms the reminder alarm twice a day. */
class HousekeepingWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val c = applicationContext.container
        c.reminders.fire()
        return Result.success()
    }
}

/** Weekly encrypted backup to the folder chosen in the settings. */
class AutoBackupWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result =
        if (runCatching { applicationContext.container.backup.autoBackup() }.isSuccess) Result.success() else Result.retry()
}
