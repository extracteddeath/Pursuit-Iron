package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.CustomTrainingCycle
import io.github.extracteddeath.pursuitiron.rebuild.CustomTrainingCycleDomain

/**
 * A completed historical workout is authenticated by the mixed history
 * manifest and independently re-opened from its original immutable custom
 * journal. Never build these entries from the screen, dates or cached counts.
 */
internal data class NativeVerifiedCustomBlockCompletion(
    val cycleId:String,val blockId:String,
    val absoluteWeek:Int,val weekday:Int
)

enum class NativeFutureActivationRefusal {
    NOT_AUTHORIZED, NO_ACCEPTED_FUTURE_CONTEXT, STALE_SOURCE_OR_DRAFT,
    NOT_A_FUTURE_BLOCK, INCOMPLETE_HISTORY, CONFLICTING_HISTORY,
    TARGET_BLOCK_ALREADY_STARTED, STORAGE_UNAVAILABLE
}

sealed interface NativeFutureActivationReview {
    /**
     * Evidence that earlier workouts were COMPLETED, not that there are no
     * live, issued or prepared sessions. This is never an activation grant.
     */
    data class BoundaryReviewed(
        val cycleId:String,
        val blockId:String,
        val firstWeek:Int,
        val precedingWeeks:Int,
        val verifiedPriorSessions:Int,
        val historyManifestRevision:Long
    ):NativeFutureActivationReview {
        val mayActivate:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
        val mayChangeSchedule:Boolean get()=false
    }
    data class Rejected(val reason:NativeFutureActivationRefusal):
        NativeFutureActivationReview
}

/** Deterministic full-cycle boundary check; no schedule or journal writes. */
internal object NativeFutureBlockBoundaryDomain {
    fun check(
        cycle:CustomTrainingCycle,
        targetBlockId:String,
        completions:List<NativeVerifiedCustomBlockCompletion>,
        manifestRevision:Long
    ):NativeFutureActivationReview {
        val index=cycle.blocks.indexOfFirst{it.blockId==targetBlockId}
        if(index<=0 || cycle.blocks.any{it.weeks<=0} ||
            cycle.blocks.map{it.blockId}.distinct().size!=cycle.blocks.size)
            return NativeFutureActivationReview.Rejected(
                NativeFutureActivationRefusal.NOT_A_FUTURE_BLOCK)
        val prior=cycle.blocks.take(index)
        val firstWeek=1+prior.sumOf{it.weeks}
        if(firstWeek !in 2..104 || manifestRevision<1)
            return NativeFutureActivationReview.Rejected(
                NativeFutureActivationRefusal.NOT_A_FUTURE_BLOCK)
        val own=completions.filter{it.cycleId==cycle.cycleId}
        if(own.any{it.absoluteWeek>=firstWeek})
            return NativeFutureActivationReview.Rejected(
                NativeFutureActivationRefusal.TARGET_BLOCK_ALREADY_STARTED)
        val expected=buildSet {
            var week=1
            for(block in prior)repeat(block.weeks) {
                for(day in block.days)add(Triple(week,block.blockId,day.weekday))
                week++
            }
        }
        // Duplicate or contradictory completed cycle-week/day entries are
        // unresolved, not permission to treat a future block as available.
        val completed=own.map{
            Triple(it.absoluteWeek,it.blockId,it.weekday)
        }
        if(completed.any{it !in expected} ||
            completed.distinct().size!=completed.size)
            return NativeFutureActivationReview.Rejected(
                NativeFutureActivationRefusal.CONFLICTING_HISTORY)
        if(!completed.toSet().containsAll(expected))
            return NativeFutureActivationReview.Rejected(
                NativeFutureActivationRefusal.INCOMPLETE_HISTORY)
        return NativeFutureActivationReview.BoundaryReviewed(
            cycle.cycleId,targetBlockId,firstWeek,firstWeek-1,
            completed.size,manifestRevision)
    }
}

