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
 * Durable, owner-locked Kotlin CUSTOM workout issuance and individual set
 * receipts. The snapshot is the exact immutable author-written session.
 *
 * A FINISH is deliberately NOT exposed: successful custom completion must
 * join a redo-backed history-manifest transaction before being publishable.
 * A plain journal Finish would leave an unindexed completed workout.
 *
 * This is an isolated native integration candidate; it is not wired to the
 * current JS-backed production UI or an uncertified history migration.
 */
enum class NativeCustomJournalRefusal {
    INVALID_ID, NOT_AUTHORIZED, NOT_PREPARED, CONSENT_CHANGED,
    HISTORY_NOT_READY, WORKOUT_ID_CONFLICT, NOT_ISSUED,
    CORRUPT_JOURNAL, STALE_OR_INVALID_SET, FINISH_REQUIRES_HISTORY_TRANSACTION,
    STORAGE_UNAVAILABLE
}
data class NativeCustomIssuedJournal(
    val ownerKey: String,
    val athleteId: String,
    val consentRevision: Long,
    val journal: CustomWorkoutJournalDraft
) {
    val isIssued: Boolean get() = true
    val mayFinishWithoutHistoryTransaction: Boolean get() = false
}
sealed interface NativeCustomJournalResult {
    data class Durable(
        val issued: NativeCustomIssuedJournal,
        val replayed: Boolean
    ): NativeCustomJournalResult
    data class Rejected(
        val reason: NativeCustomJournalRefusal,
        val journalReason: CustomWorkoutJournalRefusal? = null
    ): NativeCustomJournalResult
}

/**
 * All operations acquire the same per-owner OS fence as the native gym,
 * cycle, signoff and prepared-snapshot store. No cross-account path is
 * accepted and no unverified UI-provided snapshot can become issued.
 */
