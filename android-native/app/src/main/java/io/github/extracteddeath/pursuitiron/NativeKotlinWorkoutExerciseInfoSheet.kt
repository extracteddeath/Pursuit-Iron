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
    val setKey:String,
    val type:DraftSetType,
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
            " · ${kotlinLoadBasisLabel(load.basis)}"
}

private fun infoReps(reps:RepBand):String =
    if(reps.minimum==reps.maximum)"${reps.minimum} reps"
    else "${reps.minimum}–${reps.maximum} reps"

/** Receives a visible one-based ordinal; issued custom slots already use it. */
private fun infoTitle(type:DraftSetType,oneBasedOrdinal:Int):String =
    "${if(type==DraftSetType.WARMUP)"Warm-up" else "Working set"} $oneBasedOrdinal"

/** Issued generated workout; no actuals and no inferred session advice. */
internal fun composerWorkoutInfoSets(
    targets:List<KotlinWorkoutTarget>
):List<NativeWorkoutInfoSet> = targets.map{target->
    NativeWorkoutInfoSet(
        setKey=target.setKey,
        type=target.type,
        title=infoTitle(target.type,target.ordinal+1),
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
            (amp.followUpActiveSeconds?.let{" · ${it}s follow-up work"}
                ?: " · follow-up work time not set")+
            (amp.followUpLoad?.let{" · ${infoLoad(it)}"} ?: "")
    }
    NativeWorkoutInfoSet(
        setKey=snapshot.setKey,
        type=target.type,
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
    warmupsOnly:Boolean=false,
    outcomes:Map<String,KotlinJournalSetOutcome> = emptyMap(),
    onSelectWarmup:((String)->Unit)?=null,
    onHistory:()->Unit,
    onDismiss:()->Unit
) {
    NativeEditSheet(busy=false,onDismiss=onDismiss) {
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
