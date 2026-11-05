package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.KotlinHistoryManifestEntry
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.security.MessageDigest
import java.util.Collections

/**
 * Read-only layout planner for a FUTURE paged workout/receipt identity index.
 *
 * Unlike the 4,096-row v2-backed candidate table, the bounded page format
 * has no artificial 4,096-completion limit. Every page holds at most 128
 * rows, in separate workout and receipt key orders. It is still an in-memory
 * REHEARSAL from caller-provided records, NOT a persistent index, uniqueness
 * proof, authenticated migration, history owner, or permission to write.
 *
 * A future durable writer must validate whole-history provenance, persist
 * immutable pages and their directory under the owner fence, CAS both page
 * trees with the segmented root, and replay source journals on recovery.
 */
internal data class NativeSegmentedIdentityPage(
    val firstKey: String,
    val lastKey: String,
    val contentDigest: String,
    val rows: List<NativeCandidateIndexedWorkout>
)

internal data class NativeSegmentedIdentityPageDirectory(
    val ownerKey: String,
    val athleteId: String,
    val sourceRootDigest: String,
    val sourceRevision: Long,
    val completionCount: Long,
    val workoutPages: List<NativeSegmentedIdentityPage>,
    val receiptPages: List<NativeSegmentedIdentityPage>
) {
    val canAuthorizeNewWorkout: Boolean get() = false
    val canPublishHistory: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val canTransferAuthority: Boolean get() = false
}

internal enum class NativeSegmentedIdentityPageRefusal {
    INVALID_SCOPE, INVALID_HISTORY, MISSING_COMPLETION,
    DUPLICATE_WORKOUT, DUPLICATE_RECEIPT, STALE_ROOT, INVALID_KEY,
    CORRUPT_PAGE
}

internal sealed interface NativeSegmentedIdentityPageDecision {
    data class Prepared(val directory: NativeSegmentedIdentityPageDirectory) :
        NativeSegmentedIdentityPageDecision
    data class Located(
        val row: NativeCandidateIndexedWorkout?
    ) : NativeSegmentedIdentityPageDecision {
        val canAuthorizeNewWorkout: Boolean get() = false
        val canAuthorizeCorrection: Boolean get() = false
    }
    data class Rejected(val reason: NativeSegmentedIdentityPageRefusal) :
        NativeSegmentedIdentityPageDecision
}

internal object NativeSegmentedIdentityPageDomain {
    const val PAGE_SIZE = 128
    private const val MAX_REVISION = 9_007_199_254_740_991L
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digest = Regex("^[a-f0-9]{64}$")
    private enum class KeyType { WORKOUT, RECEIPT }

    private fun deny(reason: NativeSegmentedIdentityPageRefusal) =
        NativeSegmentedIdentityPageDecision.Rejected(reason)

    private fun key(row: NativeCandidateIndexedWorkout, kind: KeyType) =
        if (kind == KeyType.WORKOUT) row.workoutId else row.storedReceiptId

    private fun digestPage(
        kind: KeyType, rows: List<NativeCandidateIndexedWorkout>
    ): String {
        val out = ByteArrayOutputStream()
        DataOutputStream(out).use { d ->
            d.writeInt(1)
            d.writeUTF(kind.name)
            d.writeInt(rows.size)
            for (row in rows) {
                d.writeUTF(row.workoutId)
                d.writeUTF(row.storedReceiptId)
                d.writeLong(row.completionOrder)
            }
        }
        return MessageDigest.getInstance("SHA-256")
            .digest(out.toByteArray()).joinToString("") {
                "%02x".format(it.toInt() and 255)
            }
    }

    private fun pages(
        rows: List<NativeCandidateIndexedWorkout>, kind: KeyType
    ): List<NativeSegmentedIdentityPage> {
        val ordered = rows.sortedBy { key(it, kind) }
        return Collections.unmodifiableList(
            ordered.chunked(PAGE_SIZE).map { chunk ->
                val safeRows = Collections.unmodifiableList(chunk.toList())
                NativeSegmentedIdentityPage(
                    key(safeRows.first(), kind),
                    key(safeRows.last(), kind),
                    digestPage(kind, safeRows), safeRows
                )
            }
        )
    }

