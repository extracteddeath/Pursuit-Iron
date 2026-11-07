package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

enum class CustomBlockStartingLoadStatus {
    BASELINE_UNAVAILABLE,
    CHANGED_TRAINING_CONTEXT,
    RESTRICTED_OR_LOW_READINESS,
    EFFORT_UNAVAILABLE,
    NEAR_FAILURE_REQUIRES_REVIEW,
    REPEAT_AUTHORED_LOADS
}

data class CustomBlockStartingSet(
    val setKey:String,
    val ordinal:Int,
    val load:BigDecimal,
    val unit:LoadUnit,
    val basis:LoadBasis,
    val reps:RepBand,
    val goalRir:Int?,
    val actualRir:Int?,
    val effortSource:ProgressionEffortSource
)

/** A conservative starting point, never an earned progression or approval. */
data class CustomBlockStartingLoadReview(
    val status:CustomBlockStartingLoadStatus,
    val baseline:CustomBlockBaselineReview,
    val startingSets:List<CustomBlockStartingSet>,
    val assumedGoalRirSetCount:Int,
    val explanation:String
) {
    val mayApplyProgression:Boolean get()=false
    val mayChangePrescription:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
    val mayModifyHistory:Boolean get()=false
}

/**
 * Rechecks the full evidence request; a caller-supplied positive baseline
 * cannot authorize a starting load. Only an unchanged training context and
 * exact authored recipe may repeat each set's already approved weight.
 * Phase conversions, estimated maxes and load increases need distinct rules.
 */
object CustomBlockStartingLoadDomain {
    fun review(
        request:CustomBlockBaselineRequest,
        readiness:CoachReadiness,
        movementRestricted:Boolean
    ):CustomBlockStartingLoadReview {
        val baseline=CustomBlockBaselineContinuityDomain.review(request)
        fun result(status:CustomBlockStartingLoadStatus,why:String,
                   sets:List<CustomBlockStartingSet> = emptyList(),assumed:Int=0)=
            CustomBlockStartingLoadReview(status,baseline,
                Collections.unmodifiableList(sets.toList()),assumed,why+
                    ProgressionEffortDomain.assumptionNote(assumed))
        if(baseline.status==CustomBlockBaselineStatus.VERIFIED_PHASE_REFERENCE_ONLY)
            return result(CustomBlockStartingLoadStatus.CHANGED_TRAINING_CONTEXT,
                "The phase or session purpose changed. Review new starting weights independently; previous work is historical reference only.")
        if(baseline.status!=CustomBlockBaselineStatus.VERIFIED_CONTEXT_ONLY)
            return result(CustomBlockStartingLoadStatus.BASELINE_UNAVAILABLE,
                baseline.explanation)
        if(movementRestricted || readiness==CoachReadiness.LOW)
            return result(CustomBlockStartingLoadStatus.RESTRICTED_OR_LOW_READINESS,
                "Review recovery or the movement restriction before choosing starting weights.")
        val next=request.next.slots.single{it.slotId==request.slotId}
        val working=next.sets.filter{it.prescription.type==DraftSetType.WORKING}
        val sets=working.map {set ->
            val prior=baseline.priorWorkingSets.single{it.ordinal==set.ordinalInSlot}
            val load=set.prescription.load as DraftSetLoad.Verified
            val effort=ProgressionEffortDomain.forCompletedSet(
                prior.actualRir,set.prescription.targetRir)
            CustomBlockStartingSet(set.setKey,set.ordinalInSlot,load.value,
                load.unit,load.basis,set.prescription.reps,
                set.prescription.targetRir,prior.actualRir,effort.source)
        }
        val efforts=sets.map{ProgressionEffortDomain.forCompletedSet(
            it.actualRir,it.goalRir)}
        val assumed=efforts.count{it.assumed}
        if(efforts.any{it.rir==null})
            return result(CustomBlockStartingLoadStatus.EFFORT_UNAVAILABLE,
                "The previous sets have neither reported effort nor an original goal RIR.",
                assumed=assumed)
        if(efforts.any{checkNotNull(it.rir)<=1})
            return result(CustomBlockStartingLoadStatus.NEAR_FAILURE_REQUIRES_REVIEW,
                "The previous effort leaves too little reserve for an automatic repeat recommendation. Review the starting weights.",
                assumed=assumed)
        return result(CustomBlockStartingLoadStatus.REPEAT_AUTHORED_LOADS,
            "Start with each set's existing authored weight and establish a new-block baseline. Previous-block results earn no extra weight or reps. The normal workout review is still required.",
            sets,assumed)
    }
}
