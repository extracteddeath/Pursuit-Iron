package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.isActive

/**
 * Candidate-only Compose state holder. All disk operations run on IO;
 * accepted UI state comes ONLY from a durable native write or a disk read.
 * No WebView/JS engine, optimistic save, fictitious program approval or
 * concurrent, silently overwritten auto-save.
 */
internal data class NativeKotlinCustomCycleState(
    val draft: CustomTrainingCycle? = null,
    val fixedNamedDraft:Boolean=false,
    val tieredDraft:Boolean=false,
    val tieredInitialReview:NativeSavedNamedReview?=null,
    val tieredCalendar:NativeCustomNextCycleSlot?=null,
    val tieredReceipt:NativeTieredInitialDraftReceipt?=null,
    val tieredReceipts:List<NativeTieredInitialDraftReceipt> = emptyList(),
    val tieredSupportAvailable:Boolean=false,
    val tieredSupportWorkoutId:String?=null,
    val tieredSupportPreview:NativeCustomPreflightResult.ReviewedPreview?=null,
    val tieredSupportPrepared:NativeCustomPreparedWorkout?=null,
    val tieredTransitionAvailable:Boolean=false,
    val tieredTransitionReview:NativeSavedNamedReview.Signed?=null,
    val staleTieredTransition:NativeStaleTierTransitionReview?=null,
    val tieredTransitionPreview:NativeFirstTierTransitionPreflight.Reviewed?=null,
    val tieredTransitionPrepared:NativeCustomPreparedWorkout?=null,
    val tieredInitialPreview:NativeCustomPreflightResult.ReviewedPreview?=null,
    val tieredInitialPrepared:NativeCustomPreparedWorkout?=null,
    val tieredReferenceBatch:NativeTieredInitialBatchReview.Ready?=null,
    val fixedNamedBatch:NativeNamedBatchReview.Ready?=null,
    val fixedNamedReview:NativeSavedNamedReview?=null,
    val fixedNamedWorkoutId:String?=null,
    val fixedNamedPreview:NativeCustomPreflightResult.ReviewedPreview?=null,
    val fixedNamedPrepared:NativeCustomPreparedWorkout?=null,
    val fixedNamedIssuedId:String?=null,
    val cycleId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val programSelectionNotice:String? = null,
    val refusal: NativeCustomDraftRefusal? = null,
    val canUndo: Boolean = false,
    val authoringRefusal: CustomAuthoringRefusal? = null,
    val availableChoices: List<CustomExercisePickerOption> = emptyList(),
    val savedDrafts: List<NativeCustomDraftSummary> = emptyList(),
    val savedDraftsLoaded:Boolean = false,
    val savedProgramsOwner:NativeKotlinOwnerIdentity? = null,
    val sourceRevision: Long? = null,
    /** Read on current native owner open; never default coaching to on. */
    val coachingSuggestionsEnabled:Boolean = false,
    val progressionCoachingEnabled:Boolean = false,
    val fatigueCoachingEnabled:Boolean = false,
    /** Current owner-reviewed source context; stale edits are rechecked at audit. */
    val reviewedSourceSetup: AthleteSetupReview? = null,
    /** Factual set credit evidence only; does not contain MEV/MRV decisions. */
    val authoredWorkloadEvidence: CustomCoachPolicyEvidence? = null,
    val evidenceSourceRevision: Long? = null,
    val coachingAudit: CustomCoachAudit? = null,
    val auditSourceRevision: Long? = null,
    /** Latest full-cycle audit inputs, freshly rechecked after cold recovery.
     * Never fabricated or reused after an authored draft edit. */
    val reviewedCoachInputs: CustomCoachAuditInputs? = null,
    /** Read-only owner-verified next session; never an issuance grant. */
    val nextCycleSlot:NativeCustomNextCycleSlot?=null,
    val nextSlotCycleRevision:Long?=null,
    /** Unsaved, athlete-entered one-week correction and its full-cycle effects. */
    val weekEditReview: CustomCoachWeekEditReview? = null,
    val signoff: NativeCustomSignoffRead? = null,
    val signoffRefusal: NativeCustomSignoffRefusal? = null,
    val authoredPreview: CustomWorkoutDaySnapshot? = null,
    val authoredPreviewRefusal: CustomWorkoutSnapshotRefusal? = null,
    val consentBoundPreview: NativeCustomPreflightResult.ReviewedPreview? = null,
    /** Current native custom consent, exact week/day and time-review result.
     * Never a signed short-session issuance grant. */
    val customShortReview: NativeCustomShortSessionPreflight.Reviewed? = null,
    /** Two-step, before-issuance Full restoration of the visible short plan. */
    val customTimeUndo:NativeCustomTimeUndoPreview.Ready?=null,
    val preparedWorkout: NativeCustomPreparedWorkout? = null,
    val preparationRefusal: NativeCustomPrepareRefusal? = null,
    val dayCoaching: NativeCustomNextCoachResult.Reviewed? = null,
    /** Owner-reverified basic progression, with no save or issuance authority. */
    val basicDayProgression:NativeCustomBasicNextReadResult.Reviewed?=null,
    val basicDayProgressionSlotId:String?=null,
    val calibrationReview:NativeBlockCalibrationPreview.Ready? = null,
    val authoredReturnReview:NativeAuthoredLoadReturnPreview.Ready? = null,
    val calibrationReceipt:NativeBlockCalibrationReceipt? = null,
    val calibrationReset:NativePreparedManualResetPreview.Ready? = null,
    val issuedCalibrationId:String? = null,
    val pendingCalibrations:List<NativeCustomPreparedWorkout> = emptyList(),
    val pendingCalibrationAcceptances:List<NativeBlockCalibrationReceipt> = emptyList(),
    /** Source- and permission-verified, non-issuable draft repair preview. */
    val strengthRepairReview:NativeStrengthVolumeRepairReady?=null,
    val strengthMenuRecoveryReview:NativeStrengthMenuRecoveryReady?=null,
    val recoveryRecipeReview:NativeRecoveryRecipeReady?=null
)

/**
 * Source must be supplied by a future trusted native gym/catalog owner.
 * No default stock, exercise inventory or legacy JS fallback is fabricated.
 * The picker is therefore intentionally empty until this is integrated.
 */
internal fun interface NativeKotlinCustomAuthoringSource {
    fun snapshot(): NativeCustomAuthoringSnapshot?
}

