package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

internal class NativeKotlinPlanEditorPresentationTest:NativeKotlinProgramFilesFixture() {
    @Test fun availablePaneWidthAndEnlargedTextBothHaveToFit() {
        assertFalse(nativePlanUsesTwoPanes(412f,1f))
        assertFalse(nativePlanUsesTwoPanes(899f,1f))
        assertTrue(nativePlanUsesTwoPanes(900f,1f))
        assertFalse(nativePlanUsesTwoPanes(900f,1.01f))
        assertFalse(nativePlanUsesTwoPanes(1200f,2f))
        assertFalse(nativePlanUsesTwoPanes(1799f,2f))
        assertTrue(nativePlanUsesTwoPanes(1800f,2f))
        assertFalse(nativePlanUsesTwoPanes(800f,0.85f))
        for(bad in listOf(Float.NaN,Float.POSITIVE_INFINITY,Float.NEGATIVE_INFINITY,-1f,0f)) {
            assertFalse(nativePlanUsesTwoPanes(bad,1f))
            assertFalse(nativePlanUsesTwoPanes(1200f,bad))
        }
    }

    @Test fun selectionUsesStableCoordinatesAcrossReorderingAndColonContainingIds() {
        val first=cycle("cycle-a").blocks.single().copy(blockId="strength:phase:1")
        val second=first.copy(blockId="strength:phase:2",label="Second block")
        val draft=cycle("cycle-a").copy(blocks=listOf(first,second))
        for(block in draft.blocks)for(day in block.days) {
            val key=programDayKey(block.blockId,day.weekday)
            val selected=checkNotNull(resolveNativePlanEditorDay(draft,key))
            assertSame(block,selected.block)
            assertSame(day,selected.day)
            val reordered=draft.copy(blocks=draft.blocks.reversed().map {
                it.copy(days=it.days.reversed())
            })
            assertEquals(selected.day,resolveNativePlanEditorDay(reordered,key)?.day)
        }
    }

    @Test fun missingRemovedOrAmbiguousDaysNeverFallBackToAnUnrelatedEditor() {
        val draft=cycle("cycle-a")
        val block=draft.blocks.single()
        val key=programDayKey(block.blockId,0)
        assertNull(resolveNativePlanEditorDay(null,key))
        assertNull(resolveNativePlanEditorDay(draft,null))
        assertNull(resolveNativePlanEditorDay(draft,"stale-block:0"))
        assertNull(resolveNativePlanEditorDay(draft,programDayKey(block.blockId,1)))
        assertNull(resolveNativePlanEditorDay(draft.copy(blocks=emptyList()),key))
        assertNull(resolveNativePlanEditorDay(draft.copy(blocks=listOf(
            block.copy(days=block.days.filter { it.weekday!=0 }))),key))
        assertNull(resolveNativePlanEditorDay(draft.copy(blocks=listOf(block,block)),key))
        assertNull(resolveNativePlanEditorDay(draft.copy(blocks=listOf(
            block.copy(days=block.days+block.days.first()))),key))
    }

    @Test fun selectedDayTracksAnExplicitSavedEditColdReadAndUndo() {
        val saved=seed()
        val owner=checkNotNull(signedIn).ownerKey
        val before=saved.change.draft
        val key=programDayKey("phase-1",2)
        val initial=checkNotNull(resolveNativePlanEditorDay(before,key))
        val edit=CustomCycleBuilderDomain.renameDay(before,before.revision,
            initial.block.blockId,initial.day.weekday,"Upper body") as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            drafts().save(owner,before.cycleId,2,edit.draft))
        val cold=(drafts().read(owner,before.cycleId) as NativeCustomDraftReadResult.Found).draft
        val selected=checkNotNull(resolveNativePlanEditorDay(cold,key))
        assertEquals("Upper body",selected.day.label)
        assertEquals(initial.day.slots,selected.day.slots)
        assertEquals(before.weekOverrides,cold.weekOverrides)
        assertEquals(before.authorNote,cold.authorNote)

        val undo=CustomCycleBuilderDomain.undo(cold,edit.receipt) as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(4),
            drafts().save(owner,before.cycleId,3,undo.draft))
        val reopened=(drafts().read(owner,before.cycleId) as NativeCustomDraftReadResult.Found).draft
        assertEquals(before.copy(revision=4),reopened)
        assertEquals(initial,resolveNativePlanEditorDay(reopened,key))
    }

    @Test fun browsingEveryDayAtDifferentWidthsDoesNotWriteOrAlterTraining() {
        val saved=seed()
        val before=hashes()
        val draft=saved.change.draft
        for(width in listOf(360f,900f,1200f,600f,1800f)) {
            nativePlanUsesTwoPanes(width,2f)
            for(block in draft.blocks)for(day in block.days) {
                val selected=checkNotNull(resolveNativePlanEditorDay(draft,
                    programDayKey(block.blockId,day.weekday)))
                assertSame(day,selected.day)
                assertSame(block,selected.block)
            }
        }
        assertEquals(before,hashes())
        assertEquals(NativeCustomDraftReadResult.Found(saved.change.draft),
            drafts().read("owner-1","cycle-b"))
        assertEquals(NativeAuthoringSourceRead.Found(saved.source),sources().read("owner-1"))
    }
}
