package com.pursuitiron.app.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.RingtoneManager
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
 * When it ends, an alarm posts a second, audible notification ("Rest over"), so the lifter
 * hears it with the phone in a pocket, the job the web app's beep does while it is open.
 */
class RestNotifier(private val context: Context) {
    private val id = COUNTDOWN_ID

    init {
        if (Build.VERSION.SDK_INT >= 26) {
            val ch = NotificationChannel(CHANNEL, context.getString(R.string.rest_channel), NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = context.getString(R.string.rest_channel_desc)
                setSound(null, null)
                enableVibration(false)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            val done = NotificationChannel(DONE_CHANNEL, context.getString(R.string.rest_done_channel), NotificationManager.IMPORTANCE_HIGH).apply {
                description = context.getString(R.string.rest_done_channel_desc)
                setSound(RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION),
                    AudioAttributes.Builder().setUsage(AudioAttributes.USAGE_NOTIFICATION_EVENT).setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build())
                enableVibration(true)
                vibrationPattern = longArrayOf(0, 40, 60, 40, 60, 120)
                lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
            }
            context.getSystemService(NotificationManager::class.java)?.createNotificationChannels(listOf(ch, done))
        }
    }

    private val alarms get() = context.getSystemService(AlarmManager::class.java)
    private fun alarmIntent(next: String?): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, RestDoneReceiver::class.java).apply { if (next != null) putExtra(EXTRA_NEXT, next) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** True when the system lets this app fire the rest alarm on the second; otherwise it may land a little late. */
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms?.canScheduleExactAlarms() == true

    fun allowed(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun actionIntent(a: String): PendingIntent = PendingIntent.getBroadcast(context, ACTIONS.indexOf(a) + 10,
        Intent(context, RestActionReceiver::class.java).putExtra(EXTRA_ACTION, a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** The design's lock-screen rest card: the countdown with −15 s, +15 s and Skip. A [sample] (Settings →
     *  Rest timer & cues → Preview lock screen) shows the same card without actions or the end alarm. */
    fun show(endAt: Long, title: String, next: String, sample: Boolean = false) {
        if (!allowed()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(context, CHANNEL)
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
        if (!sample) {
            b.addAction(R.mipmap.ic_launcher_monochrome, "−15 s", actionIntent("minus"))
            b.addAction(R.mipmap.ic_launcher_monochrome, "+15 s", actionIntent("plus"))
            b.addAction(R.mipmap.ic_launcher_monochrome, "Skip", actionIntent("skip"))
        }
        @Suppress("MissingPermission")
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
        if (sample) return
        val am = alarms ?: return
        val pi = alarmIntent(next)
        runCatching {
            if (exactAllowed()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
        }
    }

    fun cancel() {
        runCatching { alarms?.cancel(alarmIntent(null)) }
        runCatching { NotificationManagerCompat.from(context).cancel(id) }
        runCatching { NotificationManagerCompat.from(context).cancel(DONE_ID) }
    }

    companion object {
        const val CHANNEL = "rest"
        const val DONE_CHANNEL = "rest-done"
        const val EXTRA_NEXT = "next"
        const val EXTRA_ACTION = "action"
        val ACTIONS = listOf("minus", "plus", "skip")
        const val COUNTDOWN_ID = 4201
        const val DONE_ID = 4202
    }
}

/** Fires when a background rest ends: swaps the silent countdown for an audible "Rest over". */
class RestDoneReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val nm = NotificationManagerCompat.from(context)
        runCatching { nm.cancel(RestNotifier.COUNTDOWN_ID) }
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, RestNotifier.DONE_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.rest_over))
            .setContentText(intent.getStringExtra(RestNotifier.EXTRA_NEXT) ?: context.getString(R.string.rest_over_body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setTimeoutAfter(5 * 60_000L)
            .build()
        @Suppress("MissingPermission")
        runCatching { nm.notify(RestNotifier.DONE_ID, n) }
    }
}

/** Taps on the rest card's −15 s / +15 s / Skip. The running app applies them to the live rest; Skip also
 *  clears the countdown and its end alarm here, so it works even if the app has been closed. */
object RestActions { val flow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8) }

class RestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.getStringExtra(RestNotifier.EXTRA_ACTION) ?: return
        if (a == "skip") RestNotifier(context).cancel()
        RestActions.flow.tryEmit(a)
    }
}
