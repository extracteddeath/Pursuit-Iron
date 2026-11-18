package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class StrengthExerciseMenuRecoveryDomainTest {
    private val fixture=GeneratedAdaptiveBlockFixture
    private val load=DraftSetLoad.Verified(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,BigDecimal("25"))
    private fun set()=CustomAuthoredSet(DraftSetType.WORKING,load,
        RepBand(8,12),2,50,90)
    private fun slot(id:String,role:ComposerRole)=CustomExerciseSlot(
        "slot_"+id,id,role,listOf(set(),set()),pinned=false)
    private val extraIds=listOf("curl","extension","wrist-flexion",
        "wrist-extension","lateral")
    private val muscles=listOf(TrainingMuscle.BICEPS,TrainingMuscle.TRICEPS,
        TrainingMuscle.WRIST_FLEXORS,TrainingMuscle.WRIST_EXTENSORS,
        TrainingMuscle.SIDE_DELTS)
    private fun extras()=extraIds.zip(muscles).map{(id,muscle)->
        CatalogMovement(id,id,"dumbbell",null,ComposerPattern.ISOLATION,
            ComposerRole.ACCESSORY,ComposerFocus.entries.toSet(),"dumbbells",
            LoadBasis.DUMBBELL_PER_HAND,
            listOf(DeclaredMuscleCredit(muscle,MuscleCreditRole.DIRECT,
                BigDecimal.ONE)),
            CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL,"source-verified")
    }
    private val catalog by lazy{fixture.movements+extras()}
    private fun core()=listOf(
        slot("bench",ComposerRole.PRIMARY).copy(pinned=true),
        slot("row",ComposerRole.SECONDARY),
        slot("squat",ComposerRole.SECONDARY))
    private fun cycle():CustomTrainingCycle {
        val split=CustomSplitIdentity.Named(ComposerSplit.FULL_BODY)
        fun days(hyper:Boolean)=listOf(0,2,4).map{weekday->
            CustomTrainingDay(weekday,"Day "+weekday,
                if(hyper)ComposerSessionPurpose.HYPERTROPHY
                else ComposerSessionPurpose.STRENGTH_SKILL,
                core()+if(hyper && weekday==0)extraIds.map{
                    slot(it,ComposerRole.ACCESSORY)
                } else emptyList())
        }
        return CustomTrainingCycle("cycle-recovery",6,"athlete-1",
            AthleteTrainingGoal.POWERBUILDING,"Source-owned future cycle",
            listOf(
                CustomTrainingBlock("hypertrophy","H",
                    TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,2,
                    split,days(true)),
                CustomTrainingBlock("strength","Strength",
                    TrainingPhase.STRENGTH,CustomBlockPurpose.TRAINING,2,
                    split,days(false))))
    }
    private fun audit(cycle:CustomTrainingCycle):CustomCoachAudit {
        val source=CustomCycleReview(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
            emptyList(),4,cycle)
        fun week(n:Int,block:String,phase:TrainingPhase)=
            CustomCoachWeekMeasurement(n,block,phase,CustomBlockPurpose.TRAINING,
                if(n<=2)28 else 18,emptyMap(),emptyMap(),emptyMap(),
                emptyMap(),emptyMap())
        return CustomCoachAudit(CustomCoachAuditStatus.UNRESOLVED,
            listOf(CustomCoachIssue(CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
                "week.3","Continuity needs review")),
            source,listOf(week(1,"hypertrophy",TrainingPhase.HYPERTROPHY),
                week(2,"hypertrophy",TrainingPhase.HYPERTROPHY),
                week(3,"strength",TrainingPhase.STRENGTH),
                week(4,"strength",TrainingPhase.STRENGTH)),
            "rev-1",cycle.revision,emptyMap())
    }
    private fun request(c:CustomTrainingCycle=cycle(),
        permission:Boolean=true,max:Int=8)=StrengthMenuRecoveryRequest(
        AdaptiveCycleOptionRequest(c,"strength",fixture.athlete(),
            catalog,CustomCoachAuditInputs(),
            CustomCycleCoachPermissions(adaptExercises=permission),
            emptyList()),max,8)

    @Test fun realExitWeekMenuRestoresFiveDistinctAccessoriesWithoutMutatingSource() {
        val current=cycle()
        val before=current.toString()
        val result=StrengthExerciseMenuRecoveryDomain.preview(
            request(current),audit(current))
        assertEquals(StrengthMenuRecoveryStatus.PREVIEW_ONLY,result.status)
        val proposed=checkNotNull(result.proposedCycle)
        assertEquals(6L,current.revision)
        assertEquals(7L,proposed.revision)
        assertEquals(before,current.toString())
        assertEquals(current.blocks.first(),proposed.blocks.first())
        assertEquals(extraIds,result.restored.map{it.exerciseId})
        assertEquals(setOf(0),result.restored.map{it.weekday}.toSet())
        assertEquals(8,proposed.blocks.last().days.first().slots.size)
        assertEquals(core(),current.blocks.last().days.first().slots)
        assertEquals(current.blocks.last().days.drop(1),
            proposed.blocks.last().days.drop(1))
        assertEquals(setOf(2),result.restored.map{it.workingSets}.toSet())
        assertEquals(extraIds,proposed.blocks.last().days.first().slots.drop(3)
            .map{it.exerciseId})
        assertEquals(extraIds.map{listOf(set(),set())},
            proposed.blocks.last().days.first().slots.drop(3).map{it.sets})
        assertEquals(5,result.restored.map{it.proposedSlotId}.distinct().size)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayAlterHistory)
    }

    @Test fun capacityAndConstrainedSourceNeverPretendTheyRestoredEverything() {
        val c=cycle()
        val first=StrengthExerciseMenuRecoveryDomain.preview(request(c,max=5),
            audit(c))
        assertEquals(StrengthMenuRecoveryStatus.PREVIEW_ONLY,first.status)
        assertEquals(2,first.restored.size)
        assertEquals(5,first.proposedCycle!!.blocks.last().days.first().slots.size)
        val zero=StrengthExerciseMenuRecoveryDomain.preview(request(c,max=3),audit(c))
        assertEquals(StrengthMenuRecoveryStatus.NO_REVIEWABLE_SOURCE,zero.status)
        val noPermission=StrengthExerciseMenuRecoveryDomain.preview(
            request(c,permission=false),audit(c))
        assertEquals(StrengthMenuRecoveryStatus.PERMISSION_REQUIRED,
            noPermission.status)
        val changed=c.copy(blocks=listOf(c.blocks.first(),
            c.blocks.last().copy(split=CustomSplitIdentity.Freeform("PPL"))))
        assertEquals(StrengthMenuRecoveryStatus.NO_REVIEWABLE_SOURCE,
            StrengthExerciseMenuRecoveryDomain.preview(
                request(changed),audit(changed)).status)
        val overridden=c.copy(weekOverrides=mapOf("strength" to listOf(
            CustomWeekSlotOverride(1,0,"slot_bench",listOf(set())))))
        assertEquals(StrengthMenuRecoveryStatus.NO_REVIEWABLE_SOURCE,
            StrengthExerciseMenuRecoveryDomain.preview(
                request(overridden),audit(overridden)).status)
        assertEquals(StrengthMenuRecoveryStatus.SOURCE_UNVERIFIED,
            StrengthExerciseMenuRecoveryDomain.preview(
                request(c),audit(c.copy(revision=7))).status)
    }

    @Test fun fullyAuditedRecoveryCanProduceANonIssuableAthleteReview() {
        val c=cycle()
        val base=checkNotNull(fixture.generation().policy)
        val activeExtras=muscles.toSet()
        val bounds=base.selectedBounds.map{dose->
            if(dose.muscle in activeExtras)
                dose.copy(mev=BigDecimal.ZERO,mav=BigDecimal("2"),
                    mrv=BigDecimal("4"),minimumDirectDays=0)
            else dose
        }
        val hyper=base.copy(baselineBounds=bounds,selectedBounds=bounds)
        val strength=hyper.copy(
            policyId="strength-source-policy",revision="strength-v2",
            phase=TrainingPhase.STRENGTH,
            minimumPurposeDays=mapOf(ComposerSessionPurpose.STRENGTH_SKILL to 3),
            primaryLiftMinimumDays=mapOf("bench" to 1))
        val inputs=CustomCoachAuditInputs(
            policiesByPhase=mapOf(
                TrainingPhase.HYPERTROPHY to hyper,
                TrainingPhase.STRENGTH to strength),
            nonSetSecondsByWeekday=mapOf(0 to 900,2 to 900,4 to 900),
            continuityPolicy=CustomCoachVolumeContinuityPolicy(
                BigDecimal("0.25"),BigDecimal("0.25"),
                BigDecimal("0.8"),BigDecimal("0.8")))
        val before=CustomCycleCoachAuditDomain.review(c,fixture.athlete(),
            catalog,inputs)
        assertEquals("A real source continuity defect is required: "+
            before.issues,CustomCoachAuditStatus.UNRESOLVED,before.status)
        assertTrue(before.issues.toString(),before.issues.any{
            it.kind==CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP})
        assertFalse(before.issues.toString(),before.issues.any{
            it.kind in setOf(CustomCoachIssueKind.CATALOG_UNVERIFIED,
                CustomCoachIssueKind.BELOW_WEEKLY_MEV,
                CustomCoachIssueKind.ABOVE_WEEKLY_MRV)})
        val input=request(c).copy(review=request(c).review.copy(coaching=inputs))
        val review=StrengthExerciseMenuRecoveryDomain.propose(input)
        assertEquals("Restored, already-authored strength movements should "+
            "pass the SAME actual full-cycle auditor: "+
            review.comparison.evaluated.map{
                it.status to it.coachingAudit?.issues},
            StrengthMenuRecoveryStatus.READY_FOR_ATHLETE_REVIEW,review.status)
        assertEquals(extraIds,review.restored.map{it.exerciseId})
        assertEquals(AdaptiveCycleOptionStatus.READY_FOR_REVIEW,
            review.comparison.evaluated.single().status)
        val candidate=checkNotNull(review.reviewedOption).proposedCycle
        assertEquals(28,review.comparison.evaluated.single()
            .coachingAudit!!.weeks[2].workingSets)
        assertEquals(7L,candidate.revision)
        assertEquals(c.blocks.first(),candidate.blocks.first())
        assertEquals(6L,c.revision)
        assertFalse(review.maySaveProgram)
        assertFalse(review.mayIssueWorkout)
        assertFalse(review.mayApplyAutomatically)
        assertFalse(review.mayAlterHistory)
    }

    @Test fun fullProposeNeverTreatsInjectedStructuralAuditAsAuthority() {
        val c=cycle()
        val result=StrengthExerciseMenuRecoveryDomain.propose(request(c))
        assertNull(result.reviewedOption)
        assertTrue(result.status!=StrengthMenuRecoveryStatus.READY_FOR_ATHLETE_REVIEW)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayAlterHistory)
        assertEquals(c,cycle())
    }
}
