package io.github.extracteddeath.pursuitiron

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import android.os.Build
import io.github.extracteddeath.pursuitiron.domain.UnconnectedEngineGateway

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent { PursuitIronTheme { NativeFoundationScreen() } }
    }
}

@Composable
fun PursuitIronTheme(content: @Composable () -> Unit) {
    val context = LocalContext.current
    val dark = androidx.compose.foundation.isSystemInDarkTheme()
    val scheme = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && dark -> dynamicDarkColorScheme(context)
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> dynamicLightColorScheme(context)
        dark -> darkColorScheme()
        else -> lightColorScheme()
    }
    MaterialTheme(colorScheme = scheme, typography = Typography(), shapes = Shapes(), content = content)
}

@Composable
private fun NativeFoundationScreen() {
    val engine = UnconnectedEngineGateway()
    Scaffold(
        topBar = { Surface(tonalElevation = 2.dp) {
            Text("Pursuit Iron", modifier = Modifier.fillMaxWidth().padding(20.dp),
                style = MaterialTheme.typography.headlineMedium)
        } }
    ) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Native foundation", style = MaterialTheme.typography.headlineLarge)
            ElevatedCard(modifier = Modifier.fillMaxWidth(), shape = MaterialTheme.shapes.extraLarge) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("Workout", style = MaterialTheme.typography.titleLarge)
                    Text("Your existing program and training history are not loaded into this preview.")
                    AssistChip(onClick = {}, enabled = false, label = { Text("Engine: not connected") })
                    Button(onClick = {}, enabled = false, modifier = Modifier.fillMaxWidth()) {
                        Text("Start workout — awaiting parity verification")
                    }
                }
            }
            OutlinedCard(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Migration safety", style = MaterialTheme.typography.titleMedium)
                    Text("The JavaScript training engine remains canonical. No training data has been imported or modified.")
                    Text("Gateway: ${engine.status}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}
