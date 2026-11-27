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

/** Revision-bound read only; never a new issued target or a program edit. */
data class NativeCustomSessionBasicReview(
    val workoutId:String,val journalRevision:Long,val setKey:String,
    val method:SavedProgressionMethod,val machineInstanceId:String?,
    val projection:CustomBasicHistoryResult
)

/**
 * Read-only owner-fenced basic next-workout candidate. Unlike optional
 * fatigue coaching, Manual/basic progression remains available when an
 * athlete turns coaching assistance off. Nothing here writes future sets.
 *
 * Every request reopens signed cycle consent and current gym sources,
 * the complete corrected history ledger and its verified issued snapshots. It selects one physical implement through the same authority
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
    private val machineRosters:NativeKotlinMachineRosterFileStore?=null,
    private val sessionJournals:NativeKotlinCustomWorkoutJournalFileStore?=null,
    private val sessionConsents:NativeKotlinCustomCycleSignoffFileStore?=null,
    private val sessionMachineChoices:NativeKotlinWorkoutMachineChoiceFileStore?=null
) {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    fun reviewAuthored(
        ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        absoluteWeek:Int,weekday:Int,slotId:String,
        nextWorkoutId:String,maximumAuthorizedIncrement:BigDecimal
    ):NativeCustomBasicNextReadResult = reviewInternal(ownerKey,cycleId,inputs,
        absoluteWeek,weekday,slotId,nextWorkoutId,maximumAuthorizedIncrement,null,null)

    /** Only a durable session receipt can select a temporary method. The
     * existing signed-source and corrected-history evaluator owns all numbers. */
    fun reviewSession(ownerKey:String,workoutId:String,setKey:String,
        maximumAuthorizedIncrement:BigDecimal):NativeCustomSessionBasicReview? = runCatching {
        fence.withOwnerTransaction(ownerKey) {
            val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                ?:return@withOwnerTransaction null
            val issued=(sessionJournals?.open(ownerKey,workoutId)
                as? NativeCustomJournalResult.Durable)?.issued
                ?.takeIf{it.athleteId==owner.athleteId}
                ?:return@withOwnerTransaction null
            val journal=issued.journal
            if(journal.finishedByEventId!=null)return@withOwnerTransaction null
            val original=journal.originalSnapshot
            val slot=journal.snapshot.slots.singleOrNull{it.sets.any{s->s.setKey==setKey}}
                ?:return@withOwnerTransaction null
            val method=journal.sessionProgressionChoices[slot.slotId]
                ?:return@withOwnerTransaction null
            if(slot.sets.none{it.setKey==setKey && it.prescription.type==DraftSetType.WORKING} ||
                CustomWorkoutVisitKey(setKey,CustomWorkoutVisitPart.MAIN) in journal.effectiveVisits ||
                slot!=original.slots.singleOrNull{it.slotId==slot.slotId})
                return@withOwnerTransaction null
            val signed=sessionConsents?.recoverReviewedInputs(ownerKey,original.stamp.cycleId)
                as? NativeCustomSavedCoachReview.Ready ?:return@withOwnerTransaction null
            if(signed.receipt.recordRevision!=issued.consentRevision ||
                signed.receipt.fingerprintSha256!=journal.preparedConsentFingerprint)
                return@withOwnerTransaction null
            val result=reviewInternal(ownerKey,original.stamp.cycleId,signed.inputs,
                original.absoluteWeek,original.weekday,slot.slotId,workoutId,
                maximumAuthorizedIncrement,issued,setKey)
                as? NativeCustomBasicNextReadResult.Reviewed ?:return@withOwnerTransaction null
            val machine=when(val projection=result.projection){
                is CustomBasicHistoryResult.Reviewed->projection.machineInstanceId
                else->slot.machineInstanceId
            }
            if(identity.signedInIdentity()!=owner)return@withOwnerTransaction null
            NativeCustomSessionBasicReview(workoutId,journal.revision,setKey,method,machine,result.projection)
        }
    }.getOrNull()

    private fun reviewInternal(
        ownerKey:String,cycleId:String,inputs:CustomCoachAuditInputs,
        absoluteWeek:Int,weekday:Int,slotId:String,nextWorkoutId:String,
        maximumAuthorizedIncrement:BigDecimal,
        session:NativeCustomIssuedJournal?,requestedSetKey:String?
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
                // Overlay the method in memory at the exact signed revision.
                // Never publish the builder's proposed new program revision.
                val evaluationCycle=if(session!=null){
                    val journal=session.journal
                    val chosen=journal.sessionProgressionChoices[slotId]
                        ?:return@withOwnerTransaction refuse(NativeCustomBasicNextRefusal.INVALID_SCOPE)
                    if(next!=journal.originalSnapshot ||
                        reviewed.receipt.recordRevision!=session.consentRevision ||
                        reviewed.receipt.fingerprintSha256!=journal.preparedConsentFingerprint ||
                        requestedSetKey==null ||
                        chosen !in MidSessionWorkingEditDomain.availableCustomProgressionMethods(
                            journal,cycle,nextWorkoutId,journal.revision,slotId))
                        return@withOwnerTransaction refuse(NativeCustomBasicNextRefusal.INVALID_SCOPE)
                    val key=ProgramProgressionSlotKey(next.blockId,weekday,slotId)
                    val saved=cycle.blocks.single{it.blockId==key.blockId}
                        .days.single{it.weekday==weekday}.slots.single{it.slotId==slotId}
                    if(saved.progressionMethod==chosen)cycle else {
                        val proposal=CustomCycleBuilderDomain.setProgressionMethod(cycle,cycle,
                            key.blockId,weekday,slotId,chosen) as? CustomCycleBuilderResult.Proposed
                            ?:return@withOwnerTransaction refuse(NativeCustomBasicNextRefusal.INVALID_SCOPE)
                        proposal.draft.copy(revision=cycle.revision)
                    }
                }else cycle
                val roster=when {
                    machineRosters==null -> null
                    else -> (machineRosters.read(ownerKey)
                        as? NativeMachineRosterResult.Saved)?.snapshot
                        ?:return@withOwnerTransaction refuse(
                            NativeCustomBasicNextRefusal.MACHINE_ROSTER_UNAVAILABLE)
                }
                val selectedForStock=if(session!=null &&
                    roster?.configurations?.any{it.gymId==gym.gymId &&
                        it.exerciseId==selected.exerciseId && it.machines.isNotEmpty()}==true){
                    val choices=(sessionMachineChoices?.read(ownerKey,nextWorkoutId,true,gym.gymId)
                        as? NativeWorkoutMachineChoiceResult.Saved)?.snapshot
                    val machine=choices?.takeIf{it.revision>0 && it.ownerId==ownerKey &&
                        it.gymId==gym.gymId}?.machines?.get(selected.exerciseId)
                        ?:return@withOwnerTransaction refuse(NativeCustomBasicNextRefusal.CURRENT_GYM_UNAVAILABLE)
                    if(selected.machineInstanceId!=null && selected.machineInstanceId!=machine)
                        return@withOwnerTransaction refuse(NativeCustomBasicNextRefusal.CURRENT_GYM_UNAVAILABLE)
                    selected.copy(machineInstanceId=machine)
                }else selected
                val physical=NativeKotlinCustomSelectedStockDomain.resolve(
                    source,selectedForStock,roster)
                    ?:return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.CURRENT_GYM_UNAVAILABLE)
                val (fetched,completed)=history.readForCustomCoaching(ownerKey,owner.athleteId)
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
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction refuse(
                        NativeCustomBasicNextRefusal.NOT_AUTHORIZED)
                // Delegate increment eligibility to the ONE numerical engine.
                // Unloaded rep progression, rep-only and manual logging do not
                // require a positive physical weight step. A weighted increase
                // will still refuse zero/invalid stock inside its own policy.
                val candidate=CustomBasicHistoryProgressionDomain.review(
                    CustomBasicHistoryRequest(
                        evaluationCycle,next,nextWorkoutId,slotId,completed,
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
