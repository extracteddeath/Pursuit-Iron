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
