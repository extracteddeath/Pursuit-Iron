package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.AtomicMoveNotSupportedException
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/**
 * Only the trusted native program/workout owner may supply this grant.
 * The approving authority must establish the exact parent workout, gym
 * revision, set membership and absence of a conflicting regular/custom
 * session ID under the shared owner lock. Compose cannot create this proof.
 */
data class NativeMeasuredApprovedWorkout(
    val plan:MeasuredWorkoutPlan,
    val approvalId:String,
    val approvalRevision:Long
)

fun interface NativeMeasuredApprovedWorkoutSource {
    fun currentApprovedWorkout(
        ownerKey:String,athleteId:String,workoutId:String
    ):NativeMeasuredApprovedWorkout?
}

data class NativeMeasuredWorkoutStored(
    val ownerKey:String,
    val athleteId:String,
    val approvalId:String,
    val approvalRevision:Long,
    val journal:MeasuredWorkoutJournal
) {
    /** A saved collection of measured set facts, not a completed History. */
    val canFinishWorkout:Boolean get()=false
    val mayPublishHistory:Boolean get()=false
}

enum class NativeMeasuredWorkoutStoreRefusal {
    INVALID_ID, NOT_AUTHORIZED, NOT_ISSUED, APPROVAL_UNAVAILABLE,
    CORRUPT_RECORD, STALE_OR_INVALID_RECEIPT, HISTORY_TRANSACTION_REQUIRED,
    STORAGE_UNAVAILABLE
}

sealed interface NativeMeasuredWorkoutStoreResult {
    data class Durable(
        val stored:NativeMeasuredWorkoutStored,
        val replayed:Boolean
    ):NativeMeasuredWorkoutStoreResult
    data class Rejected(
        val reason:NativeMeasuredWorkoutStoreRefusal,
        val journalReason:MeasuredWorkoutRefusal?=null
    ):NativeMeasuredWorkoutStoreResult
}

/**
 * Full measured-workout *set collection* persistence. One atomic file per
 * approved parent, not fourteen unrelated issuance files. All operations
 * authenticate the current owner, re-read cold disk, replay every child and
 * use the same approval revision fence as the ordinary native gym.
 *
 * Finish is intentionally NOT writable until the canonical enrolled History
 * manifest can prepare, commit and recover the parent-level Finish as one
 * transactional operation. No measurements are yet UI-issuable.
 */
