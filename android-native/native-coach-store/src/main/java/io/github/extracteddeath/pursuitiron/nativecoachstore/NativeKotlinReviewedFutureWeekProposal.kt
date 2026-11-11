package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

/**
 * Native read boundary for the evidence -> future-week bridge. The headless
 * domain deliberately accepts historical facts as arguments; this boundary
 * NEVER accepts those facts, the cycle, policy, permissions or gym from UI.
 *
 * Every source is reopened under one owner OS fence. A proposal is a
 * temporary review option, not an approved draft, workout or journal write.
 */
enum class NativeFutureWeekProposalRefusal {
    INVALID_SELECTION, NOT_AUTHORIZED, CYCLE_OR_CONSENT_UNAVAILABLE,
    GYM_OR_POLICY_UNAVAILABLE, PERMISSIONS_UNAVAILABLE,
    HISTORY_UNVERIFIED, FUTURE_WORKOUT_ALREADY_PREPARED,
    COACH_NOT_VERIFIED, REVIEW_DIVERGED,
    STORAGE_UNAVAILABLE
}

sealed interface NativeFutureWeekProposalResult {
    data class Reviewed(
        val proposal: ReviewedFutureWeekProgression,
        val cycleRevision: Long,
        val gymSourceRevision: Long,
        val historyManifestRevision: Long,
        val permissionRevision: Long,
        val consentRevision: Long,
        val assistanceRevision: Long,
        val machineRosterRevision: Long=0
    ) : NativeFutureWeekProposalResult {
        val maySaveProgram:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
        val mayAlterHistory:Boolean get()=false
    }
    data class Rejected(val reason:NativeFutureWeekProposalRefusal):
        NativeFutureWeekProposalResult
}

/**
 * Does not claim ownership of an issued snapshot. Approval/persistence must
 * separately CAS the owner, source, cycle, consent, permissions and history,
 * reject issued targets, and rederive this option inside that transaction.
 */
/**
 * Any prepared or issued workout for the selected future week is protected,
 * including a different weekday or older cycle revision. Also refuse any
 * unfinished workout from this same cycle, rather than orphaning a currently
 * actionable plan when its signed draft revision changes.
 *
 * Historical FINISHED sessions from prior weeks remain untouched.
 */
internal object NativeFutureWeekProposalInventoryPolicy {
    fun blocks(
        sessions:List<NativeCustomInventoryFact>,
        cycleId:String,absoluteWeek:Int
    ):Boolean=sessions.any{
        it.cycleId==cycleId &&
            (it.absoluteWeek==absoluteWeek || !it.completed)
    }
}

