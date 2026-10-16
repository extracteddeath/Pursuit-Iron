package com.pursuitiron.app.widget

import android.content.Context
import android.content.Intent
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.appwidget.updateAll
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.material3.ColorProviders
import androidx.glance.semantics.contentDescription
import androidx.glance.semantics.semantics
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.pursuitiron.app.MainActivity
import com.pursuitiron.app.data.Prefs
import com.pursuitiron.app.ui.theme.schemeFor
import org.json.JSONObject
import java.text.DateFormat
import java.util.Date

/**
 * What the widget shows, written by the app whenever Today changes. The widget never runs the
 * engine: it draws this snapshot, so it is instant and costs nothing between updates.
 */
data class WidgetSnapshot(
    val kind: String = "empty", // empty | next | live
    val title: String = "",
    val sub: String = "",
    val done: Int = 0,
    val total: Int = 0,
    val restEndsAt: Long = 0,
) {
    fun toJson(): String = JSONObject().put("kind", kind).put("title", title).put("sub", sub).put("done", done).put("total", total).put("restEndsAt", restEndsAt).toString()

    companion object {
        private const val FILE = "pi-widget"
        fun read(context: Context): WidgetSnapshot = runCatching {
            val o = JSONObject(context.getSharedPreferences(FILE, Context.MODE_PRIVATE).getString("snap", null) ?: return WidgetSnapshot())
            WidgetSnapshot(o.optString("kind", "empty"), o.optString("title"), o.optString("sub"), o.optInt("done"), o.optInt("total"), o.optLong("restEndsAt"))
        }.getOrDefault(WidgetSnapshot())

        /** Saves the snapshot and redraws every placed widget, only when it changed. */
        suspend fun publish(context: Context, snap: WidgetSnapshot) {
            val sp = context.getSharedPreferences(FILE, Context.MODE_PRIVATE)
            val json = snap.toJson()
            if (sp.getString("snap", null) == json) return
            sp.edit().putString("snap", json).apply()
            runCatching {
                if (GlanceAppWidgetManager(context).getGlanceIds(NextWorkoutWidget::class.java).isNotEmpty()) NextWorkoutWidget().updateAll(context)
            }
        }
    }
}

const val ACTION_WIDGET_START = "com.pursuitiron.app.action.START_WORKOUT"

class NextWorkoutWidget : GlanceAppWidget() {
    // Buckets sized for what a host actually leaves after its own padding: a 3x2 placement on a phone
    // is roughly 230x150 dp inside the frame, so a 250 dp bucket would never be chosen.
    override val sizeMode = SizeMode.Responsive(setOf(DpSize(110.dp, 110.dp), DpSize(180.dp, 110.dp), DpSize(180.dp, 150.dp)))

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val snap = WidgetSnapshot.read(context)
        val seed = Prefs(context).seed
        provideContent {
            GlanceTheme(colors = ColorProviders(light = schemeFor(seed, false), dark = schemeFor(seed, true))) { Content(context, snap) }
        }
    }

    @Composable
    private fun Content(context: Context, s: WidgetSnapshot) {
        val c = GlanceTheme.colors
        val size = LocalSize.current
        val roomy = size.height >= 150.dp
        val narrow = size.width < 180.dp
        val open = Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val start = Intent(context, MainActivity::class.java).setAction(ACTION_WIDGET_START).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP)
        val kicker = when (s.kind) { "live" -> "IN PROGRESS"; "next" -> "UP NEXT"; else -> "PURSUIT IRON" }
        val title = when (s.kind) { "empty" -> "Build your program"; else -> s.title }
        val sub = when {
            s.kind == "live" && s.restEndsAt > System.currentTimeMillis() -> "Resting · next set at ${DateFormat.getTimeInstance(DateFormat.SHORT).format(Date(s.restEndsAt))}"
            s.kind == "live" -> "${s.done} of ${s.total} sets"
            s.kind == "empty" -> "Days, time and equipment. The engine does the rest."
            else -> s.sub
        }
        Column(
            GlanceModifier.fillMaxSize().cornerRadius(28.dp).background(c.primaryContainer).padding(16.dp).clickable(actionStartActivity(open))
                .semantics { contentDescription = "$kicker: $title. $sub" },
        ) {
            Text(kicker, style = TextStyle(fontSize = 11.sp, fontWeight = FontWeight.Medium, color = c.onPrimaryContainer))
            Text(title, maxLines = if (roomy) 2 else 1, style = TextStyle(fontSize = if (narrow) 16.sp else 20.sp, fontWeight = FontWeight.Bold, color = c.onPrimaryContainer))
            if (!narrow || roomy) Text(sub, maxLines = 2, style = TextStyle(fontSize = 12.sp, color = c.onPrimaryContainer))
            Spacer(GlanceModifier.defaultWeight())
            if (s.kind == "live" && s.total > 0) {
                // Progress as a filled run of segments: readable without colour, no custom drawing.
                Row(GlanceModifier.fillMaxWidth().padding(bottom = 8.dp)) {
                    val n = s.total.coerceAtMost(if (narrow) 8 else 16)
                    val filled = (s.done.toFloat() / s.total * n).toInt()
                    repeat(n) { i -> Box(GlanceModifier.defaultWeight().height(6.dp).padding(end = 2.dp)) {
                        Box(GlanceModifier.fillMaxSize().cornerRadius(3.dp).background(if (i < filled) c.primary else c.surface)) {}
                    } }
                }
            }
            Box(GlanceModifier.fillMaxWidth().cornerRadius(20.dp).background(c.primary).padding(vertical = 10.dp)
                .clickable(actionStartActivity(if (s.kind == "empty") open else start)), contentAlignment = Alignment.Center) {
                Text(when (s.kind) { "live" -> "Resume"; "next" -> "Start workout"; else -> "Open" },
                    style = TextStyle(fontSize = 14.sp, fontWeight = FontWeight.Bold, color = c.onPrimary))
            }
        }
    }
}

class NextWorkoutWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = NextWorkoutWidget()
}
