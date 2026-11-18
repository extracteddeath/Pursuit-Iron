package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * Exact, owner-authenticated typed projection of measured completions from
 * the SAME enrolled canonical history manifest used by Composer and custom
 * workouts. This deliberately proves ONLY measured entries; other types
 * still require the full existing mixed reader. No guessed PRs, e1RMs, rep
 * conversions, elapsed meters, or automatic coaching changes.
 */
data class NativeMeasuredHistoryCompletion(
    val manifest:KotlinHistoryManifestEntry,
    val stored:NativeMeasuredWorkoutStored
) {
    val actualSets:List<MeasuredJournalOperation.Record> get()=
        stored.journal.approvedPlan.sets.mapNotNull {plan->
            stored.journal.bySetKey[plan.setKey]?.effectiveRecord
                ?.operation as? MeasuredJournalOperation.Record
        }
    val gymId:String get()=stored.journal.approvedPlan.gymId
    val mayIssueWorkout:Boolean get()=false
    val mayPublishPr:Boolean get()=false
    val mayApplyProgression:Boolean get()=false
}

data class NativeMeasuredHistoryReview(
    val athleteId:String,
    val manifestRevision:Long,
    val verifiedMeasured:List<NativeMeasuredHistoryCompletion>
) {
    /** A typed subset of the same ledger, never a complete History census. */
    val provesAllHistory:Boolean get()=false
    val mayPublishPr:Boolean get()=false
    val mayApplyProgression:Boolean get()=false
}

enum class NativeMeasuredHistoryRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, NOT_ENROLLED,
    PENDING_TRANSACTION, CORRUPT_LEDGER, UNVERIFIED_MEASUREMENT,
    UNSUPPORTED_TYPED_SCHEMA, MANIFEST_CHANGED, STORAGE_UNAVAILABLE
}
sealed interface NativeMeasuredHistoryReadResult {
    data class Review(val history:NativeMeasuredHistoryReview):
        NativeMeasuredHistoryReadResult
    data class Rejected(val reason:NativeMeasuredHistoryRefusal):
        NativeMeasuredHistoryReadResult
}

class NativeKotlinMeasuredHistoryReadCoordinator(
    private val canonical:NativeKotlinHistoryManifestFileStore,
    private val measured:NativeMeasuredWorkoutParentFileStore,
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val typedReceipt=Regex("^measured-v1-[a-f0-9]{64}$")

    private fun fail(reason:NativeMeasuredHistoryRefusal)=
        NativeMeasuredHistoryReadResult.Rejected(reason)

    private fun authorized(owner:String,athlete:String):Boolean =
        runCatching{identity.signedInIdentity()}.getOrNull() ==
            NativeKotlinOwnerIdentity(owner,athlete)

    fun read(
        owner:String,athlete:String
    ):NativeMeasuredHistoryReadResult {
        if(!id.matches(owner) || !id.matches(athlete))
            return fail(NativeMeasuredHistoryRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(owner) {
                if(!authorized(owner,athlete))
                    return@withOwnerTransaction fail(
                        NativeMeasuredHistoryRefusal.NOT_AUTHORIZED)
                val opened=canonical.open(owner,athlete)
                val ledger=(opened as? NativeHistoryLedgerResult.Durable)
                    ?.ledger?:return@withOwnerTransaction fail(
                        if((opened as? NativeHistoryLedgerResult.Rejected)?.reason==
                            NativeHistoryLedgerRefusal.NOT_ENROLLED)
                            NativeMeasuredHistoryRefusal.NOT_ENROLLED
                        else NativeMeasuredHistoryRefusal.CORRUPT_LEDGER)
                if(ledger.pending!=null)
                    return@withOwnerTransaction fail(
                        NativeMeasuredHistoryRefusal.PENDING_TRANSACTION)
                if(!ledger.readyForHistoryReads ||
                    ledger.ownerKey!=owner || ledger.athleteId!=athlete ||
                    ledger.revision<1 ||
                    ledger.completions.map{it.workoutId}.distinct().size!=
                        ledger.completions.size ||
                    ledger.completions.map{it.completionOrder}!=
                        (1L..ledger.completions.size.toLong()).toList())
                    return@withOwnerTransaction fail(
                        NativeMeasuredHistoryRefusal.CORRUPT_LEDGER)
                val facts=mutableListOf<NativeMeasuredHistoryCompletion>()
                for(entry in ledger.completions) {
                    if(!entry.storedReceiptId.startsWith("measured-"))
                        continue
                    if(!typedReceipt.matches(entry.storedReceiptId))
                        return@withOwnerTransaction fail(
                            NativeMeasuredHistoryRefusal.UNSUPPORTED_TYPED_SCHEMA)
                    val value=(measured.open(owner,entry.workoutId)
                        as? NativeMeasuredWorkoutStoreResult.Durable)
                        ?.stored ?:return@withOwnerTransaction fail(
                            NativeMeasuredHistoryRefusal.UNVERIFIED_MEASUREMENT)
                    val journal=value.journal
                    if(value.ownerKey!=owner || value.athleteId!=athlete ||
                        journal.approvedPlan.workoutId!=entry.workoutId ||
                        journal.revision!=entry.originalJournalRevision ||
                        journal.finishedByEventId!=entry.finishEventId ||
                        entry.savedCorrectionRevision!=0L ||
                        journal.bySetKey.size!=journal.approvedPlan.sets.size ||
                        journal.bySetKey.values.any{it.effectiveRecord==null} ||
                        runCatching{
                            NativeMeasuredWorkoutFinishReceipt.compute(value)
                        }.getOrNull()!=entry.storedReceiptId)
                        return@withOwnerTransaction fail(
                            NativeMeasuredHistoryRefusal.UNVERIFIED_MEASUREMENT)
                    facts+=NativeMeasuredHistoryCompletion(entry,value)
                }
                val fresh=canonical.open(owner,athlete)
                if(!authorized(owner,athlete))
                    return@withOwnerTransaction fail(
                        NativeMeasuredHistoryRefusal.NOT_AUTHORIZED)
                if(fresh !is NativeHistoryLedgerResult.Durable ||
                    fresh.ledger!=ledger)
                    return@withOwnerTransaction fail(
                        NativeMeasuredHistoryRefusal.MANIFEST_CHANGED)
                NativeMeasuredHistoryReadResult.Review(
                    NativeMeasuredHistoryReview(athlete,ledger.revision,
                        Collections.unmodifiableList(facts.toList())))
            }
        }.getOrElse{fail(NativeMeasuredHistoryRefusal.STORAGE_UNAVAILABLE)}
    }
}
