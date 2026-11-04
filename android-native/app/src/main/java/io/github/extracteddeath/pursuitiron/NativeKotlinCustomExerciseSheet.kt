package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

/**
 * Author input only. Never infer equipment loading, session prescriptions
 * or completed reps from a catalog label. Domain revalidates against the
 * current reviewed stock immediately before durable save.
 */
private data class CustomSetFields(
    val type: DraftSetType = DraftSetType.WORKING,
    val weight: String = "",
    val minimum: String = "",
    val maximum: String = "",
    val rir: String = "",
    val seconds: String = "",
    val rest: String = "",
    val amplifier: CustomAmplifierKind? = null,
    val extraReps: String = "",
    val extraRest: String = "",
    val extraLoad: String = ""
)

private fun CustomAuthoredSet.toFields(): CustomSetFields =
    CustomSetFields(
        type = type,
        weight = (load as? DraftSetLoad.Verified)
            ?.value?.toPlainString().orEmpty(),
        minimum = reps.minimum.toString(),
        maximum = reps.maximum.toString(),
        rir = targetRir?.toString().orEmpty(),
        seconds = activeSeconds.toString(),
        rest = restSeconds.toString(),
        amplifier = amplifier?.kind,
        extraReps = amplifier?.followUpReps?.toString().orEmpty(),
        extraRest = amplifier?.followUpRestSeconds?.toString().orEmpty(),
        extraLoad = (amplifier?.followUpLoad as? DraftSetLoad.Verified)
            ?.value?.toPlainString().orEmpty()
    )

private fun parsedSets(
    forms: List<CustomSetFields>,
    option: CustomExercisePickerOption?
): List<CustomAuthoredSet>? {
    if (option == null || forms.isEmpty() || forms.size > 20) return null
    return runCatching {
        forms.map { field ->
            fun amount(text: String) = BigDecimal(text.trim())
            fun weight(value: String): DraftSetLoad =
                if (option.bodyweight) DraftSetLoad.ExplicitBodyweight
                else DraftSetLoad.Verified(
                    requireNotNull(option.loadUnit),
                    requireNotNull(option.loadBasis),
                    amount(value)
                )
            val kind = field.amplifier
            val amplifier = if (kind == null) null else CustomSetAmplifier(
                kind,
                requireNotNull(field.extraReps.toIntOrNull()),
                requireNotNull(field.extraRest.toIntOrNull()),
                if (kind == CustomAmplifierKind.DROP_SET)
                    weight(field.extraLoad)
                else null)
            CustomAuthoredSet(
                field.type,
                weight(field.weight),
                RepBand(
                    requireNotNull(field.minimum.toIntOrNull()),
                    requireNotNull(field.maximum.toIntOrNull())),
                if (field.rir.isBlank()) null else
                    requireNotNull(field.rir.toIntOrNull()),
                requireNotNull(field.seconds.toIntOrNull()),
                requireNotNull(field.rest.toIntOrNull()),
                amplifier
            )
        }
    }.getOrNull()
}

@Composable
private fun AuthorNumberField(
    value: String, title: String, onChange: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedTextField(
        value = value, onValueChange = { candidate ->
            if (candidate.length <= 12 &&
                candidate.all { it.isDigit() || it == '.' })
                onChange(candidate)
        },
        label = { Text(title) },
        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
        singleLine = true,
        modifier = modifier
    )
}

