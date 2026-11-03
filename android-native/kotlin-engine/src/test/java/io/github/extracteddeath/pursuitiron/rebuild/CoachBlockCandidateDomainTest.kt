package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CoachBlockCandidateDomainTest {
    private fun n(value:Int)=BigDecimal(value)
    private val chest=TrainingMuscle.CHEST
    private val lats=TrainingMuscle.LATS
    private val quads=TrainingMuscle.QUADS
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,listOf(n(20),n(25),n(30),n(35)))
    private fun option(id:String,m:TrainingMuscle,pattern:ComposerPattern,
        role:ComposerRole=ComposerRole.SECONDARY)=ComposerExerciseOption(
            id,pattern,setOf(ComposerFocus.FULL_BODY),
            ComposerEquipment.Verified(LoadUnit.LB,n(30),stock),
            listOf(DeclaredMuscleCredit(m,MuscleCreditRole.DIRECT,n(1))),
            90,90,2,4,3,1,1,3,role=role)
    private val press=option("press",chest,ComposerPattern.HORIZONTAL_PRESS,
        ComposerRole.PRIMARY)
    private val row=option("row",lats,ComposerPattern.HORIZONTAL_PULL)
    private val squat=option("squat",quads,ComposerPattern.SQUAT)
    private val all=listOf(press,row,squat)
    private fun bounds(max:Int):List<DeclaredMuscleDoseBounds> =
        TrainingMuscle.entries.map {muscle->
            if(muscle in setOf(chest,lats,quads))
                DeclaredMuscleDoseBounds(muscle,n(2),n(max),n(max),1)
            else DeclaredMuscleDoseBounds(muscle,n(0),n(0),n(0),0)
        }
    private fun plan(max:Int=8,phase:TrainingPhase=TrainingPhase.HYPERTROPHY,
        sessions:List<ComposerSessionIntent> =emptyList(),
        exercises:List<ComposerExerciseOption> =all)=
        ComposerRequest(ComposerSplit.FULL_BODY,phase,
            listOf(0,2,4).map {
                ComposerDay(it,ComposerFocus.FULL_BODY,
                    DeclaredSessionWindow(1,90,null,60))
            },bounds(max),exercises,4,1,sessions)
    private fun intents(strength:Boolean=false)=listOf(
        ComposerSessionIntent(0,
            if(strength)ComposerSessionPurpose.STRENGTH_SKILL
            else ComposerSessionPurpose.GENERAL_PREPARATION,
            if(strength)"press" else null),
        ComposerSessionIntent(1,ComposerSessionPurpose.HYPERTROPHY),
        ComposerSessionIntent(2,ComposerSessionPurpose.HYPERTROPHY)
    )
    private val policy=LongitudinalReviewPolicy(3,1,2,2,3,2)
    private fun history(success:Boolean,
        next:TrainingPhase?=null):LongitudinalReviewRequest {
        val weeks=(0..1).map {i->
            val key="week"+i
            val sessions=(0..2).map {key+"-work"+it}.toSet()
            val workout=ProgressionWorkoutEvidence(key+"-work0","press",
                LoadUnit.LB,RepBand(5,8),3,
                (0..2).map {j ->
                    WorkingSetFact(j,SetOutcome.COMPLETED,n(30),
                        if(success)8 else 3,
                        if(success)2 else 1,n(30),2)
                })
            LoggedTrainingWeek(i,key,TrainingPhase.HYPERTROPHY,
                sessions,sessions,listOf(workout),
                if(success)CoachReadiness.NORMAL else CoachReadiness.LOW,
                if(success)false else true,false)
        }
        return LongitudinalReviewRequest(weeks,setOf("press"),policy,next)
    }
    private fun deload(
        proposed:ComposerRequest=plan(max=4,sessions=intents()),
        permissions:BlockAdaptPermissions=BlockAdaptPermissions(),
        limit:Int=20,
        h:LongitudinalReviewRequest=history(false)
    )=BlockCandidateRequest(h,plan(),proposed,
        BlockCandidateKind.RECOVERY_DELOAD,permissions,limit)
    private fun nextPhase(
        proposed:ComposerRequest=plan(max=6,phase=TrainingPhase.STRENGTH,
            sessions=intents(strength=true)),
        permissions:BlockAdaptPermissions=BlockAdaptPermissions(),
        h:LongitudinalReviewRequest=history(true,next=TrainingPhase.STRENGTH)
    )=BlockCandidateRequest(h,plan(),proposed,
        BlockCandidateKind.PHASE_TRANSITION,permissions)

    @Test fun repeatedRecoveryStrainCanProduceBoundedReviewableDeloadWeek() {
        val review=CoachBlockCandidateDomain.review(deload())
        assertEquals(BlockReviewDirection.REVIEW_DELOAD_WITH_ATHLETE,
            review.historyDecision.direction)
        assertEquals(BlockCandidateStatus.READY_FOR_HUMAN_REVIEW,review.status)
        assertEquals(TrainingPhase.HYPERTROPHY,review.proposedDraft.week.phase)
        assertTrue(review.proposedWorkingSets<review.baselineWorkingSets)
        assertTrue(review.proposedWorkingSets<=20)
        assertTrue(review.proposedDraft.audit.satisfiesDeclaredHardConstraints)
        assertFalse(review.mayApplyAutomatically)
        assertFalse(review.mayWriteWorkoutHistory)
    }

    @Test fun evidenceOfRecoveryWithoutPersistentStrainCannotAuthorizeDeload() {
        val request=deload(h=history(true))
        val review=CoachBlockCandidateDomain.review(request)
        assertEquals(BlockCandidateStatus.NO_SUPPORTING_COACHING_EVIDENCE,
            review.status)
        assertTrue(review.unmetConstraints.any {it.contains("do not justify")})
        assertFalse(review.mayApplyAutomatically)
    }

    @Test fun deloadMustActuallyReduceWorkAndRespectDeclaredAbsoluteCap() {
        val unchanged=CoachBlockCandidateDomain.review(deload(
            proposed=plan(max=8,sessions=intents()),limit=30))
        assertEquals(BlockCandidateStatus.PROGRAM_QUALITY_UNRESOLVED,
            unchanged.status)
        assertEquals(unchanged.baselineWorkingSets,unchanged.proposedWorkingSets)
        val over=CoachBlockCandidateDomain.review(deload(limit=2))
        assertEquals(BlockCandidateStatus.PROGRAM_QUALITY_UNRESOLVED,over.status)
        assertTrue(over.unmetConstraints.any {it.contains("deload maximum")})
    }

    @Test fun successfulWeeksCanRequestButNeverAutomaticallyEnterStrengthPhase() {
        val review=CoachBlockCandidateDomain.review(nextPhase())
        assertEquals(BlockReviewDirection.REVIEW_REQUESTED_PHASE_TRANSITION,
            review.historyDecision.direction)
        assertEquals(BlockCandidateStatus.READY_FOR_HUMAN_REVIEW,review.status)
        assertEquals(TrainingPhase.STRENGTH,review.proposedDraft.week.phase)
        assertEquals(TrainingPhase.HYPERTROPHY,review.baselineDraft.week.phase)
        assertEquals("press",review.proposedDraft.sessions.first().primaryExerciseId)
        assertFalse(review.mayApplyAutomatically)
    }

    @Test fun phaseTransitionMustBeExplicitlyRequestedAndEvidenceGrounded() {
        val request=nextPhase(h=history(true))
        val result=CoachBlockCandidateDomain.review(request)
        assertEquals(BlockCandidateStatus.NO_SUPPORTING_COACHING_EVIDENCE,
            result.status)
        assertFalse(result.mayWriteWorkoutHistory)
        val wrong=nextPhase(proposed=plan(max=6,phase=TrainingPhase.PEAK,
            sessions=intents()),h=history(true,next=TrainingPhase.STRENGTH))
        assertEquals(BlockCandidateStatus.NO_SUPPORTING_COACHING_EVIDENCE,
            CoachBlockCandidateDomain.review(wrong).status)
    }

    @Test fun strengthPhaseWithoutNamedPrimaryLiftRemainsUnresolved() {
        val poor=plan(max=6,phase=TrainingPhase.STRENGTH,
            sessions=intents(strength=false))
        val review=CoachBlockCandidateDomain.review(nextPhase(proposed=poor))
        assertEquals(BlockCandidateStatus.PROGRAM_QUALITY_UNRESOLVED,review.status)
        assertTrue(review.unmetConstraints.any {it.contains("primary skill lift")})
    }

    @Test fun unspecifiedSessionsAreNotCalledCoachWrittenProgram() {
        val review=CoachBlockCandidateDomain.review(
            deload(proposed=plan(max=4)))
        assertEquals(BlockCandidateStatus.PROGRAM_QUALITY_UNRESOLVED,review.status)
        assertTrue(review.unmetConstraints.any {it.contains("session")})
    }

    @Test fun scheduleChangesNeedExplicitPermissionEvenWhenWeekIsFeasible() {
        val newDays=plan(max=4,sessions=intents()).days.mapIndexed {i,d ->
            d.copy(calendarDay=listOf(0,3,5)[i])
        }
        val proposed=plan(max=4,sessions=intents()).copy(days=newDays)
        val blocked=CoachBlockCandidateDomain.review(deload(proposed=proposed))
        assertEquals(BlockCandidateStatus.CHANGE_REQUIRES_PERMISSION,blocked.status)
        assertTrue(blocked.change.scheduleChanged)
        val allowed=CoachBlockCandidateDomain.review(deload(proposed=proposed,
            permissions=BlockAdaptPermissions(adaptSchedule=true)))
        assertEquals(BlockCandidateStatus.READY_FOR_HUMAN_REVIEW,allowed.status)
    }

    @Test fun changingAvailableWeightsNeedsExplicitAuthorization() {
        val changed=all.map {item ->
            if(item.exerciseId=="press")item.copy(
                equipment=ComposerEquipment.Verified(LoadUnit.LB,n(25),stock))
            else item
        }
        val proposed=plan(max=4,sessions=intents(),exercises=changed)
        val blocked=CoachBlockCandidateDomain.review(deload(proposed=proposed))
        assertEquals(BlockCandidateStatus.CHANGE_REQUIRES_PERMISSION,blocked.status)
        assertTrue(blocked.change.equipmentLoadsChanged)
        assertFalse(blocked.change.exerciseCatalogChanged)
        val permitted=CoachBlockCandidateDomain.review(deload(proposed=proposed,
            permissions=BlockAdaptPermissions(adaptLoads=true)))
        assertEquals(BlockCandidateStatus.READY_FOR_HUMAN_REVIEW,permitted.status)
    }

    @Test fun exerciseStructureChangesNeedAdaptExercisesPermission() {
        val alt=all.map {item ->
            if(item.exerciseId=="row")item.copy(priority=10)else item
        }
        val proposed=plan(max=4,sessions=intents(),exercises=alt)
        val result=CoachBlockCandidateDomain.review(deload(proposed=proposed))
        assertEquals(BlockCandidateStatus.CHANGE_REQUIRES_PERMISSION,result.status)
        assertTrue(result.change.exerciseCatalogChanged)
    }

    @Test fun pinnedCustomExerciseCannotBeRemovedWithGenericAdaptation() {
        val remaining=listOf(row,squat)
        val proposed=plan(max=4,sessions=intents(),exercises=remaining)
            .copy(bounds=TrainingMuscle.entries.map {m ->
                if(m==chest)DeclaredMuscleDoseBounds(m,n(0),n(0),n(0),0)
                else if(m==lats || m==quads)
                    DeclaredMuscleDoseBounds(m,n(2),n(4),n(4),1)
                else DeclaredMuscleDoseBounds(m,n(0),n(0),n(0),0)
            })
        val blocked=CoachBlockCandidateDomain.review(deload(
            proposed=proposed,
            permissions=BlockAdaptPermissions(adaptExercises=true)))
        assertEquals(BlockCandidateStatus.CHANGE_REQUIRES_PERMISSION,blocked.status)
        assertEquals(setOf("press"),blocked.change.pinnedExercisesRemoved)
    }

    @Test fun infeasibleCandidateNeverBecomesReadyDespiteCorrectHistory() {
        val proposed=plan(max=4,sessions=intents()).copy(
            bounds=bounds(1))
        assertThrows(IllegalArgumentException::class.java) {
            // A malformed MEV/MAV/MRV band fails independently.
            CoachBlockCandidateDomain.review(deload(proposed=proposed))
        }
        val excessive=plan(max=4,sessions=intents()).copy(
            days=plan(max=4).days.map {d ->
                d.copy(window=d.window.copy(userHardLimitMinutes=1))
            })
        val result=CoachBlockCandidateDomain.review(deload(proposed=excessive))
        assertEquals(BlockCandidateStatus.PROGRAM_QUALITY_UNRESOLVED,result.status)
    }

    @Test fun sameInputsYieldSameDraftAndNoSourceMutation() {
        val request=deload()
        val first=CoachBlockCandidateDomain.review(request)
        val second=CoachBlockCandidateDomain.review(request)
        assertEquals(first,second)
        assertEquals(TrainingPhase.HYPERTROPHY,request.baseline.phase)
        assertEquals(n(30),(request.baseline.exercises.first().equipment
            as ComposerEquipment.Verified).plannedLoad)
        assertThrows(UnsupportedOperationException::class.java) {
            @Suppress("UNCHECKED_CAST")
            val mutable=first.unmetConstraints as MutableList<String>
            mutable.add("false")
        }
    }

    @Test fun reducingPinnedRequiredVisitsNeedsSameSpecialPermissionAsRemoval() {
        val reduced=all.map {
            if(it.exerciseId=="press")it.copy(requiredVisits=0) else it
        }
        val proposal=plan(max=4,sessions=intents(),exercises=reduced)
        val result=CoachBlockCandidateDomain.review(deload(
            proposed=proposal,
            permissions=BlockAdaptPermissions(adaptExercises=true)))
        assertEquals(BlockCandidateStatus.CHANGE_REQUIRES_PERMISSION,result.status)
        assertEquals(setOf("press"),result.change.pinnedExercisesRemoved)
    }

    @Test fun globalDeloadCannotIncreaseOneMuscleToMaskReductionsElsewhere() {
        val old=plan()
        val shifted=plan(max=4,sessions=intents()).copy(
            bounds=TrainingMuscle.entries.map {m ->
                when(m) {
                    chest -> DeclaredMuscleDoseBounds(m,n(10),n(10),n(10),1)
                    lats,quads -> DeclaredMuscleDoseBounds(m,n(2),n(2),n(2),1)
                    else -> DeclaredMuscleDoseBounds(m,n(0),n(0),n(0),0)
                }
            })
        val proposed=CoachBlockCandidateDomain.review(deload(
            proposed=shifted,limit=20))
        assertEquals(BlockCandidateStatus.PROGRAM_QUALITY_UNRESOLVED,proposed.status)
        assertTrue(proposed.unmetConstraints.any {
            it.contains("increase a muscle")
        })
        assertEquals(old.phase,proposed.baselineDraft.week.phase)
    }

}
