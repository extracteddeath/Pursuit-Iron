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
import io.github.extracteddeath.pursuitiron.domain.BackupSavedProgramInventory
import io.github.extracteddeath.pursuitiron.domain.NativeBackupMergePreview
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupCandidateReader
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupScreening
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyDocumentScreening
import io.github.extracteddeath.pursuitiron.domain.ReadOnlyBackupSchemaResult
import io.github.extracteddeath.pursuitiron.domain.UnverifiedBackupFingerprint
import java.io.IOException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** Only summary metadata and fingerprint enter Compose memory/state. */
private sealed interface BackupScreeningStage {
    data object NoSelection : BackupScreeningStage
    data object Reading : BackupScreeningStage
    data class Screened(val fingerprint: UnverifiedBackupFingerprint) : BackupScreeningStage
    data class SchemaChecked(
        val fingerprint: UnverifiedBackupFingerprint,
        val result: ReadOnlyBackupSchemaResult
    ) : BackupScreeningStage
    data class ProgramInventory(
        val fingerprint: UnverifiedBackupFingerprint,
        val inventory: BackupSavedProgramInventory
    ) : BackupScreeningStage
    data class MergePreview(
        val fingerprint: UnverifiedBackupFingerprint,
        val preview: NativeBackupMergePreview
    ) : BackupScreeningStage
    data class TooLargeForSchema(val fingerprint: UnverifiedBackupFingerprint) : BackupScreeningStage
    data object Rejected : BackupScreeningStage
}

