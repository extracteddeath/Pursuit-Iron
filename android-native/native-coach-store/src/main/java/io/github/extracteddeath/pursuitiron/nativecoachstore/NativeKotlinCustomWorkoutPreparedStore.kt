package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.DataInputStream
import java.io.DataOutputStream
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
 * Cold-recoverable PREPARED session, NOT an issued workout. Owner-verified
 * content includes the EXACT custom snapshot and exact consent provenance.
 * The log/finish authority may never accept this as a journal or as proof
 * of historical enrollment. A separate native issuer must enforce both.
 */
enum class NativeCustomPreparationAuthority { CUSTOM_CYCLE, ACTIVATED_BLOCK }

data class NativeCustomPreparedWorkout(
    val ownerKey:String,
    val athleteId:String,
    val workoutId:String,
    val consentRevision:Long,
    val consentFingerprint:String,
    val snapshot:CustomWorkoutDaySnapshot,
    /** Explicit, durable provenance: activated consent is not a legacy grant. */
    val authority:NativeCustomPreparationAuthority =
        NativeCustomPreparationAuthority.CUSTOM_CYCLE,
    /** Only approved, rederived future-set amendments may populate this. */
    val repApprovalFingerprint:String?=null,
    val loadApprovalFingerprint:String?=null,
    /** Explicit athlete reset of unissued coaching changes to authored plan. */
    val resetToAuthored:Boolean=false
) {
    val isIssued:Boolean get() = false
    val mayLog:Boolean get() = false
    val mayFinish:Boolean get() = false
}

enum class NativeCustomPrepareRefusal {
    NOT_AUTHORIZED, INVALID_SELECTION, REVIEW_UNAVAILABLE,
    DIFFERENT_WORKOUT_ALREADY_PREPARED, CORRUPT_RECORD,
    STORAGE_UNAVAILABLE
}
sealed interface NativeCustomPrepareResult {
    data class Prepared(
        val workout:NativeCustomPreparedWorkout,
        val replayed:Boolean
    ):NativeCustomPrepareResult
    data class Rejected(val reason:NativeCustomPrepareRefusal):
        NativeCustomPrepareResult
}
sealed interface NativeCustomPreparedRead {
    data object Missing:NativeCustomPreparedRead
    data class Found(val workout:NativeCustomPreparedWorkout):
        NativeCustomPreparedRead
    data class Rejected(val reason:NativeCustomPrepareRefusal):
        NativeCustomPreparedRead
}

/**
 * Reset is the ONLY exception to immutable preparation: the athlete must
 * explicitly review and authorize the exact, original authored workout.
 * It replaces ONE unissued prepared snapshot atomically, never an issued
 * journal, history receipt, or source training cycle.
 */
enum class NativePreparedManualResetRefusal {
    INVALID_SELECTION,NOT_AUTHORIZED,NO_AMENDED_PREPARATION,
    ALREADY_ISSUED,CONSENT_CHANGED,UNSAFE_AMENDMENT,
    SETTINGS_UNAVAILABLE,STALE_PREVIEW,CONFIRMATION_REQUIRED,
    CORRUPT_RECORD,STORAGE_UNAVAILABLE
}
sealed interface NativePreparedManualResetPreview {
    data class Ready(
        val original:NativeCustomPreparedWorkout,
        val authored:CustomWorkoutDaySnapshot,
        val preparedFingerprint:String,
        val settingsRevision:Long
    ):NativePreparedManualResetPreview {
        val mayChangeIssuedWorkout:Boolean get()=false
        val mayChangeHistory:Boolean get()=false
    }
    data class Rejected(val reason:NativePreparedManualResetRefusal):
        NativePreparedManualResetPreview
}
sealed interface NativePreparedManualResetWrite {
    data class Reset(val prepared:NativeCustomPreparedWorkout):
        NativePreparedManualResetWrite
    data class Rejected(val reason:NativePreparedManualResetRefusal):
        NativePreparedManualResetWrite
}

/**
 * One-way immutable preparation file: no mutation or overwrite API.
 * Idempotent same-content retries are safe; a different cycle/week/target
 * with the same workout ID is a hard collision. All file reads and writes
 * take the same owner OS revision fence as gym, signoff and program edits.
 */
