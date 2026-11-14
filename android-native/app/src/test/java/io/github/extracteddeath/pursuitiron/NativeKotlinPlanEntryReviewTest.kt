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
class NativeKotlinPlanEntryReviewTest {
    @get:Rule val temp=TemporaryFolder()
    private var signedIn:NativeKotlinOwnerIdentity?=NativeKotlinOwnerIdentity("owner-1","athlete-1")
    private val identity=NativeKotlinCurrentIdentity {signedIn}
    private var sourceReads=0
    private var censusReads=0
    private val draftReads=mutableListOf<String>()
    private fun n(value:String)=BigDecimal(value)
    private fun fence()=NativeKotlinApprovalRevisionFence(temp.root.toPath())
    private fun drafts()=NativeCustomCycleDraftFileStore(temp.root.toPath(),fence(),identity)
    private fun sources()=NativeKotlinCustomAuthoringSourceFileStore(temp.root.toPath(),fence(),identity)
    private fun source(revision:Long=1,owner:NativeKotlinOwnerIdentity=checkNotNull(signedIn)):
        NativeCustomAuthoringSnapshot {
        val setup=CoachAthleteSetupDomain.review(AthleteSetupDraft(
            profileRevision="profile-$revision",athleteProfileId=owner.athleteId,
            goal=AthleteTrainingGoal.HYPERTROPHY,experience=AthleteExperience.ADVANCED,
            split=ComposerSplit.FULL_BODY,trainingWeekdays=setOf(0,2,4),
            timezoneId="America/New_York",sessionTimeBucket=SessionTimeBucket.FORTY_TO_SIXTY,
            hardSessionLimit=HardSessionLimit.NO_HARD_LIMIT,
            gym=GymProfileDraft("gym-1",LoadUnit.LB,mapOf("dumbbell-rack" to
                VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.DUMBBELL_PER_HAND,
                    listOf(n("15"),n("20"),n("25"),n("30")))),emptySet(),emptySet()),
            allowBodyweight=true,allowSupersets=false,allowTricepsOnLower=false,
            maxBarbellExercisesPerDay=0,focusedMuscles=emptySet(),reducedMuscles=emptySet(),
            excludedExerciseIds=emptySet(),lockedTrainingDays=emptySet(),hardTrainingDays=emptySet(),
            longTrainingDays=emptySet(),declaredRestDays=setOf(1,3,5,6)))
        assertEquals(AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW,setup.status)
        return NativeCustomAuthoringSnapshot(owner.ownerKey,owner.athleteId,revision,
            setup,StarterExerciseTranche.movements())
    }
    private fun load(value:String)=DraftSetLoad.Verified(LoadUnit.LB,
        LoadBasis.DUMBBELL_PER_HAND,n(value))
    private fun cycle(id:String,athlete:String=checkNotNull(signedIn).athleteId):CustomTrainingCycle {
        val working=CustomAuthoredSet(DraftSetType.WORKING,load("20"),RepBand(8,12),2,50,90)
        return CustomTrainingCycle(id,1,athlete,AthleteTrainingGoal.HYPERTROPHY,
            "Program $id",listOf(CustomTrainingBlock("phase-1","Foundation",
                TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,3,
                CustomSplitIdentity.Freeform("My custom program"),listOf(0,2,4).map { day->
                    CustomTrainingDay(day,"Day $day",ComposerSessionPurpose.HYPERTROPHY,
                        listOf(CustomExerciseSlot("curl-$day","db_curl",ComposerRole.ACCESSORY,
                            listOf(working.copy(type=DraftSetType.WARMUP,load=load("15"),
                                reps=RepBand(5,5),targetRir=null),working,
                                working.copy(amplifier=CustomSetAmplifier(
                                    CustomAmplifierKind.DROP_SET,6,20,load("15"),30))),
                            pinned=true,coachNote="Keep the exact attachment and tempo")))
                })),mapOf("phase-1" to listOf(CustomWeekSlotOverride(2,0,"curl-0",
                    listOf(working.copy(load=load("25")))))),"Original author notes")
    }
    private data class Saved(
        val before:CustomTrainingCycle,
        val change:CustomCycleBuilderResult.Proposed,
        val source:NativeCustomAuthoringSnapshot
    )
    private fun seed(count:Int=2):Saved {
        val owner=checkNotNull(signedIn)
        val context=source()
        assertEquals(NativeAuthoringSourceWrite.Committed(1),sources().publish(owner.ownerKey,0,context))
        if(count>1)assertEquals(NativeCustomDraftWriteResult.Committed(1),
            drafts().save(owner.ownerKey,"cycle-a",0,cycle("cycle-a")))
        val before=cycle("cycle-b")
        val change=CustomCycleBuilderDomain.editCycleMetadata(before,1,
            "My selected second program","Edited author notes") as CustomCycleBuilderResult.Proposed
        if(count>0) {
            assertEquals(NativeCustomDraftWriteResult.Committed(1),drafts().save(owner.ownerKey,"cycle-b",0,before))
            assertEquals(NativeCustomDraftWriteResult.Committed(2),drafts().save(owner.ownerKey,"cycle-b",1,change.draft))
        }
        return Saved(before,change,context)
    }
    private fun enter(
        saved:Saved,visible:CustomTrainingCycle?=saved.change.draft,
        seenSource:NativeCustomAuthoringSnapshot?=saved.source,
        undo:CustomCycleDraftReceipt?=saved.change.receipt,
        afterDraftRead:()->Unit={}
    ):NativeKotlinPlanEntry {
        val owner=signedIn
        fun read()=NativeKotlinPlanEntryReview.review(identity,visible,seenSource,undo,
            readSource={sourceReads++;sources().read(checkNotNull(owner).ownerKey)},
            readDraft={id->draftReads+=id
                drafts().read(checkNotNull(owner).ownerKey,id).also{afterDraftRead()}},
            listDrafts={censusReads++;drafts().listOwned(checkNotNull(owner).ownerKey)})
        return if(owner==null)read() else fence().withOwnerTransaction(owner.ownerKey){read()}
    }
    private fun hashes():Map<String,String> = Files.walk(temp.root.toPath()).use { paths->
        paths.filter{Files.isRegularFile(it)}.toList().associate {
            temp.root.toPath().relativize(it).toString() to MessageDigest.getInstance("SHA-256")
                .digest(Files.readAllBytes(it)).joinToString(""){byte->"%02x".format(byte)}
        }
    }

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
