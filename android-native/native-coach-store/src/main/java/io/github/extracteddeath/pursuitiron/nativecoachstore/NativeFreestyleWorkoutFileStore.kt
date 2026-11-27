package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.IOException
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption

enum class NativeFreestyleRefusal {
    INVALID_ID, NOT_AUTHORIZED, SOURCE_UNAVAILABLE, HISTORY_NOT_READY,
    ID_CONFLICT, NOT_STARTED, CORRUPT, INVALID_RECEIPT, FINISH_REQUIRES_HISTORY,
    STORAGE_UNAVAILABLE
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
    private val history:NativeKotlinHistoryManifestFileStore
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
