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
import kotlinx.coroutines.launch
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
    private fun alarmIntent(next: String?, canLog: Boolean = false): PendingIntent = PendingIntent.getBroadcast(context, 0,
        Intent(context, RestDoneReceiver::class.java).apply { if (next != null) putExtra(EXTRA_NEXT, next); putExtra(EXTRA_CAN_LOG, canLog) },
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    /** True when the system lets this app fire the rest alarm on the second; otherwise it may land a little late. */
    fun exactAllowed(): Boolean = Build.VERSION.SDK_INT < 31 || alarms?.canScheduleExactAlarms() == true

    fun allowed(): Boolean = Build.VERSION.SDK_INT < 33 ||
        ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) == PackageManager.PERMISSION_GRANTED

    private fun actionIntent(a: String): PendingIntent = actionIntent(context, a)

    /** The design's lock-screen rest card: the countdown with −15 s, +15 s and Skip. A [sample] (Settings →
     *  Rest timer & cues → Preview lock screen) shows the same card without actions or the end alarm. */
    fun show(endAt: Long, title: String, next: String, sample: Boolean = false, canLog: Boolean = false) {
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
            // Android 16 Live Update: the countdown also rides in the status bar as a chip and leads the lock screen,
            // the way a ride or a delivery does (iOS calls it a Live Activity). Older Android ignores the request.
            .setRequestPromotedOngoing(!sample)
        if (!sample) {
            b.addAction(R.mipmap.ic_launcher_monochrome, "−15 s", actionIntent("minus"))
            b.addAction(R.mipmap.ic_launcher_monochrome, "+15 s", actionIntent("plus"))
            b.addAction(R.mipmap.ic_launcher_monochrome, "Skip", actionIntent("skip"))
        }
        @Suppress("MissingPermission")
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
        if (sample) return
        val am = alarms ?: return
        val pi = alarmIntent(next, canLog)
        runCatching {
            if (exactAllowed()) am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, endAt, pi)
        }
    }

    /** A workout running with no rest in progress, while you're out of the app: the session clock counting up and the
     *  next set, so it reads like the rest card (and, on Android 16, sits in the status bar as a Live Update). */
    fun showWorkout(startedAt: Long?, title: String, next: String, canLog: Boolean = false) {
        if (!allowed()) return
        val open = PendingIntent.getActivity(context, 0, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val b = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(title)
            .setContentText(next)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_STOPWATCH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setContentIntent(open)
            .setRequestPromotedOngoing(true)
        if (startedAt != null && startedAt > 0) b.setWhen(startedAt).setShowWhen(true).setUsesChronometer(true) else b.setShowWhen(false)
        // Log the next set at its target from the lock screen; the rest card follows, as after a tap in the app.
        if (canLog) b.addAction(R.mipmap.ic_launcher_monochrome, "Log set", actionIntent("log"))
        runCatching { alarms?.cancel(alarmIntent(null)) }
        @Suppress("MissingPermission")
        runCatching { NotificationManagerCompat.from(context).notify(id, b.build()) }
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
        const val EXTRA_CAN_LOG = "canLog"
        val ACTIONS = listOf("minus", "plus", "skip", "log")
        fun actionIntent(context: Context, a: String): PendingIntent = PendingIntent.getBroadcast(context, ACTIONS.indexOf(a) + 10,
            Intent(context, RestActionReceiver::class.java).putExtra(EXTRA_ACTION, a), PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
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
        val b = NotificationCompat.Builder(context, RestNotifier.DONE_CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.rest_over))
            .setContentText(intent.getStringExtra(RestNotifier.EXTRA_NEXT) ?: context.getString(R.string.rest_over_body))
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            .setAutoCancel(true)
            .setContentIntent(open)
            .setTimeoutAfter(5 * 60_000L)
        if (intent.getBooleanExtra(RestNotifier.EXTRA_CAN_LOG, false))
            b.addAction(R.mipmap.ic_launcher_monochrome, "Log set", RestNotifier.actionIntent(context, "log"))
        @Suppress("MissingPermission")
        runCatching { nm.notify(RestNotifier.DONE_ID, b.build()) }
    }
}

/** Taps on the rest card's −15 s / +15 s / Skip. The running app applies them to the live rest; Skip also
 *  clears the countdown and its end alarm here, so it works even if the app has been closed. */
object RestActions { val flow = kotlinx.coroutines.flow.MutableSharedFlow<String>(extraBufferCapacity = 8) }

class RestActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val a = intent.getStringExtra(RestNotifier.EXTRA_ACTION) ?: return
        if (a == "skip") RestNotifier(context).cancel()
        // The app is running: it logs through its own core, so the screen and the card stay in step.
        if (RestActions.flow.subscriptionCount.value > 0) { RestActions.flow.tryEmit(a); return }
        if (a != "log") return
        // The app's process is gone (the system reclaimed it mid-workout): load the saved workout, log the set and
        // put the next card up, then let go. The app reads the same files when it opens.
        val pending = goAsync()
        kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Main).launch {
            var core: com.pursuitiron.app.engine.Core? = null
            try {
                core = com.pursuitiron.app.engine.Core.start(context)
                val s = core.obj("session")
                val n = LiveCard.next(s)
                if (s != null && n != null && n.loggable) {
                    if (n.ei != s.optInt("exIdx")) core.call("act", org.json.JSONObject().put("type", "select").put("ei", n.ei))
                    val after = core.obj("act", org.json.JSONObject().put("type", "toggle").put("ei", n.ei).put("si", n.si))
                    runCatching { NotificationManagerCompat.from(context).cancel(RestNotifier.DONE_ID) }
                    LiveCard.post(RestNotifier(context), after, System.currentTimeMillis())
                }
            } catch (_: Throwable) {
            } finally {
                runCatching { core?.close() }
                pending.finish()
            }
        }
    }
}
