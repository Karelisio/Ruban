package com.karelisio.ruban.notify

import android.Manifest
import android.annotation.SuppressLint
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.karelisio.ruban.R

object Notifications {
    const val CHANNEL_REMINDERS = "reminders"
    const val CHANNEL_PRICES = "prices"
    const val CHANNEL_UPDATES = "updates"
    const val CHANNEL_BACKUP = "backup"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannels(
            listOf(
                NotificationChannel(CHANNEL_REMINDERS, "Rappels d'événements", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Anniversaires, fêtes et événements à venir"
                },
                NotificationChannel(CHANNEL_PRICES, "Baisses de prix", NotificationManager.IMPORTANCE_DEFAULT).apply {
                    description = "Quand une idée devient moins chère"
                },
                NotificationChannel(CHANNEL_UPDATES, "Mises à jour", NotificationManager.IMPORTANCE_LOW),
                NotificationChannel(CHANNEL_BACKUP, "Sauvegardes", NotificationManager.IMPORTANCE_MIN),
            ),
        )
    }

    fun pending(context: Context, requestCode: Int, intent: Intent): PendingIntent =
        PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

    fun builder(context: Context, channel: String): NotificationCompat.Builder =
        NotificationCompat.Builder(context, channel)
            .setSmallIcon(R.drawable.ic_notification)
            .setColor(ContextCompat.getColor(context, R.color.splash_ribbon))
            .setAutoCancel(true)

    @SuppressLint("MissingPermission")
    fun post(context: Context, id: Int, builder: NotificationCompat.Builder) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        NotificationManagerCompat.from(context).notify(id, builder.build())
    }
}