class NativeKotlinCustomWorkoutJournalFileStore(
    private val root: Path,
    private val fence: NativeKotlinApprovalRevisionFence,
    private val identity: NativeKotlinCurrentIdentity,
    private val prepared: NativeKotlinCustomWorkoutPreparedStore,
    private val preflight: NativeKotlinCustomWorkoutPreflight,
    private val history: NativeKotlinHistoryManifestFileStore,
    /** Prevent collisions with the existing Composer workout journal. */
    private val composerJournals: KotlinNativeJournalFileStore,
    private val activationHandoff:NativeKotlinFutureActivationHandoffFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val magic=0x50494349 // PICI
    private val version=1
    private val MAX_BYTES=NativeCustomWorkoutJournalCodec.MAX_BYTES+4096

    private fun owner(ownerKey:String):NativeKotlinOwnerIdentity? {
        if(!id.matches(ownerKey))return null
        return runCatching {identity.signedInIdentity()}.getOrNull()
            ?.takeIf {it.ownerKey==ownerKey && id.matches(it.athleteId)}
    }
    private fun path(ownerKey:String,workoutId:String):Path {
        val digest=MessageDigest.getInstance("SHA-256").digest(
            (ownerKey+"\u0000"+workoutId).toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it.toInt() and 255)}
        return root.resolve("issued-custom-$digest.bin")
    }
    private fun encode(record:NativeCustomIssuedJournal):ByteArray {
        require(record.journal.snapshot.stamp.athleteId==record.athleteId)
        require(record.consentRevision>0)
        val contents=NativeCustomWorkoutJournalCodec.encode(
            NativeCustomWorkoutJournalRecord(record.ownerKey,record.journal))
        val payload=ByteArrayOutputStream()
        DataOutputStream(payload).use{out->
            out.writeInt(magic);out.writeInt(version)
            out.writeUTF(record.ownerKey);out.writeUTF(record.athleteId)
            out.writeLong(record.consentRevision)
            out.writeInt(contents.size);out.write(contents)
        }
        val body=payload.toByteArray()
        require(body.size+36<=MAX_BYTES)
        return ByteArrayOutputStream().also{bytes->
            DataOutputStream(bytes).use{out->
                out.writeInt(body.size);out.write(body)
                out.write(MessageDigest.getInstance("SHA-256").digest(body))
            }
        }.toByteArray()
    }
    private fun decode(file:Path):NativeCustomIssuedJournal? {
        if(!Files.exists(file))return null
        if(Files.isSymbolicLink(file) || !Files.isRegularFile(file) ||
            Files.size(file) !in 44L..MAX_BYTES.toLong())
            throw IOException("invalid custom issued file")
        val bytes=Files.readAllBytes(file)
        val envelope=DataInputStream(ByteArrayInputStream(bytes))
        val length=envelope.readInt()
        if(length !in 16..(MAX_BYTES-36) || length+36!=bytes.size)
            throw IOException("invalid custom journal envelope")
        val payload=ByteArray(length)
        envelope.readFully(payload)
        val expected=ByteArray(32)
        envelope.readFully(expected)
        if(!MessageDigest.isEqual(expected,
            MessageDigest.getInstance("SHA-256").digest(payload)))
            throw IOException("issued custom journal checksum mismatch")
        return try {
            DataInputStream(ByteArrayInputStream(payload)).use{input->
                if(input.readInt()!=magic || input.readInt()!=version)
                    throw IOException("unknown custom journal file version")
                val owner=input.readUTF()
                val athlete=input.readUTF()
                val consentRevision=input.readLong()
                val size=input.readInt()
                if(!id.matches(owner) || !id.matches(athlete) ||
                    consentRevision<1 || size !in 16..NativeCustomWorkoutJournalCodec.MAX_BYTES ||
                    size>input.available())
                    throw IOException("invalid custom journal metadata")
                val raw=ByteArray(size);input.readFully(raw)
                if(input.available()!=0)
                    throw IOException("trailing issued workout bytes")
                val journal=NativeCustomWorkoutJournalCodec.decode(raw)
                if(journal.ownerKey!=owner ||
                    journal.journal.snapshot.stamp.athleteId!=athlete)
                    throw IOException("journal owner/athlete mismatch")
                NativeCustomIssuedJournal(owner,athlete,
                    consentRevision,journal.journal)
            }
        }catch(ex:Exception){
            throw IOException("unreadable issued custom journal",ex)
        }
    }
    private fun atomic(file:Path,bytes:ByteArray) {
        Files.createDirectories(root)
        if(Files.isSymbolicLink(root))
            throw IOException("unsafe custom journal directory")
        val temp=Files.createTempFile(root,"issued-custom-", ".pending")
        try{
            FileChannel.open(temp,StandardOpenOption.WRITE,
                StandardOpenOption.TRUNCATE_EXISTING).use{channel->
                val buffer=ByteBuffer.wrap(bytes)
                while(buffer.hasRemaining())channel.write(buffer)
                channel.force(true)
            }
            try{
                Files.move(temp,file,StandardCopyOption.ATOMIC_MOVE,
                    StandardCopyOption.REPLACE_EXISTING)
            }catch(ex:AtomicMoveNotSupportedException){
                throw IOException("atomic journal replacement unavailable",ex)
            }
        }finally{Files.deleteIfExists(temp)}
    }
    private fun read(
        ownerKey:String,workoutId:String,
        authenticated:NativeKotlinOwnerIdentity
    ):NativeCustomJournalResult {
        val loaded=try{decode(path(ownerKey,workoutId))}
            catch(_:Exception){return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.CORRUPT_JOURNAL)}
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_ISSUED)
        if(loaded.ownerKey!=ownerKey ||
            loaded.athleteId!=authenticated.athleteId ||
            loaded.journal.workoutId!=workoutId)
            return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_AUTHORIZED)
        val plan=prepared.open(ownerKey,workoutId)
        val actual=(plan as? NativeCustomPreparedRead.Found)?.workout
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_PREPARED)
        if(actual.athleteId!=loaded.athleteId ||
            actual.consentRevision!=loaded.consentRevision ||
            actual.consentFingerprint!=loaded.journal.preparedConsentFingerprint ||
            actual.snapshot!=loaded.journal.snapshot)
            return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.CORRUPT_JOURNAL)
        return NativeCustomJournalResult.Durable(loaded,true)
    }
    fun open(ownerKey:String,workoutId:String):NativeCustomJournalResult {
        if(!id.matches(workoutId))
            return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.INVALID_ID)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val current=owner(ownerKey)
                    ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.NOT_AUTHORIZED)
                read(ownerKey,workoutId,current)
            }
        }.getOrElse {NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * The ONLY path that upgrades a merely prepared snapshot to issued.
     * Existing identical sessions may replay after later program edits, but
     * a first issuance requires current consent, full re-audit and already
     * enrolled, non-pending native history. A prepared session is NEVER an
     * authorization token by itself.
     */
    fun issue(
        ownerKey:String,workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs
    ):NativeCustomJournalResult {
        if(!id.matches(workoutId)||!id.matches(cycleId) ||
            absoluteWeek !in 1..104 || weekday !in 0..6)
            return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.INVALID_ID)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val current=owner(ownerKey)
                    ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.NOT_AUTHORIZED)
                val currentRecord=read(ownerKey,workoutId,current)
                if(currentRecord is NativeCustomJournalResult.Durable) {
                    // Even historical replay is scoped to the ORIGINAL
                    // consent type; never cross-call into activated authority.
                    val origin=(prepared.open(ownerKey,workoutId)
                        as? NativeCustomPreparedRead.Found)?.workout
                    val snap=currentRecord.issued.journal.snapshot
                    return@withOwnerTransaction if(origin?.authority==
                            NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
                        snap.stamp.cycleId==cycleId &&
                        snap.absoluteWeek==absoluteWeek && snap.weekday==weekday)
                        currentRecord
                    else NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.WORKOUT_ID_CONFLICT)
                }
                if((currentRecord as NativeCustomJournalResult.Rejected).reason!=
                    NativeCustomJournalRefusal.NOT_ISSUED)
                    return@withOwnerTransaction currentRecord
                // Existing durable sessions can replay above, but no NEW
                // issuance may cross a staged future-source transition.
                if(activationHandoff?.blocksNewIssuance(ownerKey)==true)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.CONSENT_CHANGED)
                val plan=prepared.open(ownerKey,workoutId)
                val selected=(plan as? NativeCustomPreparedRead.Found)?.workout
                    ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.NOT_PREPARED)
                // A future-block preparation is a different consent protocol.
                // It must never be laundered into legacy custom issuance,
                // even after a later activation handoff has been cleared.
                if(selected.authority!=NativeCustomPreparationAuthority.CUSTOM_CYCLE)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.CONSENT_CHANGED)
                if(selected.athleteId!=current.athleteId ||
                    selected.snapshot.stamp.cycleId!=cycleId ||
                    selected.snapshot.absoluteWeek!=absoluteWeek ||
                    selected.snapshot.weekday!=weekday)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.WORKOUT_ID_CONFLICT)
                val ledger=history.open(ownerKey,current.athleteId)
                if(ledger !is NativeHistoryLedgerResult.Durable ||
                    !ledger.ledger.readyForHistoryReads ||
                    ledger.ledger.ownerKey!=ownerKey ||
                    ledger.ledger.athleteId!=current.athleteId ||
                    ledger.ledger.completions.any{it.workoutId==workoutId})
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.HISTORY_NOT_READY)
                // An ID cannot be claimed both by custom and composer journals.
                val other=composerJournals.openStored(ownerKey,workoutId) {
                    owner, source -> owner==ownerKey &&
                        source.athleteId==current.athleteId
                }
                if(other !is NativeJournalFileResult.Rejected ||
                    other.reason!=NativeJournalFileRefusal.NOT_ISSUED)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.WORKOUT_ID_CONFLICT)
                val pre=preflight.inspect(ownerKey,cycleId,
                    absoluteWeek,weekday,inputs)
                if(pre !is NativeCustomPreflightResult.ReviewedPreview ||
                    pre.receipt.recordRevision!=selected.consentRevision ||
                    pre.receipt.fingerprintSha256!=selected.consentFingerprint ||
                    pre.session!=selected.snapshot)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.CONSENT_CHANGED)
                if(owner(ownerKey)!=current)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.NOT_AUTHORIZED)
                val created=NativeCustomIssuedJournal(
                    ownerKey,current.athleteId,selected.consentRevision,
                    CustomWorkoutJournalDomain.begin(workoutId,
                        selected.snapshot,selected.consentFingerprint))
                atomic(path(ownerKey,workoutId),encode(created))
                NativeCustomJournalResult.Durable(created,false)
            }
        }.getOrElse {
            NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Isolated NEW authority for a previously activated future custom block.
     * No global barrier release: ONLY a precisely signed, same-block,
     * immutable preparation may issue while other new issuers stay blocked.
     * History and identity are rechecked at the point of durable issuance.
     *
     * The separate store provenance prevents a legacy custom consent from
     * crossing this path. Already-issued journal replay keeps its historical
     * snapshot and requires no retroactive rewrite after later plan changes.
     */
    fun issueActivated(
        ownerKey:String,workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,inputs:CustomCoachAuditInputs,
        signoffs:NativeKotlinActivatedCycleSignoffFileStore,
        activation:NativeKotlinFutureActivationCommitTransaction,
        repApprovals:NativeKotlinCustomNextRepApprovalFileStore?=null
    ):NativeCustomJournalResult {
        if(!id.matches(workoutId)||!id.matches(cycleId) ||
            absoluteWeek !in 1..104 || weekday !in 0..6)
            return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.INVALID_ID)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val current=owner(ownerKey)
                ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.NOT_AUTHORIZED)
            val existing=read(ownerKey,workoutId,current)
            if(existing is NativeCustomJournalResult.Durable) {
                val preparedWorkout=(prepared.open(ownerKey,workoutId)
                    as? NativeCustomPreparedRead.Found)?.workout
                val snapshot=existing.issued.journal.snapshot
                return@withOwnerTransaction if(preparedWorkout?.authority==
                        NativeCustomPreparationAuthority.ACTIVATED_BLOCK &&
                    snapshot.stamp.cycleId==cycleId &&
                    snapshot.absoluteWeek==absoluteWeek &&
                    snapshot.weekday==weekday)
                    existing
                else NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.WORKOUT_ID_CONFLICT)
            }
            if((existing as NativeCustomJournalResult.Rejected).reason!=
                NativeCustomJournalRefusal.NOT_ISSUED)
                return@withOwnerTransaction existing
            val staged=(activationHandoff?.read(ownerKey)
                as? NativeFutureHandoffReview.Staged)?.receipt
                ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.CONSENT_CHANGED)
            val accepted=activation.read(ownerKey)
                as? NativeFutureCommitReview.Applied
                ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.CONSENT_CHANGED)
            val consent=(signoffs.read(ownerKey,inputs)
                as? NativeActivatedSignoffRead.Found)?.takeIf{it.isCurrent}
                ?.receipt
                ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.CONSENT_CHANGED)
            if(staged.cycleId!=cycleId || staged.blockId!=consent.blockId ||
                staged.firstWeek!=consent.firstWeek ||
                accepted.context.cycleId!=cycleId ||
                accepted.context.blockId!=consent.blockId ||
                accepted.context.sourceRevision!=consent.sourceRevision ||
                consent.athleteId!=current.athleteId ||
                consent.ownerKey!=ownerKey || absoluteWeek<consent.firstWeek)
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.CONSENT_CHANGED)
            val selected=(prepared.open(ownerKey,workoutId)
                as? NativeCustomPreparedRead.Found)?.workout
                ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.NOT_PREPARED)
            if(selected.snapshot.stamp.cycleId!=cycleId ||
                selected.snapshot.absoluteWeek!=absoluteWeek ||
                selected.snapshot.weekday!=weekday)
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.WORKOUT_ID_CONFLICT)
            if(selected.authority!=
                    NativeCustomPreparationAuthority.ACTIVATED_BLOCK ||
                selected.ownerKey!=ownerKey ||
                selected.athleteId!=current.athleteId ||
                selected.consentRevision!=consent.recordRevision ||
                selected.consentFingerprint!=consent.reviewFingerprint ||
                selected.snapshot.stamp.cycleId!=cycleId ||
                selected.snapshot.stamp.gymCatalogSourceRevision!=
                    consent.sourceRevision ||
                selected.snapshot.blockId!=consent.blockId ||
                selected.snapshot.absoluteWeek!=absoluteWeek ||
                selected.snapshot.weekday!=weekday)
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.CONSENT_CHANGED)
            val exact=activation.previewActivatedWorkout(ownerKey,inputs,
                absoluteWeek,weekday) as? CustomWorkoutSnapshotResult.Preview
                ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.CONSENT_CHANGED)
            // Base-cycle signoff alone cannot authorize a changed rep band.
            // Independently redo the athlete's rep amendment from verified
            // history. A corrupt/stale receipt must never silently fall back
            // to an unchanged or forged prepared snapshot.
            val repApproval=repApprovals?.readCurrent(ownerKey,workoutId,
                inputs,absoluteWeek,weekday) ?:NativeCustomNextRepRead.Missing
            if(repApproval is NativeCustomNextRepRead.Rejected)
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.CONSENT_CHANGED)
            val amended=repApproval as? NativeCustomNextRepRead.Current
            if(selected.repApprovalFingerprint!=amended?.receipt?.fingerprint ||
                (amended?.amendment?.amended ?:exact.snapshot)!=selected.snapshot)
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.CONSENT_CHANGED)
            val ledger=history.open(ownerKey,current.athleteId)
            if(ledger !is NativeHistoryLedgerResult.Durable ||
                !ledger.ledger.readyForHistoryReads ||
                ledger.ledger.ownerKey!=ownerKey ||
                ledger.ledger.athleteId!=current.athleteId ||
                // A reset/replaced manifest must not satisfy a future-block
                // approval that was signed against a later verified boundary.
                ledger.ledger.revision<staged.historyManifestRevision ||
                ledger.ledger.completions.size<staged.firstWeek-1 ||
                ledger.ledger.completions.any{it.workoutId==workoutId})
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.HISTORY_NOT_READY)
            val other=composerJournals.openStored(ownerKey,workoutId){
                owner,source->owner==ownerKey &&
                    source.athleteId==current.athleteId
            }
            if(other !is NativeJournalFileResult.Rejected ||
                other.reason!=NativeJournalFileRefusal.NOT_ISSUED)
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.WORKOUT_ID_CONFLICT)
            if(owner(ownerKey)!=current)
                return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                    NativeCustomJournalRefusal.NOT_AUTHORIZED)
            val issued=NativeCustomIssuedJournal(ownerKey,current.athleteId,
                selected.consentRevision,
                CustomWorkoutJournalDomain.begin(workoutId,
                    selected.snapshot,selected.consentFingerprint))
            atomic(path(ownerKey,workoutId),encode(issued))
            NativeCustomJournalResult.Durable(issued,false)
        }}.getOrElse{NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Durable actual set / amplifier receipt; explicitly excludes Finish.
     * The shared history transaction coordinator commits Finish through
     * manifest redo before making any completed session visible.
     */
    fun record(
        ownerKey:String,workoutId:String,
        operation:CustomWorkoutJournalOperation.Record
    ):NativeCustomJournalResult {
        if(!id.matches(workoutId))
            return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.INVALID_ID)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val current=owner(ownerKey)
                    ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.NOT_AUTHORIZED)
                val existing=read(ownerKey,workoutId,current)
                if(existing !is NativeCustomJournalResult.Durable)
                    return@withOwnerTransaction existing
                val ledger=history.open(ownerKey,current.athleteId)
                if(ledger !is NativeHistoryLedgerResult.Durable ||
                    !ledger.ledger.readyForHistoryReads)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.HISTORY_NOT_READY)
                val decision=CustomWorkoutJournalDomain.reduce(
                    existing.issued.journal,operation)
                if(decision !is CustomWorkoutJournalDecision.Proposed)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.STALE_OR_INVALID_SET,
                        (decision as CustomWorkoutJournalDecision.Rejected).reason)
                if(decision.replayed)return@withOwnerTransaction existing
                if(owner(ownerKey)!=current)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.NOT_AUTHORIZED)
                val saved=existing.issued.copy(journal=decision.draft)
                atomic(path(ownerKey,workoutId),encode(saved))
                NativeCustomJournalResult.Durable(saved,false)
            }
        }.getOrElse {NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.STORAGE_UNAVAILABLE)}
    }
    /**
     * Internal HALF of a redo-backed Finish. The shared ledger must ALREADY
     * contain this exact CustomFinish intent, written under the same owner
     * fence. This cannot be called as an ordinary UI Finish action.
     *
     * A process death after the journal commit but before manifest finalize
     * leaves the durable intent, and retrying the identical event returns the
     * identical journal. Other set edits refuse while history is pending.
     */
    internal fun applyPendingFinish(
        ownerKey:String,workoutId:String,
        operation:CustomWorkoutJournalOperation.Finish
    ):NativeCustomJournalResult {
        if(!id.matches(workoutId))
            return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.INVALID_ID)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val current=owner(ownerKey)
                    ?:return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.NOT_AUTHORIZED)
                val ledger=history.open(ownerKey,current.athleteId)
                if(ledger !is NativeHistoryLedgerResult.Durable ||
                    ledger.ledger.pending!=NativeHistoryWriteIntent.CustomFinish(
                        workoutId,operation))
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.HISTORY_NOT_READY)
                val previous=read(ownerKey,workoutId,current)
                if(previous !is NativeCustomJournalResult.Durable)
                    return@withOwnerTransaction previous
                val decision=CustomWorkoutJournalDomain.reduce(
                    previous.issued.journal,operation)
                if(decision !is CustomWorkoutJournalDecision.Proposed)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.STALE_OR_INVALID_SET,
                        (decision as CustomWorkoutJournalDecision.Rejected).reason)
                if(decision.replayed)
                    return@withOwnerTransaction previous
                if(owner(ownerKey)!=current)
                    return@withOwnerTransaction NativeCustomJournalResult.Rejected(
                        NativeCustomJournalRefusal.NOT_AUTHORIZED)
                val saved=previous.issued.copy(journal=decision.draft)
                atomic(path(ownerKey,workoutId),encode(saved))
                NativeCustomJournalResult.Durable(saved,false)
            }
        }.getOrElse {NativeCustomJournalResult.Rejected(
            NativeCustomJournalRefusal.STORAGE_UNAVAILABLE)}
    }


    /**
     * Enumerate issued custom journals, not merely those listed as already
     * finished in the history manifest. Orphan, renamed and corrupt journal
     * files MUST NOT disappear from the activation gate.
     */
    internal fun inventory(ownerKey:String):NativeIssuedInventoryResult {
        fun fail(reason:NativeCustomInventoryRefusal)=
            NativeIssuedInventoryResult.Rejected(reason)
        if(owner(ownerKey)==null)
            return fail(NativeCustomInventoryRefusal.NOT_AUTHORIZED)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val signed=owner(ownerKey)
                    ?:return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.NOT_AUTHORIZED)
                if(!Files.exists(root))
                    return@withOwnerTransaction NativeIssuedInventoryResult.Found(
                        emptyList())
                if(Files.isSymbolicLink(root))
                    return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                val files=Files.list(root).use{stream->
                    stream.filter{it.fileName.toString().startsWith(
                        "issued-custom-")}.toList()
                }
                if(files.size>4096)
                    return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.TOO_MANY_FILES)
                val filename=Regex("^issued-custom-[a-f0-9]{64}\\.bin$")
                val found=mutableListOf<NativeCustomIssuedJournal>()
                for(file in files) {
                    if(!filename.matches(file.fileName.toString()) ||
                        !Files.isRegularFile(file) || Files.isSymbolicLink(file))
                        return@withOwnerTransaction fail(
                            NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                    val data=try{decode(file)}catch(_:Exception){
                        return@withOwnerTransaction fail(
                            NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                    }?:return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                    if(path(data.ownerKey,data.journal.workoutId)!=file)
                        return@withOwnerTransaction fail(
                            NativeCustomInventoryRefusal.CORRUPT_OR_UNRECOGNIZED_FILE)
                    if(data.ownerKey==ownerKey) {
                        if(data.athleteId!=signed.athleteId)
                            return@withOwnerTransaction fail(
                                NativeCustomInventoryRefusal.NOT_AUTHORIZED)
                        val verified=read(ownerKey,data.journal.workoutId,signed)
                        if(verified !is NativeCustomJournalResult.Durable ||
                            verified.issued!=data)
                            return@withOwnerTransaction fail(
                                NativeCustomInventoryRefusal.ISSUED_CONTENT_MISMATCH)
                        found+=data
                    }
                }
                if(owner(ownerKey)!=signed)
                    return@withOwnerTransaction fail(
                        NativeCustomInventoryRefusal.NOT_AUTHORIZED)
                NativeIssuedInventoryResult.Found(found.toList())
            }
        }.getOrElse{fail(NativeCustomInventoryRefusal.STORAGE_UNAVAILABLE)}
    }

}