@Composable
internal fun BackupCopyScreeningPanel(
    verifier: CanonicalBackupSchemaVerifier? = null,
    verifierLoading: Boolean = false,
    onPreviewBackupMerge: (suspend (ByteArray) -> NativeBackupMergePreview?)? = null
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    // Neither selected URI nor raw bytes are saveable or held in Compose state.
    var stage by remember { mutableStateOf<BackupScreeningStage>(BackupScreeningStage.NoSelection) }
    var wantsSchema by remember { mutableStateOf(false) }
    var wantsPrograms by remember { mutableStateOf(false) }
    var wantsMerge by remember { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { selectedUri ->
            if (selectedUri != null) {
                val inspectMerge = wantsMerge && onPreviewBackupMerge != null
                val inspectPrograms = wantsPrograms && verifier != null
                val inspectSchema = wantsSchema && verifier != null
                stage = BackupScreeningStage.Reading
                scope.launch {
                    stage = try {
                        if (!inspectSchema && !inspectPrograms && !inspectMerge) {
                            val screened = withContext(Dispatchers.IO) {
                                val source = context.contentResolver.openInputStream(selectedUri)
                                    ?: throw IOException("Selected source unavailable")
                                val job = currentCoroutineContext()
                                ReadOnlyBackupScreening.fingerprint(
                                    source, checkActive = { job.ensureActive() }
                                )
                            }
                            BackupScreeningStage.Screened(screened)
                        } else {
                            // A single pass hashes ALL bytes; only a complete bounded
                            // file can be handed to the canonical, read-only checker.
                            val outcome = withContext(Dispatchers.IO) {
                                val source = context.contentResolver.openInputStream(selectedUri)
                                    ?: throw IOException("Selected source unavailable")
                                val job = currentCoroutineContext()
                                when (val screening = ReadOnlyBackupCandidateReader.screen(
                                    source, checkActive = { job.ensureActive() }
                                )) {
                                    is ReadOnlyDocumentScreening.FingerprintOnly ->
                                        BackupScreeningStage.TooLargeForSchema(screening.fingerprint)
                                    is ReadOnlyDocumentScreening.SmallCandidate -> try {
                                        if (inspectMerge) {
                                            val preview = checkNotNull(onPreviewBackupMerge).invoke(screening.bytes)
                                            BackupScreeningStage.MergePreview(screening.fingerprint,checkNotNull(preview))
                                        } else if (inspectPrograms) {
                                            val saved = verifier!!.inspectPrograms(
                                                screening.bytes, screening.fingerprint
                                            ).getOrThrow()
                                            BackupScreeningStage.ProgramInventory(screening.fingerprint, saved)
                                        } else {
                                            val valid = verifier!!.inspect(
                                                screening.bytes, screening.fingerprint
                                            ).getOrThrow()
                                            BackupScreeningStage.SchemaChecked(screening.fingerprint, valid)
                                        }
                                    } finally {
                                        screening.bytes.fill(0)
                                    }
                                }
                            }
                            outcome
                        }
                    } catch (cancelled: CancellationException) {
                        throw cancelled
                    } catch (_: Exception) {
                        // Never expose raw parser/provider exceptions or record data.
                        BackupScreeningStage.Rejected
                    }
                }
            }
        }
    )

    SectionLabel("Backup safety", "Read-only preview")
    PursuitCard {
        Text("Inspect a backup copy", style = MaterialTheme.typography.titleMedium)
        Text(
            "Choose a copy to check its size and fingerprint. Nothing is imported, " +
                "saved or used to prescribe training.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        FilledTonalButton(
            onClick = {
                wantsSchema = false
                wantsPrograms = false
                wantsMerge = false
                stage = BackupScreeningStage.NoSelection
                picker.launch(arrayOf("*/*"))
            },
            enabled = stage !is BackupScreeningStage.Reading,
            modifier = Modifier.fillMaxWidth()
        ) { Text("Check backup fingerprint") }
        if (verifier != null) {
            OutlinedButton(
                onClick = {
                    wantsSchema = true
                    wantsPrograms = false
                    wantsMerge = false
                    stage = BackupScreeningStage.NoSelection
                    picker.launch(arrayOf("application/json", "text/plain"))
                },
                enabled = stage !is BackupScreeningStage.Reading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Validate small backup structure") }
            OutlinedButton(
                onClick = {
                    wantsSchema = false
                    wantsPrograms = true
                    wantsMerge = false
                    stage = BackupScreeningStage.NoSelection
                    picker.launch(arrayOf("application/json", "text/plain"))
                },
                enabled = stage !is BackupScreeningStage.Reading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("View saved programs in backup") }
        }
        if (onPreviewBackupMerge != null && verifier != null) {
            OutlinedButton(
                onClick = {
                    wantsSchema = false
                    wantsPrograms = false
                    wantsMerge = true
                    stage = BackupScreeningStage.NoSelection
                    picker.launch(arrayOf("application/json", "text/plain"))
                },
                enabled = stage !is BackupScreeningStage.Reading,
                modifier = Modifier.fillMaxWidth()
            ) { Text("Compare backup with saved data · preview only") }
        }
        when (val current = stage) {
            BackupScreeningStage.NoSelection ->
                Text("No backup selected", style = MaterialTheme.typography.labelMedium)
            BackupScreeningStage.Reading ->
                Text("Reading selected copy without storing it…", style = MaterialTheme.typography.bodySmall)
            is BackupScreeningStage.Screened ->
                BackupFingerprintText(current.fingerprint)
            is BackupScreeningStage.TooLargeForSchema -> {
                BackupFingerprintText(current.fingerprint)
                Text("The complete file was fingerprinted. It exceeds the current " +
                    "1 MiB read-only schema-check limit; nothing was validated or imported.",
                    style = MaterialTheme.typography.bodySmall)
            }
            is BackupScreeningStage.SchemaChecked -> {
                BackupFingerprintText(current.fingerprint)
                Text(
                    "Canonical v${current.result.storeVersion} structure checked. " +
                        "Programs: ${current.result.counts["saved"] ?: 0} · " +
                        "History records: ${current.result.counts["history"] ?: 0}",
                    style = MaterialTheme.typography.bodyMedium
                )
                Text("Structure check only. No personal program was opened, imported or altered.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            is BackupScreeningStage.MergePreview -> {
                BackupFingerprintText(current.fingerprint)
                val preview = current.preview
                Text("Read-only comparison with your saved data",style=MaterialTheme.typography.titleMedium)
                Text("Saved workouts: currently ${preview.current.history} · selected copy ${preview.incoming.history} · hypothetical ${preview.hypothetical.history}")
                Text("Saved programs: currently ${preview.current.saved} · selected copy ${preview.incoming.saved} · hypothetical ${preview.hypothetical.saved}")
                Text("Overlapping record conflicts: ${preview.conflicts} · records lost to history cap: ${preview.historyDropped}")
                if(preview.flags.isNotEmpty()) Text(
                    "Attention: " + preview.flags.map { flag -> when(flag) {
                        "HISTORY_CAP_LOSS" -> "history records could be removed"
                        "NATIVE_STORE_CAPACITY" -> "merged data would exceed supported storage"
                        else -> "revisions require review"
                    }}.joinToString("; "),color=MaterialTheme.colorScheme.error)
                Text("Estimate only. Settings and other data need review. Nothing was imported, changed or approved for restoration.",
                    style=MaterialTheme.typography.bodySmall)
            }
            is BackupScreeningStage.ProgramInventory -> {
                BackupFingerprintText(current.fingerprint)
                BackupSavedProgramInventoryPanel(current.inventory)
            }
            BackupScreeningStage.Rejected ->
                Text("The selected copy could not be checked. Nothing was imported or retained.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error)
        }
        if (stage is BackupScreeningStage.Screened ||
            stage is BackupScreeningStage.SchemaChecked ||
            stage is BackupScreeningStage.ProgramInventory ||
            stage is BackupScreeningStage.MergePreview ||
            stage is BackupScreeningStage.TooLargeForSchema) {
            OutlinedButton(onClick = { stage = BackupScreeningStage.NoSelection }) {
                Text("Clear result")
            }
        }
        if (verifierLoading) {
            Text("Checking local read-only engine compatibility…",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        } else if (verifier == null) {
            Text("Canonical file validation is not connected in this preview.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun BackupFingerprintText(fingerprint: UnverifiedBackupFingerprint) {
    Text(
        "Fingerprint: ${fingerprint.byteCount} bytes · SHA-256 ${fingerprint.sha256Hex.take(12)}…",
        style = MaterialTheme.typography.bodySmall
    )
}
