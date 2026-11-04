package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.security.MessageDigest

/**
 * Exactly-once custom Finish across TWO physically separate native files:
 *
 * durable shared history intent -> durable custom Finish receipt ->
 * durable shared history manifest entry.
 *
 * Replays recover the identical pending event after process death. The
 * composer-only history reader refuses entries with custom- receipt IDs
 * until the unified custom-aware history projector is implemented; no
 * invented Last/PR/progression data may be published in the meantime.
 */
enum class NativeCustomFinishRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_ENROLLED, FOREIGN_PENDING_INTENT,
    SESSION_NOT_READY, FINISH_NOT_READY, FINISH_EVENT_MISMATCH,
    HISTORY_NOT_READY, JOURNAL_UNAVAILABLE, MANIFEST_REJECTED,
    STORAGE_UNAVAILABLE
}
internal object NativeCustomFinishReceipt {
    fun compute(
        owner:String,athlete:String,journal:CustomWorkoutJournalDraft
    ):String {
        val bytes=NativeKotlinCustomWorkoutSnapshotCodec.encode(journal.snapshot)
        val hash=MessageDigest.getInstance("SHA-256")
        listOf(owner,athlete,journal.workoutId,
            journal.preparedConsentFingerprint).forEach {
            val raw=it.toByteArray(Charsets.UTF_8)
            hash.update((raw.size ushr 8).toByte())
            hash.update(raw.size.toByte())
            hash.update(raw)
        }
        hash.update(bytes)
        return "custom-"+hash.digest().joinToString("") {
            "%02x".format(it.toInt() and 255)
        }
    }

}

sealed interface NativeCustomFinishResult {
    data class Committed(
        val completion:KotlinHistoryManifestEntry,
        val evidence:CustomWorkoutHistoricalEvidence,
        val manifestRevision:Long,
        val replayed:Boolean
    ):NativeCustomFinishResult
    data class Rejected(val reason:NativeCustomFinishRefusal):
        NativeCustomFinishResult
}

/**
 * A trusted owner/athlete must already have been explicitly enrolled into
 * the native history ledger. No user-supplied boolean can enroll history.
 * All composer/custom issuance and history writes MUST share this owner fence.
 */