internal class NativeKotlinCustomCycleController(
    private val host: NativeKotlinCandidateHost,
    private val authoringSource: NativeKotlinCustomAuthoringSource =
        NativeKotlinCustomAuthoringSource {
            (host.readCustomAuthoringSource() as? NativeAuthoringSourceRead.Found)
                ?.source
        }
) : AutoCloseable {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val pending = AtomicBoolean(false)
    private val planEntries = NativeKotlinPlanEntryRequests()
    private val current = MutableStateFlow(NativeKotlinCustomCycleState())
    val state: StateFlow<NativeKotlinCustomCycleState> = current.asStateFlow()
    private val pickerSession=NativeKotlinProgramPickerSession()
    private val pickerCurrent=MutableStateFlow<NativeKotlinProgramPickerState?>(null)
    val programPicker:StateFlow<NativeKotlinProgramPickerState?> = pickerCurrent.asStateFlow()
    private var lastUndo: CustomCycleDraftReceipt? = null
    private var openedSource: NativeCustomAuthoringSnapshot? = null
    private var auditedInputs: CustomCoachAuditInputs? = null
    private data class PendingWeekEdit(
        val source: NativeCustomAuthoringSnapshot,
        val inputs: CustomCoachAuditInputs,
        val review: CustomCoachWeekEditReview
    )
    private var pendingWeekEdit: PendingWeekEdit? = null

    private fun run(preserveFeedback:Boolean=false,action: () -> Unit) {
        if (!pending.compareAndSet(false, true)) return
        current.value = if(preserveFeedback)current.value.copy(busy=true)
            else current.value.copy(busy = true, refusal = null,
                authoringRefusal = null, message = null)
        scope.launch {
            try {
                action()
            } catch (_: Exception) {
                current.value = current.value.copy(
                    message = "Native draft operation failed; reload the saved version.")
            } finally {
                current.value = current.value.copy(busy = false)
                pending.set(false)
                if(scope.isActive && planEntries.hasPending)drainPlanEntries()
            }
        }
    }

    private fun enter(explicit:Boolean=true,action:()->Unit) {
        if(!scope.isActive)return
        if(explicit)planEntries.select(action) else planEntries.resume(action)
        drainPlanEntries()
    }

    private fun drainPlanEntries() = run(preserveFeedback=true) {planEntries.takeNext()?.invoke()}

    private fun afterSaved(cycleId:String,action:()->Unit) {
        if(!scope.isActive)return
        planEntries.refreshSaved(cycleId,{current.value.draft?.cycleId},action)
        drainPlanEntries()
    }

    private fun applyRead(
        cycleId:String,result:NativeCustomDraftReadResult,
        latest:NativeCustomAuthoringSnapshot?=
            runCatching {authoringSource.snapshot()}.getOrNull()
    ) {
        applyPreparedRead(prepareRead(cycleId,result,latest))
    }

    private data class PreparedProgramRead(
        val state:NativeKotlinCustomCycleState,
        val source:NativeCustomAuthoringSnapshot?
    )

    /** All native reads finish before a picker session can claim this view. */
    private fun prepareRead(cycleId:String,result:NativeCustomDraftReadResult,
        latest:NativeCustomAuthoringSnapshot?):PreparedProgramRead {
        val assistance=(runCatching{host.readCoachAssistance()}
            .getOrNull() as? NativeCoachSettingsRead.Current)
            ?.settings?.policy
        val found = (result as? NativeCustomDraftReadResult.Found)?.draft
        val source = latest?.takeIf { it.athleteId == found?.athleteId }
        val choices = if (source == null) emptyList() else
            CustomExerciseAuthoringDomain.picker(source.setup,source.catalog)
        val view = when (result) {
            is NativeCustomDraftReadResult.Found ->
                NativeKotlinCustomCycleState(
                    draft = result.draft,
                    fixedNamedDraft=result.namedReceipts.isNotEmpty(),
                    tieredDraft=result.tieredInitialReceipts.isNotEmpty(),
                    tieredReceipt=result.tieredInitialReceipt,
                    tieredReceipts=result.tieredInitialReceipts,
                    cycleId = result.draft.cycleId,
                    availableChoices = choices,
                    sourceRevision = source?.revision,
                    coachingSuggestionsEnabled = assistance?.coachingSuggestionsEnabled==true,
                    progressionCoachingEnabled = assistance?.offersProgression==true,
                    fatigueCoachingEnabled = assistance?.offersFatigueAdvice==true,
                    reviewedSourceSetup = source?.setup,
                    signoff = if(result.tieredInitialReceipts.isEmpty())
                        host.readCustomCycleConsent(result.draft.cycleId) else null,
                    message = "Saved custom cycle restored · revision ${result.draft.revision}")
            NativeCustomDraftReadResult.Missing ->
                NativeKotlinCustomCycleState(cycleId = cycleId,
                    message = "No saved draft exists with that ID.")
            is NativeCustomDraftReadResult.Deleted ->
                NativeKotlinCustomCycleState(cycleId = cycleId,
                    message = "This draft was deleted at revision ${result.revision}.")
            is NativeCustomDraftReadResult.Rejected ->
                NativeKotlinCustomCycleState(cycleId = cycleId,
                    refusal = result.reason,
                    message = "Saved draft could not be verified. Nothing was overwritten.")
        }.copy(busy=pending.get())
        return PreparedProgramRead(view,source)
    }

    /** Memory-only publication, also used inside the owner/session claim. */
    private fun applyPreparedRead(prepared:PreparedProgramRead) {
        lastUndo=null
        auditedInputs=null
        pendingWeekEdit=null
        openedSource=prepared.source
        current.value=prepared.state
    }

    /** Prefer an exact signed review, including custom rules. An unsigned
     * generated draft may still recover its original calibrated preview. */
    private fun recoverSavedCoachPolicy(cycleId:String) {
        if(current.value.draft?.cycleId!=cycleId)return
        if(current.value.tieredDraft) {
            refreshTieredInitialReview(cycleId)
            return
        }
        if(current.value.fixedNamedDraft) {
            refreshFixedNamedReview(cycleId)
            return
        }
        val saved=host.recoverSavedCustomCycleReview(cycleId)
        when(saved) {
            is NativeCustomSavedCoachReview.Ready -> {
                auditReviewedSavedCycle(saved.inputs)
                if(current.value.coachingAudit?.status==
                    CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
                    (current.value.signoff as? NativeCustomSignoffRead.Found)?.isCurrent==true)
                    current.value=current.value.copy(
                        message="Your saved coaching rules were restored and rechecked against this program and gym. Your current approval is preserved.")
                return
            }
            is NativeCustomSavedCoachReview.Rejected -> {
                current.value=current.value.copy(
                    message="The saved review could not be restored (${saved.reason.name.lowercase().replace('_',' ')}). Recheck the program, gym and coaching rules before starting a new workout.")
                return
            }
            NativeCustomSavedCoachReview.Missing,
            NativeCustomSavedCoachReview.LegacyWithoutInputs -> {}
        }
        when(val recovered=host.recoverSavedFirstProgramReview(cycleId)) {
            is NativeRecoveredFirstProgramReview.Ready -> {
                auditReviewedSavedCycle(recovered.inputs)
                if(current.value.coachingAudit?.status==
                    CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW)
                    current.value=current.value.copy(
                        message="Generated first-program coaching policy restored from verified saved calibration. Review and sign this exact cycle before preparing a workout.")
            }
            is NativeRecoveredFirstProgramReview.Rejected ->
                current.value=current.value.copy(
                    message=recovered.reason+
                        " Your saved custom program remains available for manual coaching review.")
            NativeRecoveredFirstProgramReview.NotGenerated -> {
                if(saved==NativeCustomSavedCoachReview.LegacyWithoutInputs)
                    current.value=current.value.copy(
                        message="This older approval did not save its coaching rules. Review and sign them once to restore them on future starts. Your program and workout history are preserved.")
            }
        }
    }

    fun open(cycleId: String) = enter {
        applyRead(cycleId, host.readCustomCycleDraft(cycleId))
        recoverSavedCoachPolicy(cycleId)
        selectNextSessionIfReviewed()
    }

    /**
     * After a human-confirmed future-week draft CAS, keep the already
     * athlete-reviewed coaching recipe only as input to a NEW full-cycle
     * audit. A changed source revision discards it. A successful audit does
     * NOT sign the new draft: explicit new athlete consent remains required.
     */
    fun reopenAfterConfirmedFutureWeekSave(cycleId:String)=afterSaved(cycleId) {
        val previous=current.value.takeIf{it.draft?.cycleId==cycleId}
        val previousInputs=previous?.reviewedCoachInputs
        val previousSourceRevision=previous?.auditSourceRevision
        applyRead(cycleId,host.readCustomCycleDraft(cycleId))
        if(previousInputs!=null && previousSourceRevision!=null &&
            previousSourceRevision==current.value.sourceRevision) {
            auditReviewedSavedCycle(previousInputs)
            current.value=current.value.copy(
                message="Your future-week draft was saved and the full cycle re-audited. Review and sign this new revision before preparing another workout.")
        } else {
            recoverSavedCoachPolicy(cycleId)
        }
    }

    /** Return to the selected task, freshly recheck data and reviews, and
     * retain a still-valid Undo. Cold entry still asks when several programs
     * exist. A return during an in-flight operation runs after that operation. */
    fun openSavedTraining() = enter(explicit=false) {
        when(val entry=host.reviewPlanEntry(current.value.draft,openedSource,
            lastUndo.takeIf{current.value.canUndo})) {
            is NativeKotlinPlanEntry.Selected -> {
                applyRead(entry.cycleId,entry.read,entry.source)
                recoverSavedCoachPolicy(entry.cycleId)
                selectNextSessionIfReviewed()
                entry.retainedUndo?.let {
                    lastUndo=it
                    current.value=current.value.copy(canUndo=true)
                }
                if(entry.undoUnavailable)current.value=current.value.copy(message=
                    listOfNotNull(current.value.message,
                        "The previous Undo is no longer valid for the current program and training setup.")
                        .joinToString("\n"))
            }
            is NativeKotlinPlanEntry.Programs ->applySavedProgramList(entry.read,entry.owner)
        }
    }

    private fun applySavedProgramList(listed:NativeCustomDraftListResult,
        owner:NativeKotlinOwnerIdentity?=null) {
        openedSource=null
        lastUndo=null
        auditedInputs=null
        pendingWeekEdit=null
        current.value=when(listed) {
            is NativeCustomDraftListResult.Ready ->
                NativeKotlinCustomCycleState(busy=true,
                    savedDrafts=listed.drafts,savedDraftsLoaded=true,savedProgramsOwner=owner,
                    message=if(listed.drafts.isEmpty())
                        "Build your first program to continue."
                    else "Choose the saved program you want to continue.")
            is NativeCustomDraftListResult.Rejected ->
                NativeKotlinCustomCycleState(busy=true,
                    refusal=listed.reason,
                    message="Saved programs could not be verified. Reconfirm your setup or retry; no program was replaced.")
        }
    }

    /** Browsing never replaces Plan, clears its feedback, or discards Undo. */
    fun refreshSavedPrograms()=enter {
        if(current.value.draft==null) {
            val entry=host.reviewSavedPrograms()
            applySavedProgramList(entry.read,entry.owner)
        }
    }

    fun findSavedPrograms() {
        if(!scope.isActive)return
        val token=pickerSession.begin{pickerCurrent.value=NativeKotlinProgramPickerState(it)}
        enter {
            try {
                val entry=host.reviewSavedPrograms()
                pickerSession.publish(token) {
                    when(val listed=entry.read) {
                        is NativeCustomDraftListResult.Ready ->pickerCurrent.value=
                            NativeKotlinProgramPickerState(token,false,entry.owner,listed.drafts)
                        is NativeCustomDraftListResult.Rejected -> {
                            if(listed.reason==NativeCustomDraftRefusal.NOT_AUTHORIZED)
                                applySavedProgramList(listed)
                            pickerCurrent.value=NativeKotlinProgramPickerState(token,
                                busy=false,failure=listed.reason)
                        }
                    }
                }
            } catch(_:Exception) {
                pickerFailure(token,NativeCustomDraftRefusal.STORAGE_UNAVAILABLE)
            }
        }
    }

    fun dismissProgramPicker()=pickerSession.dismiss{pickerCurrent.value=null}

    private fun pickerFailure(token:Long,reason:NativeCustomDraftRefusal) {
        pickerSession.publish(token) {
            if(reason==NativeCustomDraftRefusal.NOT_AUTHORIZED) {
                applySavedProgramList(NativeCustomDraftListResult.Rejected(reason))
                pickerCurrent.value=NativeKotlinProgramPickerState(token,busy=false,failure=reason)
            } else pickerCurrent.value=pickerCurrent.value?.copy(busy=false,opening=null,failure=reason)
        }
    }

    /** The list supplies a locator, never cached draft contents or an approval. */
    fun openSavedProgram(selection:NativeKotlinProgramSelection) {
        if(!scope.isActive)return
        val picker=pickerCurrent.value
        if(picker!=null && (picker.busy || picker.owner!=selection.owner ||
                picker.programs.none{it.cycleId==selection.cycleId && it.revision==selection.listedRevision}))return
        if(picker==null && (current.value.savedProgramsOwner!=selection.owner ||
                current.value.savedDrafts.none{it.cycleId==selection.cycleId &&
                    it.revision==selection.listedRevision}))return
        val token=if(picker==null)pickerSession.begin {
            pickerCurrent.value=NativeKotlinProgramPickerState(it,true,selection.owner,
                current.value.savedDrafts,selection.cycleId)
        } else picker.token.also { active->pickerSession.publish(active) {
            pickerCurrent.value=picker.copy(busy=true,opening=selection.cycleId,failure=null)
        }}
        enter {
            try {
                var stillCurrent=false
                pickerSession.publish(token){stillCurrent=true}
                if(stillCurrent) {
                    val entry=host.reviewPlanEntry(current.value.draft,openedSource,
                        lastUndo.takeIf{current.value.canUndo},selection)
                    when(entry) {
                        is NativeKotlinPlanEntry.Programs ->pickerFailure(token,
                            (entry.read as? NativeCustomDraftListResult.Rejected)?.reason
                                ?:NativeCustomDraftRefusal.INVALID_DRAFT)
                        is NativeKotlinPlanEntry.Selected ->when(val read=entry.read) {
                            is NativeCustomDraftReadResult.Found -> {
                                val prepared=prepareRead(entry.cycleId,read,entry.source)
                                val accepted=host.publishProgramViewIfOwned(selection.owner) {
                                    pickerSession.claim(token) {
                                        applyPreparedRead(prepared)
                                        pickerCurrent.value=null
                                    }
                                }
                                if(accepted) {
                                    recoverSavedCoachPolicy(entry.cycleId)
                                    selectNextSessionIfReviewed()
                                    entry.retainedUndo?.let {
                                        lastUndo=it
                                        current.value=current.value.copy(canUndo=true)
                                    }
                                    val notice=when {
                                        entry.selectionUpdated -> "This program changed while you were browsing. The latest saved version is open; review it before training."
                                        entry.undoUnavailable -> "This program or its training setup changed. The previous Undo is no longer available."
                                        else -> null
                                    }
                                    current.value=current.value.copy(programSelectionNotice=notice)
                                } else pickerFailure(token,NativeCustomDraftRefusal.NOT_AUTHORIZED)
                            }
                            is NativeCustomDraftReadResult.Rejected ->pickerFailure(token,read.reason)
                            else ->pickerFailure(token,NativeCustomDraftRefusal.INVALID_DRAFT)
                        }
                    }
                }
            } catch(_:Exception) {
                pickerFailure(token,NativeCustomDraftRefusal.STORAGE_UNAVAILABLE)
            }
        }
    }

    /** Re-open the verified disk state and CURRENT gym/catalog when returning
     * from native equipment editing. A previously rendered choice cannot
     * stand in for today's reviewed physical availability. */
    fun refresh() {
        val id=current.value.draft?.cycleId ?: current.value.cycleId
        if(!id.isNullOrBlank()) open(id)
    }


    fun create(
        title: String,
        goal: AthleteTrainingGoal,
        weeks: Int,
        phase: TrainingPhase,
        phaseName: String,
        customSplit: String
    ) = run {
        val cycleId = "cycle-" + UUID.randomUUID().toString().replace("-", "")
        val result = host.createBlankCustomCycleDraft(
            cycleId, goal, title, "phase-1", phaseName, weeks, phase,
            CustomSplitIdentity.Freeform(customSplit))
        when (result) {
            is NativeCustomDraftWriteResult.Committed ->
                applyRead(cycleId, host.readCustomCycleDraft(cycleId))
            is NativeCustomDraftWriteResult.Rejected ->
                current.value = current.value.copy(refusal = result.reason,
                    message = "Could not save this new custom cycle.")
        }
    }

    private fun edit(
        reviewedSourceRevision: () -> Long? = { null },
        command: (CustomTrainingCycle) -> CustomCycleBuilderResult
    ) = run {
        val saved = current.value.draft ?: return@run
        val result = command(saved)
        when (result) {
            is CustomCycleBuilderResult.Rejected ->
                if (current.value.authoringRefusal == null && current.value.message == null)
                    current.value = current.value.copy(
                        message = "Edit refused: ${result.reason.name.replace('_', ' ').lowercase()}.")
            is CustomCycleBuilderResult.Proposed -> {
                val sourceRevision = reviewedSourceRevision()
                val write = if (sourceRevision == null)
                    host.saveCustomCycleDraft(result.draft,saved.revision)
                else host.saveReviewedCustomCycleDraft(
                    result.draft,saved.revision,sourceRevision)
                when (write) {
                    is NativeCustomDraftWriteResult.Committed -> {
                        lastUndo = result.receipt
                        auditedInputs = null
                        pendingWeekEdit = null
                        current.value = current.value.copy(
                            draft = result.draft,
                            cycleId = result.draft.cycleId,
                            coachingAudit = null,
                            authoredWorkloadEvidence = null,
                            evidenceSourceRevision = null,
                            auditSourceRevision = null,
                            reviewedCoachInputs = null,
                            strengthRepairReview = null,
                            strengthMenuRecoveryReview = null,
                            recoveryRecipeReview = null,
                            nextCycleSlot = null,
                            nextSlotCycleRevision = null,
                            weekEditReview = null,
                            signoff = host.readCustomCycleConsent(result.draft.cycleId),
                            signoffRefusal = null,
                            authoredPreview = null,
                            authoredPreviewRefusal = null,
                            consentBoundPreview = null,
                            preparedWorkout = null,
                            authoredReturnReview = null,
                            preparationRefusal = null,
                            canUndo = true,
                            message = "Saved revision ${write.revision} · ${result.receipt.operation.replace('-', ' ')}.")
                    }
                    is NativeCustomDraftWriteResult.Rejected -> {
                        // The disk wins, never a stale Compose draft. A storage
                        // failure should not cause this screen to show success.
                        lastUndo = null
                        val latest = host.readCustomCycleDraft(saved.cycleId)
                        applyRead(saved.cycleId, latest)
                        current.value = current.value.copy(
                            refusal = write.reason,
                            message = "Edit not saved: ${write.reason.name.replace('_', ' ').lowercase()}. Reloaded durable state.")
                    }
                }
            }
        }
    }

    /**
     * A changed on-screen policy draft is NOT the previously reviewed policy.
     * Invalidate the remembered audit and consent status without altering
     * any durable athlete consent, program, or issued workout. A fresh source-
     * and revision-backed review is required before another signoff.
     */
    fun markPolicyInputsChanged() {
        val previous=current.value
        if(previous.busy || (previous.coachingAudit==null &&
            previous.reviewedCoachInputs==null && previous.weekEditReview==null)) return
        auditedInputs=null
        pendingWeekEdit=null
        val staleSignoff=when(val old=previous.signoff) {
            is NativeCustomSignoffRead.Found->old.copy(isCurrent=false)
            else -> old
        }
        current.value=previous.copy(
            coachingAudit=null,
            reviewedCoachInputs=null,
            strengthRepairReview=null,
            strengthMenuRecoveryReview=null,
            recoveryRecipeReview=null,
            nextCycleSlot=null,
            nextSlotCycleRevision=null,
            weekEditReview=null,
            auditSourceRevision=null,
            consentBoundPreview=null,
            preparedWorkout=null,
            authoredReturnReview=null,
            signoff=staleSignoff,
            message="The policy draft changed. Its previous audit and signoff view are no longer current; run a complete new review.")
    }

    /**
     * Review exact authored workload from the SAME native full-cycle credit
     * engine. An intentionally missing coach policy produces factual volume
     * measurements only; it never supplies dose bounds, consent or issuance.
     * Rejects any different durable cycle, source, account or catalog before
     * publishing the read-only evidence back to the Compose state.
     */
    fun inspectAuthoredWorkloadEvidence() = run {
        val requested=current.value.draft ?:return@run
        val disk=host.readCustomCycleDraft(requested.cycleId)
        if(disk !is NativeCustomDraftReadResult.Found ||
            disk.draft!=requested) {
            applyRead(requested.cycleId,disk)
            current.value=current.value.copy(
                message="Custom cycle changed; reload the exact saved program before inspecting workload.")
            return@run
        }
        val source=runCatching{authoringSource.snapshot()}.getOrNull()
        if(source==null || source.athleteId!=disk.draft.athleteId) {
            current.value=current.value.copy(
                authoredWorkloadEvidence=null,evidenceSourceRevision=null,
                message="Reviewed native athlete, gym or exercise catalog unavailable.")
            return@run
        }
        val audited=CustomCycleCoachAuditDomain.review(
            disk.draft,source.setup,source.catalog,CustomCoachAuditInputs())
        val evidence=CustomCoachPolicyEvidenceDomain.review(audited)
        val latest=host.readCustomCycleDraft(requested.cycleId)
        val fresh=runCatching{authoringSource.snapshot()}.getOrNull()
        if(latest !is NativeCustomDraftReadResult.Found ||
            latest.draft!=disk.draft || fresh!=source) {
            applyRead(requested.cycleId,latest)
            current.value=current.value.copy(
                message="The native program or gym changed during workload review. Read again.")
            return@run
        }
        current.value=when(evidence) {
            is CustomCoachPolicyEvidenceReview.Reviewed ->
                current.value.copy(
                    authoredWorkloadEvidence=evidence.evidence,
                    evidenceSourceRevision=source.revision,
                    message="Authored weekly working sets and muscle credits verified; these are observations, not MEV/MRV prescriptions.")
            is CustomCoachPolicyEvidenceReview.Rejected ->
                current.value.copy(
                    authoredWorkloadEvidence=null,
                    evidenceSourceRevision=null,
                    message="Verified authored workload unavailable: ${evidence.reason.name.replace('_',' ').lowercase()}. No dose suggestion issued.")
        }
    }

    /**
     * Read-only, full-cycle coaching audit. Source and draft are read from
     * durable storage on IO; a second read refuses stale concurrent updates.
     * No default dose policy or transition time is supplied, and passing
     * the review NEVER creates an approved workout or a storage grant.
     */
    private fun auditReviewedSavedCycle(inputs:CustomCoachAuditInputs) {
        if(current.value.tieredDraft)return
        pendingWeekEdit=null
        current.value=current.value.copy(weekEditReview=null)
        val requested = current.value.draft ?: return
        val disk = host.readCustomCycleDraft(requested.cycleId)
        if(disk !is NativeCustomDraftReadResult.Found ||
            disk.draft.revision != requested.revision ||
            disk.draft != requested) {
            applyRead(requested.cycleId,disk)
            current.value=current.value.copy(
                message="The saved cycle changed. Reloaded before review; run the audit again.")
            return
        }
        val source=runCatching {authoringSource.snapshot()}.getOrNull()
        if(source==null || source.athleteId!=disk.draft.athleteId) {
            current.value=current.value.copy(
                coachingAudit=null,auditSourceRevision=null,
                reviewedCoachInputs=null,
                message="No current owner-reviewed native gym and catalog. Complete setup before coach review.")
            return
        }
        val result=CustomCycleCoachAuditDomain.review(
            disk.draft,source.setup,source.catalog,inputs)
        val currentSource=runCatching{authoringSource.snapshot()}.getOrNull()
        val latest=host.readCustomCycleDraft(disk.draft.cycleId)
        if(currentSource==null ||
            currentSource.revision!=source.revision ||
            latest !is NativeCustomDraftReadResult.Found ||
            latest.draft!=disk.draft) {
            applyRead(disk.draft.cycleId,latest)
            current.value=current.value.copy(
                message="Saved gym or program changed during audit; nothing accepted. Run a fresh review.")
            return
        }
        auditedInputs=inputs
        current.value=current.value.copy(
            coachingAudit=result,auditSourceRevision=source.revision,
            reviewedCoachInputs=inputs,
            // A new authored coaching review invalidates an earlier
            // strength proposal even if draft/source revisions match.
            strengthRepairReview=null,
            strengthMenuRecoveryReview=null,
            recoveryRecipeReview=null,
            signoff=host.readCustomCycleConsent(disk.draft.cycleId,inputs),
            signoffRefusal=null,
            message="Whole-cycle coach audit: ${result.issues.size} actionable issues across ${result.weeks.size} evaluated weeks. No workout approved.")
    }

    /** Normal manual/custom audit path. The same checks apply to generated plans. */
    fun auditFullCycle(
        inputs:CustomCoachAuditInputs=CustomCoachAuditInputs()
    )=run {auditReviewedSavedCycle(inputs)}

    /** Determine the next verified session from the complete actual native
     * journal/manifest census. No calendar-start date is inferred. */
    private fun selectNextSessionIfReviewed() {
        val value=current.value
        if(value.fixedNamedDraft || value.tieredDraft)return
        if(value.coachingAudit?.status==CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
            (value.signoff as? NativeCustomSignoffRead.Found)?.isCurrent==true)
            selectNextCycleSession()
    }

    fun findNextCycleSession()=run {selectNextCycleSession()}

    // Dedicated initial-tier approval never supplies ordinary session authority.
    private fun refreshTieredInitialReview(cycleId:String) {
        val before=current.value
        val draft=before.draft ?:return
        if(!before.tieredDraft || draft.cycleId!=cycleId)return
        val review=host.recoverTieredInitialCycle(cycleId)
        val ready=(review as? NativeSavedNamedReview.Ready)?.takeIf{
            it.draft==draft && it.initialTierReceipts==before.tieredReceipts && it.initialTierReceipts.isNotEmpty()
        }
        val signed=(review as? NativeSavedNamedReview.Signed)?.takeIf{
            it.draft==draft && it.initialTierReceipts==before.tieredReceipts &&
                it.initialTierReceipts.isNotEmpty() && it.nextTierCandidate==null
        }
        val singleThread=before.tieredReceipts.size==1
        val reviewedNext=signed?.let{host.reviewTieredInitialSession(cycleId,it.inputs)}
        val multiPosition=when(reviewedNext) {
            is NativeCustomNextCycleSlot.Next -> reviewedNext.absoluteWeek to reviewedNext.weekday
            is NativeCustomNextCycleSlot.Prepared -> reviewedNext.absoluteWeek to reviewedNext.weekday
            is NativeCustomNextCycleSlot.ResumeIssued -> reviewedNext.absoluteWeek to reviewedNext.weekday
            else -> null
        }
        val multiSupport=!singleThread && multiPosition!=null &&
            host.isMultiTieredSupportDay(cycleId,multiPosition.first,multiPosition.second)
        val multiInitial=multiPosition!=null && before.tieredReceipts.any{
            it.selection.absoluteWeek==multiPosition.first && it.selection.weekday==multiPosition.second
        }
        val initialNext=reviewedNext.takeIf{singleThread || multiInitial}
        val calendar=if(!singleThread && !multiInitial)reviewedNext
            else if(singleThread && ready==null && (initialNext==null || initialNext is NativeCustomNextCycleSlot.Rejected))
                host.reviewTieredCalendar(cycleId) else null
        val position=when(calendar) {
            is NativeCustomNextCycleSlot.Next -> calendar.absoluteWeek to calendar.weekday
            is NativeCustomNextCycleSlot.Prepared -> calendar.absoluteWeek to calendar.weekday
            else -> null
        }
        val supporting=multiSupport || before.tieredReceipt?.let{r->
            val block=draft.blocks.singleOrNull{it.blockId==r.blockId}
            position!=null && block!=null && position.second!=r.selection.weekday &&
                position.first in r.selection.absoluteWeek..(r.selection.absoluteWeek+block.weeks-r.weekInBlock) &&
                (position.first!=r.selection.absoluteWeek || position.second>r.selection.weekday)
        }==true
        val transition=before.tieredReceipts.any{r->
            val block=draft.blocks.singleOrNull{it.blockId==r.blockId}
            position!=null && block!=null && position.second==r.selection.weekday &&
                position.first in (r.selection.absoluteWeek+1)..(r.selection.absoluteWeek+block.weeks-r.weekInBlock)
        }
        current.value=before.copy(tieredInitialReview=ready ?:signed,
            tieredCalendar=calendar,tieredSupportAvailable=supporting,
            tieredTransitionAvailable=transition,tieredTransitionReview=null,staleTieredTransition=null,
            tieredTransitionPreview=null,tieredTransitionPrepared=null,
            tieredSupportWorkoutId=null,tieredSupportPreview=null,tieredSupportPrepared=null,
            signoff=null,coachingAudit=null,reviewedCoachInputs=null,
            nextCycleSlot=initialNext,
            nextSlotCycleRevision=signed?.draft?.revision,
            tieredInitialPreview=null,tieredInitialPrepared=null,
            message=when {
                ready!=null -> "Initial tier references verified. Review this exact revision."
                signed!=null && !singleThread -> "Multi-thread approval verified. Review the next scheduled workout."
                signed!=null -> "Exact initial tier approval restored and rechecked."
                calendar!=null && calendar !is NativeCustomNextCycleSlot.Rejected ->
                    "Initial tier review closed. Review the next scheduled workout."
                else -> "Initial tier review is unavailable or already completed. No ordinary workout can start."
            })
    }

    fun refreshTieredInitialCycle()=run {
        current.value.draft?.cycleId?.let{refreshTieredInitialReview(it)}
    }

    fun approveTieredInitialCycle(displayed:NativeSavedNamedReview.Ready)=run {
        val before=current.value
        val draft=before.draft ?:return@run
        if(!before.tieredDraft || before.tieredInitialReview!=displayed ||
            displayed.draft!=draft || displayed.initialTierReceipts.isEmpty() ||
            displayed.initialTierReceipts!=before.tieredReceipts)return@run
        val result=host.confirmTieredInitialCycle(draft.cycleId,displayed,true)
        refreshTieredInitialReview(draft.cycleId)
        if(result !is NativeCustomSignoffWrite.Committed)
            current.value=current.value.copy(message="Initial tier approval refused. Refresh the saved review.")
    }

    private fun selectedInitialTier(review:NativeSavedNamedReview.Signed,
        next:NativeCustomNextCycleSlot?):NativeTieredInitialSelection? {
        val position=when(next) {
            is NativeCustomNextCycleSlot.Next -> next.absoluteWeek to next.weekday
            is NativeCustomNextCycleSlot.Prepared -> next.absoluteWeek to next.weekday
            else -> return null
        }
        return review.initialTierReceipts.firstOrNull{r->
            r.selection.absoluteWeek==position.first && r.selection.weekday==position.second &&
                (next !is NativeCustomNextCycleSlot.Prepared || next.workoutId==r.selection.nextWorkoutId)
        }?.selection
    }

    fun previewTieredInitialWorkout()=run {
        val before=current.value
        val review=before.tieredInitialReview as? NativeSavedNamedReview.Signed ?:return@run
        if(before.draft!=review.draft)return@run
        val next=host.reviewTieredInitialSession(review.draft.cycleId,review.inputs)
        val selection=selectedInitialTier(review,next) ?:return@run
        val shown=(host.previewTieredInitialWorkout(selection.nextWorkoutId,review.draft.cycleId,
            selection.absoluteWeek,selection.weekday,review.inputs) as? NativeCustomPreflightResult.ReviewedPreview)
            ?.takeIf{it.receipt==review.consent && it.session.stamp.cycleRevision==review.draft.revision}
        val saved=if(shown!=null && next is NativeCustomNextCycleSlot.Prepared)
            (host.openPreparedCustomWorkout(selection.nextWorkoutId) as? NativeCustomPreparedRead.Found)?.workout
                ?.takeIf{it.authority==NativeCustomPreparationAuthority.TIERED_INITIAL &&
                    it.snapshot==shown.session && it.consentFingerprint==shown.receipt.fingerprintSha256 &&
                    it.consentRevision==shown.receipt.recordRevision}
        else null
        current.value=before.copy(nextCycleSlot=next,tieredInitialPreview=shown,tieredInitialPrepared=saved,
            message=if(shown!=null)"Review exact initial workout before preparing."
                else "Initial workout or saved preparation could not be verified.")
    }

    fun prepareTieredInitialWorkout()=run {
        val before=current.value
        val review=before.tieredInitialReview as? NativeSavedNamedReview.Signed ?:return@run
        val shown=before.tieredInitialPreview ?:return@run
        val selection=selectedInitialTier(review,before.nextCycleSlot) ?:return@run
        if(shown.session.absoluteWeek!=selection.absoluteWeek || shown.session.weekday!=selection.weekday ||
            before.draft!=review.draft || shown.receipt!=review.consent ||
            before.nextCycleSlot !is NativeCustomNextCycleSlot.Next)return@run
        val result=host.prepareTieredInitialWorkout(selection.nextWorkoutId,review.draft.cycleId,shown,review.inputs)
        val saved=(result as? NativeCustomPrepareResult.Prepared)?.workout?.takeIf{
            it.authority==NativeCustomPreparationAuthority.TIERED_INITIAL && it.snapshot==shown.session &&
                it.consentFingerprint==shown.receipt.fingerprintSha256 &&
                it.consentRevision==shown.receipt.recordRevision &&
                (host.openPreparedCustomWorkout(selection.nextWorkoutId) as? NativeCustomPreparedRead.Found)?.workout==it
        }
        current.value=before.copy(tieredInitialPrepared=saved,
            nextCycleSlot=host.reviewTieredInitialSession(review.draft.cycleId,review.inputs),
            message=if(saved!=null)"Exact initial workout prepared. Not issued."
                else "Preparation refused or changed. Refresh the review.")
    }

    fun issueTieredInitialWorkout()=run {
        val before=current.value
        val review=before.tieredInitialReview as? NativeSavedNamedReview.Signed ?:return@run
        val saved=before.tieredInitialPrepared ?:return@run
        val shown=before.tieredInitialPreview ?:return@run
        if(before.draft!=review.draft || shown.receipt!=review.consent || saved.snapshot!=shown.session)return@run
        val result=host.issueTieredInitialWorkout(saved.workoutId,review.draft.cycleId,saved,review.inputs)
        refreshTieredInitialReview(review.draft.cycleId)
        current.value=current.value.copy(message=if(result is NativeCustomJournalResult.Durable &&
            result.issued.journal.originalSnapshot==saved.snapshot)
            "Initial workout issued. Resume in Train to log actuals."
            else "Initial issuance refused. Refresh the review.")
    }

    fun previewTieredSupportWorkout()=run {
        val before=current.value
        val draft=before.draft ?:return@run
        if(!before.tieredDraft || !before.tieredSupportAvailable)return@run
        val next=host.reviewTieredCalendar(draft.cycleId)
        if(next!=before.tieredCalendar) {
            refreshTieredInitialReview(draft.cycleId)
            return@run
        }
        val (id,week,day)=when(next) {
            is NativeCustomNextCycleSlot.Next -> Triple("support-"+UUID.randomUUID().toString(),next.absoluteWeek,next.weekday)
            is NativeCustomNextCycleSlot.Prepared -> Triple(next.workoutId,next.absoluteWeek,next.weekday)
            else -> return@run
        }
        val shown=(host.previewTieredSupportWorkout(id,draft.cycleId,week,day)
            as? NativeCustomPreflightResult.ReviewedPreview)
            ?.takeIf{it.session.stamp.cycleRevision==draft.revision && it.session.stamp.cycleId==draft.cycleId}
        val saved=if(shown!=null && next is NativeCustomNextCycleSlot.Prepared)
            (host.openPreparedCustomWorkout(id) as? NativeCustomPreparedRead.Found)?.workout
                ?.takeIf{it.authority==NativeCustomPreparationAuthority.TIERED_SUPPORT &&
                    it.snapshot==shown.session && it.consentFingerprint==shown.receipt.fingerprintSha256 &&
                    it.consentRevision==shown.receipt.recordRevision}
        else null
        current.value=before.copy(tieredSupportWorkoutId=id.takeIf{shown!=null},
            tieredSupportPreview=shown,tieredSupportPrepared=saved,
            message=if(shown!=null)"Review the exact supporting workout."
                else "Supporting workout or saved preparation could not be verified.")
    }

    fun prepareTieredSupportWorkout()=run {
        val before=current.value
        val draft=before.draft ?:return@run
        val shown=before.tieredSupportPreview ?:return@run
        val id=before.tieredSupportWorkoutId ?:return@run
        if(!before.tieredSupportAvailable || before.tieredCalendar !is NativeCustomNextCycleSlot.Next)return@run
        val result=host.prepareTieredSupportWorkout(id,draft.cycleId,shown)
        val saved=(result as? NativeCustomPrepareResult.Prepared)?.workout?.takeIf{
            it.authority==NativeCustomPreparationAuthority.TIERED_SUPPORT &&
                it.snapshot==shown.session && it.consentFingerprint==shown.receipt.fingerprintSha256 &&
                it.consentRevision==shown.receipt.recordRevision &&
                (host.openPreparedCustomWorkout(id) as? NativeCustomPreparedRead.Found)?.workout==it
        }
        if(saved==null) {
            refreshTieredInitialReview(draft.cycleId)
            current.value=current.value.copy(message="Supporting preparation refused or changed. Review again.")
            return@run
        }
        current.value=before.copy(tieredSupportPrepared=saved,
            tieredCalendar=host.reviewTieredCalendar(draft.cycleId),
            message="Exact supporting workout prepared. Not issued.")
    }

    fun issueTieredSupportWorkout()=run {
        val before=current.value
        val draft=before.draft ?:return@run
        val shown=before.tieredSupportPreview ?:return@run
        val saved=before.tieredSupportPrepared ?:return@run
        if(!before.tieredSupportAvailable || saved.workoutId!=before.tieredSupportWorkoutId ||
            saved.snapshot!=shown.session || saved.consentFingerprint!=shown.receipt.fingerprintSha256 ||
            saved.consentRevision!=shown.receipt.recordRevision)return@run
        val result=host.issueTieredSupportWorkout(draft.cycleId,saved)
        refreshTieredInitialReview(draft.cycleId)
        current.value=current.value.copy(message=if(result is NativeCustomJournalResult.Durable &&
            result.issued.journal.originalSnapshot==saved.snapshot)
            "Supporting workout issued. Resume in Train to log actuals."
            else "Supporting issuance refused. Refresh the review.")
    }

    /** Restore an existing receipt before making a fresh candidate.
     * A changed history must never silently replace an approved transition. */
    fun previewTieredTransition(recovery:Boolean,recoverySlots:Set<String> = emptySet())=run {
        val before=current.value
        val draft=before.draft ?:return@run
        if(!before.tieredDraft || !before.tieredTransitionAvailable)return@run
        val next=host.reviewTieredCalendar(draft.cycleId)
        if(next!=before.tieredCalendar) {
            refreshTieredInitialReview(draft.cycleId)
            return@run
        }
        val (week,day)=when(next) {
            is NativeCustomNextCycleSlot.Next -> next.absoluteWeek to next.weekday
            is NativeCustomNextCycleSlot.Prepared -> next.absoluteWeek to next.weekday
            else -> return@run
        }
        when(val restored=host.recoverTieredTransition(draft.cycleId,week,day)) {
            is NativeTieredTransitionRecovery.Ready ->
                showApprovedTieredTransition(before,restored.review)
            is NativeTieredTransitionRecovery.Refused -> {
                if(restored.reason!=NativeTieredTransitionConsentResult.MISSING ||
                    next !is NativeCustomNextCycleSlot.Next) {
                    val stale=if(before.tieredReceipts.size==1)host.reviewStaleTieredTransition(draft.cycleId,week,day) else null
                    current.value=before.copy(tieredTransitionReview=stale?.review,staleTieredTransition=stale,
                        tieredTransitionPreview=null,tieredTransitionPrepared=null,
                        message=if(stale!=null)"Corrected history changed the approval. Review the old and new targets."
                            else "Saved tier approval cannot be verified. Review history before continuing.")
                    return@run
                }
                val id="tier-"+UUID.randomUUID().toString()
                val shown=if(before.tieredReceipts.size>1) {
                    val selection=NativeMultiTierNextSelection(id,week,day,recoverySlots)
                    (host.previewMultiTieredTransition(draft.cycleId,selection) as? NativeSavedNamedReview.Signed)
                        ?.takeIf{it.draft==draft && it.nextMultiTierCandidate?.selection==selection}
                } else {
                    val selection=NativeTieredNextSelection(id,week,recovery)
                    (host.previewTieredTransition(draft.cycleId,day,selection) as? NativeSavedNamedReview.Signed)
                        ?.takeIf{it.draft==draft && it.nextTierCandidate?.selection==selection}
                }
                current.value=before.copy(tieredTransitionReview=shown,staleTieredTransition=null,tieredTransitionPreview=null,
                    tieredTransitionPrepared=null,
                    message=if(shown!=null)"Review the next tier targets before approving."
                        else "No verified next tier targets are available.")
            }
        }
    }

    private fun showApprovedTieredTransition(before:NativeKotlinCustomCycleState,review:NativeSavedNamedReview.Signed) {
        val draft=before.draft ?:return
        val multi=review.nextMultiTierCandidate?.selection
        val single=review.nextTierCandidate?.selection
        val day=multi?.weekday ?:review.initialTierReceipt?.selection?.weekday ?:return
        val week=multi?.absoluteWeek ?:single?.absoluteWeek ?:return
        if(review.draft!=draft)return
        val id=multi?.nextWorkoutId ?:single?.nextWorkoutId ?:return
        val shown=(host.previewTieredTransitionWorkout(draft.cycleId,id,week,day)
            as? NativeFirstTierTransitionPreflight.Reviewed)?.takeIf{it.review==review}
        val next=host.reviewTieredCalendar(draft.cycleId)
        val saved=if(shown!=null && next is NativeCustomNextCycleSlot.Prepared && next.workoutId==id)
            (host.openPreparedCustomWorkout(id) as? NativeCustomPreparedRead.Found)?.workout
                ?.takeIf{it.authority==NativeCustomPreparationAuthority.TIERED_TRANSITION &&
                    it.snapshot==shown.session && it.consentFingerprint==shown.receiptFingerprint &&
                    it.consentRevision==review.consent.recordRevision}
        else null
        current.value=before.copy(tieredCalendar=next,tieredTransitionReview=review,staleTieredTransition=null,
            tieredTransitionPreview=shown,tieredTransitionPrepared=saved,
            message=if(shown!=null)"Approved tier workout verified. Review all targets before preparing."
                else "Approved tier workout cannot be verified.")
    }

    fun approveTieredTransition(reset:Boolean,recovery:Boolean,resetSlots:Set<String> = emptySet(),
        recoverySlots:Set<String> = emptySet())=run {
        val before=current.value
        val draft=before.draft ?:return@run
        val shown=before.tieredTransitionReview ?:return@run
        if(!before.tieredTransitionAvailable || before.tieredTransitionPreview!=null ||
            shown.draft!=draft)return@run
        val stale=before.staleTieredTransition
        if(stale?.preparation!=null)return@run
        val result=if(stale!=null)host.replaceStaleTieredTransition(draft.cycleId,stale,reset,recovery)
            else if(shown.nextMultiTierCandidate!=null)
                host.confirmMultiTieredTransition(draft.cycleId,shown,resetSlots,recoverySlots)
            else host.confirmTieredTransition(draft.cycleId,shown,reset,recovery)
        if(result in setOf(NativeTieredTransitionConsentResult.RECORDED,
                NativeTieredTransitionConsentResult.REPLAYED,NativeTieredTransitionConsentResult.REPLACED))
            showApprovedTieredTransition(before,shown)
        else current.value=before.copy(message="Tier approval refused: "+result.name.lowercase().replace('_',' '))
    }

    fun discardStaleTieredPreparation()=run {
        val before=current.value
        val draft=before.draft ?:return@run
        val stale=before.staleTieredTransition ?:return@run
        val saved=stale.preparation ?:return@run
        if(!before.tieredTransitionAvailable || stale.review!=before.tieredTransitionReview)return@run
        val removed=host.cancelStaleTieredTransition(draft.cycleId,stale)
        refreshTieredInitialReview(draft.cycleId)
        if(removed) {
            val fresh=host.reviewStaleTieredTransition(draft.cycleId,saved.snapshot.absoluteWeek,saved.snapshot.weekday)
            current.value=current.value.copy(tieredTransitionReview=fresh?.review,staleTieredTransition=fresh,
                message="Old preparation discarded. Review the corrected targets before replacing approval.")
        } else current.value=current.value.copy(message="Preparation was not discarded. Refresh and verify history.")
    }

    fun prepareTieredTransitionWorkout()=run {
        val before=current.value
        val draft=before.draft ?:return@run
        val shown=before.tieredTransitionPreview ?:return@run
        val id=shown.review.nextMultiTierCandidate?.selection?.nextWorkoutId
            ?:shown.review.nextTierCandidate?.selection?.nextWorkoutId ?:return@run
        if(!before.tieredTransitionAvailable || before.tieredCalendar !is NativeCustomNextCycleSlot.Next)return@run
        val result=host.prepareTieredTransitionWorkout(draft.cycleId,shown)
        val saved=(result as? NativeCustomPrepareResult.Prepared)?.workout?.takeIf{
            it.authority==NativeCustomPreparationAuthority.TIERED_TRANSITION &&
                it.snapshot==shown.session && it.consentFingerprint==shown.receiptFingerprint &&
                it.consentRevision==shown.review.consent.recordRevision &&
                (host.openPreparedCustomWorkout(id) as? NativeCustomPreparedRead.Found)?.workout==it
        }
        if(saved==null) {
            refreshTieredInitialReview(draft.cycleId)
            current.value=current.value.copy(message="Tier preparation refused. Review the saved transition again.")
            return@run
        }
        current.value=before.copy(tieredTransitionPrepared=saved,
            tieredCalendar=host.reviewTieredCalendar(draft.cycleId),message="Exact tier workout prepared. Not issued.")
    }

    fun issueTieredTransitionWorkout()=run {
        val before=current.value
        val draft=before.draft ?:return@run
        val shown=before.tieredTransitionPreview ?:return@run
        val saved=before.tieredTransitionPrepared ?:return@run
        if(!before.tieredTransitionAvailable || saved.snapshot!=shown.session ||
            saved.consentFingerprint!=shown.receiptFingerprint ||
            saved.consentRevision!=shown.review.consent.recordRevision)return@run
        val result=host.issueTieredTransitionWorkout(draft.cycleId,saved)
        refreshTieredInitialReview(draft.cycleId)
        current.value=current.value.copy(message=if(result is NativeCustomJournalResult.Durable &&
            result.issued.journal.originalSnapshot==saved.snapshot)
            "Tier workout issued. Resume in Train to log actuals."
            else "Tier issuance refused. Refresh the saved transition.")
    }

    // Fixed named flow owns distinct review state. Never publish ordinary
    // consent flags for a specialist draft or turn a preview into a grant.
    private fun refreshFixedNamedReview(cycleId:String) {
        val before=current.value
        val draft=before.draft ?:return
        if(draft.cycleId!=cycleId || !before.fixedNamedDraft)return
        val review=host.recoverFixedNamedCycle(cycleId)
        val signed=(review as? NativeSavedNamedReview.Signed)?.takeIf{it.draft==draft}
        val pending=(review as? NativeSavedNamedReview.Ready)?.takeIf{it.draft==draft}
        current.value=before.copy(fixedNamedReview=signed ?:pending,
            nextCycleSlot=signed?.let{host.reviewNextCustomCycleSession(cycleId,it.inputs)},
            nextSlotCycleRevision=signed?.draft?.revision,
            fixedNamedWorkoutId=null,fixedNamedPreview=null,fixedNamedPrepared=null,
            fixedNamedIssuedId=null,
            message=when {
                signed!=null -> "Exact specialist consent restored and rechecked."
                pending!=null -> "Reference saved. Review and approve this revised cycle before starting."
                else -> "Specialist consent could not be verified. No workout can be prepared."
            })
    }

    fun refreshFixedNamedCycle()=run {
        current.value.draft?.cycleId?.let{refreshFixedNamedReview(it)}
    }

    fun reviewTieredInitialChoices(choices:List<NativeTieredInitialChoice>)=run {
        val before=current.value
        val draft=before.draft ?:return@run
        if(before.fixedNamedDraft || before.tieredDraft)return@run
        current.value=before.copy(tieredReferenceBatch=null,fixedNamedBatch=null)
        val result=host.previewTieredInitialChoices(draft.cycleId,choices)
        val ready=(result as? NativeTieredInitialBatchReview.Ready)?.takeIf{
            it.individual.all{review->review.source.cycle==draft} &&
                it.selections.map{selection->NativeTieredInitialChoice.from(selection)}==choices
        }
        current.value=current.value.copy(tieredReferenceBatch=ready,
            message=if(ready!=null)"Review all exact initial targets before saving."
                else "Tier reference preview refused: "+(result as? NativeTieredInitialBatchReview.Rejected)
                    ?.reason?.name.orEmpty().lowercase().replace('_',' '))
    }

    fun acceptTieredInitialChoices(displayed:NativeTieredInitialBatchReview.Ready)=run {
        val before=current.value
        val draft=before.draft ?:return@run
        if(before.fixedNamedDraft || before.tieredDraft || before.tieredReferenceBatch!=displayed ||
            displayed.individual.any{it.source.cycle!=draft})return@run
        val result=host.acceptTieredInitialChoices(displayed,true)
        if(result !is NativeTieredInitialBatchAcceptance.Saved) {
            current.value=before.copy(tieredReferenceBatch=null,
                message="Tier reference review changed or was refused. Nothing approved.")
            return@run
        }
        applyRead(draft.cycleId,host.readCustomCycleDraft(draft.cycleId))
        refreshTieredInitialReview(draft.cycleId)
    }

    fun reviewFixedNamedChoices(choices:List<NativeFixedNamedChoice>)=run {
        val before=current.value
        val draft=before.draft ?:return@run
        current.value=before.copy(fixedNamedBatch=null,tieredReferenceBatch=null)
        if(before.fixedNamedDraft || before.tieredDraft)return@run
        val result=host.previewFixedNamedCycle(draft.cycleId,choices)
        val ready=(result as? NativeNamedBatchReview.Ready)?.takeIf{
            it.individual.all{review->review.source.cycle==draft}
        }
        current.value=current.value.copy(fixedNamedBatch=ready,
            message=if(ready!=null)"Review the exact specialist targets before saving."
                else "Specialist preview refused: "+(result as? NativeNamedBatchReview.Rejected)
                    ?.reason?.name.orEmpty().lowercase().replace('_',' '))
    }

    fun acceptFixedNamedChoices(displayed:NativeNamedBatchReview.Ready)=run {
        val before=current.value
        val draft=before.draft ?:return@run
        if(before.fixedNamedDraft || before.tieredDraft || before.fixedNamedBatch!=displayed ||
            displayed.individual.any{it.source.cycle!=draft})return@run
        val saved=host.acceptFixedNamedCycle(displayed,true)
        if(saved !is NativeNamedBatchAcceptance.Saved) {
            current.value=before.copy(fixedNamedBatch=null,
                message="Reference review changed or was refused. Nothing approved.")
            return@run
        }
        applyRead(draft.cycleId,host.readCustomCycleDraft(draft.cycleId))
        refreshFixedNamedReview(draft.cycleId)
    }

    fun approveFixedNamedCycle(displayed:NativeSavedNamedReview.Ready)=run {
        val before=current.value
        val draft=before.draft ?:return@run
        if(before.fixedNamedReview!=displayed || displayed.draft!=draft)return@run
        val saved=host.confirmFixedNamedCycle(draft.cycleId,displayed,true)
        refreshFixedNamedReview(draft.cycleId)
        if(saved !is NativeCustomSignoffWrite.Committed)
            current.value=current.value.copy(message="Specialist approval refused. Recheck the saved review.")
    }

    fun revokeFixedNamedCycle()=run {
        val before=current.value
        val review=before.fixedNamedReview as? NativeSavedNamedReview.Signed ?:return@run
        host.revokeCustomCycleConsent(review.draft.cycleId,review.consent.recordRevision)
        refreshFixedNamedReview(review.draft.cycleId)
    }

    fun previewNextFixedNamedWorkout()=run {
        val before=current.value
        val review=before.fixedNamedReview as? NativeSavedNamedReview.Signed ?:return@run
        val draft=before.draft ?:return@run
        if(review.draft!=draft)return@run
        val selection=host.reviewNextCustomCycleSession(draft.cycleId,review.inputs)
        val (id,week,day)=when(selection) {
            is NativeCustomNextCycleSlot.Next ->
                Triple("custom-"+UUID.randomUUID().toString().replace("-",""),selection.absoluteWeek,selection.weekday)
            is NativeCustomNextCycleSlot.Prepared ->
                Triple(selection.workoutId,selection.absoluteWeek,selection.weekday)
            else -> {
                current.value=before.copy(nextCycleSlot=selection,fixedNamedWorkoutId=null,
                    fixedNamedPreview=null,fixedNamedPrepared=null)
                return@run
            }
        }
        val checked=host.previewFixedNamedWorkout(id,draft.cycleId,week,day,review.inputs)
        val ready=(checked as? NativeCustomPreflightResult.ReviewedPreview)?.takeIf{
            it.receipt==review.consent && it.session.stamp.cycleRevision==draft.revision
        }
        val restored=if(ready!=null && selection is NativeCustomNextCycleSlot.Prepared)
            (host.openPreparedCustomWorkout(id) as? NativeCustomPreparedRead.Found)?.workout
                ?.takeIf{it.authority==NativeCustomPreparationAuthority.NAMED_CYCLE &&
                    it.snapshot==ready.session && it.consentFingerprint==ready.receipt.fingerprintSha256 &&
                    it.consentRevision==ready.receipt.recordRevision}
        else null
        current.value=before.copy(nextCycleSlot=selection,fixedNamedWorkoutId=id.takeIf{ready!=null},
            fixedNamedPreview=ready,fixedNamedPrepared=restored,
            message=if(ready!=null && (selection !is NativeCustomNextCycleSlot.Prepared || restored!=null))
                "Exact specialist workout verified. Review before starting."
                else "Specialist workout or saved preparation could not be verified.")
    }

    fun prepareNextFixedNamedWorkout()=run {
        val before=current.value
        val review=before.fixedNamedReview as? NativeSavedNamedReview.Signed ?:return@run
        val shown=before.fixedNamedPreview ?:return@run
        val id=before.fixedNamedWorkoutId ?:return@run
        if(before.nextCycleSlot !is NativeCustomNextCycleSlot.Next ||
            shown.receipt!=review.consent || before.draft!=review.draft)return@run
        val result=host.prepareFixedNamedWorkout(id,review.draft.cycleId,
            shown.session.absoluteWeek,shown.session.weekday,review.inputs)
        val saved=if(result is NativeCustomPrepareResult.Prepared)
            (host.openPreparedCustomWorkout(id) as? NativeCustomPreparedRead.Found)?.workout
                ?.takeIf{it==result.workout && it.authority==NativeCustomPreparationAuthority.NAMED_CYCLE &&
                    it.snapshot==shown.session && it.consentFingerprint==shown.receipt.fingerprintSha256 &&
                    it.consentRevision==shown.receipt.recordRevision}
        else null
        current.value=before.copy(fixedNamedPrepared=saved,
            message=if(saved!=null)"Exact workout prepared. Not issued."
                else "Specialist preparation refused or changed. Nothing issued.")
    }

    fun issueNextFixedNamedWorkout()=run {
        val before=current.value
        val review=before.fixedNamedReview as? NativeSavedNamedReview.Signed ?:return@run
        val shown=before.fixedNamedPreview ?:return@run
        val saved=before.fixedNamedPrepared ?:return@run
        if(before.draft!=review.draft || shown.receipt!=review.consent ||
            saved.snapshot!=shown.session || saved.authority!=NativeCustomPreparationAuthority.NAMED_CYCLE ||
            saved.consentRevision!=shown.receipt.recordRevision ||
            saved.consentFingerprint!=shown.receipt.fingerprintSha256 ||
            (host.openPreparedCustomWorkout(saved.workoutId) as? NativeCustomPreparedRead.Found)?.workout!=saved)
            return@run
        val issued=host.issueFixedNamedWorkout(saved.workoutId,review.draft.cycleId,
            shown.session.absoluteWeek,shown.session.weekday,review.inputs)
        val durable=(issued as? NativeCustomJournalResult.Durable)?.issued?.journal
            ?.takeIf{it.originalSnapshot==saved.snapshot}
        current.value=before.copy(fixedNamedIssuedId=durable?.workoutId,
            fixedNamedPrepared=null,fixedNamedPreview=null,
            nextCycleSlot=host.reviewNextCustomCycleSession(review.draft.cycleId,review.inputs),
            message=if(durable!=null)"Workout issued. Open Train to log actuals."
                else "Specialist issuance refused. Refresh the next workout.")
    }

    private fun selectNextCycleSession() {
        val before=current.value
        if(before.tieredDraft)return
        val cycle=before.draft ?:return
        val inputs=auditedInputs
        if(inputs==null ||
            before.coachingAudit?.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
            (before.signoff as? NativeCustomSignoffRead.Found)?.isCurrent!=true) {
            current.value=before.copy(nextCycleSlot=null,
                nextSlotCycleRevision=null,
                message="Review and sign this exact training cycle before selecting the next workout.")
            return
        }
        val selected=host.reviewNextCustomCycleSession(cycle.cycleId,inputs)
        val latest=host.readCustomCycleDraft(cycle.cycleId)
        if(latest !is NativeCustomDraftReadResult.Found ||
            latest.draft!=cycle) {
            applyRead(cycle.cycleId,latest)
            current.value=current.value.copy(
                message="The saved cycle changed while selecting the next workout. Reopen it.")
            return
        }
        current.value=before.copy(
            nextCycleSlot=selected,
            nextSlotCycleRevision=cycle.revision,
            message=when(selected) {
                is NativeCustomNextCycleSlot.Next ->
                    "This is the next unfinished session in your saved cycle, not an inferred calendar date. Verify it before preparing."
                is NativeCustomNextCycleSlot.Prepared ->
                    "The next session has a saved preparation. Reopen and verify it rather than creating a duplicate."
                is NativeCustomNextCycleSlot.ResumeIssued ->
                    "An issued session is unfinished. Resume its exact journal instead of starting another."
                NativeCustomNextCycleSlot.CycleComplete ->
                    "Every scheduled session in this cycle has verified completed history."
                is NativeCustomNextCycleSlot.Rejected ->selected.reason
            })
    }

    /**
     * Open a newly saved generated program AND re-audit its reviewed policy
     * in one IO operation. Never trust a transient preview or grant consent;
     * disk source, draft and revision are checked twice by the audit.
     */
    fun openSavedFirstProgramForReview(
        cycleId:String,inputs:CustomCoachAuditInputs
    )=enter {
        applyRead(cycleId,host.readCustomCycleDraft(cycleId))
        if(current.value.draft?.cycleId!=cycleId)return@enter
        when(val recovered=host.recoverSavedFirstProgramReview(cycleId)) {
            is NativeRecoveredFirstProgramReview.Ready ->
                if(recovered.inputs==inputs) {
                    auditReviewedSavedCycle(recovered.inputs)
                    selectNextSessionIfReviewed()
                } else current.value=current.value.copy(
                    message="The generated coaching policy changed since you reviewed it. Reopen and review the saved program.")
            NativeRecoveredFirstProgramReview.NotGenerated ->
                current.value=current.value.copy(
                    message="No durable generated coaching policy exists for this custom program. Run a manual full-cycle audit.")
            is NativeRecoveredFirstProgramReview.Rejected ->
                current.value=current.value.copy(message=recovered.reason)
        }
    }

    /**
     * Only direct UI consent after a fresh, policy-complete audit.
     * The store checks both current source and draft AGAIN under owner lock.
     * This never calls Composer approval or workout issuance.
     */
    fun confirmAthleteConsent(confirmed:Boolean) = run {
        val value=current.value
        val draft=value.draft ?: return@run
        val audit=value.coachingAudit ?: return@run
        val inputs=auditedInputs
        if(!confirmed || inputs==null ||
            audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
            audit.issues.isNotEmpty() || audit.auditedCycleRevision!=draft.revision ||
            value.auditSourceRevision==null) {
            current.value=current.value.copy(
                message="Athlete confirmation and a complete reviewed coaching policy are required. No approval saved.",
                signoffRefusal=NativeCustomSignoffRefusal.COACH_REVIEW_UNRESOLVED)
            return@run
        }
        val previous=host.readCustomCycleConsent(draft.cycleId,inputs)
        val expected=when(previous) {
            NativeCustomSignoffRead.Missing -> 0L
            is NativeCustomSignoffRead.Found -> previous.receipt.recordRevision
            is NativeCustomSignoffRead.Revoked -> previous.recordRevision
            is NativeCustomSignoffRead.Rejected -> {
                current.value=current.value.copy(
                    signoff=previous,
                    message="Existing consent could not be verified. Nothing overwritten.")
                return@run
            }
        }
        val saved=host.confirmCustomCycleConsent(draft.cycleId,draft.revision,
            value.auditSourceRevision,expected,inputs,true)
        val now=host.readCustomCycleConsent(draft.cycleId,inputs)
        when(saved) {
            is NativeCustomSignoffWrite.Committed -> {
                current.value=current.value.copy(
                    signoff=now,signoffRefusal=null,
                    message="Your custom cycle review and explicit consent were saved. This is not a workout approval or an issued session.")
                selectNextSessionIfReviewed()
            }
            is NativeCustomSignoffWrite.Rejected -> {
                current.value=current.value.copy(
                    signoff=now,signoffRefusal=saved.reason,
                    message="Consent was not saved: ${saved.reason.name.replace('_',' ').lowercase()}. Recheck your gym, program and policy.")
            }
        }
    }

    fun revokeAthleteConsent() = run {
        val draft=current.value.draft ?:return@run
        val previous=host.readCustomCycleConsent(draft.cycleId)
        val receipt=(previous as? NativeCustomSignoffRead.Found)?.receipt
            ?:return@run
        when(val result=host.revokeCustomCycleConsent(
            draft.cycleId,receipt.recordRevision)) {
            is NativeCustomSignoffWrite.Committed -> {
                current.value=current.value.copy(
                    signoff=host.readCustomCycleConsent(draft.cycleId),
                    signoffRefusal=null,
                    message="Athlete consent revoked. Saved custom program and workout history were not modified.")
            }
            is NativeCustomSignoffWrite.Rejected -> {
                current.value=current.value.copy(
                    signoff=host.readCustomCycleConsent(draft.cycleId),
                    signoffRefusal=result.reason,
                    message="Consent revocation was not saved; refreshed authoritative record.")
            }
        }
    }

    /**
     * Display ONLY a lossless authored-week preview. A structurally valid
     * athlete draft is not a consent, grant, or workout.
     */
    fun previewAuthoredDay(absoluteWeek:Int,weekday:Int) = run {
        current.value=current.value.copy(dayCoaching=null,basicDayProgression=null,
            basicDayProgressionSlotId=null,authoredReturnReview=null,
            preparedWorkout=current.value.preparedWorkout?.takeIf{
                it.snapshot.absoluteWeek==absoluteWeek&&it.snapshot.weekday==weekday})
        val draft=current.value.draft ?:return@run
        val durable=host.readCustomCycleDraft(draft.cycleId)
        val source=runCatching{authoringSource.snapshot()}.getOrNull()
        if(durable !is NativeCustomDraftReadResult.Found ||
            durable.draft!=draft ||
            source==null || source.athleteId!=draft.athleteId) {
            applyRead(draft.cycleId,durable)
            current.value=current.value.copy(
                message="The saved cycle or gym changed. Reloaded; choose the week again.")
            return@run
        }
        val result=CustomWorkoutSnapshotDomain.preview(
            draft,source.setup,source.catalog,source.revision,
            absoluteWeek,weekday)
        val freshSource=runCatching{authoringSource.snapshot()}.getOrNull()
        val latest=host.readCustomCycleDraft(draft.cycleId)
        if(freshSource?.revision!=source.revision ||
            latest !is NativeCustomDraftReadResult.Found ||
            latest.draft!=draft) {
            applyRead(draft.cycleId,latest)
            current.value=current.value.copy(
                message="Gym or program changed during preview. Reloaded authoritative draft.")
            return@run
        }
        current.value=when(result) {
            is CustomWorkoutSnapshotResult.Preview ->
                current.value.copy(authoredPreview=result.snapshot,
                    authoredPreviewRefusal=null,
                    consentBoundPreview=null,
                    message="Showing exact week ${absoluteWeek}, day ${weekday} authored prescriptions. Preview only; nothing issued.")
            is CustomWorkoutSnapshotResult.Rejected ->
                current.value.copy(authoredPreview=null,
                    authoredPreviewRefusal=result.reason,
                    consentBoundPreview=null,
                    message="Cannot preview: ${result.reason.name.replace('_',' ').lowercase()}.")
        }
    }

    /**
     * Independent basic progression. The native owner authenticates the
     * signed program, gym and complete corrected history; this UI never
     * supplies observed sets or approves the resulting proposal.
     */
    fun reviewAuthoredDayBasicProgression(
        absoluteWeek:Int,weekday:Int,slotId:String,
        maximumIncrement:java.math.BigDecimal
    ) = run {
        current.value=current.value.copy(basicDayProgression=null,
            basicDayProgressionSlotId=null)
        val draft=current.value.draft ?:return@run
        val inputs=auditedInputs
        if(inputs==null) {
            current.value=current.value.copy(message=
                "Review the full saved training cycle before previewing basic progression.")
            return@run
        }
        val result=host.reviewNextAuthoredBasicProgression(
            draft.cycleId,absoluteWeek,weekday,slotId,inputs,
            "basic-preview-"+UUID.randomUUID().toString(),maximumIncrement)
        val reviewed=result as? NativeCustomBasicNextReadResult.Reviewed
        val shown=current.value.authoredPreview
        val next=reviewed?.next
        if(next!=null && shown!=null &&
            next.copy(declaredNonSetSeconds=shown.declaredNonSetSeconds)==shown &&
            next.stamp.cycleRevision==draft.revision &&
            next.stamp.gymCatalogSourceRevision==current.value.sourceRevision) {
            current.value=current.value.copy(basicDayProgression=reviewed,
                basicDayProgressionSlotId=slotId,
                message="Basic progression reviewed against saved work. No sets or targets were changed.")
        } else current.value=current.value.copy(
            message="Basic progression could not be verified. Recheck your saved program, equipment and workout history.")
    }

    /** Same complete native history reader as activated-block coaching. */
    fun reviewAuthoredDayCoaching(
        absoluteWeek:Int,weekday:Int,slotId:String,
        readiness:CoachReadiness,movementRestricted:Boolean,
        maximumIncrease:java.math.BigDecimal
    ) = run {
        current.value=current.value.copy(dayCoaching=null,calibrationReview=null,authoredReturnReview=null,
            calibrationReceipt=null,calibrationReset=null,issuedCalibrationId=null)
        val draft=current.value.draft ?:return@run
        val inputs=auditedInputs
        if(inputs==null) {
            current.value=current.value.copy(
                message="Run your current full-cycle coaching audit before reviewing starting weights.")
            return@run
        }
        // Read current owner-persisted settings at the time of review,
        // not the last rendered toggle. Both regular and Future Weeks use
        // the same versioned selector and the trusted owner checks again.
        val settings=(host.readCoachAssistance()
            as? NativeCoachSettingsRead.Current)?.settings?.policy
        val policy=settings?.let{CustomRepForecastPolicyDomain.newProposal(it)}
        if(policy==null) {
            current.value=current.value.copy(
                message="Progression assistance is off or the saved coaching settings could not be verified. No targets were changed.")
            return@run
        }
        val result=host.reviewNextAuthoredWorkoutProgression(draft.cycleId,
            absoluteWeek,weekday,slotId,inputs,readiness,movementRestricted,
            maximumIncrease,policy)
        val reviewed=result as? NativeCustomNextCoachResult.Reviewed
        val preview=current.value.authoredPreview
        val next=reviewed?.nextWorkout
        if(next!=null && preview!=null &&
            next.copy(declaredNonSetSeconds=preview.declaredNonSetSeconds)==preview &&
            next.stamp.cycleRevision==draft.revision &&
            next.stamp.gymCatalogSourceRevision==current.value.sourceRevision) {
            current.value=current.value.copy(dayCoaching=reviewed,
                message="Saved history reviewed for this exact training day. Nothing was changed.")
        } else current.value=current.value.copy(
            message="Coaching is unavailable for this preview. Check the saved cycle, gym, athlete consent and current coaching audit.")
    }

    fun auditBlockCalibration(readiness:CoachReadiness,restricted:Boolean,
        maximumIncrease:java.math.BigDecimal,
        kind:NativeBlockLoadConsentKind=NativeBlockLoadConsentKind.CONSERVATIVE_REDUCTION) = run {
        val coaching=current.value.dayCoaching ?:return@run
        val next=coaching.nextWorkout ?:return@run
        val slot=coaching.selectedSlotId ?:return@run
        val inputs=auditedInputs ?:return@run
        val workoutId="calibration-"+UUID.randomUUID().toString()
        val result=host.previewBlockCalibration(workoutId,next.stamp.cycleId,
            next.absoluteWeek,next.weekday,slot,inputs,readiness,restricted,maximumIncrease,kind)
        current.value=current.value.copy(calibrationReview=result as? NativeBlockCalibrationPreview.Ready,
            authoredReturnReview=null,
            calibrationReceipt=null,calibrationReset=null,preparedWorkout=null,issuedCalibrationId=null,
            message=if(result is NativeBlockCalibrationPreview.Ready)
                "The complete cycle and saved history support this exact workout change. Review and confirm before saving."
            else "Weight review unavailable. Check current load-change permissions, consent, source and outstanding workouts.")
    }

    fun clearCalibrationOffer() {
        if(!pending.get() && current.value.calibrationReceipt==null &&
            current.value.preparedWorkout?.calibrationApprovalFingerprint==null)
            current.value=current.value.copy(calibrationReview=null,calibrationReset=null)
    }

    fun acceptBlockCalibration() = run {
        val offer=current.value.calibrationReview ?:return@run
        val inputs=auditedInputs ?:return@run
        val result=host.acceptBlockCalibration(offer.receipt,inputs,true)
        current.value=current.value.copy(calibrationReceipt=(result as? NativeBlockCalibrationWrite.Accepted)?.receipt,
            message=if(result is NativeBlockCalibrationWrite.Accepted)
                "Weight acceptance saved. Prepare the exact workout before issuance."
            else "Acceptance refused. Recheck the current review; no workout was changed.")
    }

    fun prepareBlockCalibration() = run {
        val accepted=current.value.calibrationReceipt ?:return@run
        val inputs=auditedInputs ?:return@run
        val result=host.prepareAcceptedBlockCalibration(accepted,inputs)
        current.value=current.value.copy(preparedWorkout=(result as? NativeCustomPrepareResult.Prepared)?.workout,
            message=if(result is NativeCustomPrepareResult.Prepared)
                "Accepted targets prepared and saved. Review once more before issuing."
            else "Preparation refused. History, permissions or the reviewed proposal changed.")
    }

    fun issueBlockCalibration() = run {
        val prepared=current.value.preparedWorkout ?:return@run
        val inputs=auditedInputs ?:return@run
        if(prepared.calibrationApprovalFingerprint==null&&!prepared.resetToAuthored&&
            prepared.authoredReturnFingerprint==null)return@run
        val snap=prepared.snapshot
        val result=host.issueReviewedCustomWorkout(prepared.workoutId,snap.stamp.cycleId,
            snap.absoluteWeek,snap.weekday,inputs)
        current.value=current.value.copy(issuedCalibrationId=(result as? NativeCustomJournalResult.Durable)
            ?.issued?.journal?.workoutId,message=if(result is NativeCustomJournalResult.Durable)
                "Exact workout issued. Its targets are now immutable; log your actual sets in Train."
            else "Issuance refused. Recheck consent and corrected history, or explicitly return to the authored workout.")
    }

    /**
     * Explicitly issue an ordinary, unamended, consent-bound custom workout.
     * Previously only special calibration preparations were exposed to the
     * Compose issuer. Do not convert a merely prepared snapshot to a grant:
     * both the current checked preflight AND the host's owner-fenced issuer
     * independently verify the exact program, consent and history.
     */
    fun issueOrdinaryPreparedWorkout() = run {
        val before=current.value
        val prepared=before.preparedWorkout ?:return@run
        val draft=before.draft ?:return@run
        val inputs=auditedInputs ?:return@run
        val preflight=before.consentBoundPreview
        // Capture cross-module nullable properties in stable local vals:
        // Kotlin cannot smart-cast a public API property of another module.
        val selectedShortMinutes=prepared.snapshot.shortSessionLimitMinutes
        val ordinary=prepared.authority==
                NativeCustomPreparationAuthority.CUSTOM_CYCLE &&
            prepared.calibrationApprovalFingerprint==null &&
            prepared.authoredReturnFingerprint==null &&
            prepared.loadApprovalFingerprint==null &&
            prepared.repApprovalFingerprint==null &&
            prepared.futureWeekApprovalFingerprint==null &&
            !prepared.resetToAuthored &&
            preflight!=null &&
            (if(selectedShortMinutes==null)
                preflight.session
            else CustomShorterSessionReviewDomain.project(
                preflight.session,selectedShortMinutes)
                )==prepared.snapshot &&
            preflight.receipt.recordRevision==prepared.consentRevision &&
            preflight.receipt.fingerprintSha256==prepared.consentFingerprint &&
            prepared.snapshot.stamp.cycleId==draft.cycleId &&
            prepared.snapshot.stamp.cycleRevision==draft.revision &&
            before.coachingAudit?.status==
                CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
            (before.signoff as? NativeCustomSignoffRead.Found)?.isCurrent==true
        if(!ordinary) {
            current.value=before.copy(
                message="The ordinary prepared workout is no longer tied to the current approved cycle and preflight. Reverify the exact day.")
            return@run
        }
        val onDisk=host.openPreparedCustomWorkout(prepared.workoutId)
        if(onDisk !is NativeCustomPreparedRead.Found ||
            onDisk.workout!=prepared) {
            current.value=before.copy(preparedWorkout=null,
                message="Saved workout preparation changed or could not be verified. No workout was issued.")
            return@run
        }
        val snap=prepared.snapshot
        val result=host.issueReviewedCustomWorkout(
            prepared.workoutId,draft.cycleId,
            snap.absoluteWeek,snap.weekday,inputs)
        current.value=current.value.copy(
            issuedCalibrationId=(result as? NativeCustomJournalResult.Durable)
                ?.issued?.journal?.workoutId,
            message=if(result is NativeCustomJournalResult.Durable)
                "Your exact regular workout was issued. Open its logger to record each actual working set."
            else "Normal workout issuance was refused. The saved consent, history or exercise targets changed; nothing started.")
    }

    fun findPendingCalibrations() = run {
        val cycle=current.value.draft?.cycleId ?:return@run
        val result=host.pendingBlockCalibrations(cycle)
        current.value=current.value.copy(pendingCalibrations=(result as? NativePendingCalibrationRead.Ready)?.workouts.orEmpty(),
            pendingCalibrationAcceptances=(result as? NativePendingCalibrationRead.Ready)?.acceptances.orEmpty(),
            message=if(result is NativePendingCalibrationRead.Ready)
                "${result.workouts.size} preparations and ${result.acceptances.size} unprepared acceptances found. Current validation is still required."
            else "Stored preparations could not be verified. Nothing was issued or replaced.")
    }

    fun resumeAcceptedCalibration(receipt:NativeBlockCalibrationReceipt) = run {
        val inputs=auditedInputs ?:return@run
        val result=host.recheckBlockCalibration(receipt,inputs)
        val rechecked=(result as? NativeBlockCalibrationRead.Current)?.review
        current.value=current.value.copy(calibrationReview=rechecked,
            calibrationReceipt=rechecked?.receipt,preparedWorkout=null,calibrationReset=null,issuedCalibrationId=null,
            message=if(rechecked!=null)"Saved acceptance revalidated. Its exact workout can now be prepared."
            else "This saved acceptance is no longer current. No targets were prepared or issued.")
    }

    /**
     * Reopen a saved custom workout of either normal or calibrated type.
     * An ordinary preparation needs a new exact consent-bound preflight;
     * neither branch treats preparation as an issued session.
     */
    fun reopenPreparedWorkout(workoutId:String) = run {
        val before=current.value
        val draft=before.draft ?:return@run
        val stored=(host.openPreparedCustomWorkout(workoutId)
            as? NativeCustomPreparedRead.Found)?.workout
        if(stored==null || stored.snapshot.stamp.cycleId!=draft.cycleId ||
            stored.snapshot.stamp.cycleRevision!=draft.revision ||
            stored.authority!=NativeCustomPreparationAuthority.CUSTOM_CYCLE) {
            current.value=before.copy(preparedWorkout=null,
                consentBoundPreview=null,
                message="The saved preparation does not match this program revision.")
            return@run
        }
        val special=stored.calibrationApprovalFingerprint!=null ||
            stored.resetToAuthored || stored.authoredReturnFingerprint!=null
        if(special) {
            current.value=before.copy(preparedWorkout=stored,calibrationReview=null,
                authoredReturnReview=null,calibrationReceipt=null,
                calibrationReset=null,issuedCalibrationId=null,
                dayCoaching=null,consentBoundPreview=null,
                authoredPreview=stored.snapshot,
                message="Saved adjusted targets reopened. Recheck the current coach policy before issuing.")
            return@run
        }
        val plain=stored.loadApprovalFingerprint==null &&
            stored.repApprovalFingerprint==null &&
            stored.futureWeekApprovalFingerprint==null
        val inputs=auditedInputs
        if(!plain || inputs==null ||
            before.coachingAudit?.status!=
                CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
            before.coachingAudit?.issues?.isNotEmpty()!=false ||
            (before.signoff as? NativeCustomSignoffRead.Found)?.isCurrent!=true) {
            current.value=before.copy(preparedWorkout=null,
                consentBoundPreview=null,
                message="A current complete review and exact athlete consent are required to reopen normal targets.")
            return@run
        }
        val snap=stored.snapshot
        val verified=host.previewConsentBoundCustomWorkout(
            draft.cycleId,snap.absoluteWeek,snap.weekday,inputs)
        // A saved short session is supposed to differ from the full
        // authored source. Cold recovery must rederive its exact optional
        // cuts, not falsely reject every valid shortened preparation.
        val verifiedSnapshot=if(verified is NativeCustomPreflightResult.ReviewedPreview) {
            val selectedMinutes=snap.shortSessionLimitMinutes
            if(selectedMinutes==null)verified.session
            else CustomShorterSessionReviewDomain.project(
                verified.session,selectedMinutes)
        } else null
        if(verified !is NativeCustomPreflightResult.ReviewedPreview ||
            verifiedSnapshot!=snap ||
            verified.receipt.recordRevision!=stored.consentRevision ||
            verified.receipt.fingerprintSha256!=stored.consentFingerprint) {
            current.value=before.copy(preparedWorkout=null,
                consentBoundPreview=null,
                message="The saved preparation differs from the current consent-bound workout. Nothing issued.")
            return@run
        }
        val second=host.openPreparedCustomWorkout(workoutId)
        if(second !is NativeCustomPreparedRead.Found ||
            second.workout!=stored) {
            current.value=before.copy(preparedWorkout=null,
                consentBoundPreview=null,
                message="The stored preparation changed during recovery. Nothing issued.")
            return@run
        }
        current.value=before.copy(preparedWorkout=stored,
            calibrationReview=null,authoredReturnReview=null,
            calibrationReceipt=null,calibrationReset=null,
            issuedCalibrationId=null,dayCoaching=null,
            consentBoundPreview=verified,authoredPreview=verified.session,
            signoff=NativeCustomSignoffRead.Found(verified.receipt,true),
            message="Original saved targets and athlete consent were independently verified. Review and issue below.")
    }

    fun reviewAuthoredLoadReturn() = run {
        val prepared=current.value.preparedWorkout
        val next=prepared?.takeIf{it.authority==NativeCustomPreparationAuthority.CUSTOM_CYCLE&&
            !it.resetToAuthored&&it.calibrationApprovalFingerprint==null}?.snapshot
            ?:current.value.dayCoaching?.nextWorkout
            ?:current.value.consentBoundPreview?.session ?:return@run
        val inputs=auditedInputs ?:return@run
        val workoutId=prepared?.takeIf{it.snapshot==next}?.workoutId
            ?:"authored-"+UUID.randomUUID().toString()
        val result=host.previewAuthoredLoadReturn(workoutId,next.stamp.cycleId,
            next.absoluteWeek,next.weekday,inputs)
        current.value=current.value.copy(authoredReturnReview=result as? NativeAuthoredLoadReturnPreview.Ready,
            calibrationReview=null,calibrationReceipt=null,calibrationReset=null,issuedCalibrationId=null,
            message=if(result is NativeAuthoredLoadReturnPreview.Ready)
                "Review every heavier set before choosing this authored workout. This is your choice, not earned progression."
            else "Authored-weight review unavailable. Recheck the current cycle, history and outstanding workouts.")
    }

    fun acceptAuthoredLoadReturn() = run {
        val shown=current.value.authoredReturnReview ?:return@run
        val inputs=auditedInputs ?:return@run
        val result=host.acceptAuthoredLoadReturn(shown,inputs,true)
        current.value=current.value.copy(preparedWorkout=(result as? NativeCustomPrepareResult.Prepared)?.workout,
            authoredReturnReview=null,preparationRefusal=(result as? NativeCustomPrepareResult.Rejected)?.reason,
            message=if(result is NativeCustomPrepareResult.Prepared)
                "Your choice of these exact authored targets is saved. Review the workout before issuing."
            else "Acceptance refused because the review changed. Recheck the weights before confirming again.")
    }

    /** Show exact authored Full sets for a shortened custom preparation.
     * A read-only preview cannot undo an already-issued or logged workout. */
    /** Cancelling the read-only restore preview never touches native files. */
    fun dismissCustomTimeUndoReview() {
        if(pending.get())return
        current.value=current.value.copy(customTimeUndo=null)
    }

    fun reviewCustomTimeUndo() = run {
        val prepared=current.value.preparedWorkout ?:return@run
        val inputs=auditedInputs ?:return@run
        val snapshot=prepared.snapshot
        val result=host.previewUndoCustomShorterWorkout(
            prepared.workoutId,snapshot.stamp.cycleId,
            snapshot.absoluteWeek,snapshot.weekday,inputs)
        current.value=current.value.copy(
            customTimeUndo=(result as? NativeCustomTimeUndoPreview.Ready)
                ?.takeIf{it.shortened==prepared},
            message=if(result is NativeCustomTimeUndoPreview.Ready &&
                    result.shortened==prepared)
                "Review the entire original Full custom workout before explicitly restoring this unissued preparation."
            else "Full restore refused. The saved workout may have been issued or its consent may have changed.")
    }

    /** No automatic Full selection; accept the exact displayed Full target
     * under the native owner/consent/checksum CAS and first-issue fence. */
    fun acceptCustomTimeUndo() = run {
        val shown=current.value.customTimeUndo ?:return@run
        val inputs=auditedInputs ?:return@run
        if(current.value.preparedWorkout!=shown.shortened) {
            current.value=current.value.copy(customTimeUndo=null,
                message="The saved custom workout changed. Review Full again before confirming.")
            return@run
        }
        val result=host.acceptUndoCustomShorterWorkout(
            shown,inputs,true)
        current.value=current.value.copy(
            preparedWorkout=(result as? NativeCustomTimeUndoWrite.Restored)
                ?.prepared ?:current.value.preparedWorkout,
            customTimeUndo=null,
            customShortReview=if(result is NativeCustomTimeUndoWrite.Restored)
                null else current.value.customShortReview,
            message=if(result is NativeCustomTimeUndoWrite.Restored)
                "Your original Full custom workout is restored and cold-recoverable. Its authored cycle and performed history were not changed. Issue it separately when ready."
            else "Full restore refused: ${(result as NativeCustomTimeUndoWrite.Rejected).reason.name.replace('_',' ').lowercase()}. Review again.")
    }

    fun reviewCalibrationReset() = run {
        val prepared=current.value.preparedWorkout ?:return@run
        val inputs=auditedInputs ?:return@run
        val snap=prepared.snapshot
        val result=host.previewResetBlockCalibration(prepared.workoutId,snap.stamp.cycleId,
            snap.absoluteWeek,snap.weekday,inputs)
        current.value=current.value.copy(calibrationReset=result as? NativePreparedManualResetPreview.Ready,
            message=if(result is NativePreparedManualResetPreview.Ready)
                "Review the original authored targets before returning to them."
            else "The workout cannot be reset. Issued targets are immutable, and current authored consent is required.")
    }

    fun acceptCalibrationReset() = run {
        val reset=current.value.calibrationReset ?:return@run
        val inputs=auditedInputs ?:return@run
        val snap=reset.authored
        val result=host.acceptResetBlockCalibration(reset.original.workoutId,snap.stamp.cycleId,
            snap.absoluteWeek,snap.weekday,inputs,reset.preparedFingerprint,reset.settingsRevision,true)
        current.value=current.value.copy(preparedWorkout=(result as? NativePreparedManualResetWrite.Reset)?.prepared
                ?:current.value.preparedWorkout,
            calibrationReview=if(result is NativePreparedManualResetWrite.Reset)null else current.value.calibrationReview,
            calibrationReceipt=if(result is NativePreparedManualResetWrite.Reset)null else current.value.calibrationReceipt,
            calibrationReset=null,message=if(result is NativePreparedManualResetWrite.Reset)
                "Original authored targets restored explicitly. Issuance still rechecks the current workout."
            else "Reset refused. The reviewed preparation or settings changed.")
    }

    /**
     * Consent-bound preview performs a complete read and coach re-audit
     * under the owner lock. It intentionally does not issue a journal.
     */
    fun verifyConsentBoundDay(absoluteWeek:Int,weekday:Int) = run {
        current.value=current.value.copy(dayCoaching=null,basicDayProgression=null,
            basicDayProgressionSlotId=null,authoredReturnReview=null,
            preparedWorkout=current.value.preparedWorkout?.takeIf{
                it.snapshot.absoluteWeek==absoluteWeek&&it.snapshot.weekday==weekday})
        val draft=current.value.draft ?:return@run
        val inputs=auditedInputs
        if(inputs==null) {
            current.value=current.value.copy(
                consentBoundPreview=null,
                message="Run the fully policy-backed coach audit before checking athlete consent.")
            return@run
        }
        val result=host.previewConsentBoundCustomWorkout(
            draft.cycleId,absoluteWeek,weekday,inputs)
        current.value=when(result) {
            is NativeCustomPreflightResult.ReviewedPreview ->
                current.value.copy(
                    authoredPreview=result.session,
                    authoredPreviewRefusal=null,
                    consentBoundPreview=result,
                    signoff=NativeCustomSignoffRead.Found(result.receipt,true),
                    message="Exact consent and selected workout prescription independently verified. Preview only; no workout started.")
            is NativeCustomPreflightResult.Rejected ->
                current.value.copy(
                    consentBoundPreview=null,
                    authoredPreview=null,
                    message="Custom workout preflight rejected: ${result.reason.name.replace('_',' ').lowercase()}.")
        }
    }

    /**
     * Read-only "time available" preview for an EXACT consent-verified
     * custom week/day. This never calls the generated Composer issuer and
     * cannot save a shorter session, even when the estimate fits.
     */
    fun reviewCustomShorterDay(
        absoluteWeek:Int,weekday:Int,minutes:Int
    )=run {
        val draft=current.value.draft ?:return@run
        val inputs=auditedInputs
        val approved=current.value.consentBoundPreview?.session
        if(inputs==null || approved==null ||
            approved.stamp.cycleId!=draft.cycleId ||
            approved.stamp.cycleRevision!=draft.revision ||
            approved.absoluteWeek!=absoluteWeek ||
            approved.weekday!=weekday) {
            current.value=current.value.copy(
                message="Verify exact current athlete consent and this week/day before reviewing a shorter custom session.")
            return@run
        }
        val result=host.previewConsentBoundCustomShorterSession(
            draft.cycleId,absoluteWeek,weekday,inputs,minutes)
        current.value=when(result) {
            is NativeCustomShortSessionPreflight.Reviewed ->
                current.value.copy(
                    customShortReview=result,
                    message=if(result.review.status in setOf(
                            CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW,
                            CustomShortSessionStatus.ALREADY_FITS))
                        "Shorter custom session checked against this signed program. Read-only: no workout was changed or issued."
                    else "Custom time review is blocked: ${result.review.explanation}")
            is NativeCustomShortSessionPreflight.Rejected ->
                current.value.copy(
                    customShortReview=null,
                    message="Custom time review refused: ${result.reason.name.replace('_',' ').lowercase()}. No workout was changed.")
        }
    }

    /** Prepare exactly the athlete's reviewed 20/30/45-minute custom
     * session, only after explicitly pressing the approval action. No
     * implicit "shortening" on Start. An independent first-issuance
     * transaction must still reverify the currently signed cycle. */
    fun prepareConsentBoundShorterDay(
        absoluteWeek:Int,weekday:Int,minutes:Int
    )=run {
        val draft=current.value.draft ?:return@run
        val inputs=auditedInputs
        val consent=current.value.consentBoundPreview
        val reviewed=current.value.customShortReview
        if(inputs==null || consent==null || reviewed==null ||
            consent.session.absoluteWeek!=absoluteWeek ||
            consent.session.weekday!=weekday ||
            reviewed.absoluteWeek!=absoluteWeek ||
            reviewed.weekday!=weekday ||
            reviewed.review.requestedMinutes!=minutes ||
            reviewed.review.source!=consent.session.stamp ||
            reviewed.review.status !in setOf(
                CustomShortSessionStatus.ALREADY_FITS,
                CustomShortSessionStatus.READY_FOR_ATHLETE_REVIEW) ||
            consent.session.stamp.cycleId!=draft.cycleId ||
            consent.session.stamp.cycleRevision!=draft.revision ||
            reviewed.receipt!=consent.receipt) {
            current.value=current.value.copy(
                customShortReview=null,
                message="Your time review is stale or blocked. Reverify the exact custom workout and try again.")
            return@run
        }
        val workoutId="custom-short-"+UUID.randomUUID().toString()
            .replace("-","")
        val result=host.prepareReviewedCustomShorterWorkout(
            workoutId,draft.cycleId,absoluteWeek,weekday,inputs,
            reviewed,true)
        val opened=if(result is NativeCustomPrepareResult.Prepared)
            host.openPreparedCustomWorkout(workoutId)
        else null
        current.value=current.value.copy(
            preparedWorkout=(opened as? NativeCustomPreparedRead.Found)
                ?.workout,
            preparationRefusal=(result as? NativeCustomPrepareResult.Rejected)
                ?.reason,
            customShortReview=null,
            message=when {
                opened is NativeCustomPreparedRead.Found ->
                    "Your exact shorter custom workout was saved and cold-reopened. Review its retained sets, then explicitly issue it before logging."
                result is NativeCustomPrepareResult.Rejected ->
                    "Shorter preparation refused: ${result.reason.name.replace('_',' ').lowercase()}. No workout was issued."
                else ->
                    "Shorter preparation could not be cold-verified. Nothing was issued."
            }
        )
    }

    /** Keep an immutable, cold-recoverable custom prescription distinct
     * from an issued session, actual set logging, or finished history. */
    fun prepareConsentBoundDay(absoluteWeek:Int,weekday:Int) = run {
        val draft=current.value.draft ?:return@run
        val inputs=auditedInputs
        if(inputs==null) {
            current.value=current.value.copy(
                message="No complete coach policy; prepare refused.",
                preparationRefusal=NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            return@run
        }
        val inspected=current.value.consentBoundPreview
        if(inspected==null ||
            inspected.session.absoluteWeek!=absoluteWeek ||
            inspected.session.weekday!=weekday ||
            inspected.session.stamp.cycleRevision!=draft.revision) {
            current.value=current.value.copy(
                message="Verify consent for this exact week and day before preparation.",
                preparationRefusal=NativeCustomPrepareRefusal.REVIEW_UNAVAILABLE)
            return@run
        }
        val workoutId="custom-"+UUID.randomUUID().toString().replace("-","")
        when(val result=host.prepareReviewedCustomWorkout(
            workoutId,draft.cycleId,absoluteWeek,weekday,inputs)) {
            is NativeCustomPrepareResult.Prepared -> {
                val restored=host.openPreparedCustomWorkout(workoutId)
                current.value=current.value.copy(
                    preparedWorkout=(restored as? NativeCustomPreparedRead.Found)?.workout,
                    preparationRefusal=null,
                    message=if(restored is NativeCustomPreparedRead.Found)
                        "Exact custom workout prepared and cold-recovered as $workoutId. It is NOT issued; no set logging or Finish is enabled."
                    else "Saved snapshot could not be independently reopened; issuance remains disabled."
                )
            }
            is NativeCustomPrepareResult.Rejected ->
                current.value=current.value.copy(
                    preparedWorkout=null,preparationRefusal=result.reason,
                    message="Custom preparation rejected: ${result.reason.name.replace('_',' ').lowercase()}. No workout issued.")
        }
    }

    /** Read-only options; absent trusted gym/catalog means NO made-up choices. */
    fun availableExerciseChoices(): List<CustomExercisePickerOption> =
        current.value.availableChoices

    private fun editReviewed(
        sourceGuard:(NativeCustomAuthoringSnapshot)->Boolean={true},
        command: (CustomTrainingCycle, AthleteSetupReview,
            List<CatalogMovement>) -> CustomAuthoringResult
    ) {
        var checkedSourceRevision: Long? = null
        edit(reviewedSourceRevision = { checkedSourceRevision }) { draft ->
            // Read inside IO. The subsequent write atomically checks that
            // exactly this same source revision remains under owner lock.
            val source = runCatching { authoringSource.snapshot() }.getOrNull()
            if (source == null || source.athleteId != draft.athleteId || !sourceGuard(source)) {
                current.value = current.value.copy(
                    message = if(source!=null && source.athleteId==draft.athleteId)
                        "Training setup changed. Reload your program before saving. Nothing saved."
                        else "Reviewed native gym and exercise catalog required. Nothing saved.",
                    authoringRefusal = CustomAuthoringRefusal.SETUP_UNRESOLVED)
                CustomCycleBuilderResult.Rejected(
                    CustomCycleBuilderRefusal.INVALID_INPUT)
            } else {
                checkedSourceRevision = source.revision
                when (val reviewed = command(draft,source.setup,source.catalog)) {
                    is CustomAuthoringResult.Accepted -> reviewed.proposal
                    is CustomAuthoringResult.Rejected -> {
                        current.value = current.value.copy(
                            message = "Exercise edit refused: ${reviewed.reason.name.replace('_', ' ').lowercase()}. Nothing saved.",
                            authoringRefusal = reviewed.reason)
                        CustomCycleBuilderResult.Rejected(
                            CustomCycleBuilderRefusal.INVALID_INPUT)
                    }
                }
            }
        }
    }

    fun addReviewedExercise(
        blockId: String, weekday: Int, exerciseId: String,
        sets: List<CustomAuthoredSet>, coachNote: String = ""
    ) = editReviewed { draft, setup, catalog ->
        val selected = CustomExerciseAuthoringDomain.picker(
            setup, catalog).firstOrNull { it.exerciseId == exerciseId }
        if (selected == null)
            CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.EXERCISE_UNAVAILABLE)
        else {
            val slot = CustomExerciseSlot(
                slotId = "slot-" + UUID.randomUUID().toString().replace("-", ""),
                exerciseId = selected.exerciseId,
                role = catalog.first { it.exerciseId == exerciseId }.role,
                sets = sets,
                attachmentId = selected.attachmentId,
                pinned = true,
                coachNote = coachNote
            )
            CustomExerciseAuthoringDomain.addSelected(
                draft, draft.revision, blockId, weekday, slot, setup, catalog)
        }
    }

    /**
     * Athlete-confirmed draft swap. The old slot and all its loads/advanced
     * sets are NOT copied: new sets come only from the explicit editor.
     * editReviewed rechecks the current gym/catalog source and saves an
     * undoable revision only after optimistic concurrency has been verified.
     */
    fun swapReviewedExercise(
        blockId:String,weekday:Int,oldSlotId:String,
        exerciseId:String,sets:List<CustomAuthoredSet>,coachNote:String=""
    ) = editReviewed {draft,setup,catalog ->
        val selected=CustomExerciseAuthoringDomain.picker(
            setup,catalog).firstOrNull {it.exerciseId==exerciseId}
        if(selected==null)
            CustomAuthoringResult.Rejected(
                CustomAuthoringRefusal.EXERCISE_UNAVAILABLE)
        else {
            val newSlot=CustomExerciseSlot(
                slotId="slot-"+UUID.randomUUID().toString().replace("-",""),
                exerciseId=selected.exerciseId,
                role=catalog.first {it.exerciseId==exerciseId}.role,
                sets=sets,
                attachmentId=selected.attachmentId,
                pinned=true,
                coachNote=coachNote
            )
            CustomExerciseAuthoringDomain.replaceSelectedExercise(
                draft,draft.revision,blockId,weekday,oldSlotId,
                newSlot,setup,catalog)
        }
    }

    /**
     * The athlete confirmed a fully disclosed removal (pinned status,
     * block-wide effect and every week-specific set exception).
     * Recheck that exact revision in the existing durable edit transaction;
     * a stale dialog cannot remove a similarly named newer exercise.
     * Undo, consent invalidation and all journal protections are shared
     * with every other custom-cycle edit.
     */
    fun removeExercise(review:NativeKotlinPlanExerciseRemovalReview) = edit { draft ->
        NativeKotlinPlanExerciseRemoval.confirmed(draft,review)
    }

    /** Explicit source/destination confirmation. Stable authored slot IDs,
     * grouped rounds and week overrides are preserved by the single Kotlin
     * cycle builder; native CAS/save/undo and renewed approval are shared. */
    fun moveExerciseAcrossDays(review:NativeKotlinPlanCrossDayMoveReview) =
        edit {draft->NativeKotlinPlanCrossDayMove.confirmed(draft,review)}

    /** Accessible alternative to drag gestures, stable slot identity intact. */
    fun moveExercise(
        blockId: String, weekday: Int, slotId: String, direction: Int
    ) = edit { draft ->
        val day = draft.blocks.firstOrNull { it.blockId == blockId }
            ?.days?.firstOrNull { it.weekday == weekday }
        val order=day?.let{
            NativeKotlinPlanSupersetOrdering.proposed(it,slotId,direction)
        }
        if(order==null)CustomCycleBuilderResult.Rejected(
            CustomCycleBuilderRefusal.SLOT_ORDER_CONFLICT)
        else CustomCycleBuilderDomain.reorderSlots(
            draft,draft.revision,blockId,weekday,order)
    }

    /**
     * Athlete-owned progression change: the existing CAS writer, undo
     * receipt and consent invalidation apply exactly as for any set edit.
     * Never changes historical loads or an already issued workout.
     */
    fun chooseExerciseProgression(
        reviewed: CustomTrainingCycle, blockId:String,weekday:Int,slotId:String,
        method:SavedProgressionMethod?
    ) = edit {draft->
        CustomCycleBuilderDomain.setProgressionMethod(
            draft,reviewed,blockId,weekday,slotId,method)
    }

    fun editReviewedExerciseSets(
        blockId: String, weekday: Int, slotId: String,
        sets: List<CustomAuthoredSet>
    ) = editReviewed { draft, setup, catalog ->
        CustomExerciseAuthoringDomain.replaceSelectedSets(
            draft, draft.revision, blockId, weekday, slotId, sets, setup, catalog)
    }

    fun supersetEditRequest(blockId:String,weekday:Int,groupId:String?=null):NativeKotlinSupersetEditRequest? {
        val state=current.value
        val draft=state.draft ?:return null
        val source=openedSource?.takeIf{it.athleteId==draft.athleteId &&
            it.revision==state.sourceRevision} ?:return null
        if(state.busy || draft.blocks.none{it.blockId==blockId &&
            it.days.any{day->day.weekday==weekday}})return null
        return NativeKotlinSupersetEditRequest(source.ownerKey,draft.athleteId,draft.cycleId,
            draft.revision,source.revision,blockId,weekday,
            groupId ?:"ss-"+UUID.randomUUID().toString().replace("-",""))
    }

    fun saveSuperset(request:NativeKotlinSupersetEditRequest,slotIds:List<String>)=
        editReviewed(sourceGuard=request::matchesSource) {draft,setup,_->
            val athlete=setup.reviewed?.takeIf{
                setup.status==AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW &&
                    it.athleteProfileId==draft.athleteId}
            if(!request.matches(draft) || athlete==null)
                CustomAuthoringResult.Rejected(CustomAuthoringRefusal.AUTHORING_CONFLICT)
            else when(val result=CustomCycleBuilderDomain.setSupersetMembers(draft,
                request.cycleRevision,request.blockId,request.weekday,request.groupId,
                slotIds,athlete.allowSupersets)) {
                is CustomCycleBuilderResult.Proposed -> CustomAuthoringResult.Accepted(result)
                is CustomCycleBuilderResult.Rejected -> CustomAuthoringResult.Rejected(
                    CustomAuthoringRefusal.AUTHORING_CONFLICT)
            }
        }

    /** Read-only comparison; available for manual authoring even with coach advice off. */
    fun previewWeekSets(
        absoluteWeek: Int, weekday: Int, slotId: String,
        sets: List<CustomAuthoredSet>?
    ) = run {
        pendingWeekEdit=null
        current.value=current.value.copy(weekEditReview=null)
        val requested=current.value.draft ?: return@run
        val disk=host.readCustomCycleDraft(requested.cycleId)
        if(disk !is NativeCustomDraftReadResult.Found || disk.draft!=requested) {
            applyRead(requested.cycleId,disk)
            current.value=current.value.copy(message="The saved cycle changed. Reopen the selected week before comparing sets.")
            return@run
        }
        val source=runCatching { authoringSource.snapshot() }.getOrNull()
        if(source==null || source.athleteId!=requested.athleteId) {
            current.value=current.value.copy(message="Reviewed athlete, gym and exercise catalog required to compare this edit.")
            return@run
        }
        val inputs=current.value.reviewedCoachInputs ?: CustomCoachAuditInputs()
        val result=CustomCoachWeekEditDomain.review(requested,requested.revision,
            absoluteWeek,weekday,slotId,sets,source.setup,source.catalog,inputs)
        val latest=host.readCustomCycleDraft(requested.cycleId)
        if(latest !is NativeCustomDraftReadResult.Found || latest.draft!=requested ||
            runCatching {authoringSource.snapshot()}.getOrNull()!=source) {
            applyRead(requested.cycleId,latest)
            current.value=current.value.copy(message="The program or gym changed during comparison. Compare again with the current source.")
            return@run
        }
        when(result) {
            is CustomCoachWeekEditResult.Rejected -> current.value=current.value.copy(
                message="Edit comparison unavailable: ${result.reason.name.replace('_',' ').lowercase()}"+
                    (result.authoringReason?.let {" · ${it.name.replace('_',' ').lowercase()}"} ?: "")+".")
            is CustomCoachWeekEditResult.Reviewed -> {
                pendingWeekEdit=PendingWeekEdit(source,inputs,result.review)
                current.value=current.value.copy(weekEditReview=result.review,
                    message="One-week edit compared across the full cycle. Inspect its effects before explicitly saving the draft.")
            }
        }
    }

    fun dismissWeekEditReview() {
        if(current.value.busy)return
        pendingWeekEdit=null
        current.value=current.value.copy(weekEditReview=null)
    }

    /** Recheck the exact comparison, then use the existing owner-locked source/draft CAS. */
    fun acceptWeekEdit() {
        var checkedRevision: Long? = null
        edit(reviewedSourceRevision={checkedRevision}) { draft ->
            val prepared=pendingWeekEdit
            val source=runCatching {authoringSource.snapshot()}.getOrNull()
            val inputs=current.value.reviewedCoachInputs ?: CustomCoachAuditInputs()
            if(prepared==null || source==null || source!=prepared.source || inputs!=prepared.inputs ||
                prepared.review.proposal.receipt.original!=draft) {
                pendingWeekEdit=null
                current.value=current.value.copy(weekEditReview=null,
                    message="This comparison is no longer current. Review the exact saved week again.")
                CustomCycleBuilderResult.Rejected(CustomCycleBuilderRefusal.STALE_REVISION)
            } else {
                val previous=prepared.review
                val fresh=CustomCoachWeekEditDomain.review(draft,draft.revision,
                    previous.absoluteWeek,previous.weekday,previous.slotId,
                    if(previous.restoresBlockSets)null else previous.proposedSets,
                    source.setup,source.catalog,inputs)
                if(fresh is CustomCoachWeekEditResult.Reviewed && fresh.review==previous) {
                    checkedRevision=source.revision
                    fresh.review.proposal
                } else {
                    pendingWeekEdit=null
                    current.value=current.value.copy(weekEditReview=null,
                        message="The full-cycle comparison changed. Nothing saved; compare again.")
                    CustomCycleBuilderResult.Rejected(CustomCycleBuilderRefusal.STALE_REVISION)
                }
            }
        }
    }

    fun editMetadata(title: String, note: String) = edit { draft ->
        CustomCycleBuilderDomain.editCycleMetadata(
            draft, draft.revision, title, note)
    }

    fun addDay(
        blockId: String, weekday: Int, label: String,
        purpose: ComposerSessionPurpose
    ) = edit { draft ->
        CustomCycleBuilderDomain.addDay(draft, draft.revision, blockId,
            CustomTrainingDay(weekday, label, purpose, emptyList()))
    }

    fun editDay(
        blockId: String, weekday: Int, label: String,
        purpose: ComposerSessionPurpose
    ) = edit { draft ->
        CustomCycleBuilderDomain.editDayMetadata(
            draft, draft.revision, blockId, weekday, label, purpose)
    }

    /** User-confirmed draft-only removal with existing owner-CAS, revoke
     * prior consent, persist Undo and invalidate all former coach reviews. */
    fun removeReviewedDay(review:NativePlanDayRemovalReview) = edit { draft ->
        NativeKotlinPlanStructureRemoval.confirmedDay(draft,review)
    }

    fun removeReviewedBlock(review:NativePlanBlockRemovalReview) = edit { draft ->
        NativeKotlinPlanStructureRemoval.confirmedBlock(draft,review)
    }

    fun renameDay(blockId: String, weekday: Int, label: String) = edit { draft ->
        CustomCycleBuilderDomain.renameDay(
            draft, draft.revision, blockId, weekday, label)
    }

    fun changeDayPurpose(
        blockId: String, weekday: Int, purpose: ComposerSessionPurpose
    ) = edit { draft ->
        CustomCycleBuilderDomain.changeDayPurpose(
            draft, draft.revision, blockId, weekday, purpose)
    }

    fun addBlock(
        label: String, phase: TrainingPhase,
        weeks: Int, split: String
    ) = edit { draft ->
        val newId = "phase-" + UUID.randomUUID().toString().replace("-", "")
        val block = CustomTrainingBlock(newId, label, phase,
            CustomBlockPurpose.TRAINING, weeks,
            CustomSplitIdentity.Freeform(split), emptyList())
        CustomCycleBuilderDomain.insertBlock(draft, draft.revision, block)
    }

    fun editBlock(
        blockId: String, label: String, phase: TrainingPhase,
        purpose: CustomBlockPurpose, weeks: Int, split: String
    ) = edit { draft ->
        CustomCycleBuilderDomain.editBlockMetadata(
            draft, draft.revision, blockId, label, phase,
            purpose, weeks, CustomSplitIdentity.Freeform(split))
    }

    fun duplicateBlock(blockId: String) = edit { draft ->
        CustomCycleBuilderDomain.duplicateBlock(
            draft, draft.revision, blockId,
            "phase-" + UUID.randomUUID().toString().replace("-", ""))
    }

    fun reorderBlock(blockId: String, direction: Int) = edit { draft ->
        val order = draft.blocks.map { it.blockId }.toMutableList()
        val currentIndex = order.indexOf(blockId)
        val target = currentIndex + direction
        if (currentIndex < 0 || target !in order.indices)
            CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.INVALID_INPUT)
        else {
            val moved = order.removeAt(currentIndex)
            order.add(target, moved)
            CustomCycleBuilderDomain.reorderBlocks(
                draft, draft.revision, order)
        }
    }

    /**
     * Exact saved-cycle strength repair, not a phase-wide algorithmic rewrite.
     * The coach policy must have been reviewed against this same source,
     * even when the review is UNRESOLVED because volume falls too sharply.
     * A preview can never approve or replace a workout automatically.
     */
    fun reviewStrengthVolumeRepair(blockId:String,maxSetsPerSlot:Int) = run {
        val shown=current.value
        val draft=shown.draft
        val sourceRevision=shown.sourceRevision
        val audited=shown.coachingAudit
        val inputs=shown.reviewedCoachInputs
        if(draft==null || sourceRevision==null || inputs==null ||
            shown.auditSourceRevision!=sourceRevision ||
            audited==null || audited.auditedCycleRevision!=draft.revision ||
            audited.structuralReview?.verifiedDraft!=draft ||
            !shown.coachingSuggestionsEnabled) {
            current.value=current.value.copy(strengthRepairReview=null,
                message="Review the saved full cycle, gym and coaching rules first. No dose change was proposed.")
            return@run
        }
        val proposal=host.previewStrengthVolumeRepair(
            NativeStrengthVolumeRepairRequest(
                draft.cycleId,blockId,draft.revision,sourceRevision,
                inputs,maxSetsPerSlot))
        current.value=when(proposal) {
            is NativeStrengthVolumeRepairPreview.Ready ->
                current.value.copy(strengthRepairReview=proposal.receipt,
                    message="Reviewed ${proposal.receipt.additionsPerWeek} additional working sets per week in ${proposal.receipt.weeksCovered} strength week(s). Check the specific exercise changes and confirm before saving.")
            is NativeStrengthVolumeRepairPreview.Rejected ->
                current.value.copy(strengthRepairReview=null,
                    message="Strength dose proposal unavailable: ${proposal.explanation}")
        }
    }

    /** Athlete confirmation calls the owner-fenced host that independently
     * re-reads/re-audits the WHOLE cycle and CAS-saves only the new draft.
     * The former program consent is stale; workouts already issued remain.
     */
    fun acceptStrengthVolumeRepair(athleteConfirmed:Boolean) = run {
        val shown=current.value
        val reviewed=shown.strengthRepairReview
        val prior=shown.draft
        if(!athleteConfirmed || reviewed==null || prior==null ||
            shown.sourceRevision!=reviewed.request.expectedSourceRevision ||
            prior.revision!=reviewed.request.expectedCycleRevision ||
            prior.cycleId!=reviewed.request.cycleId) {
            current.value=current.value.copy(strengthRepairReview=null,
                message="The review is no longer current. Recheck the exact proposal; nothing saved.")
            return@run
        }
        when(val decision=host.acceptStrengthVolumeRepair(
            reviewed,athleteConfirmed)) {
            is NativeStrengthVolumeRepairDecision.Saved -> {
                val disk=host.readCustomCycleDraft(prior.cycleId)
                if(disk is NativeCustomDraftReadResult.Found &&
                    disk.draft==reviewed.proposedCycle &&
                    disk.draft.revision==decision.newCycleRevision) {
                    applyRead(prior.cycleId,disk)
                    lastUndo=CustomCycleDraftReceipt(
                        "strength-dose-repair",prior,reviewed.proposedCycle)
                    current.value=current.value.copy(canUndo=true,
                        strengthRepairReview=null,
                        message="Saved strength working-set changes · revision ${decision.newCycleRevision}. The previous program approval is no longer current; review and sign this updated cycle before another workout.")
                } else {
                    applyRead(prior.cycleId,disk)
                    current.value=current.value.copy(strengthRepairReview=null,
                        message="The save result could not be verified. Reloaded the durable program; do not assume the proposal was applied.")
                }
            }
            is NativeStrengthVolumeRepairDecision.Rejected -> {
                val disk=host.readCustomCycleDraft(prior.cycleId)
                applyRead(prior.cycleId,disk)
                current.value=current.value.copy(strengthRepairReview=null,
                    message="Strength proposal not saved: ${decision.explanation}")
            }
        }
    }

    /**
     * Recover source-authored, source-reviewed vanished Strength exercises.
     * Preview and Save are separate actions and source revisions; an empty or
     * stale audit never becomes permission to modify the saved program.
     */
    fun reviewStrengthExerciseMenu(blockId:String,maxExercisesPerDay:Int)=run {
        val shown=current.value
        val cycle=shown.draft
        val sourceRevision=shown.sourceRevision
        val audited=shown.coachingAudit
        val inputs=shown.reviewedCoachInputs
        if(cycle==null || sourceRevision==null || inputs==null ||
            shown.auditSourceRevision!=sourceRevision ||
            audited==null || audited.auditedCycleRevision!=cycle.revision ||
            audited.structuralReview?.verifiedDraft!=cycle ||
            !shown.coachingSuggestionsEnabled) {
            current.value=current.value.copy(strengthMenuRecoveryReview=null,
            recoveryRecipeReview=null,
                message="Review the current full cycle, gym and coaching policy before restoring exercises.")
            return@run
        }
        val response=host.previewStrengthMenuRecovery(
            NativeStrengthMenuRecoveryRequest(cycle.cycleId,blockId,
                cycle.revision,sourceRevision,inputs,maxExercisesPerDay))
        current.value=when(response) {
            is NativeStrengthMenuRecoveryPreview.Ready ->
                current.value.copy(strengthMenuRecoveryReview=response.receipt,
                    message="Reviewed "+response.receipt.restored.size+
                        " actual source exercise(s) for Strength. Confirm the exact choices before saving.")
            is NativeStrengthMenuRecoveryPreview.Rejected ->
                current.value.copy(strengthMenuRecoveryReview=null,
            recoveryRecipeReview=null,
                    message="Strength menu proposal unavailable: "+response.explanation)
        }
    }

    fun acceptStrengthExerciseMenu(athleteConfirmed:Boolean)=run {
        val shown=current.value
        val reviewed=shown.strengthMenuRecoveryReview
        val prior=shown.draft
        if(!athleteConfirmed || reviewed==null || prior==null ||
            shown.sourceRevision!=reviewed.request.expectedSourceRevision ||
            prior.revision!=reviewed.request.expectedCycleRevision ||
            prior.cycleId!=reviewed.request.cycleId) {
            current.value=current.value.copy(strengthMenuRecoveryReview=null,
            recoveryRecipeReview=null,
                message="The source exercise review is stale. Recheck before saving.")
            return@run
        }
        when(val saved=host.acceptStrengthMenuRecovery(
            reviewed,athleteConfirmed)) {
            is NativeStrengthMenuRecoveryDecision.Saved -> {
                val disk=host.readCustomCycleDraft(prior.cycleId)
                if(disk is NativeCustomDraftReadResult.Found &&
                    disk.draft==reviewed.proposedCycle &&
                    disk.draft.revision==saved.newCycleRevision) {
                    applyRead(prior.cycleId,disk)
                    lastUndo=CustomCycleDraftReceipt(
                        "strength-menu-recovery",prior,reviewed.proposedCycle)
                    current.value=current.value.copy(canUndo=true,
                        strengthMenuRecoveryReview=null,
            recoveryRecipeReview=null,
                        message="Saved "+reviewed.restored.size+
                            " source exercise(s) in Strength. The old approval is stale; review and sign the new program before its next workout.")
                } else {
                    applyRead(prior.cycleId,disk)
                    current.value=current.value.copy(strengthMenuRecoveryReview=null,
            recoveryRecipeReview=null,
                        message="The saved result could not be verified. Reloaded disk state without claiming success.")
                }
            }
            is NativeStrengthMenuRecoveryDecision.Rejected -> {
                val disk=host.readCustomCycleDraft(prior.cycleId)
                applyRead(prior.cycleId,disk)
                current.value=current.value.copy(strengthMenuRecoveryReview=null,
            recoveryRecipeReview=null,
                    message="Strength exercises were not saved: "+saved.explanation)
            }
        }
    }

    fun reviewRecoveryRecipe(maxSets:Int,minimumRir:Int,
        maximumEasierStep:java.math.BigDecimal?=null,stepUnit:LoadUnit?=null,
        anchorBlockId:String?=null,insertAfterAnchor:Boolean=false)=run {
        val shown=current.value
        val draft=shown.draft
        val sourceRevision=shown.sourceRevision
        val inputs=shown.reviewedCoachInputs
        val audited=shown.coachingAudit
        val anchor=if(anchorBlockId==null)draft?.blocks?.lastOrNull()
            else draft?.blocks?.singleOrNull{it.blockId==anchorBlockId}
        if(draft==null || sourceRevision==null || inputs==null ||
            anchor?.purpose!=CustomBlockPurpose.TRAINING ||
            shown.auditSourceRevision!=sourceRevision ||
            audited?.auditedCycleRevision!=draft.revision ||
            audited?.structuralReview?.verifiedDraft!=draft ||
            !shown.fatigueCoachingEnabled) {
            current.value=current.value.copy(recoveryRecipeReview=null,
                message="Review this exact saved cycle and enable recovery coaching before proposing a recovery week.")
            return@run
        }
        when(val preview=host.previewRecoveryRecipe(NativeRecoveryRecipeRequest(
            draft.cycleId,anchor.blockId,draft.revision,sourceRevision,inputs,maxSets,minimumRir,
            maximumEasierStep,stepUnit,insertAfterAnchor))) {
            is NativeRecoveryRecipePreview.Ready ->
                current.value=current.value.copy(recoveryRecipeReview=preview.receipt,
                    message="Review the recovery week and any shifted future weeks before saving.")
            is NativeRecoveryRecipePreview.Rejected ->
                current.value=current.value.copy(recoveryRecipeReview=null,
                    message=preview.explanation)
        }
    }

    fun acceptRecoveryRecipe(athleteConfirmed:Boolean)=run {
        val shown=current.value
        val reviewed=shown.recoveryRecipeReview
        val prior=shown.draft
        if(!athleteConfirmed || reviewed==null || prior==null ||
            shown.sourceRevision!=reviewed.request.expectedSourceRevision ||
            shown.auditSourceRevision!=shown.sourceRevision ||
            shown.reviewedCoachInputs!=reviewed.request.coaching ||
            prior.revision!=reviewed.request.expectedCycleRevision ||
            prior.cycleId!=reviewed.request.cycleId) {
            current.value=current.value.copy(recoveryRecipeReview=null,
                message="The recovery review is no longer current. Nothing saved.")
            return@run
        }
        when(val result=host.acceptRecoveryRecipe(reviewed,athleteConfirmed)) {
            is NativeRecoveryRecipeDecision.Saved -> {
                val disk=host.readCustomCycleDraft(prior.cycleId)
                applyRead(prior.cycleId,disk)
                if(disk is NativeCustomDraftReadResult.Found &&
                    disk.draft==reviewed.proposedCycle && disk.draft.revision==result.newCycleRevision) {
                    lastUndo=CustomCycleDraftReceipt("recovery-recipe",prior,reviewed.proposedCycle)
                    current.value=current.value.copy(canUndo=true,recoveryRecipeReview=null,
                        message="Recovery week saved in draft revision ${result.newCycleRevision}. Review and approve the updated program before issuing workouts.")
                } else current.value=current.value.copy(recoveryRecipeReview=null,
                    message="Reloaded saved program; the recovery edit could not be verified.")
            }
            is NativeRecoveryRecipeDecision.Rejected -> {
                applyRead(prior.cycleId,host.readCustomCycleDraft(prior.cycleId))
                current.value=current.value.copy(recoveryRecipeReview=null,
                    message=result.explanation)
            }
        }
    }

    fun undo() = run {
        val saved = current.value.draft ?: return@run
        val receipt = lastUndo ?: return@run
        val result = CustomCycleBuilderDomain.undo(saved, receipt)
        if (result !is CustomCycleBuilderResult.Proposed) {
            lastUndo = null
            current.value = current.value.copy(
                canUndo = false, message = "Undo is no longer valid for this revision.")
            return@run
        }
        // A strength repair changed physical working-set prescriptions
        // against a particular verified gym source. Its Undo must obey
        // the same source-revision fence, never restore an obsolete stack.
        val write = if(receipt.operation=="recovery-recipe") {
            val source=current.value.sourceRevision
            if(source==null)NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.STALE_REVISION)
            else host.undoRecoveryRecipe(receipt,source)
        } else if(receipt.operation in setOf("strength-dose-repair",
            "strength-menu-recovery")) {
            val source= current.value.sourceRevision
            if(source==null)NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.STALE_REVISION)
            else host.saveReviewedCustomCycleDraft(
                result.draft,saved.revision,source)
        } else host.saveCustomCycleDraft(result.draft,saved.revision)
        lastUndo = null
        auditedInputs = null
        pendingWeekEdit = null
        when (write) {
            is NativeCustomDraftWriteResult.Committed ->
                current.value = current.value.copy(
                    draft = result.draft, canUndo = false,
                    coachingAudit = null, auditSourceRevision = null,
                    authoredWorkloadEvidence = null,
                    evidenceSourceRevision = null,
                    reviewedCoachInputs = null,
                    weekEditReview = null,
                    signoff = host.readCustomCycleConsent(result.draft.cycleId),
                    signoffRefusal = null,
                    authoredPreview = null,
                    authoredPreviewRefusal = null,
                    consentBoundPreview = null,
                    preparedWorkout = null,
                    preparationRefusal = null,
                    message = "Undo saved · revision ${write.revision}")
            is NativeCustomDraftWriteResult.Rejected -> {
                applyRead(saved.cycleId, host.readCustomCycleDraft(saved.cycleId))
                current.value = current.value.copy(refusal = write.reason,
                    message = "Undo was not saved; restored disk state.")
            }
        }
    }

    fun delete() = run {
        val draft = current.value.draft ?: return@run
        val write = host.deleteCustomCycleDraft(draft.cycleId, draft.revision)
        when (write) {
            is NativeCustomDraftWriteResult.Committed -> {
                lastUndo = null
                current.value = NativeKotlinCustomCycleState(
                    cycleId = draft.cycleId,
                    message = "Draft deleted · revision ${write.revision}. Older autosaves cannot restore it.")
            }
            is NativeCustomDraftWriteResult.Rejected -> {
                applyRead(draft.cycleId, host.readCustomCycleDraft(draft.cycleId))
                current.value = current.value.copy(refusal = write.reason,
                    message = "Delete was not saved; restored disk state.")
            }
        }
    }

    override fun close() {
        dismissProgramPicker()
        planEntries.clear()
        scope.cancel()
        lastUndo = null
        openedSource = null
    }
}
