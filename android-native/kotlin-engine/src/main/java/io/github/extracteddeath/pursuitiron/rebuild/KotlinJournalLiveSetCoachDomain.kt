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
    UNVERIFIED_OR_MIXED_SET_SCHEME, ASSISTANCE_DISABLED
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

/**
 * ONE straight-set coaching policy. Both native journal types supply only
 * their immutable issued set facts and effective recorded observations.
 * Neither adapter may decide eligibility, RIR, load steps or fatigue alone.
 */
object KotlinJournalLiveSetCoachDomain {
    private data class StraightSetEvidence(
        val setKey:String,
        val ordinal:Int,
        val reps:RepBand,
        val issuedLoad:DraftSetLoad,
        val issuedRir:Int?,
        val outcome:KotlinJournalSetOutcome?,
        val performed:KotlinPerformedSet?
    )

    private fun unavailable(reason:LiveSetCoachUnavailable):LiveSetCoachResult =
        LiveSetCoachResult.Unavailable(reason)

    /** The only shared policy entry after lossless journal-specific extraction. */
    private fun reviewStraightSets(
        workoutId:String,
        revision:Long,
        nextSetKey:String,
        working:List<StraightSetEvidence>,
        straightSchemeAllowed:Boolean,
        currentGymStock:VerifiedLoadStock?,
        readiness:CoachReadiness,
        movementRestricted:Boolean,
        maximumReviewStep:BigDecimal,
        assistance:CoachAssistancePolicy
    ):LiveSetCoachResult {
        val ordered=working.sortedBy{it.ordinal}
        val index=ordered.indexOfFirst{it.setKey==nextSetKey}
        if(index<1 || ordered.size !in 2..32)
            return unavailable(LiveSetCoachUnavailable.WARMUP_OR_FIRST_WORKING_SET)
        val next=ordered[index]
        val nextLoad=next.issuedLoad as? DraftSetLoad.Verified
            ?:return unavailable(LiveSetCoachUnavailable.UNVERIFIED_OR_MIXED_SET_SCHEME)
        // Previously these checks lived in separate generated/custom
        // branches. A malformed custom ordinal could pass when a generated
        // ordinal would fail. Both must reject exactly the same scheme.
        // Without an owner-verified authored scheme, DIFFERENT working
        // weights are not ordinary straight sets. A tempting drop must not
        // be retrospectively called a "planned backoff" just because the
        // next ordinal is lighter. Preserve every issued set unchanged,
        // but do not offer ordinary live fatigue advice for mixed loading.
        if(!straightSchemeAllowed ||
            ordered.map{it.ordinal}.distinct().size!=ordered.size ||
            ordered.map{it.setKey}.distinct().size!=ordered.size ||
            ordered.any{
                it.reps!=next.reps ||
                    (it.issuedLoad as? DraftSetLoad.Verified)?.let{load->
                        load.unit==nextLoad.unit &&
                            load.basis==nextLoad.basis &&
                            load.value.compareTo(nextLoad.value)==0
                    }!=true
            })
            return unavailable(LiveSetCoachUnavailable.UNVERIFIED_OR_MIXED_SET_SCHEME)

        // Include later logged sets. Their presence must prevent a stale
        // out-of-order recommendation, for BOTH workout formats.
        val observed=ordered.mapIndexedNotNull{ordinal,target->
            val result=target.outcome ?:return@mapIndexedNotNull null
            val outcome=when(result){
                KotlinJournalSetOutcome.COMPLETED->SetOutcome.COMPLETED
                KotlinJournalSetOutcome.SKIPPED->SetOutcome.SKIPPED
                KotlinJournalSetOutcome.FAILED_ATTEMPT->SetOutcome.FAILED_ATTEMPT
            }
            WorkingSetFact(ordinal,outcome,target.performed?.load,
                target.performed?.reps,target.performed?.actualRir,
                (target.issuedLoad as DraftSetLoad.Verified).value,
                target.issuedRir)
        }
        val coaching=InWorkoutCoachDomain.review(InWorkoutReviewRequest(
            next.reps,ordered.size,observed,index,nextLoad.value,
            nextLoad.unit,currentGymStock,maximumReviewStep,
            nextTargetUserEdited=false,
            readiness=assistance.coachingReadiness(readiness),
            movementRestricted=movementRestricted))
        return LiveSetCoachResult.Reviewed(workoutId,revision,nextSetKey,
            ordered[index-1].setKey,coaching)
    }