class NativeKotlinCustomHistoryTransaction(
    private val history:NativeKotlinHistoryManifestFileStore,
    private val custom:NativeKotlinCustomWorkoutJournalFileStore,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun authorize(owner:String,athlete:String):Boolean =
        runCatching {identity.signedInIdentity()}.getOrNull()?.let {
            it.ownerKey==owner && it.athleteId==athlete
        }==true


    private fun committed(
        owner:String,athlete:String,
        ledger:NativeHistoryLedger,
        item:KotlinHistoryManifestEntry,
        replay:Boolean
    ):NativeCustomFinishResult {
        if(!authorize(owner,athlete))
            return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.NOT_AUTHORIZED)
        val opened=custom.open(owner,item.workoutId)
        val journal=(opened as? NativeCustomJournalResult.Durable)
            ?.issued?.journal ?:return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.JOURNAL_UNAVAILABLE)
        val evidence=CustomWorkoutHistoryEvidenceDomain.project(journal)
        if(evidence !is CustomHistoryEvidenceResult.Candidate ||
            journal.finishedByEventId!=item.finishEventId ||
            journal.revision!=item.originalJournalRevision ||
            item.storedReceiptId!=NativeCustomFinishReceipt.compute(owner,athlete,journal))
            return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.FINISH_EVENT_MISMATCH)
        return NativeCustomFinishResult.Committed(
            item,evidence.evidence,ledger.revision,replay)
    }

    private fun replayPending(
        owner:String,athlete:String,
        ledger:NativeHistoryLedger
    ):NativeCustomFinishResult {
        val intent=ledger.pending
            as? NativeHistoryWriteIntent.CustomFinish
            ?:return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.FOREIGN_PENDING_INTENT)
        if(!authorize(owner,athlete))
            return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.NOT_AUTHORIZED)
        val applied=custom.applyPendingFinish(owner,intent.workoutId,
            intent.operation)
        val journal=(applied as? NativeCustomJournalResult.Durable)
            ?.issued?.journal ?:return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.JOURNAL_UNAVAILABLE)
        val evidence=CustomWorkoutHistoryEvidenceDomain.project(journal)
        if(evidence !is CustomHistoryEvidenceResult.Candidate ||
            journal.revision!=intent.operation.expectedRevision+1 ||
            journal.finishedByEventId!=intent.operation.eventId)
            return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.FINISH_EVENT_MISMATCH)
        val entry=KotlinHistoryManifestEntry(
            ledger.completions.size.toLong()+1,
            intent.workoutId,
            NativeCustomFinishReceipt.compute(owner,athlete,journal),
            intent.operation.eventId,journal.revision,0)
        val saved=history.finalize(owner,athlete,intent,entry)
        if(saved !is NativeHistoryLedgerResult.Durable)
            return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.MANIFEST_REJECTED)
        return committed(owner,athlete,saved.ledger,entry,false)
    }

    private fun recoverLedger(
        owner:String,athlete:String
    ):NativeHistoryLedger? {
        val opened=history.open(owner,athlete)
        if(opened !is NativeHistoryLedgerResult.Durable ||
            opened.ledger.ownerKey!=owner ||
            opened.ledger.athleteId!=athlete)
            return null
        return opened.ledger
    }

    fun complete(
        owner:String,athlete:String,workoutId:String,
        operation:CustomWorkoutJournalOperation.Finish
    ):NativeCustomFinishResult = fenced(owner,athlete,workoutId) {
        val original=recoverLedger(owner,athlete)
            ?:return@fenced NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.NOT_ENROLLED)
        val recovered=if(original.pending!=null) {
            val replay=replayPending(owner,athlete,original)
            if(replay !is NativeCustomFinishResult.Committed)
                return@fenced replay
            recoverLedger(owner,athlete)
                ?:return@fenced NativeCustomFinishResult.Rejected(
                    NativeCustomFinishRefusal.HISTORY_NOT_READY)
        } else original
        if(!recovered.readyForHistoryReads)
            return@fenced NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.HISTORY_NOT_READY)
        val old=recovered.completions.singleOrNull {
            it.workoutId==workoutId
        }
        if(old!=null)
            return@fenced if(old.finishEventId==operation.eventId &&
                old.originalJournalRevision==operation.expectedRevision+1)
                committed(owner,athlete,recovered,old,true)
            else NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.FINISH_EVENT_MISMATCH)
        val issued=custom.open(owner,workoutId)
        val draft=(issued as? NativeCustomJournalResult.Durable)?.issued?.journal
            ?:return@fenced NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.SESSION_NOT_READY)
        if(draft.finishedByEventId!=null)
            return@fenced NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.FINISH_EVENT_MISMATCH)
        if(draft.revision!=operation.expectedRevision ||
            !draft.canProposeFinish ||
            CustomWorkoutJournalDomain.reduce(draft,operation)
                !is CustomWorkoutJournalDecision.Proposed)
            return@fenced NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.FINISH_NOT_READY)
        val intent=NativeHistoryWriteIntent.CustomFinish(workoutId,operation)
        val prepared=history.prepare(owner,athlete,recovered.revision,intent)
        if(prepared !is NativeHistoryLedgerResult.Durable)
            return@fenced NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.MANIFEST_REJECTED)
        replayPending(owner,athlete,prepared.ledger)
    }

    /** A stale foreign Composer intent MUST be recovered by its own owner. */
    fun recover(owner:String,athlete:String):
        NativeCustomFinishResult = fenced(owner,athlete,null) {
        val ledger=recoverLedger(owner,athlete)
            ?:return@fenced NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.NOT_ENROLLED)
        if(ledger.pending==null)
            return@fenced NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.FINISH_NOT_READY)
        replayPending(owner,athlete,ledger)
    }

    private fun fenced(owner:String,athlete:String,workoutId:String?,
        work:()->NativeCustomFinishResult):NativeCustomFinishResult {
        if(!id.matches(owner)||!id.matches(athlete) ||
            (workoutId!=null && !id.matches(workoutId)))
            return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if(!authorize(owner,athlete))
                    NativeCustomFinishResult.Rejected(
                        NativeCustomFinishRefusal.NOT_AUTHORIZED)
                else work()
            }
        }.getOrElse {NativeCustomFinishResult.Rejected(
            NativeCustomFinishRefusal.STORAGE_UNAVAILABLE)}
    }
}
