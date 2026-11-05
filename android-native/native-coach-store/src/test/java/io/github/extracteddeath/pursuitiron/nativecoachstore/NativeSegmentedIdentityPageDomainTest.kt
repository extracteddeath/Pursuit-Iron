package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryManifestEntry
import org.junit.Assert.*
import org.junit.Test

class NativeSegmentedIdentityPageDomainTest {
    private val digest = "a".repeat(64)
    private fun entry(n: Long, receipt: String = "receipt-${100000L - n}") =
        KotlinHistoryManifestEntry(n, "workout-$n", receipt,
            "finish-$n", 2, 0)
    private fun prepared(items: List<KotlinHistoryManifestEntry>) =
        (NativeSegmentedIdentityPageDomain.prepare(
            "owner-1", "athlete-1", digest, 12, items.size.toLong(), items)
            as NativeSegmentedIdentityPageDecision.Prepared).directory

    @Test fun indexesMoreThan4096InBoundedPagesWithoutDroppingOldIdentities() {
        val items = (1L..4097L).map { entry(it) }
        val snapshot = prepared(items)
        assertEquals(4097L, snapshot.completionCount)
        assertEquals(33, snapshot.workoutPages.size)
        assertEquals(33, snapshot.receiptPages.size)
        assertTrue(snapshot.workoutPages.all { it.rows.size <= 128 })
        assertTrue(snapshot.receiptPages.all { it.rows.size <= 128 })
        assertEquals((1L..4097L).toList(),
            snapshot.workoutPages.flatMap { it.rows }.map {
                it.completionOrder
            }.sorted())
        assertFalse(snapshot.canAuthorizeNewWorkout)
        assertFalse(snapshot.canPublishHistory)
        assertFalse(snapshot.canIssueProgression)
        assertFalse(snapshot.canTransferAuthority)
        for (ordinal in listOf(1L, 128L, 129L, 4096L, 4097L)) {
            val fromWorkout = NativeSegmentedIdentityPageDomain.findWorkout(
                snapshot, "owner-1", "athlete-1", digest, 12,
                "workout-$ordinal")
                as NativeSegmentedIdentityPageDecision.Located
            assertEquals(ordinal, fromWorkout.row?.completionOrder)
            assertFalse(fromWorkout.canAuthorizeNewWorkout)
            val fromReceipt = NativeSegmentedIdentityPageDomain.findReceipt(
                snapshot, "owner-1", "athlete-1", digest, 12,
                "receipt-${100000L - ordinal}")
                as NativeSegmentedIdentityPageDecision.Located
            assertEquals(ordinal, fromReceipt.row?.completionOrder)
            assertFalse(fromReceipt.canAuthorizeCorrection)
        }
    }

