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
enum class NativeTieredTransitionConsentResult {
    RECORDED, REPLAYED, VERIFIED, REPLACED, MISSING, CONFIRMATION_REQUIRED,
    NOT_AUTHORIZED, STALE_OR_UNVERIFIED, CONFLICT_OR_CORRUPT, STORAGE_UNAVAILABLE
}

sealed interface NativeTieredTransitionRecovery {
    data class Ready(val review:NativeSavedNamedReview.Signed,
        val receiptFingerprint:String):NativeTieredTransitionRecovery {
        val mayIssueWorkout:Boolean get()=false
    }
    data class Refused(val reason:NativeTieredTransitionConsentResult):NativeTieredTransitionRecovery
}

/** A corrected-history review, never permission to issue. The exact old
 * preparation is separately discarded before the athlete can replace consent. */
data class NativeStaleTierTransitionReview(
    val review:NativeSavedNamedReview.Signed,
    val previousReceiptFingerprint:String,
    val previousTargets:List<CustomAuthoredSet>,
    val preparation:NativeCustomPreparedWorkout?
)

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

class NativeKotlinFirstTierTransitionConsentStore(
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

    private val multiMaxBytes=NativeCustomCycleDraftCodec.MAX_BYTES+
        32*NativeTieredHistoryPrefixCodec.MAX_BYTES+NativeCustomCoachAuditInputsCodec.MAX_BYTES+32768

    private data class FrozenDay(
        val owner:String,val athlete:String,val cycle:String,
        val selection:NativeMultiTierNextSelection,val inputs:CustomCoachAuditInputs,
        val consentRevision:Long,val cycleRevision:Long,val sourceRevision:Long,
        val consentFingerprint:String,val historyRevision:Long,
        val slots:Map<String,ByteArray>,val resets:Set<String>,val proposed:ByteArray
    )

    private fun multiPath(owner:String,cycle:String,week:Int,weekday:Int):Path {
        val key=fingerprint("$owner/$cycle".toByteArray(Charsets.UTF_8))
        return directory.resolve("tier-day-transition-$key-w$week-d$weekday.bin")
    }

    /** One receipt binds the entire session, not a separate approval per
     * slot. Prefix bytes are canonical and retain corrected actuals at
     * consent; the original consent authenticates scope and reference. */
    private fun encodeMulti(review:NativeSavedNamedReview.Signed):ByteArray {
        val next=checkNotNull(review.nextMultiTierCandidate)
        val consent=review.consent
        require(next.historyRevision==review.historyRevision && next.slots.size in 1..32)
        val sorted=next.slots.sortedBy{it.evidence.key.slotId}
        require(sorted.map{it.evidence.key.slotId}.distinct().size==sorted.size)
        require(next.selection.recoverySlotIds.all{id->sorted.any{it.evidence.key.slotId==id}})
        val body=ByteArrayOutputStream().also{buffer->
            DataOutputStream(buffer).use{out->
                fun segment(bytes:ByteArray){out.writeInt(bytes.size);out.write(bytes)}
                out.writeInt(0x50495444);out.writeInt(1) // PITD
                out.writeUTF(consent.ownerKey);out.writeUTF(consent.athleteId);out.writeUTF(consent.cycleId)
                out.writeLong(consent.recordRevision);out.writeLong(consent.cycleRevision);out.writeLong(consent.sourceRevision)
                out.writeUTF(consent.fingerprintSha256);out.writeLong(next.historyRevision)
                out.writeUTF(next.selection.nextWorkoutId);out.writeInt(next.selection.absoluteWeek)
                out.writeInt(next.selection.weekday);out.writeInt(sorted.size)
                for(slot in sorted){
                    val key=slot.evidence.key
                    val request=slot.evidence.request
                    require(request.nextWorkoutId==next.selection.nextWorkoutId &&
                        request.sourceSlotId==key.slotId && key.weekday==next.selection.weekday &&
                        request.recovery==(key.slotId in next.selection.recoverySlotIds))
                    out.writeUTF(key.slotId);out.writeBoolean(request.recovery)
                    out.writeBoolean(slot.requiresExplicitResetReview)
                    segment(NativeTieredHistoryPrefixCodec.encode(request.scope,request.tier,key.slotId,request.completed))
                }
                segment(NativeCustomCycleDraftCodec.encode(next.draft))
                segment(NativeCustomCoachAuditInputsCodec.encode(review.inputs))
            }
        }.toByteArray()
        return (body+digest(body)).also{require(it.size<=multiMaxBytes)}
    }

    private fun readMulti(file:Path):ByteArray=readBytes(file,multiMaxBytes)

    private fun decodeMulti(stored:ByteArray):FrozenDay {
        require(stored.size in 40..multiMaxBytes)
        val body=stored.copyOfRange(0,stored.size-32)
        require(MessageDigest.isEqual(digest(body),stored.copyOfRange(stored.size-32,stored.size)))
        return DataInputStream(ByteArrayInputStream(body)).use{input->
            require(input.readInt()==0x50495444 && input.readInt()==1)
            fun identifier()=input.readUTF().also{require(id.matches(it))}
            fun revision()=input.readLong().also{require(it in 1..9_007_199_254_740_991L)}
            fun flag()=input.readUnsignedByte().also{require(it in 0..1)}==1
            fun segment(max:Int):ByteArray {
                val length=input.readInt()
                require(length in 1..max && length<=input.available())
                return ByteArray(length).also{input.readFully(it)}
            }
            val owner=identifier();val athlete=identifier();val cycle=identifier()
            val consentRev=revision();val cycleRev=revision();val sourceRev=revision()
            val consentHash=input.readUTF().also{require(sha.matches(it))}
            val historyRev=revision()
            val workout=identifier();val week=input.readInt().also{require(it in 2..104)}
            val weekday=input.readInt().also{require(it in 0..6)}
            val count=input.readInt().also{require(it in 1..32)}
            val slots=linkedMapOf<String,ByteArray>()
            val recovery=linkedSetOf<String>()
            val resets=linkedSetOf<String>()
            repeat(count){
                val slot=identifier()
                require(slot !in slots && (slots.isEmpty() || slots.keys.last()<slot))
                if(flag())recovery+=slot
                if(flag())resets+=slot
                slots[slot]=segment(NativeTieredHistoryPrefixCodec.MAX_BYTES)
            }
            val proposed=segment(NativeCustomCycleDraftCodec.MAX_BYTES)
            val inputBytes=segment(NativeCustomCoachAuditInputsCodec.MAX_BYTES)
            val inputs=NativeCustomCoachAuditInputsCodec.decode(inputBytes)
            require(input.available()==0 && NativeCustomCoachAuditInputsCodec.encode(inputs).contentEquals(inputBytes))
            FrozenDay(owner,athlete,cycle,NativeMultiTierNextSelection(workout,week,weekday,recovery.toSet()),
                inputs,consentRev,cycleRev,sourceRev,consentHash,historyRev,slots.toMap(),resets.toSet(),proposed)
        }
    }

    /** The only new write path is another day-keyed receipt in this store.
     * It reopens the signed review under the owner fence and requires exact
     * explicit approval of the displayed reset and recovery slots. */
    fun confirmMulti(ownerKey:String,cycleId:String,displayed:NativeSavedNamedReview.Signed,
        athleteConfirmed:Boolean,resetSlots:Set<String> = emptySet(),
        recoverySlots:Set<String> = emptySet()):NativeTieredTransitionConsentResult {
        if(!athleteConfirmed)return NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED
        if(!id.matches(ownerKey)||!id.matches(cycleId))return NativeTieredTransitionConsentResult.NOT_AUTHORIZED
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.NOT_AUTHORIZED
            val shown=displayed.nextMultiTierCandidate
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
                signoffs,history,outstanding,machineRosters)
            val fresh=reviewer.previewMultiTieredNext(ownerKey,cycleId,displayed.inputs,shown.selection)
                as? NativeSavedNamedReview.Signed
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            if(fresh!=displayed || fresh.consent.ownerKey!=ownerKey ||
                fresh.consent.athleteId!=owner.athleteId || fresh.consent.cycleId!=cycleId)
                return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            val next=checkNotNull(fresh.nextMultiTierCandidate)
            if(resetSlots!=next.slots.filter{it.requiresExplicitResetReview}.map{it.evidence.key.slotId}.toSet() ||
                recoverySlots!=next.selection.recoverySlotIds)
                return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFIRMATION_REQUIRED
            if(Files.isSymbolicLink(directory))
                return@withOwnerTransaction NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE
            val file=multiPath(ownerKey,cycleId,next.selection.absoluteWeek,next.selection.weekday)
            val bytes=encodeMulti(fresh)
            if(Files.exists(file,LinkOption.NOFOLLOW_LINKS)){
                val old=runCatching{readMulti(file)}.getOrNull()
                    ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT
                return@withOwnerTransaction if(MessageDigest.isEqual(old,bytes))
                    NativeTieredTransitionConsentResult.REPLAYED else NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT
            }
            if(!writeReceipt(file,bytes,owner,false))
                return@withOwnerTransaction NativeTieredTransitionConsentResult.NOT_AUTHORIZED
            NativeTieredTransitionConsentResult.RECORDED
        }}.getOrElse{NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE}
    }

    /** Cold re-review has no caller-supplied inputs or reference values.
     * No preparation or issuance is authorized by this receipt alone. */
    fun recoverMulti(ownerKey:String,cycleId:String,week:Int,weekday:Int):NativeTieredTransitionRecovery {
        fun no(reason:NativeTieredTransitionConsentResult)=NativeTieredTransitionRecovery.Refused(reason)
        if(!id.matches(ownerKey)||!id.matches(cycleId)||week !in 2..104||weekday !in 0..6)
            return no(NativeTieredTransitionConsentResult.NOT_AUTHORIZED)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction no(NativeTieredTransitionConsentResult.NOT_AUTHORIZED)
            if(Files.isSymbolicLink(directory))
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE)
            val file=multiPath(ownerKey,cycleId,week,weekday)
            if(!Files.exists(file,LinkOption.NOFOLLOW_LINKS))
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.MISSING)
            val bytes=runCatching{readMulti(file)}.getOrNull()
                ?:return@withOwnerTransaction no(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT)
            val old=runCatching{decodeMulti(bytes)}.getOrNull()
                ?:return@withOwnerTransaction no(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT)
            if(old.owner!=ownerKey || old.athlete!=owner.athleteId || old.cycle!=cycleId ||
                old.selection.absoluteWeek!=week || old.selection.weekday!=weekday)
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT)
            val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
                signoffs,history,outstanding,machineRosters)
            val fresh=reviewer.previewMultiTieredNext(ownerKey,cycleId,old.inputs,old.selection)
                as? NativeSavedNamedReview.Signed
                ?:return@withOwnerTransaction no(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED)
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.NOT_AUTHORIZED)
            if(!MessageDigest.isEqual(bytes,encodeMulti(fresh)))
                return@withOwnerTransaction no(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED)
            NativeTieredTransitionRecovery.Ready(fresh,fingerprint(bytes))
        }}.getOrElse{no(NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE)}
    }

    /** Shared atomic persistence for both receipt shapes. Owner fence is
     * held by the caller. Never replace an existing receipt by default. */
    private fun writeReceipt(file:Path,bytes:ByteArray,owner:NativeKotlinOwnerIdentity,replace:Boolean):Boolean {
        Files.createDirectories(directory)
        require(!Files.isSymbolicLink(directory))
        val pending=Files.createTempFile(directory,"tier-consent-", ".pending")
        try {
            FileChannel.open(pending,StandardOpenOption.WRITE,StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            if(identity.signedInIdentity()!=owner)return false
            if(replace)Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
            else Files.move(pending,file,StandardCopyOption.ATOMIC_MOVE)
            return true
        }finally{Files.deleteIfExists(pending)}
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

    /** App-facing schedule gate; the original thread replay remains
     * independent of the full calendar for historical evidence. */
    private fun scheduled(ownerKey:String,cycleId:String,week:Int,weekday:Int,workoutId:String?,
        calendar:NativeKotlinCustomNextCycleSessionReview,requirePrepared:Boolean=false):Boolean {
        val receipt=(drafts.read(ownerKey,cycleId) as? NativeCustomDraftReadResult.Found)
            ?.tieredInitialReceipt ?:return false
        if(weekday!=receipt.selection.weekday || week<=receipt.selection.absoluteWeek)return false
        return when(val next=calendar.reviewTieredCalendar(ownerKey,cycleId)) {
            is NativeCustomNextCycleSlot.Next -> !requirePrepared &&
                next.absoluteWeek==week && next.weekday==weekday
            is NativeCustomNextCycleSlot.Prepared -> next.absoluteWeek==week && next.weekday==weekday &&
                (workoutId==null || next.workoutId==workoutId)
            else -> false
        }
    }

    fun previewScheduled(ownerKey:String,cycleId:String,weekday:Int,selection:NativeTieredNextSelection,
        calendar:NativeKotlinCustomNextCycleSessionReview):NativeSavedNamedReview =
        runCatching{fence.withOwnerTransaction(ownerKey){
            if(!scheduled(ownerKey,cycleId,selection.absoluteWeek,weekday,selection.nextWorkoutId,calendar) ||
                (recover(ownerKey,cycleId,selection.absoluteWeek) as? NativeTieredTransitionRecovery.Refused)
                    ?.reason!=NativeTieredTransitionConsentResult.MISSING)
                return@withOwnerTransaction NativeSavedNamedReview.Rejected(NativeSavedNamedReviewRefusal.PROTECTED_WEEK)
            previewUsingSavedRules(ownerKey,cycleId,selection)
        }}.getOrElse{NativeSavedNamedReview.Rejected(NativeSavedNamedReviewRefusal.STORAGE_UNAVAILABLE)}

    fun recoverScheduled(ownerKey:String,cycleId:String,week:Int,weekday:Int,
        calendar:NativeKotlinCustomNextCycleSessionReview):NativeTieredTransitionRecovery =
        runCatching{fence.withOwnerTransaction(ownerKey){
            if(!scheduled(ownerKey,cycleId,week,weekday,null,calendar))
                return@withOwnerTransaction NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED)
            val result=recover(ownerKey,cycleId,week)
            if(result is NativeTieredTransitionRecovery.Ready &&
                !scheduled(ownerKey,cycleId,week,weekday,result.review.nextTierCandidate?.selection?.nextWorkoutId,calendar))
                return@withOwnerTransaction NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED)
            result
        }}.getOrElse{NativeTieredTransitionRecovery.Refused(NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE)}

    /** The old receipt and any unissued preparation remain unchanged.
     * Recover the same workout ID and recovery choice, with newer verified
     * history and proof of the original frozen recipe. */
    fun reviewStaleScheduled(ownerKey:String,cycleId:String,week:Int,weekday:Int,
        calendar:NativeKotlinCustomNextCycleSessionReview):NativeStaleTierTransitionReview? =
        runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction null
            if(!scheduled(ownerKey,cycleId,week,weekday,null,calendar) ||
                (recover(ownerKey,cycleId,week) as? NativeTieredTransitionRecovery.Refused)
                    ?.reason!=NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED)
                return@withOwnerTransaction null
            val first=firstWeek(ownerKey,cycleId)?:return@withOwnerTransaction null
            if(week !in first..104 ||
                (week+1..104).any{Files.exists(path(ownerKey,cycleId,it,first),LinkOption.NOFOLLOW_LINKS)})
                return@withOwnerTransaction null
            val old=decode(readBytes(path(ownerKey,cycleId,week,first)))
            if(old.ownerKey!=ownerKey || old.athleteId!=owner.athleteId ||
                old.cycleId!=cycleId || old.selection.absoluteWeek!=week ||
                !scheduled(ownerKey,cycleId,week,weekday,old.selection.nextWorkoutId,calendar))
                return@withOwnerTransaction null
            val preparation=outstanding.preparedOrigin(ownerKey,old.selection.nextWorkoutId)
            if(preparation!=null && !canCancelStalePreparation(preparation))
                return@withOwnerTransaction null
            val rules=signoffs.signedTieredInputs(ownerKey,cycleId)
                ?:return@withOwnerTransaction null
            val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
                signoffs,history,outstanding,machineRosters)
            val fresh=reviewer.previewTieredNextForPreparation(ownerKey,cycleId,rules,old.selection,
                old.fingerprint.takeIf{preparation!=null},priorReceipts(ownerKey,cycleId,first,week),
                staleReceipt=old) as? NativeSavedNamedReview.Signed
                ?:return@withOwnerTransaction null
            val initial=fresh.initialTierReceipt?:return@withOwnerTransaction null
            val keyWeek=initial.weekInBlock+week-initial.selection.absoluteWeek
            val previous=NativeCustomCycleDraftCodec.decode(old.proposedCycle).weekOverrides[initial.blockId]
                ?.singleOrNull{it.weekInBlock==keyWeek && it.weekday==weekday && it.slotId==initial.selection.slotId}
                ?.sets?:return@withOwnerTransaction null
            if(identity.signedInIdentity()!=owner)return@withOwnerTransaction null
            NativeStaleTierTransitionReview(fresh,old.fingerprint,previous,preparation)
        }}.getOrNull()

    fun cancelStaleScheduled(ownerKey:String,cycleId:String,shown:NativeStaleTierTransitionReview,
        calendar:NativeKotlinCustomNextCycleSessionReview,prepared:NativeKotlinCustomWorkoutPreparedStore,
        confirmed:Boolean):Boolean =
        runCatching{fence.withOwnerTransaction(ownerKey){
            if(!confirmed)return@withOwnerTransaction false
            val saved=shown.preparation?:return@withOwnerTransaction false
            if(reviewStaleScheduled(ownerKey,cycleId,saved.snapshot.absoluteWeek,saved.snapshot.weekday,calendar)!=shown)
                return@withOwnerTransaction false
            prepared.cancelStaleTierPreparation(saved,this,true)
        }}.getOrDefault(false)

    fun replaceStaleScheduled(ownerKey:String,cycleId:String,shown:NativeStaleTierTransitionReview,
        calendar:NativeKotlinCustomNextCycleSessionReview,confirmed:Boolean,reset:Boolean,recovery:Boolean):
        NativeTieredTransitionConsentResult =
        runCatching{fence.withOwnerTransaction(ownerKey){
            if(shown.preparation!=null)
                return@withOwnerTransaction NativeTieredTransitionConsentResult.CONFLICT_OR_CORRUPT
            val week=shown.review.nextTierCandidate?.selection?.absoluteWeek
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            val day=shown.review.initialTierReceipt?.selection?.weekday
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            if(reviewStaleScheduled(ownerKey,cycleId,week,day,calendar)!=shown)
                return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            replaceStaleUnprepared(ownerKey,cycleId,shown.review,shown.previousReceiptFingerprint,
                confirmed,reset,recovery)
        }}.getOrElse{NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE}

    fun confirmScheduled(ownerKey:String,cycleId:String,displayed:NativeSavedNamedReview.Signed,
        calendar:NativeKotlinCustomNextCycleSessionReview,confirmed:Boolean,
        resetConfirmed:Boolean,recoveryConfirmed:Boolean):NativeTieredTransitionConsentResult =
        runCatching{fence.withOwnerTransaction(ownerKey){
            val next=displayed.nextTierCandidate
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            val day=displayed.initialTierReceipt?.selection?.weekday
                ?:return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            if(!scheduled(ownerKey,cycleId,next.selection.absoluteWeek,day,next.selection.nextWorkoutId,calendar))
                return@withOwnerTransaction NativeTieredTransitionConsentResult.STALE_OR_UNVERIFIED
            confirm(ownerKey,cycleId,displayed,confirmed,resetConfirmed,recoveryConfirmed)
        }}.getOrElse{NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE}

    fun inspectScheduled(ownerKey:String,cycleId:String,workoutId:String,week:Int,weekday:Int,
        calendar:NativeKotlinCustomNextCycleSessionReview,
        preflight:NativeKotlinCustomWorkoutPreflight):NativeFirstTierTransitionPreflight =
        runCatching{fence.withOwnerTransaction(ownerKey){
            if(!scheduled(ownerKey,cycleId,week,weekday,workoutId,calendar))
                return@withOwnerTransaction NativeFirstTierTransitionPreflight.Rejected(NativeCustomPreflightRefusal.SESSION_UNAVAILABLE)
            preflight.inspectFirstTierTransition(ownerKey,workoutId,cycleId,week,weekday,this)
        }}.getOrElse{NativeFirstTierTransitionPreflight.Rejected(NativeCustomPreflightRefusal.STORAGE_UNAVAILABLE)}

    fun prepareScheduled(ownerKey:String,cycleId:String,shown:NativeFirstTierTransitionPreflight.Reviewed,
        calendar:NativeKotlinCustomNextCycleSessionReview,preflight:NativeKotlinCustomWorkoutPreflight,
        prepared:NativeKotlinCustomWorkoutPreparedStore):NativeCustomPrepareResult =
        runCatching{fence.withOwnerTransaction(ownerKey){
            val id=shown.review.nextTierCandidate?.selection?.nextWorkoutId
                ?:return@withOwnerTransaction NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            val week=shown.session.absoluteWeek
            val day=shown.session.weekday
            if(inspectScheduled(ownerKey,cycleId,id,week,day,calendar,preflight)!=shown)
                return@withOwnerTransaction NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            prepared.prepareFirstTierTransition(ownerKey,id,cycleId,week,day,this)
        }}.getOrElse{NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.STORAGE_UNAVAILABLE)}

    fun issueScheduled(ownerKey:String,cycleId:String,shown:NativeCustomPreparedWorkout,
        calendar:NativeKotlinCustomNextCycleSessionReview,preflight:NativeKotlinCustomWorkoutPreflight,
        prepared:NativeKotlinCustomWorkoutPreparedStore,
        issued:NativeKotlinCustomWorkoutJournalFileStore):NativeCustomJournalResult =
        runCatching{fence.withOwnerTransaction(ownerKey){
            val id=shown.workoutId
            val week=shown.snapshot.absoluteWeek
            val day=shown.snapshot.weekday
            if(!scheduled(ownerKey,cycleId,week,day,id,calendar,requirePrepared=true))
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_PREPARED)
            val fresh=inspectScheduled(ownerKey,cycleId,id,week,day,calendar,preflight)
                as? NativeFirstTierTransitionPreflight.Reviewed
            if(shown.authority!=NativeCustomPreparationAuthority.TIERED_TRANSITION ||
                fresh==null || fresh.session!=shown.snapshot ||
                fresh.receiptFingerprint!=shown.consentFingerprint ||
                fresh.review.consent.recordRevision!=shown.consentRevision ||
                (prepared.open(ownerKey,id) as? NativeCustomPreparedRead.Found)?.workout!=shown)
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.NOT_PREPARED)
            issued.issueFirstTierTransition(ownerKey,id,cycleId,week,day,this)
        }}.getOrElse{NativeCustomJournalResult.Rejected(NativeCustomJournalRefusal.STORAGE_UNAVAILABLE)}

    private fun readBytes(file:Path,limit:Int=maxBytes):ByteArray {
        require(!Files.isSymbolicLink(file) && Files.isRegularFile(file,LinkOption.NOFOLLOW_LINKS))
        require(Files.size(file) in 40L..limit.toLong())
        return Files.readAllBytes(file).also{require(it.size in 40..limit)}
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

    /** Cold UI entry: no externally supplied policy, source, load or
     * history. The entire ordinary transition review still runs under the
     * owner fence after recovering the exact saved signed rules. */
    fun previewUsingSavedRules(ownerKey:String,cycleId:String,
        selection:NativeTieredNextSelection):NativeSavedNamedReview =
        runCatching{fence.withOwnerTransaction(ownerKey){
            if(identity.signedInIdentity()?.ownerKey!=ownerKey)
                return@withOwnerTransaction NativeSavedNamedReview.Rejected(
                    NativeSavedNamedReviewRefusal.NOT_AUTHORIZED)
            val inputs=signoffs.signedTieredInputs(ownerKey,cycleId)
                ?:return@withOwnerTransaction NativeSavedNamedReview.Rejected(
                    NativeSavedNamedReviewRefusal.STALE_PRIOR_CONSENT)
            preview(ownerKey,cycleId,inputs,selection)
        }}.getOrElse{NativeSavedNamedReview.Rejected(NativeSavedNamedReviewRefusal.STORAGE_UNAVAILABLE)}

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

    /** Called only by the prepared store inside its owner transaction.
     * The existing receipt authorizes re-review of this exact unissued
     * preparation, not issuance of the freshly derived candidate.
     * Require a strictly newer verified history revision and unchanged
     * underlying cycle authority. Corruption, revocation, or unrelated
     * outstanding sessions cannot be repaired by removing a preparation.
     */
    internal fun canCancelStalePreparation(saved:NativeCustomPreparedWorkout):Boolean {
        val ownerKey=saved.ownerKey
        val cycleId=saved.snapshot.stamp.cycleId
        val week=saved.snapshot.absoluteWeek
        if(!id.matches(ownerKey)||!id.matches(cycleId)||
            saved.authority!=NativeCustomPreparationAuthority.TIERED_TRANSITION)return false
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{
                it.ownerKey==ownerKey && it.athleteId==saved.athleteId
            }?:return@withOwnerTransaction false
            val first=firstWeek(ownerKey,cycleId)?:return@withOwnerTransaction false
            if(Files.isSymbolicLink(directory)||week !in first..104)
                return@withOwnerTransaction false
            val old=decode(readBytes(path(ownerKey,cycleId,week,first)))
            if(old.ownerKey!=ownerKey || old.athleteId!=owner.athleteId ||
                old.cycleId!=cycleId || old.selection.absoluteWeek!=week ||
                old.selection.nextWorkoutId!=saved.workoutId ||
                old.consentRevision!=saved.consentRevision ||
                old.fingerprint!=saved.consentFingerprint ||
                outstanding.preparedOrigin(ownerKey,saved.workoutId)!=saved ||
                (week+1..104).any{Files.exists(path(ownerKey,cycleId,it,first),LinkOption.NOFOLLOW_LINKS)})
                return@withOwnerTransaction false
            val prior=priorReceipts(ownerKey,cycleId,first,week)
            val reviewer=NativeKotlinSavedNamedPercentageReview(fence,identity,drafts,sources,
                signoffs,history,outstanding,machineRosters)
            // The reviewer independently checks the complete issued/completed
            // census, exact historical origins and corrected current ledger.
            val fresh=reviewer.previewTieredNextForPreparation(ownerKey,cycleId,old.inputs,
                old.selection,old.fingerprint,prior,staleReceipt=old) as? NativeSavedNamedReview.Signed
                ?:return@withOwnerTransaction false
            val next=fresh.nextTierCandidate?:return@withOwnerTransaction false
            identity.signedInIdentity()==owner &&
                fresh.consent.recordRevision==old.consentRevision &&
                fresh.consent.fingerprintSha256==old.consentFingerprint &&
                fresh.consent.cycleRevision==old.cycleRevision &&
                fresh.consent.sourceRevision==old.sourceRevision &&
                next.selection==old.selection && next.historyRevision>old.historyRevision
        }}.getOrDefault(false)
    }

    /**
     * Recovery-only inventory. Scan physical files, not just selected weeks.
     * Reconcile frozen consent prefixes with the verified completed census
     * and exact prepared origins. Stale approvals remain evidence, never
     * authority. Live preparation/issuance must still call recover().
     * Null is an invalid, incomplete or foreign inventory.
     */
    internal fun inventoryForRecovery(ownerKey:String):Int? {
        if(!id.matches(ownerKey))return null
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction null
            require(Files.isDirectory(directory,LinkOption.NOFOLLOW_LINKS))
            val files=Files.walk(directory).use{stream->
                stream.filter{it.fileName.toString().startsWith("tier-first-transition-")}
                    .limit(4097).toList()
            }
            require(files.size<=4096)
            val records=files.map{file->
                val old=decode(readBytes(file))
                require(old.ownerKey==ownerKey && old.athleteId==owner.athleteId)
                val first=firstWeek(ownerKey,old.cycleId)?:error("missing initial tier receipt")
                require(old.selection.absoluteWeek in first..104)
                require(file==path(ownerKey,old.cycleId,old.selection.absoluteWeek,first))
                old
            }
            val timeline=(history.read(ownerKey,owner.athleteId)
                as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
                ?:return@withOwnerTransaction null
            val completed=timeline.completions.filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                .map{it.manifest.workoutId}.toSet()
            val census=(outstanding.inspect(ownerKey,owner.athleteId,completed)
                as? NativeCustomInventoryReview.Reviewed)?:return@withOwnerTransaction null
            require(records.map{it.selection.nextWorkoutId}.distinct().size==records.size)
            for((cycleId,unordered) in records.groupBy{it.cycleId}){
                val stored=drafts.read(ownerKey,cycleId) as? NativeCustomDraftReadResult.Found
                    ?:return@withOwnerTransaction null
                val initial=stored.tieredInitialReceipt?:return@withOwnerTransaction null
                require(NativeTieredInitialDraftReceiptCodec.valid(initial,ownerKey,stored.draft))
                val selection=initial.selection
                val origin=outstanding.preparedOrigin(ownerKey,selection.nextWorkoutId)
                    ?:return@withOwnerTransaction null
                require(origin.authority==NativeCustomPreparationAuthority.TIERED_INITIAL &&
                    origin.snapshot.stamp.cycleId==cycleId &&
                    origin.snapshot.absoluteWeek==selection.absoluteWeek &&
                    origin.snapshot.weekday==selection.weekday &&
                    selection.nextWorkoutId in completed)
                val scope=BasicProgressionScope(owner.athleteId,cycleId,initial.stamp.gymId,
                    initial.blockId,initial.blockId+":d"+selection.weekday,initial.exerciseId,
                    initial.machineInstanceId,initial.unit,initial.basis)
                val sorted=unordered.sortedBy{it.selection.absoluteWeek}
                // Reconstruct the physical request from the signed initial
                // source rather than trusting numerical fields in a receipt.
                val source=(sources.read(ownerKey) as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction null
                val inputs=sorted.first().inputs
                require(source.ownerKey==ownerKey && source.athleteId==owner.athleteId &&
                    source.revision==initial.stamp.gymCatalogSourceRevision &&
                    NativeCustomCycleSignoffFingerprint.calculateTieredInitial(
                        stored.draft,source,inputs,initial)==origin.consentFingerprint)
                val roster=if(machineRosters==null)null else
                    (machineRosters.read(ownerKey) as? NativeMachineRosterResult.Saved)?.snapshot
                        ?:return@withOwnerTransaction null
                require((roster?.revision?:0L)==initial.machineRosterRevision &&
                    (roster==null || roster.ownerId==ownerKey))
                val stripped=stored.draft.weekOverrides.mapValues{(_,edits)->
                    edits.filterNot{edit->edit.sets.any{it.percentageIntent!=null}}}
                    .filterNot{(blockId,edits)->blockId==initial.blockId && edits.isEmpty()}
                val base=stored.draft.copy(revision=initial.stamp.cycleRevision,weekOverrides=stripped)
                val original=(CustomWorkoutSnapshotDomain.reviewForConsent(base,source.setup,
                    source.catalog,source.revision,inputs,selection.absoluteWeek,selection.weekday)
                    as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                    ?:return@withOwnerTransaction null
                require(original.stamp==initial.stamp &&
                    NativeKotlinCustomMachineBindingPolicy.review(ownerKey,original,source.catalog,roster)
                        is NativeCustomMachineBindingReview.Verified)
                val selected=original.slots.single{it.slotId==selection.slotId}
                val physical=NativeKotlinCustomSelectedStockDomain.resolve(source,selected,roster)
                    ?:return@withOwnerTransaction null
                val stock=physical.stock?:return@withOwnerTransaction null
                require(selected.exerciseId==initial.exerciseId &&
                    selected.machineInstanceId==initial.machineInstanceId &&
                    physical.machineInstanceId==initial.machineInstanceId &&
                    stock.unit==initial.unit && stock.basis==initial.basis)
                val initialRequest=TieredLinearRequest(selection.nextWorkoutId,scope,selection.tier,
                    TieredLinearState(0,selection.initialLoad),true,emptyList(),stock,
                    selection.maximumAuthorizedIncrease,
                    selected.sets.count{it.prescription.type==DraftSetType.WORKING},sourceSlotId=selection.slotId)
                var historicalCycle=stored.draft
                val prefixIds=mutableListOf(selection.nextWorkoutId)
                var priorRevision=initial.historyRevision
                for((index,old) in sorted.withIndex()){
                    val week=selection.absoluteWeek+index+1
                    require(old.selection.absoluteWeek==week &&
                        old.consentRevision==origin.consentRevision &&
                        old.consentFingerprint==origin.consentFingerprint &&
                        old.cycleRevision==stored.draft.revision &&
                        old.sourceRevision==initial.stamp.gymCatalogSourceRevision &&
                        old.initialState==TieredLinearState(0,selection.initialLoad) &&
                        old.maximumIncrease==selection.maximumAuthorizedIncrease &&
                        old.historyRevision>priorRevision &&
                        old.historyRevision<=timeline.manifestRevision)
                    val prefix=NativeTieredHistoryPrefixCodec.decode(old.prefix,scope,selection.tier,selection.slotId)
                    require(prefix.map{it.workoutId}==prefixIds && prefixIds.all{it in completed})
                    require(old.inputs==inputs && old.setBudget==initialRequest.approvedSetBudget)
                    val ledger=history.readTieredHistoryPrefixForRecovery(ownerKey,owner.athleteId,scope,
                        selection.slotId,selection.tier,week) as? NativeOwnerTieredLedgerResult.Review
                        ?:return@withOwnerTransaction null
                    require(ledger.manifestRevision==timeline.manifestRevision &&
                        ledger.exposures.size==prefix.size &&
                        prefix.zip(ledger.exposures).all{(saved,live)->
                            saved.copy(observations=live.observations)==live} &&
                        (old.historyRevision!=ledger.manifestRevision || prefix==ledger.exposures))
                    val request=initialRequest.copy(nextWorkoutId=old.selection.nextWorkoutId,
                        completed=prefix,recovery=old.selection.recovery)
                    val numeric=TieredLinearProgressionDomain.review(request)
                        as? TieredLinearResult.Candidate?:return@withOwnerTransaction null
                    require(needsNewTierResetConsent(request,numeric)==old.resetRequired)
                    val replay=CustomPercentageDraftDomain.reviewTiered(
                        CustomPercentageDraftSource(historicalCycle,
                            initial.stamp.copy(cycleRevision=historicalCycle.revision),
                            initial.blockId,initial.weekInBlock+index+1,selection.weekday,selection.slotId),
                        request) as? CustomPercentageDraftResult.Proposed?:return@withOwnerTransaction null
                    val proposed=replay.proposal.draft
                    require(NativeCustomCycleDraftCodec.encode(proposed).contentEquals(old.proposedCycle))
                    historicalCycle=proposed
                    val workoutId=old.selection.nextWorkoutId
                    val fact=census.sessions.singleOrNull{it.workoutId==workoutId}
                    if(fact!=null){
                        val prepared=outstanding.preparedOrigin(ownerKey,workoutId)
                            ?:return@withOwnerTransaction null
                        require(fact.cycleId==cycleId && fact.absoluteWeek==week &&
                            fact.weekday==selection.weekday &&
                            prepared.authority==NativeCustomPreparationAuthority.TIERED_TRANSITION &&
                            prepared.consentRevision==old.consentRevision &&
                            prepared.consentFingerprint==old.fingerprint &&
                            prepared.snapshot.stamp.cycleRevision==proposed.revision)
                    }
                    if(index<sorted.lastIndex)require(fact?.completed==true)
                    prefixIds+=workoutId
                    priorRevision=old.historyRevision
                }
            }
            // Missing receipt must be detected even when no matching file
            // exists at all. Completed and unfinished origins both count.
            for(fact in census.sessions){
                val prepared=outstanding.preparedOrigin(ownerKey,fact.workoutId)
                    ?:return@withOwnerTransaction null
                if(prepared.authority==NativeCustomPreparationAuthority.TIERED_TRANSITION)
                    require(records.any{it.selection.nextWorkoutId==fact.workoutId &&
                        it.fingerprint==prepared.consentFingerprint})
            }
            if(identity.signedInIdentity()!=owner)return@withOwnerTransaction null
            records.size
        }}.getOrNull()
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
            if(!writeReceipt(file,bytes,owner,exists))
                return@withOwnerTransaction NativeTieredTransitionConsentResult.NOT_AUTHORIZED
            if(exists)NativeTieredTransitionConsentResult.REPLACED else NativeTieredTransitionConsentResult.RECORDED
        }}.getOrElse{NativeTieredTransitionConsentResult.STORAGE_UNAVAILABLE}
    }
}
