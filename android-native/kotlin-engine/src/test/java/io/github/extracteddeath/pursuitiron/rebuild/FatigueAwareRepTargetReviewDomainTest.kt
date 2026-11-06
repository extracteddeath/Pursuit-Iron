package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/**
 * Known legacy regressions: +3 rep jumps, blindly raising every set,
 * target RIR mistaken for actual effort, and making late fatigued sets
 * mimic the first set. These are hard refusals, not coaching slogans.
 */
class FatigueAwareRepTargetReviewDomainTest {
    private fun n(value:String)=BigDecimal(value)
    private val load=n("30")
    private val gym=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
        listOf(n("25"),load,n("35")))
    private fun session(
        reps:List<Int> = listOf(14,13,12),
        rir:List<Int?> = listOf(3,3,2),
        loads:List<String> = listOf("30","30","30"),
        issued:List<String?> = listOf("30","30","30"),
        outcome:List<SetOutcome> = List(3){SetOutcome.COMPLETED}
    ):ProgressionWorkoutEvidence {
        require(reps.size==3 && rir.size==3 && loads.size==3 &&
            issued.size==3 && outcome.size==3)
        return ProgressionWorkoutEvidence(
            "recorded-1","db-curl",LoadUnit.LB,RepBand(10,15),3,
            (0..2).map {i->
                WorkingSetFact(i,outcome[i],
                    if(outcome[i]==SetOutcome.SKIPPED)null else n(loads[i]),
                    when(outcome[i]) {
                        SetOutcome.SKIPPED -> null
                        SetOutcome.FAILED_ATTEMPT -> 0
                        SetOutcome.COMPLETED -> reps[i]
                    },
                    if(outcome[i]==SetOutcome.COMPLETED)rir[i] else null,
                    issued[i]?.let(::n),2)
            })
    }
    private fun review(
        session:ProgressionWorkoutEvidence=session(),
        stock:VerifiedLoadStock?=gym,
        basis:LoadBasis=LoadBasis.DUMBBELL_PER_HAND,
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false
    )=FatigueAwareRepTargetReviewDomain.assess(
        session,stock,basis,readiness,restricted)

    @Test fun oneFatigueAwareSetEarnsOnlyOneAdditionalRepWithoutWeightChanges() {
        val original=session()
        val result=review(original)
        assertEquals(FatigueRepReviewReason.ONE_SET_REP_INCREASE_FOR_REVIEW,
            result.reason)
        assertEquals(listOf(14,13,12),result.observedReps)
        assertEquals(listOf(14,14,12),result.candidateRepTargets)
        assertEquals(1,result.proposedOrdinal)
        assertEquals(load,result.observedLoad)
        assertFalse(result.canAutoApply)
        assertFalse(result.mayIssueWorkout)
        assertEquals(listOf(14,13,12),original.observations.map {it.actualReps})
        assertEquals(listOf(load,load,load),
            original.observations.map {it.actualLoad})
    }

    @Test fun noThreeRepJumpAndNoAutomaticIncreaseForEverySet() {
        val input=session(reps=listOf(10,10,10),rir=listOf(5,5,5))
        val result=review(input)
        assertEquals(listOf(11,10,10),result.candidateRepTargets)
        assertEquals(0,result.proposedOrdinal)
        assertEquals(1,
            result.candidateRepTargets!!.zip(result.observedReps!!)
                .sumOf {(after,before)->after-before})
        val nearTop=review(session(reps=listOf(15,14,13),
            rir=listOf(3,4,4)))
        assertEquals(listOf(15,14,14),nearTop.candidateRepTargets)
        assertEquals(2,nearTop.proposedOrdinal)
    }

    @Test fun missingActualRirCannotInheritTargetOrFakeRepHeadroom() {
        assertEquals(FatigueRepReviewReason.ACTUAL_EFFORT_NOT_REPORTED,
            review(session(rir=listOf(3,null,2))).reason)
        assertNull(review(session(rir=listOf(3,null,2))).candidateRepTargets)
        assertNull(review(session(rir=listOf(null,null,null))).candidateRepTargets)
    }

    @Test fun nearFailureOrLowReadinessAlwaysWinsOverFirstSetStrength() {
        assertEquals(FatigueRepReviewReason.NEAR_FAILURE_OR_RECOVERY_PRIORITY,
            review(session(rir=listOf(5,4,1))).reason)
        assertNull(review(session(rir=listOf(5,4,0))).candidateRepTargets)
        assertEquals(FatigueRepReviewReason.NEAR_FAILURE_OR_RECOVERY_PRIORITY,
            review(readiness=CoachReadiness.LOW).reason)
        assertEquals(FatigueRepReviewReason.NEAR_FAILURE_OR_RECOVERY_PRIORITY,
            review(restricted=true).reason)
        assertEquals(FatigueRepReviewReason.NO_DEMONSTRATED_EFFORT_HEADROOM,
            review(session(rir=listOf(2,2,2))).reason)
    }

    @Test fun belowFloorOrUnfinishedCannotBecomeProgression() {
        assertEquals(FatigueRepReviewReason.BELOW_TARGET_FLOOR,
            review(session(reps=listOf(14,9,12))).reason)
        val skipped=session(outcome=listOf(SetOutcome.COMPLETED,
            SetOutcome.SKIPPED,SetOutcome.COMPLETED))
        assertEquals(FatigueRepReviewReason.INCOMPLETE_OR_FAILED_SESSION,
            review(skipped).reason)
        assertNull(review(skipped).candidateRepTargets)
        val failed=session(outcome=listOf(SetOutcome.COMPLETED,
            SetOutcome.FAILED_ATTEMPT,SetOutcome.COMPLETED))
        assertEquals(FatigueRepReviewReason.INCOMPLETE_OR_FAILED_SESSION,
            review(failed).reason)
    }

    @Test fun noUnverifiedEquipmentOrChangedAthleteLoads() {
        assertEquals(FatigueRepReviewReason.CURRENT_LOAD_NOT_VERIFIED,
            review(stock=null).reason)
        assertEquals(FatigueRepReviewReason.CURRENT_LOAD_NOT_VERIFIED,
            review(basis=LoadBasis.CABLE_STACK).reason)
        assertEquals(FatigueRepReviewReason.CURRENT_LOAD_NOT_VERIFIED,
            review(stock=VerifiedLoadStock.Totals(LoadUnit.LB,
                LoadBasis.DUMBBELL_PER_HAND,listOf(n("25"),n("35")))).reason)
        assertEquals(FatigueRepReviewReason.UNSTABLE_OR_USER_EDITED_LOADS,
            review(session(issued=listOf("25","30","30"))).reason)
        assertEquals(FatigueRepReviewReason.UNSTABLE_OR_USER_EDITED_LOADS,
            review(session(loads=listOf("30","35","30"))).reason)
        assertEquals(FatigueRepReviewReason.ZERO_LOAD_NEEDS_SPECIFIC_POLICY,
            review(session(loads=listOf("0","0","0"),
                issued=listOf("0","0","0"))).reason)
    }

    @Test fun topOfAllSetsIsSeparateWeightProgressionNotMoreRepTargets() {
        val result=review(session(reps=listOf(15,15,15)))
        assertEquals(FatigueRepReviewReason.ALREADY_AT_REP_CEILING,result.reason)
        assertNull(result.candidateRepTargets)
    }

    @Test fun exerciseCoachExposesOnlyAnOptionalSingleSetReview() {
        val context=CoachExerciseContext(
            CoachReadiness.NORMAL,false,gym,n("5"))
        val w=session().copy(workoutId="recorded-2")
        val interpreted=CoachExerciseDecisionDomain.review(listOf(w),context)
        assertEquals(CoachDirection.BUILD_REPS_AT_CURRENT_LOAD,
            interpreted.direction)
        assertEquals(listOf(14,14,12),interpreted.nextRepTargetsForReview)
        assertNull(interpreted.nextLoadToReview)
        assertTrue(interpreted.explanation.contains("one additional rep"))
        assertEquals(listOf(14,13,12),w.observations.map {it.actualReps})
        val unknown=CoachExerciseDecisionDomain.review(listOf(
            session(rir=listOf(3,null,2))),context)
        assertEquals(CoachDirection.BUILD_REPS_AT_CURRENT_LOAD,unknown.direction)
        assertNull(unknown.nextRepTargetsForReview)
        assertNull(unknown.nextLoadToReview)
        val poorlyRecovered=CoachExerciseDecisionDomain.review(listOf(
            session(rir=listOf(3,3,1))),context)
        assertNull(poorlyRecovered.nextRepTargetsForReview)
    }

    @Test fun verifiedPerSetLoadsCanEarnOneRepWithoutCopyingHeaviestWeight() {
        val mixed=session(reps=listOf(14,13,12),rir=listOf(3,3,4),
            loads=listOf("35","30","25"),
            issued=listOf("35","30","25"))
        // Default straight-set mode must still reject mixed weights.
        assertEquals(FatigueRepReviewReason.UNSTABLE_OR_USER_EDITED_LOADS,
            review(mixed).reason)
        val reviewed=FatigueAwareRepTargetReviewDomain.assess(
            mixed,gym,LoadBasis.DUMBBELL_PER_HAND,CoachReadiness.NORMAL,
            false,verifiedPerSetLoading=true)
        assertEquals(FatigueRepReviewReason.ONE_SET_REP_INCREASE_FOR_REVIEW,
            reviewed.reason)
        assertNull(reviewed.observedLoad)
        assertEquals(listOf(n("35"),n("30"),n("25")),
            reviewed.observedSetLoads)
        assertEquals(listOf(14,13,13),reviewed.candidateRepTargets)
        assertEquals(2,reviewed.proposedOrdinal)
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.canAutoApply)
        assertEquals(listOf(n("35"),n("30"),n("25")),
            mixed.observations.map {it.actualLoad})
        val context=CoachExerciseContext(
            CoachReadiness.NORMAL,false,gym,n("5"),
            verifiedPerSetLoading=true)
        val decision=CoachExerciseDecisionDomain.review(listOf(mixed),context)
        assertEquals(CoachDirection.BUILD_REPS_AT_CURRENT_LOAD,decision.direction)
        assertEquals(listOf(14,13,13),decision.nextRepTargetsForReview)
        assertNull(decision.nextLoadToReview)
        assertNull(decision.observedWorkingLoad)
    }

    @Test fun mixedSetLoadingNeverLaundersEditedUnattainableOrNearFailureLoads() {
        fun authorized(session:ProgressionWorkoutEvidence,
            stock:VerifiedLoadStock?=gym):FatigueRepTargetReview =
            FatigueAwareRepTargetReviewDomain.assess(
                session,stock,LoadBasis.DUMBBELL_PER_HAND,
                CoachReadiness.NORMAL,false,verifiedPerSetLoading=true)
        val valid=session(loads=listOf("35","30","25"),
            issued=listOf("35","30","25"))
        assertEquals(FatigueRepReviewReason.UNSTABLE_OR_USER_EDITED_LOADS,
            authorized(valid.copy(observations=valid.observations.mapIndexed{i,set->
                if(i==2)set.copy(issuedLoad=n("30")) else set
            })).reason)
        assertEquals(FatigueRepReviewReason.CURRENT_LOAD_NOT_VERIFIED,
            authorized(session(loads=listOf("35","32.5","25"),
                issued=listOf("35","32.5","25"))).reason)
        assertEquals(FatigueRepReviewReason.CURRENT_LOAD_NOT_VERIFIED,
            authorized(valid,stock=null).reason)
        assertEquals(FatigueRepReviewReason.BELOW_TARGET_FLOOR,
            authorized(session(loads=listOf("35","30","25"),
                issued=listOf("35","30","25"),
                reps=listOf(14,13,9))).reason)
        assertEquals(FatigueRepReviewReason.NEAR_FAILURE_OR_RECOVERY_PRIORITY,
            authorized(session(loads=listOf("35","30","25"),
                issued=listOf("35","30","25"),
                rir=listOf(3,3,1))).reason)
        assertEquals(CoachDirection.STABILIZE_SET_LOADING,
            CoachExerciseDecisionDomain.review(listOf(valid),
                CoachExerciseContext(CoachReadiness.NORMAL,false,gym,n("5")))
                .direction)
    }

}
