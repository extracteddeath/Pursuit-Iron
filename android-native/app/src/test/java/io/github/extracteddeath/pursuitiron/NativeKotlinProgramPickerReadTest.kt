package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import org.junit.Assert.*
import org.junit.Test

internal class NativeKotlinProgramPickerReadTest:NativeKotlinProgramFilesFixture() {
    private fun census(afterRead:()->Unit={}):NativeKotlinPlanEntry.Programs {
        val owner=signedIn
        fun read()=NativeKotlinPlanEntryReview.programs(identity) {
            censusReads++
            drafts().listOwned(checkNotNull(owner).ownerKey).also{afterRead()}
        }
        return if(owner==null)read() else fence().withOwnerTransaction(owner.ownerKey){read()}
    }
    private fun selection(list:NativeKotlinPlanEntry.Programs,id:String)=
        NativeKotlinProgramSelection(checkNotNull(list.owner),id,
            (list.read as NativeCustomDraftListResult.Ready).drafts.single{it.cycleId==id}.revision)

    @Test fun browsingUsesActualMetadataAndLeavesEveryNativeFileAndSelectedUndoUntouched() {
        val saved=seed()
        val before=hashes()
        val list=census()
        assertEquals(signedIn,list.owner)
        val summary=(list.read as NativeCustomDraftListResult.Ready).drafts.single{it.cycleId=="cycle-b"}
        assertEquals(saved.change.draft.title,summary.title)
        assertEquals(2L,summary.revision)
        assertEquals(AthleteTrainingGoal.HYPERTROPHY,summary.goal)
        assertEquals(3,summary.weeks)
        assertEquals(3,summary.minimumDays)
        assertEquals(3,summary.maximumDays)
        assertEquals("Hypertrophy · 3 weeks · 3 days/week · 1 block",summary.programDescription())
        assertEquals(0,sourceReads)
        assertTrue(draftReads.isEmpty())
        assertEquals(saved.change.receipt,(enter(saved) as NativeKotlinPlanEntry.Selected).retainedUndo)
        assertEquals(before,hashes())
    }

    @Test fun choosingAnotherProgramReadsThatProgramWithoutRewritingOrApprovingIt() {
        val saved=seed()
        val selected=selection(census(),"cycle-a")
        val before=hashes()
        val entry=enter(saved,selection=selected) as NativeKotlinPlanEntry.Selected
        assertEquals("cycle-a",entry.cycleId)
        assertEquals(cycle("cycle-a"),(entry.read as NativeCustomDraftReadResult.Found).draft)
        assertNull(entry.retainedUndo)
        assertFalse(entry.undoUnavailable)
        assertFalse(entry.selectionUpdated)
        assertEquals(listOf("cycle-a"),draftReads)
        assertEquals(1,censusReads)
        assertEquals(before,hashes())
    }

