package io.github.extracteddeath.pursuitiron

import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Read-only, workout-scoped exercise facts from the CURRENT saved native
 * prescription. This is not an unverified movement tutorial, movement
 * equivalence claim, or source of new targets. History remains separate.
 */
internal data class NativeWorkoutInfoSet(
    val title:String,
    val load:String,
    val reps:String,
    val targetRir:String,
    val workSeconds:Int,
    val restSeconds:Int,
    val technique:String?=null
)

private fun infoLoad(load:DraftSetLoad):String=when(load) {
    DraftSetLoad.ExplicitBodyweight -> "Bodyweight · no external load"
    is DraftSetLoad.Verified ->
        "${load.value.stripTrailingZeros().toPlainString()} ${load.unit.name.lowercase()}"+
            " · ${load.basis.name.lowercase().replace('_',' ')}"
}

private fun infoReps(reps:RepBand):String =
    if(reps.minimum==reps.maximum)"${reps.minimum} reps"
    else "${reps.minimum}–${reps.maximum} reps"

private fun infoTitle(type:DraftSetType,ordinal:Int):String =
    "${if(type==DraftSetType.WARMUP)"Warm-up" else "Working set"} ${ordinal+1}"

/** Issued generated workout; no actuals and no inferred session advice. */
internal fun composerWorkoutInfoSets(
    targets:List<KotlinWorkoutTarget>
):List<NativeWorkoutInfoSet> = targets.map{target->
    NativeWorkoutInfoSet(
        title=infoTitle(target.type,target.ordinal),
        load=infoLoad(target.load),
        reps=infoReps(target.reps),
        targetRir=target.targetRir?.toString() ?: "Not set",
        workSeconds=target.activeSeconds,
        restSeconds=target.restSeconds
    )
}

/** Issued custom workout; keep each authored amplifier with its owner set. */
internal fun customWorkoutInfoSets(
    targets:List<CustomWorkoutSetSnapshot>
):List<NativeWorkoutInfoSet> = targets.map { snapshot ->
    val target=snapshot.prescription
    val technique=target.amplifier?.let{amp->
        "${amp.kind.name.lowercase().replace('_',' ')} · "+
            "${amp.followUpReps} follow-up reps · "+
            "${amp.followUpRestSeconds}s follow-up rest"+
            (amp.followUpLoad?.let{" · ${infoLoad(it)}"} ?: "")
    }
    NativeWorkoutInfoSet(
        title=infoTitle(target.type,snapshot.ordinalInSlot),
        load=infoLoad(target.load),
        reps=infoReps(target.reps),
        targetRir=target.targetRir?.toString() ?: "Not set",
        workSeconds=target.activeSeconds,
        restSeconds=target.restSeconds,
        technique=technique
    )
}

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
    onHistory:()->Unit,
    onDismiss:()->Unit
) {
    NativeEditSheet(busy=false,onDismiss=onDismiss) {
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
        if(sets.isEmpty()) {
            Text("No saved set details are available.",
                style=MaterialTheme.typography.bodyMedium)
        } else {
            sets.forEach { set ->
                HorizontalDivider()
                Text(set.title,style=MaterialTheme.typography.titleSmall)
                Text("${set.load} · ${set.reps} · target RIR ${set.targetRir}",
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