@Composable
internal fun NativeKotlinCustomExerciseSheet(
    busy: Boolean,
    available: List<CustomExercisePickerOption>,
    existing: CustomExerciseSlot?,
    onSave: (exerciseId: String, sets: List<CustomAuthoredSet>, note: String) -> Unit,
    onDismiss: () -> Unit
) {
    var search by remember(existing?.slotId) { mutableStateOf("") }
    var exerciseId by remember(existing?.slotId) {
        mutableStateOf(existing?.exerciseId.orEmpty())
    }
    var note by remember(existing?.slotId) {
        mutableStateOf(existing?.coachNote.orEmpty())
    }
    var forms by remember(existing?.slotId) {
        mutableStateOf(existing?.sets?.map { it.toFields() }
            ?: listOf(CustomSetFields()))
    }
    val option = available.firstOrNull { it.exerciseId == exerciseId }
    val parsed = parsedSets(forms, option)
    val filtered = available.filter {
        it.exerciseId.contains(search.trim(),ignoreCase = true) ||
            it.familyId.contains(search.trim(),ignoreCase = true)
    }.take(15)

    NativeEditSheet(busy = busy, onDismiss = onDismiss) {
        Text(if (existing == null) "Add your exercise" else "Edit authored sets",
            style = MaterialTheme.typography.titleLarge)
        Text("Every set stays yours. The coach will not silently round loads, swap movements or replace your target reps.",
            style = MaterialTheme.typography.bodyMedium)
        if (available.isEmpty()) {
            Text("No reviewed gym/catalog options are available. Connect the native gym inventory and reviewed movement catalog before editing prescriptions.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        } else if (existing == null) {
            OutlinedTextField(search, { search = it.take(80) },
                label = { Text("Find available exercise or variant") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true)
            filtered.forEach { candidate ->
                FilterChip(
                    selected = exerciseId == candidate.exerciseId,
                    onClick = { exerciseId = candidate.exerciseId },
                    label = {
                        Text(candidate.exerciseId.replace('-', ' ') +
                            " · " + candidate.variantId.replace('-', ' '))
                    })
            }
        } else {
            Text(existing.exerciseId.replace('-', ' '),
                style = MaterialTheme.typography.titleMedium)
            Text("Pinned: ${existing.pinned} · Attachment: ${existing.attachmentId ?: "none"}",
                style = MaterialTheme.typography.bodySmall)
        }
        if (option != null) {
            Text(
                if (option.bodyweight) "Explicit bodyweight (no external load)"
                else "Physically verified: ${option.loadUnit} · ${option.loadBasis}",
                style = MaterialTheme.typography.labelLarge)
            Text("Attachment: ${option.attachmentId ?: "none"}",
                style = MaterialTheme.typography.bodySmall)
        }
        forms.forEachIndexed { index, fields ->
            key(index) {
                HorizontalDivider()
                Text("Set ${index + 1}",
                    style = MaterialTheme.typography.titleMedium)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(DraftSetType.WORKING, DraftSetType.WARMUP).forEach { type ->
                        FilterChip(selected = fields.type == type,
                            onClick = {
                                forms = forms.toMutableList().also {
                                    it[index] = fields.copy(type = type,
                                        amplifier = if (type == DraftSetType.WARMUP)
                                            null else fields.amplifier)
                                }
                            }, label = { Text(type.name.lowercase().replaceFirstChar {
                                it.uppercase()
                            }) })
                    }
                }
                fun update(value: CustomSetFields) {
                    forms = forms.toMutableList().also { it[index] = value }
                }
                if (option?.bodyweight == false) {
                    AuthorNumberField(fields.weight, "Load · ${option.loadUnit}",
                        { update(fields.copy(weight = it)) },
                        Modifier.fillMaxWidth())
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthorNumberField(fields.minimum, "Minimum reps",
                        { update(fields.copy(minimum = it)) },
                        Modifier.weight(1f))
                    AuthorNumberField(fields.maximum, "Maximum reps",
                        { update(fields.copy(maximum = it)) },
                        Modifier.weight(1f))
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AuthorNumberField(fields.rir, "Target RIR (optional)",
                        { update(fields.copy(rir = it)) },
                        Modifier.weight(1f))
                    AuthorNumberField(fields.seconds, "Work seconds",
                        { update(fields.copy(seconds = it)) },
                        Modifier.weight(1f))
                    AuthorNumberField(fields.rest, "Rest seconds",
                        { update(fields.copy(rest = it)) },
                        Modifier.weight(1f))
                }
                if (fields.type != DraftSetType.WARMUP) {
                    Text("Advanced set technique",
                        style = MaterialTheme.typography.labelLarge)
                    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = fields.amplifier == null,
                            onClick = { update(fields.copy(amplifier = null)) },
                            label = { Text("None") })
                        CustomAmplifierKind.entries.forEach { technique ->
                            FilterChip(selected = fields.amplifier == technique,
                                onClick = {
                                    update(fields.copy(amplifier = technique))
                                },
                                label = { Text(technique.name.replace('_', ' ').lowercase()) })
                        }
                    }
                    if (fields.amplifier != null) {
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            AuthorNumberField(fields.extraReps, "Follow-up reps",
                                { update(fields.copy(extraReps = it)) },
                                Modifier.weight(1f))
                            AuthorNumberField(fields.extraRest, "Intra-set rest (s)",
                                { update(fields.copy(extraRest = it)) },
                                Modifier.weight(1f))
                        }
                        if (fields.amplifier == CustomAmplifierKind.DROP_SET) {
                            AuthorNumberField(fields.extraLoad, "Lower verified drop load",
                                { update(fields.copy(extraLoad = it)) },
                                Modifier.fillMaxWidth())
                        }
                    }
                }
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    TextButton(
                        onClick = {
                            forms = forms.toMutableList().also {
                                it.add(index + 1, fields.copy())
                            }
                        }, enabled = !busy && forms.size < 20) {
                        Text("Duplicate")
                    }
                    TextButton(
                        onClick = {
                            forms = forms.toMutableList().also {
                                val moved = it.removeAt(index)
                                it.add(index - 1, moved)
                            }
                        }, enabled = !busy && index > 0) {
                        Text("Earlier")
                    }
                    TextButton(
                        onClick = {
                            forms = forms.toMutableList().also {
                                val moved = it.removeAt(index)
                                it.add(index + 1, moved)
                            }
                        }, enabled = !busy && index < forms.lastIndex) {
                        Text("Later")
                    }
                    TextButton(
                        onClick = {
                            forms = forms.toMutableList().also { it.removeAt(index) }
                        }, enabled = !busy && forms.size > 1) {
                        Text("Remove")
                    }
                }
            }
        }
        OutlinedButton(onClick = { forms = forms + CustomSetFields() },
            enabled = !busy && forms.size < 20,
            modifier = Modifier.fillMaxWidth()) { Text("Add another set") }
        if (existing == null) {
            OutlinedTextField(note, { note = it.take(1000) },
                label = { Text("Your optional coach note") },
                modifier = Modifier.fillMaxWidth(),
                minLines = 2)
        }
        Text("Saving checks actual gym loading and every set again. A draft is never automatically approved.",
            style = MaterialTheme.typography.bodySmall)
        Button(
            onClick = {
                val sets = parsedSets(forms, option)
                if (sets != null) onSave(exerciseId, sets, note)
            },
            enabled = !busy && parsed != null && option != null &&
                note.length <= 1000,
            modifier = Modifier.fillMaxWidth()) {
            Text("Save authored sets")
        }
        TextButton(onClick = onDismiss,enabled = !busy) { Text("Cancel") }
    }
}
