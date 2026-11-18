package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.security.MessageDigest

/** Unlike a regular set receipt, this binds every measured unit and physical
 * source, all effective corrections, the final receipt and approval metadata.
 * It cannot be substituted for a Composer/custom stored receipt identifier. */
internal object NativeMeasuredWorkoutFinishReceipt {
    fun compute(stored:NativeMeasuredWorkoutStored):String {
        require(stored.journal.finishedByEventId!=null)
        val digest=MessageDigest.getInstance("SHA-256")
        digest.update("pursuit-measured-finish-v1".toByteArray(Charsets.UTF_8))
        digest.update(NativeMeasuredWorkoutParentCodec.encode(stored))
        return "measured-v1-"+digest.digest().joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
    }
}

enum class NativeMeasuredFinishRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_ENROLLED, FOREIGN_PENDING_INTENT,
    SESSION_NOT_READY, FINISH_NOT_READY, FINISH_EVENT_MISMATCH,
    HISTORY_NOT_READY, JOURNAL_UNAVAILABLE, MANIFEST_REJECTED,
    STORAGE_UNAVAILABLE
}
sealed interface NativeMeasuredFinishResult {
    data class Committed(
        val completion:KotlinHistoryManifestEntry,
        /** Exact observed side/unit outcomes, never converted to rep sets. */
        val stored:NativeMeasuredWorkoutStored,
        val manifestRevision:Long,
        val replayed:Boolean
    ):NativeMeasuredFinishResult {
        val canChangeProgram:Boolean get()=false
        val canIssueProgression:Boolean get()=false
    }
    data class Rejected(val reason:NativeMeasuredFinishRefusal):
        NativeMeasuredFinishResult
}

/**
 * The ONE canonical enrolled ledger owns the Finish of a measured workout.
 * Stage ledger intent -> atomically finish the full parent file -> finalize
 * the same shared ledger. Retry after either disk-write interruption always
 * replays the exact source-owned event; foreign pending intents never vanish.
 *
 * The active UI must remain gated until its source of parent approval and
 * factual typed History renderer are separately connected.
 */
