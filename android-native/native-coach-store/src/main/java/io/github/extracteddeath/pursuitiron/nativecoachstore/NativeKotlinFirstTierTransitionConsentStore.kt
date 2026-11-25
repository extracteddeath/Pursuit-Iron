package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.io.ByteArrayInputStream
import java.io.DataInputStream
import java.io.ByteArrayOutputStream
import java.io.DataOutputStream
import java.nio.ByteBuffer
import java.nio.channels.FileChannel
import java.nio.file.Files
import java.nio.file.LinkOption
import java.nio.file.Path
import java.nio.file.StandardCopyOption
import java.nio.file.StandardOpenOption
import java.security.MessageDigest

/** Immutable per-week transition consent. It cannot update the authored
 * source or rewrite past prescriptions. Preparation/issuance use separate
 * owner-fenced transactions; only a stale, never-prepared approval can be
 * explicitly replaced with an exact fingerprint compare-and-swap.
 */
internal enum class NativeTieredTransitionConsentResult {
    RECORDED, REPLAYED, VERIFIED, REPLACED, MISSING, CONFIRMATION_REQUIRED,
    NOT_AUTHORIZED, STALE_OR_UNVERIFIED, CONFLICT_OR_CORRUPT, STORAGE_UNAVAILABLE
}

internal sealed interface NativeTieredTransitionRecovery {
    data class Ready(val review:NativeSavedNamedReview.Signed,
        val receiptFingerprint:String):NativeTieredTransitionRecovery {
        val mayIssueWorkout:Boolean get()=false
    }
    data class Refused(val reason:NativeTieredTransitionConsentResult):NativeTieredTransitionRecovery
}

/** Decoded only from the private, bounded receipt file. The historical
 * replay must additionally authenticate every field and the issued origin.
 */
internal data class NativeFrozenTierTransition(
    val ownerKey:String,val athleteId:String,val cycleId:String,
    val consentRevision:Long,val cycleRevision:Long,val sourceRevision:Long,
    val consentFingerprint:String,val historyRevision:Long,
    val selection:NativeTieredNextSelection,val resetRequired:Boolean,
    val initialState:TieredLinearState,val maximumIncrease:BigDecimal,val setBudget:Int,
    val prefix:ByteArray,val proposedCycle:ByteArray,val inputs:CustomCoachAuditInputs,
    val fingerprint:String
)

