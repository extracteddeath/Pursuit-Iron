package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class ReadOnlyProgramPresentationPolicyTest {
    private val ref = ReadOnlyProgramReference(
        "synthetic-program",42,100,13,"a".repeat(64))
    private val good = ReadOnlyProgramSummary(
        reference = ref, id = "synthetic-program", name = "Synthetic plan",
        custom = true, goal = "hypertrophy", weeks = 6,
        days = listOf(ReadOnlyProgramDay("day-1","Upper",
            listOf("bb-bench","cable-fly")))
    )
    private fun checked(
        reference: ReadOnlyProgramReference = ref,
        deletion: CurrentProgramDeletionEvidence =
            CurrentProgramDeletionEvidence.CheckedAbsent
    ) = CurrentProgramEvidence(reference, deletion)

    private fun available(
        summary: ReadOnlyProgramSummary? = good,
        evidence: CurrentProgramEvidence? = checked()
    ) = ReadOnlyProgramPresentationPolicy.inspect(summary,evidence) is
        ReadOnlyProgramPresentation.Structure

    @Test fun uncheckedDeletionStateIsNotProofOfAbsence() {
        assertFalse(available(evidence = null))
        assertFalse(available(summary = null))
        assertTrue(available(evidence = checked()))
        assertTrue(available(evidence = checked(
            deletion = CurrentProgramDeletionEvidence.CheckedAt(41))))
        for (clock in listOf(42L,43L,-1L,9_007_199_254_740_992L)) {
            assertFalse(available(evidence = checked(
                deletion = CurrentProgramDeletionEvidence.CheckedAt(clock))))
        }
    }

    @Test fun staleOrMalformedProgramsNeverEnterCompose() {
        assertFalse(available(evidence = checked(ref.copy(programUpdatedAt = 43))))
        assertFalse(available(evidence = checked(ref.copy(snapshotSha256 = "b".repeat(64)))))
        assertFalse(available(summary = good.copy(id = "other")))
        assertFalse(available(summary = good.copy(name = "")))
        assertFalse(available(summary = good.copy(weeks = 0)))
        assertFalse(available(summary = good.copy(days = emptyList())))
        assertFalse(available(summary = good.copy(days = listOf(
            good.days[0],good.days[0]))))
        assertFalse(available(summary = good.copy(days = listOf(
            good.days[0].copy(exerciseIds = listOf("invalid id"))))))
        assertFalse(available(summary = good.copy(
            reference = ref.copy(storeSavedAt = 101))))
    }

    @Test fun approvedPresentationCannotBeClonedOrMutatedViaSourceCollections() {
        // The source may legally provide a MutableList even though its DTO
        // exposes List; UI authorization cannot keep that mutable alias.
        val mutableExercises = mutableListOf("bb-bench", "cable-fly")
        val mutableDays = mutableListOf(
            ReadOnlyProgramDay("day-1", "Upper", mutableExercises)
        )
        val original = good.copy(days = mutableDays)
        val approved = ReadOnlyProgramPresentationPolicy.inspect(
            original, checked()
        ) as ReadOnlyProgramPresentation.Structure
        assertNotSame(original, approved.program)
        assertNotSame(mutableDays, approved.program.days)
        assertNotSame(mutableExercises, approved.program.days[0].exerciseIds)

        mutableExercises[0] = "unverified-exercise"
        mutableDays.clear()
        assertEquals(listOf("bb-bench","cable-fly"),
            approved.program.days[0].exerciseIds)
        assertEquals(1, approved.program.days.size)
        assertTrue(runCatching {
            @Suppress("UNCHECKED_CAST")
            (approved.program.days as MutableList<ReadOnlyProgramDay>).clear()
        }.isFailure)
        assertTrue(runCatching {
            @Suppress("UNCHECKED_CAST")
            (approved.program.days[0].exerciseIds as MutableList<String>).clear()
        }.isFailure)
        assertFalse(ReadOnlyProgramPresentation.Structure::class.java
            .declaredMethods.any { it.name == "copy" ||
                it.name.startsWith("component") })
    }
}
