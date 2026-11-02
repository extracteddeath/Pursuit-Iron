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
