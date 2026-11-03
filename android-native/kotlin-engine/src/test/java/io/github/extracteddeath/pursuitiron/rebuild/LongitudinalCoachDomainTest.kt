package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class LongitudinalCoachDomainTest {
    private fun n(v:Int)=BigDecimal(v)
    private val key=setOf("bench")
    private val policy=LongitudinalReviewPolicy(
        minimumCompletedWorkoutsPerWeek=3,
        minimumKeyExerciseWorkoutsPerWeek=1,
        consecutiveStrainWeeksForLoadReview=2,
        consecutiveStrainAndRecoveryWeeksForDeloadReview=2,
        comparableWeeksForPlateauReview=3,
        successfulWeeksForPhaseReview=2
    )
    private fun week(index:Int,reps:List<Int> = listOf(6,6,6),
        rir:List<Int?> = listOf(2,2,2),
        load:Int=200,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        readiness:CoachReadiness?=CoachReadiness.NORMAL,
        excessFatigue:Boolean?=false,
        restricted:Boolean=false,
        completed:Int=3,
        target:RepBand=RepBand(5,8),
        incomplete:Boolean=false,
        mixedLoad:Boolean=false
    ):LoggedTrainingWeek {
        val id="week"+index
        val planned=(0..2).map {id+":session"+it}.toSet()
        val completedIds=(0 until completed).map {id+":session"+it}.toSet()
        val observations=(0..2).map {ordinal->
            WorkingSetFact(ordinal,SetOutcome.COMPLETED,
                n(if(mixedLoad && ordinal==2)load+5 else load),
                reps[ordinal],rir[ordinal],n(200),2)
        }.let {if(incomplete)it.dropLast(1) else it}
        val workouts=if(completedIds.isEmpty())emptyList()
            else listOf(ProgressionWorkoutEvidence(id+":session0","bench",
                LoadUnit.LB,target,3,observations))
        return LoggedTrainingWeek(index,id,phase,planned,completedIds,
            workouts,readiness,excessFatigue,restricted)
    }
    private fun report(vararg weeks:LoggedTrainingWeek,
        next:TrainingPhase?=null)=LongitudinalCoachDomain.review(
            LongitudinalReviewRequest(weeks.toList(),key,policy,next))

    @Test fun repeatedStrainWithReportedPoorRecoveryRequestsDeloadReviewOnly() {
        val first=week(1,reps=listOf(3,4,3),
            readiness=CoachReadiness.LOW,excessFatigue=true)
        val second=week(2,reps=listOf(4,3,4),
            readiness=CoachReadiness.LOW,excessFatigue=true)
        val result=report(first,second)
        assertEquals(BlockReviewDirection.REVIEW_DELOAD_WITH_ATHLETE,result.direction)
        assertEquals(TrainingPhase.HYPERTROPHY,result.currentPhase)
        assertNull(result.nextPhaseForReview)
        assertFalse(result.canChangeExistingProgram)
        assertEquals(2,result.weekPatterns.size)
        assertEquals(1,result.weekPatterns.last().belowRepFloorWorkouts)
        assertEquals(0,result.weekPatterns.last().nearFailureWorkouts)
    }

    @Test fun aSingleHardWorkoutDoesNotForceAProgramDeload() {
        val result=report(week(1,reps=listOf(3,3,3),
            readiness=CoachReadiness.LOW))
        assertEquals(BlockReviewDirection.REVIEW_RECOVERY_BEFORE_TRAINING,
            result.direction)
        assertNull(result.nextPhaseForReview)
    }

    @Test fun sustainedDifficultyWithoutRecoveryComplaintsReviewsLoadNotDeload() {
        val result=report(week(1,reps=listOf(2,3,4)),
            week(2,reps=listOf(3,4,4)))
        assertEquals(BlockReviewDirection.REVIEW_TRAINING_LOAD,result.direction)
        assertFalse(result.canChangeExistingProgram)
    }

    @Test fun oneLowReadinessWeekAfterPriorNormalDifficultyNeedsRecoveryReview() {
        val result=report(week(1,reps=listOf(2,3,4)),
            week(2,reps=listOf(3,4,4),readiness=CoachReadiness.LOW))
        assertEquals(BlockReviewDirection.REVIEW_RECOVERY_BEFORE_TRAINING,
            result.direction)
    }

    @Test fun notReportingRirNeverBecomesAnInferredNearFailureOrDeload() {
        val unknown=listOf(null,null,null)
        val result=report(week(1,rir=unknown,readiness=CoachReadiness.LOW),
            week(2,rir=unknown,readiness=CoachReadiness.LOW))
        assertEquals(BlockReviewDirection.REVIEW_RECOVERY_BEFORE_TRAINING,
            result.direction)
        assertTrue(result.weekPatterns.all {it.nearFailureWorkouts==0})
        assertEquals(CoachConfidence.LIMITED,result.confidence)
        assertEquals(1,result.weekPatterns.last().workoutsWithMissingActualRir)
    }

    @Test fun skippedOrMissingSetDoesNotCountAsEvidenceOfPerformanceFailure() {
        val result=report(week(1),week(2,incomplete=true))
        assertEquals(BlockReviewDirection.GATHER_MORE_TRAINING_EVIDENCE,
            result.direction)
        assertFalse(result.weekPatterns.last().sufficientForReview)
        assertEquals(0,result.weekPatterns.last().belowRepFloorWorkouts)
        assertEquals(0,result.weekPatterns.last().nearFailureWorkouts)
    }

    @Test fun unfinishedTrainingWeekDoesNotBecomeRepeatedStrain() {
        val result=report(week(1,reps=listOf(2,3,4),
            readiness=CoachReadiness.LOW),
            week(2,reps=listOf(2,3,4),readiness=CoachReadiness.LOW,
                completed=1))
        assertEquals(BlockReviewDirection.GATHER_MORE_TRAINING_EVIDENCE,
            result.direction)
        assertFalse(result.weekPatterns.last().sufficientForReview)
    }

    @Test fun weeksAtSameLoadAndRepsCanFlagObjectiveSpecificPlateau() {
        val result=report(week(1),week(2),week(3))
        assertEquals(BlockReviewDirection.REVIEW_STALLED_EXERCISES,result.direction)
        assertEquals(setOf("bench"),result.comparablePlateauExerciseIds)
        assertTrue(result.explanation.contains("fixed load"))
    }

    @Test fun improvementOrChangedLoadShouldNotFakeAPlateau() {
        val improved=report(week(1,reps=listOf(6,6,6)),
            week(2,reps=listOf(6,7,6)),
            week(3,reps=listOf(7,7,6)))
        assertEquals(BlockReviewDirection.CONTINUE_CURRENT_BLOCK,improved.direction)
        assertTrue(improved.comparablePlateauExerciseIds.isEmpty())
        val weightChanged=report(week(1),week(2),week(3,load=205))
        assertEquals(BlockReviewDirection.CONTINUE_CURRENT_BLOCK,weightChanged.direction)
    }

    @Test fun mixedActualLoadsAndChangedRepTargetsInvalidatePlateauComparability() {
        val mixed=report(week(1),week(2),
            week(3,mixedLoad=true))
        assertTrue(mixed.comparablePlateauExerciseIds.isEmpty())
        val changed=report(week(1),week(2),
            week(3,target=RepBand(6,10)))
        assertEquals(BlockReviewDirection.CONTINUE_CURRENT_BLOCK,changed.direction)
        assertTrue(changed.comparablePlateauExerciseIds.isEmpty())
    }

    @Test fun weekIndexGapOrPhaseChangeBreaksStrainStreak() {
        val gap=report(week(1,reps=listOf(2,3,4),
            readiness=CoachReadiness.LOW),
            week(3,reps=listOf(2,3,4),readiness=CoachReadiness.LOW))
        assertEquals(BlockReviewDirection.REVIEW_RECOVERY_BEFORE_TRAINING,
            gap.direction)
        val newPhase=report(week(1,reps=listOf(2,3,4),
                readiness=CoachReadiness.LOW),
            week(2,reps=listOf(2,3,4),readiness=CoachReadiness.LOW,
                phase=TrainingPhase.STRENGTH))
        assertEquals(BlockReviewDirection.REVIEW_RECOVERY_BEFORE_TRAINING,
            newPhase.direction)
    }

    @Test fun requestedPhaseTransitionRequiresRepeatableSuccessAndApproval() {
        val strong1=week(1,reps=listOf(8,8,8))
        val strong2=week(2,reps=listOf(8,8,8))
        val held=report(strong1,strong2)
        assertEquals(BlockReviewDirection.CONTINUE_CURRENT_BLOCK,held.direction)
        assertNull(held.nextPhaseForReview)
        val transition=report(strong1,strong2,next=TrainingPhase.STRENGTH)
        assertEquals(BlockReviewDirection.REVIEW_REQUESTED_PHASE_TRANSITION,
            transition.direction)
        assertEquals(TrainingPhase.STRENGTH,transition.nextPhaseForReview)
        assertFalse(transition.canChangeExistingProgram)
        assertEquals(TrainingPhase.HYPERTROPHY,strong1.phase)
    }

    @Test fun unknownRirLowersConfidenceButDoesNotInventFailureOnPhaseReview() {
        val unknown=listOf(null,null,null)
        val result=report(week(1,reps=listOf(8,8,8),rir=unknown),
            week(2,reps=listOf(8,8,8),rir=unknown),
            next=TrainingPhase.STRENGTH)
        assertEquals(BlockReviewDirection.REVIEW_REQUESTED_PHASE_TRANSITION,
            result.direction)
        assertEquals(CoachConfidence.LIMITED,result.confidence)
    }

    @Test fun actualNearFailureStopsAutomaticPhasePraise() {
        val result=report(week(1,reps=listOf(8,8,8),rir=listOf(1,2,2)),
            week(2,reps=listOf(8,8,8),rir=listOf(0,2,2)),
            next=TrainingPhase.STRENGTH)
        assertEquals(BlockReviewDirection.REVIEW_TRAINING_LOAD,result.direction)
        assertNull(result.nextPhaseForReview)
    }

    @Test fun reportedMovementRestrictionOutranksCycleAndProgression() {
        val result=report(week(1,reps=listOf(8,8,8)),
            week(2,reps=listOf(8,8,8),restricted=true),
            next=TrainingPhase.STRENGTH)
        assertEquals(BlockReviewDirection.REVIEW_REPORTED_RESTRICTION,
            result.direction)
        assertNull(result.nextPhaseForReview)
    }

    @Test fun noHistoryOrUnknownWorkoutReferencesAreInvalidNotEvidence() {
        val valid=week(1)
        val invalid=listOf(
            valid.copy(scheduledWorkoutIds=emptySet()),
            valid.copy(completedWorkoutIds=setOf("not-scheduled")),
            valid.copy(keyExerciseWorkouts=listOf(
                valid.keyExerciseWorkouts.single().copy(workoutId="not-scheduled"))),
            valid.copy(keyExerciseWorkouts=valid.keyExerciseWorkouts+
                valid.keyExerciseWorkouts),
            valid.copy(weekIndex=-1),
            valid.copy(weekId="invalid week id")
        )
        invalid.forEach {w->
            assertThrows(IllegalArgumentException::class.java) {
                report(w)
            }
        }
        assertThrows(IllegalArgumentException::class.java) {
            LongitudinalCoachDomain.review(
                LongitudinalReviewRequest(emptyList(),key,policy))
        }
        assertThrows(IllegalArgumentException::class.java) {
            report(week(1),week(1))
        }
        assertThrows(IllegalArgumentException::class.java) {
            report(week(2),week(1))
        }
    }

    @Test fun malformedPolicyAndFabricatedActualEffortFailClosed() {
        val w=week(1)
        val invalid=listOf(
            policy.copy(minimumCompletedWorkoutsPerWeek=0),
            policy.copy(minimumKeyExerciseWorkoutsPerWeek=0),
            policy.copy(comparableWeeksForPlateauReview=2),
            policy.copy(consecutiveStrainAndRecoveryWeeksForDeloadReview=1),
            policy.copy(successfulWeeksForPhaseReview=1))
        invalid.forEach {p->
            assertThrows(IllegalArgumentException::class.java) {
                LongitudinalCoachDomain.review(
                    LongitudinalReviewRequest(listOf(w),key,p))
            }
        }
        val e=w.keyExerciseWorkouts.single()
        val fictional=e.copy(observations=listOf(
            WorkingSetFact(0,SetOutcome.COMPLETED,n(200),6,20)))
        assertThrows(IllegalArgumentException::class.java) {
            report(w.copy(keyExerciseWorkouts=listOf(fictional)))
        }
    }

    @Test fun outputIsDeterministicAndReturnCollectionCannotBeModified() {
        val w=listOf(week(1),week(2),week(3))
        val a=LongitudinalCoachDomain.review(
            LongitudinalReviewRequest(w,key,policy))
        val b=LongitudinalCoachDomain.review(
            LongitudinalReviewRequest(w,key,policy))
        assertEquals(a,b)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val changed=a.weekPatterns as MutableList<ObservedWeekPattern>
            changed.clear()
        }
    }
}
