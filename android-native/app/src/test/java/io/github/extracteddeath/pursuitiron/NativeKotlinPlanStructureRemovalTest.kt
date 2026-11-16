package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import org.junit.Assert.*
import org.junit.Test

/** Real versioned Kotlin owner stores and cycle-builder undo receipts.
 * A dialog preview alone never makes an issued workout or changes History. */
internal class NativeKotlinPlanStructureRemovalTest:
    NativeKotlinProgramFilesFixture() {
    private val policy=NativeKotlinPlanStructureRemoval

    @Test fun exactDayPreviewCountsPinnedWorkAndAllAuthoredWeekExceptions() {
        val original=cycle("delete-day-preview")
        val shown=policy.day(original,"phase-1",0)!!
        assertEquals("Day 0",shown.dayLabel)
        assertEquals(3,shown.blockWeeks)
        assertEquals(1,shown.exerciseCount)
        assertEquals(2,shown.workingSets)
        assertEquals(1,shown.warmupSets)
        assertEquals(1,shown.pinnedExercises)
        assertEquals(1,shown.overrideCount)
        assertEquals(listOf(2),shown.overrideWeeks)
        assertEquals(1,shown.overrideWorkingSets)
        assertEquals(2,shown.remainingDays)
        assertNull(policy.day(original,"phase-1",6))
        assertNull(policy.day(original,"wrong-block",0))
    }

    @Test fun deletingDayColdSavesOnlyItsFutureOverridesAndUndoRestoresEverything() {
        val saved=seed()
        val before=saved.change.draft
        val preview=policy.day(before,"phase-1",0)!!
        val changed=policy.confirmedDay(before,preview)
            as CustomCycleBuilderResult.Proposed
        val blockBefore=before.blocks.single()
        val blockAfter=changed.draft.blocks.single()
        assertEquals(blockBefore.days.drop(1),blockAfter.days)
        assertEquals(emptyList<CustomWeekSlotOverride>(),
            changed.draft.weekOverrides["phase-1"])
        assertEquals(1,before.weekOverrides["phase-1"]!!.size)
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            drafts().save("owner-1","cycle-b",2,changed.draft))
        val cold=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(changed.draft,cold)
        val restored=CustomCycleBuilderDomain.undo(cold,changed.receipt)
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(4),
            drafts().save("owner-1","cycle-b",3,restored.draft))
        val reread=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(before.blocks,reread.blocks)
        assertEquals(before.weekOverrides,reread.weekOverrides)
        assertEquals(before.title,reread.title)
    }

    @Test fun removingOnlyBlockFailsButSelectedBlockAndOverridesCanBeRemoved() {
        val before=cycle("remove-block")
        assertNull(policy.block(before,"phase-1"))
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (CustomCycleBuilderDomain.removeBlock(before,1,"phase-1")
                as CustomCycleBuilderResult.Rejected).reason)
        val second=CustomCycleBuilderDomain.duplicateBlock(
            before,1,"phase-1","phase-2") as CustomCycleBuilderResult.Proposed
        val preview=policy.block(second.draft,"phase-1")!!
        assertEquals(3,preview.blockWeeks)
        assertEquals(3,preview.trainingDays)
        assertEquals(3,preview.exerciseVisits)
        assertEquals(6,preview.workingSets)
        assertEquals(3,preview.warmupSets)
        assertEquals(3,preview.pinnedExercises)
        assertEquals(1,preview.overrideCount)
        assertTrue(preview.shiftsLaterBlockWeeks)
        val changed=policy.confirmedBlock(second.draft,preview)
            as CustomCycleBuilderResult.Proposed
        assertEquals(listOf("phase-2"),changed.draft.blocks.map{it.blockId})
        assertEquals(mapOf("phase-2" to second.draft.weekOverrides["phase-2"]!!),
            changed.draft.weekOverrides)
        assertEquals(second.draft.blocks.last(),changed.draft.blocks.single())
        val undone=CustomCycleBuilderDomain.undo(changed.draft,changed.receipt)
            as CustomCycleBuilderResult.Proposed
        assertEquals(second.draft.blocks,undone.draft.blocks)
        assertEquals(second.draft.weekOverrides,undone.draft.weekOverrides)
    }

    @Test fun fullBlockDeleteOnDiskAndUndoRemainOwnedAndAtomic() {
        val saved=seed()
        val before=saved.change.draft
        val duplicate=CustomCycleBuilderDomain.duplicateBlock(
            before,2,"phase-1","phase-2") as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            drafts().save("owner-1","cycle-b",2,duplicate.draft))
        val review=policy.block(duplicate.draft,"phase-2")!!
        assertFalse(review.shiftsLaterBlockWeeks)
        val result=policy.confirmedBlock(duplicate.draft,review)
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(4),
            drafts().save("owner-1","cycle-b",3,result.draft))
        val cold=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(listOf("phase-1"),cold.blocks.map{it.blockId})
        assertEquals(before.weekOverrides,cold.weekOverrides)
        val undo=CustomCycleBuilderDomain.undo(cold,result.receipt)
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(5),
            drafts().save("owner-1","cycle-b",4,undo.draft))
        val restored=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(duplicate.draft.blocks,restored.blocks)
        assertEquals(duplicate.draft.weekOverrides,restored.weekOverrides)
    }

    @Test fun staleOrRetargetedDialogsCannotDeleteADifferentDayOrBlock() {
        val before=cycle("stale-structure")
        val dayReview=policy.day(before,"phase-1",0)!!
        val altered=CustomCycleBuilderDomain.renameDay(
            before,1,"phase-1",0,"Different day"
        ) as CustomCycleBuilderResult.Proposed
        assertEquals(CustomCycleBuilderRefusal.STALE_REVISION,
            (policy.confirmedDay(altered.draft,dayReview)
                as CustomCycleBuilderResult.Rejected).reason)
        val shifted=before.copy(weekOverrides=before.weekOverrides.mapValues{
            (_,items)->items.map{it.copy(weekInBlock=3)}
        })
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (policy.confirmedDay(shifted,dayReview)
                as CustomCycleBuilderResult.Rejected).reason)
        val second=CustomCycleBuilderDomain.duplicateBlock(
            before,1,"phase-1","new-phase"
        ) as CustomCycleBuilderResult.Proposed
        val blockReview=policy.block(second.draft,"phase-1")!!
        val changed=second.draft.copy(blocks=second.draft.blocks.reversed())
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (policy.confirmedBlock(changed,blockReview)
                as CustomCycleBuilderResult.Rejected).reason)
        val otherOwner=second.draft.copy(athleteId="another")
        assertEquals(CustomCycleBuilderRefusal.STALE_REVISION,
            (policy.confirmedBlock(otherOwner,blockReview)
                as CustomCycleBuilderResult.Rejected).reason)
    }

    @Test fun orphanedAmbiguousOverrideCannotBecomeDeletionConsent() {
        val original=cycle("bad-override")
        val duplicate=original.copy(
            weekOverrides=original.weekOverrides.mapValues{(_,entries)->
                entries+entries.first()
            })
        assertNull(policy.day(duplicate,"phase-1",0))
        val missingSlot=original.copy(
            weekOverrides=original.weekOverrides.mapValues{(_,entries)->
                entries.map{it.copy(slotId="not-a-saved-slot")}
            })
        assertNull(policy.day(missingSlot,"phase-1",0))
        val second=CustomCycleBuilderDomain.duplicateBlock(
            original,1,"phase-1","phase-2"
        ) as CustomCycleBuilderResult.Proposed
        val corrupt=second.draft.copy(
            weekOverrides=second.draft.weekOverrides.mapValues{(_,items)->
                items.map{it.copy(weekInBlock=99)}
            })
        assertNull(policy.block(corrupt,"phase-1"))
    }

    @Test fun reviewingDeleteOptionsDoesNotTouchPrivateStore() {
        val saved=seed()
        val before=hashes()
        val draft=saved.change.draft
        draft.blocks.forEach{block->
            policy.block(draft,block.blockId)
            block.days.forEach{day->
                policy.day(draft,block.blockId,day.weekday)
            }
        }
        assertEquals(before,hashes())
        assertEquals(NativeCustomDraftReadResult.Found(saved.change.draft),
            drafts().read("owner-1","cycle-b"))
    }
}
