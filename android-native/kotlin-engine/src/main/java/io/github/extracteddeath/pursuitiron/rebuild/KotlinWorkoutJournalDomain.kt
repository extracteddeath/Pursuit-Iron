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

    /** Workout-only, no program edit. Targets are separately source-verified
     * by the native owner before the durable append, never copied from UI. */
    data class SwapExercise(
        override val eventId:String,
        override val expectedJournalRevision:Long,
        val previousExerciseId:String,
        val replacementTargets:List<KotlinWorkoutTarget>
    ):KotlinJournalOperation

    /** An unstarted replacement may restore its exact prior issued targets. */
    data class UndoSwap(
        override val eventId:String,
        override val expectedJournalRevision:Long,
        val swapEventId:String
    ):KotlinJournalOperation

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
    val finishedByEventId: String?,
    /** Immutable initial issuance: survives every later Swap/Undo receipt. */
    val originalSnapshot:KotlinStagedWorkout=snapshot
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
    INVALID_VALUES, INCOMPLETE_WORKOUT,
    SWAP_ALREADY_STARTED, SWAP_INVALID_TARGET, SWAP_IDENTITY_CONFLICT,
    SWAP_UNDO_UNAVAILABLE
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
        var nextSnapshot=journal.snapshot
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
            is KotlinJournalOperation.SwapExercise -> {
                val earlier=journal.snapshot.targets.filter{
                    it.exerciseId==operation.previousExerciseId
                }
                if(earlier.isEmpty())
                    return KotlinJournalDecision.Rejected(
                        KotlinJournalRefusal.UNKNOWN_SET)
                if(journal.receipts.any{receipt->
                    (receipt.operation as? KotlinJournalOperation.RecordSet)
                        ?.setKey in earlier.map{it.setKey}.toSet()
                })return KotlinJournalDecision.Rejected(
                    KotlinJournalRefusal.SWAP_ALREADY_STARTED)
                val next=operation.replacementTargets
                val nextId=next.firstOrNull()?.exerciseId
                val originalKeys=journal.originalSnapshot.targets.map{it.setKey}
                val allSwapKeys=journal.receipts.flatMap{
                    (it.operation as? KotlinJournalOperation.SwapExercise)
                        ?.replacementTargets?.map{target->target.setKey}.orEmpty()
                }
                if(nextId==null || nextId==operation.previousExerciseId ||
                    journal.snapshot.targets.any{it.exerciseId==nextId} ||
                    next.any{it.setKey in originalKeys || it.setKey in allSwapKeys})
                    return KotlinJournalDecision.Rejected(
                        KotlinJournalRefusal.SWAP_IDENTITY_CONFLICT)
                val oldOrdinals=earlier.map{it.type to it.ordinal}.toSet()
                if(next.size!=earlier.size || next.size !in 1..32 ||
                    next.map{it.setKey}.distinct().size!=next.size ||
                    next.map{it.type to it.ordinal}.toSet()!=oldOrdinals ||
                    next.any{target->
                        target.exerciseId!=nextId ||
                        target.role!=earlier.first().role ||
                        target.purpose!=earlier.first().purpose ||
                        (target.type to target.ordinal) !in oldOrdinals ||
                        target.setKey!="swap:${operation.eventId}:${target.type.name.lowercase()}:${target.ordinal}" ||
                        target.setKey.length !in 1..100 ||
                        target.reps.minimum !in 1..100 ||
                        target.reps.maximum !in target.reps.minimum..100 ||
                        target.targetRir?.let{it !in 0..10}==true ||
                        target.activeSeconds !in 1..3600 ||
                        target.restSeconds !in 0..3600 ||
                        when(val load=target.load){
                            DraftSetLoad.ExplicitBodyweight->false
                            is DraftSetLoad.Verified ->
                                load.value<BigDecimal.ZERO ||
                                load.value>max ||
                                load.value.stripTrailingZeros().scale()>3
                        }
                    })return KotlinJournalDecision.Rejected(
                        KotlinJournalRefusal.SWAP_INVALID_TARGET)
                val start=journal.snapshot.targets.indexOfFirst{
                    it.exerciseId==operation.previousExerciseId
                }
                val changed=journal.snapshot.targets.filterNot{
                    it.exerciseId==operation.previousExerciseId
                }.toMutableList().apply{addAll(start,next)}
                if(changed.size>256)
                    return KotlinJournalDecision.Rejected(
                        KotlinJournalRefusal.SWAP_INVALID_TARGET)
                nextSnapshot=journal.snapshot.copy(targets=
                    Collections.unmodifiableList(changed))
                null
            }
            is KotlinJournalOperation.UndoSwap -> {
                val prior=journal.receipts.firstOrNull{
                    it.operation.eventId==operation.swapEventId
                }?.operation as? KotlinJournalOperation.SwapExercise
                    ?:return KotlinJournalDecision.Rejected(
                        KotlinJournalRefusal.SWAP_UNDO_UNAVAILABLE)
                val changedTargets=prior.replacementTargets
                val activeKeys=journal.snapshot.targets.map{it.setKey}.toSet()
                if(changedTargets.any{it.setKey !in activeKeys} ||
                    journal.receipts.any{
                        (it.operation as? KotlinJournalOperation.UndoSwap)
                            ?.swapEventId==operation.swapEventId
                    })return KotlinJournalDecision.Rejected(
                        KotlinJournalRefusal.SWAP_UNDO_UNAVAILABLE)
                val changedKeys=changedTargets.map{it.setKey}.toSet()
                if(journal.receipts.any {
                    (it.operation as? KotlinJournalOperation.RecordSet)
                        ?.setKey in changedKeys
                })return KotlinJournalDecision.Rejected(
                    KotlinJournalRefusal.SWAP_ALREADY_STARTED)
                val historical=journal.originalSnapshot.targets.filter{
                    it.exerciseId==prior.previousExerciseId
                }.ifEmpty{
                    journal.receipts.takeWhile{
                        it.operation.eventId!=operation.swapEventId
                    }.mapNotNull{
                        it.operation as? KotlinJournalOperation.SwapExercise
                    }.lastOrNull{swap->
                        swap.replacementTargets.first().exerciseId==
                            prior.previousExerciseId
                    }?.replacementTargets.orEmpty()
                }
                if(historical.isEmpty() || journal.snapshot.targets.any{
                    it.exerciseId==prior.previousExerciseId
                })return KotlinJournalDecision.Rejected(
                    KotlinJournalRefusal.SWAP_UNDO_UNAVAILABLE)
                val position=journal.snapshot.targets.indexOfFirst{
                    it.setKey==changedTargets.first().setKey
                }
                if(position<0 || journal.snapshot.targets.filter{
                    it.setKey in changedKeys
                }!=changedTargets)
                    return KotlinJournalDecision.Rejected(
                        KotlinJournalRefusal.SWAP_UNDO_UNAVAILABLE)
                val changed=journal.snapshot.targets.filterNot{
                    it.setKey in changedKeys
                }.toMutableList().apply{addAll(position,historical)}
                nextSnapshot=journal.snapshot.copy(targets=
                    Collections.unmodifiableList(changed))
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
            journal.copy(snapshot=nextSnapshot,revision=nextRevision,
                receipts=nextReceipts,effectiveSets=nextSets,
                finishedByEventId=finish),false
        )
    }
}
