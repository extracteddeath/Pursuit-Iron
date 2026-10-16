package com.pursuitiron.app

import android.app.Activity
import android.appwidget.AppWidgetHost
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.os.Bundle
import android.util.Log
import android.util.TypedValue
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.LinearLayout
import com.pursuitiron.app.widget.NextWorkoutWidgetReceiver

/**
 * Hosts the real widget the way a launcher does (AppWidgetHost + bound id) at two sizes, so the
 * emulator smoke run renders it end to end. Needs `appwidget grantbind` for this package.
 */
class WidgetPreviewActivity : Activity() {
    private lateinit var host: AppWidgetHost
    private val ids = mutableListOf<Int>()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        host = AppWidgetHost(this, 4711)
        val mgr = AppWidgetManager.getInstance(this)
        val provider = ComponentName(this, NextWorkoutWidgetReceiver::class.java)
        val info = mgr.installedProviders.firstOrNull { it.provider == provider }
        val col = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; gravity = Gravity.CENTER_HORIZONTAL; setPadding(48, 160, 48, 48) }
        fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()
        if (info == null) { Log.e("PiWidget", "widget provider not installed"); setContentView(col); return }
        listOf(260 to 180, 140 to 140).forEach { (w, h) ->
            val id = host.allocateAppWidgetId(); ids += id
            if (!mgr.bindAppWidgetIdIfAllowed(id, provider)) { Log.e("PiWidget", "bind not allowed"); return@forEach }
            val opts = Bundle().apply {
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_WIDTH, w); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_WIDTH, w)
                putInt(AppWidgetManager.OPTION_APPWIDGET_MIN_HEIGHT, h); putInt(AppWidgetManager.OPTION_APPWIDGET_MAX_HEIGHT, h)
            }
            mgr.updateAppWidgetOptions(id, opts)
            val view = host.createView(this, id, info)
            col.addView(view, LinearLayout.LayoutParams(dp(w), dp(h)).apply { bottomMargin = dp(24) })
        }
        setContentView(FrameLayout(this).apply { addView(col) })
        // Ask the widget to draw now (a launcher does this when a widget is placed).
        sendBroadcast(android.content.Intent(AppWidgetManager.ACTION_APPWIDGET_UPDATE).setComponent(provider)
            .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids.toIntArray()))
    }

    override fun onStart() { super.onStart(); host.startListening() }
    override fun onStop() { host.stopListening(); super.onStop() }
    override fun onDestroy() { ids.forEach { host.deleteAppWidgetId(it) }; super.onDestroy() }
}
