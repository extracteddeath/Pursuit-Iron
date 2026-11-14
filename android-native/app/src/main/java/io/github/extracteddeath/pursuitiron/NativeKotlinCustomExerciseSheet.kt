package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.listSaver
import androidx.compose.runtime.saveable.rememberSaveable
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
    /** Explicit total follow-up work time; blank means not yet reviewed. */
    val extraWork: String = "",
    val extraLoad: String = ""
)

// Only unsaved editor text is Bundle-restored. It never certifies physical
// stock, source validity, a swap, or an actual workout receipt.
private val customSetFieldsSaver = listSaver<List<CustomSetFields>,String>(
    save = { fields -> fields.flatMap { field ->
        listOf(field.type.name,field.weight,field.minimum,field.maximum,
            field.rir,field.seconds,field.rest,field.amplifier?.name.orEmpty(),
            field.extraReps,field.extraRest,field.extraWork,field.extraLoad)
    } },
    restore = { serialized ->
        serialized.chunked(12).filter { it.size==12 }.take(20).map { row ->
            CustomSetFields(
                type=DraftSetType.entries.firstOrNull { it.name==row[0] }
                    ?:DraftSetType.WORKING,
                weight=row[1],minimum=row[2],maximum=row[3],rir=row[4],
                seconds=row[5],rest=row[6],
                amplifier=CustomAmplifierKind.entries.firstOrNull {
                    it.name==row[7]
                },
                extraReps=row[8],extraRest=row[9],extraWork=row[10],
                extraLoad=row[11])
        }.ifEmpty { listOf(CustomSetFields()) }
    }
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
        extraWork = amplifier?.followUpActiveSeconds?.toString().orEmpty(),
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
                else null,
                if(field.extraWork.isBlank())null else
                    requireNotNull(field.extraWork.toIntOrNull()))
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
    onDismiss: () -> Unit,
    scopeLabel: String? = null,
    saveLabel: String = "Save authored sets",
    onRestore: (() -> Unit)? = null,
    feedback: String? = null,
    /** Explicit replacement only; existing authored sets are NEVER reused. */
    replacing: CustomExerciseSlot? = null,
    /** Issued-workout replacement is not a program-draft edit. */
    workoutOnlySwap: Boolean = false,
    /** Exact other exercises in this program day or issued workout, if known. */
    otherDayExerciseIds: List<String>? = null,
    /** Search suggestion only. Never preselects or transfers a load/target. */
    initialSearchQuery:String = ""
) {
    // The issued/current source identity and *all* authored prescriptions
    // fence restoration. An amended source must not inherit an old editor.
    val authorScope=buildString {
        append(if(workoutOnlySwap) "workout:" else "program:")
        append(existing?.toString() ?: "new")
        append("|replacement:")
        append(replacing?.toString() ?: "none")
    }
    var search by rememberSaveable(authorScope,initialSearchQuery) {
        mutableStateOf(initialSearchQuery)
    }
    var exerciseId by rememberSaveable(authorScope) {
        mutableStateOf(existing?.exerciseId.orEmpty())
    }
    var samePatternOnly by rememberSaveable(authorScope) {mutableStateOf(false)}
    var sharedMusclesOnly by rememberSaveable(authorScope) {mutableStateOf(false)}
    var sameRoleOnly by rememberSaveable(authorScope) {mutableStateOf(false)}
    var sameFamilyOnly by rememberSaveable(authorScope) {mutableStateOf(false)}
    // Reconfirmation is intentionally required after process recreation.
    var swapConfirmation by remember(replacing?.slotId) {mutableStateOf(false)}
    var note by rememberSaveable(authorScope) {
        mutableStateOf(existing?.coachNote.orEmpty())
    }
    var forms by rememberSaveable(authorScope,saver=customSetFieldsSaver) {
        mutableStateOf(existing?.sets?.map { it.toFields() }
            ?: listOf(CustomSetFields()))
    }
    val option = available.firstOrNull { it.exerciseId == exerciseId }
    val parsed = parsedSets(forms, option)
    // A query can span the movement, equipment variant, attachment and muscle.
    // Matching is independent of token order; ranking is still calculated from
    // the ENTIRE verified gym catalog before the search filter is applied.
    val terms=search.trim().replace('_',' ').replace('-',' ')
        .lowercase().split(Regex("\\s+"))
        .filter { it.isNotEmpty() }
    val labeled=available.filter { choice ->
        val searchable=(listOfNotNull(
            choice.exerciseId,choice.familyId,choice.variantId,choice.attachmentId,
            kotlinExerciseLabel(choice.exerciseId),choice.pattern?.name,
            choice.role?.name
        ) + choice.muscleCredits.map { it.muscle.name })
            .joinToString(" ").replace('_',' ').replace('-',' ').lowercase()
        terms.all { searchable.contains(it) }
    }
    // Rank using the FULL eligible current-gym catalog first. Search only
    // filters the visible candidates; it must never remove the source
    // movement before its pattern/family/muscle context is inspected.
    val ranked=CustomExerciseSwapRankingDomain.rank(
        available,replacing?.exerciseId,
        samePatternOnly=samePatternOnly,
        sharedMusclesOnly=sharedMusclesOnly,
        sameRoleOnly=sameRoleOnly,
        sameFamilyOnly=sameFamilyOnly,
        otherDayExerciseIds=otherDayExerciseIds,
        sourceHasWorkingSets=replacing?.sets?.any {
            it.type==DraftSetType.WORKING
        }==true)
    // Compare the entire verified choice, including attachment/variant.
    // A matching name must not make a sibling attachment appear authorized.
    val matchedChoices=labeled.toSet()
    val searched=ranked.filter { it.choice in matchedChoices }
    // This is a paging cap, not a catalog cap. The complete verified ranked
    // pool remains available, even when it contains more than twenty choices.
    var visibleCount by remember(search,samePatternOnly,sharedMusclesOnly,
        sameRoleOnly,sameFamilyOnly,replacing?.slotId) { mutableIntStateOf(20) }
    val filtered=searched.take(visibleCount)
    // A replacement change clears drafts only on a real athlete tap, not
    // on rotation/restoration (which would destroy saved unsent edits).
    // Any changed target or entered set invalidates previous confirmation.
    LaunchedEffect(exerciseId,forms,note,replacing?.slotId) {
        swapConfirmation=false
    }

    NativeEditSheet(busy = busy, onDismiss = onDismiss) {
        Text(when {
            replacing!=null -> "Swap exercise"
            existing==null -> "Add your exercise"
            else -> "Edit authored sets"
        },style=MaterialTheme.typography.titleLarge)
        replacing?.let {source ->
            Text(if(workoutOnlySwap)
                "Replacing ${kotlinExerciseLabel(source.exerciseId)} in this workout only. Your saved program will not change."
                else "Replacing ${kotlinExerciseLabel(source.exerciseId)} on this program day only.",
                style=MaterialTheme.typography.titleMedium)
            Text(if(workoutOnlySwap)
                "Choose a verified replacement and author its new sets. Existing recorded work is protected; old loads, techniques, attachments and notes do not carry over. Only an unstarted, unlinked exercise can be swapped."
                else "Choose a new exercise and author new sets. Old weights, advanced techniques, attachments, notes and history are never transferred. Protected week edits and supersets require separate review.",
                style=MaterialTheme.typography.bodySmall,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
        }
        scopeLabel?.let { Text(it,style=MaterialTheme.typography.titleMedium) }
        feedback?.let { Text(it,style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.error) }
        Text("Every set stays yours. The coach will not silently round loads, swap movements or replace your target reps.",
            style = MaterialTheme.typography.bodyMedium)
        if (available.isEmpty()) {
            Text("No reviewed gym/catalog options are available. Connect the native gym inventory and reviewed movement catalog before editing prescriptions.",
                color = MaterialTheme.colorScheme.error,
                style = MaterialTheme.typography.bodyMedium)
        } else if (existing == null) {
            OutlinedTextField(search, { search = it.take(80) },
                label = { Text("Search exercise, family, pattern, attachment or muscle") },
                modifier = Modifier.fillMaxWidth(),
                singleLine = true)
            if(replacing!=null) {
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    FilterChip(selected=!samePatternOnly && !sharedMusclesOnly && !sameRoleOnly && !sameFamilyOnly,
                        onClick={samePatternOnly=false;sharedMusclesOnly=false;sameRoleOnly=false;sameFamilyOnly=false},
                        label={Text("All available")})
                    FilterChip(selected=samePatternOnly,
                        onClick={
                            samePatternOnly=!samePatternOnly
                            sharedMusclesOnly=false
                            sameRoleOnly=false
                            sameFamilyOnly=false
                        },label={Text("Same pattern")})
                    FilterChip(selected=sameFamilyOnly,
                        onClick={
                            sameFamilyOnly=!sameFamilyOnly
                            samePatternOnly=false
                            sharedMusclesOnly=false
                            sameRoleOnly=false
                        },label={Text("Same family")})
                    FilterChip(selected=sharedMusclesOnly,
                        onClick={
                            sharedMusclesOnly=!sharedMusclesOnly
                            samePatternOnly=false
                            sameRoleOnly=false
                            sameFamilyOnly=false
                        },label={Text("Shared muscles")})
                    FilterChip(selected=sameRoleOnly,
                        onClick={
                            sameRoleOnly=!sameRoleOnly
                            samePatternOnly=false
                            sharedMusclesOnly=false
                            sameFamilyOnly=false
                        },label={Text("Same role")})
                }
                val source=available.firstOrNull {
                    it.exerciseId==replacing.exerciseId
                }
                Text(if(source==null)
                    "The former exercise is not in the current gym/catalog. No similarity score is assumed."
                    else "Alternatives rank by reviewed movement pattern, family, training role and credited muscles. When the full day catalog is verified, sole direct muscle coverage is also considered. A match is not a guarantee of equal load or effect.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(filtered.isEmpty())Text("No verified matches. Try another search or filter.",
                style=MaterialTheme.typography.bodySmall)
            filtered.forEach { optionRow ->
                val candidate=optionRow.choice
                FilterChip(
                    selected=exerciseId==candidate.exerciseId,
                    onClick={
                        if(exerciseId!=candidate.exerciseId) {
                            exerciseId=candidate.exerciseId
                            if(replacing!=null) {
                                forms=listOf(CustomSetFields())
                                note=""
                            }
                        }
                    },
                    label={Text(kotlinExerciseLabel(candidate.exerciseId)+
                        " · "+candidate.variantId.replace('-', ' ')+
                        (candidate.attachmentId?.let {
                            " · attachment: "+it.replace('_',' ').replace('-',' ')
                        } ?: ""))})
                if(replacing!=null)Text(optionRow.explanation,
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            if(searched.size>filtered.size) {
                Text("Showing ${filtered.size} of ${searched.size} verified ranked choices.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    OutlinedButton(
                        onClick={visibleCount=(visibleCount+20).coerceAtMost(searched.size)},
                        modifier=Modifier.heightIn(min=48.dp)
                    ) { Text("Show next ${minOf(20,searched.size-filtered.size)}") }
                    TextButton(
                        onClick={visibleCount=searched.size},
                        modifier=Modifier.heightIn(min=48.dp)
                    ) { Text("Show all") }
                }
            }
        } else {
            Text(kotlinExerciseLabel(existing.exerciseId),
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
                        AuthorNumberField(fields.extraWork,
                            "Extra working time (s)",
                            { update(fields.copy(extraWork = it)) },
                            Modifier.fillMaxWidth())
                        Text("Enter total additional active seconds across every follow-up, separate from the main set and intra-set rest. Leaving this blank keeps time-limited approval blocked; no duration is inferred from rep count.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant)
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
        Text(if(workoutOnlySwap)
            "Your reviewed replacement is rechecked against the gym and saved immediately to this workout only after confirmation. It never edits the approved program."
            else "Saving checks actual gym loading and every set again. A draft is never automatically approved.",
            style = MaterialTheme.typography.bodySmall)
        val canSubmit=!busy && parsed!=null && option!=null &&
            note.length<=1000 && (replacing==null ||
                exerciseId!=replacing.exerciseId)
        if(replacing!=null && swapConfirmation) {
            PursuitHistoryStatus(
                if(workoutOnlySwap) "Confirm workout-only replacement"
                    else "Confirm program replacement",
                if(workoutOnlySwap)
                    "${kotlinExerciseLabel(replacing.exerciseId)} → ${kotlinExerciseLabel(exerciseId)}. Only this active workout changes. The replacement starts with fresh sets and a new slot identity; earlier recorded work and the approved program stay intact. After recording new work, this slot cannot be swapped again."
                else "${kotlinExerciseLabel(replacing.exerciseId)} → ${kotlinExerciseLabel(exerciseId)}. Only this day changes. The replacement gets a new history-safe slot identity. All new targets are exactly the sets entered here; the edit is undoable, and a fresh program audit is required.")
            OutlinedButton(onClick={swapConfirmation=false},
                modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
                Text("Keep editing")
            }
        }
        Button(
            onClick = {
                if(replacing!=null && !swapConfirmation)
                    swapConfirmation=true
                else {
                    val sets=parsedSets(forms,option)
                    if(sets!=null)onSave(exerciseId,sets,note)
                }
            },
            enabled=canSubmit,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)) {
            Text(if(replacing!=null) {
                if(swapConfirmation)if(workoutOnlySwap)
                    "Confirm workout swap" else "Confirm this program swap"
                else "Review replacement"
            } else saveLabel)
        }
        onRestore?.let { restore ->
            OutlinedButton(onClick=restore,enabled=!busy,modifier=Modifier.fillMaxWidth()) {
                Text("Compare restoring this week's block sets")
            }
        }
        TextButton(onClick = onDismiss,enabled = !busy) { Text("Cancel") }
    }
}
