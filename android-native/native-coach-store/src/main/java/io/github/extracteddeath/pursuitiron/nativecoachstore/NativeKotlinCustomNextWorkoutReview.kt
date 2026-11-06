package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

enum class NativeCustomNextCoachRefusal {
    INVALID_SCOPE, NOT_AUTHORIZED, ACTIVATED_CONSENT_UNAVAILABLE,
    NEXT_WORKOUT_UNAVAILABLE, CURRENT_GYM_UNAVAILABLE,
    HISTORY_CHANGED_OR_UNVERIFIED, ASSISTANCE_DISABLED,
    COACH_SETTINGS_UNAVAILABLE, STORAGE_UNAVAILABLE
}
sealed interface NativeCustomNextCoachResult {
    data class Reviewed(
        val historyRevision:Long,
        val approvedConsentRevision:Long,
        val review:CustomNextWorkoutCoachingReview,
        /** Same owner-fenced, source-verified inventory used by the review. */
        val verifiedStock:VerifiedLoadStock?
    ):NativeCustomNextCoachResult {
        /** Display-only: re-review after subsequent Finish/correction. */
        val mayIssueWorkout:Boolean get()=false
    }
    data class HistoryRejected(val reason:NativeKotlinMixedHistoryRefusal):
        NativeCustomNextCoachResult
    data class Rejected(val reason:NativeCustomNextCoachRefusal):
        NativeCustomNextCoachResult
}

/**
 * Trusted native read owner for CUSTOM, ACTIVATED-BLOCK progression.
 *
 * Unlike accepting two UI-supplied good sessions, it reopens the ENTIRE
 * mixed native history including all custom saved corrections, then reads
 * the true immutable snapshot of each custom session. A single owner OS
 * fence covers consent, current gym, history and coaching; the result is
 * diagnostics only, NEVER new weight/reps in the issued workout.
 */