    /** The caller MUST independently prove these are every durable journal. */
    fun prepare(
        owner: String, athlete: String, sourceRootDigest: String,
        sourceRevision: Long, expectedCompletionCount: Long,
        completeHistory: List<KotlinHistoryManifestEntry>
    ): NativeSegmentedIdentityPageDecision {
        if (!id.matches(owner) || !id.matches(athlete) ||
            !digest.matches(sourceRootDigest) ||
            sourceRevision !in 1..MAX_REVISION ||
            expectedCompletionCount !in 0 until MAX_REVISION)
            return deny(NativeSegmentedIdentityPageRefusal.INVALID_SCOPE)
        if (completeHistory.size.toLong() != expectedCompletionCount ||
            completeHistory.any {
                !id.matches(it.workoutId) ||
                    !id.matches(it.storedReceiptId) ||
                    !id.matches(it.finishEventId) ||
                    it.originalJournalRevision !in 1..MAX_REVISION ||
                    it.savedCorrectionRevision !in 0..MAX_REVISION
            })
            return deny(NativeSegmentedIdentityPageRefusal.INVALID_HISTORY)
        if (completeHistory.indices.any { i ->
                completeHistory[i].completionOrder != i.toLong() + 1L
            })
            return deny(NativeSegmentedIdentityPageRefusal.MISSING_COMPLETION)
        if (completeHistory.map { it.workoutId }.toSet().size !=
            completeHistory.size)
            return deny(NativeSegmentedIdentityPageRefusal.DUPLICATE_WORKOUT)
        if (completeHistory.map { it.storedReceiptId }.toSet().size !=
            completeHistory.size)
            return deny(NativeSegmentedIdentityPageRefusal.DUPLICATE_RECEIPT)
        val rows = completeHistory.map {
            NativeCandidateIndexedWorkout(
                it.workoutId, it.storedReceiptId, it.completionOrder
            )
        }
        return NativeSegmentedIdentityPageDecision.Prepared(
            NativeSegmentedIdentityPageDirectory(
                owner, athlete, sourceRootDigest, sourceRevision,
                expectedCompletionCount,
                pages(rows, KeyType.WORKOUT),
                pages(rows, KeyType.RECEIPT)
            )
        )
    }

    /**
     * Lookup in an already prepared snapshot; a null row DOES NOT prove
     * absence in authentic lifetime history. The expected root/revision
     * arguments are caller-supplied, not independently authenticated here.
     */
    private fun find(
        directory: NativeSegmentedIdentityPageDirectory,
        owner: String, athlete: String,
        expectedRootDigest: String, expectedRevision: Long,
        searchKey: String, kind: KeyType
    ): NativeSegmentedIdentityPageDecision {
        if (!id.matches(searchKey))
            return deny(NativeSegmentedIdentityPageRefusal.INVALID_KEY)
        if (owner != directory.ownerKey || athlete != directory.athleteId ||
            expectedRootDigest != directory.sourceRootDigest ||
            expectedRevision != directory.sourceRevision)
            return deny(NativeSegmentedIdentityPageRefusal.STALE_ROOT)
        val pages = if (kind == KeyType.WORKOUT)
            directory.workoutPages else directory.receiptPages
        val pageIndex = pages.binarySearch { page ->
            when {
                searchKey < page.firstKey -> 1
                searchKey > page.lastKey -> -1
                else -> 0
            }
        }
        if (pageIndex < 0)
            return NativeSegmentedIdentityPageDecision.Located(null)
        val page = pages[pageIndex]
        if (page.rows.size !in 1..PAGE_SIZE ||
            page.firstKey != key(page.rows.first(), kind) ||
            page.lastKey != key(page.rows.last(), kind) ||
            page.rows.zipWithNext().any { (a, b) -> key(a, kind) >= key(b, kind) } ||
            digestPage(kind, page.rows) != page.contentDigest)
            return deny(NativeSegmentedIdentityPageRefusal.CORRUPT_PAGE)
        val index = page.rows.binarySearchBy(searchKey) { key(it, kind) }
        return NativeSegmentedIdentityPageDecision.Located(
            if (index < 0) null else page.rows[index]
        )
    }

    /** Change at most one page per key tree, splitting at 128 rows. */
    private fun insertPage(
        current: List<NativeSegmentedIdentityPage>,
        row: NativeCandidateIndexedWorkout,
        kind: KeyType
    ): List<NativeSegmentedIdentityPage>? {
        if (current.isEmpty()) return pages(listOf(row), kind)
        val newKey = key(row, kind)
        // Find the first page ending at/after this key (or the last page).
        // The rest of the already verified directory is not materialized.
        val result = current.binarySearch { page ->
            page.lastKey.compareTo(newKey)
        }
        val index = if (result >= 0) result
            else minOf(current.lastIndex, -result - 1)
        val old = current[index]
        if (old.rows.size !in 1..PAGE_SIZE ||
            old.firstKey != key(old.rows.first(), kind) ||
            old.lastKey != key(old.rows.last(), kind) ||
            old.rows.zipWithNext().any { (a,b) -> key(a, kind) >= key(b, kind) } ||
            digestPage(kind, old.rows) != old.contentDigest ||
            old.rows.any { key(it, kind) == newKey })
            return null
        val replacementRows = (old.rows + row).sortedBy { key(it, kind) }
        val split = if (replacementRows.size > PAGE_SIZE)
            listOf(replacementRows.take(PAGE_SIZE / 2),
                replacementRows.drop(PAGE_SIZE / 2))
        else listOf(replacementRows)
        val updated = current.toMutableList()
        updated.removeAt(index)
        updated.addAll(index, split.map { rows ->
            val frozen = Collections.unmodifiableList(rows.toList())
            NativeSegmentedIdentityPage(
                key(frozen.first(), kind), key(frozen.last(), kind),
                digestPage(kind, frozen), frozen)
        })
        return Collections.unmodifiableList(updated)
    }

