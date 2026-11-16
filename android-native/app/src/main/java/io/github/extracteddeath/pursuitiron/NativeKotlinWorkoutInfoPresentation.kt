package io.github.extracteddeath.pursuitiron

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
    is DraftSetLoad.Verified -> kotlinIssuedLoadLabel(load)
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
    // Durable ordinals count every set in the slot. UI numbering restarts
    // for working sets after warm-ups, matching Focus and the actual logger.
    val position=targets.asSequence().filter {
        it.slotId==snapshot.slotId && it.prescription.type==target.type
    }.indexOfFirst{it.setKey==snapshot.setKey}+1
    NativeWorkoutInfoSet(
        setKey=snapshot.setKey,
        type=target.type,
        title=infoTitle(target.type,position),
        load=infoLoad(target.load),
        reps=infoReps(target.reps),
        targetRir=target.targetRir?.toString() ?: "Not set",
        workSeconds=target.activeSeconds,
        restSeconds=target.restSeconds,
        technique=technique
    )
}
