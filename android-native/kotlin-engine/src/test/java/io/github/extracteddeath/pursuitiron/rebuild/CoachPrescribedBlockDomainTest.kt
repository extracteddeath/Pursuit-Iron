package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachPrescribedBlockDomainTest {
    private fun n(v:Int)=BigDecimal(v)
    private val loaded=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n(20),n(25),n(30),n(35)))
    private val groups=listOf(
        TrainingMuscle.CHEST,TrainingMuscle.LATS,TrainingMuscle.QUADS)
    private fun option(id:String,m:TrainingMuscle,pattern:ComposerPattern,
        role:ComposerRole=ComposerRole.SECONDARY)=ComposerExerciseOption(
        id,pattern,setOf(ComposerFocus.FULL_BODY),
        ComposerEquipment.Verified(LoadUnit.LB,n(30),loaded),
        listOf(DeclaredMuscleCredit(m,MuscleCreditRole.DIRECT,BigDecimal.ONE)),
        90,90,2,4,3,1,1,3,role=role)
    private val exercises=listOf(
        option("bench",TrainingMuscle.CHEST,ComposerPattern.HORIZONTAL_PRESS,
            ComposerRole.PRIMARY),
        option("row",TrainingMuscle.LATS,ComposerPattern.HORIZONTAL_PULL),
        option("squat",TrainingMuscle.QUADS,ComposerPattern.SQUAT))
    private fun bounds(cap:Int)=TrainingMuscle.entries.map {m->
        if(m in groups)DeclaredMuscleDoseBounds(m,n(2),n(cap),n(cap),1)
        else DeclaredMuscleDoseBounds(m,n(0),n(0),n(0),0)
    }
    private fun intents(strength:Boolean=false)=listOf(
        ComposerSessionIntent(0,
            if(strength)ComposerSessionPurpose.STRENGTH_SKILL
            else ComposerSessionPurpose.GENERAL_PREPARATION,
            if(strength)"bench" else null),
        ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
        ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY))
    private fun program(
        cap:Int=8,phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        strength:Boolean=false,
        options:List<ComposerExerciseOption> =exercises,
        hard:Int?=null
    )=ComposerRequest(
        ComposerSplit.FULL_BODY,phase,
        listOf(0,2,4).map {day->
            ComposerDay(day,ComposerFocus.FULL_BODY,
                DeclaredSessionWindow(1,90,hard,60))
        },bounds(cap),options,4,1,intents(strength))
    private val evidencePolicy=LongitudinalReviewPolicy(3,1,2,2,3,2)
    private fun history(
        success:Boolean,next:TrainingPhase?=null
    ):LongitudinalReviewRequest {
        val weeks=(0..1).map {index->
            val id="week"+index
            val ids=(0..2).map {id+"-session"+it}.toSet()
            val sets=(0..2).map {ordinal->
                WorkingSetFact(ordinal,SetOutcome.COMPLETED,n(30),
                    if(success)8 else 3,
                    if(success)3 else 1,n(30),2)
            }
            val workout=ProgressionWorkoutEvidence(id+"-session0","bench",
                LoadUnit.LB,RepBand(5,8),3,sets)
            LoggedTrainingWeek(index,id,TrainingPhase.HYPERTROPHY,
                ids,ids,listOf(workout),
                if(success)CoachReadiness.NORMAL else CoachReadiness.LOW,
                !success,false)
        }
        return LongitudinalReviewRequest(weeks,setOf("bench"),
            evidencePolicy,next)
    }
    private fun policies(
        goal:Int=3,rest:Int=150,
        strengthReps:RepBand=RepBand(3,5)
    ):List<ExercisePrescriptionPolicy> =
        exercises.flatMap {exercise->
            listOf(ComposerSessionPurpose.GENERAL_PREPARATION,
                ComposerSessionPurpose.HYPERTROPHY,
                ComposerSessionPurpose.STRENGTH_SKILL).map {purpose->
                ExercisePrescriptionPolicy(exercise.exerciseId,purpose,
                    if(purpose==ComposerSessionPurpose.STRENGTH_SKILL)
                        strengthReps else RepBand(6,10),
                    goal,90,rest,SetLoadScheme.STRAIGHT)
            }
        }
    private fun deload(
        proposed:ComposerRequest=program(cap=4),
        permissions:BlockAdaptPermissions=BlockAdaptPermissions(),
        history:LongitudinalReviewRequest=history(false)
    )=BlockCandidateRequest(history,program(),proposed,
        BlockCandidateKind.RECOVERY_DELOAD,permissions,20)
    private fun transition(
        proposed:ComposerRequest=program(cap=6,
            phase=TrainingPhase.STRENGTH,strength=true),
        h:LongitudinalReviewRequest=history(true,TrainingPhase.STRENGTH)
    )=BlockCandidateRequest(h,program(),proposed,
        BlockCandidateKind.PHASE_TRANSITION,BlockAdaptPermissions())

    private fun review(
        block:BlockCandidateRequest=deload(),
        baseline:List<ExercisePrescriptionPolicy> =policies(),
        candidate:List<ExercisePrescriptionPolicy> =policies()
    )=CoachPrescribedBlockDomain.review(
        PrescribedBlockRequest(block,baseline,candidate))

    @Test fun fullyPrescribedDeloadWithLowerDosePassesBothAuditsForReview() {
        val r=review()
        assertEquals(PrescribedBlockStatus.READY_FOR_HUMAN_REVIEW,r.status)
        assertEquals(BlockCandidateStatus.READY_FOR_HUMAN_REVIEW,
            r.coarseReview.status)
        assertEquals(PrescriptionDraftStatus.COMPLETE_FOR_REVIEW,
            r.proposed.status)
        assertTrue(r.doseComparisons.all {it.change<=BigDecimal.ZERO})
        assertTrue(r.doseComparisons.any {it.change<BigDecimal.ZERO})
        assertEquals(TrainingMuscleCoverage.current.size,r.doseComparisons.size)
        assertTrue(r.unresolvedReasons.isEmpty())
        assertFalse(r.mayIssueWorkout)
        assertFalse(r.mayApplyAutomatically)
        assertFalse(r.mayModifyHistory)
    }

    @Test fun strengthTransitionActuallyIncludesDifferentPurposeRepsAndRir() {
        val r=review(block=transition(),candidate=policies(goal=2))
        assertEquals(PrescribedBlockStatus.READY_FOR_HUMAN_REVIEW,r.status)
        assertEquals(TrainingPhase.STRENGTH,r.proposed.composer.week.phase)
        val main=r.proposed.sessions.first().exercises.first()
        assertEquals("bench",main.exerciseId)
        assertTrue(main.sets.filter {it.type==DraftSetType.WORKING}
            .all {it.targetReps==RepBand(3,5) && it.targetRir==2})
        assertEquals(TrainingPhase.HYPERTROPHY,r.baseline.composer.week.phase)
        assertFalse(r.mayIssueWorkout)
    }

    @Test fun futurePhaseStillNeedsActualEvidenceAndExplicitRequest() {
        val r=review(block=transition(h=history(true)))
        assertEquals(PrescribedBlockStatus.BLOCK_CHANGE_UNRESOLVED,r.status)
        assertEquals(BlockCandidateStatus.NO_SUPPORTING_COACHING_EVIDENCE,
            r.coarseReview.status)
        assertFalse(r.mayApplyAutomatically)
    }

    @Test fun baselineMissingExerciseTargetsBlocksComparativeApproval() {
        val missing=policies().filterNot {it.exerciseId=="row"}
        val r=review(baseline=missing)
        assertEquals(PrescribedBlockStatus.BASELINE_PRESCRIPTION_UNRESOLVED,
            r.status)
        assertNull(r.baseline.finalAudit)
        assertTrue(r.unresolvedReasons.any {it.contains("baseline")})
    }

    @Test fun candidateMissingPurposePolicyIsNotBorrowedFromAnotherDay() {
        val missing=policies().filterNot {it.exerciseId=="bench"}
        val r=review(candidate=missing)
        assertEquals(PrescribedBlockStatus.NEW_PRESCRIPTION_UNRESOLVED,r.status)
        assertNull(r.proposed.finalAudit)
        assertTrue(r.proposed.unresolvedExerciseVisits.isNotEmpty())
    }

    @Test fun oversizedActualRestBreaksCandidateEvenIfCoarseWeekWasFine() {
        val program=program(cap=4,hard=25)
        val block=deload(proposed=program)
        val r=review(block=block,candidate=policies(rest=3600))
        assertEquals(PrescribedBlockStatus.NEW_PRESCRIPTION_UNRESOLVED,r.status)
        assertTrue(r.coarseReview.proposedDraft.audit.satisfiesDeclaredHardConstraints)
        assertFalse(checkNotNull(r.proposed.finalAudit)
            .satisfiesDeclaredHardConstraints)
        assertTrue(r.unresolvedReasons.any {it.contains("set-level")})
    }

    @Test fun deloadCannotIncreasePhysicalWorkingWeightEvenWhenPermitted() {
        val changed=exercises.map {e->
            if(e.exerciseId=="bench")e.copy(equipment=ComposerEquipment.Verified(
                LoadUnit.LB,n(35),loaded)) else e
        }
        val block=deload(proposed=program(cap=4,options=changed),
            permissions=BlockAdaptPermissions(adaptLoads=true))
        val r=review(block=block)
        assertEquals(BlockCandidateStatus.READY_FOR_HUMAN_REVIEW,
            r.coarseReview.status)
        assertEquals(PrescribedBlockStatus.RECOVERY_COMPARISON_UNRESOLVED,r.status)
        assertTrue(r.unresolvedReasons.any {it.contains("load increased")})
    }

    @Test fun deloadCannotBecomeHarderAtSameWeightAndRepRange() {
        val r=review(candidate=policies(goal=0))
        assertEquals(PrescribedBlockStatus.RECOVERY_COMPARISON_UNRESOLVED,r.status)
        assertTrue(r.unresolvedReasons.any {it.contains("effort became harder")})
    }

    @Test fun losingPreviouslyAuthoredEffortDoesNotCountAsProofOfRecovery() {
        val candidate=policies().map {it.copy(targetRir=null)}
        val r=review(candidate=candidate)
        assertEquals(PrescribedBlockStatus.RECOVERY_COMPARISON_UNRESOLVED,r.status)
        assertTrue(r.unresolvedReasons.any {it.contains("removed")})
    }

    @Test fun equipmentSemanticsCannotChangeFromNumericToBodyweightInDeload() {
        val changed=exercises.map {e->
            if(e.exerciseId=="bench")e.copy(equipment=ComposerEquipment.Bodyweight)
            else e
        }
        val r=review(block=deload(
            proposed=program(cap=4,options=changed),
            permissions=BlockAdaptPermissions(adaptLoads=true)))
        assertEquals(PrescribedBlockStatus.RECOVERY_COMPARISON_UNRESOLVED,r.status)
        assertTrue(r.unresolvedReasons.any {it.contains("incompatible equipment")})
    }

    @Test fun loadsPermissionCannotBeImpliedFromAuthoringOfProposedPolicies() {
        val changed=exercises.map {e->
            if(e.exerciseId=="bench")e.copy(equipment=ComposerEquipment.Verified(
                LoadUnit.LB,n(25),loaded)) else e
        }
        val r=review(block=deload(
            proposed=program(cap=4,options=changed)))
        assertEquals(PrescribedBlockStatus.BLOCK_CHANGE_UNRESOLVED,r.status)
        assertEquals(BlockCandidateStatus.CHANGE_REQUIRES_PERMISSION,
            r.coarseReview.status)
    }

    @Test fun statusIsDeterministicAndNeitherDraftCanModifySourceHistory() {
        val input=PrescribedBlockRequest(deload(),policies(),policies())
        val first=CoachPrescribedBlockDomain.review(input)
        val second=CoachPrescribedBlockDomain.review(input)
        assertEquals(first,second)
        assertEquals(TrainingPhase.HYPERTROPHY,
            input.block.baseline.phase)
        assertEquals(n(30),(input.block.baseline.exercises.first().equipment
            as ComposerEquipment.Verified).plannedLoad)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val writable=first.unresolvedReasons as MutableList<String>
            writable.add("invented")
        }
    }

    @Test fun deloadCannotIncreaseRemainingSetRepDemandAtSameWeight() {
        // Reduced volume is no excuse for higher per-set rep demands.
        // Raising either rep bound still warrants coach review, even when a
        // higher target RIR was typed without actual effort evidence.
        for (band in listOf(RepBand(6,12), RepBand(8,10), RepBand(8,12))) {
            for (effortTarget in listOf(3,4)) {
                val changed=policies(goal=effortTarget).map {
                    it.copy(workingReps=band)
                }
                val result=review(candidate=changed)
                assertEquals(
                    "Rep band $band and target RIR $effortTarget must be reviewed",
                    PrescribedBlockStatus.RECOVERY_COMPARISON_UNRESOLVED,
                    result.status
                )
                assertTrue(result.unresolvedReasons.any {
                    it.contains("rep demands increased")
                })
                assertFalse(result.mayApplyAutomatically)
            }
        }
    }

    @Test fun verifiedLighterLoadMaySupportExplicitHigherRepRecoveryWork() {
        // A reviewed, physically available 25-lb load is genuinely lighter
        // than the original 30-lb work. Preserve this legitimate option
        // rather than forbidding all higher-rep recovery programming.
        val lighter=exercises.map { exercise ->
            exercise.copy(equipment=ComposerEquipment.Verified(
                LoadUnit.LB,n(25),loaded))
        }
        val changed=policies().map {
            it.copy(workingReps=RepBand(8,12))
        }
        val r=review(
            block=deload(
                proposed=program(cap=4,options=lighter),
                permissions=BlockAdaptPermissions(adaptLoads=true)
            ),
            candidate=changed
        )
        assertEquals(BlockCandidateStatus.READY_FOR_HUMAN_REVIEW,
            r.coarseReview.status)
        assertEquals(PrescribedBlockStatus.READY_FOR_HUMAN_REVIEW,r.status)
        assertTrue(r.unresolvedReasons.isEmpty())
        assertFalse(r.mayIssueWorkout)
    }

    @Test fun deloadCannotArtificiallyShortenRecoveryBetweenRemainingSets() {
        val shorter=review(candidate=policies(rest=0))
        assertEquals(PrescribedBlockStatus.RECOVERY_COMPARISON_UNRESOLVED,
            shorter.status)
        assertTrue(shorter.unresolvedReasons.any {
            it.contains("rest was shortened")
        })
        val longer=review(candidate=policies(rest=200))
        assertEquals(PrescribedBlockStatus.READY_FOR_HUMAN_REVIEW,
            longer.status)
    }

    @Test fun noUserEditOrCustomProgramIsEverAppliedByReadyReview() {
        val input=PrescribedBlockRequest(deload(),policies(),policies())
        val r=CoachPrescribedBlockDomain.review(input)
        assertEquals(PrescribedBlockStatus.READY_FOR_HUMAN_REVIEW,r.status)
        assertFalse(r.mayIssueWorkout)
        assertFalse(r.mayModifyHistory)
        assertFalse(r.mayApplyAutomatically)
        assertEquals(3,input.block.baseline.exercises.size)
        assertEquals(n(30),
            (input.block.baseline.exercises.first().equipment as
                ComposerEquipment.Verified).plannedLoad)
    }

}
