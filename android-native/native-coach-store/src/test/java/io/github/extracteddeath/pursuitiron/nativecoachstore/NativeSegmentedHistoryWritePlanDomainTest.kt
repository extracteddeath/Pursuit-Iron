package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class NativeSegmentedHistoryWritePlanDomainTest {
    private fun digest(c: Char) = c.toString().repeat(64)
    private fun head(count: Long, revision: Long = 7) =
        NativeSegmentedWriteHead("owner-1", "athlete-1",
            revision, count, digest('a'))
    private fun entry(i: Long, custom: Boolean = false, edits: Long = 0) =
        KotlinHistoryManifestEntry(i, "workout-$i",
            if (custom) "custom-v2-receipt-$i" else "stored-$i",
            "finish-$i", 2, edits)
    private fun segment(head: NativeSegmentedWriteHead,
        first: Long, count: Int) = NativeSegmentedWriteSegment(
            head.rootDigest, first,
            (first until first + count).map { entry(it) })
    private fun finish(number: Long) = NativeHistoryWriteIntent.Finish(
        "workout-$number", KotlinJournalOperation.Finish("finish-$number", 1))
    private fun customFinish(number: Long) = NativeHistoryWriteIntent.CustomFinish(
        "workout-$number", CustomWorkoutJournalOperation.Finish("finish-$number", 1))
    private fun correction(number: Long, revision: Long = 0) =
        NativeHistoryWriteIntent.Correct("workout-$number",
            KotlinSavedSetCorrection("edit-$number", revision, "set-$number",
                "set-event-$number", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("195"), LoadUnit.LB, 6, 2)))
    private fun customCorrection(number: Long, revision: Long = 0) =
        NativeHistoryWriteIntent.CustomCorrect("workout-$number",
            CustomSavedVisitCorrection("edit-$number", revision,
                CustomWorkoutVisitKey("set-$number", CustomWorkoutVisitPart.MAIN),
                "set-event-$number", KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("195"), LoadUnit.LB, 6, 2)))
    private fun prepared(
        head: NativeSegmentedWriteHead, intent: NativeHistoryWriteIntent,
        target: KotlinHistoryManifestEntry? = null
    ): NativeSegmentedWriteHead {
        val d = NativeSegmentedHistoryWritePlanDomain.prepare(
            head, head.revision, intent, target)
        assertTrue(d.toString(), d is NativeSegmentedWriteDecision.Prepared)
        return (d as NativeSegmentedWriteDecision.Prepared).head
    }
    private fun patch(result: NativeSegmentedWriteDecision):
        NativeSegmentedWritePatch {
        assertTrue(result.toString(), result is NativeSegmentedWriteDecision.ProposedPatch)
        return (result as NativeSegmentedWriteDecision.ProposedPatch).patch
    }
    private fun rejected(reason: NativeSegmentedWriteRefusal) =
        NativeSegmentedWriteDecision.Rejected(reason)

    @Test fun append129UsesOnlySingleNew128EntrySegment() {
        val h = head(128)
        val intent = finish(129)
        val staged = prepared(h, intent)
        val result = patch(NativeSegmentedHistoryWritePlanDomain.finalize(
            staged, intent, null, segment(h, 129, 0), entry(129).copy(
                originalJournalRevision = 2)))
        assertEquals(129L, result.nextCompletionCount)
        assertEquals(8L, result.nextHeadRevision)
        assertEquals(129L, result.replaceSegmentFirstOrdinal)
        assertEquals(listOf(entry(129)), result.completions)
        assertFalse(result.canPublish)
        assertFalse(result.canIssueProgression)
    }

    @Test fun appendAfter4096DoesNotLoadOrTruncateEarlierCompletions() {
        val h = head(4096, 100)
        val intent = finish(4097)
        val result = patch(NativeSegmentedHistoryWritePlanDomain.finalize(
            prepared(h, intent), intent, null,
            segment(h, 4097, 0), entry(4097)))
        assertEquals(4097L, result.nextCompletionCount)
        assertEquals(4097L, result.replaceSegmentFirstOrdinal)
        assertEquals(1, result.completions.size)
        assertEquals(101L, result.nextHeadRevision)
    }

    @Test fun correctionOfOldWorkoutTouchesOnlyItsSegment() {
        val h = head(512)
        val original = entry(160)
        val intent = correction(160)
        val staged = prepared(h, intent, original)
        val page = segment(h, 129, 128)
        val updated = original.copy(savedCorrectionRevision = 1)
        val result = patch(NativeSegmentedHistoryWritePlanDomain.finalize(
            staged, intent, original, page, updated))
        assertEquals(129L, result.replaceSegmentFirstOrdinal)
        assertEquals(512L, result.nextCompletionCount)
        assertEquals(updated, result.completions[31])
        assertEquals(page.completions.take(31), result.completions.take(31))
        assertEquals(page.completions.drop(32), result.completions.drop(32))
        assertEquals(8L, result.nextHeadRevision)
    }

    @Test fun customFinishAndCustomCorrectionPreserveExactReceipt() {
        val h = head(1)
        val intent = customFinish(2)
        val staged = prepared(h, intent)
        val finished = entry(2, custom = true)
        val appended = patch(NativeSegmentedHistoryWritePlanDomain.finalize(
            staged, intent, null, segment(h, 1, 1), finished))
        assertEquals(finished, appended.completions.last())
        val later = head(2, 8)
        val editIntent = customCorrection(2)
        val editStaged = prepared(later, editIntent, finished)
        val segment = NativeSegmentedWriteSegment(later.rootDigest, 1,
            listOf(entry(1), finished))
        val corrected = finished.copy(savedCorrectionRevision = 1)
        val patch = patch(NativeSegmentedHistoryWritePlanDomain.finalize(
            editStaged, editIntent, finished, segment, corrected))
        assertEquals(listOf(entry(1), corrected), patch.completions)
        assertEquals(9L, patch.nextHeadRevision)
    }

    @Test fun stalePendingAndWrongReceiptNeverProducePatch() {
        val h = head(130)
        val intent = finish(131)
        assertEquals(rejected(NativeSegmentedWriteRefusal.REVISION_CONFLICT),
            NativeSegmentedHistoryWritePlanDomain.prepare(
                h, 6, intent, null))
        val staged = prepared(h, intent)
        assertEquals(rejected(NativeSegmentedWriteRefusal.PENDING_TRANSACTION),
            NativeSegmentedHistoryWritePlanDomain.prepare(
                staged, 7, intent, null))
        val valid = segment(h, 129, 2)
        assertEquals(rejected(NativeSegmentedWriteRefusal.NO_MATCHING_INTENT),
            NativeSegmentedHistoryWritePlanDomain.finalize(
                staged, finish(132), null, valid, entry(132)))
        assertEquals(rejected(NativeSegmentedWriteRefusal.UNVERIFIED_RECEIPT),
            NativeSegmentedHistoryWritePlanDomain.finalize(
                staged, intent, null, valid,
                entry(131).copy(finishEventId = "forged")))
        assertEquals(rejected(NativeSegmentedWriteRefusal.REVISION_CONFLICT),
            NativeSegmentedHistoryWritePlanDomain.finalize(
                staged, intent, null, valid.copy(
                    sourceRootDigest = digest('b')), entry(131)))
    }

    @Test fun correctionMustMatchExactOldEntryAndExpectedRevision() {
        val h = head(150)
        val prior = entry(10)
        val intent = correction(10)
        val staged = prepared(h, intent, prior)
        assertEquals(rejected(NativeSegmentedWriteRefusal.TARGET_CONFLICT),
            NativeSegmentedHistoryWritePlanDomain.prepare(
                h, 7, correction(10, 1), prior))
        assertEquals(rejected(NativeSegmentedWriteRefusal.UNVERIFIED_RECEIPT),
            NativeSegmentedHistoryWritePlanDomain.finalize(
                staged, intent, prior, segment(h, 1, 128),
                prior.copy(savedCorrectionRevision = 3)))
        assertEquals(rejected(NativeSegmentedWriteRefusal.UNVERIFIED_RECEIPT),
            NativeSegmentedHistoryWritePlanDomain.finalize(
                staged, intent, prior.copy(storedReceiptId = "forged"),
                segment(h, 1, 128), prior.copy(savedCorrectionRevision = 1)))
        assertEquals(rejected(NativeSegmentedWriteRefusal.INVALID_SEGMENT),
            NativeSegmentedHistoryWritePlanDomain.finalize(
                staged, intent, prior, segment(h, 2, 128),
                prior.copy(savedCorrectionRevision = 1)))
    }

    @Test fun rejectsMismatchedCustomAndComposerCorrectionKinds() {
        val h = head(150)
        val custom = entry(10, custom = true)
        val normal = entry(10)
        assertEquals(rejected(NativeSegmentedWriteRefusal.TARGET_CONFLICT),
            NativeSegmentedHistoryWritePlanDomain.prepare(
                h, 7, correction(10), custom))
        assertEquals(rejected(NativeSegmentedWriteRefusal.TARGET_CONFLICT),
            NativeSegmentedHistoryWritePlanDomain.prepare(
                h, 7, customCorrection(10), normal))
        assertEquals(rejected(NativeSegmentedWriteRefusal.TARGET_CONFLICT),
            NativeSegmentedHistoryWritePlanDomain.prepare(
                h, 7, finish(10), normal))
    }

    @Test fun failsClosedForInvalidHeadAndOrdinalGaps() {
        val h = head(129)
        val intent = finish(130)
        assertEquals(rejected(NativeSegmentedWriteRefusal.INVALID_STATE),
            NativeSegmentedHistoryWritePlanDomain.prepare(
                h.copy(ownerKey = "../owner"), 7, intent, null))
        assertEquals(rejected(NativeSegmentedWriteRefusal.INVALID_SEGMENT),
            NativeSegmentedHistoryWritePlanDomain.finalize(
                prepared(h, intent), intent, null,
                NativeSegmentedWriteSegment(h.rootDigest, 129,
                    listOf(entry(128))),
                entry(130)))
        assertEquals(rejected(NativeSegmentedWriteRefusal.INVALID_STATE),
            NativeSegmentedHistoryWritePlanDomain.prepare(
                h.copy(rootDigest = "bad"), 7, intent, null))
    }
}
