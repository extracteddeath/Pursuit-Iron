package io.github.extracteddeath.pursuitiron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat

/** Debug-only entry to the real Kotlin journey; never starts the legacy runtime. */
class KotlinEnginePreviewActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appearance=getSharedPreferences("pursuit_native_appearance",MODE_PRIVATE)
        val theme=appearance.getString("theme_id",PursuitThemes.DEFAULT_ID)
            ?.takeIf{PursuitThemes.find(it)!=null} ?:PursuitThemes.DEFAULT_ID
        val bars=WindowCompat.getInsetsController(window,window.decorView)
        bars.isAppearanceLightStatusBars=PursuitThemes.resolve(theme).isLight
        bars.isAppearanceLightNavigationBars=PursuitThemes.resolve(theme).isLight
        setContent {
            PursuitIronTheme(themeId=theme) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    NativeKotlinCandidateJourney()
                }
            }
        }
    }
}
