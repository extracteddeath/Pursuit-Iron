package com.pursuitiron.app.data

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.pursuitiron.app.MainActivity
import com.pursuitiron.app.R
import com.pursuitiron.app.widget.WidgetSnapshot
import org.json.JSONObject
import java.util.Calendar

/**
 * Training reminders (the web app's `reminders`: on/off, a time, weekdays with 0 = Sunday). The
 * store lives in the engine, which a receiver can't reach, so the app mirrors the setting here and
 * this schedules one alarm for the next reminder; each firing posts the notification and books the
 * next one. Restarts and app updates re-arm it.
 */
object Reminders {
    private const val FILE = "pi-reminders"
    const val CHANNEL = "reminders"
    private const val NOTIFY_ID = 4301

    data class Config(val enabled: Boolean, val hour: Int, val minute: Int, val days: Set<Int>)

    fun parse(o: JSONObject?): Config {
        val t = (o?.optString("time") ?: "18:00").split(":")
        val days = o?.optJSONArray("days")?.let { a -> (0 until a.length()).map { a.optInt(it) }.toSet() } ?: setOf(1, 2, 3, 4, 5)
        return Config(o?.optBoolean("enabled") == true, t.getOrNull(0)?.toIntOrNull()?.coerceIn(0, 23) ?: 18, t.getOrNull(1)?.toIntOrNull()?.coerceIn(0, 59) ?: 0, days)
    }

    fun save(context: Context, o: JSONObject?) {
        context.getSharedPreferences(FILE, Context.MODE_PRIVATE).edit().putString("config", (o ?: JSONObject()).toString()).apply()
        schedule(context)
    }

    private fun load(context: Context): Config =
        parse(runCatching { JSONObject(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("config", null) ?: "{}") }.getOrNull())

    /** The next reminder strictly after [from], or null when none is set. Calendar weekday 1 = Sunday. */
    fun next(c: Config, from: Long = System.currentTimeMillis()): Long? {
        if (!c.enabled || c.days.isEmpty()) return null
        val cal = Calendar.getInstance().apply { timeInMillis = from; set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0); set(Calendar.HOUR_OF_DAY, c.hour); set(Calendar.MINUTE, c.minute) }
        repeat(8) {
            if (cal.timeInMillis > from && (cal.get(Calendar.DAY_OF_WEEK) - 1) in c.days) return cal.timeInMillis
            cal.add(Calendar.DAY_OF_YEAR, 1)
        }
        return null
    }

    private fun intent(context: Context): PendingIntent = PendingIntent.getBroadcast(context, 1, Intent(context, ReminderReceiver::class.java),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)

    fun schedule(context: Context) {
        val am = context.getSystemService(AlarmManager::class.java) ?: return
        am.cancel(intent(context))
        val at = next(load(context)) ?: return
        // A reminder may land a few minutes late; it never needs the exact-alarm permission.
        runCatching { am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, intent(context)) }
    }

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT < 26) return
        val ch = NotificationChannel(CHANNEL, context.getString(R.string.reminder_channel), NotificationManager.IMPORTANCE_DEFAULT)
            .apply { description = context.getString(R.string.reminder_channel_desc) }
        context.getSystemService(NotificationManager::class.java)?.createNotificationChannel(ch)
    }

    fun post(context: Context) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val snap = WidgetSnapshot.read(context)
        val body = when (snap.kind) {
            "next" -> "${snap.title} is up next" + if (snap.sub.isNotBlank()) " · ${snap.sub}" else ""
            "live" -> "${snap.title} is still open. Pick up where you left off."
            else -> context.getString(R.string.reminder_body)
        }
        val open = PendingIntent.getActivity(context, 2, Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        val n = NotificationCompat.Builder(context, CHANNEL)
            .setSmallIcon(R.mipmap.ic_launcher_monochrome)
            .setContentTitle(context.getString(R.string.reminder_title))
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setCategory(NotificationCompat.CATEGORY_REMINDER)
            .setAutoCancel(true)
            .setContentIntent(open)
            .build()
        @Suppress("MissingPermission")
        runCatching { NotificationManagerCompat.from(context).notify(NOTIFY_ID, n) }
    }

}

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        Reminders.post(context)
        Reminders.schedule(context)
    }
}

/** Alarms do not survive a reboot, an app update or a clock change; re-arm on each. */
class ReminderBootReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) { Reminders.schedule(context) }
}
