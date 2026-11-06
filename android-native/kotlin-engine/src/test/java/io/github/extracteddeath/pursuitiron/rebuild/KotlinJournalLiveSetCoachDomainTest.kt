package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal

class KotlinJournalLiveSetCoachDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private val source=KotlinTrainingSourceRevision(
        "athlete-1",2,"program-1",1,"gym-1",1,"catalog-1","coach-1")
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.SMITH_TOTAL,listOf(n("200"),n("205"),
            n("210"),n("215")))
    private fun target(
        ordinal:Int,load:String="215",reps:RepBand=RepBand(5,8),
        exerciseId:String="bench"
    )=KotlinWorkoutTarget(exerciseId,ComposerRole.PRIMARY,
        ComposerSessionPurpose.STRENGTH_SKILL,
        "$exerciseId:s$ordinal",DraftSetType.WORKING,ordinal,
        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,n(load)),
        reps,3,60,120)
    private fun started(sets:List<KotlinWorkoutTarget> =listOf(
        target(0),target(1),target(2,"205"),target(3,"205")
    )):KotlinWorkoutJournalState {
        val snapshot=KotlinStagedWorkout(source,"grant-1","workout-1",
            0,0,ComposerSessionPurpose.STRENGTH_SKILL,48,sets)
        return KotlinWorkoutJournalDomain.begin(snapshot,
            KotlinStoredWorkoutIdentity("receipt-1","workout-1",source,1))
    }
    private fun record(j:KotlinWorkoutJournalState,set:String,
        reps:Int=2,rir:Int?=0,load:String="215"
    ):KotlinWorkoutJournalState {
        val op=KotlinJournalOperation.RecordSet("event-${j.revision}",
            j.revision,set,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(n(load),LoadUnit.LB,reps,rir))
        return (KotlinWorkoutJournalDomain.reduce(j,op)
            as KotlinJournalDecision.Proposed).state
    }
    private fun look(
        j:KotlinWorkoutJournalState,
        set:String,
        gym:VerifiedLoadStock?=stock
    )=KotlinJournalLiveSetCoachDomain.review(
        j,set,gym,CoachReadiness.NORMAL,false,n("5"))

    @Test fun completedTopSetRecognizesAlreadyPlannedLighterBackoff() {
        val j=record(started(),"bench:s0")
        val first=look(j,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
            first.coaching.direction)
        assertEquals(n("210"),first.coaching.weightForHumanReview)
        val planned=look(j,"bench:s2") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.PRIOR_SET_UNRESOLVED,
            planned.coaching.direction)
        val j2=record(j,"bench:s1",2,0,"215")
        val coached=look(j2,"bench:s2") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.FOLLOW_PLANNED_BACKOFF,
            coached.coaching.direction)
        assertNull(coached.coaching.weightForHumanReview)
        assertEquals(n("205"),coached.coaching.issuedTargetUnchanged)
        assertFalse(coached.canIssueWorkout)
        assertFalse(coached.canModifyWorkout)
        assertEquals(n("205"),
            (j2.snapshot.targets[2].load as DraftSetLoad.Verified).value)
    }

    @Test fun onlyDurableEffectiveCorrectionMayInfluenceNextCoaching() {
        val first=record(started(),"bench:s0")
        val prior=look(first,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
            prior.coaching.direction)
        val corrected=(KotlinWorkoutJournalDomain.reduce(first,
            KotlinJournalOperation.RecordSet("correction-1",first.revision,
                "bench:s0",KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(n("215"),LoadUnit.LB,6,3),
                correctsEventId="event-0")
        ) as KotlinJournalDecision.Proposed).state
        val next=look(corrected,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.BUILD_REPS_AS_PLANNED,
            next.coaching.direction)
        assertEquals(corrected.revision,next.journalRevision)
        assertEquals(InWorkoutDirection.REVIEW_SMALLER_NEXT_LOAD,
            (look(first,"bench:s1") as LiveSetCoachResult.Reviewed)
                .coaching.direction)
    }

    @Test fun neverIssueSuggestionsForUnsavedOrMixedRepsOrFinishedWork() {
        val empty=started()
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.WARMUP_OR_FIRST_WORKING_SET),
            look(empty,"bench:s0"))
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.UNKNOWN_OR_COMPLETED_SET),
            look(empty,"missing"))
        val hetero=started(listOf(target(0),target(1,reps=RepBand(8,12))))
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.UNVERIFIED_OR_MIXED_SET_SCHEME),
            look(hetero,"bench:s1"))
        var complete=empty
        for(set in complete.snapshot.targets) {
            val load=(set.load as DraftSetLoad.Verified).value
            complete=record(complete,set.setKey,5,3,load.toPlainString())
        }
        val finished=(KotlinWorkoutJournalDomain.reduce(complete,
            KotlinJournalOperation.Finish("finished",complete.revision))
            as KotlinJournalDecision.Proposed).state
        assertEquals(LiveSetCoachResult.Unavailable(
            LiveSetCoachUnavailable.FINISHED),
            look(finished,"bench:s2"))
    }

    @Test fun laterRecordedSetBlocksLoadAdviceForEarlierStillOpenSet() {
        val initial=record(started(),"bench:s0",2,0,"215")
        // Journal events are saved by set key, not necessarily in target order.
        // This could happen through skipping ahead or a history correction.
        val outOfOrder=record(initial,"bench:s2",5,2,"205")
        val review=look(outOfOrder,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.NEXT_SET_ALREADY_LOGGED,
            review.coaching.direction)
        assertNull(review.coaching.weightForHumanReview)
        assertEquals(n("215"),review.coaching.issuedTargetUnchanged)
        assertFalse(review.canModifyWorkout)
        assertFalse(review.canIssueWorkout)
        // It is still okay to coach an actually next, unrecorded set.
        val last=look(outOfOrder,"bench:s3") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.PRIOR_SET_UNRESOLVED,
            last.coaching.direction)
    }

    @Test fun laterSkippedSetAlsoBlocksEarlierLoadSuggestion() {
        val initial=record(started(),"bench:s0")
        val skipped=KotlinJournalOperation.RecordSet(
            "skipped-later",initial.revision,"bench:s2",
            KotlinJournalSetOutcome.SKIPPED,null)
        val saved=(KotlinWorkoutJournalDomain.reduce(initial,skipped)
            as KotlinJournalDecision.Proposed).state
        val reviewed=look(saved,"bench:s1") as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.NEXT_SET_ALREADY_LOGGED,
            reviewed.coaching.direction)
        assertNull(reviewed.coaching.weightForHumanReview)
    }

    @Test fun gymMustBeVerifiedBeforeSuggestingAnyWeightChange() {
        val j=record(started(),"bench:s0")
        val suggestion=look(j,"bench:s1",null) as LiveSetCoachResult.Reviewed
        assertEquals(InWorkoutDirection.VERIFY_PLANNED_EQUIPMENT,
            suggestion.coaching.direction)
        assertNull(suggestion.coaching.weightForHumanReview)
    }
}
