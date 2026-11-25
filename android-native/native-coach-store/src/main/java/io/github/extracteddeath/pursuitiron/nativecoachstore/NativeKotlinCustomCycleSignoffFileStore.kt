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
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Recovery-only facts for every physical signed or revoked program consent.
 * A historical receipt is not an active signoff or a workout issuance grant. */
internal data class NativeCustomSignoffInventoryFact(
    val cycleId:String,
    val revision:Long,
    val revoked:Boolean,
    val hasReviewedInputs:Boolean
)
internal sealed interface NativeCustomSignoffInventory {
    data class Verified(val records:List<NativeCustomSignoffInventoryFact>):
        NativeCustomSignoffInventory
    data object InvalidOrForeign:NativeCustomSignoffInventory
}

private data class SignoffFileRecord(
    val ownerKey:String,
    val athleteId:String,
    val cycleId:String,
    val revision:Long,
    val consent:NativeKotlinCustomCycleSignoff?,
    val reviewedInputs:CustomCoachAuditInputs?=null
)

/** A recovered review is revalidated; it never grants new consent or a workout. */
sealed interface NativeCustomSavedCoachReview {
    data class Ready(val inputs:CustomCoachAuditInputs,
        val receipt:NativeKotlinCustomCycleSignoff):NativeCustomSavedCoachReview
    data object Missing:NativeCustomSavedCoachReview
    data object LegacyWithoutInputs:NativeCustomSavedCoachReview
    data class Rejected(val reason:NativeCustomSignoffRefusal):NativeCustomSavedCoachReview
}

/**
 * Revision-CAS athlete consent for EXACT custom-cycle content. A consent is
 * not the composer's executable program grant: the workout issuer must use
 * a separate custom snapshot pipeline preserving individual authored sets
 * and amplifier semantics. No call here can issue a session or write history.
 *
 * All checks and writes use the SAME OS owner lock as native gym, draft and
 * workout authorities. Every re-open compares fingerprints, not status flags.
 */
