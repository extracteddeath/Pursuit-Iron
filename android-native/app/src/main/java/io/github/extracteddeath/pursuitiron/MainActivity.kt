package io.github.extracteddeath.pursuitiron

import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.MotionScheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

class MainActivity : ComponentActivity() {
    // These Compose state holders are updated on Main only; no engine status
    // is represented as a workout capability.
    private var readOnlyVerifier by mutableStateOf<PackagedReadOnlySchemaVerifier?>(null)
    private var verifierLoading by mutableStateOf(true)
    private val assetScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var released = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        // Draw the native UI immediately. Asset I/O, manifest verification,
        // SHA-256 and worker construction MUST NOT block the first frame.
        setContent {
            PursuitIronTheme {
                NativePreviewApp(
                    schemaVerifier = readOnlyVerifier,
                    schemaVerifierLoading = verifierLoading,
                    syntheticOutlineReader = readOnlyVerifier
                )
            }
        }
        assetScope.launch {
            val loaded = PackagedReadOnlySchemaVerifier.openIfPackaged(
                this@MainActivity.applicationContext
            )
            // A cancelled Activity must still close a verifier created just
            // before disposal. The bounded local-read job has no network I/O.
            withContext(NonCancellable + Dispatchers.Main.immediate) {
                if (released) loaded?.close()
                else {
                    readOnlyVerifier = loaded
                    verifierLoading = false
                }
            }
        }
    }

    override fun onDestroy() {
        released = true
        assetScope.cancel()
        // Close asynchronously; never block the Android UI thread.
        readOnlyVerifier?.close()
        readOnlyVerifier = null
        super.onDestroy()
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
fun PursuitIronTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, motionScheme = MotionScheme.expressive(),
        typography = Typography(), shapes = PursuitShapes, content = content)
}
