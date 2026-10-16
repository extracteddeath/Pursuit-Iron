package com.pursuitiron.app.data

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pursuitiron.app.MainActivity
import com.pursuitiron.app.R

/**
 * The rest countdown on the lock screen and in the shade while the app is in the background.
 * The system draws the countdown (a chronometer), so nothing has to run to keep it moving.
 */
class RestNotifier(private val context: Context) {
    private val id = 4201

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, context.getString(R.string.rest_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.rest_channel_desc)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
        }
    }

    fun allowed(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    fun show(endAt: Long, title: String, next: String) {
        if (!allowed()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(next)
            .setWhen(endAt)
            .setShowWhen(true)
            .setUsesChronometer(true)
            .setChronometerCountDown(true)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setTimeoutAfter((endAt - System.currentTimeMillis()).coerceAtLeast(1000L) + 30_000L)
            .build()
        @Suppress("MissingPermission")
        runCatching { NotificationManagerCompat.from(context).notify(id, n) }
    }

    fun cancel() { runCatching { NotificationManagerCompat.from(context).cancel(id) } }

    companion object { const val CHANNEL = "rest" }
}
