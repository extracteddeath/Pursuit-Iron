package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class NativeProgramEditDraftTest {
    private val reference=ReadOnlyProgramReference("program-1",12L,20L,13,"a".repeat(64))
    private fun verified(ref: ReadOnlyProgramReference=reference): ReadOnlyProgramPresentation.Structure {
        val program=ReadOnlyProgramSummary(ref,ref.programId,"My plan",false,"both",6,
            listOf(
                ReadOnlyProgramDay("day-1","Upper",listOf("bench","curl","row")),
                ReadOnlyProgramDay("day-2","Lower",listOf("squat","rdl"))
            ))
        return ReadOnlyProgramPresentationPolicy.inspect(program,
            CurrentProgramEvidence(ref,CurrentProgramDeletionEvidence.CheckedAbsent)
        ) as ReadOnlyProgramPresentation.Structure
    }

    @Test fun editsPreserveAllDayIdsAndExerciseMembershipAndOriginals() {
        val approved=verified()
        val draft=NativeProgramEditDraft.from(approved)
        assertTrue(draft.matchesCurrent(approved))
        assertFalse(draft.isChanged)
        val reordered=checkNotNull(draft.moveExercise("day-1",2,-1))
        val edited=checkNotNull(reordered.renameDay("day-1","Push / Pull"))
        val renamed=checkNotNull(edited.renameProgram("New plan"))
        assertEquals(listOf("bench","row","curl"),renamed.days[0].exerciseIds)
        assertEquals("Push / Pull",renamed.days[0].label)
        assertEquals("New plan",renamed.name)
        assertEquals(listOf("bench","curl","row"),draft.days[0].exerciseIds)
        assertEquals("day-1",renamed.days[0].id)
        assertEquals("day-2",renamed.days[1].id)
        assertEquals(reference,renamed.reference)
        assertEquals(1,renamed.changedDayCount)
        assertTrue(renamed.isChanged)
        assertFalse(renamed.reset().isChanged)
    }

    @Test fun movingDaysNeverChangesDayMembershipOrExerciseSequence() {
        val original = NativeProgramEditDraft.from(verified())
        val moved = checkNotNull(original.moveDay(0, 1))
        assertEquals(listOf("day-2", "day-1"), moved.days.map { it.id })
        assertEquals(listOf("squat", "rdl"), moved.days[0].exerciseIds)
        assertEquals(listOf("bench", "curl", "row"), moved.days[1].exerciseIds)
        assertEquals(listOf("day-1", "day-2"), original.days.map { it.id })
        assertEquals(reference, moved.reference)
        assertTrue(moved.isChanged)
        assertTrue(moved.matchesCurrent(verified()))
        assertEquals(original.days, moved.reset().days)
        assertNull(original.moveDay(-1, 1))
        assertNull(original.moveDay(0, -1))
        assertNull(original.moveDay(1, 1))
        assertNull(original.moveDay(0, 0))
        assertNull(original.moveDay(0, 2))
        assertEquals(original.days, NativeProgramEditDraft.from(verified()).days)
    }

    @Test fun staleCurrentRevisionAndChangedInitialStructureCannotBeReused() {
        val draft=NativeProgramEditDraft.from(verified())
        assertFalse(draft.matchesCurrent(verified(reference.copy(snapshotSha256="b".repeat(64)))))
        assertFalse(draft.matchesCurrent(verified(reference.copy(programUpdatedAt=13))))
        val renamed=checkNotNull(draft.renameProgram("different"))
        assertTrue(renamed.matchesCurrent(verified()))
    }

    @Test fun nestedMovementCollectionsCannotMutateTheOriginalOrDraft() {
        val draft=NativeProgramEditDraft.from(verified())
        assertTrue(runCatching {
            @Suppress("UNCHECKED_CAST")
            (draft.days as MutableList<ReadOnlyProgramDay>).clear()
        }.isFailure)
        assertTrue(runCatching {
            @Suppress("UNCHECKED_CAST")
            (draft.days.first().exerciseIds as MutableList<String>).clear()
        }.isFailure)
        assertEquals(2,draft.days.size)
        assertEquals(3,draft.days[0].exerciseIds.size)
        val changed=checkNotNull(draft.moveExercise("day-1",1,1))
        assertTrue(runCatching {
            @Suppress("UNCHECKED_CAST")
            (changed.days.first().exerciseIds as MutableList<String>).add("unexpected")
        }.isFailure)
        assertEquals(listOf("bench","row","curl"),changed.days[0].exerciseIds)
        assertEquals(listOf("bench","curl","row"),draft.days[0].exerciseIds)
    }

    @Test fun repeatedExerciseSlotsHaveIndependentImmutableIdentity() {
        val base=verified()
        val summary=base.program.copy(days=listOf(ReadOnlyProgramDay("day-1","Repeated",listOf("bench","bench","curl"))))
        val structure=ReadOnlyProgramPresentationPolicy.inspect(summary,
            CurrentProgramEvidence(reference,CurrentProgramDeletionEvidence.CheckedAbsent)) as ReadOnlyProgramPresentation.Structure
        val original=NativeProgramEditDraft.from(structure)
        val moved=checkNotNull(original.moveExercise("day-1",0,1))
        assertEquals(original.days,moved.days)
        assertTrue(moved.isChanged)
        assertEquals(listOf(1,0,2),moved.originalSlots("day-1"))
        assertEquals(listOf(0,1,2),original.originalSlots("day-1"))
        assertTrue(runCatching { (moved.originalSlots("day-1") as MutableList<Int>).clear() }.isFailure)
        val request=org.json.JSONObject(NativeProgramEditCodec.editJson(moved,"edit-repeat",30))
        assertEquals("[1,0,2]",request.getJSONArray("days").getJSONObject(0).getJSONArray("slots").toString())
        assertFalse(moved.reset().isChanged)
    }

    @Test fun savedWorkspaceRestoresAllDraftIdentityAndRejectsOtherRevisions() {
        val approved=verified()
        val draft=checkNotNull(checkNotNull(checkNotNull(checkNotNull(NativeProgramEditDraft.from(approved)
            .moveDay(0,1)).moveExercise("day-1",0,1)).renameDay("day-1","Changed day")).renameProgram("Changed plan"))
        val raw=NativeProgramEditCodec.workspaceJson(draft)
        val restored=checkNotNull(NativeProgramEditCodec.restoreWorkspace(approved,raw))
        assertEquals(draft.days,restored.days)
        assertEquals(draft.name,restored.name)
        assertEquals(draft.originalSlots("day-1"),restored.originalSlots("day-1"))
        assertEquals(draft.reference,restored.reference)
        assertNull(NativeProgramEditCodec.restoreWorkspace(verified(reference.copy(snapshotSha256="b".repeat(64))),raw))
        assertNull(NativeProgramEditCodec.restoreWorkspace(approved,raw.replace("Changed plan","")))
        assertNull(NativeProgramEditCodec.restoreWorkspace(approved,"{}"))
        assertFalse(raw.contains("operationId"))
    }

    @Test fun removalRetainsRepeatedSlotIdentityAndRestoresSavedWorkspace() {
        val base=verified()
        val summary=base.program.copy(days=listOf(
            ReadOnlyProgramDay("day-1","Repeated",listOf("bench","bench","curl"))))
        val structure=ReadOnlyProgramPresentationPolicy.inspect(summary,
            CurrentProgramEvidence(reference,CurrentProgramDeletionEvidence.CheckedAbsent))
            as ReadOnlyProgramPresentation.Structure
        val original=NativeProgramEditDraft.from(structure)
        val removed=checkNotNull(original.removeExercise("day-1",0))
        assertEquals(listOf("bench","curl"),removed.days[0].exerciseIds)
        assertEquals(listOf(1,2),removed.originalSlots("day-1"))
        assertEquals(1,removed.removedMovementCount)
        assertEquals(2,org.json.JSONObject(NativeProgramEditCodec.editJson(removed,"edit-remove",30)).getInt("format"))
        val restored=checkNotNull(NativeProgramEditCodec.restoreWorkspace(structure,
            NativeProgramEditCodec.workspaceJson(removed)))
        assertEquals(removed.originalSlots("day-1"),restored.originalSlots("day-1"))
        val moved=checkNotNull(removed.moveExercise("day-1",1,-1))
        assertEquals(listOf(2,1),checkNotNull(NativeProgramEditCodec.restoreWorkspace(
            structure,NativeProgramEditCodec.workspaceJson(moved))).originalSlots("day-1"))
        assertNull(removed.removeExercise("day-1",-1))
        assertNull(removed.removeExercise("day-1",2))
        assertNull(original.removeExercise("missing",0))
        assertNull(checkNotNull(removed.removeExercise("day-1",0)).removeExercise("day-1",0))
        assertFalse(original.isChanged)
    }

    @Test fun singleSubstitutionStaysIsolatedFromOtherOrganizationEditsAndRestores() {
        val original=NativeProgramEditDraft.from(verified())
        val switched=checkNotNull(original.substituteExercise("day-1",1,"rdl"))
        assertEquals(listOf("bench","rdl","row"),switched.days[0].exerciseIds)
        assertEquals(NativeProgramReplacement("day-1",1,"rdl"),switched.replacement)
        val payload=org.json.JSONObject(NativeProgramEditCodec.editJson(switched,"sub-once",30))
        assertEquals(3,payload.getInt("format"))
        assertEquals("rdl",payload.getJSONObject("replacement").getString("exerciseId"))
        assertEquals("[0,1,2]",payload.getJSONArray("days").getJSONObject(0).getJSONArray("slots").toString())
        val restored=checkNotNull(NativeProgramEditCodec.restoreWorkspace(
            verified(),NativeProgramEditCodec.workspaceJson(switched)))
        assertEquals(switched.days,restored.days)
        assertEquals(switched.replacement,restored.replacement)
        assertNull(switched.moveExercise("day-1",0,1))
        assertNull(switched.removeExercise("day-1",0))
        assertNull(switched.renameProgram("Different"))
        assertNull(original.substituteExercise("day-1",0,"bench"))
        assertNotNull(original.substituteExercise("day-1",1,"anything-unknown"))
        // The immutable draft accepts a syntactically valid catalog choice;
        // the canonical save preparation rejects unknown exercise identities.
        assertNull(original.substituteExercise("day-1",1,"row"))
        assertFalse(original.isChanged)
        assertFalse(switched.reset().isChanged)
    }

    @Test fun invalidMovementOperationsOrLabelsRefuseWithoutPartialMutation() {
        val draft=NativeProgramEditDraft.from(verified())
        assertNull(draft.moveExercise("day-bogus",0,1))
        assertNull(draft.moveExercise("day-1",0,-1))
        assertNull(draft.moveExercise("day-1",2,1))
        assertNull(draft.moveExercise("day-1",1,0))
        assertNull(draft.moveExercise("day-1",-1,1))
        assertNull(draft.renameDay("day-missing","Valid"))
        assertNull(draft.renameDay("day-1","\nBad"))
        assertNull(draft.renameProgram(""))
        assertNull(draft.renameProgram("x".repeat(121)))
        assertEquals(listOf("bench","curl","row"),draft.days[0].exerciseIds)
        assertEquals("Upper",draft.days[0].label)
    }
}