internal class NativeKotlinFirstTierTransitionConsentStore(
    private val directory:Path,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val signoffs:NativeKotlinCustomCycleSignoffFileStore,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val outstanding:NativeKotlinCustomOutstandingWorkoutInventory,
    private val machineRosters:NativeKotlinMachineRosterFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val sha=Regex("^[a-f0-9]{64}$")
    private val maxBytes=NativeCustomCycleDraftCodec.MAX_BYTES+
        NativeTieredHistoryPrefixCodec.MAX_BYTES+NativeCustomCoachAuditInputsCodec.MAX_BYTES+8192
    private fun digest(bytes:ByteArray)=MessageDigest.getInstance("SHA-256").digest(bytes)
    private fun fingerprint(bytes:ByteArray)=digest(bytes).joinToString(""){"%02x".format(it.toInt() and 255)}
    private fun firstWeek(owner:String,cycle:String):Int? =
        (drafts.read(owner,cycle) as? NativeCustomDraftReadResult.Found)
            ?.tieredInitialReceipt?.selection?.absoluteWeek?.plus(1)

    private fun path(owner:String,cycle:String,week:Int,firstWeek:Int):Path {
        val key=digest("$owner/$cycle".toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return directory.resolve(if(week==firstWeek)"tier-first-transition-$key.bin"
            else "tier-first-transition-$key-w$week.bin")
    }

    // Only freshly rederived candidates enter this representation. The
    // original signed fingerprint authenticates initial recipe, source,
    // policy and stock; the complete proposed cycle binds the exact new
    // prescription, not merely the same underlying history.
    private fun encode(review:NativeSavedNamedReview.Signed):ByteArray {
        val next=checkNotNull(review.nextTierCandidate)
        val consent=review.consent
        val slot=checkNotNull(next.request.sourceSlotId)
        require(sha.matches(consent.fingerprintSha256))
        require(next.historyRevision==review.historyRevision)
        val prefix=NativeTieredHistoryPrefixCodec.encode(next.request.scope,
            next.request.tier,slot,next.request.completed)
        val cycle=NativeCustomCycleDraftCodec.encode(next.draft.proposal.draft)
        val inputs=NativeCustomCoachAuditInputsCodec.encode(review.inputs)
        val payload=ByteArrayOutputStream().also{buffer->
            DataOutputStream(buffer).use{out->
                out.writeInt(0x50495443);out.writeInt(1) // PITC
                out.writeUTF(consent.ownerKey);out.writeUTF(consent.athleteId)
                out.writeUTF(consent.cycleId);out.writeLong(consent.recordRevision)
                out.writeLong(consent.cycleRevision);out.writeLong(consent.sourceRevision)
                out.writeUTF(consent.fingerprintSha256)
                out.writeLong(next.historyRevision)
                out.writeUTF(next.selection.nextWorkoutId);out.writeInt(next.selection.absoluteWeek)
                out.writeBoolean(next.selection.recovery)
                out.writeBoolean(next.requiresExplicitResetReview)
                out.writeUTF(next.request.initialState.load.toString())
                out.writeInt(next.request.initialState.stage)
                out.writeUTF(next.request.maximumAuthorizedIncrease.toString())
                out.writeInt(next.request.approvedSetBudget)
                out.writeInt(prefix.size);out.write(prefix)
                out.writeInt(cycle.size);out.write(cycle)
                out.writeInt(inputs.size);out.write(inputs)
            }
        }.toByteArray()
        return (payload+digest(payload)).also{require(it.size<=maxBytes)}
    }

    fun confirm(ownerKey:String,cycleId:String,displayed:NativeSavedNamedReview.Signed,
        athleteConfirmed:Boolean,resetConfirmed:Boolean=false,recoveryConfirmed:Boolean=false):
        NativeTieredTransitionConsentResult =
        evaluate(ownerKey,cycleId,displayed,true,athleteConfirmed,resetConfirmed,recoveryConfirmed)

    /** Replace ONLY an unprepared stale approval for the same workout.
     * The old digest is the athlete-reviewed compare-and-swap token.
     * Ordinary confirm cannot replace a different prescription. A prepared
     * or issued workout requires a separate cancellation protocol.
     */
    fun replaceStaleUnprepared(ownerKey:String,cycleId:String,
        displayed:NativeSavedNamedReview.Signed,expectedReceiptFingerprint:String,
        athleteConfirmed:Boolean,resetConfirmed:Boolean=false,recoveryConfirmed:Boolean=false):
        NativeTieredTransitionConsentResult =
        evaluate(ownerKey,cycleId,displayed,true,athleteConfirmed,resetConfirmed,
            recoveryConfirmed,expectedReceiptFingerprint)

    /** Revalidation requires the exact displayed review again; this is not
     * a general cold-start discovery API or permission to issue a workout. */
    fun verify(ownerKey:String,cycleId:String,displayed:NativeSavedNamedReview.Signed):
        NativeTieredTransitionConsentResult =
        evaluate(ownerKey,cycleId,displayed,false,false,false,false)

    private fun readBytes(file:Path):ByteArray {
        require(!Files.isSymbolicLink(file) && Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
        require(Files.size(file) in 40L..maxBytes.toLong())
        return Files.readAllBytes(file).also{require(it.size in 40..maxBytes)}
    }
    private fun decode(stored:ByteArray):NativeFrozenTierTransition {
        val payload=stored.copyOfRange(0,stored.size-32)
        require(MessageDigest.isEqual(digest(payload),stored.copyOfRange(stored.size-32,stored.size)))
        return DataInputStream(ByteArrayInputStream(payload)).use{input->
            require(input.readInt()==0x50495443 && input.readInt()==1)
            fun identifier()=input.readUTF().also{require(id.matches(it))}
            fun revision()=input.readLong().also{require(it in 1..9_007_199_254_740_991L)}
            fun number()=input.readUTF().let{raw->
                require(raw.length in 1..64)
                BigDecimal(raw).also{require(it.toString()==raw)}
            }
            fun flag()=input.readUnsignedByte().also{require(it in 0..1)}==1
            fun segment(max:Int):ByteArray {
                val length=input.readInt()
                require(length in 1..max && length<=input.available())
                return ByteArray(length).also{input.readFully(it)}
            }
            val owner=identifier();val athlete=identifier();val cycle=identifier()
            val consentRevision=revision();val cycleRevision=revision();val sourceRevision=revision()
            val consentFingerprint=input.readUTF().also{require(sha.matches(it))}
            val historyRevision=revision()
            val workout=identifier();val week=input.readInt().also{require(it in 2..104)}
            val recovery=flag();val reset=flag()
            val load=number();val stage=input.readInt().also{require(it in 0..2)}
            val increase=number();val budget=input.readInt().also{require(it in 1..20)}
            val prefix=segment(NativeTieredHistoryPrefixCodec.MAX_BYTES)
            val proposed=segment(NativeCustomCycleDraftCodec.MAX_BYTES)
            val inputBytes=segment(NativeCustomCoachAuditInputsCodec.MAX_BYTES)
            val inputs=NativeCustomCoachAuditInputsCodec.decode(inputBytes)
            require(input.available()==0)
            require(NativeCustomCoachAuditInputsCodec.encode(inputs).contentEquals(inputBytes))
            NativeFrozenTierTransition(owner,athlete,cycle,consentRevision,cycleRevision,
                sourceRevision,consentFingerprint,historyRevision,
                NativeTieredNextSelection(workout,week,recovery),reset,
                TieredLinearState(stage,load),increase,budget,prefix,proposed,inputs,fingerprint(stored))
        }
    }

    private fun priorReceipts(owner:String,cycle:String,first:Int,target:Int):
        List<NativeFrozenTierTransition> {
        require(first in 2..104 && target in first..104)
        return (first until target).map{week->
            decode(readBytes(path(owner,cycle,week,first))).also{
                require(it.ownerKey==owner && it.cycleId==cycle && it.selection.absoluteWeek==week)
            }
        }
    }

    /** Consecutive candidate preview. Earlier approval files must exist;
     * the reviewer authenticates their frozen recipes and completed origins.
     */
    fun preview(ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        selection:NativeTieredNextSelection):NativeSavedNamedReview {
        fun no(r:NativeSavedNamedReviewRefusal)=NativeSavedNamedReview.Rejected(r)
        if(!id.matches(ownerKey)||!id.matches(cycleId))return no(NativeSavedNamedReviewRefusal.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            if(identity.signedInIdentity()?.ownerKey!=ownerKey)
                return@withOwnerTransaction no(NativeSavedNamedReviewRefusal.NOT_AUTHORIZED)
            val first=firstWeek(ownerKey,cycleId)
                ?:return@withOwnerTransaction no(NativeSavedNamedReviewRefusal.NO_CONFIRMED_DRAFT)
            if(Files.isSymbolicLink(directory) || selection.absoluteWeek !in first..104)
                return@withOwnerTransaction no(NativeSavedNamedReviewRefusal.PROTECTED_WEEK)
            val prior=try{priorReceipts(ownerKey,cycleId,first,selection.absoluteWeek)}
                catch(_:Exception){return@withOwnerTransaction no(NativeSavedNamedReviewRefusal.HISTORY_CHANGED)}
            val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
                signoffs,history,outstanding,machineRosters)
            reviewer.previewTieredNextForPreparation(ownerKey,cycleId,inputs,selection,null,prior)
        }}.getOrElse{no(NativeSavedNamedReviewRefusal.STORAGE_UNAVAILABLE)}
    }

    /** Cold discovery of a selected week needs no caller-supplied inputs.
     * The default retains the first-transition discovery entry point.
     */
    fun recover(ownerKey:String,cycleId:String,absoluteWeek:Int?=null):NativeTieredTransitionRecovery {
        fun no(reason:NativeTieredTransitionConsentResult)=NativeTieredTransitionRecovery.Refused(reason)
        if(!id.matches(ownerKey)||!id.matches(cycleId))
            return no(NativeTieredTransitionConsentResult.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction no(NativeTieredTransitionConsentResult.NOT_AUTHORIZED)
            if(Files.isSymbolicLink(directory))
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE)
            val first=firstWeek(ownerKey,cycleId)
                ?:return@withOwnerTransaction no(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED)
            val week=absoluteWeek?:first
            if(week !in first..104)
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED)
            val file=path(ownerKey,cycleId,week,first)
            if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.MISSING)
            val stored:ByteArray
            val metadata:NativeFrozenTierTransition
            val prior:List<NativeFrozenTierTransition>
            try {
                stored=readBytes(file)
                metadata=decode(stored)
                require(metadata.ownerKey==ownerKey && metadata.athleteId==owner.athleteId &&
                    metadata.cycleId==cycleId && metadata.selection.absoluteWeek==week)
                prior=priorReceipts(ownerKey,cycleId,first,week)
            }catch(_:Exception){
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT)
            }
            val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
                signoffs,history,outstanding,machineRosters)
            val fresh=reviewer.previewTieredNextForPreparation(ownerKey,cycleId,metadata.inputs,
                metadata.selection,metadata.fingerprint,prior)
                as? NativeSavedNamedReview.Signed
                ?:return@withOwnerTransaction no(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED)
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.NOT_AUTHORIZED)
            if(!MessageDigest.isEqual(stored,encode(fresh)))
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED)
            NativeTieredTransitionRecovery.Ready(fresh,metadata.fingerprint)
        }}.getOrElse{no(NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE)}
    }

    private fun evaluate(ownerKey:String,cycleId:String,displayed:NativeSavedNamedReview.Signed,
        write:Boolean,confirmed:Boolean,reset:Boolean,recovery:Boolean,
        replaceFingerprint:String?=null):NativeTieredTransitionConsentResult {
        if(!id.matches(ownerKey)||!id.matches(cycleId))
            return NativeTieredTransitionConsentResult.NOT_AUTHORIZED
        if(write&&!confirmed)return NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED
        if(replaceFingerprint!=null && !sha.matches(replaceFingerprint))
            return NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.NOT_AUTHORIZED
            val shown=displayed.nextTierCandidate
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
                signoffs,history,outstanding,machineRosters)
            // Receipt-backed re-review is only possible for the exact
            // already-persisted bytes, never for an arbitrary display.
            val first=firstWeek(ownerKey,cycleId)
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            if(Files.isSymbolicLink(directory) || shown.selection.absoluteWeek !in first..104)
                return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            val prior=try{priorReceipts(ownerKey,cycleId,first,shown.selection.absoluteWeek)}
                catch(_:Exception){return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT}
            val existingFile=path(ownerKey,cycleId,shown.selection.absoluteWeek,first)
            val displayBytes=encode(displayed)
            val sameReceipt=Files.isRegularFile(existingFile,LinkOption.NOFOLLOW_LINKS) &&
                !Files.isSymbolicLink(directory) && Files.size(existingFile)==displayBytes.size.toLong() &&
                MessageDigest.isEqual(Files.readAllBytes(existingFile),displayBytes)
            val fresh=reviewer.previewTieredNextForPreparation(
                ownerKey,cycleId,displayed.inputs,shown.selection,
                fingerprint(displayBytes).takeIf{sameReceipt && replaceFingerprint==null},prior)
                as? NativeSavedNamedReview.Signed
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            if(fresh!=displayed || fresh.consent.athleteId!=owner.athleteId ||
                fresh.consent.ownerKey!=ownerKey || fresh.consent.cycleId!=cycleId)
                return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            val next=checkNotNull(fresh.nextTierCandidate)
            if(write && ((next.requiresExplicitResetReview&&!reset) ||
                (next.selection.recovery&&!recovery)))
                return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED
            val bytes=encode(fresh)
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction NativeTieredTransitionConsentResult.NOT_AUTHORIZED
            if(Files.isSymbolicLink(directory))
                return@withOwnerTransaction NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE
            val file=existingFile
            val exists=Files.exists(file,LinkOption.NOFOLLOW_LINKS)
            if(exists){
                val oldBytes=try{readBytes(file)}
                    catch(_:Exception){return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT}
                if(MessageDigest.isEqual(oldBytes,bytes))
                    return@withOwnerTransaction if(replaceFingerprint!=null)
                        NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT
                    else if(write)NativeTieredTransitionConsentResult.REPLAYED
                    else NativeTieredTransitionConsentResult.VERIFIED
                if(replaceFingerprint==null)
                    return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT
                val old=try{decode(oldBytes)}
                    catch(_:Exception){return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT}
                if(old.fingerprint!=replaceFingerprint ||
                    old.ownerKey!=ownerKey || old.athleteId!=owner.athleteId || old.cycleId!=cycleId ||
                    old.selection.nextWorkoutId!=next.selection.nextWorkoutId ||
                    old.selection.absoluteWeek!=next.selection.absoluteWeek ||
                    old.consentRevision!=fresh.consent.recordRevision ||
                    old.consentFingerprint!=fresh.consent.fingerprintSha256 ||
                    old.cycleRevision!=fresh.consent.cycleRevision ||
                    old.sourceRevision!=fresh.consent.sourceRevision ||
                    old.historyRevision>=next.historyRevision)
                    return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT
                // The fresh review above excluded ALL outstanding workouts,
                // even matching self-preparation. Also refuse a later receipt
                // (a gap/corrupt chain is never repaired by overwriting it).
                if((next.selection.absoluteWeek+1..104).any{week->
                    Files.exists(path(ownerKey,cycleId,week,first),LinkOption.NOFOLLOW_LINKS)})
                    return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT
            }else if(replaceFingerprint!=null)
                return@withOwnerTransaction NativeTieredTransitionConsentResult.MISSING
            if(!write)return@withOwnerTransaction NativeTieredTransitionConsentResult.MISSING
            Files.createDirectories(directory)
            val pending=Files.createTempFile(directory,"tier-first-transition-", ".pending")
            try {
                FileChannel.open(pending,StandardOpenOption.WRITE,
                    StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                    val buffer=ByteBuffer.wrap(bytes)
                    while(buffer.hasRemaining())channel.write(buffer)
                    channel.force(true)
                }
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction NativeTieredTransitionConsentResult.NOT_AUTHORIZED
                if(exists)Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
                else Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE)
            }finally{Files.deleteIfExists(pending)}
            if(exists)NativeTieredTransitionConsentResult.REPLACED else NativeTieredTransitionConsentResult.RECORDED
        }}.getOrElse{NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE}
    }
}
