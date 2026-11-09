package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

private data class ComposerSwapDraftRow(
    val weight:String="",val min:String="",val max:String="",
    val targetRir:String=""
)

/**
 * Workout-only Composer replacement. Unlike a blind "copy sets" action,
 * every replacement set is re-authored and visually confirmed, including
 * warmups, exact gym weight, reps and planned RIR. No actuals transfer.
 */
@Composable
internal fun NativeKotlinComposerSwapSheet(
    fromExerciseId:String,
    sourceTargets:List<KotlinWorkoutTarget>,
    choices:List<KotlinComposerSwapChoice>,
    busy:Boolean,
    onConfirm:(String,List<KotlinComposerSwapSetIntent>)->Unit,
    onDismiss:()->Unit
) {
    var query by remember(fromExerciseId){mutableStateOf("")}
    var matchingPattern by remember(fromExerciseId){mutableStateOf(false)}
    var selectedId by remember(fromExerciseId){mutableStateOf<String?>(null)}
    val selected=choices.firstOrNull{it.option.exerciseId==selectedId}
    val rows=remember(fromExerciseId,selectedId) {
        sourceTargets.map {ComposerSwapDraftRow()}.toMutableStateList()
    }
    val permitted=choices.filter {
        (!matchingPattern || it.samePattern) &&
            (query.isBlank() ||
                it.option.exerciseId.replace('_',' ').replace('-',' ')
                    .contains(query.trim(),ignoreCase=true) ||
                it.option.pattern.name.replace('_',' ')
                    .contains(query.trim(),ignoreCase=true))
    }
    val approved=selected?.let{choice->
        runCatching {
            sourceTargets.mapIndexed {i,target->
                val row=rows[i]
                val min=row.min.trim().toInt()
                val max=row.max.trim().toInt()
                require(min in 1..100 && max in min..100)
                val rir=row.targetRir.trim().takeIf{it.isNotEmpty()}
                    ?.toInt()?.also{require(it in 0..10)}
                val load=when(val gear=choice.option.equipment){
                    ComposerEquipment.Bodyweight ->
                        DraftSetLoad.ExplicitBodyweight
                    is ComposerEquipment.Verified -> {
                        val basis=checkNotNull(gear.inventory).basis
                        DraftSetLoad.Verified(
                            gear.unit,basis,BigDecimal(row.weight.trim()))
                    }
                }
                KotlinComposerSwapSetIntent(
                    target.type,target.ordinal,load,RepBand(min,max),rir)
            }
        }.getOrNull()
    }
    AlertDialog(
        onDismissRequest={if(!busy)onDismiss()},
        title={Text(if(selected==null)"Swap this exercise"
            else "Confirm workout-only targets")},
        text={
            Column(
                Modifier.fillMaxWidth().heightIn(max=510.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement=Arrangement.spacedBy(12.dp)
            ) {
                Text("Replacing ${kotlinExerciseLabel(fromExerciseId)}. "+
                    "Your saved program, other sets and historical lifts "+
                    "will not change.",
                    style=MaterialTheme.typography.bodyMedium)
                if(selected==null){
                    OutlinedTextField(query,{query=it},
                        label={Text("Search exercise or training pattern")},
                        singleLine=true,modifier=Modifier.fillMaxWidth())
                    FilterChip(selected=matchingPattern,
                        onClick={matchingPattern=!matchingPattern},
                        label={Text("Same movement pattern")})
                    if(permitted.isEmpty())
                        Text("No verified same-role exercises match these filters. "+
                            "Try all available replacements or review your gym.",
                            style=MaterialTheme.typography.bodySmall)
                    permitted.forEach {candidate->
                        OutlinedButton(
                            onClick={selectedId=candidate.option.exerciseId},
                            enabled=!busy,modifier=Modifier.fillMaxWidth()
                        ){
                            Column(Modifier.fillMaxWidth()){
                                Text(kotlinExerciseLabel(candidate.option.exerciseId))
                                Text(candidate.explanation,
                                    style=MaterialTheme.typography.labelSmall)
                                Text(candidate.option.pattern.name.replace('_',' ')
                                    .lowercase(),
                                    style=MaterialTheme.typography.labelSmall)
                            }
                        }
                    }
                } else {
                    Text(kotlinExerciseLabel(selected.option.exerciseId),
                        style=MaterialTheme.typography.titleMedium)
                    Text(selected.explanation,
                        style=MaterialTheme.typography.bodySmall)
                    Text("Enter NEW targets for every set. No previous "+
                        "exercise load, reps or completed set is copied. "+
                        "Weights must match your currently reviewed gym. "+
                        "Leave target RIR blank if you do not want one.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                    sourceTargets.forEachIndexed{i,target->
                        val row=rows[i]
                        HorizontalDivider()
                        Text("${if(target.type==DraftSetType.WARMUP)
                            "Warm-up" else "Working"} · set ${target.ordinal+1}",
                            style=MaterialTheme.typography.labelLarge)
                        val gear=selected.option.equipment
                        if(gear is ComposerEquipment.Verified) {
                            OutlinedTextField(row.weight,{
                                rows[i]=row.copy(weight=it)
                            },label={Text("New weight (${gear.unit.name.lowercase()})")},
                                keyboardOptions=KeyboardOptions(
                                    keyboardType=KeyboardType.Decimal),
                                singleLine=true,
                                modifier=Modifier.fillMaxWidth())
                        } else Text("Verified bodyweight · no external load",
                            style=MaterialTheme.typography.labelMedium)
                        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                            OutlinedTextField(row.min,{
                                rows[i]=row.copy(min=it)
                            },label={Text("Min reps")},
                                keyboardOptions=KeyboardOptions(
                                    keyboardType=KeyboardType.Number),
                                singleLine=true,modifier=Modifier.weight(1f))
                            OutlinedTextField(row.max,{
                                rows[i]=row.copy(max=it)
                            },label={Text("Max reps")},
                                keyboardOptions=KeyboardOptions(
                                    keyboardType=KeyboardType.Number),
                                singleLine=true,modifier=Modifier.weight(1f))
                        }
                        OutlinedTextField(row.targetRir,{
                            rows[i]=row.copy(targetRir=it)
                        },label={Text("Target RIR (optional)")},
                            keyboardOptions=KeyboardOptions(
                                keyboardType=KeyboardType.Number),
                            singleLine=true,modifier=Modifier.fillMaxWidth())
                    }
                    if(approved==null)
                        Text("Enter a valid rep range and verified weight "+
                            "for each set to review the replacement.",
                            style=MaterialTheme.typography.bodySmall,
                            color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        },
        confirmButton={
            if(selected!=null)Button(
                onClick={
                    if(approved!=null)
                        onConfirm(selected.option.exerciseId,approved)
                },
                enabled=!busy && approved!=null
            ){Text("Confirm workout-only swap")}
        },
        dismissButton={
            TextButton(onClick={
                if(selected!=null)selectedId=null else onDismiss()
            },enabled=!busy) {
                Text(if(selected!=null)"Back to exercises" else "Cancel")
            }
        }
    )
}