class NativeMeasuredWorkoutParentFileStore(
    private val root:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val approvals:NativeMeasuredApprovedWorkoutSource,
    // Optional only for isolated pre-integration store tests. Production
    // issuance must provide this exact shared, enrolled History authority.
    private val history:NativeKotlinHistoryManifestFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val maxBytes=NativeMeasuredWorkoutParentCodec.MAX_BYTES

    private fun active(owner:String):NativeKotlinOwnerIdentity? =
        runCatching{identity.signedInIdentity()}.getOrNull()
            ?.takeIf{ id.matches(owner) && it.ownerKey==owner &&
                id.matches(it.athleteId) }

    private fun ledgerReady(owner:String,athlete:String,workout:String):Boolean {
        val source=history?:return true
        val ledger=(source.open(owner,athlete) as?
            NativeHistoryLedgerResult.Durable)?.ledger ?:return false
        return ledger.pending==null &&
            ledger.completions.none{it.workoutId==workout}
    }

    private fun path(owner:String,workout:String):Path {
        val bytes=(owner+"\u0000"+workout).toByteArray(Charsets.UTF_8)
        val hash=MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return root.resolve("measured-parent-$hash.bin")
    }

    private fun fromDisk(
        owner:String,athlete:String,workout:String
    ):NativeMeasuredWorkoutStoreResult {
        val file=path(owner,workout)
        if(Files.isSymbolicLink(file))
            return NativeMeasuredWorkoutStoreResult.Rejected(
                NativeMeasuredWorkoutStoreRefusal.CORRUPT_RECORD)
        if(!Files.exists(file))
            return NativeMeasuredWorkoutStoreResult.Rejected(
                NativeMeasuredWorkoutStoreRefusal.NOT_ISSUED)
        val stored=try {
            if(!Files.isRegularFile(file) ||
                Files.size(file) !in 44L..maxBytes.toLong())
                throw IOException("not a regular measured parent file")
            NativeMeasuredWorkoutParentCodec.decode(Files.readAllBytes(file),
                owner,athlete)
        } catch(_:Exception) {
            return NativeMeasuredWorkoutStoreResult.Rejected(
                NativeMeasuredWorkoutStoreRefusal.CORRUPT_RECORD)
        }
        return if(stored.journal.approvedPlan.workoutId==workout)
            NativeMeasuredWorkoutStoreResult.Durable(stored,true)
        else NativeMeasuredWorkoutStoreResult.Rejected(
            NativeMeasuredWorkoutStoreRefusal.CORRUPT_RECORD)
    }

    private fun commit(file:Path,stored:NativeMeasuredWorkoutStored) {
        val data=NativeMeasuredWorkoutParentCodec.encode(stored)
        if(Files.isSymbolicLink(root))
            throw IOException("unsafe measured parent directory")
        Files.createDirectories(root)
        if(!Files.isDirectory(root) || Files.isSymbolicLink(root) ||
            Files.isSymbolicLink(file))
            throw IOException("unsafe measured parent path")
        val temp=Files.createTempFile(root,"measured-parent-", ".pending")
        try {
            FileChannel.open(temp,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(data)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            } catch(ex:AtomicMoveNotSupportedException) {
                throw IOException("atomic measured parent write unavailable",ex)
            }
        } finally {Files.deleteIfExists(temp)}
        if(!Files.readAllBytes(file).contentEquals(data))
            throw IOException("measured parent write could not be verified")
    }

    fun open(ownerKey:String,workoutId:String):NativeMeasuredWorkoutStoreResult {
        if(!id.matches(ownerKey) || !id.matches(workoutId))
            return NativeMeasuredWorkoutStoreResult.Rejected(
                NativeMeasuredWorkoutStoreRefusal.INVALID_ID)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val athlete=active(ownerKey)
                    ?:return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.NOT_AUTHORIZED)
                fromDisk(ownerKey,athlete.athleteId,workoutId)
            }
        }.getOrElse{NativeMeasuredWorkoutStoreResult.Rejected(
            NativeMeasuredWorkoutStoreRefusal.STORAGE_UNAVAILABLE)}
    }

    fun issue(ownerKey:String,workoutId:String):NativeMeasuredWorkoutStoreResult {
        if(!id.matches(ownerKey) || !id.matches(workoutId))
            return NativeMeasuredWorkoutStoreResult.Rejected(
                NativeMeasuredWorkoutStoreRefusal.INVALID_ID)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val athlete=active(ownerKey)
                    ?:return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.NOT_AUTHORIZED)
                if(!ledgerReady(ownerKey,athlete.athleteId,workoutId))
                    return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.HISTORY_TRANSACTION_REQUIRED)
                val prior=fromDisk(ownerKey,athlete.athleteId,workoutId)
                if(prior !is NativeMeasuredWorkoutStoreResult.Rejected ||
                    prior.reason!=NativeMeasuredWorkoutStoreRefusal.NOT_ISSUED)
                    return@withOwnerTransaction prior
                val consent=approvals.currentApprovedWorkout(ownerKey,
                    athlete.athleteId,workoutId)
                    ?:return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.APPROVAL_UNAVAILABLE)
                if(consent.plan.workoutId!=workoutId ||
                    consent.approvalRevision<1 ||
                    !id.matches(consent.approvalId))
                    return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.APPROVAL_UNAVAILABLE)
                val journal=MeasuredWorkoutJournalDomain.begin(consent.plan)
                    ?:return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.APPROVAL_UNAVAILABLE)
                val saved=NativeMeasuredWorkoutStored(ownerKey,
                    athlete.athleteId,consent.approvalId,
                    consent.approvalRevision,journal)
                commit(path(ownerKey,workoutId),saved)
                val read=fromDisk(ownerKey,athlete.athleteId,workoutId)
                if(read !is NativeMeasuredWorkoutStoreResult.Durable ||
                    read.stored!=saved)
                    return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.STORAGE_UNAVAILABLE)
                read.copy(replayed=false)
            }
        }.getOrElse{NativeMeasuredWorkoutStoreResult.Rejected(
            NativeMeasuredWorkoutStoreRefusal.STORAGE_UNAVAILABLE)}
    }

    fun record(
        ownerKey:String,workoutId:String,
        operation:MeasuredWorkoutOperation.Record
    ):NativeMeasuredWorkoutStoreResult {
        if(!id.matches(ownerKey) || !id.matches(workoutId))
            return NativeMeasuredWorkoutStoreResult.Rejected(
                NativeMeasuredWorkoutStoreRefusal.INVALID_ID)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val athlete=active(ownerKey)
                    ?:return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.NOT_AUTHORIZED)
                if(!ledgerReady(ownerKey,athlete.athleteId,workoutId))
                    return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.HISTORY_TRANSACTION_REQUIRED)
                val prior=fromDisk(ownerKey,athlete.athleteId,workoutId)
                val old=(prior as? NativeMeasuredWorkoutStoreResult.Durable)
                    ?.stored ?:return@withOwnerTransaction prior
                when(val next=MeasuredWorkoutJournalDomain.reduce(
                    old.journal,operation)) {
                    is MeasuredWorkoutDecision.Rejected ->
                        NativeMeasuredWorkoutStoreResult.Rejected(
                            NativeMeasuredWorkoutStoreRefusal.STALE_OR_INVALID_RECEIPT,
                            next.reason)
                    is MeasuredWorkoutDecision.Proposed -> {
                        if(next.replayed)
                            return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Durable(
                                old,true)
                        val saved=old.copy(journal=next.journal)
                        commit(path(ownerKey,workoutId),saved)
                        val checked=fromDisk(ownerKey,athlete.athleteId,workoutId)
                        if(checked !is NativeMeasuredWorkoutStoreResult.Durable ||
                            checked.stored!=saved)
                            NativeMeasuredWorkoutStoreResult.Rejected(
                                NativeMeasuredWorkoutStoreRefusal.STORAGE_UNAVAILABLE)
                        else checked.copy(replayed=false)
                    }
                }
            }
        }.getOrElse{NativeMeasuredWorkoutStoreResult.Rejected(
            NativeMeasuredWorkoutStoreRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Called ONLY by the enrolled History transaction after a matching intent
     * has been durably prepared. Idempotent after a crash between journal
     * commit and manifest finalize. Do not expose to screens/controllers.
     */
    internal fun applyPendingFinish(
        ownerKey:String,workoutId:String,
        operation:MeasuredWorkoutOperation.Finish
    ):NativeMeasuredWorkoutStoreResult {
        if(!id.matches(ownerKey) || !id.matches(workoutId))
            return NativeMeasuredWorkoutStoreResult.Rejected(
                NativeMeasuredWorkoutStoreRefusal.INVALID_ID)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val athlete=active(ownerKey)
                    ?:return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.NOT_AUTHORIZED)
                val pending=(history?.open(ownerKey,athlete.athleteId)
                    as? NativeHistoryLedgerResult.Durable)?.ledger?.pending
                    as? NativeHistoryWriteIntent.MeasuredFinish
                if(pending?.workoutId!=workoutId ||
                    pending?.operation!=operation)
                    return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.HISTORY_TRANSACTION_REQUIRED)
                val previous=fromDisk(ownerKey,athlete.athleteId,workoutId)
                val old=(previous as? NativeMeasuredWorkoutStoreResult.Durable)
                    ?.stored?:return@withOwnerTransaction previous
                val decided=MeasuredWorkoutJournalDomain.reduce(
                    old.journal,operation)
                val updated=(decided as? MeasuredWorkoutDecision.Proposed)
                    ?:return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.STALE_OR_INVALID_RECEIPT,
                        (decided as MeasuredWorkoutDecision.Rejected).reason)
                if(updated.replayed)
                    return@withOwnerTransaction if(
                        old.journal.finishedByEventId==operation.eventId &&
                        old.journal.revision==operation.expectedRevision+1)
                        previous else NativeMeasuredWorkoutStoreResult.Rejected(
                            NativeMeasuredWorkoutStoreRefusal.STALE_OR_INVALID_RECEIPT)
                if(active(ownerKey)!=athlete)
                    return@withOwnerTransaction NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.NOT_AUTHORIZED)
                val next=old.copy(journal=updated.journal)
                commit(path(ownerKey,workoutId),next)
                val verified=fromDisk(ownerKey,athlete.athleteId,workoutId)
                if(verified !is NativeMeasuredWorkoutStoreResult.Durable ||
                    verified.stored!=next)
                    NativeMeasuredWorkoutStoreResult.Rejected(
                        NativeMeasuredWorkoutStoreRefusal.STORAGE_UNAVAILABLE)
                else verified.copy(replayed=false)
            }
        }.getOrElse{NativeMeasuredWorkoutStoreResult.Rejected(
            NativeMeasuredWorkoutStoreRefusal.STORAGE_UNAVAILABLE)}
    }

    /** No direct Finish: never claim History has recorded an orphan sidecar. */
    fun finish(
        ownerKey:String,workoutId:String,
        operation:MeasuredWorkoutOperation.Finish
    ):NativeMeasuredWorkoutStoreResult =
        NativeMeasuredWorkoutStoreResult.Rejected(
            NativeMeasuredWorkoutStoreRefusal.HISTORY_TRANSACTION_REQUIRED)
}
