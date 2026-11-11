package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
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
 * Shared, bounded numeric actual-entry controls. The target/actuals and
 * physical equipment policies are supplied by the calling workout owner.
 * Deliberately no unverified load stepper: available plate, machine and
 * dumbbell increments differ.
 *
 * Compact phones show TWO accessible 56dp rows (Reps then RIR); wide layouts
 * show matching equal-width controls. Both text entry and +/- modify only
 * unsaved draft fields. A long press performs a single, bounded multi-step
 * edit, not repeated asynchronous writes or any automatic logged set.
 */
@Composable
internal fun NativeSetValueFields(unit:String,load:String,reps:String,rir:String,
    enabled:Boolean,onLoad:(String)->Unit,onReps:(String)->Unit,onRir:(String)->Unit,
    allowFailedAttempt:Boolean=false,showLoad:Boolean=true,
    loadError:Boolean=false,repsError:Boolean=false,rirError:Boolean=false,
    showFieldHelp:Boolean=true
) {
    if(showLoad)OutlinedTextField(
        value=load,
        onValueChange={input->
            if(input.length<=10 && input.all{it.isDigit()||it=='.'})onLoad(input)
        },
        label={Text("Load ($unit)")},
        modifier=Modifier.fillMaxWidth(),
        singleLine=true,enabled=enabled,isError=loadError,
        keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Decimal)
    )
    // Advance from the CURRENT draft once, applying the same existing domain
    // bounds at each intermediate step. No guessed coach recommendations and
    // no calls to the journal from a long-press.
    fun stepReps(direction:Int,count:Int) {
        NativeSetEntryPolicy.stepRepsBy(
            reps,direction,count,allowFailedAttempt)?.let(onReps)
    }
    fun stepRir(direction:Int,count:Int) {
        NativeSetEntryPolicy.stepRirBy(rir,direction,count)?.let(onRir)
    }
    val repsControl:@Composable ()->Unit={
        NativeSetCounterField(
            label="Reps",value=reps,enabled=enabled,
            onValueChange={input->
                if(input.length<=3 && input.all(Char::isDigit))onReps(input)
            },
            decrement={stepReps(-1,1)},increment={stepReps(1,1)},
            longDecrement={stepReps(-1,3)},longIncrement={stepReps(1,3)},
            longStepCount=3,
            canDecrement=NativeSetEntryPolicy.stepReps(
                reps,-1,allowFailedAttempt)!=null,
            canIncrement=NativeSetEntryPolicy.stepReps(
                reps,1,allowFailedAttempt)!=null,
            isError=repsError
        )
    }
    val rirControl:@Composable ()->Unit={
        NativeSetCounterField(
            label="RIR",value=rir,enabled=enabled,
            onValueChange={input->
                if(input.length<=2 && input.all(Char::isDigit))onRir(input)
            },
            decrement={stepRir(-1,1)},increment={stepRir(1,1)},
            longDecrement={stepRir(-1,2)},longIncrement={stepRir(1,2)},
            longStepCount=2,
            canDecrement=NativeSetEntryPolicy.stepRir(rir,-1)!=null,
            canIncrement=NativeSetEntryPolicy.stepRir(rir,1)!=null,
            isError=rirError
        )
    }
    val largeText=LocalDensity.current.fontScale>=1.35f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        // At compact widths, two full-width inline counters are MUCH shorter
        // and more readable than two 110dp-tall half-width columns.
        // On tablet/foldables each counter occupies an equal column.
        if(!largeText && maxWidth>=420.dp) {
            Row(Modifier.fillMaxWidth(),
                horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                Box(Modifier.weight(1f)){repsControl()}
                Box(Modifier.weight(1f)){rirControl()}
            }
        } else {
            Column(Modifier.fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                repsControl()
                rirControl()
            }
        }
    }
    if(showFieldHelp || !showLoad)Text(
        when {
            !showLoad -> "Bodyweight movement · no external load. RIR can remain blank."
            load=="0" -> "Zero external load does not necessarily mean bodyweight. RIR is optional."
            else -> "RIR is optional; leave it blank if unreported."
        },
        style=MaterialTheme.typography.bodySmall,
        color=MaterialTheme.colorScheme.onSurfaceVariant
    )
}