    /**
     * PROSPECTIVE, non-durable copy-on-write page layout for a new Finish.
     * An independent native owner must first prove that this directory is
     * complete/current and that the new journal is a real durable Finish.
     * This reducer alone cannot authorize an insert or publish history.
     */
    fun proposeAppend(
        current: NativeSegmentedIdentityPageDirectory,
        expectedRootDigest: String, expectedRevision: Long,
        nextRootDigest: String, nextRevision: Long,
        newEntry: KotlinHistoryManifestEntry
    ): NativeSegmentedIdentityPageDecision {
        if (expectedRootDigest != current.sourceRootDigest ||
            expectedRevision != current.sourceRevision)
            return deny(NativeSegmentedIdentityPageRefusal.STALE_ROOT)
        if (!digest.matches(nextRootDigest) ||
            nextRootDigest == expectedRootDigest ||
            current.sourceRevision >= MAX_REVISION ||
            nextRevision != current.sourceRevision + 1 ||
            current.completionCount >= MAX_REVISION - 1 ||
            newEntry.completionOrder != current.completionCount + 1 ||
            newEntry.savedCorrectionRevision != 0L ||
            newEntry.originalJournalRevision !in 1..MAX_REVISION ||
            !id.matches(newEntry.workoutId) ||
            !id.matches(newEntry.storedReceiptId) ||
            !id.matches(newEntry.finishEventId))
            return deny(NativeSegmentedIdentityPageRefusal.INVALID_HISTORY)
        val workout = find(current, current.ownerKey, current.athleteId,
            expectedRootDigest, expectedRevision, newEntry.workoutId,
            KeyType.WORKOUT)
        val receipt = find(current, current.ownerKey, current.athleteId,
            expectedRootDigest, expectedRevision, newEntry.storedReceiptId,
            KeyType.RECEIPT)
        if (workout is NativeSegmentedIdentityPageDecision.Rejected)
            return workout
        if (receipt is NativeSegmentedIdentityPageDecision.Rejected)
            return receipt
        if ((workout as NativeSegmentedIdentityPageDecision.Located).row != null)
            return deny(NativeSegmentedIdentityPageRefusal.DUPLICATE_WORKOUT)
        if ((receipt as NativeSegmentedIdentityPageDecision.Located).row != null)
            return deny(NativeSegmentedIdentityPageRefusal.DUPLICATE_RECEIPT)
        val row = NativeCandidateIndexedWorkout(
            newEntry.workoutId, newEntry.storedReceiptId,
            newEntry.completionOrder)
        val nextWorkouts = insertPage(current.workoutPages,row,KeyType.WORKOUT)
            ?: return deny(NativeSegmentedIdentityPageRefusal.CORRUPT_PAGE)
        val nextReceipts = insertPage(current.receiptPages,row,KeyType.RECEIPT)
            ?: return deny(NativeSegmentedIdentityPageRefusal.CORRUPT_PAGE)
        return NativeSegmentedIdentityPageDecision.Prepared(current.copy(
            sourceRootDigest = nextRootDigest,
            sourceRevision = nextRevision,
            completionCount = current.completionCount + 1,
            workoutPages = nextWorkouts,
            receiptPages = nextReceipts
        ))
    }

    fun findWorkout(
        directory: NativeSegmentedIdentityPageDirectory,
        owner: String, athlete: String, expectedRootDigest: String,
        expectedRevision: Long, workoutId: String
    ) = find(directory, owner, athlete, expectedRootDigest,
        expectedRevision, workoutId, KeyType.WORKOUT)

    fun findReceipt(
        directory: NativeSegmentedIdentityPageDirectory,
        owner: String, athlete: String, expectedRootDigest: String,
        expectedRevision: Long, receiptId: String
    ) = find(directory, owner, athlete, expectedRootDigest,
        expectedRevision, receiptId, KeyType.RECEIPT)
}