class NativeKotlinCustomCycleSignoffFileStore(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val sha=Regex("^[a-f0-9]{64}$")
    private val magic=0x50494341 // PICA
    private val version=2
    private val maxFileSize=NativeCustomCoachAuditInputsCodec.MAX_BYTES+8192
    private val maximumRevision=9_007_199_254_740_991L

    private fun current(owner:String):NativeKotlinOwnerIdentity? {
        if(!id.matches(owner))return null
        return runCatching{identity.signedInIdentity()}.getOrNull()
            ?.takeIf {it.ownerKey==owner && id.matches(it.athleteId)}
    }
    private fun path(owner:String,cycle:String):Path {
        val key=MessageDigest.getInstance("SHA-256")
            .digest("$owner/$cycle".toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return directory.resolve("custom-consent-$key.bin")
    }
    private fun encode(item:SignoffFileRecord):ByteArray {
        val output=ByteArrayOutputStream()
        DataOutputStream(output).use {out->
            out.writeInt(magic);out.writeInt(version)
            out.writeUTF(item.ownerKey);out.writeUTF(item.athleteId)
            out.writeUTF(item.cycleId);out.writeLong(item.revision)
            out.writeBoolean(item.consent!=null)
            item.consent?.let{receipt->
                require(receipt.ownerKey==item.ownerKey &&
                    receipt.athleteId==item.athleteId &&
                    receipt.cycleId==item.cycleId &&
                    receipt.recordRevision==item.revision &&
                    receipt.cycleRevision>0 && receipt.sourceRevision>0 &&
                    sha.matches(receipt.fingerprintSha256) &&
                    receipt.policyReferences.size<=TrainingPhase.entries.size)
                out.writeLong(receipt.cycleRevision)
                out.writeLong(receipt.sourceRevision)
                out.writeUTF(receipt.fingerprintSha256)
                out.writeInt(receipt.policyReferences.size)
                receipt.policyReferences.toSortedMap(compareBy{it.name})
                    .forEach{(phase,revision)->
                        out.writeUTF(phase.name);out.writeUTF(revision)
                    }
            }
            require(item.consent!=null || item.reviewedInputs==null)
            out.writeBoolean(item.reviewedInputs!=null)
            item.reviewedInputs?.let {
                val saved=NativeCustomCoachAuditInputsCodec.encode(it)
                out.writeInt(saved.size);out.write(saved)
            }
        }
        val payload=output.toByteArray()
        require(payload.size+36<=maxFileSize)
        val checksum=MessageDigest.getInstance("SHA-256").digest(payload)
        return ByteArrayOutputStream().also{bytes->
            DataOutputStream(bytes).use{out->
                out.writeInt(payload.size);out.write(payload);out.write(checksum)
            }
        }.toByteArray()
    }
    private fun decode(file:Path):SignoffFileRecord? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file) ||
            Files.size(file) !in 44L..maxFileSize.toLong())
            throw IOException("Invalid custom consent file")
        val content=Files.readAllBytes(file)
        val header=DataInputStream(ByteArrayInputStream(content))
        val length=header.readInt()
        if(length !in 8..(maxFileSize-36) || length+36!=content.size)
            throw IOException("Invalid custom consent envelope")
        val payload=ByteArray(length);header.readFully(payload)
        val expected=ByteArray(32);header.readFully(expected)
        if(!MessageDigest.isEqual(expected,
            MessageDigest.getInstance("SHA-256").digest(payload)))
            throw IOException("Custom consent checksum mismatch")
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use{input->
                if(input.readInt()!=magic)
                    throw IOException("Unknown custom consent version")
                val storedVersion=input.readInt()
                if(storedVersion !in 1..version)
                    throw IOException("Unknown custom consent version")
                val owner=input.readUTF();val athlete=input.readUTF()
                val cycle=input.readUTF();val revision=input.readLong()
                if(!id.matches(owner)||!id.matches(athlete)||!id.matches(cycle)||
                    revision !in 1..maximumRevision)
                    throw IOException("Invalid custom consent identity")
                val receipt=if(input.readBoolean()) {
                    val cycleRev=input.readLong()
                    val sourceRev=input.readLong()
                    val fingerprint=input.readUTF()
                    val count=input.readInt()
                    if(count !in 0..TrainingPhase.entries.size)
                        throw IOException("Invalid policy reference count")
                    val policies=buildMap {
                        repeat(count){
                            val phaseName=input.readUTF()
                            val phase=TrainingPhase.entries.firstOrNull {
                                it.name==phaseName
                            } ?:throw IOException("Invalid policy phase")
                            val label=input.readUTF()
                            if(label.isBlank() || label.length>80 ||
                                put(phase,label)!=null)
                                throw IOException("Invalid policy reference")
                        }
                    }
                    NativeKotlinCustomCycleSignoff(owner,athlete,cycle,
                        cycleRev,sourceRev,revision,fingerprint,policies).also {
                        if(cycleRev !in 1..maximumRevision ||
                            sourceRev !in 1..maximumRevision ||
                            !sha.matches(fingerprint))
                            throw IOException("Invalid consent receipt")
                    }
                } else null
                val inputs=if(storedVersion>=2 && input.readBoolean()) {
                    val size=input.readInt()
                    if(receipt==null || size !in 17..NativeCustomCoachAuditInputsCodec.MAX_BYTES ||
                        size>input.available())throw IOException("Invalid saved review inputs")
                    val raw=ByteArray(size);input.readFully(raw)
                    NativeCustomCoachAuditInputsCodec.decode(raw)
                } else null
                if(input.available()!=0)throw IOException("Trailing consent bytes")
                SignoffFileRecord(owner,athlete,cycle,revision,receipt,inputs)
            }
        } catch(ex:Exception){
            throw IOException("Corrupt custom consent data",ex)
        }
    }
    private fun atomicWrite(file:Path,bytes:ByteArray) {
        Files.createDirectories(directory)
        if(Files.isSymbolicLink(directory))
            throw IOException("Unsafe owner file directory")
        val pending=Files.createTempFile(directory,"custom-consent-", ".pending")
        try {
            FileChannel.open(pending,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try {
                Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            }catch(ex:AtomicMoveNotSupportedException){
                throw IOException("Atomic consent persistence not supported",ex)
            }
        }finally {
            Files.deleteIfExists(pending)
        }
    }
    private fun verified(
        ownerKey:String,
        cycleId:String,
        expectedCycleRevision:Long,
        expectedSourceRevision:Long,
        inputs:CustomCoachAuditInputs
    ):Pair<NativeKotlinCustomCycleSignoff?,NativeCustomSignoffRefusal?> {
        val owner=current(ownerKey)
            ?:return null to NativeCustomSignoffRefusal.NOT_AUTHORIZED
        val draft=(drafts.read(ownerKey,cycleId)
            as? NativeCustomDraftReadResult.Found)?.draft
            ?:return null to NativeCustomSignoffRefusal.PROGRAM_MISSING
        if(draft.revision!=expectedCycleRevision ||
            draft.athleteId!=owner.athleteId)
            return null to NativeCustomSignoffRefusal.STALE_PROGRAM
        val source=(sources.read(ownerKey)
            as? NativeAuthoringSourceRead.Found)?.source
            ?:return null to NativeCustomSignoffRefusal.SOURCE_MISSING
        if(source.revision!=expectedSourceRevision ||
            source.athleteId!=owner.athleteId)
            return null to NativeCustomSignoffRefusal.STALE_SOURCE
        val audit=CustomCycleCoachAuditDomain.review(
            draft,source.setup,source.catalog,inputs)
        if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
            audit.issues.isNotEmpty() || audit.auditedCycleRevision!=draft.revision)
            return null to NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED
        val fingerprint=NativeCustomCycleSignoffFingerprint.calculate(
            draft,source,inputs)
        return NativeKotlinCustomCycleSignoff(
            ownerKey,owner.athleteId,cycleId,draft.revision,source.revision,
            0,fingerprint,audit.coachingPolicyRevisions
        ) to null
    }
    /**
     * Explicit confirmation is only one gate: the full coach review is run
     * AGAIN inside the lock, before the disk CAS. No cached screen badge or
     * caller-provided "approved" flag can substitute for it.
     */
    fun confirm(
        ownerKey:String,cycleId:String,
        expectedCycleRevision:Long,expectedSourceRevision:Long,
        expectedRecordRevision:Long,
        inputs:CustomCoachAuditInputs,
        athleteConfirmed:Boolean
    ):NativeCustomSignoffWrite {
        if(!athleteConfirmed)
            return NativeCustomSignoffWrite.Rejected(
                NativeCustomSignoffRefusal.CONFIRMATION_REQUIRED)
        if(!id.matches(ownerKey) || !id.matches(cycleId) ||
            expectedRecordRevision !in 0 until maximumRevision)
            return NativeCustomSignoffWrite.Rejected(
                NativeCustomSignoffRefusal.STALE_CONSENT)
        return try {
            fence.withOwnerTransaction(ownerKey){
                val owner=current(ownerKey)
                    ?:return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.NOT_AUTHORIZED)
                val file=path(ownerKey,cycleId)
                val previous=try{decode(file)}catch(_:Exception){
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.CORRUPT_RECORD)
                }
                if(previous!=null &&
                    (previous.ownerKey!=ownerKey ||
                    previous.athleteId!=owner.athleteId ||
                    previous.cycleId!=cycleId))
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.NOT_AUTHORIZED)
                if((previous?.revision?:0L)!=expectedRecordRevision)
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.STALE_CONSENT)
                // Snapshot mutable caller collections before either audit or
                // fingerprint, and persist that SAME complete reviewed recipe.
                val savedInputs=try {NativeCustomCoachAuditInputsCodec.decode(
                    NativeCustomCoachAuditInputsCodec.encode(inputs))}
                catch(_:Exception){
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED)
                }
                val (candidate,refusal)=verified(ownerKey,cycleId,
                    expectedCycleRevision,expectedSourceRevision,savedInputs)
                if(candidate==null)
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        refusal ?: NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED)
                if(current(ownerKey)!=owner)
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.NOT_AUTHORIZED)
                val approved=candidate.copy(recordRevision=expectedRecordRevision+1)
                atomicWrite(file,encode(SignoffFileRecord(
                    ownerKey,owner.athleteId,cycleId,approved.recordRevision,
                    approved,savedInputs)))
                NativeCustomSignoffWrite.Committed(approved)
            }
        }catch(_:Exception){
            NativeCustomSignoffWrite.Rejected(
                NativeCustomSignoffRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Explicit final consent to a previously confirmed specialist draft.
     * Uses the SAME owner lock, CAS record, and atomic persistence as ordinary
     * consent. Reopens all authoritative sources, never accepts a UI-provided
     * stock or audit verdict. The dedicated named preflight must separately
     * revalidate this receipt before any session may issue.
     */
    fun confirmNamed(
        ownerKey:String,cycleId:String,displayed:NativeSavedNamedReview.Ready,
        athleteConfirmed:Boolean,
        history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory,
        machineRosters:NativeKotlinMachineRosterFileStore?=null
    ):NativeCustomSignoffWrite =
        confirmSpecialist(ownerKey,cycleId,displayed,athleteConfirmed,history,outstanding,machineRosters,false)

    fun confirmTieredInitial(
        ownerKey:String,cycleId:String,displayed:NativeSavedNamedReview.Ready,
        athleteConfirmed:Boolean,history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory,
        machineRosters:NativeKotlinMachineRosterFileStore?=null
    ):NativeCustomSignoffWrite =
        confirmSpecialist(ownerKey,cycleId,displayed,athleteConfirmed,history,outstanding,machineRosters,true)

    private fun confirmSpecialist(
        ownerKey:String,cycleId:String,displayed:NativeSavedNamedReview.Ready,
        athleteConfirmed:Boolean,history:NativeKotlinMixedHistoryReadCoordinator,
        outstanding:NativeKotlinCustomOutstandingWorkoutInventory,
        machineRosters:NativeKotlinMachineRosterFileStore?,tiered:Boolean
    ):NativeCustomSignoffWrite {
        fun deny(reason:NativeCustomSignoffRefusal)=NativeCustomSignoffWrite.Rejected(reason)
        if(!athleteConfirmed)return deny(NativeCustomSignoffRefusal.CONFIRMATION_REQUIRED)
        if(!id.matches(ownerKey)||!id.matches(cycleId))return deny(NativeCustomSignoffRefusal.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=current(ownerKey)
                ?:return@withOwnerTransaction deny(NativeCustomSignoffRefusal.NOT_AUTHORIZED)
            val file=path(ownerKey,cycleId)
            val previous=try{decode(file)}catch(_:Exception){
                return@withOwnerTransaction deny(NativeCustomSignoffRefusal.CORRUPT_RECORD)
            }?:return@withOwnerTransaction deny(NativeCustomSignoffRefusal.STALE_CONSENT)
            if(previous.ownerKey!=ownerKey || previous.athleteId!=owner.athleteId ||
                previous.cycleId!=cycleId)
                return@withOwnerTransaction deny(NativeCustomSignoffRefusal.NOT_AUTHORIZED)
            if(previous.consent!=displayed.priorConsent ||
                previous.revision !in 1 until maximumRevision)
                return@withOwnerTransaction deny(NativeCustomSignoffRefusal.STALE_CONSENT)
            val savedInputs=try{NativeCustomCoachAuditInputsCodec.decode(
                NativeCustomCoachAuditInputsCodec.encode(displayed.inputs))}
            catch(_:Exception){
                return@withOwnerTransaction deny(NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED)
            }
            val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
                this,history,outstanding,machineRosters)
            val fresh=(if(tiered)reviewer.reviewTieredInitial(ownerKey,cycleId,savedInputs)
                else reviewer.review(ownerKey,cycleId,savedInputs)) as? NativeSavedNamedReview.Ready
                ?:return@withOwnerTransaction deny(NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED)
            if(fresh!=displayed || fresh.priorConsent!=previous.consent)
                return@withOwnerTransaction deny(NativeCustomSignoffRefusal.STALE_CONSENT)
            val source=(sources.read(ownerKey) as? NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction deny(NativeCustomSignoffRefusal.SOURCE_MISSING)
            if(source.revision!=fresh.stamp.gymCatalogSourceRevision ||
                source.athleteId!=owner.athleteId || current(ownerKey)!=owner)
                return@withOwnerTransaction deny(NativeCustomSignoffRefusal.STALE_SOURCE)
            val hash=if(tiered)NativeCustomCycleSignoffFingerprint.calculateTieredInitial(
                fresh.draft,source,savedInputs,checkNotNull(fresh.initialTierReceipt))
            else NativeCustomCycleSignoffFingerprint.calculateNamed(fresh.draft,source,savedInputs,fresh.receipts)
            val approved=NativeKotlinCustomCycleSignoff(ownerKey,owner.athleteId,cycleId,
                fresh.draft.revision,source.revision,previous.revision+1,hash,
                fresh.audit.coachingPolicyRevisions)
            atomicWrite(file,encode(SignoffFileRecord(ownerKey,owner.athleteId,cycleId,
                approved.recordRevision,approved,savedInputs)))
            NativeCustomSignoffWrite.Committed(approved)
        }}.getOrElse{deny(NativeCustomSignoffRefusal.STORAGE_UNAVAILABLE)}
    }

    /** Cold recovery requires the original unrevoked consent, exact current
     * cycle/source, fresh full-cycle audit AND unchanged fingerprint. A valid
     * checksum alone cannot turn altered review inputs into approved rules. */
    fun recoverReviewedInputs(ownerKey:String,cycleId:String):NativeCustomSavedCoachReview {
        if(current(ownerKey)==null || !id.matches(cycleId))
            return NativeCustomSavedCoachReview.Rejected(NativeCustomSignoffRefusal.NOT_AUTHORIZED)
        return runCatching {fence.withOwnerTransaction(ownerKey) {
            val owner=current(ownerKey) ?:return@withOwnerTransaction NativeCustomSavedCoachReview.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
            val stored=try{decode(path(ownerKey,cycleId))}catch(_:Exception){
                return@withOwnerTransaction NativeCustomSavedCoachReview.Rejected(
                    NativeCustomSignoffRefusal.CORRUPT_RECORD)
            } ?:return@withOwnerTransaction NativeCustomSavedCoachReview.Missing
            if(stored.ownerKey!=ownerKey || stored.athleteId!=owner.athleteId ||
                stored.cycleId!=cycleId)return@withOwnerTransaction NativeCustomSavedCoachReview.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
            val receipt=stored.consent ?:return@withOwnerTransaction NativeCustomSavedCoachReview.Rejected(
                NativeCustomSignoffRefusal.STALE_CONSENT)
            val inputs=stored.reviewedInputs ?:return@withOwnerTransaction NativeCustomSavedCoachReview.LegacyWithoutInputs
            val (checked,refusal)=verified(ownerKey,cycleId,
                receipt.cycleRevision,receipt.sourceRevision,inputs)
            if(checked==null)return@withOwnerTransaction NativeCustomSavedCoachReview.Rejected(
                refusal ?:NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED)
            if(checked.fingerprintSha256!=receipt.fingerprintSha256 ||
                checked.policyReferences!=receipt.policyReferences)
                return@withOwnerTransaction NativeCustomSavedCoachReview.Rejected(
                    NativeCustomSignoffRefusal.STALE_CONSENT)
            if(current(ownerKey)!=owner)return@withOwnerTransaction NativeCustomSavedCoachReview.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
            NativeCustomSavedCoachReview.Ready(inputs,receipt)
        }}.getOrElse {NativeCustomSavedCoachReview.Rejected(
            NativeCustomSignoffRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Recovery-only full physical inventory: include revoked and retired
     * cycle receipts without reviving their authority. Ordinary read(cycle)
     * rightly ignores unrelated records; a complete backup audit cannot.
     * Check every filename, envelope, checksum, embedded identity, receipt
     * revision and original owner-scoped hashed path under the owner fence.
     *
     * Interruptions, symbolic links, foreign records and renamed duplicates
     * fail closed rather than disappearing from a restored athlete's data.
     * No draft, source, policy audit, consent or workout is changed here.
     */
    internal fun inventoryOwner(ownerKey:String):NativeCustomSignoffInventory =
        runCatching {fence.withOwnerTransaction(ownerKey) {
            val signed=current(ownerKey)
                ?:return@withOwnerTransaction NativeCustomSignoffInventory.InvalidOrForeign
            if(!Files.exists(directory,LinkOption.NOFOLLOW_LINKS))
                return@withOwnerTransaction NativeCustomSignoffInventory.Verified(emptyList())
            require(Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(directory))
            val physical=Files.list(directory).use {stream->
                stream.filter{it.fileName.toString().startsWith("custom-consent-")}
                    .limit(4097).toList()
            }
            require(physical.size<=4096)
            val validName=Regex("^custom-consent-[a-f0-9]{64}\\.bin$")
            val seen=mutableSetOf<String>()
            val records=physical.sortedBy{it.fileName.toString()}.map{file->
                require(validName.matches(file.fileName.toString()) &&
                    !Files.isSymbolicLink(file) &&
                    Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS) &&
                    Files.size(file) in 44L..maxFileSize.toLong())
                val stored=decode(file) ?:error("Missing physical signoff")
                val consent=stored.consent
                require(stored.ownerKey==ownerKey &&
                    stored.athleteId==signed.athleteId &&
                    path(ownerKey,stored.cycleId)==file &&
                    seen.add(stored.cycleId) &&
                    (consent==null || (
                        consent.ownerKey==ownerKey &&
                        consent.athleteId==signed.athleteId &&
                        consent.cycleId==stored.cycleId &&
                        consent.recordRevision==stored.revision
                    )) &&
                    (consent!=null || stored.reviewedInputs==null))
                NativeCustomSignoffInventoryFact(
                    stored.cycleId,stored.revision,
                    revoked=consent==null,
                    hasReviewedInputs=stored.reviewedInputs!=null)
            }
            require(current(ownerKey)==signed)
            NativeCustomSignoffInventory.Verified(records)
        }}.getOrElse{NativeCustomSignoffInventory.InvalidOrForeign}

    /** An old receipt can be VIEWED, but is current only after recomputing
     * audit and checking all content, including coaching inputs. */
    fun read(
        ownerKey:String,cycleId:String,
        currentInputs:CustomCoachAuditInputs?=null
    ):NativeCustomSignoffRead {
        if(current(ownerKey)==null || !id.matches(cycleId))
            return NativeCustomSignoffRead.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
        return try {
            fence.withOwnerTransaction(ownerKey){
                val owner=current(ownerKey)
                    ?:return@withOwnerTransaction NativeCustomSignoffRead.Rejected(
                        NativeCustomSignoffRefusal.NOT_AUTHORIZED)
                val previous=try{decode(path(ownerKey,cycleId))}catch(_:Exception){
                    return@withOwnerTransaction NativeCustomSignoffRead.Rejected(
                        NativeCustomSignoffRefusal.CORRUPT_RECORD)
                }
                when {
                    previous==null -> NativeCustomSignoffRead.Missing
                    previous.ownerKey!=ownerKey ||
                        previous.athleteId!=owner.athleteId ||
                        previous.cycleId!=cycleId ->
                        NativeCustomSignoffRead.Rejected(
                            NativeCustomSignoffRefusal.NOT_AUTHORIZED)
                    previous.consent==null ->
                        NativeCustomSignoffRead.Revoked(previous.revision)
                    else -> {
                        val receipt=previous.consent
                        val validated=if(currentInputs!=null)
                            verified(ownerKey,cycleId,receipt.cycleRevision,
                                receipt.sourceRevision,currentInputs).first
                        else null
                        NativeCustomSignoffRead.Found(receipt,
                            validated!=null &&
                                MessageDigest.isEqual(
                                    validated.fingerprintSha256.toByteArray(),
                                    receipt.fingerprintSha256.toByteArray()) &&
                                validated.policyReferences==receipt.policyReferences)
                    }
                }
            }
        }catch(_:Exception){
            NativeCustomSignoffRead.Rejected(
                NativeCustomSignoffRefusal.STORAGE_UNAVAILABLE)
        }
    }
    /** Durable tombstone, so queued stale signoff callbacks cannot restore it. */
    fun revoke(
        ownerKey:String,cycleId:String,expectedRecordRevision:Long
    ):NativeCustomSignoffWrite {
        if(current(ownerKey)==null || !id.matches(cycleId))
            return NativeCustomSignoffWrite.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
        return try {
            fence.withOwnerTransaction(ownerKey){
                val owner=current(ownerKey)
                    ?:return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.NOT_AUTHORIZED)
                val file=path(ownerKey,cycleId)
                val previous=try{decode(file)}catch(_:Exception){
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.CORRUPT_RECORD)
                }
                if(previous==null ||
                    previous.revision!=expectedRecordRevision ||
                    previous.consent==null)
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.STALE_CONSENT)
                if(previous.athleteId!=owner.athleteId ||
                    previous.ownerKey!=ownerKey)
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.NOT_AUTHORIZED)
                val revision=expectedRecordRevision+1
                if(revision !in 1..maximumRevision)
                    return@withOwnerTransaction NativeCustomSignoffWrite.Rejected(
                        NativeCustomSignoffRefusal.STALE_CONSENT)
                atomicWrite(file,encode(SignoffFileRecord(
                    ownerKey,owner.athleteId,cycleId,revision,null)))
                NativeCustomSignoffWrite.Committed(previous.consent.copy(
                    recordRevision=revision))
            }
        }catch(_:Exception){
            NativeCustomSignoffWrite.Rejected(
                NativeCustomSignoffRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
