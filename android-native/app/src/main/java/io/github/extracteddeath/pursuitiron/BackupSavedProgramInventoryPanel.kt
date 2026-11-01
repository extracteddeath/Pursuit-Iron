package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.BackupSavedProgramInventory

/**
 * Shared M3 Expressive presentation for an explicitly selected OFFLINE BACKUP.
 * Deliberately distinct from SavedProgramOverviewPanel/active Plan authority.
 * No click-to-activate, workout or history actions can be attached here.
 */
@Composable
internal fun BackupSavedProgramInventoryPanel(inventory: BackupSavedProgramInventory) {
    PreviewLabel("BACKUP COPY · NOT YOUR ACTIVE PLAN", emphasis = false)
    Text("Saved programs in selected copy",
        style = MaterialTheme.typography.titleMedium,
        fontWeight = FontWeight.SemiBold)
    if (inventory.entries.isEmpty()) {
        Text("No active saved programs in this copy.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
    inventory.entries.forEachIndexed { index, entry ->
        Surface(
            modifier = Modifier.fillMaxWidth(),
            shape = MaterialTheme.shapes.large,
            color = MaterialTheme.colorScheme.surfaceContainerHigh
        ) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(14.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    (index + 1).toString().padStart(2, '0'),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.primary
                )
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    Text(entry.name, style = MaterialTheme.typography.titleSmall,
                        maxLines = 2, overflow = TextOverflow.Ellipsis)
                    Text(
                        "${entry.trainingDays} training days · " +
                            "${entry.exerciseSlots} exercise slots",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
    Text(
        "Read-only backup snapshot. Deleted entries are omitted based on the copy's " +
            "saved revisions. None of these programs are loaded or used for training.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
}
