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
    val resetToAuthored:Boolean=false,
    /** Separate from a one-workout load amendment. */
    val futureWeekApprovalFingerprint:String?=null,
    val futureWeekSlotId:String?=null,
    /** Ordinary-cycle calibration has distinct consent from earned increases. */
    val calibrationApprovalFingerprint:String?=null,
    /** One-workout explicit choice of heavier authored targets, not progression. */
    val authoredReturnFingerprint:String?=null
) {
    val isIssued:Boolean get() = false
    val mayLog:Boolean get() = false
    val mayFinish:Boolean get() = false
}

enum class NativeCustomPrepareRefusal {
    NOT_AUTHORIZED, INVALID_SELECTION, REVIEW_UNAVAILABLE,
    DIFFERENT_WORKOUT_ALREADY_PREPARED, CORRUPT_RECORD,
    AUTHORED_LOAD_REVERSION_REVIEW_REQUIRED, CONFIRMATION_REQUIRED,
    REVIEW_CHANGED, STORAGE_UNAVAILABLE
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
 * Reset changes an amended preparation only after the athlete explicitly
 * reviews and authorizes the exact, original authored workout.
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
 * Immutable prepared targets, except for explicitly confirmed authored reset.
 * Adding/renewing authored-return consent changes only its proof, never targets.
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
        require(workout.calibrationApprovalFingerprint==null ||
            (workout.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
                fingerprint.matches(workout.calibrationApprovalFingerprint)))
        require(workout.authoredReturnFingerprint==null ||
            (workout.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
                fingerprint.matches(workout.authoredReturnFingerprint)))
        require((workout.futureWeekApprovalFingerprint==null)==
            (workout.futureWeekSlotId==null))
        require(workout.futureWeekApprovalFingerprint==null ||
            (workout.authority==NativeCustomPreparationAuthority.ACTIVATED_BLOCK &&
                fingerprint.matches(workout.futureWeekApprovalFingerprint) &&
                id.matches(checkNotNull(workout.futureWeekSlotId))))
        require(listOf(workout.repApprovalFingerprint,
            workout.loadApprovalFingerprint,
            workout.futureWeekApprovalFingerprint,
            workout.calibrationApprovalFingerprint,
            workout.authoredReturnFingerprint).count{it!=null}<=1)
        require(workout.snapshot.shortSessionLimitMinutes==null ||
            (workout.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
                !workout.resetToAuthored &&
                workout.repApprovalFingerprint==null &&
                workout.loadApprovalFingerprint==null &&
                workout.futureWeekApprovalFingerprint==null &&
                workout.calibrationApprovalFingerprint==null &&
                workout.authoredReturnFingerprint==null))
        require(!workout.resetToAuthored ||
            (workout.repApprovalFingerprint==null &&
                workout.loadApprovalFingerprint==null &&
                workout.futureWeekApprovalFingerprint==null &&
                workout.calibrationApprovalFingerprint==null &&
                workout.authoredReturnFingerprint==null))
        val session=NativeKotlinCustomWorkoutSnapshotCodec.encode(
            workout.snapshot)
        val payload=ByteArrayOutputStream()
        DataOutputStream(payload).use{out->
            out.writeInt(magic)
            out.writeInt(if(workout.snapshot.shortSessionLimitMinutes!=null)10 else
                if(workout.authoredReturnFingerprint!=null)9 else
                if(workout.resetToAuthored &&
                    workout.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE)8 else
                if(workout.calibrationApprovalFingerprint!=null)7 else
                if(workout.resetToAuthored)5 else
                if(workout.futureWeekApprovalFingerprint!=null)6 else
                if(workout.loadApprovalFingerprint!=null)4 else
                if(workout.repApprovalFingerprint!=null)3 else
                    if(workout.authority==
                        NativeCustomPreparationAuthority.CUSTOM_CYCLE) version else 2)
            out.writeUTF(workout.ownerKey)
            out.writeUTF(workout.athleteId)
            out.writeUTF(workout.workoutId)
            out.writeLong(workout.consentRevision)
            out.writeUTF(workout.consentFingerprint)
            if(workout.authority==NativeCustomPreparationAuthority.ACTIVATED_BLOCK ||
                workout.calibrationApprovalFingerprint!=null || workout.resetToAuthored ||
                workout.authoredReturnFingerprint!=null ||
                workout.snapshot.shortSessionLimitMinutes!=null)
                out.writeUTF(workout.authority.name)
            if(workout.repApprovalFingerprint!=null)
                out.writeUTF(workout.repApprovalFingerprint)
            if(workout.loadApprovalFingerprint!=null)
                out.writeUTF(workout.loadApprovalFingerprint)
            if(workout.futureWeekApprovalFingerprint!=null) {
                out.writeUTF(workout.futureWeekApprovalFingerprint)
                out.writeUTF(checkNotNull(workout.futureWeekSlotId))
            }
            if(workout.calibrationApprovalFingerprint!=null)
                out.writeUTF(workout.calibrationApprovalFingerprint)
            if(workout.authoredReturnFingerprint!=null)
                out.writeUTF(workout.authoredReturnFingerprint)
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
                if(schema !in 1..10)
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
                if(schema==5 &&
                    authority!=NativeCustomPreparationAuthority.ACTIVATED_BLOCK)
                    throw IOException("invalid manual-reset preparation authority")
                if(schema in 7..10 && authority!=NativeCustomPreparationAuthority.CUSTOM_CYCLE)
                    throw IOException("invalid ordinary calibration authority")
                val repProof=if(schema==3)data.readUTF() else null
                val loadProof=if(schema==4)data.readUTF() else null
                val futureProof=if(schema==6)data.readUTF() else null
                val futureSlot=if(schema==6)data.readUTF() else null
                val calibration=if(schema==7)data.readUTF() else null
                val authoredReturn=if(schema==9)data.readUTF() else null
                if(calibration!=null && !fingerprint.matches(calibration))
                    throw IOException("invalid calibration proof")
                if(authoredReturn!=null && !fingerprint.matches(authoredReturn))
                    throw IOException("invalid authored return proof")
                if((repProof!=null || loadProof!=null || futureProof!=null) &&
                    (authority!=NativeCustomPreparationAuthority.ACTIVATED_BLOCK ||
                        !fingerprint.matches(checkNotNull(
                            repProof ?: loadProof ?: futureProof)) ||
                        (futureSlot!=null && !id.matches(futureSlot))))
                    throw IOException("invalid amended-preparation proof")
                val sessionBytes=data.readInt()
                if(sessionBytes !in 16..NativeKotlinCustomWorkoutSnapshotCodec.MAX_BYTES ||
                    sessionBytes>data.available())
                    throw IOException("invalid prepared session size")
                val session=ByteArray(sessionBytes)
                data.readFully(session)
                if(data.available()!=0)
                    throw IOException("trailing prepared data")
                val snapshot=NativeKotlinCustomWorkoutSnapshotCodec.decode(session)
                if((schema==10)!=(snapshot.shortSessionLimitMinutes!=null))
                    throw IOException("time selection schema does not match approved snapshot")
                NativeCustomPreparedWorkout(owner,athlete,workoutId,
                    revision,receiptHash,snapshot,
                    authority,repProof,loadProof,schema==5||schema==8,
                    futureProof,futureSlot,calibration,authoredReturn).also{
                        encode(it)
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
        // A return choice or earned step can already equal the authored
        // targets. Explicit reset clears its stale proof, including with
        // coaching disabled, without changing a set.
        if(selected.authoredReturnFingerprint!=null ||
            selected.calibrationApprovalFingerprint!=null&&selected.snapshot==authored)
            return !selected.resetToAuthored && selected.snapshot==authored &&
                selected.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE
        if(selected.resetToAuthored ||
            (selected.repApprovalFingerprint==null &&
                selected.loadApprovalFingerprint==null &&
                selected.futureWeekApprovalFingerprint==null &&
                selected.calibrationApprovalFingerprint==null) ||
            (selected.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
                selected.calibrationApprovalFingerprint==null) ||
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
                val goodLoad=(selected.loadApprovalFingerprint!=null ||
                    selected.futureWeekApprovalFingerprint!=null ||
                    selected.calibrationApprovalFingerprint!=null) &&
                    sameExceptLoad && originalLoad!=null && actualLoad!=null &&
                    actualLoad.unit==originalLoad.unit &&
                    actualLoad.basis==originalLoad.basis &&
                    (if(selected.calibrationApprovalFingerprint!=null)
                        actualLoad.value<=originalLoad.value
                    else actualLoad.value>originalLoad.value)
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

    private fun resetPreviewFenced(
        ownerKey:String,workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,inputs:CustomCoachAuditInputs,
        signoffs:NativeKotlinActivatedCycleSignoffFileStore?,
        activation:NativeKotlinFutureActivationCommitTransaction?,
        issued:NativeKotlinCustomWorkoutJournalFileStore,
        settings:NativeKotlinCoachAssistanceSettingsStore
    ):NativePreparedManualResetPreview {
        fun no(r:NativePreparedManualResetRefusal)=
            NativePreparedManualResetPreview.Rejected(r)
        val athlete=current(ownerKey)?:return no(
            NativePreparedManualResetRefusal.NOT_AUTHORIZED)
        val read=openFenced(ownerKey,workoutId,athlete)
        if(read is NativeCustomPreparedRead.Rejected)
            return no(if(read.reason==NativeCustomPrepareRefusal.NOT_AUTHORIZED)
                NativePreparedManualResetRefusal.NOT_AUTHORIZED
                else NativePreparedManualResetRefusal.CORRUPT_RECORD)
        val saved=(read as? NativeCustomPreparedRead.Found)?.workout
            ?:return no(NativePreparedManualResetRefusal.NO_AMENDED_PREPARATION)
        if(saved.resetToAuthored ||
            saved.repApprovalFingerprint==null &&
                saved.loadApprovalFingerprint==null &&
                saved.futureWeekApprovalFingerprint==null &&
                saved.calibrationApprovalFingerprint==null &&
                saved.authoredReturnFingerprint==null)
            return no(NativePreparedManualResetRefusal.NO_AMENDED_PREPARATION)
        if(saved.snapshot.stamp.cycleId!=cycleId ||
            saved.snapshot.absoluteWeek!=absoluteWeek ||
            saved.snapshot.weekday!=weekday ||
            saved.ownerKey!=ownerKey || saved.athleteId!=athlete.athleteId)
            return no(NativePreparedManualResetRefusal.INVALID_SELECTION)
        when(val journal=issued.open(ownerKey,workoutId)) {
            is NativeCustomJournalResult.Durable ->
                return no(NativePreparedManualResetRefusal.ALREADY_ISSUED)
            is NativeCustomJournalResult.Rejected ->
                if(journal.reason!=NativeCustomJournalRefusal.NOT_ISSUED)
                    return no(if(journal.reason==
                            NativeCustomJournalRefusal.NOT_AUTHORIZED)
                        NativePreparedManualResetRefusal.NOT_AUTHORIZED
                        else NativePreparedManualResetRefusal.STORAGE_UNAVAILABLE)
        }
        val preferences=(settings.read(ownerKey)
            as? NativeCoachSettingsRead.Current)?.settings
            ?:return no(NativePreparedManualResetRefusal.SETTINGS_UNAVAILABLE)
        val original=if(saved.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE) {
            if((saved.calibrationApprovalFingerprint==null&&saved.authoredReturnFingerprint==null) ||
                activationHandoff?.blocksNewIssuance(ownerKey)==true)
                return no(NativePreparedManualResetRefusal.CONSENT_CHANGED)
            val pre=preflight.inspect(ownerKey,cycleId,absoluteWeek,weekday,inputs)
                as? NativeCustomPreflightResult.ReviewedPreview
                ?:return no(NativePreparedManualResetRefusal.CONSENT_CHANGED)
            if(pre.receipt.recordRevision!=saved.consentRevision ||
                pre.receipt.fingerprintSha256!=saved.consentFingerprint)
                return no(NativePreparedManualResetRefusal.CONSENT_CHANGED)
            pre.session
        } else {
        val consent=(signoffs?.read(ownerKey,inputs)
            as? NativeActivatedSignoffRead.Found)?.takeIf{it.isCurrent}
            ?.receipt ?:return no(NativePreparedManualResetRefusal.CONSENT_CHANGED)
        val active=(activation?.read(ownerKey)
            as? NativeFutureCommitReview.Applied)?.context
            ?:return no(NativePreparedManualResetRefusal.CONSENT_CHANGED)
        val activated=(activation?.previewActivatedWorkout(
            ownerKey,inputs,absoluteWeek,weekday)
            as? CustomWorkoutSnapshotResult.Preview)?.snapshot
            ?:return no(NativePreparedManualResetRefusal.CONSENT_CHANGED)
        if(consent.ownerKey!=ownerKey ||
            consent.athleteId!=athlete.athleteId ||
            consent.cycleId!=cycleId || consent.blockId!=active.blockId ||
            consent.sourceRevision!=active.sourceRevision ||
            consent.recordRevision!=saved.consentRevision ||
            consent.reviewFingerprint!=saved.consentFingerprint ||
            absoluteWeek<consent.firstWeek ||
            activated.stamp.athleteId!=athlete.athleteId ||
            activated.stamp.cycleId!=cycleId ||
            activated.stamp.gymCatalogSourceRevision!=active.sourceRevision ||
            activated.blockId!=consent.blockId ||
            activated.absoluteWeek!=absoluteWeek ||
            activated.weekday!=weekday)
            return no(NativePreparedManualResetRefusal.CONSENT_CHANGED)
        activated
        }
        if(!isResettableAmendment(saved,original))
            return no(NativePreparedManualResetRefusal.UNSAFE_AMENDMENT)
        if(current(ownerKey)!=athlete)
            return no(NativePreparedManualResetRefusal.NOT_AUTHORIZED)
        return NativePreparedManualResetPreview.Ready(
            saved,original,storedFingerprint(ownerKey,workoutId),
            preferences.revision)
    }

    /** Show the exact original targets BEFORE requesting reset acceptance. */
    fun previewManualReset(
        ownerKey:String,workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,inputs:CustomCoachAuditInputs,
        signoffs:NativeKotlinActivatedCycleSignoffFileStore?,
        activation:NativeKotlinFutureActivationCommitTransaction?,
        issued:NativeKotlinCustomWorkoutJournalFileStore,
        settings:NativeKotlinCoachAssistanceSettingsStore
    ):NativePreparedManualResetPreview {
        if(!listOf(ownerKey,workoutId,cycleId).all(id::matches) ||
            absoluteWeek !in 1..104 || weekday !in 0..6)
            return NativePreparedManualResetPreview.Rejected(
                NativePreparedManualResetRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            resetPreviewFenced(ownerKey,workoutId,cycleId,absoluteWeek,
                weekday,inputs,signoffs,activation,issued,settings)
        }}.getOrElse{NativePreparedManualResetPreview.Rejected(
            NativePreparedManualResetRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * This single-file, owner-fenced CAS transaction is never automatic:
     * exact old checksum, current preferences revision and explicit athlete
     * confirmation must still match at the durable replacement point.
     *
     * A reset-only schema v5 flag prevents a previously accepted progression
     * from silently reappearing when coaching is switched back ON later.
     */
    fun acceptManualReset(
        ownerKey:String,workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,inputs:CustomCoachAuditInputs,
        signoffs:NativeKotlinActivatedCycleSignoffFileStore?,
        activation:NativeKotlinFutureActivationCommitTransaction?,
        issued:NativeKotlinCustomWorkoutJournalFileStore,
        settings:NativeKotlinCoachAssistanceSettingsStore,
        expectedPreparedFingerprint:String,
        expectedSettingsRevision:Long,
        athleteConfirmed:Boolean
    ):NativePreparedManualResetWrite {
        fun no(r:NativePreparedManualResetRefusal)=
            NativePreparedManualResetWrite.Rejected(r)
        if(!athleteConfirmed)
            return no(NativePreparedManualResetRefusal.CONFIRMATION_REQUIRED)
        if(!fingerprint.matches(expectedPreparedFingerprint) ||
            expectedSettingsRevision<0)
            return no(NativePreparedManualResetRefusal.INVALID_SELECTION)
        if(!listOf(ownerKey,workoutId,cycleId).all(id::matches) ||
            absoluteWeek !in 1..104 || weekday !in 0..6)
            return no(NativePreparedManualResetRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val preview=resetPreviewFenced(ownerKey,workoutId,cycleId,
                absoluteWeek,weekday,inputs,signoffs,activation,issued,settings)
            if(preview !is NativePreparedManualResetPreview.Ready) {
                val denied=(preview as NativePreparedManualResetPreview.Rejected)
                return@withOwnerTransaction no(denied.reason)
            }
            if(preview.preparedFingerprint!=expectedPreparedFingerprint ||
                preview.settingsRevision!=expectedSettingsRevision)
                return@withOwnerTransaction no(
                    NativePreparedManualResetRefusal.STALE_PREVIEW)
            val athlete=current(ownerKey)
                ?:return@withOwnerTransaction no(
                    NativePreparedManualResetRefusal.NOT_AUTHORIZED)
            if(athlete.athleteId!=preview.original.athleteId)
                return@withOwnerTransaction no(
                    NativePreparedManualResetRefusal.NOT_AUTHORIZED)
            val baseline=preview.original.copy(
                snapshot=preview.authored,
                repApprovalFingerprint=null,
                loadApprovalFingerprint=null,
                futureWeekApprovalFingerprint=null,
                futureWeekSlotId=null,
                calibrationApprovalFingerprint=null,
                authoredReturnFingerprint=null,
                resetToAuthored=true)
            replacePreparedAtomically(ownerKey,workoutId,baseline)
            NativePreparedManualResetWrite.Reset(baseline)
        }}.getOrElse{no(NativePreparedManualResetRefusal.STORAGE_UNAVAILABLE)}
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
        coachingInputs:CustomCoachAuditInputs,
        calibration:NativeKotlinBlockCalibrationConsentStore?=null,
        expectedCalibrationFingerprint:String?=null
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
                val existing=openFenced(ownerKey,workoutId,owner)
                if(existing is NativeCustomPreparedRead.Rejected)
                    return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        existing.reason)
                val reset=(existing as? NativeCustomPreparedRead.Found)?.workout?.resetToAuthored==true
                val hasCalibration=NativeKotlinBlockCalibrationConsentStore.hasRecord(
                    directory,ownerKey,workoutId)
                val accepted=if((hasCalibration||expectedCalibrationFingerprint!=null ||
                        (existing as? NativeCustomPreparedRead.Found)?.workout?.calibrationApprovalFingerprint!=null)&&!reset) {
                    (calibration?.readCurrent(ownerKey,workoutId,cycleId,absoluteWeek,
                        weekday,coachingInputs) as? NativeBlockCalibrationRead.Current)?.review
                        ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                            NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
                } else null
                if(accepted!=null && (accepted.original!=pre.session ||
                    accepted.receipt.consentRevision!=pre.receipt.recordRevision ||
                    (expectedCalibrationFingerprint!=null &&
                        accepted.receipt.fingerprint!=expectedCalibrationFingerprint)))
                    return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
                val returnProof=(existing as? NativeCustomPreparedRead.Found)?.workout?.authoredReturnFingerprint
                if(returnProof!=null) {
                    val recheck=calibration?.previewAuthoredReturn(ownerKey,workoutId,
                        cycleId,coachingInputs,absoluteWeek,weekday)
                        as? NativeAuthoredLoadReturnPreview.Ready
                    if(recheck?.fingerprint!=returnProof||recheck.authored!=pre.session||accepted!=null)
                        return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                            NativeCustomPrepareRefusal.REVIEW_CHANGED)
                }
                if(accepted==null && returnProof==null && !reset && calibration!=null) {
                    when(calibration.authoredPreparationGate(ownerKey,cycleId,
                        coachingInputs,pre.session)) {
                        NativeAuthoredLoadPreparationGate.CLEAR -> Unit
                        NativeAuthoredLoadPreparationGate.LOWER_ISSUED_LOAD_NEEDS_REVIEW ->
                            return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                                NativeCustomPrepareRefusal.AUTHORED_LOAD_REVERSION_REVIEW_REQUIRED)
                        NativeAuthoredLoadPreparationGate.UNVERIFIED_HISTORY ->
                            return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                                NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
                    }
                }
                val planned=NativeCustomPreparedWorkout(ownerKey,
                    owner.athleteId,workoutId,pre.receipt.recordRevision,
                    pre.receipt.fingerprintSha256,accepted?.projected ?:pre.session,
                    resetToAuthored=reset,
                    calibrationApprovalFingerprint=accepted?.receipt?.fingerprint,
                    authoredReturnFingerprint=returnProof)
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
     * Explicit athlete-confirmed short workout preparation. The optional
     * cuts are recomputed from CURRENT coach-reviewed, owner-fenced custom
     * consent under the same OS lock. Unlike a UI preview, this persists
     * versioned and self-contained approved targets. Still not an issuance
     * grant, and impossible/unknown amplifier time is always refused.
     *
     * No automatic combination with calibrated future weights, individual
     * rep amendments, or previous already-prepared workout identities.
     */
    fun prepareShorter(
        ownerKey:String,workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,
        coachingInputs:CustomCoachAuditInputs,
        shown:NativeCustomShortSessionPreflight.Reviewed,
        athleteConfirmed:Boolean,
        calibration:NativeKotlinBlockCalibrationConsentStore?=null
    ):NativeCustomPrepareResult {
        val minutes=shown.review.requestedMinutes
        if(!id.matches(workoutId) || !id.matches(cycleId) ||
            absoluteWeek !in 1..104 || weekday !in 0..6 ||
            shown.absoluteWeek!=absoluteWeek || shown.weekday!=weekday ||
            shown.review.source?.cycleId!=cycleId ||
            shown.review.status !in setOf(
                CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW,
                CustomShortSessionStatus.ALREADY_FITS) ||
            minutes !in setOf(20,30,45))
            return NativeCustomPrepareResult.Rejected(
                NativeCustomPrepareRefusal.INVALID_SELECTION)
        if(!athleteConfirmed)return NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.CONFIRMATION_REQUIRED)
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
                    as? NativeCustomPreflightResult.ReviewedPreview
                    ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
                // A fresh valid workout is NOT necessarily the same
                // workout the athlete reviewed. Fence the exact signed
                // consent revision AND complete selected/omitted set list.
                // Any gym, cycle, week, timing or policy change must require
                // a new explicit Preview -> Confirm step.
                if(pre.receipt!=shown.receipt ||
                    pre.session.stamp!=shown.review.source ||
                    CustomShorterSessionReviewDomain.inspect(
                        pre.session,minutes)!=shown.review)
                    return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.REVIEW_CHANGED)
                val snapshot=CustomShorterSessionReviewDomain.project(
                    pre.session,minutes)
                    ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
                val existing=openFenced(ownerKey,workoutId,owner)
                if(existing is NativeCustomPreparedRead.Rejected)
                    return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        existing.reason)
                if(calibration!=null) {
                    // Shorter sessions cannot bypass the personalized
                    // last-same-day load regression review.
                    when(calibration.authoredPreparationGate(ownerKey,cycleId,
                        coachingInputs,pre.session)) {
                        NativeAuthoredLoadPreparationGate.CLEAR -> Unit
                        NativeAuthoredLoadPreparationGate.LOWER_ISSUED_LOAD_NEEDS_REVIEW ->
                            return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                                NativeCustomPrepareRefusal.AUTHORED_LOAD_REVERSION_REVIEW_REQUIRED)
                        NativeAuthoredLoadPreparationGate.UNVERIFIED_HISTORY ->
                            return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                                NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
                    }
                }
                val candidate=NativeCustomPreparedWorkout(
                    ownerKey,owner.athleteId,workoutId,
                    pre.receipt.recordRevision,pre.receipt.fingerprintSha256,
                    snapshot)
                if(existing is NativeCustomPreparedRead.Found)
                    return@withOwnerTransaction if(existing.workout==candidate)
                        NativeCustomPrepareResult.Prepared(existing.workout,true)
                    else NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED)
                if(current(ownerKey)!=owner)
                    return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.NOT_AUTHORIZED)
                atomicWrite(path(ownerKey,workoutId),encode(candidate))
                NativeCustomPrepareResult.Prepared(candidate,false)
            }
        }.getOrElse {NativeCustomPrepareResult.Rejected(
            NativeCustomPrepareRefusal.STORAGE_UNAVAILABLE)}
    }

    /** Accept exact displayed targets and persist their consent in ONE atomic preparation. */
    fun prepareAuthoredReturn(shown:NativeAuthoredLoadReturnPreview.Ready,
        inputs:CustomCoachAuditInputs,athleteConfirmed:Boolean,
        calibration:NativeKotlinBlockCalibrationConsentStore
    ):NativeCustomPrepareResult {
        fun no(r:NativeCustomPrepareRefusal)=NativeCustomPrepareResult.Rejected(r)
        if(!athleteConfirmed)return no(NativeCustomPrepareRefusal.CONFIRMATION_REQUIRED)
        if(!id.matches(shown.workoutId)||!fingerprint.matches(shown.fingerprint))
            return no(NativeCustomPrepareRefusal.INVALID_SELECTION)
        return runCatching{fence.withOwnerTransaction(shown.ownerKey){
            val owner=current(shown.ownerKey)
                ?:return@withOwnerTransaction no(NativeCustomPrepareRefusal.NOT_AUTHORIZED)
            if(owner.athleteId!=shown.authored.stamp.athleteId)
                return@withOwnerTransaction no(NativeCustomPrepareRefusal.NOT_AUTHORIZED)
            if(activationHandoff?.blocksNewIssuance(shown.ownerKey)==true)
                return@withOwnerTransaction no(NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val next=shown.authored
            val fresh=calibration.previewAuthoredReturn(shown.ownerKey,shown.workoutId,
                next.stamp.cycleId,inputs,next.absoluteWeek,next.weekday)
                as? NativeAuthoredLoadReturnPreview.Ready
            if(fresh!=shown)return@withOwnerTransaction no(NativeCustomPrepareRefusal.REVIEW_CHANGED)
            val pre=preflight.inspect(shown.ownerKey,next.stamp.cycleId,
                next.absoluteWeek,next.weekday,inputs) as? NativeCustomPreflightResult.ReviewedPreview
            if(pre?.session!=next||pre.receipt.recordRevision!=shown.consentRevision||
                pre.receipt.fingerprintSha256!=shown.consentFingerprint)
                return@withOwnerTransaction no(NativeCustomPrepareRefusal.REVIEW_CHANGED)
            val existing=openFenced(shown.ownerKey,shown.workoutId,owner)
            if(existing is NativeCustomPreparedRead.Rejected)
                return@withOwnerTransaction no(existing.reason)
            val planned=NativeCustomPreparedWorkout(shown.ownerKey,owner.athleteId,
                shown.workoutId,shown.consentRevision,shown.consentFingerprint,next,
                authoredReturnFingerprint=shown.fingerprint)
            if(current(shown.ownerKey)!=owner)
                return@withOwnerTransaction no(NativeCustomPrepareRefusal.NOT_AUTHORIZED)
            if(existing is NativeCustomPreparedRead.Found) {
                if(existing.workout==planned)
                    return@withOwnerTransaction NativeCustomPrepareResult.Prepared(existing.workout,true)
                // Add or explicitly renew consent on the same preparation;
                // targets and every other provenance field remain exact.
                if(existing.workout.copy(authoredReturnFingerprint=null)!=
                    planned.copy(authoredReturnFingerprint=null))
                    return@withOwnerTransaction no(NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED)
                replacePreparedAtomically(shown.ownerKey,shown.workoutId,planned)
            } else atomicWrite(path(shown.ownerKey,shown.workoutId),encode(planned))
            NativeCustomPrepareResult.Prepared(planned,false)
        }}.getOrElse{no(NativeCustomPrepareRefusal.STORAGE_UNAVAILABLE)}
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
        loadApprovals:NativeKotlinCustomNextLoadApprovalFileStore?=null,
        futureWeek:NativeKotlinAcceptedFutureWeekLoadHandoff?=null,
        futureWeekSlotId:String?=null
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
            val futureOffer=if(futureWeekSlotId!=null)
                futureWeek?.preview(ownerKey,cycleId,consent.blockId,
                    coachingInputs,absoluteWeek,weekday,futureWeekSlotId)
                    as? NativeAcceptedFutureWeekLoadHandoff.Ready
                    ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                        NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            else null
            if(futureOffer!=null &&
                (futureOffer.original!=session ||
                    futureOffer.slotId!=futureWeekSlotId))
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
                (amended!=null && loadRead is NativeCustomLoadRead.Current) ||
                (futureOffer!=null &&
                    (amended!=null||loadRead is NativeCustomLoadRead.Current)))
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val load=loadRead as? NativeCustomLoadRead.Current
            val planned=NativeCustomPreparedWorkout(ownerKey,owner.athleteId,
                workoutId,consent.recordRevision,consent.reviewFingerprint,
                futureOffer?.projected ?:load?.amendment?.amended ?:
                    amended?.amendment?.amended ?:session,
                NativeCustomPreparationAuthority.ACTIVATED_BLOCK,
                amended?.receipt?.fingerprint,load?.receipt?.fingerprint,
                futureWeekApprovalFingerprint=futureOffer?.fingerprint,
                futureWeekSlotId=futureOffer?.slotId)
            val existing=openFenced(ownerKey,workoutId,owner)
            if(existing is NativeCustomPreparedRead.Rejected)
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(
                    existing.reason)
            if(existing is NativeCustomPreparedRead.Found) {
                val stored=existing.workout
                // A version-5 athlete reset is intentionally sticky: an old
                // still-accepted suggestion must never override it on an
                // idempotent re-entry to the same future workout.
                val acceptedReset=stored.resetToAuthored &&
                    stored.authority==
                        NativeCustomPreparationAuthority.ACTIVATED_BLOCK &&
                    stored.repApprovalFingerprint==null &&
                    stored.loadApprovalFingerprint==null &&
                    stored.futureWeekApprovalFingerprint==null &&
                    stored.snapshot==session &&
                    stored.consentRevision==consent.recordRevision &&
                    stored.consentFingerprint==consent.reviewFingerprint &&
                    stored.ownerKey==ownerKey &&
                    stored.athleteId==owner.athleteId
                return@withOwnerTransaction if(stored==planned || acceptedReset)
                    NativeCustomPrepareResult.Prepared(stored,true)
                else NativeCustomPrepareResult.Rejected(
                    NativeCustomPrepareRefusal.DIFFERENT_WORKOUT_ALREADY_PREPARED)
            }
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