    @Test fun censusDescribesChangingFrequencyAcrossAuthoredBlocksWithoutGuessingASplit() {
        val saved=seed()
        val original=cycle("variable")
        val block=original.blocks.single()
        val varied=original.copy(blocks=listOf(block.copy(weeks=2,days=block.days.take(2)),
            block.copy(blockId="phase-2",label="Strength",weeks=4)))
        assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts().save(
            checkNotNull(signedIn).ownerKey,varied.cycleId,0,varied))
        val before=hashes()
        val summary=(census().read as NativeCustomDraftListResult.Ready).drafts.single{it.cycleId==varied.cycleId}
        assertEquals(6,summary.weeks)
        assertEquals(2,summary.minimumDays)
        assertEquals(3,summary.maximumDays)
        assertEquals(2,summary.blocks)
        assertEquals(before,hashes())
        assertEquals(saved.change.receipt,(enter(saved) as NativeKotlinPlanEntry.Selected).retainedUndo)
    }

    @Test fun failedCensusDoesNotLabelRowsAsOwned() {
        val entry=NativeKotlinPlanEntryReview.programs(identity) {
            NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.CORRUPT_RECORD)
        }
        assertNull(entry.owner)
        assertEquals(NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.CORRUPT_RECORD),entry.read)
    }

    @Test fun choosingTheCurrentUnchangedProgramKeepsItsCanonicalUndo() {
        val saved=seed()
        val entry=enter(saved,selection=selection(census(),"cycle-b")) as NativeKotlinPlanEntry.Selected
        assertEquals(saved.change.receipt,entry.retainedUndo)
        assertFalse(entry.selectionUpdated)
        assertEquals(saved.before,(CustomCycleBuilderDomain.undo(
            (entry.read as NativeCustomDraftReadResult.Found).draft,
            checkNotNull(entry.retainedUndo)) as CustomCycleBuilderResult.Proposed).draft.copy(revision=1))
    }

    @Test fun aProgramChangedAfterListingOpensItsLatestContentsAndReportsTheChange() {
        val saved=seed()
        val selected=selection(census(),"cycle-a")
        val change=CustomCycleBuilderDomain.editCycleMetadata(cycle("cycle-a"),1,
            "Renamed while browsing","Current notes") as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(2),drafts().save(
            checkNotNull(signedIn).ownerKey,"cycle-a",1,change.draft))
        val before=hashes()
        val entry=enter(saved,selection=selected) as NativeKotlinPlanEntry.Selected
        assertEquals(change.draft,(entry.read as NativeCustomDraftReadResult.Found).draft)
        assertTrue(entry.selectionUpdated)
        assertNull(entry.retainedUndo)
        assertEquals(before,hashes())
    }

    @Test fun aDeletedSelectionIsReportedWithoutFallingBackToTheCurrentProgram() {
        val saved=seed()
        val selected=selection(census(),"cycle-a")
        assertEquals(NativeCustomDraftWriteResult.Committed(2),
            drafts().delete(checkNotNull(signedIn).ownerKey,"cycle-a",1))
        val before=hashes()
        val entry=enter(saved,selection=selected) as NativeKotlinPlanEntry.Selected
        assertEquals(NativeCustomDraftReadResult.Deleted(2),entry.read)
        assertEquals(listOf("cycle-a"),draftReads)
        assertEquals(before,hashes())
    }

    @Test fun staleListCannotRemapAProgramIdToAnotherOwnerWithTheSameAthleteId() {
        val saved=seed()
        val selected=selection(census(),"cycle-a")
        signedIn=NativeKotlinOwnerIdentity("owner-2","athlete-1")
        val replacement=cycle("cycle-a").copy(title="Different owner's program")
        assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts().save(
            checkNotNull(signedIn).ownerKey,"cycle-a",0,replacement))
        val before=hashes()
        val entry=enter(saved,selection=selected) as NativeKotlinPlanEntry.Programs
        assertEquals(NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),entry.read)
        assertNull(entry.owner)
        assertEquals(0,sourceReads)
        assertTrue(draftReads.isEmpty())
        assertEquals(1,censusReads)
        assertEquals(before,hashes())
    }

    @Test fun staleAthleteBindingIsRejectedBeforeAnyFileLookup() {
        val saved=seed()
        val selected=selection(census(),"cycle-a")
        signedIn=NativeKotlinOwnerIdentity("owner-1","athlete-2")
        val entry=enter(saved,selection=selected) as NativeKotlinPlanEntry.Programs
        assertEquals(NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),entry.read)
        assertEquals(0,sourceReads)
        assertTrue(draftReads.isEmpty())
    }

    @Test fun signOutWhileListingDiscardsAllRowsAndOwnerContext() {
        seed()
        val entry=census{signedIn=null}
        assertNull(entry.owner)
        assertEquals(NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),entry.read)
    }

    @Test fun signedOutBrowsingPerformsNoCensus() {
        signedIn=null
        assertNull(census().owner)
        assertEquals(0,censusReads)
    }

    @Test fun signOutDuringSelectionDiscardsTheReadProgram() {
        val saved=seed()
        val selected=selection(census(),"cycle-a")
        val entry=enter(saved,selection=selected,afterDraftRead={signedIn=null}) as NativeKotlinPlanEntry.Programs
        assertNull(entry.owner)
        assertEquals(NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),entry.read)
    }

    @Test fun coldCensusRowsCarryTheOwnerNeededForAnExplicitSelection() {
        val saved=seed()
        val entry=enter(saved,visible=null,seenSource=null,undo=null) as NativeKotlinPlanEntry.Programs
        assertEquals(signedIn,entry.owner)
        val opened=enter(saved,visible=null,seenSource=null,undo=null,
            selection=selection(entry,"cycle-b")) as NativeKotlinPlanEntry.Selected
        assertEquals(saved.change.draft,(opened.read as NativeCustomDraftReadResult.Found).draft)
        assertNull(opened.retainedUndo)
    }
}
