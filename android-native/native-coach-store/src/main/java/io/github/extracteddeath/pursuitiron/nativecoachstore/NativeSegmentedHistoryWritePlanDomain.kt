package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * Pure candidate state machine for a future ONE-WRITER segmented manifest.
 *
 * There is deliberately NO file IO, no authority switch and no publication:
 * a persistent owner must separately authenticate the athlete, prove its
 * unique indexed target lookup, verify the journal evidence behind "resolved",
 * apply a prepared redo, force a replacement immutable segment, then atomically
 * publish the new root. Never treat a ProposedPatch as a durable completion.
 *
 * Both stages are bounded to ONE 128-entry segment, independent of lifetime
 * completion count; ordinals use Long so the 4,096-entry v2 limit does not leak
 * into the replacement format.
 */
data class NativeSegmentedWriteHead(
    val ownerKey: String,
    val athleteId: String,
    val revision: Long,
    val completionCount: Long,
    val rootDigest: String,
    val pending: NativeHistoryWriteIntent? = null
)

data class NativeSegmentedWriteSegment(
    val sourceRootDigest: String,
    val firstOrdinal: Long,
    val completions: List<KotlinHistoryManifestEntry>
)

data class NativeSegmentedWritePatch(
    val priorRootDigest: String,
    val expectedHeadRevision: Long,
    val nextHeadRevision: Long,
    val nextCompletionCount: Long,
    val replaceSegmentFirstOrdinal: Long,
    val completions: List<KotlinHistoryManifestEntry>,
    val consumedIntent: NativeHistoryWriteIntent
) {
    /** This is a prospective patch, never an authenticated journal receipt. */
    val canPublish: Boolean get() = false
    val canIssueProgression: Boolean get() = false
}

enum class NativeSegmentedWriteRefusal {
    INVALID_STATE, INVALID_INTENT, REVISION_CONFLICT,
    PENDING_TRANSACTION, NO_MATCHING_INTENT, TARGET_CONFLICT,
    INVALID_SEGMENT, UNVERIFIED_RECEIPT, STORAGE_CAPACITY
}

sealed interface NativeSegmentedWriteDecision {
    data class Prepared(val head: NativeSegmentedWriteHead) :
        NativeSegmentedWriteDecision
    data class ProposedPatch(val patch: NativeSegmentedWritePatch) :
        NativeSegmentedWriteDecision
    data class Rejected(val reason: NativeSegmentedWriteRefusal) :
        NativeSegmentedWriteDecision
}

object NativeSegmentedHistoryWritePlanDomain {
    const val SEGMENT_SIZE = 128
    private const val MAX_REVISION = 9_007_199_254_740_991L
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val digest = Regex("^[a-f0-9]{64}$")

    private fun denied(reason: NativeSegmentedWriteRefusal) =
        NativeSegmentedWriteDecision.Rejected(reason)

    private fun validHead(head: NativeSegmentedWriteHead) =
        id.matches(head.ownerKey) && id.matches(head.athleteId) &&
            head.revision in 1..MAX_REVISION &&
            head.completionCount in 0 until MAX_REVISION &&
            digest.matches(head.rootDigest)

    private fun validEntry(e: KotlinHistoryManifestEntry) =
        e.completionOrder in 1..MAX_REVISION &&
            e.originalJournalRevision in 1..MAX_REVISION &&
            e.savedCorrectionRevision in 0..MAX_REVISION &&
            id.matches(e.workoutId) && id.matches(e.storedReceiptId) &&
            id.matches(e.finishEventId)

