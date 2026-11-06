package io.github.extracteddeath.pursuitiron.nativecoachstore

import org.junit.Assert.*
import org.junit.Test

/** No source transition can rely on an old UI "workout completed" flag. */
class NativeComposerActivationInventoryDomainTest {
    private fun saved(id:String,finish:String?)=
        NativeComposerInventoryFact(id,finish)
    private fun check(
        issued:List<NativeComposerInventoryFact>,
        manifest:Map<String,String> =emptyMap()
    )=NativeComposerActivationInventoryDomain.review(issued,manifest)

    @Test fun noComposerJournalsIsAReviewableEmptyInventory() {
        val approved=check(emptyList())
        assertEquals(NativeComposerActivationInventoryResult.Clear(0,0),approved)
        assertFalse((approved as NativeComposerActivationInventoryResult.Clear).mayActivate)
    }

    @Test fun issuedUnfinishedComposerBlocksAnyFutureCustomScheduleChange() {
        assertEquals(NativeComposerActivationInventoryResult.Rejected(
            NativeComposerActivationInventoryRefusal.UNRESOLVED_COMPOSER_WORKOUT),
            check(listOf(saved("other-program-run",null))))
        assertEquals(NativeComposerActivationInventoryResult.Rejected(
            NativeComposerActivationInventoryRefusal.UNRESOLVED_COMPOSER_WORKOUT),
            check(listOf(saved("done","finish-1"),
                saved("different-program",null)),
                mapOf("done" to "finish-1")))
    }

    @Test fun identicalVerifiedFinishReceiptsAllowReadOnlyBoundaryReview() {
        val reviewed=check(listOf(saved("day1","done1"),
            saved("day2","done2")),mapOf("day2" to "done2","day1" to "done1"))
        assertEquals(NativeComposerActivationInventoryResult.Clear(2,2),reviewed)
        assertFalse((reviewed as NativeComposerActivationInventoryResult.Clear).mayActivate)
    }

    @Test fun finishedJournalMissingManifestCannotMasqueradeAsCompletion() {
        assertEquals(NativeComposerActivationInventoryResult.Rejected(
            NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT),
            check(listOf(saved("day1","finish-1"))))
    }

    @Test fun manifestNotBackedByNativeJournalMustBeRejected() {
        assertEquals(NativeComposerActivationInventoryResult.Rejected(
            NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT),
            check(emptyList(),mapOf("unverified" to "finish-1")))
        assertEquals(NativeComposerActivationInventoryResult.Rejected(
            NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT),
            check(listOf(saved("another",null)),
                mapOf("not-present" to "finish-x")))
    }

    @Test fun mismatchedFinishOrDuplicatePhysicalWorkoutBlocksTransition() {
        assertEquals(NativeComposerActivationInventoryResult.Rejected(
            NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT),
            check(listOf(saved("day1","finish-current")),
                mapOf("day1" to "finish-old")))
        assertEquals(NativeComposerActivationInventoryResult.Rejected(
            NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT),
            check(listOf(saved("same",null),saved("same",null))))
        assertEquals(NativeComposerActivationInventoryResult.Rejected(
            NativeComposerActivationInventoryRefusal.JOURNAL_MANIFEST_CONFLICT),
            check(listOf(saved("day1",null)),
                mapOf("day1" to "finish-1")))
    }
}
