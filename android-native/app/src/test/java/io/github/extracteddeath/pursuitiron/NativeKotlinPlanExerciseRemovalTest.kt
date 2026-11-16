package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import org.junit.Assert.*
import org.junit.Test

/** Real native files, Kotlin builder receipts and cold readback, not a mock
 * Compose button or an invented completed workout. */
internal class NativeKotlinPlanExerciseRemovalTest:NativeKotlinProgramFilesFixture() {
    private val domain=NativeKotlinPlanExerciseRemoval

    @Test fun pinnedWeekExceptionsMustBeDisclosedBeforeExactRemoval() {
        val original=cycle("remove-review")
        val review=domain.inspect(original,"phase-1",0,"curl-0")!!
        assertEquals(original.athleteId,review.athleteId)
        assertEquals(original.revision,review.cycleRevision)
        assertEquals("db_curl",review.exerciseId)
        assertTrue(review.pinned)
        assertEquals(3,review.blockWeeks)
        assertEquals(2,review.baseWorkingSets)
        assertEquals(1,review.baseWarmupSets)
        assertEquals(listOf(2),review.overriddenWeeks)
        assertEquals(1,review.overrideWorkingSets)
        assertEquals(0,review.remainingExercises)
        assertEquals(null,domain.inspect(original,"phase-1",0,"no-slot"))
        assertEquals(null,domain.inspect(original,"other",0,"curl-0"))
    }

    @Test fun removalAndUndoColdReplayKeepOtherDaysAndTheirPrescriptions() {
        val saved=seed()
        val before=saved.change.draft
        val review=domain.inspect(before,"phase-1",0,"curl-0")!!
        val removal=domain.confirmed(before,review)
            as CustomCycleBuilderResult.Proposed
        assertEquals(before.revision+1,removal.draft.revision)
        val oldBlock=before.blocks.single()
        val afterBlock=removal.draft.blocks.single()
        assertTrue(afterBlock.days.first().slots.isEmpty())
        assertEquals(oldBlock.days.drop(1),afterBlock.days.drop(1))
        assertTrue(removal.draft.weekOverrides["phase-1"].orEmpty().isEmpty())
        assertEquals(1,before.weekOverrides["phase-1"].orEmpty().size)
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            drafts().save("owner-1","cycle-b",2,removal.draft))
        val cold=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(removal.draft,cold)
        assertTrue(cold.blocks.single().days.first().slots.isEmpty())
        val undo=CustomCycleBuilderDomain.undo(cold,removal.receipt)
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(4),
            drafts().save("owner-1","cycle-b",3,undo.draft))
        val recovered=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(before.blocks,recovered.blocks)
        assertEquals(before.weekOverrides,recovered.weekOverrides)
        assertEquals(4L,recovered.revision)
    }

    @Test fun staleDialogAndChangedAuthoredWeekCannotEraseDifferentSlot() {
        val before=cycle("stale-removal")
        val review=domain.inspect(before,"phase-1",0,"curl-0")!!
        val next=CustomCycleBuilderDomain.editCycleMetadata(
            before,before.revision,"Retitled cycle","Updated note")
            as CustomCycleBuilderResult.Proposed
        assertEquals(CustomCycleBuilderRefusal.STALE_REVISION,
            (domain.confirmed(next.draft,review)
                as CustomCycleBuilderResult.Rejected).reason)
        val changedOverride=before.copy(weekOverrides=before.weekOverrides.mapValues{
            (_,items)->items.map{it.copy(weekInBlock=3)}
        })
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (domain.confirmed(changedOverride,review)
                as CustomCycleBuilderResult.Rejected).reason)
        val anotherAthlete=before.copy(athleteId="another-athlete")
        assertEquals(CustomCycleBuilderRefusal.STALE_REVISION,
            (domain.confirmed(anotherAthlete,review)
                as CustomCycleBuilderResult.Rejected).reason)
    }

    @Test fun linkedSupersetsAndAmbiguousSlotIdentityCannotBeRemovedByThisRoute() {
        val original=cycle("linked-removal")
        val block=original.blocks.single()
        val day=block.days.first()
        val linkedDay=day.copy(slots=day.slots.map{
            it.copy(supersetGroup="pair-1")
        })
        val linked=original.copy(blocks=listOf(block.copy(
            days=listOf(linkedDay)+block.days.drop(1))))
        assertNull(domain.inspect(linked,"phase-1",0,"curl-0"))
        val duplicate=day.copy(slots=day.slots+day.slots.first())
        val ambiguous=original.copy(blocks=listOf(block.copy(
            days=listOf(duplicate)+block.days.drop(1))))
        assertNull(domain.inspect(ambiguous,"phase-1",0,"curl-0"))
        val malformedWeek=original.copy(
            weekOverrides=original.weekOverrides.mapValues { (_,items) ->
                items+items.first()
            })
        assertNull(domain.inspect(malformedWeek,"phase-1",0,"curl-0"))
    }

    @Test fun previewNeverTouchesOwnerPrivateTrainingStore() {
        val saved=seed()
        val before=hashes()
        val cycle=saved.change.draft
        for(block in cycle.blocks)for(day in block.days)
            for(slot in day.slots) {
                domain.inspect(cycle,block.blockId,day.weekday,slot.slotId)
            }
        assertEquals(before,hashes())
    }
}