/**
 * Owner-fenced read-only precursor to explicit future profile activation.
 * Uses durable future review evidence AND a complete verified history read.
 * It cannot prove an issued-but-unfinished workout does not exist, so even a
 * BoundaryReviewed result MUST NOT authorize source mutation or issuance.
 */
class NativeKotlinFutureBlockActivationPreflight(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val contexts:NativeKotlinFutureBlockContextFileStore,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val customJournals:NativeKotlinCustomWorkoutJournalFileStore
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    fun inspect(ownerKey:String,cycleId:String,blockId:String):
        NativeFutureActivationReview {
        if(!listOf(ownerKey,cycleId,blockId).all(id::matches))
            return NativeFutureActivationReview.Rejected(
                NativeFutureActivationRefusal.NOT_AUTHORIZED)
        return runCatching{
            fence.withOwnerTransaction(ownerKey){
                val owner=identity.signedInIdentity()?.takeIf{
                    it.ownerKey==ownerKey
                }?:return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                    NativeFutureActivationRefusal.NOT_AUTHORIZED)
                val future=contexts.read(ownerKey,cycleId,blockId)
                    as? NativeFutureBlockContextRead.Found
                    ?:return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                        NativeFutureActivationRefusal.NO_ACCEPTED_FUTURE_CONTEXT)
                val draft=(drafts.read(ownerKey,cycleId)
                    as? NativeCustomDraftReadResult.Found)?.draft
                    ?:return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                        NativeFutureActivationRefusal.STALE_SOURCE_OR_DRAFT)
                if(draft.athleteId!=owner.athleteId ||
                    draft.revision!=future.receipt.draftRevision)
                    return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                        NativeFutureActivationRefusal.STALE_SOURCE_OR_DRAFT)
                val block=draft.blocks.singleOrNull{it.blockId==blockId}
                    ?:return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                        NativeFutureActivationRefusal.NOT_A_FUTURE_BLOCK)
                val reviewed=future.receipt.future.setup.reviewed
                if(reviewed==null ||
                    block.days.map{it.weekday}.toSet()!=reviewed.trainingWeekdays ||
                    (block.split as? io.github.extracteddeath.pursuitiron.rebuild.CustomSplitIdentity.Named)
                        ?.split!=reviewed.split)
                    return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                        NativeFutureActivationRefusal.STALE_SOURCE_OR_DRAFT)
                val full=history.read(ownerKey,owner.athleteId)
                    as? NativeKotlinMixedHistoryReadResult.Review
                    ?:return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                        NativeFutureActivationRefusal.INCOMPLETE_HISTORY)
                val sessions=mutableListOf<NativeVerifiedCustomBlockCompletion>()
                for(completion in full.timeline.completions){
                    if(completion !is NativeKotlinMixedHistoryCompletion.Custom)
                        continue
                    val stored=customJournals.open(ownerKey,
                        completion.manifest.workoutId)
                        as? NativeCustomJournalResult.Durable
                        ?:return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                            NativeFutureActivationRefusal.INCOMPLETE_HISTORY)
                    val journal=stored.issued.journal
                    val snap=journal.snapshot
                    if(journal.finishedByEventId!=completion.evidence.finishedEventId ||
                        snap.stamp.athleteId!=owner.athleteId ||
                        snap.stamp.cycleId!=completion.evidence.cycleId ||
                        snap.stamp.cycleRevision!=completion.evidence.cycleRevision)
                        return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                            NativeFutureActivationRefusal.CONFLICTING_HISTORY)
                    sessions+=NativeVerifiedCustomBlockCompletion(
                        snap.stamp.cycleId,snap.blockId,
                        snap.absoluteWeek,snap.weekday)
                }
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction NativeFutureActivationReview.Rejected(
                        NativeFutureActivationRefusal.NOT_AUTHORIZED)
                NativeFutureBlockBoundaryDomain.check(
                    draft,blockId,sessions,full.timeline.manifestRevision)
            }
        }.getOrElse{
            NativeFutureActivationReview.Rejected(
                NativeFutureActivationRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
