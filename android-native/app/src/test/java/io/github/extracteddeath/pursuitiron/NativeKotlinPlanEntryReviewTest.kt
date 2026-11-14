package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.nio.file.Files
import java.security.MessageDigest
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

/** Actual native files and canonical builder receipts; no Android Context or mocked stores. */
internal class NativeKotlinPlanEntryReviewTest:NativeKotlinProgramFilesFixture() {
    @Test fun returningToSecondProgramRetainsSelectionAndUndoWithoutOpeningTheCensus() {
        val saved=seed()
        val before=hashes()
        val entry=enter(saved) as NativeKotlinPlanEntry.Selected
        assertEquals("cycle-b",entry.cycleId)
        assertEquals(NativeCustomDraftReadResult.Found(saved.change.draft),entry.read)
        assertEquals(saved.change.receipt,entry.retainedUndo)
        assertFalse(entry.undoUnavailable)
        assertEquals(saved.source,entry.source)
        assertEquals(listOf("cycle-b"),draftReads)
        assertEquals(0,censusReads)
        assertEquals(before,hashes())
    }

    @Test fun explicitUndoAfterReturningRestoresAllSetsAmplifiersNotesAndWeekOverrides() {
        val saved=seed()
        val before=hashes()
        val entry=enter(saved) as NativeKotlinPlanEntry.Selected
        val current=(entry.read as NativeCustomDraftReadResult.Found).draft
        val undone=CustomCycleBuilderDomain.undo(current,checkNotNull(entry.retainedUndo))
            as CustomCycleBuilderResult.Proposed
        assertEquals(saved.before.copy(revision=3),undone.draft)
        assertEquals(NativeCustomDraftWriteResult.Committed(3),
            drafts().save("owner-1",entry.cycleId,2,undone.draft))
        assertEquals(NativeCustomDraftReadResult.Found(undone.draft),drafts().read("owner-1","cycle-b"))
        assertEquals(NativeCustomDraftReadResult.Found(cycle("cycle-a")),drafts().read("owner-1","cycle-a"))
        assertEquals(1,before.keys.count{before[it]!=hashes()[it]})
        assertEquals(NativeAuthoringSourceRead.Found(saved.source),sources().read("owner-1"))
    }

    @Test fun coldEntryWithSeveralProgramsRequiresAChoiceAndDoesNotInventUndo() {
        val saved=seed()
        val entry=enter(saved,visible=null,seenSource=null) as NativeKotlinPlanEntry.Programs
        assertEquals(setOf("cycle-a","cycle-b"),
            (entry.read as NativeCustomDraftListResult.Ready).drafts.map{it.cycleId}.toSet())
        assertTrue(draftReads.isEmpty())
    }

    @Test fun coldEntryWithOneProgramUsesFreshFilesWithoutRecoveringAnOldReceipt() {
        val saved=seed(1)
        val entry=enter(saved,visible=null,seenSource=null) as NativeKotlinPlanEntry.Selected
        assertEquals(saved.change.draft,(entry.read as NativeCustomDraftReadResult.Found).draft)
        assertNull(entry.retainedUndo)
        assertEquals(1,censusReads)
    }

    @Test fun firstEntryWithNoProgramReturnsAnEmptyVerifiedCensus() {
        val saved=seed(0)
        val entry=enter(saved,visible=null,seenSource=null) as NativeKotlinPlanEntry.Programs
        assertTrue((entry.read as NativeCustomDraftListResult.Ready).drafts.isEmpty())
        assertTrue(draftReads.isEmpty())
    }

    @Test fun aNewerProgramRevisionReplacesTheViewAndInvalidatesThePreviousUndo() {
        val saved=seed()
        val changed=CustomCycleBuilderDomain.editCycleMetadata(saved.change.draft,2,
            "Changed elsewhere","Another real edit") as CustomCycleBuilderResult.Proposed
        assertEquals(NativeCustomDraftWriteResult.Committed(3),drafts().save("owner-1","cycle-b",2,changed.draft))
        val entry=enter(saved) as NativeKotlinPlanEntry.Selected
        assertEquals(changed.draft,(entry.read as NativeCustomDraftReadResult.Found).draft)
        assertNull(entry.retainedUndo)
        assertTrue(entry.undoUnavailable)
        assertEquals(0,censusReads)
    }

    @Test fun equalRevisionNumbersCannotHideDifferentVisibleProgramContents() {
        val saved=seed()
        val entry=enter(saved,visible=saved.change.draft.copy(authorNote="Not the saved facts"))
            as NativeKotlinPlanEntry.Selected
        assertNull(entry.retainedUndo)
        assertEquals(saved.change.draft,(entry.read as NativeCustomDraftReadResult.Found).draft)
    }

    @Test fun changedGymContextKeepsTheSelectedProgramButRequiresANewUndoContext() {
        val saved=seed()
        val changed=source(2)
        assertEquals(NativeAuthoringSourceWrite.Committed(2),sources().publish("owner-1",1,changed))
        val entry=enter(saved) as NativeKotlinPlanEntry.Selected
        assertEquals("cycle-b",entry.cycleId)
        assertEquals(changed,entry.source)
        assertNull(entry.retainedUndo)
        assertTrue(entry.undoUnavailable)
    }

    @Test fun fullSourceContentsMustMatchNotOnlyItsRevisionNumber() {
        val saved=seed()
        val other=saved.source.copy(catalog=saved.source.catalog.reversed())
        val entry=enter(saved,seenSource=other) as NativeKotlinPlanEntry.Selected
        assertNull(entry.retainedUndo)
    }

