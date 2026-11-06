package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class AdaptiveScheduleCalendarSearchTest {
    private val f=GeneratedAdaptiveBlockFixture
    private fun n(x:String)=BigDecimal(x)
    private fun cycle()=f.cycle().let {original ->
        original.copy(blocks=listOf(original.blocks.single(),
            original.blocks.single().copy(
                blockId="block-2",label="Future block",weeks=2)))
    }
    private fun base()=AdaptiveBlockGenerationRequest(
        cycle(),"block-2",f.athlete(),f.movements,
        CustomCoachAuditInputs(
            policiesByPhase=mapOf(TrainingPhase.HYPERTROPHY to
                checkNotNull(f.generation().policy)),
            nonSetSecondsByWeekday=mapOf(0 to 2100,2 to 2100,4 to 2100),
            continuityPolicy=CustomCoachVolumeContinuityPolicy(
                n("0.5"),n("0.5"),n("0.5"),n("0.5"))),
        CustomCycleCoachPermissions(adaptSplit=true,adaptDayCount=true,
            adaptSchedule=true,adaptExercises=true,adaptSetCount=true,
            adaptLoads=true,adaptReps=true,adaptRir=true,adaptRest=true),
        emptyList())
    private fun template()=AdaptiveScheduleBlueprintRequest(
        base(),f.generation(),emptyList())
    private fun offer(split:ComposerSplit,count:Int):AdaptiveCalendarPolicyOffer {
        val original=checkNotNull(f.generation().policy)
        val dose=original.selectedBounds.map {bound ->
            bound.copy(minimumDirectDays=
                bound.minimumDirectDays.coerceAtMost(count))
        }
        return AdaptiveCalendarPolicyOffer(
            original.copy(
                revision="policy_"+split.name+"_"+count,
                split=split,trainingDays=count,
                baselineBounds=dose,selectedBounds=dose,
                minimumPurposeDays=mapOf(
                    ComposerSessionPurpose.HYPERTROPHY to count)),
            f.generation().choices,f.policies())
    }
    private fun input(vararg policies:AdaptiveCalendarPolicyOffer)=
        AdaptiveCalendarSearchRequest(template(),setOf(0,1,2,4),
            mapOf(0 to 2100,1 to 2100,2 to 2100,4 to 2100),
            policies.toList())

    @Test fun enumeratesThreeAndFourDayFullProgramsWithIndependentReviews() {
        val request=input(offer(ComposerSplit.FULL_BODY,3),
            offer(ComposerSplit.UPPER_LOWER,4))
        val result=AdaptiveScheduleCalendarSearchDomain.search(request)
        assertEquals(4,result.blueprintsGenerated)
        assertEquals(4,result.calendarsConsidered)
        assertFalse(result.searchTruncated)
        assertEquals(4,result.result.outcomes.size)
        val future=result.result.outcomes.single {it.optionId=="auto_1_0"}
        assertNull(future.refusal)
        assertEquals(future.generated?.explanation,
            GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW,
            future.generated?.status)
        val assessment=result.result.comparison.evaluated.single {
            it.optionId=="auto_1_0"
        }
        assertEquals(assessment.explanation,
            AdaptiveCycleOptionStatus.PROSPECTIVE_REVIEW_REQUIRED,
            assessment.status)
        assertNotNull(assessment.qualityScore)
        assertEquals("auto_1_0",
            result.result.comparison.recommendedProspectiveOptionId)
        assertEquals(4,checkNotNull(future.generated?.proposedOption)
            .proposedCycle.blocks.last().days.size)
        assertEquals(setOf(0,2,4),
            request.blueprintTemplate.base.approvedCycle.blocks.first()
                .days.map {it.weekday}.toSet())
        assertEquals(result,
            AdaptiveScheduleCalendarSearchDomain.search(request))
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun unsupportedSplitFrequencyHasNoFabricatedFocusOrProgram() {
        val result=AdaptiveScheduleCalendarSearchDomain.search(
            input(offer(ComposerSplit.ULPPL,3)))
        assertEquals(0,result.blueprintsGenerated)
        assertTrue(result.result.outcomes.isEmpty())
        assertEquals(1,result.result.comparison.candidatesChecked)
        assertEquals("keep_current",result.result.recommendedOptionId)
    }

    @Test fun morePoliciesCannotStarveTheFourDayOptionOrEscapeBudget() {
        val offers=listOf(offer(ComposerSplit.FULL_BODY,3),
            offer(ComposerSplit.UPPER_LOWER,4))+
            List(10) {offer(ComposerSplit.FULL_BODY,3)}
        val result=AdaptiveScheduleCalendarSearchDomain.search(
            input(*offers.toTypedArray()))
        assertEquals(7,result.blueprintsGenerated)
        assertTrue(result.searchTruncated)
        assertTrue(result.result.outcomes.any {it.optionId=="auto_1_0"})
        assertTrue(result.result.comparison.candidatesChecked<=8)
        assertTrue(result.result.outcomes.all {
            it.generated==null || !it.generated.mayIssueWorkout
        })
    }

    @Test fun availabilityAndMissingWorkoutTimeMustBeExplicit() {
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveScheduleCalendarSearchDomain.search(
                input(offer(ComposerSplit.FULL_BODY,3)).copy(
                    availableFutureWeekdays=setOf(0,1,2,3,4)))
        }
        assertThrows(IllegalArgumentException::class.java) {
            AdaptiveScheduleCalendarSearchDomain.search(
                input(offer(ComposerSplit.FULL_BODY,3)).copy(
                    availableFutureWeekdays=setOf(0,2)))
        }
        assertEquals(3,cycle().blocks.first().days.size)
    }
}