class NativeKotlinReviewedFutureWeekProposal(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val drafts:NativeCustomCycleDraftFileStore,
    private val sources:NativeKotlinCustomAuthoringSourceFileStore,
    private val signoffs:NativeKotlinCustomCycleSignoffFileStore,
    private val permissions:NativeKotlinAdaptiveCyclePermissionFileStore,
    private val assistance:NativeKotlinCoachAssistanceSettingsStore,
    private val preflight:NativeKotlinCustomWorkoutPreflight,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val journals:NativeKotlinCustomWorkoutJournalFileStore,
    private val reviewer:NativeKotlinCustomNextWorkoutReview,
    private val outstanding:NativeKotlinCustomOutstandingWorkoutInventory,
    private val machineRosters:NativeKotlinMachineRosterFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    companion object {
        /** Rights are intentionally narrower than other adaptive edits. */
        internal fun weekPermissions(rights:Set<CustomCycleChangeKind>)=
            CustomCycleCoachPermissions(
                adaptWeekOverrides=CustomCycleChangeKind.WEEK_OVERRIDES in rights,
                adaptReps=CustomCycleChangeKind.REPS in rights,
                adaptLoads=CustomCycleChangeKind.LOADS in rights)

        /** Includes older completed revisions; never trusts an input week. */
        internal fun lastObservedWeek(
            cycleId:String,completed:List<Pair<String,Int>>
        ):Int=completed.filter{it.first==cycleId}
            .maxOfOrNull{it.second} ?:0
    }

    fun review(
        ownerKey:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        slotId:String,readiness:CoachReadiness,movementRestricted:Boolean,
        maximumAuthorizedIncrease:BigDecimal,
        forecast:CustomRepForecastPolicy=CustomRepForecastPolicy.TARGET_RIR_V2
    ):NativeFutureWeekProposalResult {
        fun reject(reason:NativeFutureWeekProposalRefusal)=
            NativeFutureWeekProposalResult.Rejected(reason)
        if(!id.matches(ownerKey) || !id.matches(cycleId) ||
            !id.matches(slotId) || absoluteWeek !in 1..104 ||
            weekday !in 0..6)
            return reject(NativeFutureWeekProposalRefusal.INVALID_SELECTION)
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val owner=identity.signedInIdentity()?.takeIf {
                    it.ownerKey==ownerKey
                } ?:return@withOwnerTransaction reject(
                    NativeFutureWeekProposalRefusal.NOT_AUTHORIZED)
                val approved=(signoffs.recoverReviewedInputs(ownerKey,cycleId)
                    as? NativeCustomSavedCoachReview.Ready)
                    ?:return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.CYCLE_OR_CONSENT_UNAVAILABLE)
                val current=(preflight.inspect(ownerKey,cycleId,
                    absoluteWeek,weekday,approved.inputs)
                    as? NativeCustomPreflightResult.ReviewedPreview)
                    ?:return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.CYCLE_OR_CONSENT_UNAVAILABLE)
                val cycle=(drafts.read(ownerKey,cycleId)
                    as? NativeCustomDraftReadResult.Found)?.draft
                    ?:return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.CYCLE_OR_CONSENT_UNAVAILABLE)
                val gym=(sources.read(ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.GYM_OR_POLICY_UNAVAILABLE)
                val selectedSlot=current.session.slots.singleOrNull{
                    it.slotId==slotId
                } ?:return@withOwnerTransaction reject(
                    NativeFutureWeekProposalRefusal.INVALID_SELECTION)
                val currentRoster=(machineRosters?.read(ownerKey)
                    as? NativeMachineRosterResult.Saved)?.snapshot
                if(currentRoster!=null && currentRoster.ownerId!=ownerKey)
                    return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.GYM_OR_POLICY_UNAVAILABLE)
                if(cycle.athleteId!=owner.athleteId ||
                    gym.athleteId!=owner.athleteId ||
                    approved.receipt.cycleRevision!=cycle.revision ||
                    approved.receipt.sourceRevision!=gym.revision ||
                    current.receipt!=approved.receipt ||
                    current.session.stamp.cycleRevision!=cycle.revision ||
                    current.session.stamp.gymCatalogSourceRevision!=gym.revision)
                    return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.GYM_OR_POLICY_UNAVAILABLE)

                val grant=(permissions.read(ownerKey,cycleId)
                    as? NativeAdaptivePermissionRead.Found)?.receipt
                    ?:return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.PERMISSIONS_UNAVAILABLE)
                if(grant.athleteId!=owner.athleteId ||
                    grant.cycleId!=cycleId || grant.ownerKey!=ownerKey)
                    return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.PERMISSIONS_UNAVAILABLE)
                val settings=(assistance.read(ownerKey)
                    as? NativeCoachSettingsRead.Current)?.settings
                    ?:return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.GYM_OR_POLICY_UNAVAILABLE)
                if(settings.ownerKey!=ownerKey || settings.athleteId!=owner.athleteId)
                    return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.NOT_AUTHORIZED)

                // The owner reviewer independently re-opens the complete
                // corrected ledger and enforces the chosen assistance policy.
                val verified=(reviewer.reviewAuthored(ownerKey,cycleId,
                    approved.inputs,absoluteWeek,weekday,slotId,readiness,
                    movementRestricted,maximumAuthorizedIncrease,forecast)
                    as? NativeCustomNextCoachResult.Reviewed)
                    ?:return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.COACH_NOT_VERIFIED)

                val read=(history.read(ownerKey,owner.athleteId)
                    as? NativeKotlinMixedHistoryReadResult.Review)
                    ?:return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.HISTORY_UNVERIFIED)
                // A finished visit is immutable history; a prepared or
                // issued but unfinished session is a separate contract.
                // Do not write a future-week option across either boundary.
                // This inventory is authoritative only after complete
                // mixed-history validation, under the SAME owner OS lock.
                val completedCustomIds=read.timeline.completions
                    .filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                    .map{it.manifest.workoutId}.toSet()
                val inventory=(outstanding.inspect(ownerKey,owner.athleteId,
                    completedCustomIds) as? NativeCustomInventoryReview.Reviewed)
                    ?:return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.HISTORY_UNVERIFIED)
                if(NativeFutureWeekProposalInventoryPolicy.blocks(
                        inventory.sessions,cycleId,absoluteWeek))
                    return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.FUTURE_WORKOUT_ALREADY_PREPARED)
                val completions=ArrayList<CustomCoachingHistoricalSession>()
                for(entry in read.timeline.completions) {
                    if(entry !is NativeKotlinMixedHistoryCompletion.Custom)continue
                    val stored=(journals.open(ownerKey,entry.manifest.workoutId)
                        as? NativeCustomJournalResult.Durable)?.issued?.journal
                        ?:return@withOwnerTransaction reject(
                            NativeFutureWeekProposalRefusal.HISTORY_UNVERIFIED)
                    val snapshot=stored.snapshot
                    if(stored.workoutId!=entry.evidence.workoutId ||
                        snapshot.stamp.athleteId!=owner.athleteId ||
                        snapshot.stamp.cycleId!=entry.evidence.cycleId ||
                        snapshot.stamp.cycleRevision!=entry.evidence.cycleRevision)
                        return@withOwnerTransaction reject(
                            NativeFutureWeekProposalRefusal.HISTORY_UNVERIFIED)
                    completions+=CustomCoachingHistoricalSession(
                        entry.manifest.completionOrder,snapshot,entry.evidence)
                }
                if(verified.historyRevision!=read.timeline.manifestRevision ||
                    verified.approvedConsentRevision!=current.receipt.recordRevision ||
                    verified.assistanceRevision!=settings.revision ||
                    verified.nextWorkout!=current.session ||
                    verified.selectedSlotId!=slotId)
                    return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.REVIEW_DIVERGED)

                // A UI-supplied "last completed week" is never authority.
                // The highest observed week in this exact cycle is used,
                // including completed sessions from older cycle revisions.
                val lastWeek=lastObservedWeek(cycleId,completions.map{
                    it.snapshot.stamp.cycleId to it.snapshot.absoluteWeek
                })
                val option=ReviewedFutureWeekProgressionDomain.propose(
                    ReviewedFutureWeekProgressionRequest(
                        cycle,gym.setup,gym.catalog,approved.inputs,
                        gym.revision,lastWeek,
                        CustomNextWorkoutCoachingRequest(
                            current.session,slotId,completions,
                            verified.verifiedStock,maximumAuthorizedIncrease,
                            settings.policy.coachingReadiness(readiness),
                            movementRestricted,forecast,
                            selectedSlot.machineInstanceId),
                        weekPermissions(grant.allowedKinds),
                        currentRoster?.configurations
                    ))
                // A second independent result must agree with the trusted
                // owner review; otherwise neither result can be displayed
                // as an eligible proposal.
                if(option.coaching!=null && option.coaching!=verified.review)
                    return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.REVIEW_DIVERGED)
                // A newly proposed load must still fit the actual A/B stack
                // for every authored set, warmup and amplifier. The pure
                // cycle audit does not own the physical-machine roster.
                option.candidate?.let {candidate->
                    val changed=(CustomWorkoutSnapshotDomain.reviewForConsent(
                        candidate,gym.setup,gym.catalog,gym.revision,
                        approved.inputs,absoluteWeek,weekday)
                        as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                        ?:return@withOwnerTransaction reject(
                            NativeFutureWeekProposalRefusal.COACH_NOT_VERIFIED)
                    if(NativeKotlinCustomMachineBindingPolicy.review(
                            ownerKey,changed,gym.catalog,currentRoster)
                        is NativeCustomMachineBindingReview.Rejected)
                        return@withOwnerTransaction reject(
                            NativeFutureWeekProposalRefusal.COACH_NOT_VERIFIED)
                }
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction reject(
                        NativeFutureWeekProposalRefusal.NOT_AUTHORIZED)
                NativeFutureWeekProposalResult.Reviewed(
                    option,cycle.revision,gym.revision,
                    read.timeline.manifestRevision,grant.revision,
                    current.receipt.recordRevision,settings.revision,
                    if(selectedSlot.machineInstanceId!=null)
                        currentRoster?.revision ?:0 else 0)
            }
        }.getOrElse {
            reject(NativeFutureWeekProposalRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
