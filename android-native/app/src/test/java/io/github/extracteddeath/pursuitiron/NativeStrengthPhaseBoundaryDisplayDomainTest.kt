package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** Plan must display the real last training week across a deload, not the
 * immediately adjacent low-dose bridge. This never edits a saved workout. */
class NativeStrengthPhaseBoundaryDisplayDomainTest {
    private fun n(s:String)=BigDecimal(s)
    private val stock=VerifiedLoadStock.Totals(
        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,listOf(n("20")))
    private fun set()=CustomAuthoredSet(
        DraftSetType.WORKING,
        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,n("20")),
        RepBand(8,12),2,50,90)
    private fun day(phase:TrainingPhase,short:Boolean)=CustomTrainingDay(
        0,"Upper day",
        if(phase==TrainingPhase.STRENGTH)
            ComposerSessionPurpose.STRENGTH_SKILL
        else ComposerSessionPurpose.HYPERTROPHY,
        listOf(
            CustomExerciseSlot("bench","bench",ComposerRole.PRIMARY,
                List(if(short)2 else 4){set()}),
            CustomExerciseSlot("row","row",ComposerRole.SECONDARY,
                List(if(short)2 else 4){set()})
        )+(if(short)emptyList() else listOf(
            CustomExerciseSlot("curl","curl",ComposerRole.ACCESSORY,
                List(4){set()})
        )))
    private fun source():CustomTrainingCycle {
        val split=CustomSplitIdentity.Named(ComposerSplit.FULL_BODY)
        return CustomTrainingCycle("cycle-1",4,"athlete-1",
            AthleteTrainingGoal.POWERBUILDING,"Training cycle",
            listOf(
                CustomTrainingBlock("h","Hypertrophy",TrainingPhase.HYPERTROPHY,
                    CustomBlockPurpose.TRAINING,2,split,
                    listOf(day(TrainingPhase.HYPERTROPHY,false))),
                CustomTrainingBlock("r","Deload",TrainingPhase.HYPERTROPHY,
                    CustomBlockPurpose.RECOVERY,1,split,
                    listOf(day(TrainingPhase.HYPERTROPHY,true))),
                CustomTrainingBlock("s","Strength",TrainingPhase.STRENGTH,
                    CustomBlockPurpose.TRAINING,2,split,
                    listOf(day(TrainingPhase.STRENGTH,true)))
            ))
    }
    private fun structure(source:CustomTrainingCycle)=
        CustomTrainingCycleDomain.review(source,listOf("bench","row","curl")
            .map{CustomExerciseAvailability(it,stock,false)})
    private fun measured(week:Int,block:String,phase:TrainingPhase,
        purpose:CustomBlockPurpose,working:Int,
        direct:Map<TrainingMuscle,BigDecimal>)=
        CustomCoachWeekMeasurement(week,block,phase,purpose,working,direct,
            emptyMap(),emptyMap(),emptyMap(),emptyMap())
    private fun audit(source:CustomTrainingCycle):CustomCoachAudit {
        val h=mapOf(
            TrainingMuscle.BICEPS to n("4"),
            TrainingMuscle.BRACHIALIS to n("1"),
            TrainingMuscle.TRICEPS to n("1"),
            TrainingMuscle.WRIST_EXTENSORS to n("1"))
        val bridge=mapOf(
            TrainingMuscle.BICEPS to n("0"),
            TrainingMuscle.BRACHIALIS to n("0"),
            TrainingMuscle.TRICEPS to n("1"),
            TrainingMuscle.WRIST_EXTENSORS to n("0"))
        return CustomCoachAudit(CustomCoachAuditStatus.UNRESOLVED,
            emptyList(),structure(source),
            listOf(
                measured(1,"h",TrainingPhase.HYPERTROPHY,
                    CustomBlockPurpose.TRAINING,12,h),
                measured(2,"h",TrainingPhase.HYPERTROPHY,
                    CustomBlockPurpose.TRAINING,12,h),
                measured(3,"r",TrainingPhase.HYPERTROPHY,
                    CustomBlockPurpose.RECOVERY,4,bridge),
                measured(4,"s",TrainingPhase.STRENGTH,
                    CustomBlockPurpose.TRAINING,4,bridge),
                measured(5,"s",TrainingPhase.STRENGTH,
                    CustomBlockPurpose.TRAINING,4,bridge)
            ),"source-v1",source.revision,emptyMap())
    }

    @Test fun lastNormalHypertrophyWeekSurvivesARecoveryBridgeForArmLossReview() {
        val original=source()
        val current=structure(original)
        assertEquals(current.issues.toString(),
            CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,current.status)
        val boundary=StrengthDoseBoundaryGuardDomain.review(current,"s")
        assertEquals(StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW,
            boundary.status)
        assertEquals(2,boundary.hypertrophyExitWeek)
        assertEquals(4,boundary.strengthEntryWeek)
        assertEquals(12,boundary.hypertrophyWorkingSets)
        assertEquals(4,boundary.strengthWorkingSets)
        val report=NativeStrengthPhaseBoundaryDisplayDomain.lostDirectArmFunctions(
            original,audit(original),boundary)
        assertEquals(setOf(
            TrainingMuscle.BICEPS,TrainingMuscle.BRACHIALIS,
            TrainingMuscle.WRIST_EXTENSORS),report)
        // A deload's zero biceps work must never replace the old baseline.
        assertEquals(n("0"),audit(original).weeks[2]
            .directSets[TrainingMuscle.BICEPS])
        assertEquals(4L,original.revision)
    }

    @Test fun staleOrInconsistentCoachAuditCannotMakeArmLossClaims() {
        val original=source()
        val good=audit(original)
        val boundary=StrengthDoseBoundaryGuardDomain.review(
            structure(original),"s")
        fun inspect(report:CustomCoachAudit,dose:StrengthDoseBoundaryReview=boundary)=
            NativeStrengthPhaseBoundaryDisplayDomain.lostDirectArmFunctions(
                original,report,dose)
        assertNull(inspect(good.copy(auditedCycleRevision=5)))
        assertNull(inspect(good.copy(structuralReview=null)))
        assertNull(inspect(good.copy(weeks=good.weeks.drop(3))))
        assertNull(inspect(good.copy(weeks=good.weeks.map{
            if(it.absoluteWeek==2)it.copy(workingSets=13) else it
        })))
        assertNull(inspect(good,boundary.copy(
            status=StrengthDoseBoundaryStatus.SOURCE_UNVERIFIED)))
        assertNull(NativeStrengthPhaseBoundaryDisplayDomain.lostDirectArmFunctions(
            original,good,null))
        assertEquals(original,source())
    }
}
