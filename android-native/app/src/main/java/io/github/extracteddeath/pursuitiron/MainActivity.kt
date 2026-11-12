package io.github.extracteddeath.pursuitiron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat

/**
 * Early beta entry: the ONE Kotlin-owned journey. It never constructs
 * NativeRuntimeHost, reads a JS training cursor, or starts the legacy worker.
 *
 * This deliberately does not convert an old private JS store into Kotlin
 * history. Historical import requires a separate owner-verified, lossless,
 * explicitly accepted migration; no hidden reset or merged athlete identity.
 */
open class MainActivity:ComponentActivity() {
    override fun onCreate(savedInstanceState:Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val appearance=getSharedPreferences("pursuit_native_appearance",MODE_PRIVATE)
        setContent {
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
                Surface(
                    Modifier.fillMaxSize(),
                    color=MaterialTheme.colorScheme.background
                ) {
                    Box(Modifier.fillMaxSize().safeDrawingPadding()) {
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
}

private val PursuitShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(
        topStart = 32.dp, topEnd = 20.dp, bottomEnd = 32.dp, bottomStart = 32.dp
    )
)

@Composable
fun PursuitIronTheme(themeId:String=PursuitThemes.DEFAULT_ID,content: @Composable () -> Unit) {
    val palette=PursuitThemes.resolve(themeId)
    CompositionLocalProvider(LocalPursuitPalette provides palette) {
        MaterialTheme(colorScheme=palette.scheme, motionScheme = MotionScheme.expressive(),
            typography=PursuitTypography,shapes=PursuitShapes,content=content)
    }
}
