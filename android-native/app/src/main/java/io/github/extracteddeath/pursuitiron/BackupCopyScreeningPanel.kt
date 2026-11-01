package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupScreening
import io.github.extracteddeath.pursuitiron.domain.UnverifiedBackupFingerprint
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** User-selected document is fingerprinted, never imported or retained. */
private sealed interface BackupScreeningStage {
    data object NoSelection : BackupScreeningStage
    data object Reading : BackupScreeningStage
    data class Screened(val fingerprint: UnverifiedBackupFingerprint) : BackupScreeningStage
    data object Rejected : BackupScreeningStage
}

@Composable
internal fun BackupCopyScreeningPanel() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Non-saveable: configuration changes do not persist URI or fingerprint.
    var stage by remember { mutableStateOf<BackupScreeningStage>(BackupScreeningStage.NoSelection) }
    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { selectedUri ->
            if (selectedUri != null) {
                stage = BackupScreeningStage.Reading
                scope.launch {
                    val next = try {
                        val screened = withContext(Dispatchers.IO) {
                            val source = context.contentResolver.openInputStream(selectedUri)
                                ?: throw IOException("Source unavailable")
                            ReadOnlyBackupScreening.fingerprint(source)
                        }
                        BackupScreeningStage.Screened(screened)
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        BackupScreeningStage.Rejected
                    }
                    stage = next
                }
            }
        }
    )
    SectionLabel("Backup safety", "Read-only preview")
    PursuitCard {
        Text("Inspect a backup copy", style = MaterialTheme.typography.titleMedium)
        Text(
            "Check a file's size and fingerprint. Nothing is imported, stored, " +
                "parsed into a program or used for training.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(
            onClick = {
                stage = BackupScreeningStage.NoSelection
                picker.launch(arrayOf("*/*"))
            },
            enabled = stage !is BackupScreeningStage.Reading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Choose backup copy") }
        when (val current = stage) {
            BackupScreeningStage.NoSelection -> {
                Text("No backup selected", style = MaterialTheme.typography.labelMedium)
            }
            BackupScreeningStage.Reading -> {
                Text("Checking selected file size and fingerprint…",
                    style = MaterialTheme.typography.bodySmall)
            }
            is BackupScreeningStage.Screened -> {
                Text(
                    "Byte check complete: ${current.fingerprint.byteCount} bytes · " +
                        "SHA-256 ${current.fingerprint.sha256Hex.take(12)}…",
                    style = MaterialTheme.typography.bodySmall
                )
                Text(
                    "Not verified by the canonical engine. No program can be opened yet.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                OutlinedButton(onClick = { stage = BackupScreeningStage.NoSelection }) {
                    Text("Clear result")
                }
            }
            BackupScreeningStage.Rejected -> {
                Text("File could not be screened. Nothing was imported or retained.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
            }
        }
    }
}
