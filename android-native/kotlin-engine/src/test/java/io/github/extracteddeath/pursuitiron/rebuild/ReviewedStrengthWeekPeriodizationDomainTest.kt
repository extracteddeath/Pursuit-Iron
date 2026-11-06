package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

/** Real three-week resolution from exact source-backed gym prescriptions. */
class ReviewedStrengthWeekPeriodizationDomainTest {
    private val fixture=GeneratedAdaptiveBlockFixture
    private fun baseline():CustomTrainingCycle {
        val original=fixture.cycle()
        return original.copy(blocks=original.blocks.map {block ->
            block.copy(phase=TrainingPhase.STRENGTH,
                days=block.days.mapIndexed {index,day->
                    day.copy(
                        purpose=if(index<2)
                            ComposerSessionPurpose.STRENGTH_SKILL
                            else ComposerSessionPurpose.GENERAL_PREPARATION,
                        slots=day.slots.map {slot->
                            slot.copy(
                                pinned=index<2 && slot.exerciseId=="bench",
                                sets=slot.sets.map {set->
                                    set.copy(reps=RepBand(4,6),targetRir=3,
                                        restSeconds=150)
                                })
                        })
                })
        })
    }
    private fun coaching():CustomCoachAuditInputs {
        val original=checkNotNull(fixture.generation().policy)
        val strength=original.copy(phase=TrainingPhase.STRENGTH,
            minimumPurposeDays=mapOf(
                ComposerSessionPurpose.STRENGTH_SKILL to 2,
                ComposerSessionPurpose.GENERAL_PREPARATION to 1),
            primaryLiftMinimumDays=mapOf("bench" to 2))
        return CustomCoachAuditInputs(
            policiesByPhase=mapOf(TrainingPhase.STRENGTH to strength),
            nonSetSecondsByWeekday=mapOf(
                0 to 2100,2 to 2100,4 to 2100))
    }
    private fun offered(source:CustomTrainingCycle):List<CustomWeekSlotOverride> {
        val original=source.blocks.single().days.single {
            it.weekday==0
        }.slots.single {it.exerciseId=="bench"}.sets.single()
        return listOf(
            CustomWeekSlotOverride(2,0,"old_bench",listOf(
                original.copy(reps=RepBand(4,5),targetRir=2))),
            CustomWeekSlotOverride(3,0,"old_bench",listOf(
                original.copy(reps=RepBand(3,5),targetRir=1))))
    }
    private fun permissions()=CustomCycleCoachPermissions(
        adaptWeekOverrides=true,adaptReps=true,adaptRir=true)
    private fun propose(
        source:CustomTrainingCycle=baseline(),
        targets:List<CustomWeekSlotOverride> =offered(source),
        perms:CustomCycleCoachPermissions=permissions()
    )=ReviewedStrengthWeekPeriodizationDomain.propose(
        ReviewedStrengthWeekPeriodizationRequest(
            AdaptiveCycleOptionRequest(
                source,"block-1",fixture.athlete(),fixture.movements,
                coaching(),perms,emptyList()),targets))

