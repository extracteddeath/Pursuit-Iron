package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Pure presentation: open from Focus or All Sets, review factual prescription,
 * then open the owner-verified full exercise History without losing any
 * unsubmitted workout fields. No journal or program writes occur here.
 */
@Composable
internal fun NativeKotlinWorkoutExerciseInfoSheet(
    exerciseId:String,
    sourceDescription:String,
    sets:List<NativeWorkoutInfoSet>,
    note:String?=null,
    warmupsOnly:Boolean=false,
    outcomes:Map<String,KotlinJournalSetOutcome> = emptyMap(),
    onSelectWarmup:((String)->Unit)?=null,
    onRepeatWarmup:(()->Unit)?=null,
    onRemoveWarmup:(()->Unit)?=null,
    busy:Boolean=false,
    machineLabel:String?=null,
    onChooseMachine:(()->Unit)?=null,
    onSwapExercise:(()->Unit)?=null,
    /** A separate confirmation and journal revision gate owns the skip. */
    onSkipRemainingExercise:(()->Unit)?=null,
    /** Undo is offered only for a still-unlogged replacement. */
    onUndoWorkoutSwap:(()->Unit)?=null,
    onAdjustRemaining:(()->Unit)?=null,
    onUndoAdjustment:(()->Unit)?=null,
    unavailableReason:String?=null,
    onHistory:()->Unit,
    onDismiss:()->Unit
) {
    NativeEditSheet(busy=busy,onDismiss=onDismiss) {
        val visible=if(warmupsOnly)sets.filter{it.type==DraftSetType.WARMUP} else sets
        if(warmupsOnly)Text("Warm-up targets",style=MaterialTheme.typography.labelLarge,
            color=MaterialTheme.colorScheme.primary)
        Text(kotlinExerciseLabel(exerciseId),
            style=MaterialTheme.typography.headlineSmall)
        Text(sourceDescription,style=MaterialTheme.typography.labelMedium,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Text("These are the saved workout's planned targets, not recorded results. "+
            "Use History for actual lifts and corrections.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        if(!note.isNullOrBlank()) {
            Text("Coach / program note",style=MaterialTheme.typography.titleSmall)
            Text(note,style=MaterialTheme.typography.bodyMedium)
        }
        // The source-issued rows, not a stale coach note, own the real set count.
        // Warm-ups and amplifier follow-ups are not counted as working sets.
        if(!warmupsOnly) {
            Text("Planned working sets · "+sets.count{it.type==DraftSetType.WORKING},
                style=MaterialTheme.typography.labelLarge,
                color=MaterialTheme.colorScheme.onSurfaceVariant)
            if(onChooseMachine!=null || onSwapExercise!=null ||
                !unavailableReason.isNullOrBlank()) {
                HorizontalDivider()
                Text("Equipment and exercise",style=MaterialTheme.typography.titleSmall)
                Text("A different physical machine keeps its own weights, Last and PRs. "+
                    "A replacement exercise changes only this workout.",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                onChooseMachine?.let { open ->
                    OutlinedButton(onClick=open,enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                        Text("Physical machine · "+(machineLabel ?: "Choose or add machine"))
                    }
                }
                onSwapExercise?.let { swap ->
                    OutlinedButton(onClick=swap,enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                        Text("Swap to another exercise")
                    }
                }
                unavailableReason?.takeIf{it.isNotBlank()}?.let {reason->
                    Text(reason,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if(onSkipRemainingExercise!=null || onUndoWorkoutSwap!=null ||
                onAdjustRemaining!=null || onUndoAdjustment!=null) {
                HorizontalDivider()
                Text("Workout actions",style=MaterialTheme.typography.titleSmall)
                onAdjustRemaining?.let{adjust->
                    OutlinedButton(onClick=adjust,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Text("Adjust remaining sets…")
                    }
                }
                onUndoAdjustment?.let{undo->
                    OutlinedButton(onClick=undo,enabled=!busy,modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)){
                        Text("Undo session adjustment")
                    }
                }
                onUndoWorkoutSwap?.let{undo->
                    OutlinedButton(onClick=undo,enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                        Text("Undo workout-only exercise swap")
                    }
                }
                onSkipRemainingExercise?.let{skip->
                    OutlinedButton(onClick=skip,enabled=!busy,
                        modifier=Modifier.fillMaxWidth().heightIn(min=52.dp)) {
                        Text("Skip remaining exercise sets…")
                    }
                    Text("Review every remaining set before recording skips. "+
                        "Completed sets and your future training plan stay unchanged.",
                        style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if(warmupsOnly) {
            onRepeatWarmup?.let{repeat->
                OutlinedButton(onClick=repeat,enabled=!busy,modifier=Modifier.fillMaxWidth()){
                    Text("Repeat approved warm-up…")
                }
            }
            onRemoveWarmup?.let{remove->
                OutlinedButton(onClick=remove,enabled=!busy,modifier=Modifier.fillMaxWidth()){
                    Text("Remove last unlogged warm-up…")
                }
            }
            if(onRepeatWarmup!=null || onRemoveWarmup!=null)
                Text("Only this workout changes. Recorded sets and your saved program stay unchanged.",
                    style=MaterialTheme.typography.bodySmall)
        }
        if(warmupsOnly && visible.isNotEmpty()) {
            val next=visible.firstOrNull{it.setKey !in outcomes}
            Text("${visible.count{it.setKey in outcomes}} of ${visible.size} warm-ups recorded",
                style=MaterialTheme.typography.bodyMedium)
            if(next!=null && onSelectWarmup!=null) {
                PursuitPrimaryAction("Go to next unlogged warm-up") {
                    onSelectWarmup(next.setKey)
                }
            } else if(next==null)Text("All warm-ups have a saved outcome.",
                style=MaterialTheme.typography.bodySmall)
        }
        if(visible.isEmpty()) {
            Text(if(warmupsOnly)"This exercise has no prescribed warm-ups."
                else "No saved set details are available.",
                style=MaterialTheme.typography.bodyMedium)
        } else {
            visible.forEach { set ->
                HorizontalDivider()
                Text(set.title,style=MaterialTheme.typography.titleSmall)
                Text(when(outcomes[set.setKey]) {
                    KotlinJournalSetOutcome.COMPLETED -> if(set.type==DraftSetType.WARMUP)
                        "Warm-up recorded" else "Main set recorded"
                    KotlinJournalSetOutcome.SKIPPED -> "Skipped"
                    KotlinJournalSetOutcome.FAILED_ATTEMPT -> "Failed attempt recorded"
                    null -> "Not recorded"
                },style=MaterialTheme.typography.labelMedium,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                // Warm-ups and legacy sets without an authored RIR
                // must not display the literal "target RIR Not set" as
                // if that were a coaching prescription.
                val rirLabel=if(set.targetRir=="Not set")"" else
                    " · target RIR ${set.targetRir}"
                Text("${set.load} · ${set.reps}$rirLabel",
                    style=MaterialTheme.typography.bodyMedium)
                Text("${set.workSeconds}s active · ${set.restSeconds}s planned rest",
                    style=MaterialTheme.typography.bodySmall,
                    color=MaterialTheme.colorScheme.onSurfaceVariant)
                set.technique?.let{
                    Text(it,style=MaterialTheme.typography.bodySmall,
                        color=MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        OutlinedButton(
            onClick=onHistory,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
        ) { Text("Open full exercise history") }
        TextButton(
            onClick=onDismiss,
            modifier=Modifier.fillMaxWidth().heightIn(min=48.dp)
        ) { Text("Close details") }
    }
}