    private fun validIntent(intent: NativeHistoryWriteIntent) =
        id.matches(intent.workoutId) && when (intent) {
            is NativeHistoryWriteIntent.Finish ->
                id.matches(intent.operation.eventId) &&
                    intent.operation.expectedJournalRevision in 0 until MAX_REVISION
            is NativeHistoryWriteIntent.CustomFinish ->
                id.matches(intent.operation.eventId) &&
                    intent.operation.expectedRevision in 0 until MAX_REVISION
            is NativeHistoryWriteIntent.Correct ->
                id.matches(intent.operation.eventId) &&
                    id.matches(intent.operation.correctsEventId) &&
                    intent.operation.setKey.length in 1..100 &&
                    intent.operation.expectedCorrectionRevision in 0 until MAX_REVISION
            is NativeHistoryWriteIntent.CustomCorrect ->
                id.matches(intent.operation.eventId) &&
                    id.matches(intent.operation.correctsEventId) &&
                    intent.operation.visit.setKey.length in 1..160 &&
                    intent.operation.expectedCorrectionRevision in 0 until MAX_REVISION
        }

    private fun isFinish(intent: NativeHistoryWriteIntent) =
        intent is NativeHistoryWriteIntent.Finish ||
            intent is NativeHistoryWriteIntent.CustomFinish

    /**
     * target is an OWNER-VERIFIED, exact existing-completion lookup; null for
     * a Finish means absence proved by the future unique workout-ID index.
     * The reducer cannot itself prove that lookup or verify the journal.
     */
    fun prepare(
        head: NativeSegmentedWriteHead,
        expectedRevision: Long,
        intent: NativeHistoryWriteIntent,
        target: KotlinHistoryManifestEntry?
    ): NativeSegmentedWriteDecision {
        if (!validHead(head) || !validIntent(intent) ||
            (target != null && !validEntry(target)))
            return denied(NativeSegmentedWriteRefusal.INVALID_STATE)
        if (head.pending != null)
            return denied(NativeSegmentedWriteRefusal.PENDING_TRANSACTION)
        if (head.revision != expectedRevision)
            return denied(NativeSegmentedWriteRefusal.REVISION_CONFLICT)
        if (head.revision == MAX_REVISION)
            return denied(NativeSegmentedWriteRefusal.STORAGE_CAPACITY)
        if (isFinish(intent)) {
            if (target != null) return denied(NativeSegmentedWriteRefusal.TARGET_CONFLICT)
            if (head.completionCount >= MAX_REVISION - 1)
                return denied(NativeSegmentedWriteRefusal.STORAGE_CAPACITY)
        } else {
            if (target == null || target.workoutId != intent.workoutId ||
                target.completionOrder > head.completionCount)
                return denied(NativeSegmentedWriteRefusal.TARGET_CONFLICT)
            when (intent) {
                is NativeHistoryWriteIntent.Correct -> {
                    if (target.storedReceiptId.startsWith("custom-") ||
                        target.savedCorrectionRevision !=
                            intent.operation.expectedCorrectionRevision)
                        return denied(NativeSegmentedWriteRefusal.TARGET_CONFLICT)
                }
                is NativeHistoryWriteIntent.CustomCorrect -> {
                    if (!target.storedReceiptId.startsWith("custom-v2-") ||
                        target.savedCorrectionRevision !=
                            intent.operation.expectedCorrectionRevision)
                        return denied(NativeSegmentedWriteRefusal.TARGET_CONFLICT)
                }
                else -> return denied(NativeSegmentedWriteRefusal.INVALID_INTENT)
            }
        }
        return NativeSegmentedWriteDecision.Prepared(head.copy(pending = intent))
    }

