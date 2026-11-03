package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.sizeIn
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.selection.toggleable
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SheetValue
import androidx.compose.material3.rememberBottomSheetState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.domain.NativeSetEntryPolicy

/**
 * Shared native presentation components. Domain/state ownership stays outside UI.
 * No React styling, CSS, or training formulas should be added here.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
internal fun NativeEditSheet(busy: Boolean,onDismiss: ()->Unit,
    canDismiss: ()->Boolean = {true},content: @Composable ColumnScope.()->Unit) {
    val currentBusy by rememberUpdatedState(busy)
    val currentCanDismiss by rememberUpdatedState(canDismiss)
    val sheet=rememberBottomSheetState(initialValue=SheetValue.Hidden,
        enabledValues=setOf(SheetValue.Hidden,SheetValue.Expanded),
        confirmValueChange={it!=SheetValue.Hidden || !currentBusy && currentCanDismiss()})
    ModalBottomSheet(sheetState=sheet,onDismissRequest={if(!currentBusy && currentCanDismiss())onDismiss()}) {
        Column(Modifier.fillMaxWidth().imePadding().verticalScroll(rememberScrollState()).padding(20.dp),
            verticalArrangement=Arrangement.spacedBy(12.dp),content=content)
    }
}

@Composable
internal fun NativeSetEditSheet(busy: Boolean,onDismiss: ()->Unit,content: @Composable ColumnScope.()->Unit) {
    NativeEditSheet(busy,onDismiss,content=content)
}

/**
 * All set-entry surfaces share these bounded, keyboard-editable controls.
 * Intentionally no load +/-: bar, machine and dumbbell increments differ by
 * exercise and must come from the canonical engine before offering steps.
 */
@Composable
internal fun NativeSetValueFields(unit: String,load: String,reps: String,rir: String,enabled: Boolean,
    onLoad: (String)->Unit,onReps: (String)->Unit,onRir: (String)->Unit,
    allowFailedAttempt: Boolean=false) {
    OutlinedTextField(value=load,onValueChange={input->
        if(input.length<=10 && input.all {it.isDigit() || it=='.'})onLoad(input)
    },label={Text("Load ($unit)")},modifier=Modifier.fillMaxWidth(),singleLine=true,enabled=enabled,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal))
    // Reps and RIR share a compact row on normal phones. Their +/- buttons
    // sit below each input so touch targets remain 48dp without stealing
    // space from the editable number. Very narrow devices stack safely.
    // The same component is reused in active sets, warm-ups and corrections;
    // presentation never computes or changes a training target.
    val repsControl: @Composable () -> Unit = {
        NativeSetCounterField("Reps",reps,enabled,
            onValueChange={input->
                if(input.length<=3 && input.all(Char::isDigit))onReps(input)
            },
            decrement={NativeSetEntryPolicy.stepReps(reps,-1,allowFailedAttempt)?.let(onReps)},
            increment={NativeSetEntryPolicy.stepReps(reps,1,allowFailedAttempt)?.let(onReps)},
            canDecrement=NativeSetEntryPolicy.stepReps(reps,-1,allowFailedAttempt)!=null,
            canIncrement=NativeSetEntryPolicy.stepReps(reps,1,allowFailedAttempt)!=null)
    }
    val rirControl: @Composable () -> Unit = {
        NativeSetCounterField("RIR",rir,enabled,
            onValueChange={input->
                if(input.length<=2 && input.all(Char::isDigit))onRir(input)
            },
            decrement={NativeSetEntryPolicy.stepRir(rir,-1)?.let(onRir)},
            increment={NativeSetEntryPolicy.stepRir(rir,1)?.let(onRir)},
            canDecrement=NativeSetEntryPolicy.stepRir(rir,-1)!=null,
            canIncrement=NativeSetEntryPolicy.stepRir(rir,1)!=null)
    }
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // Each column owns its field plus a 48dp-high two-button row.
        // No squeezed 3-column rows or offscreen steppers on typical phones.
        if(maxWidth>=340.dp) {
            Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                Box(Modifier.weight(1f)) { repsControl() }
                Box(Modifier.weight(1f)) { rirControl() }
            }
        } else {
            Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
                repsControl()
                rirControl()
            }
        }
    }
    Text(if(load=="0")"0 load is not automatically bodyweight · check this exercise’s loading. RIR is optional."
        else "RIR is optional · leave blank if unknown.",
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Matching accessible touch targets, while keeping a full keyboard field. */
@Composable
private fun NativeSetCounterField(
    label:String,value:String,enabled:Boolean,onValueChange:(String)->Unit,
    decrement:()->Unit,increment:()->Unit,canDecrement:Boolean,canIncrement:Boolean
) {
    Column(Modifier.fillMaxWidth(),verticalArrangement=Arrangement.spacedBy(6.dp)) {
        OutlinedTextField(value=value,onValueChange=onValueChange,
            label={Text(label)},modifier=Modifier.fillMaxWidth(),singleLine=true,
            enabled=enabled,keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number))
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
            FilledTonalIconButton(onClick=decrement,enabled=enabled && canDecrement,
                modifier=Modifier.weight(1f).sizeIn(minWidth=48.dp,minHeight=48.dp)) {
                Icon(Icons.Outlined.Remove,contentDescription="Decrease $label")
            }
            FilledTonalIconButton(onClick=increment,enabled=enabled && canIncrement,
                modifier=Modifier.weight(1f).sizeIn(minWidth=48.dp,minHeight=48.dp)) {
                Icon(Icons.Outlined.Add,contentDescription="Increase $label")
            }
        }
    }
}

