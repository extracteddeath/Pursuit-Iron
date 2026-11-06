package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Fast, pure live coaching projection from a NATIVE DURABLE journal.
 * The host is responsible for authenticating the journal and CURRENT gym.
 * The next set's issued target is never changed, and user-entered actuals
 * which have not been saved in the native journal are never considered.
 */
enum class LiveSetCoachUnavailable {
    FINISHED, UNKNOWN_OR_COMPLETED_SET, WARMUP_OR_FIRST_WORKING_SET,
    UNVERIFIED_OR_MIXED_SET_SCHEME
}
sealed interface LiveSetCoachResult {
    data class Unavailable(val reason:LiveSetCoachUnavailable):LiveSetCoachResult
    data class Reviewed(
        val workoutId:String,
        val journalRevision:Long,
        val setKey:String,
        val previousSetKey:String,
        val coaching:InWorkoutReviewResult
    ):LiveSetCoachResult {
        val canModifyWorkout:Boolean get()=false
        val canIssueWorkout:Boolean get()=false
    }
}

object KotlinJournalLiveSetCoachDomain {
    fun review(
        journal:KotlinWorkoutJournalState,
        nextSetKey:String,
        currentGymStock:VerifiedLoadStock?,
        readiness:CoachReadiness,
        movementRestricted:Boolean,
        maximumReviewStep:BigDecimal
    ):LiveSetCoachResult {
        fun unavailable(reason:LiveSetCoachUnavailable)=
            LiveSetCoachResult.Unavailable(reason)
        if(journal.finishedByEventId!=null)
            return unavailable(LiveSetCoachUnavailable.FINISHED)
        if(nextSetKey !in journal.snapshot.targets.map{it.setKey} ||
            nextSetKey in journal.effectiveSets)
            return unavailable(LiveSetCoachUnavailable.UNKNOWN_OR_COMPLETED_SET)
        val next=journal.snapshot.targets.singleOrNull{it.setKey==nextSetKey}
            ?:return unavailable(LiveSetCoachUnavailable.UNKNOWN_OR_COMPLETED_SET)
        if(next.type!=DraftSetType.WORKING)
            return unavailable(LiveSetCoachUnavailable.WARMUP_OR_FIRST_WORKING_SET)
        val working=journal.snapshot.targets.filter{
            it.exerciseId==next.exerciseId && it.type==DraftSetType.WORKING
        }.sortedBy{it.ordinal}
        val index=working.indexOfFirst{it.setKey==nextSetKey}
        if(index<1 || working.size !in 2..32)
            return unavailable(LiveSetCoachUnavailable.WARMUP_OR_FIRST_WORKING_SET)
        // Never treat heterogeneous backoff rep targets, bodyweight or
        // different equipment bases as interchangeable progression evidence.
        val nextLoad=next.load as? DraftSetLoad.Verified
            ?:return unavailable(LiveSetCoachUnavailable.UNVERIFIED_OR_MIXED_SET_SCHEME)
        if(working.map{it.ordinal}.distinct().size!=working.size ||
            working.any{
                it.reps!=next.reps ||
                    (it.load as? DraftSetLoad.Verified)?.let{load->
                        load.unit==nextLoad.unit && load.basis==nextLoad.basis
                    }!=true
            })
            return unavailable(LiveSetCoachUnavailable.UNVERIFIED_OR_MIXED_SET_SCHEME)
        val prior=working.take(index).mapIndexed{ordinal,target->
            val record=journal.effectiveSets[target.setKey]?.operation
                as? KotlinJournalOperation.RecordSet
            val outcome=when(record?.outcome) {
                KotlinJournalSetOutcome.COMPLETED ->SetOutcome.COMPLETED
                KotlinJournalSetOutcome.SKIPPED ->SetOutcome.SKIPPED
                KotlinJournalSetOutcome.FAILED_ATTEMPT ->SetOutcome.FAILED_ATTEMPT
                null ->null
            }
            if(outcome==null)null else {
                val actual=record.performed
                WorkingSetFact(ordinal,outcome,actual?.load,actual?.reps,
                    actual?.actualRir,
                    (target.load as DraftSetLoad.Verified).value,target.targetRir)
            }
        }.filterNotNull()
        val review=InWorkoutCoachDomain.review(InWorkoutReviewRequest(
            next.reps,working.size,prior,index,nextLoad.value,
            nextLoad.unit,currentGymStock,maximumReviewStep,
            nextTargetUserEdited=false,readiness=readiness,
            movementRestricted=movementRestricted))
        return LiveSetCoachResult.Reviewed(
            journal.snapshot.workoutId,journal.revision,nextSetKey,
            working[index-1].setKey,review)
    }
}
