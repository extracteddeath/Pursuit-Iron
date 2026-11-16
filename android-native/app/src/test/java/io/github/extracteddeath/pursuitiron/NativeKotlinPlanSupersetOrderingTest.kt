package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import org.junit.Assert.*
import org.junit.Test

/** A UI row cannot split a linked program round or write one slot's old
 * loads to another. Real native drafts are cold reopened after Undo. */
internal class NativeKotlinPlanSupersetOrderingTest:
    NativeKotlinProgramFilesFixture() {
    private val policy=NativeKotlinPlanSupersetOrdering

    private fun threeSlotDraft(id:String):CustomTrainingCycle {
        val base=cycle(id)
        val block=base.blocks.single()
        val day=block.days.first()
        val first=day.slots.single().copy(supersetGroup="pair")
        val second=first.copy(slotId="partner",pinned=false)
        val solo=first.copy(slotId="solo",supersetGroup=null,pinned=false)
        return base.copy(blocks=listOf(block.copy(days=
            block.days.map{if(it.weekday==day.weekday)
                it.copy(slots=listOf(first,second,solo))else it})))
    }

    @Test fun movingOneLinkedMemberMovesEntireGroupWithStableInnerOrder() {
        val cycle=threeSlotDraft("atomic-plan")
        val day=cycle.blocks.single().days.first()
        assertEquals(listOf("solo","curl-0","partner"),
            policy.proposed(day,"curl-0",1))
        assertEquals(listOf("solo","curl-0","partner"),
            policy.proposed(day,"partner",1))
        assertNull(policy.proposed(day,"partner",-1))
        assertEquals(listOf("solo","curl-0","partner"),
            policy.proposed(day,"solo",-1))
        val actions0=nativePlanExerciseActions(
            cycle,cycle.blocks.single(),day,0,false)
        val actions1=nativePlanExerciseActions(
            cycle,cycle.blocks.single(),day,1,false)
        assertFalse(actions0.earlier)
        assertFalse(actions1.earlier)
        assertTrue(actions0.later)
        assertTrue(actions1.later)
    }

    @Test fun canonicalBuilderRejectsMaliciousScatteredRoundButAcceptsAtomicMove() {
        val before=threeSlotDraft("scatter")
        val broken=CustomCycleBuilderDomain.reorderSlots(
            before,before.revision,"phase-1",0,
            listOf("curl-0","solo","partner"))
        assertEquals(CustomCycleBuilderRefusal.SLOT_ORDER_CONFLICT,
            (broken as CustomCycleBuilderResult.Rejected).reason)
        val day=before.blocks.single().days.first()
        val ordering=policy.proposed(day,"partner",1)!!
        val valid=CustomCycleBuilderDomain.reorderSlots(
            before,before.revision,"phase-1",0,ordering)
            as CustomCycleBuilderResult.Proposed
        val newSlots=valid.draft.blocks.single().days.first().slots
        assertEquals(listOf("solo","curl-0","partner"),
            newSlots.map{it.slotId})
        assertEquals(day.slots.first(),newSlots[1])
        assertEquals(day.slots[1],newSlots[2])
        assertEquals(before.weekOverrides,valid.draft.weekOverrides)
        val undone=CustomCycleBuilderDomain.undo(valid.draft,valid.receipt)
            as CustomCycleBuilderResult.Proposed
        assertEquals(before.blocks,undone.draft.blocks)
    }

    @Test fun invalidOrNoncontiguousSupersetRemainsImmutableUntilUnlinked() {
        val before=threeSlotDraft("invalid-group")
        val block=before.blocks.single()
        val day=block.days.first()
        val separated=day.copy(slots=listOf(day.slots[0],day.slots[2],day.slots[1]))
        assertNull(policy.proposed(separated,"solo",-1))
        assertNull(policy.proposed(separated,"curl-0",1))
        val incomplete=day.copy(slots=day.slots.mapIndexed{i,slot->
            if(i==1)slot.copy(supersetGroup=null)else slot
        })
        assertNull(policy.proposed(incomplete,"curl-0",1))
        val duplicated=day.copy(slots=day.slots+day.slots.last())
        assertNull(policy.proposed(duplicated,"solo",-1))
        assertNull(policy.proposed(day,"curl-0",0))
        assertNull(policy.proposed(day,"not-a-real-slot",1))
        val altered=before.copy(blocks=listOf(block.copy(
            days=listOf(separated)+block.days.drop(1))))
        val blocked=nativePlanExerciseActions(
            altered,altered.blocks.single(),separated,0,false)
        assertFalse(blocked.earlier)
        assertFalse(blocked.later)
    }

    @Test fun authorCannotCreateSeparatedSupersetButCanUnlinkOldMembership() {
        val before=threeSlotDraft("new-superset-members")
        val blocked=CustomCycleBuilderDomain.setSupersetMembers(
            before,1,"phase-1",0,"pair",
            listOf("curl-0","solo"),true)
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (blocked as CustomCycleBuilderResult.Rejected).reason)
        val detached=CustomCycleBuilderDomain.setSupersetMembers(
            before,1,"phase-1",0,"pair",emptyList(),false)
            as CustomCycleBuilderResult.Proposed
        assertTrue(detached.draft.blocks.single().days.first().slots
            .all{it.supersetGroup==null})
        assertEquals(before.weekOverrides,detached.draft.weekOverrides)
    }

    @Test fun realNativeFileMoveAndUndoPreserveEachProgramSetAndOverride() {
        val saved=seed()
        val original=saved.change.draft
        val selected=original.blocks.single().days.first().slots.single()
        val added=CustomCycleBuilderDomain.addSlot(
            original,2,"phase-1",0,
            selected.copy(slotId="partner",pinned=false))
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            drafts().save("owner-1","cycle-b",2,added.draft))
        val addedSolo=CustomCycleBuilderDomain.addSlot(
            added.draft,3,"phase-1",0,
            selected.copy(slotId="solo",pinned=false))
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(4),
            drafts().save("owner-1","cycle-b",3,addedSolo.draft))
        val grouped=CustomCycleBuilderDomain.setSupersetMembers(
            addedSolo.draft,4,"phase-1",0,"pair",
            listOf("curl-0","partner"),true)
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(5),
            drafts().save("owner-1","cycle-b",4,grouped.draft))
        val day=grouped.draft.blocks.single().days.first()
        val order=policy.proposed(day,"partner",1)!!
        val moved=CustomCycleBuilderDomain.reorderSlots(
            grouped.draft,5,"phase-1",0,order)
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(6),
            drafts().save("owner-1","cycle-b",5,moved.draft))
        val cold=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(moved.draft,cold)
        assertEquals(grouped.draft.weekOverrides,cold.weekOverrides)
        val next=cold.blocks.single().days.first().slots
        assertEquals(listOf("solo","curl-0","partner"),next.map{it.slotId})
        assertEquals(day.slots[0],next[1])
        assertEquals(day.slots[1],next[2])
        val undone=CustomCycleBuilderDomain.undo(cold,moved.receipt)
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(7),
            drafts().save("owner-1","cycle-b",6,undone.draft))
        val restored=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(grouped.draft.blocks,restored.blocks)
        assertEquals(grouped.draft.weekOverrides,restored.weekOverrides)
    }

    @Test fun inspectingMovementOrderNeverWritesOwnerTrainingFiles() {
        val saved=seed()
        val before=hashes()
        val draft=saved.change.draft
        for(block in draft.blocks)for(day in block.days)
            for(slot in day.slots) {
                policy.proposed(day,slot.slotId,-1)
                policy.proposed(day,slot.slotId,1)
            }
        assertEquals(before,hashes())
    }
}