    @Test fun threeWeeksCarryExactDistinctVerifiedRepAndEffortPrescriptions() {
        val source=baseline()
        val sourceAudit=CustomCycleCoachAuditDomain.review(
            source,fixture.athlete(),fixture.movements,coaching())
        assertEquals(sourceAudit.issues.toString(),
            CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW,
            sourceAudit.status)
        val result=propose(source)
        assertEquals(result.explanation,
            ReviewedStrengthWeekPeriodizationStatus.READY_FOR_ATHLETE_REVIEW,
            result.status)
        assertEquals(setOf(2,3),result.coveredWeeks)
        val proposed=checkNotNull(result.option).proposedCycle
        assertEquals(source.revision+1,proposed.revision)
        assertEquals(source.blocks,proposed.blocks)
        assertEquals(listOf(9,9,9),
            result.comparison!!.evaluated.single().coachingAudit!!.weeks
                .map {it.workingSets})
        val structural=checkNotNull(
            result.comparison.evaluated.single().coachingAudit!!.structuralReview)
        fun bench(week:Int)=checkNotNull(
            CustomTrainingCycleDomain.week(structural,week))
            .days.single {it.weekday==0}.slots
            .single {it.exerciseId=="bench"}.sets.single()
        assertEquals(RepBand(4,6),bench(1).reps)
        assertEquals(3,bench(1).targetRir)
        assertEquals(RepBand(4,5),bench(2).reps)
        assertEquals(2,bench(2).targetRir)
        assertEquals(RepBand(3,5),bench(3).reps)
        assertEquals(1,bench(3).targetRir)
        assertEquals(bench(1).load,bench(2).load)
        assertEquals(bench(1).load,bench(3).load)
        assertEquals(150,bench(3).restSeconds)
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayAlterHistory)
        assertEquals(source,baseline())
        assertEquals(result,propose(source))
    }

    @Test fun overridePermissionDoesNotGrantUnreviewedRepsAndRir() {
        val withoutTargetPermissions=propose(
            perms=CustomCycleCoachPermissions(adaptWeekOverrides=true))
        assertEquals(ReviewedStrengthWeekPeriodizationStatus.PERMISSION_REQUIRED,
            withoutTargetPermissions.status)
        assertNull(withoutTargetPermissions.option)
        assertTrue(withoutTargetPermissions.comparison!!.evaluated.single()
            .transition!!.blockedKinds.containsAll(setOf(
                CustomCycleChangeKind.REPS,CustomCycleChangeKind.RIR)))
        val onlyReps=propose(perms=CustomCycleCoachPermissions(
            adaptWeekOverrides=true,adaptReps=true))
        assertEquals(ReviewedStrengthWeekPeriodizationStatus.PERMISSION_REQUIRED,
            onlyReps.status)
        assertEquals(setOf(CustomCycleChangeKind.RIR),
            onlyReps.comparison!!.evaluated.single().transition!!.blockedKinds)
    }

    @Test fun existingAuthoredWeekIsProtectedAndIndependentWeekPreserved() {
        val source=baseline()
        val prior=CustomWeekSlotOverride(1,2,"old_row",listOf(
            source.blocks.single().days.single {it.weekday==2}
                .slots.single {it.exerciseId=="row"}.sets.single().copy(
                    reps=RepBand(5,6))))
        val protected=source.copy(weekOverrides=mapOf(
            "block-1" to listOf(prior)))
        val reviewed=propose(protected,offered(protected))
        assertEquals(ReviewedStrengthWeekPeriodizationStatus.READY_FOR_ATHLETE_REVIEW,
            reviewed.status)
        assertEquals(prior,reviewed.option!!.proposedCycle
            .weekOverrides.getValue("block-1").first())
        assertEquals(3,reviewed.option.proposedCycle
            .weekOverrides.getValue("block-1").size)
        val conflict=propose(protected,offered(protected)+
            CustomWeekSlotOverride(1,2,"old_row",prior.sets))
        assertEquals(ReviewedStrengthWeekPeriodizationStatus.PROTECTED_WEEK,
            conflict.status)
        assertNull(conflict.option)
    }

    @Test fun impossibleGymLoadAndInvalidWeekFailClosed() {
        val source=baseline()
        val bad=offered(source).mapIndexed {index,step ->
            if(index>0)step else step.copy(
                sets=step.sets.map {set->
                    set.copy(load=DraftSetLoad.Verified(
                        LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                        fixture.n("27.5")))
                })
        }
        val rejected=propose(source,bad)
        assertEquals(ReviewedStrengthWeekPeriodizationStatus.COACH_AUDIT_FAILED,
            rejected.status)
        assertNull(rejected.option)
        val invalid=propose(source,offered(source).map {
            it.copy(weekInBlock=5)
        })
        assertEquals(ReviewedStrengthWeekPeriodizationStatus.INVALID_REQUEST,
            invalid.status)
        assertNull(invalid.option)
    }
}