/** One equal-width, bounded Material 3 control with 48dp tap targets.
 * The long-press action is a discrete draft-only edit and has an accessible
 * action label. Disabled controls cannot receive either gesture. */
@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun NativeSetStepButton(
    label:String,increase:Boolean,enabled:Boolean,longStepCount:Int,
    onTap:()->Unit,onHold:()->Unit,
    modifier:Modifier=Modifier
) {
    val action=if(increase)"Increase" else "Decrease"
    Box(
        modifier=modifier.sizeIn(minWidth=48.dp,minHeight=48.dp)
            .clip(CircleShape)
            .background(if(enabled)MaterialTheme.colorScheme.secondaryContainer
                else MaterialTheme.colorScheme.surfaceContainerLow)
            .combinedClickable(
                enabled=enabled,
                role=Role.Button,
                onClickLabel="$action $label by one",
                onLongClickLabel="$action $label by up to $longStepCount",
                onClick=onTap,
                onLongClick=onHold
            ),
        contentAlignment=Alignment.Center
    ) {
        Icon(
            if(increase)Icons.Outlined.Add else Icons.Outlined.Remove,
            contentDescription=null,
            tint=if(enabled)MaterialTheme.colorScheme.onSecondaryContainer
                else MaterialTheme.colorScheme.onSurface.copy(alpha=0.38f)
        )
    }
}

/** Inline counter when there is room; stacked field and buttons otherwise.
 * At large Android font scales, labels and values never compete with icons. */
@Composable
private fun NativeSetCounterField(
    label:String,value:String,enabled:Boolean,onValueChange:(String)->Unit,
    decrement:()->Unit,increment:()->Unit,
    longDecrement:()->Unit,longIncrement:()->Unit,longStepCount:Int,
    canDecrement:Boolean,canIncrement:Boolean,isError:Boolean=false
) {
    val field:@Composable (Modifier)->Unit={modifier->
        OutlinedTextField(
            value=value,onValueChange=onValueChange,
            label={Text(label)},modifier=modifier,
            singleLine=true,enabled=enabled,isError=isError,
            textStyle=MaterialTheme.typography.titleMedium.copy(
                textAlign=TextAlign.Center),
            keyboardOptions=KeyboardOptions(keyboardType=KeyboardType.Number)
        )
    }
    val minus:@Composable (Modifier)->Unit={modifier->
        NativeSetStepButton(label,false,enabled&&canDecrement,
            longStepCount,decrement,longDecrement,modifier)
    }
    val plus:@Composable (Modifier)->Unit={modifier->
        NativeSetStepButton(label,true,enabled&&canIncrement,
            longStepCount,increment,longIncrement,modifier)
    }
    val largeText=LocalDensity.current.fontScale>=1.35f
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        if(!largeText&&maxWidth>=200.dp) {
            Row(Modifier.fillMaxWidth(),
                verticalAlignment=Alignment.CenterVertically,
                horizontalArrangement=Arrangement.spacedBy(4.dp)) {
                minus(Modifier)
                field(Modifier.weight(1f).widthIn(min=0.dp))
                plus(Modifier)
            }
        } else {
            Column(Modifier.fillMaxWidth(),
                verticalArrangement=Arrangement.spacedBy(PursuitSpace.xxs)) {
                field(Modifier.fillMaxWidth())
                Row(Modifier.fillMaxWidth(),
                    horizontalArrangement=Arrangement.spacedBy(PursuitSpace.xs)) {
                    minus(Modifier.weight(1f))
                    plus(Modifier.weight(1f))
                }
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