    @Test fun revokingTheGymDoesNotSwitchProgramsOrRetainAnOldUndo() {
        val saved=seed()
        assertEquals(NativeAuthoringSourceWrite.Committed(2),sources().revoke("owner-1",1))
        val entry=enter(saved) as NativeKotlinPlanEntry.Selected
        assertEquals("cycle-b",entry.cycleId)
        assertNull(entry.source)
        assertNull(entry.retainedUndo)
        assertEquals(0,censusReads)
    }

    @Test fun deletingTheSelectedProgramDoesNotSilentlyChooseAnother() {
        val saved=seed()
        assertEquals(NativeCustomDraftWriteResult.Committed(3),drafts().delete("owner-1","cycle-b",2))
        val entry=enter(saved) as NativeKotlinPlanEntry.Selected
        assertEquals(NativeCustomDraftReadResult.Deleted(3),entry.read)
        assertNull(entry.retainedUndo)
        assertEquals(0,censusReads)
    }

    @Test fun ownerSwitchUsesOnlyTheNewOwnersProgramsEvenWithTheSameAthleteId() {
        val saved=seed()
        signedIn=NativeKotlinOwnerIdentity("owner-2","athlete-1")
        val context=source()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish("owner-2",0,context))
        val other=cycle("cycle-c")
        assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts().save("owner-2","cycle-c",0,other))
        val entry=enter(saved) as NativeKotlinPlanEntry.Selected
        assertEquals("cycle-c",entry.cycleId)
        assertEquals(other,(entry.read as NativeCustomDraftReadResult.Found).draft)
        assertNull(entry.retainedUndo)
        assertEquals(listOf("cycle-c"),draftReads)
    }

    @Test fun signedOutEntryReadsNoSourceProgramOrCensus() {
        val saved=seed()
        signedIn=null
        val entry=enter(saved) as NativeKotlinPlanEntry.Programs
        assertEquals(NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),entry.read)
        assertEquals(0,sourceReads+censusReads+draftReads.size)
    }

    @Test fun signingOutDuringAReadReturnsNoPreviouslyReadSourceOrProgram() {
        val saved=seed()
        val entry=enter(saved,afterDraftRead={signedIn=null}) as NativeKotlinPlanEntry.Programs
        assertEquals(NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),entry.read)
    }

    @Test fun absentVerifiedSourceBindingCannotRetainAnArbitrarySelectedProgram() {
        val saved=seed()
        assertTrue(enter(saved,seenSource=null) is NativeKotlinPlanEntry.Programs)
        assertTrue(draftReads.isEmpty())
    }

    @Test fun invalidUndoReceiptsAreRejectedByTheCanonicalBuilder() {
        val saved=seed()
        for(bad in listOf(saved.change.receipt.copy(
            proposed=saved.change.draft.copy(title="Not this revision")),
            saved.change.receipt.copy(original=saved.before.copy(athleteId="foreign")),
            saved.change.receipt.copy(original=saved.before.copy(cycleId="foreign")))) {
            val entry=enter(saved,undo=bad) as NativeKotlinPlanEntry.Selected
            assertNull(entry.retainedUndo)
        }
    }

    @Test fun corruptProgramIsReportedWithoutSwitchingToAnotherOrRewritingItsBytes() {
        val saved=seed()
        val path=Files.walk(temp.root.toPath()).use { paths->paths.filter {
            Files.isRegularFile(it) && Files.readAllBytes(it).toString(Charsets.UTF_8).contains("cycle-b")
        }.toList().single() }
        Files.write(path,Files.readAllBytes(path)+byteArrayOf(7))
        val before=hashes()
        val entry=enter(saved) as NativeKotlinPlanEntry.Selected
        assertEquals(NativeCustomDraftReadResult.Rejected(NativeCustomDraftRefusal.CORRUPT_RECORD),entry.read)
        assertNull(entry.retainedUndo)
        assertEquals(0,censusReads)
        assertEquals(before,hashes())
    }

    @Test fun corruptSourceCannotRetainUndoAndIsNeverRepairedByARead() {
        val saved=seed()
        val path=Files.list(temp.root.toPath()).use { paths->paths.filter {
            it.fileName.toString().startsWith("authoring-source-") && it.toString().endsWith(".bin")
        }.toList().single() }
        Files.write(path,Files.readAllBytes(path)+byteArrayOf(7))
        val before=hashes()
        val entry=enter(saved) as NativeKotlinPlanEntry.Selected
        assertEquals("cycle-b",entry.cycleId)
        assertNull(entry.source)
        assertNull(entry.retainedUndo)
        assertEquals(before,hashes())
    }

    @Test fun rejectedSourceAuthorizationDoesNotTriggerAnyProgramLookup() {
        val saved=seed()
        val entry=NativeKotlinPlanEntryReview.review(identity,saved.change.draft,saved.source,
            saved.change.receipt,
            readSource={NativeAuthoringSourceRead.Rejected(NativeAuthoringSourceRefusal.NOT_AUTHORIZED)},
            readDraft={error("Unauthorized program read")},
            listDrafts={error("Unauthorized program census")}) as NativeKotlinPlanEntry.Programs
        assertEquals(NativeCustomDraftListResult.Rejected(NativeCustomDraftRefusal.NOT_AUTHORIZED),entry.read)
    }
}
