package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
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
    private fun available(summary: ReadOnlyProgramSummary?, current: ReadOnlyProgramReference?,
        tombstone: Long? = null) =
        ReadOnlyProgramPresentationPolicy.inspect(summary,current,tombstone) is
            ReadOnlyProgramPresentation.Structure

    @Test fun readOnlyMetadataMustBeProvenCurrentWithoutConflictingDeletion() {
        assertTrue(available(good,ref))
        assertTrue(available(good,ref,41))
        assertFalse(available(good,ref,42))
        assertFalse(available(good,ref,43))
        assertFalse(available(good,null))
        assertFalse(available(null,ref))
        assertFalse(available(good,ref.copy(programUpdatedAt=43)))
        assertFalse(available(good,ref.copy(snapshotSha256="b".repeat(64))))
        assertFalse(available(good,ref,-1))
    }

    @Test fun presentationCannotSkipDecoderOrAcceptMalformedProgram() {
        assertFalse(available(good.copy(id="other"),ref))
        assertFalse(available(good.copy(name=""),ref))
        assertFalse(available(good.copy(weeks=0),ref))
        assertFalse(available(good.copy(days=emptyList()),ref))
        assertFalse(available(good.copy(days=listOf(
            good.days[0],good.days[0])),ref))
        assertFalse(available(good.copy(days=listOf(
            good.days[0].copy(exerciseIds=listOf("invalid id")))),ref))
        assertFalse(available(good.copy(reference=ref.copy(storeSavedAt=101)),ref))
    }
}
