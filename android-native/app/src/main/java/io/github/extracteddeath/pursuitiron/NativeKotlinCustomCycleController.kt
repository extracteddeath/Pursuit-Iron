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

/**
 * Candidate-only Compose state holder. All disk operations run on IO;
 * accepted UI state comes ONLY from a durable native write or a disk read.
 * No WebView/JS engine, optimistic save, fictitious program approval or
 * concurrent, silently overwritten auto-save.
 */
internal data class NativeKotlinCustomCycleState(
    val draft: CustomTrainingCycle? = null,
    val cycleId: String? = null,
    val busy: Boolean = false,
    val message: String? = null,
    val refusal: NativeCustomDraftRefusal? = null,
    val canUndo: Boolean = false,
    val authoringRefusal: CustomAuthoringRefusal? = null,
    val availableChoices: List<CustomExercisePickerOption> = emptyList(),
    val savedDrafts: List<NativeCustomDraftSummary> = emptyList(),
    val savedDraftsLoaded:Boolean = false,
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
    val preparedWorkout: NativeCustomPreparedWorkout? = null,
    val preparationRefusal: NativeCustomPrepareRefusal? = null,
    val dayCoaching: NativeCustomNextCoachResult.Reviewed? = null,
    val calibrationReview:NativeBlockCalibrationPreview.Ready? = null,
    val authoredReturnReview:NativeAuthoredLoadReturnPreview.Ready? = null,
    val calibrationReceipt:NativeBlockCalibrationReceipt? = null,
    val calibrationReset:NativePreparedManualResetPreview.Ready? = null,
    val issuedCalibrationId:String? = null,
    val pendingCalibrations:List<NativeCustomPreparedWorkout> = emptyList(),
    val pendingCalibrationAcceptances:List<NativeBlockCalibrationReceipt> = emptyList()
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
    private val current = MutableStateFlow(NativeKotlinCustomCycleState())
    val state: StateFlow<NativeKotlinCustomCycleState> = current.asStateFlow()
    private var lastUndo: CustomCycleDraftReceipt? = null
    private var auditedInputs: CustomCoachAuditInputs? = null
    private data class PendingWeekEdit(
        val source: NativeCustomAuthoringSnapshot,
        val inputs: CustomCoachAuditInputs,
        val review: CustomCoachWeekEditReview
    )
    private var pendingWeekEdit: PendingWeekEdit? = null

    private fun run(action: () -> Unit) {
        if (!pending.compareAndSet(false, true)) return
        current.value = current.value.copy(busy = true, refusal = null,
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
            }
        }
    }

    private fun applyRead(cycleId: String, result: NativeCustomDraftReadResult) {
        lastUndo = null
        auditedInputs = null
        pendingWeekEdit = null
        val latest = runCatching { authoringSource.snapshot() }.getOrNull()
        val assistance=(runCatching{host.readCoachAssistance()}
            .getOrNull() as? NativeCoachSettingsRead.Current)
            ?.settings?.policy
        val found = (result as? NativeCustomDraftReadResult.Found)?.draft
        val source = latest?.takeIf { it.athleteId == found?.athleteId }
        val choices = if (source == null) emptyList() else
            CustomExerciseAuthoringDomain.picker(source.setup,source.catalog)
        current.value = when (result) {
            is NativeCustomDraftReadResult.Found ->
                NativeKotlinCustomCycleState(
                    draft = result.draft,
                    cycleId = result.draft.cycleId,
                    availableChoices = choices,
                    sourceRevision = source?.revision,
                    coachingSuggestionsEnabled = assistance?.coachingSuggestionsEnabled==true,
                    progressionCoachingEnabled = assistance?.offersProgression==true,
                    fatigueCoachingEnabled = assistance?.offersFatigueAdvice==true,
                    reviewedSourceSetup = source?.setup,
                    signoff = host.readCustomCycleConsent(result.draft.cycleId),
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
    }

    /** Prefer an exact signed review, including custom rules. An unsigned
     * generated draft may still recover its original calibrated preview. */
    private fun recoverSavedCoachPolicy(cycleId:String) {
        if(current.value.draft?.cycleId!=cycleId)return
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

    fun open(cycleId: String) = run {
        applyRead(cycleId, host.readCustomCycleDraft(cycleId))
        recoverSavedCoachPolicy(cycleId)
        selectNextSessionIfReviewed()
    }

    /** Re-enter training from setup or Finish using the owned program census.
     * A single saved program is unambiguous; multiple programs require a choice.
     * Listing/reopening is read-only and never grants consent or starts training. */
    fun openSavedTraining() = run {
        when(val listed=host.listOwnedCustomCycleDrafts()) {
            is NativeCustomDraftListResult.Ready -> {
                val selected=listed.drafts.singleOrNull()
                if(selected==null) {
                    applySavedProgramList(listed)
                } else {
                    applyRead(selected.cycleId,
                        host.readCustomCycleDraft(selected.cycleId))
                    recoverSavedCoachPolicy(selected.cycleId)
                    selectNextSessionIfReviewed()
                }
            }
            is NativeCustomDraftListResult.Rejected ->applySavedProgramList(listed)
        }
    }

    private fun applySavedProgramList(listed:NativeCustomDraftListResult) {
        lastUndo=null
        auditedInputs=null
        pendingWeekEdit=null
        current.value=when(listed) {
            is NativeCustomDraftListResult.Ready ->
                NativeKotlinCustomCycleState(busy=true,
                    savedDrafts=listed.drafts,savedDraftsLoaded=true,
                    message=if(listed.drafts.isEmpty())
                        "Your setup is ready. Build your first program to continue."
                    else "Choose the saved program you want to continue.")
            is NativeCustomDraftListResult.Rejected ->
                NativeKotlinCustomCycleState(busy=true,
                    refusal=listed.reason,
                    message="Saved programs could not be verified. Reconfirm your setup or retry; no program was replaced.")
        }
    }

    /** Owner-verified, read-only program picker. IDs stay internal to navigation. */
    fun findSavedPrograms() = run {
        applySavedProgramList(host.listOwnedCustomCycleDrafts())
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
        if(value.coachingAudit?.status==CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW &&
            (value.signoff as? NativeCustomSignoffRead.Found)?.isCurrent==true)
            selectNextCycleSession()
    }

    fun findNextCycleSession()=run {selectNextCycleSession()}

    private fun selectNextCycleSession() {
        val before=current.value
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
    )=run {
        applyRead(cycleId,host.readCustomCycleDraft(cycleId))
        if(current.value.draft?.cycleId!=cycleId)return@run
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
        current.value=current.value.copy(dayCoaching=null,authoredReturnReview=null,
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
        if(verified !is NativeCustomPreflightResult.ReviewedPreview ||
            verified.session!=snap ||
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
        current.value=current.value.copy(dayCoaching=null,authoredReturnReview=null,
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
        command: (CustomTrainingCycle, AthleteSetupReview,
            List<CatalogMovement>) -> CustomAuthoringResult
    ) {
        var checkedSourceRevision: Long? = null
        edit(reviewedSourceRevision = { checkedSourceRevision }) { draft ->
            // Read inside IO. The subsequent write atomically checks that
            // exactly this same source revision remains under owner lock.
            val source = runCatching { authoringSource.snapshot() }.getOrNull()
            if (source == null || source.athleteId != draft.athleteId) {
                current.value = current.value.copy(
                    message = "Reviewed native gym and exercise catalog required. Nothing saved.",
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

    /** Accessible alternative to drag gestures, stable slot identity intact. */
    fun moveExercise(
        blockId: String, weekday: Int, slotId: String, direction: Int
    ) = edit { draft ->
        val day = draft.blocks.firstOrNull { it.blockId == blockId }
            ?.days?.firstOrNull { it.weekday == weekday }
        val order = day?.slots?.map { it.slotId }?.toMutableList()
        val index = order?.indexOf(slotId) ?: -1
        val target = index + direction
        if (order == null || index < 0 || target !in order.indices)
            CustomCycleBuilderResult.Rejected(
                CustomCycleBuilderRefusal.SLOT_ORDER_CONFLICT)
        else {
            val chosen = order.removeAt(index)
            order.add(target, chosen)
            CustomCycleBuilderDomain.reorderSlots(
                draft, draft.revision, blockId, weekday, order)
        }
    }

    fun editReviewedExerciseSets(
        blockId: String, weekday: Int, slotId: String,
        sets: List<CustomAuthoredSet>
    ) = editReviewed { draft, setup, catalog ->
        CustomExerciseAuthoringDomain.replaceSelectedSets(
            draft, draft.revision, blockId, weekday, slotId, sets, setup, catalog)
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
        val write = host.saveCustomCycleDraft(result.draft, saved.revision)
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
        scope.cancel()
        lastUndo = null
    }
}