class NativeKotlinCustomNextWorkoutReview(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val activation:NativeKotlinFutureActivationCommitTransaction,
    private val signoffs:NativeKotlinActivatedCycleSignoffFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val journals:NativeKotlinCustomWorkoutJournalFileStore,
    private val assistance:NativeKotlinCoachAssistanceSettingsStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    fun review(
        ownerKey:String,inputs:CustomCoachAuditInputs,
        nextAbsoluteWeek:Int,nextWeekday:Int,slotId:String,
        readiness:CoachReadiness,movementRestricted:Boolean,
        maximumAuthorizedIncrease:BigDecimal
    ):NativeCustomNextCoachResult {
        fun reject(reason:NativeCustomNextCoachRefusal)=
            NativeCustomNextCoachResult.Rejected(reason)
        if(!id.matches(ownerKey) || !id.matches(slotId) ||
            nextAbsoluteWeek !in 1..104 || nextWeekday !in 0..6)
            return reject(NativeCustomNextCoachRefusal.INVALID_SCOPE)
        return runCatching{fence.withOwnerTransaction(ownerKey){
            val current=identity.signedInIdentity()?.takeIf{
                it.ownerKey==ownerKey
            }?:return@withOwnerTransaction reject(
                NativeCustomNextCoachRefusal.NOT_AUTHORIZED)
            val preference=if(assistance==null)
                CoachAssistancePolicy.STANDARD else
                (assistance.read(ownerKey) as? NativeCoachSettingsRead.Current)
                    ?.settings?.policy
                    ?:return@withOwnerTransaction reject(
                        NativeCustomNextCoachRefusal.COACH_SETTINGS_UNAVAILABLE)
            if(!preference.offersProgression)
                return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.ASSISTANCE_DISABLED)
            val acceptance=(signoffs.read(ownerKey,inputs)
                as? NativeActivatedSignoffRead.Found)?.takeIf{it.isCurrent}
                ?.receipt
                ?:return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.ACTIVATED_CONSENT_UNAVAILABLE)
            val active=(activation.read(ownerKey)
                as? NativeFutureCommitReview.Applied)?.context
                ?:return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.ACTIVATED_CONSENT_UNAVAILABLE)
            if(acceptance.ownerKey!=ownerKey ||
                acceptance.athleteId!=current.athleteId ||
                acceptance.cycleId!=active.cycleId ||
                acceptance.blockId!=active.blockId ||
                acceptance.firstWeek!=active.firstWeek ||
                acceptance.sourceRevision!=active.sourceRevision)
                return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.ACTIVATED_CONSENT_UNAVAILABLE)
            val next=(activation.previewActivatedWorkout(ownerKey,inputs,
                nextAbsoluteWeek,nextWeekday)
                as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                ?:return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.NEXT_WORKOUT_UNAVAILABLE)
            if(next.stamp.athleteId!=current.athleteId ||
                next.stamp.cycleId!=active.cycleId ||
                next.blockId!=active.blockId ||
                next.stamp.gymCatalogSourceRevision!=active.sourceRevision)
                return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.NEXT_WORKOUT_UNAVAILABLE)
            val source=(sources.read(ownerKey)
                as? NativeAuthoringSourceRead.Found)?.source
                ?:return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.CURRENT_GYM_UNAVAILABLE)
            val gym=source.setup.reviewed?.gym
                ?:return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.CURRENT_GYM_UNAVAILABLE)
            if(source.athleteId!=current.athleteId ||
                source.revision!=active.sourceRevision ||
                gym.gymId!=next.stamp.gymId)
                return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.CURRENT_GYM_UNAVAILABLE)
            val selected=next.slots.singleOrNull{it.slotId==slotId}
                ?:return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.NEXT_WORKOUT_UNAVAILABLE)
            val movement=source.catalog.singleOrNull{
                it.exerciseId==selected.exerciseId &&
                    it.familyId==selected.familyId &&
                    it.variantId==selected.variantId
            }?:return@withOwnerTransaction reject(
                NativeCustomNextCoachRefusal.CURRENT_GYM_UNAVAILABLE)
            val verifiedStock=movement.equipmentId?.let{
                gym.confirmedStock[it]
            }?.takeIf{it.basis==movement.expectedLoadBasis && it.unit==gym.unit}
            val historyRead=history.read(ownerKey,current.athleteId)
            val full=historyRead as? NativeKotlinMixedHistoryReadResult.Review
                ?:return@withOwnerTransaction when(historyRead) {
                    is NativeKotlinMixedHistoryReadResult.Rejected ->
                        NativeCustomNextCoachResult.HistoryRejected(historyRead.reason)
                    else -> reject(NativeCustomNextCoachRefusal.HISTORY_CHANGED_OR_UNVERIFIED)
                }
            val completions=ArrayList<CustomCoachingHistoricalSession>()
            for(entry in full.timeline.completions){
                if(entry !is NativeKotlinMixedHistoryCompletion.Custom)
                    continue
                val stored=(journals.open(ownerKey,entry.manifest.workoutId)
                    as? NativeCustomJournalResult.Durable)?.issued?.journal
                    ?:return@withOwnerTransaction reject(
                        NativeCustomNextCoachRefusal.HISTORY_CHANGED_OR_UNVERIFIED)
                val snap=stored.snapshot
                if(stored.workoutId!=entry.evidence.workoutId ||
                    snap.stamp.athleteId!=current.athleteId ||
                    snap.stamp.cycleId!=entry.evidence.cycleId ||
                    snap.stamp.cycleRevision!=entry.evidence.cycleRevision)
                    return@withOwnerTransaction reject(
                        NativeCustomNextCoachRefusal.HISTORY_CHANGED_OR_UNVERIFIED)
                completions+=CustomCoachingHistoricalSession(
                    entry.manifest.completionOrder,snap,entry.evidence)
            }
            if(identity.signedInIdentity()!=current)
                return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.NOT_AUTHORIZED)
            val validated=runCatching{CustomNextWorkoutCoachingDomain.review(
                CustomNextWorkoutCoachingRequest(
                    next,slotId,completions,verifiedStock,
                    maximumAuthorizedIncrease,
                    preference.coachingReadiness(readiness),movementRestricted)
            )}.getOrNull()
                ?:return@withOwnerTransaction reject(
                    NativeCustomNextCoachRefusal.HISTORY_CHANGED_OR_UNVERIFIED)
            NativeCustomNextCoachResult.Reviewed(
                full.timeline.manifestRevision,acceptance.recordRevision,
                validated,verifiedStock)
        }}.getOrElse{reject(NativeCustomNextCoachRefusal.STORAGE_UNAVAILABLE)}
    }
}
