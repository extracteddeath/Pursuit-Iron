package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

enum class NativeFreestyleRefusal {
    INVALID_ID, NOT_AUTHORIZED, SOURCE_UNAVAILABLE, HISTORY_NOT_READY,
    ID_CONFLICT, NOT_STARTED, CORRUPT, INVALID_RECEIPT, FINISH_REQUIRES_HISTORY,
    STORAGE_UNAVAILABLE, FINISH_MISMATCH
}
sealed interface NativeFreestyleResult {
    data class Durable(val workout:KotlinFreestyleWorkout,val replayed:Boolean):NativeFreestyleResult
    data class Rejected(val reason:NativeFreestyleRefusal,val journalReason:KotlinFreestyleRefusal?=null):
        NativeFreestyleResult
}

/** Native owner-fenced active-session store. Finish is deliberately withheld
 * until the existing canonical History manifest has a recoverable freestyle
 * transaction, so there can be no completed but unindexed workout. */
class NativeFreestyleWorkoutFileStore(
    private val root:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val source:NativeKotlinCustomAuthoringSourceFileStore,
    private val history:NativeKotlinHistoryManifestFileStore,
    private val clock:NativeHistoryCompletionClock=NativeHistoryCompletionClock.Undated
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private fun reject(reason:NativeFreestyleRefusal)=NativeFreestyleResult.Rejected(reason)
    private fun guarded(owner:String,workout:String,action:(NativeKotlinOwnerIdentity)->NativeFreestyleResult)
        :NativeFreestyleResult {
        if(!id.matches(owner) || !id.matches(workout))return reject(NativeFreestyleRefusal.INVALID_ID)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                val current=identity.signedInIdentity()?.takeIf{
                    it.ownerKey==owner && id.matches(it.athleteId)
                }?:return@withOwnerTransaction reject(NativeFreestyleRefusal.NOT_AUTHORIZED)
                action(current)
            }
        }.getOrElse{reject(NativeFreestyleRefusal.STORAGE_UNAVAILABLE)}
    }
    private fun path(owner:String,workout:String)=NativeMeasuredWorkoutIdClaim.freestylePath(root,owner,workout)
    private fun read(owner:String,athlete:String,workout:String):NativeFreestyleResult {
        val file=path(owner,workout)
        if(Files.isSymbolicLink(file))return reject(NativeFreestyleRefusal.CORRUPT)
        if(!Files.exists(file))return reject(NativeFreestyleRefusal.NOT_STARTED)
        return try {
            if(!Files.isRegularFile(file) || Files.size(file) !in 44L..NativeFreestyleWorkoutCodec.MAX_BYTES.toLong())
                throw IOException("invalid freestyle file")
            val state=NativeFreestyleWorkoutCodec.decode(Files.readAllBytes(file),owner,athlete,workout)
            NativeFreestyleResult.Durable(state,true)
        }catch(_:Exception){reject(NativeFreestyleRefusal.CORRUPT)}
    }
    private fun save(owner:NativeKotlinOwnerIdentity,state:KotlinFreestyleWorkout):NativeFreestyleResult {
        if(identity.signedInIdentity()!=owner)return reject(NativeFreestyleRefusal.NOT_AUTHORIZED)
        val file=path(owner.ownerKey,state.workoutId)
        val bytes=NativeFreestyleWorkoutCodec.encode(owner.ownerKey,state)
        Files.createDirectories(root)
        if(Files.isSymbolicLink(root) || !Files.isDirectory(root) || Files.isSymbolicLink(file))
            throw IOException("unsafe freestyle path")
        val temp=Files.createTempFile(root,"freestyle-", ".pending")
        try {
            FileChannel.open(temp,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        }finally{Files.deleteIfExists(temp)}
        val verified=read(owner.ownerKey,owner.athleteId,state.workoutId)
        return if(verified is NativeFreestyleResult.Durable && verified.workout==state)
            verified.copy(replayed=false) else reject(NativeFreestyleRefusal.STORAGE_UNAVAILABLE)
    }
    private fun source(owner:NativeKotlinOwnerIdentity):NativeCustomAuthoringSnapshot? =
        (source.read(owner.ownerKey) as? NativeAuthoringSourceRead.Found)?.source?.takeIf{
            it.athleteId==owner.athleteId && it.ownerKey==owner.ownerKey
        }
    private fun ready(owner:NativeKotlinOwnerIdentity,workout:String):Boolean {
        val ledger=(history.open(owner.ownerKey,owner.athleteId) as? NativeHistoryLedgerResult.Durable)?.ledger
            ?:return false
        return ledger.readyForHistoryReads && ledger.hasCompletionCapacity &&
            ledger.completions.none{it.workoutId==workout}
    }

    private fun finishReceipt(owner:String,workout:KotlinFreestyleWorkout):String {
        require(workout.finishedByEventId!=null)
        val digest=MessageDigest.getInstance("SHA-256")
        digest.update("pursuit-freestyle-finish-v1".toByteArray(Charsets.UTF_8))
        digest.update(NativeFreestyleWorkoutCodec.encode(owner,workout))
        return "freestyle-v1-"+digest.digest().joinToString(""){"%02x".format(it.toInt() and 255)}
    }
    private fun ledger(owner:NativeKotlinOwnerIdentity):NativeHistoryLedger? =
        (history.open(owner.ownerKey,owner.athleteId) as? NativeHistoryLedgerResult.Durable)
            ?.ledger?.takeIf{it.ownerKey==owner.ownerKey && it.athleteId==owner.athleteId}

    private fun committed(owner:NativeKotlinOwnerIdentity,entry:KotlinHistoryManifestEntry,
        replayed:Boolean):NativeFreestyleResult {
        val saved=read(owner.ownerKey,owner.athleteId,entry.workoutId)
        val state=(saved as? NativeFreestyleResult.Durable)?.workout?:return saved
        if(state.finishedByEventId!=entry.finishEventId ||
            state.revision!=entry.originalJournalRevision || entry.savedCorrectionRevision!=0L ||
            finishReceipt(owner.ownerKey,state)!=entry.storedReceiptId)
            return reject(NativeFreestyleRefusal.FINISH_MISMATCH)
        return NativeFreestyleResult.Durable(state,replayed)
    }

    /** Read an exact committed freestyle entry under the shared owner
     * fence, with no program targets or inference. Pending or unindexed
     * archives cannot be published as History facts. */
    internal fun readCommitted(owner:String,athlete:String,entry:KotlinHistoryManifestEntry)
        :NativeFreestyleResult=guarded(owner,entry.workoutId){current->
        if(current.athleteId!=athlete)return@guarded reject(NativeFreestyleRefusal.NOT_AUTHORIZED)
        val manifest=ledger(current)?:return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        if(!manifest.readyForHistoryReads ||
            manifest.completions.singleOrNull{it.workoutId==entry.workoutId}!=entry)
            return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        committed(current,entry,true)
    }

    /** The canonical manifest owns completion. Only the exact prepared intent
     * can advance the persisted active journal, even after a process death. */
    private fun replayFinish(owner:NativeKotlinOwnerIdentity,ledger:NativeHistoryLedger):NativeFreestyleResult {
        val intent=ledger.pending as? NativeHistoryWriteIntent.FreestyleFinish
            ?:return reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        val loaded=read(owner.ownerKey,owner.athleteId,intent.workoutId)
        val state=(loaded as? NativeFreestyleResult.Durable)?.workout?:return loaded
        val next=KotlinFreestyleWorkoutDomain.reduce(state,intent.operation)
            as? KotlinFreestyleDecision.Proposed?:return reject(NativeFreestyleRefusal.FINISH_MISMATCH)
        if(next.workout.finishedByEventId!=intent.operation.eventId ||
            next.workout.revision!=intent.operation.expectedRevision+1)
            return reject(NativeFreestyleRefusal.FINISH_MISMATCH)
        if(!next.replayed) {
            val written=save(owner,next.workout)
            if(written !is NativeFreestyleResult.Durable)return written
        }
        if(identity.signedInIdentity()!=owner)return reject(NativeFreestyleRefusal.NOT_AUTHORIZED)
        val entry=KotlinHistoryManifestEntry(ledger.completions.size.toLong()+1,
            intent.workoutId,finishReceipt(owner.ownerKey,next.workout),
            intent.operation.eventId,next.workout.revision,0,intent.completedAtEpochMillis)
        val finalized=history.finalize(owner.ownerKey,owner.athleteId,intent,entry)
            as? NativeHistoryLedgerResult.Durable ?:return reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        if(finalized.ledger.pending!=null || finalized.ledger.completions.lastOrNull()!=entry)
            return reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        return committed(owner,entry,false)
    }

    /** Internal source candidate. Do not route live UI through this until
     * the shared mixed History reader accepts and displays freestyle facts. */
    internal fun complete(owner:String,workout:String,operation:KotlinFreestyleOperation.Finish)
        :NativeFreestyleResult=guarded(owner,workout){current->
        var manifest=ledger(current)?:return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        if(manifest.pending!=null) {
            if(manifest.pending !is NativeHistoryWriteIntent.FreestyleFinish)
                return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
            val recovered=replayFinish(current,manifest)
            if(recovered !is NativeFreestyleResult.Durable)return@guarded recovered
            manifest=ledger(current)?:return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        }
        val existing=manifest.completions.singleOrNull{it.workoutId==workout}
        if(existing!=null) {
            if(existing.finishEventId!=operation.eventId ||
                existing.originalJournalRevision!=operation.expectedRevision+1 ||
                !existing.storedReceiptId.startsWith("freestyle-v1-"))
                return@guarded reject(NativeFreestyleRefusal.FINISH_MISMATCH)
            return@guarded committed(current,existing,true)
        }
        if(!manifest.readyForHistoryReads || !manifest.hasCompletionCapacity)
            return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        val opened=read(owner,current.athleteId,workout)
        val prior=(opened as? NativeFreestyleResult.Durable)?.workout?:return@guarded opened
        val proposed=KotlinFreestyleWorkoutDomain.reduce(prior,operation)
            as? KotlinFreestyleDecision.Proposed?:return@guarded reject(NativeFreestyleRefusal.FINISH_MISMATCH)
        if(proposed.replayed)return@guarded reject(NativeFreestyleRefusal.FINISH_MISMATCH)
        // Ensure the bounded archive is encodable BEFORE a pending intent
        // can block the entire owner's History.
        NativeFreestyleWorkoutCodec.encode(owner,proposed.workout)
        val intent=NativeHistoryWriteIntent.FreestyleFinish(workout,operation,clock.observe())
        val prepared=history.prepare(owner,current.athleteId,manifest.revision,intent)
            as? NativeHistoryLedgerResult.Durable
            ?:return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        replayFinish(current,prepared.ledger)
    }

    internal fun recover(owner:String,workout:String):NativeFreestyleResult=guarded(owner,workout){current->
        val manifest=ledger(current)?:return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        if(manifest.pending?.workoutId!=workout)
            return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
        replayFinish(current,manifest)
    }

    fun open(owner:String,workout:String):NativeFreestyleResult=guarded(owner,workout){
        read(owner,it.athleteId,workout)
    }
    /** Empty start or explicit routine start, only with current native gym.
     * Neither path edits or creates an approved program. */
    fun start(owner:String,workout:String,routine:KotlinFreestyleRoutine?=null):NativeFreestyleResult =
        guarded(owner,workout){current->
            val existing=read(owner,current.athleteId,workout)
            if(existing !is NativeFreestyleResult.Rejected || existing.reason!=NativeFreestyleRefusal.NOT_STARTED)
                return@guarded existing
            if(!ready(current,workout))return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
            if(NativeMeasuredWorkoutIdClaim.anyClaimed(root,owner,workout))
                return@guarded reject(NativeFreestyleRefusal.ID_CONFLICT)
            val reviewed=source(current)?:return@guarded reject(NativeFreestyleRefusal.SOURCE_UNAVAILABLE)
            val state=if(routine==null)KotlinFreestyleWorkoutDomain.source(reviewed.setup,reviewed.revision)
                ?.let{KotlinFreestyleWorkoutDomain.begin(workout,it)}
            else KotlinFreestyleWorkoutDomain.startRoutine(workout,routine,reviewed.setup,
                reviewed.revision,reviewed.catalog)
            save(current,state?:return@guarded reject(NativeFreestyleRefusal.SOURCE_UNAVAILABLE))
        }

    fun apply(owner:String,workout:String,operation:KotlinFreestyleOperation):NativeFreestyleResult =
        guarded(owner,workout){current->
            val existing=read(owner,current.athleteId,workout)
            val old=(existing as? NativeFreestyleResult.Durable)?.workout?:return@guarded existing
            if(operation is KotlinFreestyleOperation.Finish)
                return@guarded reject(NativeFreestyleRefusal.FINISH_REQUIRES_HISTORY)
            val result=KotlinFreestyleWorkoutDomain.reduce(old,operation)
            if(result is KotlinFreestyleDecision.Rejected)
                return@guarded NativeFreestyleResult.Rejected(NativeFreestyleRefusal.INVALID_RECEIPT,result.reason)
            result as KotlinFreestyleDecision.Proposed
            if(result.replayed)return@guarded NativeFreestyleResult.Durable(old,true)
            if(!ready(current,workout))return@guarded reject(NativeFreestyleRefusal.HISTORY_NOT_READY)
            // Historic context is sufficient for already recorded exercise
            // actuals, but new selections must still match the exact source.
            if(operation is KotlinFreestyleOperation.Add) {
                val reviewed=source(current)?:return@guarded reject(NativeFreestyleRefusal.SOURCE_UNAVAILABLE)
                val chosen=KotlinFreestyleWorkoutDomain.selectedExercise(old,reviewed.setup,
                    reviewed.revision,reviewed.catalog,operation.exercise.slotId,operation.exercise.exerciseId)
                if(chosen!=operation.exercise)
                    return@guarded reject(NativeFreestyleRefusal.SOURCE_UNAVAILABLE)
            }
            save(current,result.workout)
        }
}
