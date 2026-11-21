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

/** Plan-only amplifier description. The engine owns stage counts, labels
 * and physical target loads; this never confuses targets with actuals.
 * A malformed legacy technique is disclosed instead of invented. */
private fun infoTechnique(set:CustomAuthoredSet):String? {
    val amp=set.amplifier ?:return null
    val title=AdvancedAmplifierProtocolDomain.template(amp.kind).title
    if(!AdvancedAmplifierProtocolDomain.validStagedFollowUps(amp))
        return "$title · technique configuration needs review"
    val total=amp.followUpRounds
    val singular=AdvancedAmplifierProtocolDomain.stageNoun(amp.kind)
    val plural=AdvancedAmplifierProtocolDomain.stageNoun(amp.kind,total!=1)
    val action=when(amp.kind) {
        CustomAmplifierKind.LENGTHENED_PARTIALS ->
            "${amp.followUpReps} controlled partial reps on the final working set · same load and station"
        else -> "$total $plural · ${amp.followUpReps} reps per $singular"
    }
    val rest=if(amp.kind==CustomAmplifierKind.LENGTHENED_PARTIALS)
        "no added rest" else "${amp.followUpRestSeconds}s between $plural"
    val work=amp.followUpActiveSeconds?.let {
        "${it}s total follow-up work"
    } ?: "follow-up work time not set"
    val physical=when(amp.kind) {
        CustomAmplifierKind.DROP_SET -> {
            if(!AdvancedAmplifierProtocolDomain.validDropSequence(set.load,amp))
                "drop weights require review"
            else (1..amp.followUpRounds).map {stage ->
                val load=AdvancedAmplifierProtocolDomain.dropStageLoad(amp,stage)
                "Drop $stage: "+(load?.let(::infoLoad) ?: "not confirmed")
            }.joinToString(" · ")
        }
        else ->amp.followUpLoad?.let {
            "Authored follow-up: ${infoLoad(it)}"
        } ?: "same activation load and station"
    }
    return listOf(title,action,rest,work,physical).joinToString(" · ")
}

/** Issued custom workout; keep each authored amplifier with its owner set. */
internal fun customWorkoutInfoSets(
    targets:List<CustomWorkoutSetSnapshot>
):List<NativeWorkoutInfoSet> = targets.map { snapshot ->
    val target=snapshot.prescription
    val technique=infoTechnique(target)
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
