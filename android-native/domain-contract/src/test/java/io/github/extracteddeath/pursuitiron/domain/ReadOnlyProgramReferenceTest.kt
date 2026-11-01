package io.github.extracteddeath.pursuitiron.domain

import org.junit.Assert.*
import org.junit.Test

class ReadOnlyProgramReferenceTest {
    private val ref = ReadOnlyProgramReference(
        programId = "synthetic-program-1",
        programUpdatedAt = 42,
        storeSavedAt = 100,
        storeVersion = 13,
        snapshotSha256 = "a".repeat(64)
    )

    @Test fun exactActiveReferenceMayBePresentedOnlyAsReadOnly() {
        assertTrue(ReadOnlyProgramReferenceGate.sameActiveRevision(ref,ref,null))
        assertTrue(ReadOnlyProgramReferenceGate.sameActiveRevision(ref,ref,41))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(ref,ref,42))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(ref,ref,43))
    }

    @Test fun staleOrDeletedRecordIsNotResolvedByGuessing() {
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref,ref.copy(programUpdatedAt=43),null))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref,ref.copy(storeSavedAt=101),null))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref,ref.copy(programId="other-program"),null))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref,ref.copy(snapshotSha256="b".repeat(64)),null))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref,ref.copy(storeVersion=14),null))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref,ref.copy(snapshotSha256="not-verified"),null))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref,ref.copy(programUpdatedAt=-1),null))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(ref,ref,-1))
    }

    @Test fun nodeAndKotlinRejectTheSameUnsafeIdentityAndClockShapes() {
        for (badId in listOf("", " ", "__proto__", "-invalid",
            "contains space", "contains\\nnewline", "x".repeat(129))) {
            assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
                ref.copy(programId = badId), ref.copy(programId = badId), null))
        }
        val beyondExactJs = 9_007_199_254_740_992L
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref.copy(programUpdatedAt = beyondExactJs),
            ref.copy(programUpdatedAt = beyondExactJs), null))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref.copy(storeSavedAt = beyondExactJs),
            ref.copy(storeSavedAt = beyondExactJs), null))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref, ref, beyondExactJs))
        assertFalse(ReadOnlyProgramReferenceGate.sameActiveRevision(
            ref.copy(snapshotSha256 = "A".repeat(64)),
            ref.copy(snapshotSha256 = "A".repeat(64)), null))
    }

    @Test fun referenceDoesNotContainOrReconstructTrainingData() {
        assertEquals(listOf("programId","programUpdatedAt","storeSavedAt",
            "storeVersion","snapshotSha256"),
            ReadOnlyProgramReference::class.java.declaredFields
                .filter { !it.isSynthetic }.map { it.name }
                .filter { !it.startsWith("$") })
    }
}
