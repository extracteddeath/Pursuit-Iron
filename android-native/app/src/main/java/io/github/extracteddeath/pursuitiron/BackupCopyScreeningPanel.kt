package io.github.extracteddeath.pursuitiron

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.rememberUpdatedState
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
    data class ReselectionVerified(
        val fingerprint: UnverifiedBackupFingerprint,
        val preview: NativeBackupMergePreview
    ) : BackupScreeningStage
    data object ReselectionMismatch : BackupScreeningStage
    data class TooLargeForSchema(val fingerprint: UnverifiedBackupFingerprint) : BackupScreeningStage
    data object Rejected : BackupScreeningStage
}

@Composable
internal fun BackupCopyScreeningPanel(
    verifier: CanonicalBackupSchemaVerifier? = null,
    verifierLoading: Boolean = false,
    onPreviewBackupMerge: (suspend (ByteArray) -> NativeBackupMergePreview?)? = null,
    onVerifyBackupMerge: (suspend (ByteArray) -> Boolean)? = null,
    onDiscardBackupReview: () -> Unit = {},
    currentStoreChangeKey: Long = 0L
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val latestDiscard by rememberUpdatedState(onDiscardBackupReview)
    // Navigating away must revoke the process ticket; rotation loses this UI review too.
    DisposableEffect(Unit) { onDispose { latestDiscard() } }
    // Neither selected URI nor raw bytes are saveable or held in Compose state.
    var stage by remember(currentStoreChangeKey) { mutableStateOf<BackupScreeningStage>(BackupScreeningStage.NoSelection) }
    var wantsSchema by remember(currentStoreChangeKey) { mutableStateOf(false) }
    var wantsPrograms by remember(currentStoreChangeKey) { mutableStateOf(false) }
    var wantsMerge by remember(currentStoreChangeKey) { mutableStateOf(false) }
    var wantsReselection by remember(currentStoreChangeKey) { mutableStateOf(false) }

    val picker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument(),
        onResult = { selectedUri ->
            if (selectedUri == null) {
                onDiscardBackupReview()
                stage = BackupScreeningStage.NoSelection
            } else {
                val inspectMerge = wantsMerge && onPreviewBackupMerge != null
                val inspectReselection=wantsReselection && onVerifyBackupMerge != null
                val previous=if(inspectReselection)stage as? BackupScreeningStage.MergePreview else null
                val inspectPrograms = wantsPrograms && verifier != null
                val inspectSchema = wantsSchema && verifier != null
                stage = BackupScreeningStage.Reading
                scope.launch {
                    stage = try {
                        if (!inspectSchema && !inspectPrograms && !inspectMerge && !inspectReselection) {
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
                                    is ReadOnlyDocumentScreening.FingerprintOnly -> {
                                        if(inspectReselection) onDiscardBackupReview()
                                        BackupScreeningStage.TooLargeForSchema(screening.fingerprint)
                                    }
                                    is ReadOnlyDocumentScreening.SmallCandidate -> try {
                                        if (inspectReselection) {
                                            val matched=previous!=null &&
                                                checkNotNull(onVerifyBackupMerge).invoke(screening.bytes)
                                            if(matched) BackupScreeningStage.ReselectionVerified(
                                                checkNotNull(previous).fingerprint,previous.preview)
                                            else BackupScreeningStage.ReselectionMismatch
                                        } else if (inspectMerge) {
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
                        // Incomplete second-source reads never leave the old ticket active.
                        if(inspectReselection) onDiscardBackupReview()
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
                wantsReselection=false
                onDiscardBackupReview()
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
                    wantsReselection=false
                    onDiscardBackupReview()
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
                    wantsReselection=false
                    onDiscardBackupReview()
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
                NativeBackupMergeReviewPanel(current.preview)
                if(onVerifyBackupMerge!=null) {
                    OutlinedButton(
                        onClick = {
                            wantsSchema=false
                            wantsPrograms=false
                            wantsMerge=false
                            wantsReselection=true
                            picker.launch(arrayOf("application/json","text/plain"))
                        },
                        modifier=Modifier.fillMaxWidth()
                    ) { Text("Select same backup again · verify only") }
                }
            }
            is BackupScreeningStage.ReselectionVerified -> {
                BackupFingerprintText(current.fingerprint)
                Text("Same backup verified twice against the current saved data.",
                    style=MaterialTheme.typography.titleMedium)
                Text("This confirms only the selected source and current merge comparison. " +
                    "No data was imported, and restoration is still disabled.",
                    style=MaterialTheme.typography.bodySmall)
                NativeBackupMergeReviewPanel(current.preview)
            }
            BackupScreeningStage.ReselectionMismatch ->
                Text("The second selection did not match the reviewed backup, " +
                    "or the saved training data changed. Compare a new copy before proceeding. " +
                    "Nothing was imported.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.error)
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
            stage is BackupScreeningStage.ReselectionVerified ||
            stage is BackupScreeningStage.ReselectionMismatch ||
            stage is BackupScreeningStage.TooLargeForSchema) {
            OutlinedButton(onClick = {
                onDiscardBackupReview()
                stage = BackupScreeningStage.NoSelection
            }) {
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
