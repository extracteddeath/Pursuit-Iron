package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.FilterChip
import androidx.compose.material3.TextButton
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
import io.github.extracteddeath.pursuitiron.domain.NativeExerciseWeightEditorPolicy
import io.github.extracteddeath.pursuitiron.domain.NativeGymLoadingFieldPolicy
import io.github.extracteddeath.pursuitiron.domain.NativeStackCandidate

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
 * Edits a user-verified per-exercise profile without asking the athlete to
 * type protocol prefixes. Switching modes is disabled while numeric values
 * exist; the user must explicitly clear the values before changing basis.
 */
@Composable
private fun NativeExerciseWeightField(
    candidate: NativeStackCandidate,
    unit: String,
    draft: Map<String,String>,
    onChange: (String,String) -> Unit,
    enabled: Boolean
) {
    val policy=NativeExerciseWeightEditorPolicy
    val key="override:${candidate.id}"
    val raw=draft[key].orEmpty()
    val exactOnly=policy.exactOnly(candidate)
    var requestedKind by rememberSaveable(candidate.id) {
        mutableStateOf(policy.preferredKind(candidate, raw))
    }
    val kind=if(raw.isNotBlank())policy.preferredKind(candidate,raw) else requestedKind
    Column(verticalArrangement=Arrangement.spacedBy(4.dp)) {
        Text(candidate.name,style=MaterialTheme.typography.titleSmall)
        if(!exactOnly) {
            androidx.compose.foundation.layout.Row(
                horizontalArrangement=Arrangement.spacedBy(8.dp)
            ) {
                FilterChip(
                    selected=kind=="exact",
                    onClick={requestedKind="exact"},
                    enabled=enabled && raw.isBlank(),
                    label={Text("Exact loads")}
                )
                FilterChip(
                    selected=kind=="range",
                    onClick={requestedKind="range"},
                    enabled=enabled && raw.isBlank(),
                    label={Text("Verified range")}
                )
            }
        }
        OutlinedTextField(
            value=policy.entryValues(raw),
            onValueChange={values ->
                if(values.length<=800) onChange(key,
                    policy.formatEntry(kind,values,exactOnly))
            },
            modifier=Modifier.fillMaxWidth(),
            enabled=enabled,
            label={Text(if(kind=="exact")"Selectable logged weights ($unit)"
                else "Min, increment, max ($unit)")},
            supportingText={Text(
                if(exactOnly) "Enter exact weights as recorded. Never assume sled, plate-side, or lever resistance."
                else if(kind=="range")
                    "Only use a range when the actual stack increment and limits are checked."
                else "Enter each physical stack setting you have verified, separated by commas."
            )},
            singleLine=true
        )
        if(raw.isNotBlank()) {
            TextButton(onClick={onChange(key,"")},enabled=enabled) {
                Text("Clear this exercise's weights")
            }
        }
    }
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
    enabled: Boolean,
    candidates: List<NativeStackCandidate> = emptyList(),
    initiallyExpanded: Boolean = false,
    focusExerciseId: String? = null,
    availableEquipment: Collection<String>? = null
) {
    var expanded by rememberSaveable { mutableStateOf(initiallyExpanded || draft.values.any { it.isNotBlank() }) }
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
    val focused=candidates.firstOrNull { it.id==focusExerciseId }
    val relevantFields=NativeGymLoadingFieldPolicy.visibleFields(availableEquipment,draft).toSet()
    val retainedFields=NativeGymLoadingFieldPolicy.retainedUnselectedFields(availableEquipment,draft)
    Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        if(focused!=null) {
            Text("Linked exercise: ${focused.name}",style=MaterialTheme.typography.titleMedium)
            Text(
                "This movement's verified loading profile must be cleared and saved before " +
                    "its final occurrence can be removed from the program.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant
            )
            NativeExerciseWeightField(focused,unit,draft,onChange,enabled)
            Text("A cleared entry remains saved until you review and confirm these changes.",
                style=MaterialTheme.typography.labelSmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (relevantFields.isEmpty() && candidates.isEmpty()) {
            Text(
                if (availableEquipment?.isEmpty() == true)
                    "Choose equipment above before adding actual weight settings."
                else
                    "These equipment choices have no shared weight field. Verify each exercise's actual loading after the program is generated.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
        if (retainedFields.isNotEmpty()) {
            Text("Previously entered weights for unselected equipment remain editable. They do not make that equipment available in your program.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if ("bar" in relevantFields) NativeGymLoadField(unit, draft, onChange, enabled,
            "bar", "Empty bar weight", "Enter only if known; pair with your actual plates below.")
        if ("plates" in relevantFields) NativeGymLoadField(unit, draft, onChange, enabled,
            "plates", "Plate pairs", "Weight x number of matching pairs, e.g. 2.5x2, 5x2, 25x1")
        if ("dumbbells" in relevantFields) NativeGymLoadField(unit, draft, onChange, enabled,
            "dumbbells", "Dumbbells per hand", "Exact available weights, e.g. 15, 20, 25, 30")
        if ("cable" in relevantFields) NativeGymLoadField(unit, draft, onChange, enabled,
            "cable", "Cable stack", "Verified minimum, increment, maximum; e.g. 5, 5, 150")
        if ("machine" in relevantFields) NativeGymLoadField(unit, draft, onChange, enabled,
            "machine", "Machine stack", "Only if this same stack applies: min, increment, max")
        if ("smith" in relevantFields) NativeGymLoadField(unit, draft, onChange, enabled,
            "smith", "Smith TOTAL loads", "Actual selectable total loads, not assumed empty-bar weight")
        if (NativeGymLoadingFieldPolicy.hasSpecialtyMachine(availableEquipment)) {
            Text("For specialty machines, verify the exact weights AS LOGGED for each movement after the program is generated. A generic machine stack cannot establish leg press, hack squat, hip, or other specialty-machine increments.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if(candidates.isNotEmpty()) {
            val configured=NativeExerciseWeightEditorPolicy.configuredCount(candidates,draft)
            var showSpecific by rememberSaveable(focusExerciseId) { mutableStateOf(
                focused!=null || configured>0
            ) }
            OutlinedButton(onClick={showSpecific=!showSpecific},enabled=enabled) {
                Text(if(showSpecific) "Hide exercise weight settings"
                    else "Set exercise weights ($configured configured)")
            }
            if(showSpecific) {
                var search by rememberSaveable { mutableStateOf("") }
                var configuredOnly by rememberSaveable { mutableStateOf(false) }
                OutlinedTextField(
                    value=search,
                    onValueChange={if(it.length<=80)search=it},
                    modifier=Modifier.fillMaxWidth(),
                    enabled=enabled,
                    label={Text("Find a program exercise")},
                    singleLine=true
                )
                FilterChip(selected=configuredOnly,
                    onClick={configuredOnly=!configuredOnly},
                    enabled=enabled,
                    label={Text("Configured only ($configured)")})
                Text("Only exercises in your saved program appear. Verify each machine's actual selectable loads; no factory increments are assumed.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                val listed=NativeExerciseWeightEditorPolicy.eligible(
                    candidates.filterNot { it.id==focused?.id },
                    search,configuredOnly,draft)
                if(listed.isEmpty()) Text("No matching exercises. Clear the search or filter to see more.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                listed.forEach { candidate ->
                    NativeExerciseWeightField(candidate,unit,draft,onChange,enabled)
                }
            }
        }
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
