package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class ReadOnlyTrainingDayJourneyTest {
    private val ref=ReadOnlyProgramReference(
        "synthetic-program",42,100,13,"a".repeat(64))
    private val summary=ReadOnlyProgramSummary(
        reference=ref,id=ref.programId,name="Synthetic split",custom=true,
        goal="hypertrophy",weeks=6,days=listOf(
            ReadOnlyProgramDay("upper-a","Upper A",listOf("bb-bench","cable-row")),
            ReadOnlyProgramDay("lower-a","Lower A",listOf("squat","rdl"))))
    private fun approved(
        data: ReadOnlyProgramSummary=summary,
        currentRef: ReadOnlyProgramReference=data.reference
    ) = ReadOnlyProgramPresentationPolicy.inspect(
        data,CurrentProgramEvidence(currentRef,CurrentProgramDeletionEvidence.CheckedAbsent))

    @Test fun exactApprovedDayCanBeBrowsedWithoutWorkoutPrescriptions() {
        val current=approved()
        val token=checkNotNull(ReadOnlyTrainingDayJourney.select(current,"upper-a"))
        val view=ReadOnlyTrainingDayJourney.inspect(current,token)
        assertTrue(view is ReadOnlyTrainingDayView.Structure)
        val day=view as ReadOnlyTrainingDayView.Structure
        assertEquals("Upper A",day.dayLabel)
        assertEquals(0,day.dayIndex)
        assertEquals(listOf("bb-bench","cable-row"),day.exerciseIds)
        assertEquals("Synthetic split",day.programName)
        assertFalse(ReadOnlyTrainingDayView.Structure::class.java.declaredMethods
            .any { it.name == "copy" || it.name.startsWith("component") })
    }

    @Test fun noBackupOrUnavailableProgramMaySelectOrDisplayDay() {
        assertNull(ReadOnlyTrainingDayJourney.select(
            ReadOnlyProgramPresentation.Unavailable,"upper-a"))
        val token=ReadOnlyTrainingDaySelection(ref,"upper-a")
        assertSame(ReadOnlyTrainingDayView.Unavailable,
            ReadOnlyTrainingDayJourney.inspect(ReadOnlyProgramPresentation.Unavailable,token))
        assertSame(ReadOnlyTrainingDayView.Unavailable,
            ReadOnlyTrainingDayJourney.inspect(approved(),null))
        assertNull(ReadOnlyTrainingDayJourney.select(approved(),"nonexistent"))
    }

    @Test fun revisionsAndDayRemovalInvalidateExistingSelection() {
        val old=approved()
        val token=checkNotNull(ReadOnlyTrainingDayJourney.select(old,"upper-a"))
        for (changed in listOf(
            ref.copy(programUpdatedAt=43),
            ref.copy(snapshotSha256="b".repeat(64)),
            ref.copy(storeSavedAt=101)
        )) {
            val newData=summary.copy(reference=changed)
            val newer=approved(newData)
            assertTrue(newer is ReadOnlyProgramPresentation.Structure)
            assertSame(ReadOnlyTrainingDayView.Unavailable,
                ReadOnlyTrainingDayJourney.inspect(newer,token))
        }
        val removed=approved(summary.copy(days=listOf(summary.days[1])))
        assertSame(ReadOnlyTrainingDayView.Unavailable,
            ReadOnlyTrainingDayJourney.inspect(removed,token))
        assertSame(ReadOnlyTrainingDayView.Unavailable,
            ReadOnlyTrainingDayJourney.inspect(approved(),
                token.copy(dayId="wrong-day")))
        assertSame(ReadOnlyTrainingDayView.Unavailable,
            ReadOnlyTrainingDayJourney.inspect(approved(),
                token.copy(reference=ref.copy(programId="other"))))
    }

    @Test fun approvedDayExerciseIdsAreDetachedFromSourceMutations() {
        val source=mutableListOf("bb-bench","cable-row")
        val current=approved(summary.copy(days=listOf(summary.days[0].copy(
            exerciseIds=source))))
        val selection=checkNotNull(ReadOnlyTrainingDayJourney.select(current,"upper-a"))
        val view=ReadOnlyTrainingDayJourney.inspect(current,selection)
            as ReadOnlyTrainingDayView.Structure
        source.clear()
        assertEquals(listOf("bb-bench","cable-row"),view.exerciseIds)
        assertTrue(runCatching {
            @Suppress("UNCHECKED_CAST")
            (view.exerciseIds as MutableList<String>).clear()
        }.isFailure)
    }
}