class NativeKotlinCustomWorkoutPreparedStore(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val preflight:NativeKotlinCustomWorkoutPreflight,
    private val activationHandoff:NativeKotlinFutureActivationHandoffFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val fingerprint=Regex("^[a-f0-9]{64}$")
    private val magic=0x50494350 // PICP
    private val version=1
    private val maxFileSize=NativeKotlinCustomWorkoutSnapshotCodec.MAX_BYTES+1024
    private fun current(ownerKey:String):NativeKotlinOwnerIdentity? {
        if(!id.matches(ownerKey))return null
        return runCatching{identity.signedInIdentity()}.getOrNull()
            ?.takeIf{it.ownerKey==ownerKey && id.matches(it.athleteId)}
    }
    private fun path(ownerKey:String,workoutId:String):Path {
        val name=MessageDigest.getInstance("SHA-256")
            .digest("$ownerKey/$workoutId".toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return directory.resolve("prepared-custom-$name.bin")
    }
    private fun encode(workout:NativeCustomPreparedWorkout):ByteArray {
        require(listOf(workout.ownerKey,workout.athleteId,
            workout.workoutId).all(id::matches))
        require(workout.consentRevision>0 &&
            fingerprint.matches(workout.consentFingerprint))
        require(workout.snapshot.stamp.athleteId==workout.athleteId)
        require(workout.repApprovalFingerprint==null ||
            (workout.authority==NativeCustomPreparationAuthority.ACTIVATED_BLOCK &&
                fingerprint.matches(workout.repApprovalFingerprint)))
        require(workout.loadApprovalFingerprint==null ||
            (workout.authority==NativeCustomPreparationAuthority.ACTIVATED_BLOCK &&
                fingerprint.matches(workout.loadApprovalFingerprint)))
        require(workout.repApprovalFingerprint==null ||
            workout.loadApprovalFingerprint==null)
        require(!workout.resetToAuthored ||
            (workout.authority==NativeCustomPreparationAuthority.ACTIVATED_BLOCK &&
                workout.repApprovalFingerprint==null &&
                workout.loadApprovalFingerprint==null))
        val session=NativeKotlinCustomWorkoutSnapshotCodec.encode(
            workout.snapshot)
        val payload=ByteArrayOutputStream()
        DataOutputStream(payload).use{out->
            out.writeInt(magic)
            out.writeInt(if(workout.resetToAuthored)5 else
                if(workout.loadApprovalFingerprint!=null)4 else
                if(workout.repApprovalFingerprint!=null)3 else
                    if(workout.authority==
                        NativeCustomPreparationAuthority.CUSTOM_CYCLE) version else 2)
            out.writeUTF(workout.ownerKey)
            out.writeUTF(workout.athleteId)
            out.writeUTF(workout.workoutId)
            out.writeLong(workout.consentRevision)
            out.writeUTF(workout.consentFingerprint)
            if(workout.authority==NativeCustomPreparationAuthority.ACTIVATED_BLOCK)
                out.writeUTF(workout.authority.name)
            if(workout.repApprovalFingerprint!=null)
                out.writeUTF(workout.repApprovalFingerprint)
            if(workout.loadApprovalFingerprint!=null)
                out.writeUTF(workout.loadApprovalFingerprint)
            out.writeInt(session.size);out.write(session)
        }
        val bytes=payload.toByteArray()
        require(bytes.size+36<=maxFileSize)
        return ByteArrayOutputStream().also{result->
            DataOutputStream(result).use{out->
                out.writeInt(bytes.size);out.write(bytes)
                out.write(MessageDigest.getInstance("SHA-256")
                    .digest(bytes))
            }
        }.toByteArray()
    }
    private fun decode(file:Path):NativeCustomPreparedWorkout? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file) ||
            Files.size(file) !in 44L..maxFileSize.toLong())
            throw IOException("invalid prepared custom workout file")
        val bytes=Files.readAllBytes(file)
        val input=DataInputStream(ByteArrayInputStream(bytes))
        val size=input.readInt()
        if(size !in 16..(maxFileSize-36) ||
            size+36!=bytes.size)
            throw IOException("invalid prepared envelope")
        val payload=ByteArray(size);input.readFully(payload)
        val expected=ByteArray(32);input.readFully(expected)
        if(!MessageDigest.isEqual(expected,MessageDigest.getInstance(
            "SHA-256").digest(payload)))
            throw IOException("prepared snapshot checksum mismatch")
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use{data->
                if(data.readInt()!=magic)
                    throw IOException("unsupported prepared custom snapshot magic")
                val schema=data.readInt()
                if(schema !in 1..5)
                    throw IOException("unsupported prepared custom snapshot version")
                val owner=data.readUTF()
                val athlete=data.readUTF()
                val workoutId=data.readUTF()
                val revision=data.readLong()
                val receiptHash=data.readUTF()
                if(!id.matches(owner) || !id.matches(athlete) ||
                    !id.matches(workoutId) || revision<1 ||
                    !fingerprint.matches(receiptHash))
                    throw IOException("invalid prepared identity")
                val authority=if(schema==1)
                    NativeCustomPreparationAuthority.CUSTOM_CYCLE
                else {
                    val authorityName=data.readUTF()
                    NativeCustomPreparationAuthority.entries.firstOrNull{
                        it.name==authorityName
                    } ?:throw IOException("unrecognized preparation authority")
                }
                val repProof=if(schema==3)data.readUTF() else null
                val loadProof=if(schema==4)data.readUTF() else null
                if((repProof!=null || loadProof!=null) &&
                    (authority!=NativeCustomPreparationAuthority.ACTIVATED_BLOCK ||
                        !(fingerprint.matches(checkNotNull(repProof ?: loadProof)))))
                    throw IOException("invalid amended-preparation proof")
                val sessionBytes=data.readInt()
                if(sessionBytes !in 16..NativeKotlinCustomWorkoutSnapshotCodec.MAX_BYTES ||
                    sessionBytes>data.available())
                    throw IOException("invalid prepared session size")
                val session=ByteArray(sessionBytes)
                data.readFully(session)
                if(data.available()!=0)
                    throw IOException("trailing prepared data")
                NativeCustomPreparedWorkout(owner,athlete,workoutId,
                    revision,receiptHash,
                    NativeKotlinCustomWorkoutSnapshotCodec.decode(session),
                    authority,repProof,loadProof,schema==5).also{
                        if(it.snapshot.stamp.athleteId!=athlete)
                            throw IOException("prepared athlete mismatch")
                    }
            }
        }catch(ex:Exception){throw IOException("invalid prepared workout",ex)}
    }
    private fun atomicWrite(file:Path,bytes:ByteArray) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))
            throw IOException("unsafe workout preparation directory")
        val pending=Files.createTempFile(directory,
            "prepared-custom-", ".pending")
        try {
            FileChannel.open(pending,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE)
            }catch(ex:AtomicMoveNotSupportedException){
                throw IOException("atomic prepared write unsupported",ex)
            }
        }finally {Files.deleteIfExists(pending)}
    }
    /** Only a verified, signed rep OR load amendment is resettable.
     * Every unrelated exercise, field, set, rep ceiling and load must still
     * match the current approved authored snapshot exactly. */
    private fun isResettableAmendment(
        selected:NativeCustomPreparedWorkout,
        authored:CustomWorkoutDaySnapshot
    ):Boolean {
        if(selected.resetToAuthored ||
            (selected.repApprovalFingerprint==null &&
                selected.loadApprovalFingerprint==null) ||
            selected.authority!=NativeCustomPreparationAuthority.ACTIVATED_BLOCK ||
            selected.snapshot.copy(slots=authored.slots)!=authored ||
            selected.snapshot.slots.size!=authored.slots.size)
            return false
        var amendedSlots=0
        for((actualSlot,baseSlot) in selected.snapshot.slots.zip(authored.slots)) {
            if(actualSlot.copy(sets=baseSlot.sets)!=baseSlot ||
                actualSlot.sets.size!=baseSlot.sets.size)return false
            var edited=0
            for((set,base) in actualSlot.sets.zip(baseSlot.sets)) {
                if(set==base)continue
                if(set.copy(prescription=base.prescription)!=base ||
                    set.prescription.type!=DraftSetType.WORKING ||
                    base.prescription.type!=DraftSetType.WORKING ||
                    set.prescription.amplifier!=null)return false
                val sameExceptReps=set.prescription.copy(
                    reps=base.prescription.reps)==base.prescription
                val sameExceptLoad=set.prescription.copy(
                    load=base.prescription.load)==base.prescription
                val goodRep=selected.repApprovalFingerprint!=null &&
                    sameExceptReps &&
                    set.prescription.reps.maximum==
                        base.prescription.reps.maximum &&
                    set.prescription.reps.minimum>
                        base.prescription.reps.minimum
                val originalLoad=base.prescription.load
                    as? DraftSetLoad.Verified
                val actualLoad=set.prescription.load
                    as? DraftSetLoad.Verified
                val goodLoad=selected.loadApprovalFingerprint!=null &&
                    sameExceptLoad && originalLoad!=null && actualLoad!=null &&
                    actualLoad.unit==originalLoad.unit &&
                    actualLoad.basis==originalLoad.basis &&
                    actualLoad.value>originalLoad.value
                if(!goodRep && !goodLoad)return false
                edited++
            }
            if(edited>0)amendedSlots++
        }
        return amendedSlots==1
    }

    private fun storedFingerprint(ownerKey:String,workoutId:String):String {
        val bytes=Files.readAllBytes(path(ownerKey,workoutId))
        return MessageDigest.getInstance("SHA-256").digest(bytes)
            .joinToString(""){"%02x".format(it.toInt() and 255)}
    }

    /**
     * Atomically replaces exactly ONE prepared file. The previous or next
     * complete checksummed snapshot survives a process crash; there is no
     * second store to update, and no partial session can be observed.
     */
    private fun replacePreparedAtomically(
        ownerKey:String,workoutId:String,reset:NativeCustomPreparedWorkout
    ) {
        if(!Files.isDirectory(directory)||Files.isSymbolicLink(directory))
            throw IOException("unsafe preparation directory")
        val original=path(ownerKey,workoutId)
        if(!Files.isRegularFile(original)||Files.isSymbolicLink(original))
            throw IOException("unavailable prepared snapshot")
        val stage=Files.createTempFile(directory,
            "manual-preparation-reset-", ".pending")
        try {
            FileChannel.open(stage,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val body=ByteBuffer.wrap(encode(reset))
                while(body.hasRemaining())channel.write(body)
                channel.force(true)
            }
            Files.move(stage,original,StandardCopyOption.ATOMIC_MOVE,
                StandardCopyOption.REPLACE_EXISTING)
        }finally {
            Files.deleteIfExists(stage)
        }
    }

    private fun openFenced(
        ownerKey:String,workoutId:String,
        owner:NativeKotlinOwnerIdentity
    ):NativeCustomPreparedRead {
        val stored=try{decode(path(ownerKey,workoutId))}
            catch(_:Exception){return NativeCustomPreparedRead.Rejected(
                NativeCustomPrepareRefusal.CORRUPT_RECORD)}
        if(stored==null)return NativeCustomPreparedRead.Missing
        if(stored.ownerKey!=ownerKey ||
            stored.athleteId!=owner.athleteId ||
            stored.workoutId!=workoutId)
            return NativeCustomPreparedRead.Rejected(
                NativeCustomPrepareRefusal.NOT_AUTHORIZED)
        return NativeCustomPreparedRead.Found(stored)
    }
    /** An authenticated athlete may re-view an OLD prepared prescription.
     * The UI MUST NOT infer its consent is still current. Never issues. */
    fun open(
        ownerKey:String,workoutId:String
    ):NativeCustomPreparedRead {
        if(!id.matches(workoutId) || current(ownerKey)==null)
            return NativeCustomPreparedRead.Rejected(
                NativeCustomPrepareRefusal.NOT_AUTHORIZED)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val owner=current(ownerKey)
                    ?:return@withOwnerTransaction NativeCustomPreparedRead.Rejected(
                        NativeCustomPrepareRefusal.NOT_AUTHORIZED)
                openFenced(ownerKey,workoutId,owner)
            }
        }.getOrElse {NativeCustomPreparedRead.Rejected(
            NativeCustomPrepareRefusal.STORAGE_UNAVAILABLE)}
    }
    fun prepare(
        ownerKey:String,
        workoutId:String,
        cycleId:String,
        absoluteWeek:Int,
        weekday:Int,
        coachingInputs:CustomCoachAuditInputs
    ):NativeCustomPrepareResult {
        if(!id.matches(workoutId) || !id.matches(cycleId) ||
            absoluteWeek !in 1..104 || weekday !in 0..6)
            return NativeCustomPrepareResult.Rejected(
                NativeCustomPrepareRefusal.INVALID_SELECTION)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val owner=current(ownerKey)
                    ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.NOT_AUTHORIZED)
                if(activationHandoff?.blocksNewIssuance(ownerKey)==true)
                    return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
                val pre=preflight.inspect(ownerKey,cycleId,
                    absoluteWeek,weekday,coachingInputs)
                if(pre !is NativeCustomPreflightResult.ReviewedPreview)
                    return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
                val planned=NativeCustomPreparedWorkout(ownerKey,
                    owner.athleteId,workoutId,pre.receipt.recordRevision,
                    pre.receipt.fingerprintSha256,pre.session)
                val existing=openFenced(ownerKey,workoutId,owner)
                if(existing is NativeCustomPreparedRead.Rejected)
                    return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        existing.reason)
                if(existing is NativeCustomPreparedRead.Found)
                    return@withOwnerTransaction if(existing.workout==planned)
                        NativeCustomPrepareResult.Prepared(existing.workout,true)
                    else NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED)
                if(current(ownerKey)!=owner)
                    return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.NOT_AUTHORIZED)
                val bytes=encode(planned)
                atomicWrite(path(ownerKey,workoutId),bytes)
                NativeCustomPrepareResult.Prepared(planned,false)
            }
        }.getOrElse {NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Prepare the EXACT post-activation workout after its independent athlete
     * signoff. Reuses the immutable custom-snapshot store and old codec reads.
     *
     * Crucially this is not a workout issuance exception: the activation
     * handoff stays in place, and the ordinary custom issuer MUST reject
     * ACTIVATED_BLOCK authority even if a future migration clears the fence.
     */
    fun prepareActivated(
        ownerKey:String,workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,coachingInputs:CustomCoachAuditInputs,
        signoffs:NativeKotlinActivatedCycleSignoffFileStore,
        activation:NativeKotlinFutureActivationCommitTransaction,
        repApprovals:NativeKotlinCustomNextRepApprovalFileStore?=null,
        loadApprovals:NativeKotlinCustomNextLoadApprovalFileStore?=null
    ):NativeCustomPrepareResult {
        if(!id.matches(workoutId)||!id.matches(cycleId) ||
            absoluteWeek !in 1..104||weekday !in 0..6)
            return NativeCustomPrepareResult.Rejected(
                NativeCustomPrepareRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=current(ownerKey)
                ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.NOT_AUTHORIZED)
            val handoff=(activationHandoff?.read(ownerKey)
                as? NativeFutureHandoffReview.Staged)?.receipt
                ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val consent=(signoffs.read(ownerKey,coachingInputs)
                as? NativeActivatedSignoffRead.Found)?.takeIf{it.isCurrent}
                ?.receipt
                ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            if(consent.ownerKey!=ownerKey || consent.athleteId!=owner.athleteId ||
                consent.cycleId!=cycleId || consent.blockId!=handoff.blockId ||
                consent.firstWeek!=handoff.firstWeek ||
                consent.sourceRevision!=handoff.sourceRevision+1 ||
                consent.firstWeek>absoluteWeek)
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val accepted=activation.read(ownerKey)
                as? NativeFutureCommitReview.Applied
                ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            if(accepted.context.cycleId!=cycleId ||
                accepted.context.blockId!=consent.blockId ||
                accepted.context.sourceRevision!=consent.sourceRevision)
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val projection=activation.previewActivatedWorkout(
                ownerKey,coachingInputs,absoluteWeek,weekday)
                as? CustomWorkoutSnapshotResult.Preview
                ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val session=projection.snapshot
            if(session.stamp.athleteId!=owner.athleteId ||
                session.stamp.cycleId!=cycleId ||
                session.stamp.gymCatalogSourceRevision!=consent.sourceRevision ||
                session.blockId!=consent.blockId ||
                session.absoluteWeek!=absoluteWeek || session.weekday!=weekday)
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val repApproval=NativeKotlinStaleWorkoutAdvicePolicy.rep(
                repApprovals?.readCurrent(ownerKey,workoutId,
                    coachingInputs,absoluteWeek,weekday)
                    ?:NativeCustomNextRepRead.Missing)
            if(repApproval is NativeCustomNextRepRead.Rejected)
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val amended=(repApproval as? NativeCustomNextRepRead.Current)
            val loadRead=NativeKotlinStaleWorkoutAdvicePolicy.load(
                loadApprovals?.readCurrent(ownerKey,workoutId,
                    coachingInputs,absoluteWeek,weekday)
                    ?:NativeCustomLoadRead.Missing)
            if(loadRead is NativeCustomLoadRead.Rejected ||
                (amended!=null && loadRead is NativeCustomLoadRead.Current))
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val load=loadRead as? NativeCustomLoadRead.Current
            val planned=NativeCustomPreparedWorkout(ownerKey,owner.athleteId,
                workoutId,consent.recordRevision,consent.reviewFingerprint,
                load?.amendment?.amended ?: amended?.amendment?.amended ?:session,
                NativeCustomPreparationAuthority.ACTIVATED_BLOCK,
                amended?.receipt?.fingerprint,load?.receipt?.fingerprint)
            val existing=openFenced(ownerKey,workoutId,owner)
            if(existing is NativeCustomPreparedRead.Rejected)
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    existing.reason)
            if(existing is NativeCustomPreparedRead.Found)
                return@withOwnerTransaction if(existing.workout==planned)
                    NativeCustomPrepareResult.Prepared(existing.workout,true)
                else NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED)
            // Multiple IDs must NEVER prepare the same cycle/week/weekday.
            // Otherwise the athlete could log two contradictory versions of
            // one session and pollute next-session progression history.
            val all=inventory(ownerKey) as? NativePreparedInventoryResult.Found
                ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            if(all.workouts.any{
                it.workoutId!=workoutId &&
                    it.snapshot.stamp.cycleId==cycleId &&
                    it.snapshot.absoluteWeek==absoluteWeek &&
                    it.snapshot.weekday==weekday
            })return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED)
            if(current(ownerKey)!=owner)
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.NOT_AUTHORIZED)
            atomicWrite(path(ownerKey,workoutId),encode(planned))
            NativeCustomPrepareResult.Prepared(planned,false)
        }}.getOrElse{NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Enumerate EVERY immutable preparation file under the owner OS fence,
     * including abandoned or never-issued work. Unknown, renamed, pending,
     * corrupt and over-capacity records fail closed rather than vanishing.
     * A different owner's well-formed records are not this athlete's work.
     */
    internal fun inventory(ownerKey:String):NativePreparedInventoryResult {
        fun fail(reason:NativeCustomInventoryRefusal)=
            NativePreparedInventoryResult.Rejected(reason)
        if(current(ownerKey)==null)
            return fail(NativeCustomInventoryRefusal.NOT_AUTHORIZED)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val signed=current(ownerKey)
                    ?:return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.NOT_AUTHORIZED)
                if(!Files.exists(directory))
                    return@withOwnerTransaction NativePreparedInventoryResult.Found(
                        emptyList())
                if(Files.isSymbolicLink(directory))
                    return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                val files=Files.list(directory).use{stream->
                    stream.filter{it.fileName.toString().startsWith(
                        "prepared-custom-")}.toList()
                }
                if(files.size>4096)
                    return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.TOO_MANY_FILES)
                val found=mutableListOf<NativeCustomPreparedWorkout>()
                val filename=Regex("^prepared-custom-[a-f0-9]{64}\\.bin$")
                for(file in files){
                    if(!filename.matches(file.fileName.toString()) ||
                        !Files.isRegularFile(file) || Files.isSymbolicLink(file))
                        return@withOwnerTransaction fail(
                            NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                    val record=try{decode(file)}catch(_:Exception){
                        return@withOwnerTransaction fail(
                            NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                    }?:return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                    if(path(record.ownerKey,record.workoutId)!=file)
                        return@withOwnerTransaction fail(
                            NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                    if(record.ownerKey==ownerKey) {
                        if(record.athleteId!=signed.athleteId)
                            return@withOwnerTransaction fail(
                                NativeCustomInventoryRefusal.NOT_AUTHORIZED)
                        found+=record
                    }
                }
                if(current(ownerKey)!=signed)
                    return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.NOT_AUTHORIZED)
                NativePreparedInventoryResult.Found(found.toList())
            }
        }.getOrElse{fail(NativeCustomInventoryRefusal.STORAGE_UNAVAILABLE)}
    }

}
