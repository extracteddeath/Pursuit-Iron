package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomPercentageDraftDomainTest {
    private fun n(v:String)=BigDecimal(v)
    private fun set(weight:String,type:DraftSetType=DraftSetType.WORKING)=
        CustomAuthoredSet(type,DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n(weight)),
            RepBand(5,8),2,45,120)
    private val slot=CustomExerciseSlot("press-slot","press",ComposerRole.PRIMARY,
        listOf(set("50",DraftSetType.WARMUP),set("100"),set("100"),set("100")),
        machineInstanceId="machine-A",progressionMethod=SavedProgressionMethod.DOUBLE)
    private val day=CustomTrainingDay(0,"Upper A",ComposerSessionPurpose.STRENGTH_SKILL,
        listOf(slot,slot.copy(slotId="other-slot")))
    private val block=CustomTrainingBlock("strength","Strength",TrainingPhase.STRENGTH,
        CustomBlockPurpose.TRAINING,4,CustomSplitIdentity.Freeform("Upper"),
        listOf(day,day.copy(weekday=2)))
    private val cycle=CustomTrainingCycle("cycle",3,"athlete",AthleteTrainingGoal.STRENGTH,
        "Plan",listOf(block))
    private val stamp=CustomWorkoutSourceStamp("athlete","profile-1","cycle",3,"gym",2)
    private val source=CustomPercentageDraftSource(cycle,stamp,"strength",3,0,"press-slot")
    private val scope=BasicProgressionScope("athlete","cycle","gym","strength","strength:d0",
        "press","machine-A",LoadUnit.LB,LoadBasis.MACHINE_STACK)
    private val stock=VerifiedLoadStock.Stack(LoadUnit.LB,LoadBasis.MACHINE_STACK,n("5"),n("5"),n("300"))
    private val request=NamedPercentageRequest(scope,NamedPercentageScheme.FIVE_THREE_ONE,
        PercentageTier.PRIMARY,PercentageDayRole.STANDARD,PercentageReferenceKind.TRAINING_MAX,
        n("200.00"),true,3,4,3,stock)

    @Test fun namedRecipeCreatesExactOneWeekOverrideWithoutChangingBaseOrOtherSlots() {
        val result=CustomPercentageDraftDomain.review(source,request) as CustomPercentageDraftResult.Proposed
        val draft=result.proposal.draft
        assertEquals(4L,draft.revision)
        assertEquals(cycle.blocks,draft.blocks)
        assertEquals(cycle,result.proposal.receipt.original)
        val edit=draft.weekOverrides.getValue("strength").single()
        assertEquals(3,edit.weekInBlock)
        assertEquals(0,edit.weekday)
        assertEquals("press-slot",edit.slotId)
        assertEquals(slot.sets.first(),edit.sets.first())
        val work=edit.sets.drop(1)
        assertEquals(listOf("150","170","190").map(::n),work.map{(it.load as DraftSetLoad.Verified).value})
        assertEquals(listOf(5,3,1),work.map{it.exactRepTarget})
        assertEquals(listOf(false,false,true),work.map{it.percentageIntent!!.repOut})
        assertTrue(work.all{it.percentageIntent!!.validForSet(it)})
        assertTrue(work.all{it.percentageIntent!!.referenceLoad==n("200.00")})
        assertEquals(slot.sets,result.originalSets)
        assertFalse(result.maySaveProgram)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayModifyHistory)
        val plan=BasicProgramProgressionPlanDomain.review(draft) as ProgramProgressionPlan.Ready
        assertEquals(ProgramProgressionRoute.SPECIAL_LOAD_SCHEME,plan.assignments.single{
            it.key==result.key}.route)
        assertEquals(ProgramProgressionRoute.BASIC_WEIGHTED,plan.assignments.single{
            it.key==ProgramProgressionSlotKey("strength",0,"other-slot")}.route)
        assertTrue(BasicProgramProgressionPlanDomain.review(draft,
            selectedPerSetDynamic=setOf(result.key)) is ProgramProgressionPlan.Refused)
    }

    @Test fun staleScopeAndUnconfirmedReferenceCannotCreateDraft() {
        fun refused(s:CustomPercentageDraftSource=source,r:NamedPercentageRequest=request,
                    reason:CustomPercentageDraftRefusal) {
            assertEquals(CustomPercentageDraftResult.Refused(reason),CustomPercentageDraftDomain.review(s,r))
        }
        refused(s=source.copy(stamp=stamp.copy(cycleRevision=2)),
            reason=CustomPercentageDraftRefusal.STALE_OR_INVALID_SOURCE)
        for(wrong in listOf(scope.copy(gymId="other"),scope.copy(machineInstanceId="machine-B"),
            scope.copy(trainingDayId="strength:d2"),scope.copy(exerciseId="other"))) {
            refused(r=request.copy(scope=wrong),reason=CustomPercentageDraftRefusal.SCOPE_MISMATCH)
        }
        refused(r=request.copy(referenceConfirmed=false),reason=CustomPercentageDraftRefusal.PROTOCOL_REFUSED)
        refused(r=request.copy(approvedSetBudget=2),reason=CustomPercentageDraftRefusal.BUDGET_MISMATCH)
        refused(r=request.copy(weekInBlock=2),reason=CustomPercentageDraftRefusal.STALE_OR_INVALID_SOURCE)
    }

    @Test fun previouslyAuthoredWeekRemainsProtected() {
        val original=CustomWeekSlotOverride(3,0,"press-slot",slot.sets)
        val edited=cycle.copy(weekOverrides=mapOf("strength" to listOf(original)))
        assertEquals(CustomPercentageDraftResult.Refused(CustomPercentageDraftRefusal.PROTECTED_WEEK),
            CustomPercentageDraftDomain.review(source.copy(cycle=edited),request))
        assertEquals(original,edited.weekOverrides.getValue("strength").single())
    }
}