class NativeKotlinMeasuredHistoryTransaction(
    private val history:NativeKotlinHistoryManifestFileStore,
    private val measured:NativeMeasuredWorkoutParentFileStore,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val clock:NativeHistoryCompletionClock=NativeHistoryCompletionClock.Undated
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun authorized(owner:String,athlete:String):Boolean =
        runCatching{identity.signedInIdentity()}.getOrNull() ==
            NativeKotlinOwnerIdentity(owner,athlete)

    private fun reject(reason:NativeMeasuredFinishRefusal)=
        NativeMeasuredFinishResult.Rejected(reason)

    private fun ledger(owner:String,athlete:String):NativeHistoryLedger? {
        val result=history.open(owner,athlete)
            as? NativeHistoryLedgerResult.Durable ?:return null
        val state=result.ledger
        return state.takeIf{it.ownerKey==owner && it.athleteId==athlete}
    }

    private fun committed(
        owner:String,athlete:String,
        state:NativeHistoryLedger,
        entry:KotlinHistoryManifestEntry,
        replayed:Boolean
    ):NativeMeasuredFinishResult {
        if(!authorized(owner,athlete))
            return reject(NativeMeasuredFinishRefusal.NOT_AUTHORIZED)
        val opened=measured.open(owner,entry.workoutId)
            as? NativeMeasuredWorkoutStoreResult.Durable
            ?:return reject(NativeMeasuredFinishRefusal.JOURNAL_UNAVAILABLE)
        val saved=opened.stored
        if(saved.ownerKey!=owner || saved.athleteId!=athlete ||
            saved.journal.approvedPlan.workoutId!=entry.workoutId ||
            saved.journal.revision!=entry.originalJournalRevision ||
            saved.journal.finishedByEventId!=entry.finishEventId ||
            entry.savedCorrectionRevision!=0L ||
            entry.storedReceiptId!=NativeMeasuredWorkoutFinishReceipt.compute(saved))
            return reject(NativeMeasuredFinishRefusal.FINISH_EVENT_MISMATCH)
        if(!authorized(owner,athlete))
            return reject(NativeMeasuredFinishRefusal.NOT_AUTHORIZED)
        return NativeMeasuredFinishResult.Committed(entry,saved,
            state.revision,replayed)
    }

    private fun replayPending(
        owner:String,athlete:String,
        state:NativeHistoryLedger
    ):NativeMeasuredFinishResult {
        val intent=state.pending as? NativeHistoryWriteIntent.MeasuredFinish
            ?:return reject(NativeMeasuredFinishRefusal.FOREIGN_PENDING_INTENT)
        if(!authorized(owner,athlete))
            return reject(NativeMeasuredFinishRefusal.NOT_AUTHORIZED)
        val applied=measured.applyPendingFinish(owner,intent.workoutId,
            intent.operation) as? NativeMeasuredWorkoutStoreResult.Durable
            ?:return reject(NativeMeasuredFinishRefusal.JOURNAL_UNAVAILABLE)
        val saved=applied.stored
        if(saved.ownerKey!=owner || saved.athleteId!=athlete ||
            saved.journal.revision!=intent.operation.expectedRevision+1 ||
            saved.journal.finishedByEventId!=intent.operation.eventId ||
            saved.journal.bySetKey.isEmpty() ||
            saved.journal.bySetKey.values.any{it.effectiveRecord==null})
            return reject(NativeMeasuredFinishRefusal.FINISH_EVENT_MISMATCH)
        val entry=KotlinHistoryManifestEntry(
            state.completions.size.toLong()+1,intent.workoutId,
            NativeMeasuredWorkoutFinishReceipt.compute(saved),
            intent.operation.eventId,saved.journal.revision,0,
            intent.completedAtEpochMillis)
        val commit=history.finalize(owner,athlete,intent,entry)
            as? NativeHistoryLedgerResult.Durable
            ?:return reject(NativeMeasuredFinishRefusal.MANIFEST_REJECTED)
        return committed(owner,athlete,commit.ledger,entry,false)
    }

    fun complete(
        owner:String,athlete:String,workoutId:String,
        operation:MeasuredWorkoutOperation.Finish
    ):NativeMeasuredFinishResult = fenced(owner,athlete,workoutId) {
        val existing=ledger(owner,athlete)
            ?:return@fenced reject(NativeMeasuredFinishRefusal.NOT_ENROLLED)
        val clean=if(existing.pending!=null) {
            val replay=replayPending(owner,athlete,existing)
            if(replay !is NativeMeasuredFinishResult.Committed)
                return@fenced replay
            ledger(owner,athlete)
                ?:return@fenced reject(NativeMeasuredFinishRefusal.HISTORY_NOT_READY)
        } else existing
        if(!clean.readyForHistoryReads)
            return@fenced reject(NativeMeasuredFinishRefusal.HISTORY_NOT_READY)
        val finished=clean.completions.singleOrNull{it.workoutId==workoutId}
        if(finished!=null) {
            return@fenced if(finished.finishEventId==operation.eventId &&
                finished.originalJournalRevision==operation.expectedRevision+1 &&
                finished.storedReceiptId.startsWith("measured-v1-"))
                committed(owner,athlete,clean,finished,true)
            else reject(NativeMeasuredFinishRefusal.FINISH_EVENT_MISMATCH)
        }
        val opened=measured.open(owner,workoutId)
            as? NativeMeasuredWorkoutStoreResult.Durable
            ?:return@fenced reject(NativeMeasuredFinishRefusal.SESSION_NOT_READY)
        val proposed=opened.stored.journal
        if(proposed.finishedByEventId!=null)
            return@fenced reject(NativeMeasuredFinishRefusal.FINISH_EVENT_MISMATCH)
        if(proposed.revision!=operation.expectedRevision ||
            !proposed.canProposeFinish ||
            MeasuredWorkoutJournalDomain.reduce(proposed,operation)
                !is MeasuredWorkoutDecision.Proposed)
            return@fenced reject(NativeMeasuredFinishRefusal.FINISH_NOT_READY)
        val intent=NativeHistoryWriteIntent.MeasuredFinish(
            workoutId,operation,clock.observe())
        val prepared=history.prepare(owner,athlete,clean.revision,intent)
            as? NativeHistoryLedgerResult.Durable
            ?:return@fenced reject(NativeMeasuredFinishRefusal.MANIFEST_REJECTED)
        replayPending(owner,athlete,prepared.ledger)
    }

    fun recover(owner:String,athlete:String):NativeMeasuredFinishResult =
        fenced(owner,athlete,null) {
            val open=ledger(owner,athlete)
                ?:return@fenced reject(NativeMeasuredFinishRefusal.NOT_ENROLLED)
            if(open.pending==null)
                return@fenced reject(NativeMeasuredFinishRefusal.FINISH_NOT_READY)
            replayPending(owner,athlete,open)
        }

    private fun fenced(
        owner:String,athlete:String,workoutId:String?,
        operation:()->NativeMeasuredFinishResult
    ):NativeMeasuredFinishResult {
        if(!id.matches(owner) || !id.matches(athlete) ||
            (workoutId!=null && !id.matches(workoutId)))
            return reject(NativeMeasuredFinishRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if(!authorized(owner,athlete))
                    reject(NativeMeasuredFinishRefusal.NOT_AUTHORIZED)
                else operation()
            }
        }.getOrElse{reject(NativeMeasuredFinishRefusal.STORAGE_UNAVAILABLE)}
    }
}