    @Test fun missingEntriesDuplicatesAndStaleRootAllFailClosed() {
        val original=(1L..257L).map { entry(it) }
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.MISSING_COMPLETION),
            NativeSegmentedIdentityPageDomain.prepare(
                "owner-1","athlete-1",digest,12,256,
                original.filterNot { it.completionOrder==128L }))
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.DUPLICATE_WORKOUT),
            NativeSegmentedIdentityPageDomain.prepare(
                "owner-1","athlete-1",digest,12,257,
                original.dropLast(1)+original.last().copy(workoutId="workout-1")))
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.DUPLICATE_RECEIPT),
            NativeSegmentedIdentityPageDomain.prepare(
                "owner-1","athlete-1",digest,12,257,
                original.dropLast(1)+original.last().copy(storedReceiptId=original[0].storedReceiptId)))
        val safe=prepared(original)
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.STALE_ROOT),
            NativeSegmentedIdentityPageDomain.findWorkout(
                safe,"owner-1","athlete-1","b".repeat(64),12,"workout-1"))
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.STALE_ROOT),
            NativeSegmentedIdentityPageDomain.findReceipt(
                safe,"owner-1","other",digest,12,original[0].storedReceiptId))
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.STALE_ROOT),
            NativeSegmentedIdentityPageDomain.findWorkout(
                safe,"owner-1","athlete-1",digest,13,"workout-1"))
        assertNull((NativeSegmentedIdentityPageDomain.findWorkout(
            safe,"owner-1","athlete-1",digest,12,"absent-workout")
            as NativeSegmentedIdentityPageDecision.Located).row)
    }

    @Test fun corruptedSelectedPageIsNeverExposedAsAnIndexedMatch() {
        val directory=prepared((1L..260L).map { entry(it) })
        val real=directory.workoutPages[1]
        val modified=real.copy(rows=real.rows.mapIndexed { index,row ->
            if (index==3) row.copy(completionOrder=50000) else row
        })
        val forged=directory.copy(workoutPages=
            directory.workoutPages.toMutableList().also { it[1]=modified })
        val target=real.rows[3].workoutId
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.CORRUPT_PAGE),
            NativeSegmentedIdentityPageDomain.findWorkout(
                forged,"owner-1","athlete-1",digest,12,target))
    }

    @Test fun append4097CopiesOnlyBoundedIndexPagesAndFencesOldRoot() {
        val initial=prepared((1L..4096L).map { entry(it) })
        val inserted=entry(4097)
        val decision=NativeSegmentedIdentityPageDomain.proposeAppend(
            initial,digest,12,"b".repeat(64),13,inserted)
        assertTrue(decision.toString(),decision is NativeSegmentedIdentityPageDecision.Prepared)
        val after=(decision as NativeSegmentedIdentityPageDecision.Prepared).directory
        assertEquals(4097L,after.completionCount)
        assertEquals(13L,after.sourceRevision)
        assertEquals(33,after.workoutPages.size)
        assertEquals(33,after.receiptPages.size)
        assertTrue(after.workoutPages.all { it.rows.size<=128 })
        assertTrue(after.receiptPages.all { it.rows.size<=128 })
        assertEquals(4096L,initial.completionCount)
        assertFalse(after.canAuthorizeNewWorkout)
        assertTrue(after.workoutPages.any { newer ->
            initial.workoutPages.any { older -> newer === older }
        })
        val found=NativeSegmentedIdentityPageDomain.findWorkout(
            after,"owner-1","athlete-1","b".repeat(64),13,"workout-4097")
            as NativeSegmentedIdentityPageDecision.Located
        assertEquals(4097L,found.row?.completionOrder)
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.STALE_ROOT),
            NativeSegmentedIdentityPageDomain.findWorkout(
                after,"owner-1","athlete-1",digest,12,"workout-4097"))
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.DUPLICATE_WORKOUT),
            NativeSegmentedIdentityPageDomain.proposeAppend(
                initial,digest,12,"b".repeat(64),13,
                inserted.copy(workoutId="workout-1")))
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.DUPLICATE_RECEIPT),
            NativeSegmentedIdentityPageDomain.proposeAppend(
                initial,digest,12,"b".repeat(64),13,
                inserted.copy(storedReceiptId=entry(1).storedReceiptId)))
        assertEquals(NativeSegmentedIdentityPageDecision.Rejected(
            NativeSegmentedIdentityPageRefusal.STALE_ROOT),
            NativeSegmentedIdentityPageDomain.proposeAppend(
                initial,digest,11,"b".repeat(64),13,inserted))
    }

    @Test fun fullPageSplitsInsteadOfGrowingUnbounded() {
        val old=prepared((1L..128L).map { entry(it) })
        val append=NativeSegmentedIdentityPageDomain.proposeAppend(
            old,digest,12,"b".repeat(64),13,
            entry(129,receipt="receipt-500").copy(workoutId="workout-64-extra"))
        assertTrue(append.toString(),append is NativeSegmentedIdentityPageDecision.Prepared)
        val next=(append as NativeSegmentedIdentityPageDecision.Prepared).directory
        assertEquals(listOf(64,65),next.workoutPages.map { it.rows.size })
        assertEquals(listOf(64,65),next.receiptPages.map { it.rows.size })
        assertEquals(128,old.workoutPages.single().rows.size)
        assertEquals(129L,next.completionCount)
        assertFalse(next.canPublishHistory)
    }

    @Test fun emptyCandidateStillDoesNotAuthorizeAnAbsentWorkout() {
        val empty=prepared(emptyList())
        assertTrue(empty.workoutPages.isEmpty())
        assertTrue(empty.receiptPages.isEmpty())
        val absent=NativeSegmentedIdentityPageDomain.findWorkout(
            empty,"owner-1","athlete-1",digest,12,"new-workout")
            as NativeSegmentedIdentityPageDecision.Located
        assertNull(absent.row)
        assertFalse(absent.canAuthorizeNewWorkout)
    }
}
