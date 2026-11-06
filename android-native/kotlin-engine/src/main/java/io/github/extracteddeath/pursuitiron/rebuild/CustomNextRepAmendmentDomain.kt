package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Exact, non-issued rep-only amendment to ONE slot of a future custom workout.
 *
 * Never mutates the approved cycle, adds a set, changes the exercise/load/RIR
 * or issues a grant. An owner must independently rederive this from complete
 * saved history and obtain athlete acceptance before saving the amendment.
 */
data class CustomNextRepAmendment(
    val slotId:String,
    val originalSetKeys:List<String>,
    val nextMinimumReps:List<Int>,
    val amended:CustomWorkoutDaySnapshot
) {
    val mayIssueWorkout:Boolean get()=false
    val mayChangeCycle:Boolean get()=false
}

enum class CustomNextRepAmendmentRefusal {
    NOT_AN_ELIGIBLE_REP_REVIEW, INVALID_TARGET_SLOT, SUGGESTIONS_CHANGED,
    NO_ACTUAL_REP_INCREASE
}
sealed interface CustomNextRepAmendmentResult {
    data class Proposed(val amendment:CustomNextRepAmendment):
        CustomNextRepAmendmentResult
    data class Rejected(val reason:CustomNextRepAmendmentRefusal):
        CustomNextRepAmendmentResult
}

object CustomNextRepAmendmentDomain {
    fun propose(
        original:CustomWorkoutDaySnapshot,slotId:String,
        reviewed:CustomNextWorkoutCoachingReview
    ):CustomNextRepAmendmentResult {
        fun fail(reason:CustomNextRepAmendmentRefusal)=
            CustomNextRepAmendmentResult.Rejected(reason)
        if(reviewed.status!=CustomNextWorkoutCoachingStatus.BUILD_REPS ||
            reviewed.loadReviewCandidate!=null ||
            reviewed.latestWorkoutId==null)
            return fail(CustomNextRepAmendmentRefusal.NOT_AN_ELIGIBLE_REP_REVIEW)
        val slot=original.slots.singleOrNull{it.slotId==slotId}
            ?:return fail(CustomNextRepAmendmentRefusal.INVALID_TARGET_SLOT)
        val working=slot.sets.filter{it.prescription.type==DraftSetType.WORKING}
        if(working.isEmpty() || working.size!=reviewed.nextSetSuggestions.size ||
            reviewed.nextSetSuggestions.map{it.setKey}!=working.map{it.setKey} ||
            working.any{it.prescription.amplifier!=null})
            return fail(CustomNextRepAmendmentRefusal.SUGGESTIONS_CHANGED)

        val updated=mutableListOf<Int>()
        var changed=false
        for((set,proposal) in working.zip(reviewed.nextSetSuggestions)){
            val range=set.prescription.reps
            if(proposal.observedReps !in range.minimum..range.maximum ||
                proposal.actualRir<2 || proposal.actualRir>10 ||
                proposal.proposedNextReps !in range.minimum..range.maximum ||
                proposal.changed !=
                    (proposal.proposedNextReps>proposal.observedReps) ||
                (proposal.changed &&
                    proposal.proposedNextReps!=proposal.observedReps+1) ||
                (!proposal.changed &&
                    proposal.proposedNextReps!=proposal.observedReps))
                return fail(CustomNextRepAmendmentRefusal.SUGGESTIONS_CHANGED)
            // Unchanged sets retain their EXACT authored rep band. Only
            // earned, per-set increases advance their next minimum.
            val floor=if(proposal.changed)
                proposal.proposedNextReps else range.minimum
            updated+=floor
            changed=changed || floor>range.minimum
        }
        if(!changed)
            return fail(CustomNextRepAmendmentRefusal.NO_ACTUAL_REP_INCREASE)
        var index=0
        val amendedSlot=slot.copy(sets=Collections.unmodifiableList(
            slot.sets.map{set->
                if(set.prescription.type!=DraftSetType.WORKING)set
                else {
                    val floor=updated[index++]
                    set.copy(prescription=set.prescription.copy(
                        reps=RepBand(floor,set.prescription.reps.maximum)))
                }
            }))
        val amended=original.copy(slots=Collections.unmodifiableList(
            original.slots.map{if(it.slotId==slotId)amendedSlot else it}))
        return CustomNextRepAmendmentResult.Proposed(CustomNextRepAmendment(
            slotId,Collections.unmodifiableList(working.map{it.setKey}),
            Collections.unmodifiableList(updated.toList()),amended))
    }
}
