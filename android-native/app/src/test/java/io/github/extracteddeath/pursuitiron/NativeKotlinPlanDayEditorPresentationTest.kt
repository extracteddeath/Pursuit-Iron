package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeCustomDraftReadResult
import org.junit.Assert.*
import org.junit.Test

/** The same source-bound day actions in compact and wide Plan compositions. */
internal class NativeKotlinPlanDayEditorPresentationTest:
    NativeKotlinProgramFilesFixture() {
    @Test fun weekOverridesAndSupersetsProtectSwapButNotSetEditing() {
        val draft=cycle("actions-a")
        val block=draft.blocks.single()
        val blocked=nativePlanExerciseActions(draft,block,block.days.first(),0,false)
        assertTrue(blocked.edit)
        assertFalse(blocked.swap)
        assertTrue(blocked.weekOverride)
        val ordinary=block.days[1]
        val ordinaryAction=nativePlanExerciseActions(draft,block,ordinary,0,false)
        assertTrue(ordinaryAction.edit)
        assertTrue(ordinaryAction.swap)
        assertFalse(ordinaryAction.weekOverride)
        val grouped=ordinary.copy(slots=ordinary.slots.map{
            it.copy(supersetGroup="linked-a")
        })
        val groupedBlock=block.copy(days=block.days.map{
            if(it.weekday==grouped.weekday)grouped else it
        })
        val linked=nativePlanExerciseActions(
            draft.copy(blocks=listOf(groupedBlock)),groupedBlock,grouped,0,false)
        assertTrue(linked.superset)
        assertTrue(linked.edit)
        assertFalse(linked.swap)
    }

    @Test fun multipleWorkingMovementsHaveSafeOrderAndSupersetActions() {
        val original=cycle("actions-b")
        val block=original.blocks.single()
        val day=block.days[1]
        val second=day.slots.single().copy(slotId="second-slot",
            exerciseId="db_lateral_raise",pinned=false)
        val expanded=day.copy(slots=day.slots+second)
        val newBlock=block.copy(days=block.days.map{
            if(it.weekday==day.weekday)expanded else it
        })
        val draft=original.copy(blocks=listOf(newBlock))
        val first=nativePlanExerciseActions(draft,newBlock,expanded,0,false)
        val last=nativePlanExerciseActions(draft,newBlock,expanded,1,false)
        assertFalse(first.earlier);assertTrue(first.later)
        assertTrue(last.earlier);assertFalse(last.later)
        assertTrue(nativePlanCanCreateSuperset(draft,newBlock,expanded,false,true))
        assertFalse(nativePlanCanCreateSuperset(draft,newBlock,expanded,false,false))
        assertFalse(nativePlanCanCreateSuperset(draft,newBlock,expanded,true,true))
        val linked=expanded.copy(slots=expanded.slots.map{
            it.copy(supersetGroup="round-a")
        })
        val linkedBlock=newBlock.copy(days=newBlock.days.map{
            if(it.weekday==day.weekday)linked else it
        })
        assertFalse(nativePlanCanCreateSuperset(
            draft.copy(blocks=listOf(linkedBlock)),linkedBlock,linked,false,true))
    }

    @Test fun obsoleteAmbiguousAndBusyPlanNeverOffersWriteActions() {
        val draft=cycle("actions-c")
        val block=draft.blocks.single()
        val day=block.days.first()
        assertEquals(NativePlanExerciseActions(),nativePlanExerciseActions(
            draft,block.copy(label="outdated"),day,0,false))
        assertEquals(NativePlanExerciseActions(),nativePlanExerciseActions(
            draft,block,day,0,true))
        assertEquals(NativePlanExerciseActions(),nativePlanExerciseActions(
            draft,block,day,-1,false))
        assertEquals(NativePlanExerciseActions(),nativePlanExerciseActions(
            draft,block,day,20,false))
        assertFalse(nativePlanDayIsCurrent(
            draft.copy(blocks=listOf(block,block)),block,day))
        assertFalse(nativePlanDayIsCurrent(draft.copy(blocks=listOf(
            block.copy(days=block.days+day))),block,day))
    }

    @Test fun readingDayActionsNeverWritesSavedPrograms() {
        val saved=seed()
        val before=hashes()
        val draft=saved.change.draft
        draft.blocks.forEach{block->block.days.forEach{day->
            nativePlanDayIsCurrent(draft,block,day)
            nativePlanCanCreateSuperset(draft,block,day,false,true)
            day.slots.indices.forEach{nativePlanExerciseActions(
                draft,block,day,it,false)}
        }}
        assertEquals(before,hashes())
        assertEquals(NativeCustomDraftReadResult.Found(saved.change.draft),
            drafts().read("owner-1","cycle-b"))
    }
}
