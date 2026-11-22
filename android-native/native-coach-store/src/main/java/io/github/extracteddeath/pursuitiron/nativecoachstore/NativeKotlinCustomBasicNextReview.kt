package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

enum class NativeCustomBasicNextRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, CONSENT_OR_DRAFT_UNAVAILABLE,
    CURRENT_GYM_UNAVAILABLE, MACHINE_ROSTER_UNAVAILABLE,
    VERIFIED_WORKOUT_UNAVAILABLE, STORAGE_UNAVAILABLE
}

sealed interface NativeCustomBasicNextReadResult {
    data class Rejected(val reason:NativeCustomBasicNextRefusal):
        NativeCustomBasicNextReadResult
    data class HistoryRejected(val reason:NativeKotlinMixedHistoryRefusal):
        NativeCustomBasicNextReadResult
    data class Reviewed(
        val historyRevision:Long,
        val approvedConsentRevision:Long,
        val next:CustomWorkoutDaySnapshot,
        val projection:CustomBasicHistoryResult
    ):NativeCustomBasicNextReadResult {
        val mayIssueWorkout:Boolean get()=false
        val mayModifyHistory:Boolean get()=false
        val maySaveProgram:Boolean get()=false
    }
}

/**
 * Read-only owner-fenced basic next-workout candidate. Unlike optional
 * fatigue coaching, Manual/basic progression remains available when an
 * athlete turns coaching assistance off. Nothing here writes future sets.
 *
 * Every request reopens signed cycle consent and current gym sources,
 * the complete corrected history ledger, and each original issued custom
 * workout. It selects one physical implement through the same authority
 * as native custom coaching and delegates ALL numbers to the pure basic
 * progression engine. UI-supplied workout identifiers are never grants.
 */
class NativeKotlinCustomBasicNextReview(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val preflight:NativeKotlinCustomWorkoutPreflight,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val journals:NativeKotlinCustomWorkoutJournalFileStore,
    private val machineRosters:NativeKotlinMachineRosterFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    fun reviewAuthored(
        ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        absoluteWeek:Int,weekday:Int,slotId:String,
        nextWorkoutId:String,maximumAuthorizedIncrement:BigDecimal
    ):NativeCustomBasicNextReadResult {
        fun refuse(reason:NativeCustomBasicNextRefusal)=
            NativeCustomBasicNextReadResult.Rejected(reason)
        if(listOf(ownerKey,cycleId,slotId,nextWorkoutId).any{!id.matches(it)} ||
            absoluteWeek !in 1..104 || weekday !in 0..6)
            return refuse(NativeCustomBasicNextRefusal.INVALID_SCOPE)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val owner=identity.signedInIdentity()?.takeIf{
                    it.ownerKey==ownerKey
                }?:return@withOwnerTransaction refuse(
                    NativeCustomBasicNextRefusal.NOT_AUTHORIZED)
                val reviewed=preflight.inspect(ownerKey,cycleId,
                    absoluteWeek,weekday,inputs)
                    as? NativeCustomPreflightResult.ReviewedPreview
                    ?:return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.CONSENT_OR_DRAFT_UNAVAILABLE)
                val cycle=(drafts.read(ownerKey,cycleId)
                    as? NativeCustomDraftReadResult.Found)?.draft
                    ?:return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.CONSENT_OR_DRAFT_UNAVAILABLE)
                val source=(sources.read(ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.CURRENT_GYM_UNAVAILABLE)
                val next=reviewed.session
                val gym=source.setup.reviewed?.gym
                    ?:return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.CURRENT_GYM_UNAVAILABLE)
                if(cycle.athleteId!=owner.athleteId ||
                    cycle.cycleId!=cycleId ||
                    cycle.revision!=next.stamp.cycleRevision ||
                    source.ownerKey!=ownerKey ||
                    source.athleteId!=owner.athleteId ||
                    source.revision!=next.stamp.gymCatalogSourceRevision ||
                    next.stamp.cycleId!=cycleId ||
                    next.stamp.athleteId!=owner.athleteId ||
                    gym.gymId!=next.stamp.gymId)
                    return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.CONSENT_OR_DRAFT_UNAVAILABLE)
                val selected=next.slots.singleOrNull{it.slotId==slotId}
                    ?:return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.VERIFIED_WORKOUT_UNAVAILABLE)
                val roster=when {
                    machineRosters==null -> null
                    else -> (machineRosters.read(ownerKey)
                        as? NativeMachineRosterResult.Saved)?.snapshot
                        ?:return@withOwnerTransaction refuse(
                            NativeCustomBasicNextRefusal.MACHINE_ROSTER_UNAVAILABLE)
                }
                val physical=NativeKotlinCustomSelectedStockDomain.resolve(
                    source,selected,roster)
                    ?:return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.CURRENT_GYM_UNAVAILABLE)
                val fetched=history.read(ownerKey,owner.athleteId)
                val full=fetched as? NativeKotlinMixedHistoryReadResult.Review
                    ?:return@withOwnerTransaction when(fetched) {
                        is NativeKotlinMixedHistoryReadResult.Rejected ->
                            NativeCustomBasicNextReadResult.HistoryRejected(
                                fetched.reason)
                        else -> refuse(
                            NativeCustomBasicNextRefusal.VERIFIED_WORKOUT_UNAVAILABLE)
                    }
                if(full.timeline.completions.any {
                    it.manifest.workoutId==nextWorkoutId
                })
                    return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.INVALID_SCOPE)
                val completed=ArrayList<CustomCoachingHistoricalSession>()
                for(entry in full.timeline.completions) {
                    if(entry !is NativeKotlinMixedHistoryCompletion.Custom)
                        continue
                    val stored=(journals.open(ownerKey,entry.manifest.workoutId)
                        as? NativeCustomJournalResult.Durable)?.issued?.journal
                        ?:return@withOwnerTransaction refuse(
                            NativeCustomBasicNextRefusal.VERIFIED_WORKOUT_UNAVAILABLE)
                    val old=stored.snapshot
                    if(stored.workoutId!=entry.evidence.workoutId ||
                        old.stamp.athleteId!=owner.athleteId ||
                        old.stamp.cycleId!=entry.evidence.cycleId ||
                        old.stamp.cycleRevision!=entry.evidence.cycleRevision ||
                        old.stamp.gymId!=entry.gymId)
                        return@withOwnerTransaction refuse(
                            NativeCustomBasicNextRefusal.VERIFIED_WORKOUT_UNAVAILABLE)
                    completed+=CustomCoachingHistoricalSession(
                        entry.manifest.completionOrder,old,entry.evidence)
                }
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.NOT_AUTHORIZED)
                // Delegate increment eligibility to the ONE numerical engine.
                // Unloaded rep progression, rep-only and manual logging do not
                // require a positive physical weight step. A weighted increase
                // will still refuse zero/invalid stock inside its own policy.
                val candidate=CustomBasicHistoryProgressionDomain.review(
                    CustomBasicHistoryRequest(
                        cycle,next,nextWorkoutId,slotId,completed,
                        physical.machineInstanceId,physical.stock,
                        maximumAuthorizedIncrement,
                        reviewedCatalog = source.catalog.associateBy { it.exerciseId },
                        experience = source.setup.reviewed?.experience))
                NativeCustomBasicNextReadResult.Reviewed(
                    full.timeline.manifestRevision,
                    reviewed.receipt.recordRevision,next,candidate)
            }
        }.getOrElse {
            refuse(NativeCustomBasicNextRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
