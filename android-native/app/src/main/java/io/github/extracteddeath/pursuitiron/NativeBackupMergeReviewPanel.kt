package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.NativeBackupMergeCounts
import io.github.extracteddeath.pursuitiron.domain.NativeBackupMergePreview

/**
 * Shared, redacted M3 review. This is not a consent sheet and never receives
 * a picked URI, personal record, imported candidate or storage handle.
 */
@Composable
internal fun NativeBackupMergeReviewPanel(preview: NativeBackupMergePreview) {
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Potential changes", style = MaterialTheme.typography.titleMedium)
        Text("Current · Backup · If combined",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
        BackupMergeCountRow("Workouts",preview.current.history,
            preview.incoming.history,preview.hypothetical.history)
        BackupMergeCountRow("Programs",preview.current.saved,
            preview.incoming.saved,preview.hypothetical.saved)
        BackupMergeCountRow("Custom programs",preview.current.custom,
            preview.incoming.custom,preview.hypothetical.custom)
        BackupMergeCountRow("Training cycles",preview.current.cycles,
            preview.incoming.cycles,preview.hypothetical.cycles)
        BackupMergeCountRow("Gyms",preview.current.gyms,
            preview.incoming.gyms,preview.hypothetical.gyms)
        HorizontalDivider()
        Text("Review of existing data",style = MaterialTheme.typography.titleSmall)
        Text("Overlapping revisions: ${preview.conflicts} · history cap losses: ${preview.historyDropped}")
        Text("Records removed: ${preview.ownedRecordsRemoved} · replaced: ${preview.ownedRecordsReplaced}")
        Text("Settings changed: ${preview.settingsChanged}")
        if (preview.flags.isEmpty()) {
            Text("No known merge risks were flagged. This is not a guarantee that restoring is safe.",
                color=MaterialTheme.colorScheme.onSurfaceVariant,
                style=MaterialTheme.typography.bodyMedium)
        } else {
            preview.flags.forEach { flag ->
                Text(
                    text="• "+when(flag) {
                        "HISTORY_CAP_LOSS" -> "Some saved workouts would be lost to the history limit."
                        "NATIVE_STORE_CAPACITY" -> "The merged store could exceed the supported size."
                        "RECORD_CONFLICTS_REQUIRE_REVIEW" -> "Overlapping record revisions need a decision."
                        "OWNED_RECORDS_REMOVED" -> "Existing training records would be removed."
                        "OWNED_RECORDS_REPLACED" -> "Existing training records would be replaced."
                        "SETTINGS_CHANGED" -> "Saved preferences or other settings would change."
                        else -> "Additional differences require review."
                    },
                    color=MaterialTheme.colorScheme.error,
                    style=MaterialTheme.typography.bodyMedium
                )
            }
        }
        HorizontalDivider()
        Text("Restore is not available yet",style=MaterialTheme.typography.titleSmall,
            fontWeight=FontWeight.SemiBold)
        Text("The comparison is informational. No backup has been imported, no current data has changed, " +
            "and this result is not approval to restore. A future restore will require a new source check, " +
            "explicit confirmation and verified rollback.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun BackupMergeCountRow(label: String,current: Int,incoming: Int,combined: Int) {
    Row(modifier=Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.SpaceBetween) {
        Text(label,style=MaterialTheme.typography.bodyMedium,
            modifier=Modifier.weight(1f))
        Text("${current} · ${incoming} · ${combined}",
            style=MaterialTheme.typography.bodyMedium,
            fontWeight=FontWeight.Medium)
    }
}
