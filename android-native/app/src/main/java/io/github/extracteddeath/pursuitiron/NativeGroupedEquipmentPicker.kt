package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.KeyboardArrowUp
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.NativeEquipmentCatalog
import io.github.extracteddeath.pursuitiron.domain.NativeEquipmentGroup
import io.github.extracteddeath.pursuitiron.domain.NativeEquipmentSelectionPolicy

/**
 * Compact, reusable M3 equipment availability editor.
 *
 * Availability is NOT a verified weight inventory. The source runtime
 * supplies the set of offered IDs, and this UI never manufactures an ID,
 * a machine stack, a load step or an equipment dependency.
 */
@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
internal fun NativeGroupedEquipmentPicker(
    available: List<String>,
    selected: List<String>,
    enabled: Boolean,
    onSelectionChanged: (List<String>) -> Unit
) {
    val selectedCount = NativeEquipmentSelectionPolicy.selectedCount(selected, available)
    Text(
        "$selectedCount of ${available.size} equipment options selected",
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        NativeEquipmentCatalog.supported(available).forEach { group ->
            NativeEquipmentGroupSection(
                group = group,
                selected = selected,
                enabled = enabled,
                onSelectionChanged = onSelectionChanged,
                available = available
            )
        }
        // A newly introduced engine ID remains usable without inventing its
        // grouping. It can be categorized in a separately reviewed UI update.
        val known = NativeEquipmentCatalog.allIds.toSet()
        val other = available.filterNot { it in known }.distinct()
        if (other.isNotEmpty()) {
            SectionLabel("Other supported equipment")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                other.forEach { id ->
                    PreviewChoice(
                        id.replace('_', ' '), id in selected, enabled
                    ) {
                        onSelectionChanged(
                            NativeEquipmentSelectionPolicy.toggle(selected, id, available))
                    }
                }
            }
        }
        val retained = selected.filterNot { it in available }.distinct()
        if (retained.isNotEmpty()) {
            SectionLabel("Previously selected")
            Text(
                "These older choices are retained until you remove them.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                retained.forEach { id ->
                    PreviewChoice(
                        if (id == "rack") "Rack (legacy)" else id.replace('_', ' '),
                        true, enabled
                    ) {
                        onSelectionChanged(
                            NativeEquipmentSelectionPolicy.toggle(selected, id, available))
                    }
                }
            }
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun NativeEquipmentGroupSection(
    group: NativeEquipmentGroup,
    available: List<String>,
    selected: List<String>,
    enabled: Boolean,
    onSelectionChanged: (List<String>) -> Unit
) {
    // A stable group title is the local UI state key; no selected equipment is
    // stored separately or lost when categories are collapsed.
    var expanded by rememberSaveable(group.title) {
        mutableStateOf(group.title == "Free weights")
    }
    val count = NativeEquipmentSelectionPolicy.selectedInGroup(selected, group)
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = MaterialTheme.shapes.large
    ) {
        Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)) {
            TextButton(
                modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp),
                onClick = { expanded = !expanded }
            ) {
                Row(modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(group.title, modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface)
                    Text("$count / ${group.choices.size}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Icon(
                        if (expanded) Icons.Outlined.KeyboardArrowUp
                        else Icons.Outlined.KeyboardArrowDown,
                        contentDescription =
                            if (expanded) "Collapse ${group.title}"
                            else "Expand ${group.title}"
                    )
                }
            }
            if (expanded) {
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            onSelectionChanged(
                                NativeEquipmentSelectionPolicy.setGroup(
                                    selected, group, available, true))
                        },
                        enabled = enabled && count < group.choices.size
                    ) { Text("Select group") }
                    TextButton(
                        onClick = {
                            onSelectionChanged(
                                NativeEquipmentSelectionPolicy.setGroup(
                                    selected, group, available, false))
                        },
                        enabled = enabled && count > 0
                    ) { Text("Clear group") }
                }
                FlowRow(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    modifier = Modifier.padding(bottom = 8.dp)
                ) {
                    group.choices.forEach { choice ->
                        PreviewChoice(
                            choice.label, choice.id in selected, enabled
                        ) {
                            onSelectionChanged(
                                NativeEquipmentSelectionPolicy.toggle(
                                    selected, choice.id, available))
                        }
                    }
                }
            }
        }
    }
}
