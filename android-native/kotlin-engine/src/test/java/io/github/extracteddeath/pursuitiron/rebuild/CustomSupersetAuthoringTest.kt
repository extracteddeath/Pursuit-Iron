package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomSupersetAuthoringTest {
    private val set=CustomAuthoredSet(DraftSetType.WORKING,
        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,BigDecimal("185")),
        RepBand(6,10),2,45,120,CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,0,
            DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.SMITH_TOTAL,BigDecimal("135"))))
    private fun initial():CustomTrainingCycle {
        val slots=listOf("a","b","c","d").map {id->
            CustomExerciseSlot(id,"same-exercise",ComposerRole.ACCESSORY,listOf(set,set),
                attachmentId="handle",pinned=true,coachNote="Keep this cue",machineInstanceId="A")
        }
        return CustomTrainingCycle("cycle",1,"athlete",AthleteTrainingGoal.HYPERTROPHY,"Custom",
            listOf(CustomTrainingBlock("base","Base",TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,
                4,CustomSplitIdentity.Freeform("Custom"),listOf(CustomTrainingDay(0,"Upper",
                    ComposerSessionPurpose.HYPERTROPHY,slots)))),mapOf("base" to listOf(
                CustomWeekSlotOverride(3,0,"a",listOf(set.copy(reps=RepBand(8,12)))))))
    }
    private fun change(draft:CustomTrainingCycle,ids:List<String>,group:String="pair",allowed:Boolean=true)=
        CustomCycleBuilderDomain.setSupersetMembers(draft,draft.revision,"base",0,group,ids,allowed)
    private fun accepted(result:CustomCycleBuilderResult)=(result as CustomCycleBuilderResult.Proposed)
    private fun slots(draft:CustomTrainingCycle)=draft.blocks.single().days.single().slots
    @Test fun createsPairWithoutChangingAnySetsLoadsTechniquesNotesVariantsOrWeekOverrides() {
        val original=initial()
        val edited=accepted(change(original,listOf("b","a")))
        assertEquals(2L,edited.draft.revision)
        assertEquals(original.weekOverrides,edited.draft.weekOverrides)
        assertEquals(slots(original).map{if(it.slotId in setOf("a","b"))it.copy(supersetGroup="pair") else it},slots(edited.draft))
        assertTrue(slots(original).all{it.supersetGroup==null})
        val undone=accepted(CustomCycleBuilderDomain.undo(edited.draft,edited.receipt)).draft
        assertEquals(original.copy(revision=3),undone)
    }
    @Test fun membershipEditReplacesTheWholeGroupAndUnlinksRemovedMembersAtomically() {
        val triple=accepted(change(initial(),listOf("a","b","c"))).draft
        val revised=accepted(change(triple,listOf("a","d"))).draft
        assertEquals(listOf("pair",null,null,"pair"),slots(revised).map{it.supersetGroup})
        assertEquals(slots(triple).map{it.sets},slots(revised).map{it.sets})
    }
    @Test fun cannotStealOneMemberFromAnotherGroupOrLeaveItsPartnerOrphaned() {
        val first=accepted(change(initial(),listOf("a","b"))).draft
        val second=accepted(change(first,listOf("c","d"),"other")).draft
        assertTrue(change(second,listOf("a","c")) is CustomCycleBuilderResult.Rejected)
        assertEquals(listOf("pair","pair","other","other"),slots(second).map{it.supersetGroup})
    }
    @Test fun disabledSupersetsRefuseNewMembershipButAllowFullUnlink() {
        val original=initial()
        assertTrue(change(original,listOf("a","b"),allowed=false) is CustomCycleBuilderResult.Rejected)
        val grouped=accepted(change(original,listOf("a","b"))).draft
        val cleared=accepted(change(grouped,emptyList(),allowed=false)).draft
        assertTrue(slots(cleared).all{it.supersetGroup==null})
        assertEquals(original.copy(revision=3),cleared)
    }
    @Test fun duplicateMissingTooFewTooManyAndInvalidIdentifiersAreRejected() {
        for(ids in listOf(emptyList(),listOf("a"),listOf("a","a"),listOf("a","missing"),listOf("a","b","c","d")))
            assertTrue(change(initial(),ids) is CustomCycleBuilderResult.Rejected)
        assertTrue(change(initial(),listOf("a","b"),"bad/group") is CustomCycleBuilderResult.Rejected)
    }
    @Test fun staleRevisionAndWrongDayCannotChangeTheProgram() {
        val original=initial()
        assertEquals(CustomCycleBuilderResult.Rejected(CustomCycleBuilderRefusal.STALE_REVISION),
            CustomCycleBuilderDomain.setSupersetMembers(original,2,"base",0,"pair",listOf("a","b"),true))
        assertEquals(CustomCycleBuilderResult.Rejected(CustomCycleBuilderRefusal.MISSING_SLOT),
            CustomCycleBuilderDomain.setSupersetMembers(original,1,"base",1,"pair",listOf("a","b"),true))
        assertEquals(CustomCycleBuilderResult.Rejected(CustomCycleBuilderRefusal.MISSING_BLOCK),
            CustomCycleBuilderDomain.setSupersetMembers(original,1,"missing",0,"pair",listOf("a","b"),true))
    }
    @Test fun unchangedMembershipDoesNotInvalidateAnApprovalWithANoopRevision() {
        val grouped=accepted(change(initial(),listOf("a","b"))).draft
        assertTrue(change(grouped,listOf("b","a")) is CustomCycleBuilderResult.Rejected)
    }
    @Test fun warmupOnlySlotCannotBecomeAnEmptyWorkingRound() {
        val original=initial()
        val block=original.blocks.single()
        val day=block.days.single()
        val draft=original.copy(blocks=listOf(block.copy(days=listOf(day.copy(slots=day.slots.map {
            if(it.slotId=="a")it.copy(sets=listOf(set.copy(type=DraftSetType.WARMUP))) else it
        })))))
        assertTrue(change(draft,listOf("a","b")) is CustomCycleBuilderResult.Rejected)
    }
}