@Composable
internal fun PreviewLabel(label: String, emphasis: Boolean = false) {
    Surface(
        shape = MaterialTheme.shapes.small,
        color = if (emphasis) MaterialTheme.colorScheme.primaryContainer
            else MaterialTheme.colorScheme.secondaryContainer,
        contentColor = if (emphasis) MaterialTheme.colorScheme.onPrimaryContainer
            else MaterialTheme.colorScheme.onSecondaryContainer
    ) {
        Text(label, modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
            style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.SemiBold)
    }
}

@Composable
internal fun PageHeading(eyebrow: String, title: String, subtitle: String) {
    Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
        Text(eyebrow.uppercase(),style=MaterialTheme.typography.labelMedium,
            color=LocalPursuitPalette.current.accentInk,fontWeight=FontWeight.Bold)
        Text(title,style=MaterialTheme.typography.headlineLarge,
            color=MaterialTheme.colorScheme.onSurface)
        if(subtitle.isNotBlank()) Text(subtitle,style=MaterialTheme.typography.bodyMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
internal fun PursuitCard(
    modifier: Modifier = Modifier,
    highlighted: Boolean = false,
    content: @Composable ColumnScope.() -> Unit
) {
    Card(
        modifier = modifier.fillMaxWidth(),
        shape=MaterialTheme.shapes.extraLarge,
        // A subtle expressive outline, not a large distracting violet fill.
        border=if(highlighted) BorderStroke(1.dp,LocalPursuitPalette.current.accentInk.copy(alpha=0.45f))
            else null,
        colors=CardDefaults.cardColors(
            containerColor=if(highlighted) MaterialTheme.colorScheme.surfaceContainerHigh
                else MaterialTheme.colorScheme.surfaceContainerLow,
            contentColor=MaterialTheme.colorScheme.onSurface
        )
    ) {
        Column(modifier=Modifier.padding(PursuitSpace.lg),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm),content=content)
    }
}

/**
 * One compact native fact strip for active and saved workout summaries.
 * Values come from exact caller-owned journal/history snapshots; this
 * component computes no training facts and performs no writes.
 */
@Composable
internal fun PursuitMetricStrip(metrics:List<Pair<String,String>>) {
    require(metrics.size==3)
    val largeText=LocalDensity.current.fontScale>=1.35f
    Surface(modifier=Modifier.fillMaxWidth(),
        shape=MaterialTheme.shapes.large,
        color=MaterialTheme.colorScheme.surfaceContainer) {
        BoxWithConstraints(Modifier.fillMaxWidth()) {
            // A forced 3-column, one-line strip clips real workout facts in
            // Android's large-text mode. Present the SAME values as rows
            // whenever accessibility scaling or narrow widths require it.
            if(largeText || maxWidth<320.dp) {
                Column(Modifier.fillMaxWidth().padding(PursuitSpace.md),
                    verticalArrangement=Arrangement.spacedBy(PursuitSpace.sm)) {
                    metrics.forEachIndexed { index,(label,value) ->
                        Row(Modifier.fillMaxWidth(),
                            horizontalArrangement=Arrangement.spacedBy(PursuitSpace.sm),
                            verticalAlignment=Alignment.CenterVertically) {
                            Text(label,modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.labelLarge,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(value,modifier=Modifier.weight(1f),
                                style=MaterialTheme.typography.titleMedium,
                                textAlign=TextAlign.End,
                                color=if(index==0) LocalPursuitPalette.current.accentInk
                                    else MaterialTheme.colorScheme.onSurface)
                        }
                    }
                }
            } else {
                Row(
                    modifier=Modifier.fillMaxWidth().padding(
                        vertical=PursuitSpace.md,horizontal=PursuitSpace.xs),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)
                ) {
                    metrics.forEachIndexed { index,(label,value) ->
                        Column(modifier=Modifier.weight(1f),
                            horizontalAlignment=Alignment.CenterHorizontally,
                            verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                            Text(value,style=MaterialTheme.typography.titleLarge,
                                maxLines=1,overflow=TextOverflow.Ellipsis,
                                color=if(index==0) LocalPursuitPalette.current.accentInk
                                    else MaterialTheme.colorScheme.onSurface)
                            Text(label,style=MaterialTheme.typography.labelMedium,
                                maxLines=1,overflow=TextOverflow.Ellipsis,
                                color=MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }
        }
    }
}

/** Active state stays journal-derived and visually consistent with History. */
@Composable
internal fun PursuitSessionMetrics(logged:Int,skipped:Int,remaining:Int) {
    require(logged>=0 && skipped>=0 && remaining>=0)
    PursuitMetricStrip(listOf(
        "Logged" to logged.toString(),
        "Skipped" to skipped.toString(),
        "Left" to remaining.toString()
    ))
}

/** One reusable 52dp high primary action across the real native journeys. */
@Composable
internal fun PursuitPrimaryAction(label:String,enabled:Boolean=true,onClick:()->Unit) {
    Button(onClick=onClick,enabled=enabled,
        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp),
        shape=MaterialTheme.shapes.large) {
        Text(label,style=MaterialTheme.typography.titleSmall)
    }
}

/**
 * Shared expressive form section: visual grouping without new data owners.
 * Its child composables retain all actual engine choice and validation logic.
 */
@Composable
internal fun PursuitFormSection(
    title:String,description:String,content:@Composable ColumnScope.()->Unit
) {
    Surface(modifier=Modifier.fillMaxWidth(),
        shape=MaterialTheme.shapes.extraLarge,
        color=MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(modifier=Modifier.padding(PursuitSpace.lg),
            verticalArrangement=Arrangement.spacedBy(PursuitSpace.md)) {
            Column(verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                Text(title,style=MaterialTheme.typography.titleLarge,
                    color=MaterialTheme.colorScheme.onSurface)
                if(description.isNotBlank()) Text(description,
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
            }
            content()
        }
    }
}

@Composable
internal fun SectionLabel(title: String, detail: String? = null) {
    Row(modifier=Modifier.fillMaxWidth(),
        verticalAlignment=Alignment.CenterVertically,
        horizontalArrangement=Arrangement.SpaceBetween) {
        Text(title,modifier=Modifier.weight(1f),
            style=MaterialTheme.typography.titleLarge,color=MaterialTheme.colorScheme.onSurface)
        if (detail != null) {
            Text(detail, modifier = Modifier.padding(start = 10.dp).widthIn(max = 148.dp),
                maxLines = 1, overflow = TextOverflow.Ellipsis,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

/**
 * Full-row accessible M3 setting control. The Switch is display-only: the row
 * owns one toggle action and one semantics role, avoiding tiny hit targets and
 * duplicated nested click actions. Preview callbacks cannot write settings.
 */
@Composable
internal fun PreviewSettingSwitch(
    title: String, description: String, checked: Boolean,
    enabled: Boolean = true,
    onCheckedChange: (Boolean) -> Unit
) {
    Surface(
        modifier = Modifier.fillMaxWidth(),
        color = if (checked) MaterialTheme.colorScheme.secondaryContainer
            else MaterialTheme.colorScheme.surfaceContainerLow,
        shape = MaterialTheme.shapes.large
    ) {
        Row(
            modifier = Modifier.fillMaxWidth()
                .toggleable(value = checked, role = Role.Switch,
                    enabled = enabled,
                    onValueChange = onCheckedChange)
                .padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Column(Modifier.weight(1f),
                verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(title, style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold)
                Text(description, style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Switch(checked = checked, onCheckedChange = null, enabled = enabled)
        }
    }
}

@Composable
internal fun PreviewChoice(label: String, selected: Boolean, enabled: Boolean = true, onClick: () -> Unit) {
    FilterChip(selected = selected, enabled = enabled, onClick = onClick, label = { Text(label) })
}

@Composable
internal fun PreviewStepper(
    title: String,
    value: String,
    subtract: () -> Unit,
    add: () -> Unit,
    canSubtract: Boolean = true,
    canAdd: Boolean = true
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                title,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(value, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
        }
        FilledTonalIconButton(
            onClick = subtract,
            enabled = canSubtract,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Icon(Icons.Outlined.Remove, contentDescription = "Decrease $title")
        }
        FilledTonalIconButton(
            onClick = add,
            enabled = canAdd,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) {
            Icon(Icons.Outlined.Add, contentDescription = "Increase $title")
        }
    }
}

/**
 * One consistent M3 training-day presentation for policy-approved current
 * Plans and clearly labeled offline-backup inventories. Presentation only:
 * this component takes labels and counts, never raw records or workout loads.
 */
@Composable
internal fun ReadOnlyTrainingDayRow(
    dayIndex: Int,
    title: String,
    exerciseCount: Int,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                (dayIndex + 1).toString().padStart(2, '0'),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(3.dp)
        ) {
            Text(
                title,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                exerciseCount.toString() + " exercises",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/** Shared native row for a canonical exercise ID when exercise names and
 * prescriptions have NOT yet been verified by the read-only engine. */
@Composable
internal fun ReadOnlyMovementIdentityRow(
    ordinal: Int,
    canonicalExerciseId: String,
    displayName: String? = null
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = MaterialTheme.shapes.small,
            color = MaterialTheme.colorScheme.secondaryContainer,
            contentColor = MaterialTheme.colorScheme.onSecondaryContainer
        ) {
            Text(
                (ordinal + 1).toString().padStart(2, '0'),
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold
            )
        }
        Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(
                displayName ?: canonicalExerciseId,
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.SemiBold,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                "Saved movement · $canonicalExerciseId",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}


/**
 * Shared native numeric set editor. Every interaction is a UI-only preview
 * unless a future certified workout state owner explicitly supplies callbacks.
 * Use the same bounds for the stepper and type-to-replace dialog.
 */
@Composable
internal fun PreviewNumericStepper(
    title: String,
    value: Int,
    min: Int,
    max: Int,
    step: Int,
    onValueChange: (Int) -> Unit,
    suffix: String = ""
) {
    require(min <= max && step > 0)
    var editing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    val validInput = draft.toIntOrNull()?.let {
        it in min..max && (it - min) % step == 0
    } == true
    if (editing) {
        AlertDialog(
            onDismissRequest = { editing = false },
            title = { Text("Edit $title") },
            text = {
                OutlinedTextField(
                    value = draft,
                    onValueChange = { input ->
                        if (input.length <= 6 && input.all(Char::isDigit)) draft = input
                    },
                    singleLine = true,
                    isError = draft.isNotEmpty() && !validInput,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    supportingText = { Text("From $min to $max in steps of $step") }
                )
            },
            confirmButton = {
                TextButton(
                    enabled = validInput,
                    onClick = {
                        draft.toIntOrNull()?.let(onValueChange)
                        editing = false
                    }
                ) { Text("Apply") }
            },
            dismissButton = {
                TextButton(onClick = { editing = false }) { Text("Cancel") }
            }
        )
    }
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        TextButton(
            modifier = Modifier.weight(1f),
            onClick = {
                draft = value.toString()
                editing = true
            }
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(2.dp)
            ) {
                Text(title, style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text(
                    value.toString() + if (suffix.isEmpty()) "" else " $suffix",
                    style = MaterialTheme.typography.titleLarge,
                    fontWeight = FontWeight.SemiBold
                )
                Text("Tap to edit", style = MaterialTheme.typography.labelSmall)
            }
        }
        FilledTonalIconButton(
            onClick = { if (value - step >= min) onValueChange(value - step) },
            enabled = value - step >= min,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) { Icon(Icons.Outlined.Remove, contentDescription = "Decrease $title") }
        FilledTonalIconButton(
            onClick = { if (value + step <= max) onValueChange(value + step) },
            enabled = value + step <= max,
            modifier = Modifier.sizeIn(minWidth = 48.dp, minHeight = 48.dp)
        ) { Icon(Icons.Outlined.Add, contentDescription = "Increase $title") }
    }
}


/**
 * Shared native Material 3 set heading for illustrative edits and canonical
 * read-only reviews. All engine decisions and writable state remain external.
 */
@Composable
internal fun TrainingSetHeader(
    label: String,
    exerciseName: String,
    exerciseNumber: Int,
    exerciseCount: Int,
    setNumber: Int,
    setCount: Int,
    overallSetNumber: Int? = null,
    totalWorkoutSets: Int? = null
) {
    val denominator = (totalWorkoutSets ?: setCount).coerceAtLeast(1)
    val numerator = (overallSetNumber ?: setNumber).coerceIn(0, denominator)
    PreviewLabel(label, emphasis = true)
    Text(exerciseName, style = MaterialTheme.typography.headlineSmall,
        fontWeight = FontWeight.Bold)
    Text(
        "Exercise " + exerciseNumber + " of " + exerciseCount +
            " · Set " + setNumber + " of " + setCount,
        style = MaterialTheme.typography.titleSmall
    )
    LinearProgressIndicator(
        progress = { numerator.toFloat() / denominator },
        modifier = Modifier.fillMaxWidth()
    )
    if (overallSetNumber != null && totalWorkoutSets != null) {
        Text("Workout set " + overallSetNumber + " of " + totalWorkoutSets,
            style = MaterialTheme.typography.labelMedium)
    }
}
