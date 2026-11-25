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

    @Test fun namedReferenceAcceptanceRecomputesEveryDraftByteAndRefusesStaleInputs() {
        val displayed=CustomPercentageDraftDomain.review(source,request)
            as CustomPercentageDraftResult.Proposed
        fun check(d:CustomPercentageDraftResult.Proposed=displayed,
            s:CustomPercentageDraftSource=source,r:NamedPercentageRequest=request,
            history:Long=9,accepted:Boolean=true)=CustomPercentageDraftRecheckDomain.recheck(
                d,request,s,r,9,history,accepted)
        val confirmed=check() as CustomPercentageRecheckResult.Reconfirmed
        assertEquals(displayed,confirmed.proposal)
        assertEquals(9L,confirmed.historyRevision)
        assertFalse(confirmed.maySaveProgram)
        assertFalse(confirmed.mayIssueWorkout)
        assertEquals(CustomPercentageRecheckResult.Refused(
            CustomPercentageRecheckRefusal.NOT_EXPLICITLY_ACCEPTED),check(accepted=false))
        assertEquals(CustomPercentageRecheckResult.Refused(
            CustomPercentageRecheckRefusal.HISTORY_CHANGED),check(history=10))
        assertEquals(CustomPercentageRecheckResult.Refused(
            CustomPercentageRecheckRefusal.REFERENCE_OR_STOCK_CHANGED),
            check(r=request.copy(confirmedReference=n("205"))))
        assertEquals(CustomPercentageRecheckResult.Refused(
            CustomPercentageRecheckRefusal.REFERENCE_OR_STOCK_CHANGED),
            check(r=request.copy(verifiedStock=VerifiedLoadStock.Stack(
                LoadUnit.LB,LoadBasis.MACHINE_STACK,n("5"),n("10"),n("300")))))
        assertEquals(CustomPercentageRecheckResult.Refused(
            CustomPercentageRecheckRefusal.SOURCE_OR_RECIPE_CHANGED),
            check(s=source.copy(stamp=stamp.copy(gymCatalogSourceRevision=3))))
        assertEquals(CustomPercentageRecheckResult.Refused(
            CustomPercentageRecheckRefusal.SOURCE_OR_RECIPE_CHANGED),
            check(r=request.copy(weekInBlock=2)))
        val forged=displayed.copy(proposal=displayed.proposal.copy(
            draft=displayed.proposal.draft.copy(weekOverrides=emptyMap())))
        assertEquals(CustomPercentageRecheckResult.Refused(
            CustomPercentageRecheckRefusal.PROPOSAL_CHANGED),check(d=forged))
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
    private fun exposure(id:String,order:Long,stage:Int=0,weight:String="100",failed:Boolean=false,
        sourceSlotId:String?="press-slot"):TieredLinearExposure {
        val reps=listOf(3,2,1)[stage]
        return TieredLinearExposure(id,order,scope,PercentageTier.PRIMARY,stage,
            List(3){i->TieredLinearSetTarget(i,n(weight),reps,i==2)},
            List(3){i->if(failed && i==2)
                WorkingSetFact(i,SetOutcome.FAILED_ATTEMPT,n(weight),0,null,n(weight),2)
                else WorkingSetFact(i,SetOutcome.COMPLETED,n(weight),reps,null,n(weight),2)},
            true,sourceSlotId)
    }
    private fun tiered(history:List<TieredLinearExposure> = emptyList())=
        TieredLinearRequest("next",scope,PercentageTier.PRIMARY,TieredLinearState(0,n("100")),
            true,history,stock,n("5"),3,sourceSlotId="press-slot")

    @Test fun tierReplayDraftPreservesStableSourceOrdinalsAndIgnoresOtherSameDaySlot() {
        val other=exposure("other",2,sourceSlotId="other-slot")
        val result=CustomPercentageDraftDomain.reviewTiered(source,tiered(listOf(exposure("a",1),other)))
            as CustomPercentageDraftResult.Proposed
        val sets=result.proposal.draft.weekOverrides.getValue("strength").single().sets.drop(1)
        assertEquals(List(3){n("105")},sets.map{(it.load as DraftSetLoad.Verified).value})
        assertEquals(List(3){3},sets.map{it.exactRepTarget})
        assertEquals(listOf(1,3,5),sets.map{it.percentageIntent!!.sourceOrdinal})
        assertTrue(sets.all{it.percentageIntent!!.sourceSetCount==5 && it.percentageIntent!!.tierStage==0})
        assertEquals(listOf(false,false,true),sets.map{it.percentageIntent!!.repOut})
        assertEquals(listOf("a"),result.tierTransitions.map{it.workoutId})
        assertFalse(result.requiresReviewedReset)
        assertFalse(result.mayIssueWorkout)
    }

    @Test fun twoTierSlotsInOneWorkoutHaveSeparateStatesButOneJournalIdentity() {
        val rootA=tiered().copy(nextWorkoutId="week-1-day-0")
        val rootB=rootA.copy(sourceSlotId="other-slot",initialState=TieredLinearState(0,n("120")))
        val nextA=rootA.copy(nextWorkoutId="week-2-day-0",completed=listOf(
            exposure(rootA.nextWorkoutId,1)))
        val nextB=rootB.copy(nextWorkoutId=nextA.nextWorkoutId,completed=listOf(
            exposure(rootB.nextWorkoutId,1,weight="120",failed=true,sourceSlotId="other-slot")))
        val nextOtherDay=rootA.copy(scope=scope.copy(trainingDayId="strength:d2"),
            nextWorkoutId="week-1-day-2")
        fun key(week:Int,slot:String,day:Int=0)=CustomNamedPercentageWeekKey("strength",week,day,slot)
        fun apply(current:CustomTrainingCycle,k:CustomNamedPercentageWeekKey,r:TieredLinearRequest):CustomTrainingCycle =
            (CustomPercentageDraftDomain.reviewTiered(CustomPercentageDraftSource(current,
                stamp.copy(cycleRevision=current.revision),k.blockId,k.weekInBlock,k.weekday,k.slotId),r)
                as CustomPercentageDraftResult.Proposed).proposal.draft
        val roots=listOf(CustomTieredInitialWeekEvidence(key(1,"press-slot"),rootA),
            CustomTieredInitialWeekEvidence(key(1,"other-slot"),rootB),
            CustomTieredInitialWeekEvidence(key(1,"press-slot",2),nextOtherDay))
        val later=listOf(CustomTieredTransitionWeekEvidence(key(2,"press-slot"),nextA),
            CustomTieredTransitionWeekEvidence(key(2,"other-slot"),nextB))
        fun project(base:CustomTrainingCycle,k:CustomNamedPercentageWeekKey,r:TieredLinearRequest)=
            CustomPercentageDraftDomain.reviewTiered(CustomPercentageDraftSource(base,
                stamp.copy(cycleRevision=base.revision),k.blockId,k.weekInBlock,k.weekday,k.slotId),r)
                as CustomPercentageDraftResult.Proposed
        val rootEdits=roots.map{project(cycle,it.key,it.request)}
        val initial=checkNotNull(CustomPercentageDraftDomain.combineReviewedWeekEdits(cycle,rootEdits))
        assertEquals(cycle.revision+1,initial.revision)
        assertEquals(roots.fold(cycle){draft,p->apply(draft,p.key,p.request)}.weekOverrides,initial.weekOverrides)
        val dayEdits=later.map{project(initial,it.key,it.request)}
        val candidate=checkNotNull(CustomPercentageDraftDomain.combineReviewedWeekEdits(initial,dayEdits))
        assertEquals(initial.revision+1,candidate.revision)
        assertEquals(later.fold(initial){draft,p->apply(draft,p.key,p.request)}.weekOverrides,candidate.weekOverrides)
        assertNull(CustomPercentageDraftDomain.combineReviewedWeekEdits(initial,listOf(dayEdits.first(),dayEdits.first())))
        assertNull(CustomPercentageDraftDomain.combineReviewedWeekEdits(initial,listOf(dayEdits.first(),rootEdits.first())))
        assertNull(CustomPercentageDraftDomain.combineReviewedWeekEdits(initial,emptyList()))
        assertNull(CustomPercentageDraftDomain.combineReviewedWeekEdits(initial,listOf(dayEdits.first().copy(
            proposal=dayEdits.first().proposal.copy(draft=dayEdits.first().proposal.draft.copy(title="Unrelated change"))))))
        assertEquals(cycle.blocks,candidate.blocks)
        fun audit(initialTiers:List<CustomTieredInitialWeekEvidence> = roots,
            transitions:List<CustomTieredTransitionWeekEvidence> = later)=
            CustomNamedPercentageCycleAuditDomain.reviewWithTierHistory(candidate,
                stamp.copy(cycleRevision=candidate.revision),emptyList(),initialTiers,transitions)
        assertEquals(5,(audit() as CustomNamedPercentageCycleAudit.Verified).reviewedWeeks)
        val numericA=TieredLinearProgressionDomain.review(nextA) as TieredLinearResult.Candidate
        val numericB=TieredLinearProgressionDomain.review(nextB) as TieredLinearResult.Candidate
        assertEquals(TieredLinearState(0,n("105")),numericA.state)
        assertEquals(TieredLinearState(1,n("120")),numericB.state)
        assertEquals(CustomNamedPercentageCycleAudit.Refused(CustomNamedPercentageCycleRefusal.TIER_HISTORY_REQUIRED),
            audit(initialTiers=roots.mapIndexed{i,p->if(i==2)
                p.copy(request=p.request.copy(nextWorkoutId=rootA.nextWorkoutId)) else p}))
        assertEquals(CustomNamedPercentageCycleAudit.Refused(CustomNamedPercentageCycleRefusal.TIER_HISTORY_REQUIRED),
            audit(transitions=later.mapIndexed{i,p->if(i==1)
                p.copy(request=p.request.copy(nextWorkoutId="different-journal")) else p}))
        assertTrue(audit(transitions=listOf(later.first(),later.last().copy(request=nextB.copy(completed=nextA.completed))))
            is CustomNamedPercentageCycleAudit.Refused)
        assertTrue(audit(initialTiers=roots.dropLast(1)) is CustomNamedPercentageCycleAudit.Refused)
    }

    @Test fun tierResetIsExplicitReviewAndMissingSlotHistoryFailsClosed() {
        val history=listOf(exposure("a",1,failed=true),exposure("b",2,1,failed=true),
            exposure("c",3,2,failed=true))
        val result=CustomPercentageDraftDomain.reviewTiered(source,tiered(history))
            as CustomPercentageDraftResult.Proposed
        assertTrue(result.requiresReviewedReset)
        assertEquals(TieredLinearDecision.RESET_REVIEW,result.tierTransitions.last().decision)
        val sets=result.proposal.draft.weekOverrides.getValue("strength").single().sets.drop(1)
        assertTrue(sets.all{(it.load as DraftSetLoad.Verified).value==n("85")})
        assertTrue(sets.all{it.percentageIntent!!.tierStage==0 && it.percentageIntent!!.referenceLoad==n("85")})
        assertFalse(result.maySaveProgram)
        assertEquals(CustomPercentageDraftResult.Refused(CustomPercentageDraftRefusal.PROTOCOL_REFUSED),
            CustomPercentageDraftDomain.reviewTiered(source,tiered(listOf(exposure("unknown",1,sourceSlotId=null)))))
        assertEquals(CustomPercentageDraftResult.Refused(CustomPercentageDraftRefusal.SCOPE_MISMATCH),
            CustomPercentageDraftDomain.reviewTiered(source,tiered().copy(sourceSlotId="other-slot")))
    }

    @Test fun tierRecoveryKeepsReferenceSeparateFromActualLightWeight() {
        val result=CustomPercentageDraftDomain.reviewTiered(source,
            tiered(listOf(exposure("a",1))).copy(recovery=true)) as CustomPercentageDraftResult.Proposed
        val sets=result.proposal.draft.weekOverrides.getValue("strength").single().sets.drop(1)
        assertTrue(sets.all{(it.load as DraftSetLoad.Verified).value==n("60")})
        assertTrue(sets.all{it.percentageIntent!!.referenceLoad==n("105") &&
            it.percentageIntent!!.effectivePercent==n("60") &&
            it.percentageIntent!!.recovery && !it.percentageIntent!!.repOut})
    }

}
