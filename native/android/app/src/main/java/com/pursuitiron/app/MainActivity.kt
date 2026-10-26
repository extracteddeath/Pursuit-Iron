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
        Transfer.fromIntent(this, intent)?.let { vm.importOffer = it }
    }

    override fun onStart() { super.onStart(); vm.onForeground() }
    override fun onStop() { vm.onBackground(); super.onStop() }
}
