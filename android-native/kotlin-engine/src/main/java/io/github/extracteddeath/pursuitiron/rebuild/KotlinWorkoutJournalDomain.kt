package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Pure, Kotlin-only set journal reducer. Every successful operation produces
 * an immutable proposed state, never a disk write. The native store MUST
 * authenticate a committed snapshot and compare-and-swap each journal revision
 * atomically before acknowledging the operation to the athlete.
 *
 * The immutable target in the issued snapshot is never overwritten by actuals.
 * Log changes, history corrections and replay are distinct concepts.
 */
data class KotlinStoredWorkoutIdentity(
    val storageReceiptId: String,
    val workoutId: String,
    val source: KotlinTrainingSourceRevision,
    val storedRevision: Long
)

data class KotlinPerformedSet(
    /** Null only for explicitly unweighted bodyweight sets. */
    val load: BigDecimal?,
    val unit: LoadUnit?,
    val reps: Int,
    /** Actual reported effort. Null is UNKNOWN, never target RIR. */
    val actualRir: Int?
)

enum class KotlinJournalSetOutcome { COMPLETED, SKIPPED, FAILED_ATTEMPT }

sealed interface KotlinJournalOperation {
    val eventId: String
    val expectedJournalRevision: Long

    data class RecordSet(
        override val eventId: String,
        override val expectedJournalRevision: Long,
        val setKey: String,
        val outcome: KotlinJournalSetOutcome,
        val performed: KotlinPerformedSet?,
        /** ID of CURRENT effective receipt. Never a target or set ordinal. */
        val correctsEventId: String? = null
    ) : KotlinJournalOperation

    data class Finish(
        override val eventId: String,
        override val expectedJournalRevision: Long
    ) : KotlinJournalOperation
}

data class KotlinJournalReceipt(
    val appliedRevision: Long,
    val operation: KotlinJournalOperation
)

data class KotlinWorkoutJournalState(
    val snapshot: KotlinStagedWorkout,
    val storedIdentity: KotlinStoredWorkoutIdentity,
    val revision: Long,
    val receipts: List<KotlinJournalReceipt>,
    val effectiveSets: Map<String, KotlinJournalReceipt>,
    val finishedByEventId: String?
) {
    val resolvedSetCount: Int get()=effectiveSets.size
    val unresolvedSetCount: Int get()=snapshot.targets.size-effectiveSets.size
    val canProposeFinish: Boolean get()=finishedByEventId==null &&
        unresolvedSetCount==0
    /** The returned reducer state itself is NOT a durable acknowledgment. */
    val persisted: Boolean get()=false
}

enum class KotlinJournalRefusal {
    STALE_REVISION, FINISHED, EVENT_ID_COLLISION,
    UNKNOWN_SET, ALREADY_RECORDED, INVALID_CORRECTION,
    INVALID_VALUES, INCOMPLETE_WORKOUT
}

sealed interface KotlinJournalDecision {
    data class Proposed(
        val state: KotlinWorkoutJournalState,
        val replayed: Boolean
    ) : KotlinJournalDecision
    data class Rejected(val reason: KotlinJournalRefusal) : KotlinJournalDecision
}

object KotlinWorkoutJournalDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val zero=BigDecimal.ZERO
    private val max=BigDecimal("100000")
    private const val MAX_RECEIPTS=2048

    internal fun validPerformed(
        target: KotlinWorkoutTarget,
        outcome: KotlinJournalSetOutcome,
        performed: KotlinPerformedSet?
    ): Boolean {
        if(outcome==KotlinJournalSetOutcome.SKIPPED)return performed==null
        val actual=performed ?:return false
        if(actual.actualRir!=null && actual.actualRir !in 0..10)return false
        if(outcome==KotlinJournalSetOutcome.COMPLETED &&
            actual.reps !in 1..100)return false
        if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT &&
            (actual.reps!=0 || actual.actualRir!=null))return false
        return when(val load=target.load) {
            DraftSetLoad.ExplicitBodyweight ->
                actual.load==null && actual.unit==null
            is DraftSetLoad.Verified -> {
                val value=actual.load
                value!=null && actual.unit==load.unit &&
                    value>=zero && value<=max &&
                    value.stripTrailingZeros().scale()<=3
            }
        }
    }

    /**
     * Reconstructs a reducer state ONLY from the storage-owner's committed
     * immutable snapshot identity. A plain receipt string is NOT a signature;
     * authentication/ownership must happen at the storage boundary.
     */
    fun begin(
        snapshot: KotlinStagedWorkout,
        identity: KotlinStoredWorkoutIdentity
    ): KotlinWorkoutJournalState {
        require(id.matches(identity.storageReceiptId))
        require(identity.workoutId==snapshot.workoutId &&
            identity.source==snapshot.source && identity.storedRevision>=1)
        require(snapshot.targets.isNotEmpty() && snapshot.targets.size<=256)
        require(snapshot.targets.map {it.setKey}.distinct().size==snapshot.targets.size)
        require(snapshot.targets.all {id.matches(it.exerciseId) &&
            it.setKey.length in 1..100 && it.type in DraftSetType.entries})
        return KotlinWorkoutJournalState(
            snapshot,identity,0L,emptyList(),emptyMap(),null
        )
    }

    /**
     * Always check duplicate event IDs before expectedRevision so retrying
     * after a lost network/storage acknowledgment is idempotent.
     * New changes to already logged sets require an explicit correction.
     */
    fun reduce(
        journal: KotlinWorkoutJournalState,
        operation: KotlinJournalOperation
    ): KotlinJournalDecision {
        if(!id.matches(operation.eventId))
            return KotlinJournalDecision.Rejected(KotlinJournalRefusal.INVALID_VALUES)
        val earlier=journal.receipts.firstOrNull {
            it.operation.eventId==operation.eventId
        }
        if(earlier!=null) {
            if(earlier.operation==operation)
                return KotlinJournalDecision.Proposed(journal,true)
            return KotlinJournalDecision.Rejected(KotlinJournalRefusal.EVENT_ID_COLLISION)
        }
        if(operation.expectedJournalRevision!=journal.revision)
            return KotlinJournalDecision.Rejected(KotlinJournalRefusal.STALE_REVISION)
        if(journal.finishedByEventId!=null)
            return KotlinJournalDecision.Rejected(KotlinJournalRefusal.FINISHED)
        if(journal.receipts.size>=MAX_RECEIPTS)
            return KotlinJournalDecision.Rejected(KotlinJournalRefusal.INVALID_VALUES)
        val nextRevision=journal.revision+1
        val nextReceipt=KotlinJournalReceipt(nextRevision,operation)
        val nextSetReceipts=LinkedHashMap(journal.effectiveSets)
        val finish=when(operation) {
            is KotlinJournalOperation.RecordSet -> {
                val target=journal.snapshot.targets.firstOrNull {
                    it.setKey==operation.setKey
                } ?:return KotlinJournalDecision.Rejected(KotlinJournalRefusal.UNKNOWN_SET)
                if(!validPerformed(target,operation.outcome,operation.performed))
                    return KotlinJournalDecision.Rejected(KotlinJournalRefusal.INVALID_VALUES)
                val previous=nextSetReceipts[operation.setKey]
                if(previous==null && operation.correctsEventId!=null ||
                    previous!=null && operation.correctsEventId==null)
                    return KotlinJournalDecision.Rejected(
                        if(previous==null)KotlinJournalRefusal.INVALID_CORRECTION
                        else KotlinJournalRefusal.ALREADY_RECORDED)
                if(previous!=null &&
                    previous.operation.eventId!=operation.correctsEventId)
                    return KotlinJournalDecision.Rejected(
                        KotlinJournalRefusal.INVALID_CORRECTION)
                nextSetReceipts[operation.setKey]=nextReceipt
                null
            }
            is KotlinJournalOperation.Finish -> {
                if(journal.unresolvedSetCount!=0)
                    return KotlinJournalDecision.Rejected(
                        KotlinJournalRefusal.INCOMPLETE_WORKOUT)
                operation.eventId
            }
        }
        val nextReceipts=Collections.unmodifiableList(journal.receipts+nextReceipt)
        val nextSets=Collections.unmodifiableMap(nextSetReceipts)
        return KotlinJournalDecision.Proposed(
            journal.copy(revision=nextRevision,receipts=nextReceipts,
                effectiveSets=nextSets,finishedByEventId=finish),false
        )
    }
}
