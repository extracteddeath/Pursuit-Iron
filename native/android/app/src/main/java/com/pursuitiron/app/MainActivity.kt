package com.pursuitiron.app

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.isSystemInDarkTheme
import com.pursuitiron.app.data.Transfer
import com.pursuitiron.app.ui.PiApp
import com.pursuitiron.app.ui.theme.PiTheme

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        if (savedInstanceState == null) handle(intent)
        publishShortcuts()
        setContent {
            com.pursuitiron.app.ui.theme.Motion.reduced = vm.prefs.reduceMotion
            PiTheme(seed = vm.prefs.seed, dark = vm.prefs.dark ?: isSystemInDarkTheme(), black = vm.prefs.black) {
                val d = androidx.compose.ui.platform.LocalDensity.current
                androidx.compose.runtime.CompositionLocalProvider(androidx.compose.ui.platform.LocalDensity provides androidx.compose.ui.unit.Density(d.density, d.fontScale * vm.prefs.zoom)) {
                    PiApp(vm)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handle(intent)
    }

    private fun handle(intent: Intent?) {
        if (intent?.action == com.pursuitiron.app.widget.ACTION_WIDGET_START) { vm.fromWidget(); return }
        if (intent?.action == ACTION_QUICK_WORKOUT) { vm.fromQuickShortcut(); return }
        Transfer.fromIntent(this, intent)?.let { vm.importOffer = it }
    }

    /** Long-press the launcher icon: Start workout (or resume it) and Quick workout, as Google's own apps offer.
     *  Published from code so the debug build's package (".dev") is the one they open. */
    private fun publishShortcuts() = runCatching {
        fun sc(id: String, short: Int, long: Int, icon: Int, action: String) = androidx.core.content.pm.ShortcutInfoCompat.Builder(this, id)
            .setShortLabel(getString(short)).setLongLabel(getString(long))
            .setIcon(androidx.core.graphics.drawable.IconCompat.createWithResource(this, icon))
            .setIntent(Intent(this, MainActivity::class.java).setAction(action)).build()
        androidx.core.content.pm.ShortcutManagerCompat.setDynamicShortcuts(this, listOf(
            sc("start", R.string.sc_start_short, R.string.sc_start_long, R.mipmap.sc_start, com.pursuitiron.app.widget.ACTION_WIDGET_START),
            sc("quick", R.string.sc_quick_short, R.string.sc_quick_long, R.mipmap.sc_quick, ACTION_QUICK_WORKOUT)))
    }

    override fun onStart() { super.onStart(); vm.onForeground() }
    override fun onStop() { vm.onBackground(); super.onStop() }
}

const val ACTION_QUICK_WORKOUT = "com.pursuitiron.app.action.QUICK_WORKOUT"
