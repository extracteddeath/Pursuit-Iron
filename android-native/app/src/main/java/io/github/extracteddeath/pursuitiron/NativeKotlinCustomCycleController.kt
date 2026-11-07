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
    val sourceRevision: Long? = null,
    /** Current owner-reviewed source context; stale edits are rechecked at audit. */
    val reviewedSourceSetup: AthleteSetupReview? = null,
    /** Factual set credit evidence only; does not contain MEV/MRV decisions. */
    val authoredWorkloadEvidence: CustomCoachPolicyEvidence? = null,
    val evidenceSourceRevision: Long? = null,
    val coachingAudit: CustomCoachAudit? = null,
    val auditSourceRevision: Long? = null,
    /** Inputs from the latest independently rechecked full-cycle audit.
     * Never persisted, fabricated, or reused after an authored draft edit. */
    val reviewedCoachInputs: CustomCoachAuditInputs? = null,
    val signoff: NativeCustomSignoffRead? = null,
    val signoffRefusal: NativeCustomSignoffRefusal? = null,
    val authoredPreview: CustomWorkoutDaySnapshot? = null,
    val authoredPreviewRefusal: CustomWorkoutSnapshotRefusal? = null,
    val consentBoundPreview: NativeCustomPreflightResult.ReviewedPreview? = null,
    val preparedWorkout: NativeCustomPreparedWorkout? = null,
    val preparationRefusal: NativeCustomPrepareRefusal? = null
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
        val latest = runCatching { authoringSource.snapshot() }.getOrNull()
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
        }
    }

    fun open(cycleId: String) = run {
        applyRead(cycleId, host.readCustomCycleDraft(cycleId))
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
                if (current.value.authoringRefusal == null)
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
                        current.value = current.value.copy(
                            draft = result.draft,
                            cycleId = result.draft.cycleId,
                            coachingAudit = null,
                            authoredWorkloadEvidence = null,
                            evidenceSourceRevision = null,
                            auditSourceRevision = null,
                            reviewedCoachInputs = null,
                            signoff = host.readCustomCycleConsent(result.draft.cycleId),
                            signoffRefusal = null,
                            authoredPreview = null,
                            authoredPreviewRefusal = null,
                            consentBoundPreview = null,
                            preparedWorkout = null,
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
            previous.reviewedCoachInputs==null)) return
        auditedInputs=null
        val staleSignoff=when(val old=previous.signoff) {
            is NativeCustomSignoffRead.Found->old.copy(isCurrent=false)
            else -> old
        }
        current.value=previous.copy(
            coachingAudit=null,
            reviewedCoachInputs=null,
            auditSourceRevision=null,
            consentBoundPreview=null,
            preparedWorkout=null,
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
    fun auditFullCycle(
        inputs: CustomCoachAuditInputs = CustomCoachAuditInputs()
    ) = run {
        val requested = current.value.draft ?: return@run
        val disk = host.readCustomCycleDraft(requested.cycleId)
        if(disk !is NativeCustomDraftReadResult.Found ||
            disk.draft.revision != requested.revision ||
            disk.draft != requested) {
            applyRead(requested.cycleId,disk)
            current.value=current.value.copy(
                message="The saved cycle changed. Reloaded before review; run the audit again.")
            return@run
        }
        val source=runCatching {authoringSource.snapshot()}.getOrNull()
        if(source==null || source.athleteId!=disk.draft.athleteId) {
            current.value=current.value.copy(
                coachingAudit=null,auditSourceRevision=null,
                reviewedCoachInputs=null,
                message="No current owner-reviewed native gym and catalog. Complete setup before coach review.")
            return@run
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
            return@run
        }
        auditedInputs=inputs
        current.value=current.value.copy(
            coachingAudit=result,auditSourceRevision=source.revision,
            reviewedCoachInputs=inputs,
            signoff=host.readCustomCycleConsent(disk.draft.cycleId,inputs),
            signoffRefusal=null,
            message="Whole-cycle coach audit: ${result.issues.size} actionable issues across ${result.weeks.size} evaluated weeks. No workout approved.")
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
     * Consent-bound preview performs a complete read and coach re-audit
     * under the owner lock. It intentionally does not issue a journal.
     */
    fun verifyConsentBoundDay(absoluteWeek:Int,weekday:Int) = run {
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
        when (write) {
            is NativeCustomDraftWriteResult.Committed ->
                current.value = current.value.copy(
                    draft = result.draft, canUndo = false,
                    coachingAudit = null, auditSourceRevision = null,
                    authoredWorkloadEvidence = null,
                    evidenceSourceRevision = null,
                    reviewedCoachInputs = null,
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