    /**
     * Only the exact previously prepared intent can propose a copy-on-write
     * segment. A caller must replay-verify resolved's saved journal/correction
     * before applying the resulting patch to a new root on disk.
     */
    fun finalize(
        prepared: NativeSegmentedWriteHead,
        intent: NativeHistoryWriteIntent,
        target: KotlinHistoryManifestEntry?,
        segment: NativeSegmentedWriteSegment,
        resolved: KotlinHistoryManifestEntry
    ): NativeSegmentedWriteDecision {
        if (!validHead(prepared) || !validIntent(intent) || !validEntry(resolved))
            return denied(NativeSegmentedWriteRefusal.INVALID_STATE)
        if (prepared.pending != intent)
            return denied(NativeSegmentedWriteRefusal.NO_MATCHING_INTENT)
        if (prepared.revision >= MAX_REVISION)
            return denied(NativeSegmentedWriteRefusal.STORAGE_CAPACITY)
        if (segment.sourceRootDigest != prepared.rootDigest)
            return denied(NativeSegmentedWriteRefusal.REVISION_CONFLICT)

        val isAppend = isFinish(intent)
        val ordinal = if (isAppend) prepared.completionCount + 1
            else target?.completionOrder ?: return denied(
                NativeSegmentedWriteRefusal.TARGET_CONFLICT)
        if (ordinal !in 1..MAX_REVISION)
            return denied(NativeSegmentedWriteRefusal.STORAGE_CAPACITY)
        val first = ((ordinal - 1) / SEGMENT_SIZE) * SEGMENT_SIZE + 1
        val oldCount = if (isAppend)
            (prepared.completionCount - first + 1).coerceAtLeast(0L)
        else minOf(SEGMENT_SIZE.toLong(), prepared.completionCount - first + 1)
        if (oldCount !in 0..SEGMENT_SIZE.toLong() ||
            segment.firstOrdinal != first ||
            segment.completions.size.toLong() != oldCount ||
            segment.completions.withIndex().any { (index, entry) ->
                !validEntry(entry) || entry.completionOrder != first + index
            } ||
            segment.completions.map { it.workoutId }.toSet().size !=
                segment.completions.size ||
            segment.completions.map { it.storedReceiptId }.toSet().size !=
                segment.completions.size)
            return denied(NativeSegmentedWriteRefusal.INVALID_SEGMENT)

        val changed = if (isAppend) {
            if (target != null || resolved.workoutId != intent.workoutId ||
                resolved.completionOrder != ordinal ||
                resolved.savedCorrectionRevision != 0L)
                return denied(NativeSegmentedWriteRefusal.UNVERIFIED_RECEIPT)
            when (intent) {
                is NativeHistoryWriteIntent.Finish ->
                    if (resolved.finishEventId != intent.operation.eventId ||
                        resolved.originalJournalRevision !=
                            intent.operation.expectedJournalRevision + 1 ||
                        resolved.storedReceiptId.startsWith("custom-"))
                        return denied(NativeSegmentedWriteRefusal.UNVERIFIED_RECEIPT)
                is NativeHistoryWriteIntent.CustomFinish ->
                    if (resolved.finishEventId != intent.operation.eventId ||
                        resolved.originalJournalRevision !=
                            intent.operation.expectedRevision + 1 ||
                        !resolved.storedReceiptId.startsWith("custom-v2-"))
                        return denied(NativeSegmentedWriteRefusal.UNVERIFIED_RECEIPT)
                else -> return denied(NativeSegmentedWriteRefusal.INVALID_INTENT)
            }
            segment.completions + resolved
        } else {
            val original = target ?: return denied(
                NativeSegmentedWriteRefusal.TARGET_CONFLICT)
            val position = (ordinal - first).toInt()
            if (segment.completions[position] != original ||
                resolved.copy(savedCorrectionRevision =
                    original.savedCorrectionRevision) != original ||
                resolved.savedCorrectionRevision !=
                    original.savedCorrectionRevision + 1L)
                return denied(NativeSegmentedWriteRefusal.UNVERIFIED_RECEIPT)
            segment.completions.toMutableList().also { it[position] = resolved }
        }
        return NativeSegmentedWriteDecision.ProposedPatch(
            NativeSegmentedWritePatch(
                prepared.rootDigest, prepared.revision, prepared.revision + 1,
                prepared.completionCount + if (isAppend) 1 else 0,
                first, Collections.unmodifiableList(changed), intent
            ))
    }
}
