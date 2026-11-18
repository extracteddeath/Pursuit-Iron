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
                CustomTrainingDay(0,"Upper",ComposerSessionPurpose.STRENGTH_SKILL,
                    listOf(slot("str_b","bench",6))),
                CustomTrainingDay(4,"Lower",ComposerSessionPurpose.STRENGTH_SKILL,
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
            // Later-week authored/override dose is the real phase exit.
            measurement(2,"base",TrainingPhase.HYPERTROPHY,30,
                mapOf(
                    TrainingMuscle.BICEPS to BigDecimal("9"),
                    TrainingMuscle.BRACHIALIS to BigDecimal("4"),
                    TrainingMuscle.WRIST_FLEXORS to BigDecimal("5"),
                    TrainingMuscle.WRIST_EXTENSORS to BigDecimal("3"))),
            measurement(3,"strength",TrainingPhase.STRENGTH,14,
                mapOf(
                    TrainingMuscle.BICEPS to BigDecimal("3"),
                    TrainingMuscle.BRACHIALIS to BigDecimal("1"),
                    TrainingMuscle.WRIST_FLEXORS to BigDecimal("1"),
                    TrainingMuscle.WRIST_EXTENSORS to BigDecimal("2"))),
            measurement(4,"strength",TrainingPhase.STRENGTH,11)),
        "profile-revision",c.revision,emptyMap())

    /** No scientific rule requires eight movements. But reducing an
     * established same-split Upper from eight to three warrants a truthful
     * movement-coverage review, independent of working-set total or RIR. */
    private fun highVarietyCycle(nextUpperCount:Int=3):CustomTrainingCycle {
        val c=cycle()
        val hyper=c.blocks.first().copy(days=c.blocks.first().days.map {day->
            if(day.weekday!=0)day else day.copy(slots=
                day.slots+ (1..7).map{i->
                    slot("hyper-extra-$i","upper-accessory-$i",2,false)
                })
        })
        val strength=c.blocks[1].copy(days=c.blocks[1].days.map{day->
            if(day.weekday==0)day.copy(slots=
                day.slots + (1 until nextUpperCount).map{i->
                    slot("strength-extra-$i","upper-accessory-$i",2,false)
                })
            else day.copy(weekday=2)
        })
        return c.copy(blocks=listOf(hyper,strength))
    }

    @Test fun eightToThreeSameSplitStrengthUpperIsFlaggedByExerciseNotSetCount() {
        val c=highVarietyCycle()
        val result=checkNotNull(CustomCycleBlockComparisonDomain.compare(
            c,"strength"))
        assertTrue(result.strengthExerciseCollapseReviewRequired)
        assertFalse(result.exerciseRosterUsesReviewedWeeks)
        assertEquals(8,result.strengthDayExerciseChanges.single{
            it.weekday==0}.priorExerciseCount)
        assertEquals(3,result.strengthDayExerciseChanges.single{
            it.weekday==0}.nextExerciseCount)
        assertEquals(5,result.strengthDayExerciseChanges.single{
            it.weekday==0}.droppedExerciseIds.size)
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayIssueWorkout)
        assertEquals(1,cycle().blocks.first().days.first().slots.size)
    }

    @Test fun measuredRostersStillRequireSourceMatchedAuditAndNoFakePeakFloor() {
        val c=highVarietyCycle()
        val reviewed=checkNotNull(CustomCycleBlockComparisonDomain.compare(
            c,"strength",audit(c)))
        assertTrue(reviewed.exerciseRosterUsesReviewedWeeks)
        assertTrue(reviewed.strengthExerciseCollapseReviewRequired)
        assertEquals(8,reviewed.strengthDayExerciseChanges.first().priorExerciseCount)
        assertEquals(3,reviewed.strengthDayExerciseChanges.first().nextExerciseCount)
        val half=highVarietyCycle(nextUpperCount=4)
        assertFalse(checkNotNull(CustomCycleBlockComparisonDomain.compare(
            half,"strength")).strengthExerciseCollapseReviewRequired)
        val changedSplit=c.copy(blocks=listOf(c.blocks.first(),
            c.blocks[1].copy(split=CustomSplitIdentity.Freeform("PPL"))))
        assertFalse(checkNotNull(CustomCycleBlockComparisonDomain.compare(
            changedSplit,"strength")).strengthExerciseCollapseReviewRequired)
        val peak=c.copy(blocks=listOf(c.blocks.first(),
            c.blocks[1].copy(phase=TrainingPhase.PEAK)))
        assertFalse(checkNotNull(CustomCycleBlockComparisonDomain.compare(
            peak,"strength")).strengthExerciseCollapseReviewRequired)
        // If the week's scheduled days change, don't equate Monday of one
        // split with an unrelated later training day by position.
        assertFalse(checkNotNull(CustomCycleBlockComparisonDomain.compare(
            cycle(),"strength")).strengthExerciseCollapseReviewRequired)
    }

    @Test fun reportsThirtyToElevenWithoutCallingItSafeOrBetter() {
        val c=cycle()
        val result=checkNotNull(CustomCycleBlockComparisonDomain.compare(
            c,"strength"))
        assertEquals(30,result.prior.baseWorkingSets)
        assertEquals(11,result.next.baseWorkingSets)
        assertEquals(-19,result.baseWorkingSetDelta)
        assertTrue(result.strengthHalfVolumeReviewRequired)
        assertFalse(result.strengthHalfVolumeUsesAuditedWeeks)
        assertNull(result.boundaryRetentionPercent)
        assertNull(result.lowestNextWeekRetentionPercent)
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
        // The previous EXIT is 30, not week-one's 28.
        assertEquals(-16,result.boundaryWorkingSetDelta)
        assertEquals(-16,result.firstAuditedWeekDelta)
        assertEquals(47,result.boundaryRetentionPercent)
        assertEquals(37,result.lowestNextWeekRetentionPercent)
        assertTrue(result.strengthHalfVolumeReviewRequired)
        assertTrue(result.strengthHalfVolumeUsesAuditedWeeks)
        assertEquals(CustomCoachAuditStatus.UNRESOLVED,
            result.matchingAuditStatus)
        assertEquals(1,result.auditIssueCount)
        val muscleChanges=checkNotNull(result.auditedDirectMuscleChanges)
            .associateBy {it.muscle}
        assertEquals(4,muscleChanges.size)
        assertEquals(BigDecimal("-6"),
            muscleChanges.getValue(TrainingMuscle.BICEPS).delta)
        assertEquals(BigDecimal("-3"),
            muscleChanges.getValue(TrainingMuscle.BRACHIALIS).delta)
        assertEquals(BigDecimal("-4"),
            muscleChanges.getValue(TrainingMuscle.WRIST_FLEXORS).delta)
        assertEquals(BigDecimal("-1"),
            muscleChanges.getValue(TrainingMuscle.WRIST_EXTENSORS).delta)
    }

    @Test fun newlyTrackedHipWorkDoesNotFabricateAnEarlierZeroBaseline() {
        val c=cycle()
        val original=audit(c)
        val changed=original.copy(weeks=original.weeks.map{week->
            if(week.absoluteWeek==3)week.copy(directSets=week.directSets+
                (TrainingMuscle.HIP_ADDUCTORS to BigDecimal("4"))) else week
        })
        val result=checkNotNull(CustomCycleBlockComparisonDomain.compare(c,"strength",changed))
        assertEquals(setOf(TrainingMuscle.HIP_ADDUCTORS),result.newlyTrackedMuscles)
        assertEquals(emptySet<TrainingMuscle>(),result.noLongerTrackedMuscles)
        assertTrue(checkNotNull(result.auditedDirectMuscleChanges).none{
            it.muscle==TrainingMuscle.HIP_ADDUCTORS})
        assertFalse(result.mayApproveProgram)
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
            assertNull(report.boundaryRetentionPercent)
            assertNull(report.lowestNextWeekRetentionPercent)
            assertFalse(report.strengthHalfVolumeUsesAuditedWeeks)
            assertTrue(report.strengthHalfVolumeReviewRequired)
            assertNull(report.next.auditedWeeklyWorkingSets)
            assertNull(report.auditedDirectMuscleChanges)
        }
        val missingTarget=CustomCycleBlockComparisonDomain.compare(
            c,"missing",original)
        val first=CustomCycleBlockComparisonDomain.compare(c,"base",original)
        assertNull(missingTarget)
        assertNull(first)
    }

    @Test fun aLaterStrengthWeekCliffIsNotHiddenByItsSafeEntryWeek() {
        val c=cycle()
        val first=audit(c)
        val edited=first.copy(weeks=first.weeks.map {week->
            if(week.absoluteWeek==3)week.copy(workingSets=20) else week
        })
        val result=checkNotNull(CustomCycleBlockComparisonDomain.compare(c,"strength",edited))
        assertEquals(67,result.boundaryRetentionPercent)
        assertEquals(37,result.lowestNextWeekRetentionPercent)
        assertEquals(-10,result.boundaryWorkingSetDelta)
        assertTrue(result.strengthHalfVolumeReviewRequired)
        assertTrue(result.strengthHalfVolumeUsesAuditedWeeks)
    }

    @Test fun anExplicitPeakBlockIsNotMisreportedAsStrengthVolumeCliff() {
        val c=cycle()
        val changed=c.copy(blocks=listOf(c.blocks[0],
            c.blocks[1].copy(phase=TrainingPhase.PEAK)))
        val result=checkNotNull(CustomCycleBlockComparisonDomain.compare(changed,"strength"))
        assertFalse(result.strengthHalfVolumeReviewRequired)
    }

    @Test fun smallWeeksDoNotReceiveAnInventedUniversalVolumeFloor() {
        val c=cycle()
        val lowBase=c.blocks[0].copy(days=c.blocks[0].days.map {day ->
            day.copy(slots=day.slots.map {slot ->
                slot.copy(sets=slot.sets.take(4))
            })
        })
        val changed=c.copy(blocks=listOf(lowBase,c.blocks[1]))
        val result=checkNotNull(CustomCycleBlockComparisonDomain.compare(changed,"strength"))
        assertEquals(8,result.prior.baseWorkingSets)
        assertFalse(result.strengthHalfVolumeReviewRequired)
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