    /** Generated-program journal: translate durable set receipts, no policy. */
    fun review(
        journal:KotlinWorkoutJournalState,
        nextSetKey:String,
        currentGymStock:VerifiedLoadStock?,
        readiness:CoachReadiness,
        movementRestricted:Boolean,
        maximumReviewStep:BigDecimal,
        assistance:CoachAssistancePolicy=CoachAssistancePolicy.STANDARD
    ):LiveSetCoachResult {
        if(journal.finishedByEventId!=null)
            return unavailable(LiveSetCoachUnavailable.FINISHED)
        if(!assistance.offersFatigueAdvice && !movementRestricted)
            return unavailable(LiveSetCoachUnavailable.ASSISTANCE_DISABLED)
        val next=journal.snapshot.targets.singleOrNull{it.setKey==nextSetKey}
        if(next==null || nextSetKey in journal.effectiveSets)
            return unavailable(LiveSetCoachUnavailable.UNKNOWN_OR_COMPLETED_SET)
        if(next.type!=DraftSetType.WORKING)
            return unavailable(LiveSetCoachUnavailable.WARMUP_OR_FIRST_WORKING_SET)
        val working=journal.snapshot.targets.filter{
            it.exerciseId==next.exerciseId && it.type==DraftSetType.WORKING
        }.map{target->
            val recorded=journal.effectiveSets[target.setKey]?.operation
                as? KotlinJournalOperation.RecordSet
            StraightSetEvidence(target.setKey,target.ordinal,
                target.reps,target.load,target.targetRir,
                recorded?.outcome,recorded?.performed)
        }
        return reviewStraightSets(journal.snapshot.workoutId,journal.revision,
            nextSetKey,working,true,currentGymStock,readiness,
            movementRestricted,maximumReviewStep,assistance)
    }

    /** Custom-cycle journal: only translate actual same-slot MAIN visits. */
    fun review(
        journal:CustomWorkoutJournalDraft,
        nextSetKey:String,
        currentGymStock:VerifiedLoadStock?,
        readiness:CoachReadiness,
        movementRestricted:Boolean,
        maximumReviewStep:BigDecimal,
        assistance:CoachAssistancePolicy=CoachAssistancePolicy.STANDARD
    ):LiveSetCoachResult {
        if(journal.finishedByEventId!=null)
            return unavailable(LiveSetCoachUnavailable.FINISHED)
        if(!assistance.offersFatigueAdvice && !movementRestricted)
            return unavailable(LiveSetCoachUnavailable.ASSISTANCE_DISABLED)
        val slot=journal.snapshot.slots.singleOrNull{slot->
            slot.sets.any{it.setKey==nextSetKey}
        } ?:return unavailable(LiveSetCoachUnavailable.UNKNOWN_OR_COMPLETED_SET)
        val next=slot.sets.singleOrNull{it.setKey==nextSetKey}
            ?:return unavailable(LiveSetCoachUnavailable.UNKNOWN_OR_COMPLETED_SET)
        if(CustomWorkoutVisitKey(nextSetKey,CustomWorkoutVisitPart.MAIN)
            in journal.effectiveVisits)
            return unavailable(LiveSetCoachUnavailable.UNKNOWN_OR_COMPLETED_SET)
        if(next.prescription.type!=DraftSetType.WORKING)
            return unavailable(LiveSetCoachUnavailable.WARMUP_OR_FIRST_WORKING_SET)
        val working=slot.sets.filter{
            it.prescription.type==DraftSetType.WORKING
        }.map{target->
            val recorded=journal.effectiveVisits[CustomWorkoutVisitKey(
                target.setKey,CustomWorkoutVisitPart.MAIN)]?.operation
                as? CustomWorkoutJournalOperation.Record
            StraightSetEvidence(target.setKey,target.ordinalInSlot,
                target.prescription.reps,target.prescription.load,
                target.prescription.targetRir,recorded?.outcome,
                recorded?.performed)
        }
        // A superset or amplifier is not ordinary straight-set evidence.
        // This structural fact is provided to the SAME eligibility policy.
        val straight=slot.supersetGroup==null &&
            slot.sets.none{it.prescription.amplifier!=null}
        return reviewStraightSets(journal.workoutId,journal.revision,
            nextSetKey,working,straight,currentGymStock,readiness,
            movementRestricted,maximumReviewStep,assistance)
    }
}
