package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomCycleBlockComparisonDomainTest {
    private fun slot(id:String,exercise:String,working:Int,pinned:Boolean=true)=
        CustomExerciseSlot(id,exercise,ComposerRole.PRIMARY,
            List(working){
                CustomAuthoredSet(DraftSetType.WORKING,
                    DraftSetLoad.ExplicitBodyweight,
                    RepBand(6,8),2,45,120)
            },pinned=pinned)
    private fun cycle():CustomTrainingCycle {
        val hypertrophy=CustomTrainingBlock(
            "base","Hypertrophy",TrainingPhase.HYPERTROPHY,
            CustomBlockPurpose.TRAINING,2,CustomSplitIdentity.Freeform("Upper/Lower"),
            listOf(
                CustomTrainingDay(0,"Upper",ComposerSessionPurpose.HYPERTROPHY,
                    listOf(slot("base_b","bench",10))),
                CustomTrainingDay(2,"Lower",ComposerSessionPurpose.HYPERTROPHY,
                    listOf(slot("base_s","squat",20)))
            )
        )
        val strength=CustomTrainingBlock(
            "strength","Strength",TrainingPhase.STRENGTH,
            CustomBlockPurpose.TRAINING,2,CustomSplitIdentity.Freeform("Upper/Lower"),
            listOf(
                CustomTrainingDay(0,"Upper",ComposerSessionPurpose.STRENGTH,
                    listOf(slot("str_b","bench",6))),
                CustomTrainingDay(4,"Lower",ComposerSessionPurpose.STRENGTH,
                    listOf(slot("str_d","deadlift",5,pinned=false)))
            )
        )
        return CustomTrainingCycle("my-cycle",12,"athlete-1",
            AthleteTrainingGoal.POWERBUILDING,"My actual cycle",
            listOf(hypertrophy,strength))
    }

    private fun measurement(week:Int,blockId:String,phase:TrainingPhase,
        count:Int,direct:Map<TrainingMuscle,BigDecimal> = emptyMap())=
        CustomCoachWeekMeasurement(
            week,blockId,phase,CustomBlockPurpose.TRAINING,count,
            direct,emptyMap(),emptyMap(),emptyMap(),emptyMap())
    private fun audit(c:CustomTrainingCycle)=CustomCoachAudit(
        CustomCoachAuditStatus.UNRESOLVED,
        listOf(CustomCoachIssue(
            CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
            "week.3","Continuity requires review")),
        CustomCycleReview(CustomCycleReviewStatus.READY_FOR_COACH_REVIEW,
            emptyList(),4,c),
        listOf(
            measurement(1,"base",TrainingPhase.HYPERTROPHY,28,
                mapOf(
                    TrainingMuscle.BICEPS to BigDecimal("8"),
                    TrainingMuscle.BRACHIALIS to BigDecimal("2"),
                    TrainingMuscle.WRIST_FLEXORS to BigDecimal("3"),
                    TrainingMuscle.WRIST_EXTENSORS to BigDecimal("1"))),
            measurement(2,"base",TrainingPhase.HYPERTROPHY,30),
            measurement(3,"strength",TrainingPhase.STRENGTH,14,
                mapOf(
                    TrainingMuscle.BICEPS to BigDecimal("3"),
                    TrainingMuscle.BRACHIALIS to BigDecimal("1"),
                    TrainingMuscle.WRIST_FLEXORS to BigDecimal("1"),
                    TrainingMuscle.WRIST_EXTENSORS to BigDecimal("2"))),
            measurement(4,"strength",TrainingPhase.STRENGTH,11)),
        "profile-revision",c.revision,emptyMap())

    @Test fun reportsThirtyToElevenWithoutCallingItSafeOrBetter() {
        val c=cycle()
        val result=checkNotNull(CustomCycleBlockComparisonDomain.compare(
            c,"strength"))
        assertEquals(30,result.prior.baseWorkingSets)
        assertEquals(11,result.next.baseWorkingSets)
        assertEquals(-19,result.baseWorkingSetDelta)
        assertEquals(listOf(4),result.addedWeekdays)
        assertEquals(listOf(2),result.removedWeekdays)
        assertEquals(setOf("squat","deadlift"),
            result.exerciseChanges.map{it.exerciseId}.toSet())
        assertEquals(1,result.next.pinnedVisits)
        assertNull(result.firstAuditedWeekDelta)
        assertNull(result.matchingAuditStatus)
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayApproveProgram)
        assertFalse(result.mayIssueWorkout)
        assertEquals(c,cycle())
    }

    @Test fun fullyMeasuredUnresolvedAuditIsVisibleButNeverCalledApproval() {
        val c=cycle()
        val result=checkNotNull(CustomCycleBlockComparisonDomain.compare(
            c,"strength",audit(c)))
        assertEquals(listOf(28,30),result.prior.auditedWeeklyWorkingSets)
        assertEquals(listOf(14,11),result.next.auditedWeeklyWorkingSets)
        assertEquals(-14,result.firstAuditedWeekDelta)
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,
            result.matchingAuditStatus)
        assertEquals(1,result.auditIssueCount)
        val muscleChanges=checkNotNull(result.auditedDirectMuscleChanges)
            .associateBy {it.muscle}
        assertEquals(4,muscleChanges.size)
        assertEquals(BigDecimal("-5"),
            muscleChanges.getValue(TrainingMuscle.BICEPS).delta)
        assertEquals(BigDecimal("-1"),
            muscleChanges.getValue(TrainingMuscle.BRACHIALIS).delta)
        assertEquals(BigDecimal("-2"),
            muscleChanges.getValue(TrainingMuscle.WRIST_FLEXORS).delta)
        assertEquals(BigDecimal("1"),
            muscleChanges.getValue(TrainingMuscle.WRIST_EXTENSORS).delta)
    }

    @Test fun rejectsStaleIncompleteOrMismatchedAuditMeasurements() {
        val c=cycle()
        val original=audit(c)
        val stale=original.copy(auditedCycleRevision=c.revision-1)
        val incomplete=original.copy(weeks=original.weeks.dropLast(1))
        val wrongCalendar=original.copy(weeks=original.weeks.map {week->
            if(week.absoluteWeek==3)week.copy(absoluteWeek=99) else week
        })
        val wrongDraft=original.copy(structuralReview=original.structuralReview
            ?.copy(verifiedDraft=c.copy(revision=c.revision+1)))
        for(bad in listOf(stale,incomplete,wrongCalendar,wrongDraft)) {
            val report=checkNotNull(CustomCycleBlockComparisonDomain.compare(
                c,"strength",bad))
            assertNull(report.firstAuditedWeekDelta)
            assertNull(report.next.auditedWeeklyWorkingSets)
            assertNull(report.auditedDirectMuscleChanges)
        }
        val missingTarget=CustomCycleBlockComparisonDomain.compare(
            c,"missing",original)
        val first=CustomCycleBlockComparisonDomain.compare(c,"base",original)
        assertNull(missingTarget)
        assertNull(first)
    }

    @Test fun sameMovementExposureChangesAreCountedByVisits() {
        val c=cycle()
        val original=c.blocks[1]
        val extra=original.days.first().copy(
            slots=original.days.first().slots+
                slot("str_b2","bench",2,pinned=false))
        val changed=c.copy(blocks=listOf(c.blocks[0],
            original.copy(days=listOf(extra,original.days[1]))))
        val report=checkNotNull(CustomCycleBlockComparisonDomain.compare(
            changed,"strength"))
        assertEquals(CustomBlockExposureChange("bench",1,2),
            report.exerciseChanges.single{it.exerciseId=="bench"})
        assertEquals(13,report.next.baseWorkingSets)
        assertEquals(2,report.next.exposuresByExercise.getValue("bench"))
    }
}
