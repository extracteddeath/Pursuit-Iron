package io.github.extracteddeath.pursuitiron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat

/** Debug-only entry to the real Kotlin journey; never starts the legacy runtime. */
class KotlinEnginePreviewActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appearance=getSharedPreferences("pursuit_native_appearance",MODE_PRIVATE)
        setContent {
            // Device-local appearance only. A previously selected theme
            // (including Lime) is preserved; Amethyst is for first install
            // or an invalid/missing preference. Workout facts never change.
            var themeId by remember {
                mutableStateOf(
                    appearance.getString("theme_id",PursuitThemes.DEFAULT_ID)
                        ?.takeIf { PursuitThemes.find(it)!=null }
                        ?:PursuitThemes.DEFAULT_ID
                )
            }
            val themeUsesLightBars=PursuitThemes.resolve(themeId).isLight
            SideEffect {
                val bars=WindowCompat.getInsetsController(window,window.decorView)
                bars.isAppearanceLightStatusBars=themeUsesLightBars
                bars.isAppearanceLightNavigationBars=themeUsesLightBars
            }
            PursuitIronTheme(themeId=themeId) {
                Surface(Modifier.fillMaxSize().safeDrawingPadding()) {
                    NativeKotlinCandidateJourney(
                        themeId=themeId,
                        onThemeSelected={requested->
                            if(PursuitThemes.find(requested)!=null) {
                                themeId=requested
                                appearance.edit().putString("theme_id",requested).apply()
                            }
                        }
                    )
                }
            }
        }
    }
}
