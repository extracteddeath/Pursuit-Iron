package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import org.junit.Assert.*
import org.junit.Test

/** Tests original identities and real versioned native storage, not a
 * movement-name lookup or a UI-only imitation of the cycle builder. */
internal class NativeKotlinPlanCrossDayMoveTest:NativeKotlinProgramFilesFixture() {
    private val domain=NativeKotlinPlanCrossDayMove

    @Test fun reviewDisclosesExactDestinationAndPreservedWeekOverrides() {
        val original=cycle("cross-day-review")
        val review=domain.inspect(original,"phase-1",0,"curl-0",2)!!
        assertEquals(1,review.movedSlotIds.size)
        assertEquals(listOf("curl-0"),review.movedSlotIds)
        assertEquals(listOf("db_curl"),review.movedExerciseIds)
        assertEquals(1,review.pinnedCount)
        assertEquals(2,review.baseWorkingSets)
        assertEquals(1,review.baseWarmupSets)
        assertEquals(1,review.overrideCount)
        assertEquals(listOf(2),review.overrideWeeks)
        assertEquals(1,review.overrideWorkingSets)
        assertEquals(1,review.destinationIndex)
        assertEquals(0,review.remainingSourceSlots)
        assertNull(domain.inspect(original,"phase-1",0,"curl-0",0))
        assertNull(domain.inspect(original,"phase-1",0,"curl-0",1))
        assertNull(domain.inspect(original,"missing-block",0,"curl-0",2))
    }

    @Test fun realSavedMoveAndUndoPreservePinnedLoadsNotesAndWeeks() {
        val saved=seed()
        val before=saved.change.draft
        val review=domain.inspect(before,"phase-1",0,"curl-0",2)!!
        val changed=domain.confirmed(before,review) as CustomCycleBuilderResult.Proposed
        val from=changed.draft.blocks.single().days.first{it.weekday==0}
        val to=changed.draft.blocks.single().days.first{it.weekday==2}
        assertTrue(from.slots.isEmpty())
        assertEquals(listOf("curl-2","curl-0"),to.slots.map{it.slotId})
        assertEquals(before.blocks.single().days.first().slots.single(),to.slots.last())
        assertEquals(before.blocks.single().days.first{it.weekday==4},
            changed.draft.blocks.single().days.first{it.weekday==4})
        val movedWeek=changed.draft.weekOverrides["phase-1"]!!.single()
        assertEquals(2,movedWeek.weekInBlock)
        assertEquals(2,movedWeek.weekday)
        assertEquals("curl-0",movedWeek.slotId)
        assertEquals(before.weekOverrides["phase-1"]!!.single().sets,movedWeek.sets)
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            drafts().save("owner-1","cycle-b",2,changed.draft))
        val cold=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(changed.draft,cold)
        val undo=CustomCycleBuilderDomain.undo(cold,changed.receipt)
            as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(4),
            drafts().save("owner-1","cycle-b",3,undo.draft))
        val recovered=(drafts().read("owner-1","cycle-b")
            as NativeCustomDraftReadResult.Found).draft
        assertEquals(before.blocks,recovered.blocks)
        assertEquals(before.weekOverrides,recovered.weekOverrides)
    }

    @Test fun linkedSupersetMovesTogetherWithoutFlatteningRoundOrLoad() {
        val before=cycle("cross-superset")
        val block=before.blocks.single()
        val source=block.days.first()
        val first=source.slots.single().copy(supersetGroup="same-round")
        val second=first.copy(slotId="partner",pinned=false)
        val paired=before.copy(blocks=listOf(block.copy(
            days=listOf(source.copy(slots=listOf(first,second)))+block.days.drop(1))))
        val review=domain.inspect(paired,"phase-1",0,"partner",2)!!
        assertEquals(listOf("curl-0","partner"),review.movedSlotIds)
        assertEquals("same-round",review.supersetGroup)
        assertEquals(2,review.baseWarmupSets)
        assertEquals(4,review.baseWorkingSets)
        assertEquals(1,review.pinnedCount)
        val changed=domain.confirmed(paired,review)
            as CustomCycleBuilderResult.Proposed
        val target=changed.draft.blocks.single().days.first{it.weekday==2}
        assertEquals(listOf("curl-2","curl-0","partner"),target.slots.map{it.slotId})
        assertEquals(listOf(first,second),target.slots.drop(1))
        assertTrue(changed.draft.blocks.single().days.first{it.weekday==0}.slots.isEmpty())
        assertEquals(2,changed.draft.weekOverrides["phase-1"]!!.single().weekday)
    }

    @Test fun staleReviewCannotMoveADifferentNewlyEditedProgram() {
        val before=cycle("move-stale")
        val review=domain.inspect(before,"phase-1",0,"curl-0",2)!!
        val newer=CustomCycleBuilderDomain.renameDay(
            before,before.revision,"phase-1",0,"New source title")
            as CustomCycleBuilderResult.Proposed
        assertEquals(CustomCycleBuilderRefusal.STALE_REVISION,
            (domain.confirmed(newer.draft,review)
                as CustomCycleBuilderResult.Rejected).reason)
        val copied=before.copy(weekOverrides=before.weekOverrides.mapValues{
            (_,items)->items.map{it.copy(weekInBlock=3)}
        })
        assertEquals(CustomCycleBuilderRefusal.INVALID_INPUT,
            (domain.confirmed(copied,review)
                as CustomCycleBuilderResult.Rejected).reason)
        val foreign=before.copy(athleteId="other")
        assertEquals(CustomCycleBuilderRefusal.STALE_REVISION,
            (domain.confirmed(foreign,review)
                as CustomCycleBuilderResult.Rejected).reason)
    }

    @Test fun groupCollisionsFullDayAndIncompleteGroupAreUnavailable() {
        val before=cycle("move-capacity")
        val block=before.blocks.single()
        val source=block.days.first()
        val target=block.days[1]
        val full=target.copy(slots=target.slots+
            (1..31).map{target.slots.single().copy(slotId="filler-$it")})
        val crowded=before.copy(blocks=listOf(block.copy(
            days=block.days.map{if(it.weekday==full.weekday)full else it})))
        assertNull(domain.inspect(crowded,"phase-1",0,"curl-0",2))
        val singleGrouped=source.copy(slots=source.slots.map{
            it.copy(supersetGroup="incomplete-round")
        })
        val invalid=before.copy(blocks=listOf(block.copy(
            days=block.days.map{if(it.weekday==0)singleGrouped else it})))
        assertNull(domain.inspect(invalid,"phase-1",0,"curl-0",2))
    }

    @Test fun browsingDestinationsNeverWritesTrainingFiles() {
        val saved=seed()
        val before=hashes()
        for(day in saved.change.draft.blocks.single().days)
            for(target in saved.change.draft.blocks.single().days)
                for(slot in day.slots) {
                    domain.inspect(saved.change.draft,"phase-1",day.weekday,
                        slot.slotId,target.weekday)
                }
        assertEquals(before,hashes())
    }
}
