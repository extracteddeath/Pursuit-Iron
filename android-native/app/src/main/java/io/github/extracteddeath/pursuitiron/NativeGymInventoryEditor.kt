package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.NativeGymInventoryDraftResult

@Composable
private fun NativeGymLoadField(
    unit: String, draft: Map<String,String>, onChange: (String,String)->Unit,
    enabled: Boolean, key: String, label: String, hint: String
) {
    OutlinedTextField(
        value = draft[key].orEmpty(),
        onValueChange = { if (it.length <= 900) onChange(key, it) },
        modifier = Modifier.fillMaxWidth(),
        enabled = enabled,
        label = { Text("$label ($unit)") },
        supportingText = { Text(hint) },
        singleLine = true
    )
}

/**
 * Optional physical inventory, deliberately empty on first launch.
 * This is NOT a training prescription and never guesses gym availability.
 */
@Composable
internal fun NativeGymInventoryEditor(
    unit: String,
    draft: Map<String, String>,
    onChange: (String, String) -> Unit,
    validation: NativeGymInventoryDraftResult,
    enabled: Boolean
) {
    var expanded by rememberSaveable { mutableStateOf(draft.values.any { it.isNotBlank() }) }
    SectionLabel("Your actual gym weights")
    Text(
        "Optional. Enter only physical settings you have checked. Without them, weight entry stays manual.",
        style = MaterialTheme.typography.bodySmall,
        color = MaterialTheme.colorScheme.onSurfaceVariant
    )
    OutlinedButton(onClick = { expanded = !expanded }, enabled = enabled) {
        Text(if (expanded) "Hide weight setup" else "Add verified weight setup")
    }
    if (!expanded) return
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        NativeGymLoadField(unit, draft, onChange, enabled, "bar", "Empty bar weight", "Enter only if known; pair with your actual plates below.")
        NativeGymLoadField(unit, draft, onChange, enabled, "plates", "Plate pairs", "Weight x number of matching pairs, e.g. 2.5x2, 5x2, 25x1")
        NativeGymLoadField(unit, draft, onChange, enabled, "dumbbells", "Dumbbells per hand", "Exact available weights, e.g. 15, 20, 25, 30")
        NativeGymLoadField(unit, draft, onChange, enabled, "cable", "Cable stack", "Verified minimum, increment, maximum; e.g. 5, 5, 150")
        NativeGymLoadField(unit, draft, onChange, enabled, "machine", "Machine stack", "Only if this same stack applies: min, increment, max")
        NativeGymLoadField(unit, draft, onChange, enabled, "smith", "Smith TOTAL loads", "Actual selectable total loads, not assumed empty-bar weight")
        if (!validation.valid) Text(
            validation.error ?: "Check equipment entries.",
            color = MaterialTheme.colorScheme.error,
            style = MaterialTheme.typography.bodySmall
        ) else if (validation.json != null) Text(
            "Verified entries will be saved with your program. Unlisted equipment remains manual.",
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            style = MaterialTheme.typography.bodySmall
        )
        Text(
            "All entries use $unit. Switching units clears the weight setup to prevent inaccurate conversion.",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
