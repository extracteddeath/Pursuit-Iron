package io.github.extracteddeath.pursuitiron

import android.content.Context
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

/**
 * First REAL Android module integration of the independent Kotlin coaching
 * engine: app-private owner, program approvals, revision fence and issued
 * workout journal all belong to ONE process-scoped native host.
 *
 * NOT ACTIVE in NativeRuntimeHost or the Compose workout screen yet.
 * The legacy web-backed runtime remains the live owner until a deliberate
 * migrated and fully verified cutover. Do not call this host with converted
 * JS training output and claim native engine approval.
 *
 * R&D calls must run off the main thread; all methods perform disk I/O.
 */
/**
 * Same-device, explicit profile resume. A verified identity alone cannot
 * open workouts: the source must also survive current revision-locked decode.
 */
internal sealed interface NativeSavedSetupResume {
    data class Ready(val setup:ReviewedAthleteSetup,val sourceRevision:Long):
        NativeSavedSetupResume
    data class Rejected(val reason:String):NativeSavedSetupResume
}

/**
 * The transient generated preview is not trusted after process death.
 * Rebuild the full candidate from durable calibration plus current verified
 * source, require exact equality to the original saved cycle, then re-audit.
 */
internal sealed interface NativeRecoveredFirstProgramReview {
    data class Ready(val inputs:CustomCoachAuditInputs):
        NativeRecoveredFirstProgramReview
    data object NotGenerated:NativeRecoveredFirstProgramReview
    data class Rejected(val reason:String):
        NativeRecoveredFirstProgramReview
}

internal data class NativeKotlinTimeLimitChoiceReview(
    val workoutId:String,
    val expectedApprovalRevision:Long,
    val currentLimitMinutes:Int?,
    val proposal:KotlinShortSessionReview,
    /** Saved journal already exists: its original time choice is immutable. */
    val alreadyIssued:Boolean
)

/** Read-only preview and explicit pre-issuance approval are separate actions. */
internal class NativeKotlinCandidateHost(context:Context) {
    private val root=context.applicationContext.noBackupFilesDir
        .resolve("kotlin-coach-v1").toPath()
    private val localAthlete=AppPrivateKotlinLocalAthlete(
        context.applicationContext)
    private val revisionFence=NativeKotlinApprovalRevisionFence(root)
    private val coachSettings=NativeKotlinCoachAssistanceSettingsStore(
        root,revisionFence,localAthlete)
    private val pinnedExerciseNotes=NativeKotlinPinnedExerciseNoteFileStore(
        root,revisionFence,localAthlete)
    private val approvalStore=NativeKotlinApprovalFileAuthority(
        root,revisionFence,localAthlete)
    private val customDraftStore=NativeCustomCycleDraftFileStore(
        root,revisionFence,localAthlete)
    private val firstProgramSeedStore=NativeKotlinFirstProgramSeedFileStore(
        root,revisionFence,localAthlete)
    private val authoringSourceStore=NativeKotlinCustomAuthoringSourceFileStore(
        root,revisionFence,localAthlete)
    private val customAuthoringCoordinator=NativeKotlinCustomAuthoringDraftCoordinator(
        authoringSourceStore,customDraftStore,revisionFence,localAthlete)
    private val customCycleSignoffs=NativeKotlinCustomCycleSignoffFileStore(
        root,revisionFence,localAthlete,customDraftStore,authoringSourceStore)
    private val adaptiveCyclePermissions=NativeKotlinAdaptiveCyclePermissionFileStore(
        root,revisionFence,localAthlete)
    private val futureBlockContexts=NativeKotlinFutureBlockContextFileStore(
        root,revisionFence,localAthlete,customDraftStore,authoringSourceStore)
    private val adaptiveCycleDrafts=NativeKotlinAdaptiveCycleDraftAcceptance(
        revisionFence,localAthlete,customDraftStore,authoringSourceStore,
        adaptiveCyclePermissions,customCycleSignoffs,futureBlockContexts,
        coachSettings)
    private val futureActivationHandoff=NativeKotlinFutureActivationHandoffFileStore(
        root,revisionFence,localAthlete,authoringSourceStore)
    private val customWorkoutPreflight=NativeKotlinCustomWorkoutPreflight(
        revisionFence,localAthlete,customDraftStore,authoringSourceStore,
        customCycleSignoffs)
    private val customPreparedStore=NativeKotlinCustomWorkoutPreparedStore(
        root,revisionFence,localAthlete,customWorkoutPreflight,
        futureActivationHandoff)
    private val machineRosters=NativeKotlinMachineRosterFileStore(root)
    private val journalStore=KotlinNativeJournalFileStore(root)
    private val correctionStore=KotlinNativeSavedCorrectionFileStore(root,journalStore)
    private val historyStore=NativeKotlinHistoryManifestFileStore(root)
    private val customIssuedStore=NativeKotlinCustomWorkoutJournalFileStore(
        root,revisionFence,localAthlete,customPreparedStore,
        customWorkoutPreflight,historyStore,journalStore,
        futureActivationHandoff,authoringSourceStore)
    private val customLiveSetCoach=NativeKotlinCustomLiveSetCoachReview(
        revisionFence,localAthlete,customIssuedStore,authoringSourceStore,coachSettings)
    private val customHistoryTransactions=NativeKotlinCustomHistoryTransaction(
        historyStore,customIssuedStore,revisionFence,localAthlete)
    private val customSavedCorrections=NativeKotlinCustomSavedCorrectionFileStore(
        root,revisionFence,localAthlete,customIssuedStore,historyStore)
    private val customCorrectionTransactions=
        NativeKotlinCustomHistoryCorrectionTransaction(
            historyStore,customIssuedStore,customSavedCorrections,
            revisionFence,localAthlete)
    private val mixedHistoryReader=NativeKotlinMixedHistoryReadCoordinator(
        historyStore,journalStore,correctionStore,customIssuedStore,
        approvalStore,localAthlete,revisionFence,customSavedCorrections)
    private val outstandingCustomInventory=NativeKotlinCustomOutstandingWorkoutInventory(
        customPreparedStore,customIssuedStore)
    private val resumableCustomWorkouts=NativeKotlinCustomResumeReview(
        revisionFence,localAthlete,mixedHistoryReader,
        outstandingCustomInventory,customIssuedStore)
    private val nextCustomCycleSession=NativeKotlinCustomNextCycleSessionReview(
        revisionFence,localAthlete,customDraftStore,authoringSourceStore,
        customCycleSignoffs,mixedHistoryReader,outstandingCustomInventory,
        customPreparedStore)
    private val composerActivationInventory=NativeKotlinComposerActivationInventory(
        journalStore,approvalStore)
    private val futureActivationPreflight=NativeKotlinFutureBlockActivationPreflight(
        revisionFence,localAthlete,customDraftStore,futureBlockContexts,
        mixedHistoryReader,customIssuedStore,outstandingCustomInventory,
        composerActivationInventory)
    private val futureActivationApproval=NativeKotlinFutureActivationApprovalFileStore(
        root,revisionFence,localAthlete,futureActivationPreflight,
        futureBlockContexts,customDraftStore,authoringSourceStore,
        adaptiveCyclePermissions)
    private val futureActivationCommit=NativeKotlinFutureActivationCommitTransaction(
        root,revisionFence,localAthlete,authoringSourceStore,customDraftStore,
        futureBlockContexts,futureActivationApproval,futureActivationHandoff)
    private val activatedCycleSignoffs=NativeKotlinActivatedCycleSignoffFileStore(
        root,revisionFence,localAthlete,futureActivationCommit)
    private val customNextWorkoutCoach=NativeKotlinCustomNextWorkoutReview(
        revisionFence,localAthlete,futureActivationCommit,
        activatedCycleSignoffs,authoringSourceStore,mixedHistoryReader,
        customIssuedStore,coachSettings,customWorkoutPreflight)
    private val blockCalibrations=NativeKotlinBlockCalibrationConsentStore(
        root,revisionFence,localAthlete,customNextWorkoutCoach,customDraftStore,
        authoringSourceStore,adaptiveCyclePermissions,mixedHistoryReader,
        outstandingCustomInventory,journalStore,customPreparedStore)
    private val futureWeekLoadOptions=NativeKotlinCustomFutureWeekLoadOptionReview(
        revisionFence,localAthlete,customNextWorkoutCoach,
        futureActivationCommit,customDraftStore,authoringSourceStore,
        adaptiveCyclePermissions,coachSettings,mixedHistoryReader,
        outstandingCustomInventory)
    private val acceptedFutureWeekLoads=NativeKotlinFutureWeekLoadConsentFileStore(
        root,revisionFence,localAthlete,futureWeekLoadOptions)
    private val futureWeekLoadHandoff=NativeKotlinAcceptedFutureWeekLoadHandoff(
        revisionFence,acceptedFutureWeekLoads,futureWeekLoadOptions,
        customNextWorkoutCoach,futureActivationCommit)
    private val acceptedFutureRepTargets=NativeKotlinCustomNextRepApprovalFileStore(
        root,revisionFence,localAthlete,futureActivationCommit,
        customNextWorkoutCoach,customPreparedStore,customIssuedStore)
    private val acceptedFutureLoads=NativeKotlinCustomNextLoadApprovalFileStore(
        root,revisionFence,localAthlete,futureActivationCommit,
        customNextWorkoutCoach,customPreparedStore,customIssuedStore)
    // Read-only indexed candidate; version-2 ledger remains the sole owner.
    private val segmentedHistoryShadow=NativeKotlinSegmentedHistoryShadowStore(
        root,historyStore,revisionFence,localAthlete)
    private val segmentedHistoryHandoff=NativeKotlinSegmentedHistoryHandoffGate(
        historyStore,segmentedHistoryShadow,mixedHistoryReader,
        revisionFence,localAthlete)
    // New native issuance requires today's authenticated gym/catalog, while
    // already-issued journals retain their historical access contract.
    private val gymCoherentAuthority=NativeKotlinGymCoherentWorkoutAuthority(
        approvalStore,authoringSourceStore,revisionFence)
    private val nativeHistoryEnrollment=NativeKotlinHistoryEnrollmentCoordinator(
        journalStore,correctionStore,historyStore,revisionFence,
        gymCoherentAuthority)
    private val coordinator=NativeKotlinWorkoutCoordinator(
        journalStore,gymCoherentAuthority,revisionFence,
        NativeKotlinAcceptedRepSnapshotVerifier { owner,approval ->
            val signed=localAthlete.signedInIdentity()
            if(signed==null || signed.ownerKey!=owner ||
                signed.athleteId!=approval.athleteId)null
            else mixedHistoryReader.rederiveAcceptedRepSnapshot(
                owner,signed.athleteId,approvalStore,approval,
                authoringSourceStore)
        },futureActivationHandoff)
    private val historyTransactions=NativeKotlinHistoryTransactionCoordinator(
        historyStore,journalStore,correctionStore,approvalStore,revisionFence)
    private val trackedWorkout=NativeKotlinTrackedWorkoutGateway(
        coordinator,historyTransactions,historyStore,correctionStore,
        gymCoherentAuthority,revisionFence)

    /**
     * Physical machine alternatives use an independent owner-fenced source.
     * Changing a default never rewrites a reviewed program or past set receipt.
     */
    fun readMachineRoster():NativeMachineRosterResult {
        val identity=localAthlete.signedInIdentity()
            ?:return NativeMachineRosterResult.Rejected("NOT_AUTHORIZED")
        return revisionFence.withOwnerTransaction(identity.ownerKey) {
            val source=(authoringSourceStore.read(identity.ownerKey)
                as? NativeAuthoringSourceRead.Found)?.source
            if(source==null || source.ownerKey!=identity.ownerKey ||
                source.athleteId!=identity.athleteId ||
                source.setup.reviewed==null)
                NativeMachineRosterResult.Rejected("UNVERIFIED_GYM")
            else machineRosters.read(identity.ownerKey)
        }
    }

    fun saveMachineRoster(
        expectedRevision:Long,configurations:List<KotlinGymMachineAlternatives>
    ):NativeMachineRosterResult {
        val identity=localAthlete.signedInIdentity()
            ?:return NativeMachineRosterResult.Rejected("NOT_AUTHORIZED")
        return revisionFence.withOwnerTransaction(identity.ownerKey) {
            val source=(authoringSourceStore.read(identity.ownerKey)
                as? NativeAuthoringSourceRead.Found)?.source
            val reviewed=source?.setup?.reviewed
            if(source==null || source.ownerKey!=identity.ownerKey ||
                source.athleteId!=identity.athleteId || reviewed==null ||
                configurations.any {it.gymId!=reviewed.gym.gymId})
                NativeMachineRosterResult.Rejected("UNVERIFIED_GYM")
            else machineRosters.save(identity.ownerKey,expectedRevision,configurations)
        }
    }

    /**
     * Read-only profile/settings summary for the CURRENT confirmed athlete.
     * Unlike explicit-resume, this never activates or deactivates a profile.
     * Source ownership and its reviewed setup are verified under the same
     * owner transaction used for canonical workout/history reads.
     */
    fun reviewActiveAthleteSetup():NativeSavedSetupResume {
        val identity=localAthlete.signedInIdentity()
            ?:return NativeSavedSetupResume.Rejected(
                "Confirm your saved athlete profile before opening Profile.")
        return runCatching {
            revisionFence.withOwnerTransaction(identity.ownerKey) {
                val source=(authoringSourceStore.read(identity.ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                val reviewed=source?.setup?.reviewed
                if(source==null || source.ownerKey!=identity.ownerKey ||
                    source.athleteId!=identity.athleteId ||
                    source.setup.mayReviewProgramCandidate!=true ||
                    reviewed?.athleteProfileId!=identity.athleteId)
                    NativeSavedSetupResume.Rejected(
                        "The current saved training profile could not be verified.")
                else NativeSavedSetupResume.Ready(reviewed,source.revision)
            }
        }.getOrElse {
            NativeSavedSetupResume.Rejected(
                "The current saved training profile could not be read.")
        }
    }

    /** A displayed local ID is not a session nor access to gym/history. */
    fun savedAthleteIdForExplicitResume():String? =
        localAthlete.savedAthleteIdForExplicitResume()

    /**
     * Restores the ORIGINAL athlete after a cold start, only on a direct
     * confirmation. The authoring store re-decodes and re-runs Kotlin setup
     * validation; missing, corrupt, revoked or mismatched sources fail closed.
     * No new profile, gym, catalog, plan, or workout is written here.
     */
    fun resumeVerifiedSavedSetup(confirmedAthleteId:String):NativeSavedSetupResume {
        val activated=localAthlete.activatePreviouslySaved(confirmedAthleteId)
        val identity=(activated as? AppPrivateKotlinLocalAthlete.Enrollment.Activated)
            ?.identity ?:return NativeSavedSetupResume.Rejected(
            (activated as? AppPrivateKotlinLocalAthlete.Enrollment.Rejected)
                ?.reason ?: "Saved athlete could not be confirmed")
        return try {
            val found=authoringSourceStore.read(identity.ownerKey)
            val source=(found as? NativeAuthoringSourceRead.Found)?.source
            val reviewed=source?.setup?.reviewed
            if(source==null || source.ownerKey!=identity.ownerKey ||
                source.athleteId!=identity.athleteId ||
                !source.setup.mayReviewProgramCandidate ||
                reviewed?.athleteProfileId!=identity.athleteId) {
                localAthlete.deactivate()
                NativeSavedSetupResume.Rejected(
                    "Saved training setup is missing, revoked or cannot be verified. No private training data was opened.")
            } else NativeSavedSetupResume.Ready(reviewed,source.revision)
        } catch(_:Exception) {
            localAthlete.deactivate()
            NativeSavedSetupResume.Rejected(
                "Saved training setup could not be verified. Nothing was replaced.")
        }
    }


    /** The Kotlin engine owns readiness validation, never the JS runtime. */
    /**
     * Athlete-facing coaching preferences (Settings > Training > Coach).
     * STANDARD: suggestions and fatigue; PROGRESSION_ONLY: naturally earned
     * rep/load options, no fatigue-driven edits; MANUAL: no suggestions.
     * Turning these off never disables workout logging or Finish.
     */
    fun readCoachAssistance():NativeCoachSettingsRead {
        val athlete=localAthlete.signedInIdentity()
            ?:return NativeCoachSettingsRead.Rejected(
                NativeCoachSettingsRefusal.NOT_AUTHORIZED)
        return coachSettings.read(athlete.ownerKey)
    }

    fun updateCoachAssistance(
        expectedRevision:Long,policy:CoachAssistancePolicy
    ):NativeCoachSettingsWrite {
        val athlete=localAthlete.signedInIdentity()
            ?:return NativeCoachSettingsWrite.Rejected(
                NativeCoachSettingsRefusal.NOT_AUTHORIZED)
        return coachSettings.save(athlete.ownerKey,expectedRevision,policy)
    }

    /** No recommendations are generated while the preference read is invalid. */
    private fun availableCoachAssistance():CoachAssistancePolicy? {
        val athlete=localAthlete.signedInIdentity()?:return null
        return (coachSettings.read(athlete.ownerKey)
            as? NativeCoachSettingsRead.Current)?.settings?.policy
    }

    fun reviewSetup(draft:AthleteSetupDraft):AthleteSetupReview =
        CoachAthleteSetupDomain.review(draft)

    /**
     * Native onboarding's explicit confirmation must precede local enrolment.
     * A review-only draft grants NO write access.
     */
    fun activateReviewedSetup(draft:AthleteSetupDraft):
        AppPrivateKotlinLocalAthlete.Enrollment {
        // Never trust an externally supplied AthleteSetupReview object. The
        // native engine revalidates the actual onboarding inputs at commit.
        val review=CoachAthleteSetupDomain.review(draft)
        val reviewed=review.reviewed
        if(review.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            reviewed==null)
            return AppPrivateKotlinLocalAthlete.Enrollment.Rejected(
                "Resolve onboarding requirements before creating an athlete")
        return localAthlete.activateReviewed(reviewed)
    }

    /** Deliberately clears only in-memory activation; training files stay. */
    fun deactivateLocalAthlete() {
        localAthlete.deactivate()
    }

    /**
     * These methods are a candidate native gym/catalog repository, NOT an
     * import of legacy JS choices and NOT an athlete program approval.
     * Native onboarding and curated catalog ownership still need activation.
     */
    /**
     * Explicit first-time NATIVE setup handoff. These 20 movements have
     * documented engineering-provisional muscle-credit reviews; they contain
     * NO user gym or target weights and do not make a workout executable.
     * Caller must present reviewed athlete inputs and obtain user approval.
     *
     * A source already on disk is NEVER reset by onboarding. Enrollment
     * cannot replace an existing different local athlete.
     */
    fun activateReviewedStarterAuthoringSource(
        draft:AthleteSetupDraft
    ):NativeAuthoringSourceWrite {
        val review=CoachAthleteSetupDomain.review(draft)
        val verified=review.reviewed
        if(review.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            verified==null)
            return NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.INVALID_SOURCE)
        val enrollment=activateReviewedSetup(draft)
        if(enrollment !is AppPrivateKotlinLocalAthlete.Enrollment.Activated)
            return NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
        // Explicitly restoring this SAME device-local athlete is safe, but
        // never overwrite its previously curated catalog or verified stock.
        return when(val existing=authoringSourceStore.read(
            enrollment.identity.ownerKey)) {
            is NativeAuthoringSourceRead.Found ->
                if(existing.source.athleteId==verified.athleteProfileId)
                    NativeAuthoringSourceWrite.Committed(existing.source.revision)
                else NativeAuthoringSourceWrite.Rejected(
                    NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
            NativeAuthoringSourceRead.Missing ->
                publishCustomAuthoringSource(draft,
                    StarterExerciseTranche.movements(),expectedRevision=0)
            is NativeAuthoringSourceRead.Revoked ->
                NativeAuthoringSourceWrite.Rejected(
                    NativeAuthoringSourceRefusal.STALE_REVISION)
            is NativeAuthoringSourceRead.Rejected ->
                NativeAuthoringSourceWrite.Rejected(existing.reason)
        }
    }

    /**
     * Durable, still NON-ISSUED custom session preparation. Must come from
     * a full current athlete-consent re-audit under the shared owner lock.
     * Cannot record sets or finish a workout; not a ComposerRequest journal.
     */
    fun prepareReviewedCustomWorkout(
        workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,inputs:CustomCoachAuditInputs
    ):NativeCustomPrepareResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomPrepareResult.Rejected(
                NativeCustomPrepareRefusal.NOT_AUTHORIZED)
        return customPreparedStore.prepare(owner.ownerKey,workoutId,
            cycleId,absoluteWeek,weekday,inputs,blockCalibrations)
    }

    /**
     * Explicit custom-time approval is saved as a separate immutable
     * prepared workout, not as a change to the authored training cycle.
     * The native store rechecks CURRENT consent and physical history.
     */
    fun prepareReviewedCustomShorterWorkout(
        workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,
        shown:NativeCustomShortSessionPreflight.Reviewed,
        athleteConfirmed:Boolean
    ):NativeCustomPrepareResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomPrepareResult.Rejected(
                NativeCustomPrepareRefusal.NOT_AUTHORIZED)
        return customPreparedStore.prepareShorter(
            owner.ownerKey,workoutId,cycleId,absoluteWeek,weekday,
            inputs,shown,athleteConfirmed,blockCalibrations)
    }

    /**
     * Read-only, current-owner preview of Full before discarding a saved
     * custom time cut. Never edits an issued set or authored training cycle.
     */
    fun previewUndoCustomShorterWorkout(
        workoutId:String,cycleId:String,
        absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs
    ):NativeCustomTimeUndoPreview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomTimeUndoPreview.Rejected(
                NativeCustomTimeUndoRefusal.NOT_AUTHORIZED)
        return customPreparedStore.previewTimeUndo(
            owner.ownerKey,workoutId,cycleId,
            absoluteWeek,weekday,inputs,customIssuedStore,blockCalibrations)
    }

    /** Explicit acceptance of exactly the previously displayed full targets.
     * The store checks owner, checksum CAS, history and signed consent again. */
    fun acceptUndoCustomShorterWorkout(
        shown:NativeCustomTimeUndoPreview.Ready,
        inputs:CustomCoachAuditInputs,
        athleteConfirmed:Boolean
    ):NativeCustomTimeUndoWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomTimeUndoWrite.Rejected(
                NativeCustomTimeUndoRefusal.NOT_AUTHORIZED)
        if(owner.ownerKey!=shown.shortened.ownerKey ||
            owner.athleteId!=shown.shortened.athleteId)
            return NativeCustomTimeUndoWrite.Rejected(
                NativeCustomTimeUndoRefusal.NOT_AUTHORIZED)
        return customPreparedStore.acceptTimeUndo(
            shown,inputs,customIssuedStore,blockCalibrations,
            athleteConfirmed)
    }

    fun openPreparedCustomWorkout(workoutId:String):NativeCustomPreparedRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomPreparedRead.Rejected(
                NativeCustomPrepareRefusal.NOT_AUTHORIZED)
        return customPreparedStore.open(owner.ownerKey,workoutId)
    }

    /**
     * PRE-ISSUANCE, read-only, owner-rechecked custom session projection.
     * Not backed by an issued-workout grant and cannot write journal sets.
     * New custom-workout issuer must re-run this under the history lock.
     */
    fun previewConsentBoundCustomWorkout(
        cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs
    ):NativeCustomPreflightResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomPreflightResult.Rejected(
                NativeCustomPreflightRefusal.NOT_AUTHORIZED)
        return customWorkoutPreflight.inspect(owner.ownerKey,cycleId,
            absoluteWeek,weekday,inputs)
    }

    /**
     * Source-verified custom shorter-session REVIEW only. Pinned, superset,
     * strength and amplifier semantics cannot be altered by a UI preview.
     * The custom issuer still requires its own future signed shortened
     * preparation, and will NOT accept this proposal as an issued workout.
     */
    fun previewConsentBoundCustomShorterSession(
        cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,minutes:Int
    ):NativeCustomShortSessionPreflight {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomShortSessionPreflight.Rejected(
                NativeCustomPreflightRefusal.NOT_AUTHORIZED)
        return customWorkoutPreflight.reviewShorter(
            owner.ownerKey,cycleId,absoluteWeek,weekday,inputs,minutes)
    }

    /**
     * Candidate-only Kotlin CUSTOM workout issuance. First issue requires
     * authenticated, enrolled history, exact durable prepared targets and
     * CURRENT coach-reviewed consent. A prepared preview by itself cannot
     * issue, log or finish a workout.
     */
    fun issueReviewedCustomWorkout(
        workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs
    ):NativeCustomJournalResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_AUTHORIZED)
        return customIssuedStore.issue(owner.ownerKey,workoutId,cycleId,
            absoluteWeek,weekday,inputs,blockCalibrations)
    }

    /**
     * An owner-fenced, historical-source-verified list of only issued and
     * unfinished custom workouts. No user-entered ID, local cache or
     * prepared-but-not-issued preview can grant access to a workout.
     */
    /** Reconciles consent, real history and exact prepared/issued workouts
     * before recommending the next saved-cycle session. Never issues a set. */
    fun reviewNextCustomCycleSession(
        cycleId:String,inputs:CustomCoachAuditInputs
    ):NativeCustomNextCycleSlot {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomNextCycleSlot.Rejected(
                "The native athlete is not active.")
        return nextCustomCycleSession.review(
            owner.ownerKey,cycleId,inputs)
    }

    fun reviewResumableCustomWorkouts():NativeCustomResumeReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomResumeReview.Rejected(
                NativeCustomResumeRefusal.NOT_AUTHORIZED)
        return resumableCustomWorkouts.review(owner.ownerKey)
    }

    /** Past committed prescriptions remain independently cold-recoverable. */
    fun openIssuedCustomWorkout(workoutId:String):NativeCustomJournalResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_AUTHORIZED)
        return customIssuedStore.open(owner.ownerKey,workoutId)
    }

    /**
     * Owner-locked one-session swap of an unstarted custom exercise.
     * The journal store rebuilds the replacement from current reviewed
     * source facts, rechecks every user-entered set and commits atomically.
     * No plan/cycle update or old-load transfer is implicit.
     */
    fun swapIssuedCustomExercise(
        workoutId:String,eventId:String,expectedRevision:Long,
        previousSlotId:String,replacementExerciseId:String,
        sets:List<CustomAuthoredSet>,note:String
    ):NativeCustomJournalResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_AUTHORIZED)
        return customIssuedStore.swap(
            owner.ownerKey,workoutId,eventId,expectedRevision,
            previousSlotId,replacementExerciseId,sets,note)
    }

    /**
     * Undo an untouched, still-active workout-only swap. No program grant
     * or authored-cycle mutation; the original issued slot is restored.
     */
    fun undoIssuedCustomExerciseSwap(
        workoutId:String,eventId:String,expectedRevision:Long,
        swapEventId:String
    ):NativeCustomJournalResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_AUTHORIZED)
        return customIssuedStore.undoSwap(
            owner.ownerKey,workoutId,eventId,expectedRevision,swapEventId)
    }

    /** Authenticated custom-session note. This is an actual atomic journal
     * receipt, not a local-only preference or a planned program note. */
    fun saveIssuedCustomSessionNote(
        workoutId:String,
        operation:CustomWorkoutJournalOperation.SessionNote
    ):NativeCustomJournalResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_AUTHORIZED)
        return customIssuedStore.saveSessionNote(
            owner.ownerKey,workoutId,operation)
    }

    /**
     * An exercise must be present in this athlete's cold-opened, verified
     * issued native workout before any cross-session pinned note is read.
     * A history-only exercise ID, autocomplete or nearby pattern is not a
     * valid editing entitlement. Notes never authorize new training sets.
     */
    private fun canAccessPinnedExercise(
        owner:NativeKotlinOwnerIdentity,
        workoutId:String,exerciseId:String,
        custom:Boolean,forWrite:Boolean
    ):Boolean {
        if(!NativePinnedExerciseNotePolicy.validExercise(exerciseId) ||
            !workoutId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")))
            return false
        if(custom) {
            val current=customIssuedStore.open(owner.ownerKey,workoutId)
                as? NativeCustomJournalResult.Durable ?:return false
            val journal=current.issued.journal
            return current.issued.athleteId==owner.athleteId &&
                (!forWrite || journal.finishedByEventId==null) &&
                journal.snapshot.slots.any{it.exerciseId==exerciseId}
        }
        val current=trackedWorkout.open(
            owner.ownerKey,owner.athleteId,workoutId)
            as? NativeJournalFileResult.Durable ?:return false
        return (!forWrite || current.journal.finishedByEventId==null) &&
            current.journal.snapshot.targets.any{it.exerciseId==exerciseId}
    }

    /** Owner-verified pinned note for this exact issued exercise ID. */
    fun readPinnedExerciseNote(
        workoutId:String,exerciseId:String,custom:Boolean
    ):NativePinnedNoteRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativePinnedNoteRead.Rejected(
                NativePinnedNoteRefusal.NOT_AUTHORIZED)
        if(!canAccessPinnedExercise(owner,workoutId,exerciseId,custom,false))
            return NativePinnedNoteRead.Rejected(
                NativePinnedNoteRefusal.NOT_AUTHORIZED)
        return pinnedExerciseNotes.read(owner.ownerKey,exerciseId)
    }

    /**
     * Display-only setup fields from CURRENT owner-reviewed native catalog
     * metadata. No catalog or exercise name grants access on its own: the
     * issued-workout entitlement is checked before exposing descriptors.
     * Old stored keys are preserved as saved-earlier fields even if the
     * reviewed source has changed or become unavailable.
     */
    fun reviewedPinnedExerciseSetupFields(
        workoutId:String,exerciseId:String,custom:Boolean,
        savedSetup:Map<String,String>
    ):List<NativePinnedSetupField>? {
        val owner=localAthlete.signedInIdentity() ?:return null
        if(!canAccessPinnedExercise(owner,workoutId,exerciseId,custom,false))
            return null
        val source=runCatching {
            (authoringSourceStore.read(owner.ownerKey)
                as? NativeAuthoringSourceRead.Found)?.source
        }.getOrNull()?.takeIf {
            it.ownerKey==owner.ownerKey && it.athleteId==owner.athleteId &&
                it.setup.mayReviewProgramCandidate &&
                it.setup.reviewed?.athleteProfileId==owner.athleteId
        }
        val movement=source?.catalog?.firstOrNull{it.exerciseId==exerciseId}
        return NativePinnedSetupFieldDomain.fields(movement,savedSetup)
    }

    /** CAS-protected user text/setup; no training plan or journal mutation. */
    fun savePinnedExerciseNote(
        workoutId:String,exerciseId:String,custom:Boolean,
        expectedRevision:Long,eventId:String,text:String,
        setup:Map<String,String>
    ):NativePinnedNoteWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativePinnedNoteWrite.Rejected(
                NativePinnedNoteRefusal.NOT_AUTHORIZED)
        if(!canAccessPinnedExercise(owner,workoutId,exerciseId,custom,true))
            return NativePinnedNoteWrite.Rejected(
                NativePinnedNoteRefusal.NOT_AUTHORIZED)
        return pinnedExerciseNotes.save(owner.ownerKey,exerciseId,
            expectedRevision,eventId,text,setup)
    }

    /** Actual observations, never suggestions or virtual completed sets. */
    fun recordIssuedCustomSet(
        workoutId:String,operation:CustomWorkoutJournalOperation.Record
    ):NativeCustomJournalResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_AUTHORIZED)
        return customIssuedStore.record(owner.ownerKey,workoutId,operation)
    }

    /**
     * Custom Finish is committed ONLY by shared native history redo.
     * No direct reducer Finish or misleading in-memory completion.
     */
    fun finishIssuedCustomWorkout(
        workoutId:String,
        operation:CustomWorkoutJournalOperation.Finish
    ):NativeCustomFinishResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.NOT_AUTHORIZED)
        return customHistoryTransactions.complete(
            owner.ownerKey,owner.athleteId,workoutId,operation)
    }

    /** Recover an interrupted custom Finish after app cold-start. */
    fun recoverPendingCustomHistory():NativeCustomFinishResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomFinishResult.Rejected(
                NativeCustomFinishRefusal.NOT_AUTHORIZED)
        return customHistoryTransactions.recover(
            owner.ownerKey,owner.athleteId)
    }

    /**
     * Candidate-only saved custom corrections. The original issued
     * session and Finish stay immutable; the shared history transaction
     * must durably acknowledge before the screen can show edited facts.
     */
    /**
     * Owner-scoped, read-only correction revision for an ALREADY FINISHED
     * custom workout. The UI must also read verified mixed history; a
     * revision alone never authorizes editing. The transaction independently
     * validates current manifest, predecessor event and revision at commit.
     */
    fun readFinishedCustomCorrections(
        workoutId:String
    ):NativeCustomSavedCorrectionResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomSavedCorrectionResult.Rejected(
                NativeCustomSavedCorrectionRefusal.NOT_AUTHORIZED)
        return customSavedCorrections.open(owner.ownerKey,workoutId)
    }

    fun correctFinishedCustomWorkout(
        workoutId:String,operation:CustomSavedVisitCorrection
    ):NativeCustomCorrectionTransactionResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomCorrectionTransactionResult.Rejected(
                NativeCustomCorrectionTransactionRefusal.NOT_AUTHORIZED)
        return customCorrectionTransactions.correct(
            owner.ownerKey,owner.athleteId,workoutId,operation)
    }

    fun recoverPendingCustomCorrection():
        NativeCustomCorrectionTransactionResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomCorrectionTransactionResult.Rejected(
                NativeCustomCorrectionTransactionRefusal.NOT_AUTHORIZED)
        return customCorrectionTransactions.recover(
            owner.ownerKey,owner.athleteId)
    }

    /**
     * Explicitly opt in to a SEPARATE Kotlin-Lab training history, not a
     * migration and not a claim that the person has never trained before.
     * Original JS/web workout data remains untouched and is not included in
     * this independent history or its fatigue/progression evidence.
     *
     * Fresh-owner origin, owner/current-source and ALL native journal,
     * correction, custom preparation and issue inventories are checked under
     * the same revision fence BEFORE an empty manifest may be created.
     */
    fun enrollSeparateKotlinHistory(
        explicitlyConfirmed:Boolean
    ):NativeHistoryEnrollmentResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeHistoryEnrollmentResult.Rejected(
                NativeHistoryEnrollmentRefusal.NOT_AUTHORIZED)
        return nativeHistoryEnrollment.enrollSeparateLocalHistory(
            owner.ownerKey,owner.athleteId,explicitlyConfirmed,
            authenticateOwner={o,a->
                localAthlete.signedInIdentity()==owner &&
                    owner.ownerKey==o && owner.athleteId==a
            },
            verifyIndependentLocalOwner={o,a->
                val source=(authoringSourceStore.read(o)
                    as? NativeAuthoringSourceRead.Found)?.source
                localAthlete.isIndependentLocalProfileOwner(o,a) &&
                    source?.ownerKey==o && source.athleteId==a &&
                    source.setup.mayReviewProgramCandidate &&
                    source.setup.reviewed?.athleteProfileId==a
            },
            customInventory=outstandingCustomInventory)
    }

    /**
     * Full mixed Composer/custom native history, reconstructed from committed
     * private journals under the authenticated shared owner fence.
     * Review-only: does not authorize PR banners or progression mutations.
     */
    fun reviewVerifiedKotlinHistory():NativeKotlinMixedHistoryReadResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeKotlinMixedHistoryReadResult.Rejected(
                NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED)
        return mixedHistoryReader.read(owner.ownerKey,owner.athleteId)
    }

    /**
     * Read-only Target/Last reference, cold-opened under the same owner fence
     * as corrected history. UI-provided sets or cached workout facts cannot
     * become a reference. Logging never depends on this optional read.
     */
    fun reviewWorkoutLastReferences(
        workoutId:String,custom:Boolean
    ):WorkoutLastReferenceResult {
        val owner=localAthlete.signedInIdentity()
            ?:return WorkoutLastReferenceResult.Unavailable
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                val history=mixedHistoryReader.read(owner.ownerKey,owner.athleteId)
                    as? NativeKotlinMixedHistoryReadResult.Review
                    ?:return@withOwnerTransaction WorkoutLastReferenceResult.Unavailable
                val result=if(custom) {
                    val current=(customIssuedStore.open(owner.ownerKey,workoutId)
                        as? NativeCustomJournalResult.Durable)?.issued?.journal
                        ?:return@withOwnerTransaction WorkoutLastReferenceResult.Unavailable
                    val completed=ArrayList<CustomCoachingHistoricalSession>()
                    for(entry in history.timeline.completions) {
                        if(entry !is NativeKotlinMixedHistoryCompletion.Custom ||
                            entry.evidence.cycleId!=current.snapshot.stamp.cycleId)continue
                        val saved=(customIssuedStore.open(owner.ownerKey,entry.manifest.workoutId)
                            as? NativeCustomJournalResult.Durable)?.issued?.journal
                            ?:return@withOwnerTransaction WorkoutLastReferenceResult.Unavailable
                        if(saved.workoutId!=entry.evidence.workoutId ||
                            saved.snapshot.stamp.athleteId!=owner.athleteId ||
                            saved.revision!=entry.evidence.journalRevision)
                            return@withOwnerTransaction WorkoutLastReferenceResult.Unavailable
                        completed+=CustomCoachingHistoricalSession(
                            entry.manifest.completionOrder,saved.snapshot,entry.evidence)
                    }
                    WorkoutLastReferenceDomain.custom(workoutId,current.snapshot,completed)
                } else {
                    val current=trackedWorkout.open(owner.ownerKey,owner.athleteId,workoutId)
                        as? NativeJournalFileResult.Durable
                        ?:return@withOwnerTransaction WorkoutLastReferenceResult.Unavailable
                    val completed=history.timeline.completions.mapNotNull {entry->
                        (entry as? NativeKotlinMixedHistoryCompletion.Composer)?.let {
                            ComposerReferenceSession(it.manifest.completionOrder,
                                it.dayIndex,it.purpose,it.evidence)
                        }
                    }
                    WorkoutLastReferenceDomain.composer(current.journal.snapshot,completed)
                }
                if(localAthlete.signedInIdentity()!=owner)
                    WorkoutLastReferenceResult.Unavailable else result
            }
        }.getOrDefault(WorkoutLastReferenceResult.Unavailable)
    }

    /**
     * Owner-verified generated + custom MAIN working-set facts. It reads
     * actual journal outcomes, corrections and physical loads while holding
     * the same native history transaction fence. Still read-only: NO
     * automatic PR, progression, training change, or UI cutover.
     */
    /**
     * Owner-verified next-session per-set REP prescription candidate.
     * Only the latest corrected workout from the exhaustive native ledger
     * can inform it; never issues or silently replaces a saved set target.
     * Execute off the UI thread, then require an explicit revision-bound
     * acceptance path before any future on-device issuance.
     */
    fun reviewNextNativeSetTargets(
        exerciseId:String,
        readiness:CoachReadiness,
        movementRestricted:Boolean
    ):NativeOwnerNextSetResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeOwnerNextSetResult.Rejected(
                NativeOwnerNextSetRefusal.NOT_AUTHORIZED)
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                val assistance=availableCoachAssistance()
                if(assistance?.offersProgression!=true)
                    NativeOwnerNextSetResult.Rejected(
                        NativeOwnerNextSetRefusal.ASSISTANCE_DISABLED)
                else mixedHistoryReader.reviewCurrentNextSetTargets(
                    owner.ownerKey,owner.athleteId,approvalStore,exerciseId,
                    assistance.coachingReadiness(readiness),movementRestricted,
                    authoringSourceStore,
                    enableRepeatedFatigueReview=assistance.offersFatigueAdvice)
            }
        }.getOrElse {NativeOwnerNextSetResult.Rejected(
            NativeOwnerNextSetRefusal.STORAGE_UNAVAILABLE)}
    }

    /** Explicitly accept only the exact reverified preview; no issuance. */
    fun acceptReviewedNativeRepTargets(
        intent:NativeKotlinAcceptedRepIntent,
        readiness:CoachReadiness,
        movementRestricted:Boolean
    ):NativeKotlinRepAcceptanceResult {
        val signed=localAthlete.signedInIdentity()
            ?:return NativeKotlinRepAcceptanceResult.Rejected(
                NativeKotlinRepAcceptanceRefusal.NOT_AUTHORIZED)
        return runCatching {
            revisionFence.withOwnerTransaction(signed.ownerKey) {
                val assistance=availableCoachAssistance()
                if(assistance?.offersProgression!=true)
                    NativeKotlinRepAcceptanceResult.Rejected(
                        NativeKotlinRepAcceptanceRefusal.ASSISTANCE_DISABLED)
                else mixedHistoryReader.acceptReviewedNextSetTargets(
                    signed.ownerKey,signed.athleteId,approvalStore,intent,
                    assistance.coachingReadiness(readiness),movementRestricted,
                    authoringSourceStore,
                    enableRepeatedFatigueReview=assistance.offersFatigueAdvice)
            }
        }.getOrElse {NativeKotlinRepAcceptanceResult.Rejected(
            NativeKotlinRepAcceptanceRefusal.STORAGE_UNAVAILABLE)}
    }

    /**
     * Full-ledger, owner-authenticated training load review. Unlike the
     * earlier pure DTO bridge, neither workout pair nor approved plan is
     * supplied by the screen. This operation is READ ONLY and disk-bound.
     * A separate, authenticated athlete approval is required to save a
     * changed program and to issue any subsequent training targets.
     */
    /**
     * Read-only choices for the Kotlin Coach screen. UI cannot invent an
     * exercise to review: the current owner's accepted program, current gym
     * source and complete exercise identities are inspected together.
     *
     * Always call off the main thread. Repeated reads are safe; the actual
     * coaching review rechecks all source/history data under the owner fence.
     */
    fun currentVerifiedCoachExerciseChoices():List<String> {
        val owner=localAthlete.signedInIdentity() ?:return emptyList()
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                if(availableCoachAssistance()?.offersProgression!=true)
                    return@withOwnerTransaction emptyList<String>()
                val approved=approvalStore.currentOwnedApproval(owner.ownerKey)
                    ?.takeIf {it.athleteId==owner.athleteId}
                    ?:return@withOwnerTransaction emptyList<String>()
                val source=(authoringSourceStore.read(owner.ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction emptyList<String>()
                if(NativeKotlinApprovedGymCoherenceDomain.inspect(approved,source)
                    !=NativeApprovedGymCoherenceResult.Matched)
                    return@withOwnerTransaction emptyList<String>()
                approved.program.exercises.map {it.exerciseId}.distinct()
            }
        }.getOrDefault(emptyList())
    }

    /**
     * Explicit candidate athlete acceptance. Never trust UI-provided
     * workouts, plans or historical observations: the native coordinator
     * replays complete history and the approved plan under the owner fence.
     * Approval DOES NOT issue the newly approved workout.
     */
    fun acceptReviewedNativeLoad(
        intent:NativeKotlinCoachLoadAcceptanceIntent,
        maximumAuthorizedIncrease:java.math.BigDecimal,
        readiness:CoachReadiness,
        movementRestricted:Boolean
    ):NativeKotlinCoachAcceptanceResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeKotlinCoachAcceptanceResult.Rejected(
                NativeKotlinCoachAcceptanceRefusal.NOT_AUTHORIZED)
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                val assistance=availableCoachAssistance()
                if(assistance?.offersProgression!=true)
                    NativeKotlinCoachAcceptanceResult.Rejected(
                        NativeKotlinCoachAcceptanceRefusal.ASSISTANCE_DISABLED)
                else mixedHistoryReader.acceptCurrentApprovedLoad(
                    owner.ownerKey,owner.athleteId,approvalStore,intent,
                    maximumAuthorizedIncrease,
                    assistance.coachingReadiness(readiness),
                    movementRestricted,authoringSourceStore)
            }
        }.getOrElse {NativeKotlinCoachAcceptanceResult.Rejected(
            NativeKotlinCoachAcceptanceRefusal.STORAGE_UNAVAILABLE)}
    }

    fun reviewCurrentNativeLoad(
        exerciseId:String,
        maximumAuthorizedIncrease:java.math.BigDecimal,
        readiness:CoachReadiness,
        movementRestricted:Boolean
    ):NativeOwnerLoadReviewResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeOwnerLoadReviewResult.Rejected(
                NativeOwnerLoadReviewRefusal.NOT_AUTHORIZED)
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                val assistance=availableCoachAssistance()
                if(assistance?.offersProgression!=true)
                    NativeOwnerLoadReviewResult.Rejected(
                        NativeOwnerLoadReviewRefusal.ASSISTANCE_DISABLED)
                else mixedHistoryReader.reviewCurrentApprovedLoad(
                    owner.ownerKey,owner.athleteId,approvalStore,exerciseId,
                    maximumAuthorizedIncrease,
                    assistance.coachingReadiness(readiness),
                    movementRestricted,authoringSourceStore)
            }
        }.getOrElse {NativeOwnerLoadReviewResult.Rejected(
            NativeOwnerLoadReviewRefusal.STORAGE_UNAVAILABLE)}
    }

    fun reviewVerifiedKotlinWorkingSetFacts(): NativeKotlinMixedFactsReadResult {
        val owner = localAthlete.signedInIdentity()
            ?: return NativeKotlinMixedFactsReadResult.HistoryRejected(
                NativeKotlinMixedHistoryRefusal.NOT_AUTHORIZED)
        return mixedHistoryReader.readFacts(owner.ownerKey, owner.athleteId)
    }

    /**
     * Explicit R&D refresh of immutable 128-record history segments.
     * Only works after the real native history has been enrolled and committed.
     * Never treats a missing ledger or unproven web migration as empty history.
     * Disk-bound: call on an IO dispatcher, never the Compose main thread.
     */
    fun refreshCandidateSegmentedHistory(): NativeSegmentedHistoryResult {
        val signedIn=localAthlete.signedInIdentity()
            ?: return NativeSegmentedHistoryResult.Rejected(
                NativeSegmentedHistoryRefusal.NOT_AUTHORIZED)
        return segmentedHistoryShadow.sync(signedIn.ownerKey,signedIn.athleteId)
    }

    /**
     * Candidate history pagination only, not Last/PR/progression authority.
     * A stale source revision refuses instead of showing an earlier result.
     * Explicit refresh is separate: never rebuild a corrupt shadow on read.
     */
    fun readCandidateSegmentedHistoryPage(
        expectedSourceRevision:Long,
        offsetFromNewest:Int,
        pageSize:Int
    ): NativeSegmentedHistoryResult {
        val signedIn=localAthlete.signedInIdentity()
            ?: return NativeSegmentedHistoryResult.Rejected(
                NativeSegmentedHistoryRefusal.NOT_AUTHORIZED)
        return segmentedHistoryShadow.readPage(
            signedIn.ownerKey,signedIn.athleteId,expectedSourceRevision,
            offsetFromNewest,pageSize)
    }

    /**
     * Explicit, read-only migration readiness DIAGNOSTIC. The shadow must
     * match the entire v2 manifest AND every Composer/custom journal and
     * saved correction must replay under the same owner approval fence.
     * A successful review still cannot transfer write authority, publish
     * PRs, switch Compose navigation, or certify legacy JS import.
     * Run off the Android main thread.
     */
    fun reviewCandidateSegmentedHistoryHandoff():
        NativeSegmentedHistoryHandoffResult {
        val owner=localAthlete.signedInIdentity()
            ?: return NativeSegmentedHistoryHandoffResult.Rejected(
                NativeSegmentedHistoryHandoffRefusal.NOT_AUTHORIZED)
        return segmentedHistoryHandoff.review(owner.ownerKey,owner.athleteId)
    }

    /**
     * Separate custom cycle CONSENT, not an accepted ComposerRequest.
     * No static global 'approved' boolean; a full review and fingerprint
     * are recalculated under the owner lock on every acknowledgement.
     */
    fun confirmCustomCycleConsent(
        cycleId:String,expectedCycleRevision:Long,
        expectedSourceRevision:Long,expectedConsentRevision:Long,
        inputs:CustomCoachAuditInputs,athleteConfirmed:Boolean
    ):NativeCustomSignoffWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomSignoffWrite.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
        return customCycleSignoffs.confirm(
            owner.ownerKey,cycleId,expectedCycleRevision,
            expectedSourceRevision,expectedConsentRevision,
            inputs,athleteConfirmed)
    }

    /**
     * Candidate-only adaptive custom-cycle choice. The host derives the
     * identity from its authenticated local athlete; a Compose card cannot
     * furnish its own owner key or pre-verified source. These calls do disk
     * I/O and must stay off the main thread. They do not activate native
     * navigation, approve workouts, or accept prospective schedule profiles.
     */
    /**
     * Return only a matching saved draft's companion future review; it is
     * NOT permission to activate a profile or issue a workout.
     */
    /** Never an activation grant: checks accepted future draft and authenticated
     * completed custom workouts before an explicit block-boundary review.
     */
    fun inspectFutureBlockActivation(
        cycleId:String,blockId:String
    ):NativeFutureActivationReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureActivationReview.Rejected(
                NativeFutureActivationRefusal.NOT_AUTHORIZED)
        return futureActivationPreflight.inspect(owner.ownerKey,cycleId,blockId)
    }

    /**
     * SECOND explicit consent to a future boundary. This is neither an
     * active profile update nor a workout grant. All calls revalidate the
     * native history and both session inventories behind the owner fence.
     */
    fun previewFutureBlockActivationApproval(
        cycleId:String,blockId:String
    ):NativeFutureApprovalPreview {
        val signed=localAthlete.signedInIdentity()
            ?:return NativeFutureApprovalPreview.Rejected(
                NativeFutureApprovalRefusal.NOT_AUTHORIZED)
        return futureActivationApproval.preview(
            signed.ownerKey,cycleId,blockId)
    }
    fun confirmFutureBlockActivationApproval(
        cycleId:String,blockId:String,
        expectedRevision:Long,reviewedFingerprint:String,
        athleteConfirmed:Boolean
    ):NativeFutureApprovalWrite {
        val signed=localAthlete.signedInIdentity()
            ?:return NativeFutureApprovalWrite.Rejected(
                NativeFutureApprovalRefusal.NOT_AUTHORIZED)
        return futureActivationApproval.confirm(
            signed.ownerKey,cycleId,blockId,expectedRevision,
            reviewedFingerprint,athleteConfirmed)
    }
    fun readFutureBlockActivationApproval(
        cycleId:String,blockId:String
    ):NativeFutureApprovalRead {
        val signed=localAthlete.signedInIdentity()
            ?:return NativeFutureApprovalRead.Rejected(
                NativeFutureApprovalRefusal.NOT_AUTHORIZED)
        return futureActivationApproval.read(
            signed.ownerKey,cycleId,blockId)
    }
    fun revokeFutureBlockActivationApproval(
        cycleId:String,blockId:String,expectedRevision:Long
    ):NativeFutureApprovalWrite {
        val signed=localAthlete.signedInIdentity()
            ?:return NativeFutureApprovalWrite.Rejected(
                NativeFutureApprovalRefusal.NOT_AUTHORIZED)
        return futureActivationApproval.revoke(
            signed.ownerKey,cycleId,blockId,expectedRevision)
    }

    /**
     * Crash-safe handoff gate, not an active-profile switch. After staging,
     * BOTH native workout issuers refuse new sessions until the handoff is
     * explicitly cancelled with the original source unchanged, or a future
     * fully recoverable activation executor consumes it.
     */
    fun stageFutureBlockActivationHandoff(
        cycleId:String,blockId:String,approvalRevision:Long,
        fingerprint:String,athleteConfirmed:Boolean
    ):NativeFutureHandoffReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.NOT_AUTHORIZED)
        return futureActivationHandoff.stage(owner.ownerKey,cycleId,blockId,
            approvalRevision,fingerprint,futureActivationApproval,athleteConfirmed)
    }
    fun readFutureBlockActivationHandoff():NativeFutureHandoffReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.NOT_AUTHORIZED)
        return futureActivationHandoff.read(owner.ownerKey)
    }
    fun cancelFutureBlockActivationHandoff(
        approvalRevision:Long,fingerprint:String,athleteConfirmed:Boolean
    ):NativeFutureHandoffReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureHandoffReview.Rejected(
                NativeFutureHandoffRefusal.NOT_AUTHORIZED)
        return futureActivationHandoff.cancel(owner.ownerKey,approvalRevision,
            fingerprint,athleteConfirmed)
    }

    /**
     * Owner-fenced source CAS with write-ahead recovery. This intentionally
     * keeps the new-workout barrier until the new cycle's active policy and
     * issuance consent have been separately installed and audited.
     */
    fun commitFutureBlockActivation():NativeFutureCommitReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.NOT_AUTHORIZED)
        return futureActivationCommit.commit(owner.ownerKey)
    }
    fun readFutureBlockActivationCommit():NativeFutureCommitReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.NOT_AUTHORIZED)
        return futureActivationCommit.read(owner.ownerKey)
    }
    /**
     * Full-cycle coach audit after source activation. Completed historical
     * blocks use the preserved ORIGINAL athlete setup; only the accepted
     * target block uses its prospective policy and new schedule.
     * Review status alone never authorizes fresh workout issuance.
     */
    fun reviewActivatedFutureCycle(
        coachingInputs:CustomCoachAuditInputs
    ):NativeFutureActivatedCycleReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureActivatedCycleReview.Rejected(
                NativeFutureCommitRefusal.NOT_AUTHORIZED)
        return futureActivationCommit.reviewActivatedCycle(owner.ownerKey,
            coachingInputs)
    }

    /**
     * Complete active-future session preview with original historical
     * context, exact authored sets and selected new source. Still NOT an
     * issuance or permission to resume a previously unsigned workout.
     */
    fun previewActivatedFutureWorkout(
        coachingInputs:CustomCoachAuditInputs,absoluteWeek:Int,weekday:Int
    ):CustomWorkoutSnapshotResult {
        val owner=localAthlete.signedInIdentity()
            ?:return CustomWorkoutSnapshotResult.Rejected(
                CustomWorkoutSnapshotRefusal.COACHING_REVIEW_REQUIRED)
        return futureActivationCommit.previewActivatedWorkout(
            owner.ownerKey,coachingInputs,absoluteWeek,weekday)
    }

    /**
     * Separate, durable athlete review of the activated cycle. Signoff
     * remains non-executable until the scoped workout-issuance handoff is
     * verified and installed. Existing issued sessions are unaffected.
     */
    fun confirmActivatedFutureCycleSignoff(
        expectedRevision:Long,expectedFingerprint:String,
        inputs:CustomCoachAuditInputs,athleteConfirmed:Boolean
    ):NativeActivatedSignoffWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeActivatedSignoffWrite.Rejected(
                NativeActivatedSignoffRefusal.NOT_AUTHORIZED)
        return activatedCycleSignoffs.confirm(owner.ownerKey,expectedRevision,
            expectedFingerprint,inputs,athleteConfirmed)
    }

    fun readActivatedFutureCycleSignoff(
        inputs:CustomCoachAuditInputs?=null
    ):NativeActivatedSignoffRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeActivatedSignoffRead.Rejected(
                NativeActivatedSignoffRefusal.NOT_AUTHORIZED)
        return activatedCycleSignoffs.read(owner.ownerKey,inputs)
    }

    fun revokeActivatedFutureCycleSignoff(
        expectedRevision:Long
    ):NativeActivatedSignoffWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeActivatedSignoffWrite.Rejected(
                NativeActivatedSignoffRefusal.NOT_AUTHORIZED)
        return activatedCycleSignoffs.revoke(owner.ownerKey,expectedRevision)
    }

    /**
     * Immutable future-block workout preparation under the NEW activated
     * signoff, not the legacy custom-cycle grant. It preserves exact authored
     * slots/sets and remains deliberately non-issued and non-loggable.
     */
    /**
     * READ-ONLY next-workout coaching after actual custom Finish. Authenticated
     * full mixed history + current future-block consent are rechecked together.
     * Upper/Push or different custom weekdays NEVER share progression state.
     */
    fun reviewNextActivatedWorkoutProgression(
        nextAbsoluteWeek:Int,nextWeekday:Int,slotId:String,
        inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        movementRestricted:Boolean,maximumAuthorizedIncrease:java.math.BigDecimal,
        repForecastPolicy:CustomRepForecastPolicy=CustomRepForecastPolicy.LEGACY_STEP_V1
    ):NativeCustomNextCoachResult {
        val signed=localAthlete.signedInIdentity()
            ?:return NativeCustomNextCoachResult.Rejected(
                NativeCustomNextCoachRefusal.NOT_AUTHORIZED)
        return customNextWorkoutCoach.review(signed.ownerKey,inputs,
            nextAbsoluteWeek,nextWeekday,slotId,readiness,movementRestricted,
            maximumAuthorizedIncrease,repForecastPolicy)
    }

    /** Same native evidence pipeline for an ordinary signed authored cycle. */
    fun reviewNextAuthoredWorkoutProgression(
        cycleId:String,nextAbsoluteWeek:Int,nextWeekday:Int,slotId:String,
        inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        movementRestricted:Boolean,maximumAuthorizedIncrease:java.math.BigDecimal,
        repForecastPolicy:CustomRepForecastPolicy=CustomRepForecastPolicy.LEGACY_STEP_V1
    ):NativeCustomNextCoachResult {
        val signed=localAthlete.signedInIdentity()
            ?:return NativeCustomNextCoachResult.Rejected(
                NativeCustomNextCoachRefusal.NOT_AUTHORIZED)
        return customNextWorkoutCoach.reviewAuthored(signed.ownerKey,cycleId,
            inputs,nextAbsoluteWeek,nextWeekday,slotId,readiness,
            movementRestricted,maximumAuthorizedIncrease,repForecastPolicy)
    }

    fun previewBlockCalibration(
        workoutId:String,cycleId:String,week:Int,weekday:Int,slotId:String,
        inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        restricted:Boolean,maximumIncrease:java.math.BigDecimal,
        kind:NativeBlockLoadConsentKind=NativeBlockLoadConsentKind.CONSERVATIVE_REDUCTION
    ):NativeBlockCalibrationPreview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeBlockCalibrationPreview.Rejected(NativeCalibrationRefusal.NOT_AUTHORIZED)
        return blockCalibrations.preview(owner.ownerKey,workoutId,cycleId,inputs,
            week,weekday,slotId,readiness,restricted,maximumIncrease,kind)
    }

    fun previewAuthoredLoadReturn(workoutId:String,cycleId:String,week:Int,
        weekday:Int,inputs:CustomCoachAuditInputs):NativeAuthoredLoadReturnPreview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeAuthoredLoadReturnPreview.Rejected(NativeCalibrationRefusal.NOT_AUTHORIZED)
        return blockCalibrations.previewAuthoredReturn(owner.ownerKey,workoutId,cycleId,inputs,week,weekday)
    }

    fun acceptAuthoredLoadReturn(shown:NativeAuthoredLoadReturnPreview.Ready,
        inputs:CustomCoachAuditInputs,confirmed:Boolean):NativeCustomPrepareResult {
        if(localAthlete.signedInIdentity()!=NativeKotlinOwnerIdentity(
                shown.ownerKey,shown.authored.stamp.athleteId))
            return NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.NOT_AUTHORIZED)
        return customPreparedStore.prepareAuthoredReturn(shown,inputs,confirmed,blockCalibrations)
    }

    fun pendingBlockCalibrations(cycleId:String):NativePendingCalibrationRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativePendingCalibrationRead.Rejected(NativeCalibrationRefusal.NOT_AUTHORIZED)
        return blockCalibrations.pendingPreparations(owner.ownerKey,cycleId)
    }

    fun recheckBlockCalibration(receipt:NativeBlockCalibrationReceipt,
        inputs:CustomCoachAuditInputs):NativeBlockCalibrationRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeBlockCalibrationRead.Rejected(NativeCalibrationRefusal.NOT_AUTHORIZED)
        if(owner.ownerKey!=receipt.ownerKey||owner.athleteId!=receipt.athleteId)
            return NativeBlockCalibrationRead.Rejected(NativeCalibrationRefusal.NOT_AUTHORIZED)
        return blockCalibrations.readCurrent(owner.ownerKey,receipt.workoutId,receipt.cycleId,
            receipt.absoluteWeek,receipt.weekday,inputs)
    }

    fun acceptBlockCalibration(receipt:NativeBlockCalibrationReceipt,
        inputs:CustomCoachAuditInputs,confirmed:Boolean):NativeBlockCalibrationWrite {
        if(localAthlete.signedInIdentity()!=NativeKotlinOwnerIdentity(receipt.ownerKey,receipt.athleteId))
            return NativeBlockCalibrationWrite.Rejected(NativeCalibrationRefusal.NOT_AUTHORIZED)
        return blockCalibrations.accept(receipt,inputs,confirmed)
    }

    fun prepareAcceptedBlockCalibration(receipt:NativeBlockCalibrationReceipt,
        inputs:CustomCoachAuditInputs):NativeCustomPrepareResult {
        if(localAthlete.signedInIdentity()!=NativeKotlinOwnerIdentity(receipt.ownerKey,receipt.athleteId))
            return NativeCustomPrepareResult.Rejected(NativeCustomPrepareRefusal.NOT_AUTHORIZED)
        return customPreparedStore.prepare(receipt.ownerKey,receipt.workoutId,receipt.cycleId,
            receipt.absoluteWeek,receipt.weekday,inputs,blockCalibrations,receipt.fingerprint)
    }

    fun previewResetBlockCalibration(workoutId:String,cycleId:String,week:Int,
        weekday:Int,inputs:CustomCoachAuditInputs):NativePreparedManualResetPreview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativePreparedManualResetPreview.Rejected(NativePreparedManualResetRefusal.NOT_AUTHORIZED)
        return customPreparedStore.previewManualReset(owner.ownerKey,workoutId,cycleId,week,
            weekday,inputs,null,null,customIssuedStore,coachSettings)
    }

    fun acceptResetBlockCalibration(workoutId:String,cycleId:String,week:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,preparedFingerprint:String,settingsRevision:Long,
        confirmed:Boolean):NativePreparedManualResetWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativePreparedManualResetWrite.Rejected(NativePreparedManualResetRefusal.NOT_AUTHORIZED)
        return customPreparedStore.acceptManualReset(owner.ownerKey,workoutId,cycleId,week,
            weekday,inputs,null,null,customIssuedStore,coachSettings,preparedFingerprint,
            settingsRevision,confirmed)
    }

    /**
     * Complete on-disk, read-only FUTURE WEEK load proposal. The native owner
     * re-reads the real finished history, permission revision, assistance
     * settings, approved draft and outstanding prepared/issued sessions.
     * This produces a reviewed candidate only, NEVER a saved workout.
     */
    fun previewFutureCycleWeekLoadOption(
        absoluteWeek:Int,weekday:Int,slotId:String,
        inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        movementRestricted:Boolean,maximumAuthorizedIncrease:java.math.BigDecimal
    ):NativeFutureWeekLoadOptionResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureWeekLoadOptionResult.Rejected(
                NativeFutureWeekLoadRefusal.NOT_AUTHORIZED)
        return futureWeekLoadOptions.review(owner.ownerKey,inputs,
            absoluteWeek,weekday,slotId,readiness,movementRestricted,
            maximumAuthorizedIncrease)
    }

    /**
     * Distinct consent for one earned future WEEK load. Acceptance always
     * repeats the mixed-history, equipment and full-cycle audit inside the
     * owner fence; nothing here edits the draft or issues a workout.
     */
    fun previewFutureWeekLoadConsent(
        cycleId:String,blockId:String,absoluteWeek:Int,weekday:Int,
        slotId:String,inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        movementRestricted:Boolean,maximumAuthorizedIncrease:java.math.BigDecimal
    ):NativeFutureWeekConsentPreview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureWeekConsentPreview.Rejected(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
        return acceptedFutureWeekLoads.preview(owner.ownerKey,cycleId,blockId,
            inputs,absoluteWeek,weekday,slotId,readiness,movementRestricted,
            maximumAuthorizedIncrease)
    }

    fun acceptFutureWeekLoadConsent(
        cycleId:String,blockId:String,absoluteWeek:Int,weekday:Int,
        slotId:String,inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        movementRestricted:Boolean,maximumAuthorizedIncrease:java.math.BigDecimal,
        expectedFingerprint:String,expectedRecordRevision:Long,
        athleteConfirmed:Boolean
    ):NativeFutureWeekConsentWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureWeekConsentWrite.Rejected(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
        return acceptedFutureWeekLoads.accept(owner.ownerKey,cycleId,blockId,
            inputs,absoluteWeek,weekday,slotId,readiness,movementRestricted,
            maximumAuthorizedIncrease,expectedFingerprint,
            expectedRecordRevision,athleteConfirmed)
    }

    fun readCurrentFutureWeekLoadConsent(
        cycleId:String,blockId:String,absoluteWeek:Int,weekday:Int,
        slotId:String,inputs:CustomCoachAuditInputs
    ):NativeFutureWeekConsentRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureWeekConsentRead.Rejected(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
        return acceptedFutureWeekLoads.readCurrent(owner.ownerKey,cycleId,
            blockId,inputs,absoluteWeek,weekday,slotId)
    }

    fun revokeFutureWeekLoadConsent(
        cycleId:String,blockId:String,absoluteWeek:Int,weekday:Int,
        slotId:String,expectedRecordRevision:Long
    ):NativeFutureWeekConsentWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureWeekConsentWrite.Rejected(
                NativeFutureWeekConsentRefusal.NOT_AUTHORIZED)
        return acceptedFutureWeekLoads.revoke(owner.ownerKey,cycleId,blockId,
            absoluteWeek,weekday,slotId,expectedRecordRevision)
    }

    fun previewAcceptedFutureWeekLoad(
        cycleId:String,blockId:String,absoluteWeek:Int,weekday:Int,
        slotId:String,inputs:CustomCoachAuditInputs
    ):NativeAcceptedFutureWeekLoadHandoff {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeAcceptedFutureWeekLoadHandoff.Rejected(
                NativeAcceptedFutureWeekLoadRefusal.NOT_AUTHORIZED)
        return futureWeekLoadHandoff.preview(owner.ownerKey,cycleId,blockId,
            inputs,absoluteWeek,weekday,slotId)
    }

    /** View exactly reviewed future-set minima without changing the program. */
    /** Physically verified next-load review; no program mutation. */
    fun previewNextActivatedLoadAmendment(
        workoutId:String,nextAbsoluteWeek:Int,nextWeekday:Int,slotId:String,
        inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        movementRestricted:Boolean,maximumAuthorizedIncrease:java.math.BigDecimal
    ):NativeCustomLoadPreview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomLoadPreview.Rejected(
                NativeCustomLoadRefusal.NOT_AUTHORIZED)
        return acceptedFutureLoads.preview(owner.ownerKey,workoutId,inputs,
            nextAbsoluteWeek,nextWeekday,slotId,readiness,movementRestricted,
            maximumAuthorizedIncrease)
    }

    /** Approve precisely the offered increment, source and full-history state. */
    fun approveNextActivatedLoadAmendment(
        workoutId:String,nextAbsoluteWeek:Int,nextWeekday:Int,slotId:String,
        inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        movementRestricted:Boolean,maximumAuthorizedIncrease:java.math.BigDecimal,
        expectedFingerprint:String,expectedHistoryRevision:Long,
        expectedConsentRevision:Long,shownSetKeys:List<String>,
        shownOriginalLoad:java.math.BigDecimal,
        shownProposedLoad:java.math.BigDecimal,athleteConfirmed:Boolean
    ):NativeCustomLoadWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomLoadWrite.Rejected(
                NativeCustomLoadRefusal.NOT_AUTHORIZED)
        return acceptedFutureLoads.accept(owner.ownerKey,workoutId,inputs,
            nextAbsoluteWeek,nextWeekday,slotId,readiness,movementRestricted,
            maximumAuthorizedIncrease,expectedFingerprint,
            expectedHistoryRevision,expectedConsentRevision,shownSetKeys,
            shownOriginalLoad,shownProposedLoad,athleteConfirmed)
    }

    fun previewNextActivatedRepAmendment(
        workoutId:String,nextAbsoluteWeek:Int,nextWeekday:Int,slotId:String,
        inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        movementRestricted:Boolean,maximumAuthorizedIncrease:java.math.BigDecimal,
        repForecastPolicy:CustomRepForecastPolicy=CustomRepForecastPolicy.LEGACY_STEP_V1
    ):NativeCustomNextRepPreview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomNextRepPreview.Rejected(
                NativeCustomNextRepRefusal.NOT_AUTHORIZED)
        return acceptedFutureRepTargets.preview(owner.ownerKey,workoutId,inputs,
            nextAbsoluteWeek,nextWeekday,slotId,readiness,movementRestricted,
            maximumAuthorizedIncrease,repForecastPolicy)
    }

    /**
     * Persist ONLY the exact displayed candidate with explicit consent.
     * Re-derives whole native history and approved cycle under one owner fence;
     * no already-prepared/issued workout can be changed retroactively.
     */
    fun approveNextActivatedRepAmendment(
        workoutId:String,nextAbsoluteWeek:Int,nextWeekday:Int,slotId:String,
        inputs:CustomCoachAuditInputs,readiness:CoachReadiness,
        movementRestricted:Boolean,maximumAuthorizedIncrease:java.math.BigDecimal,
        expectedFingerprint:String,expectedHistoryRevision:Long,
        expectedConsentRevision:Long,shownSetKeys:List<String>,
        shownNextMinimumReps:List<Int>,athleteConfirmed:Boolean,
        repForecastPolicy:CustomRepForecastPolicy=CustomRepForecastPolicy.LEGACY_STEP_V1
    ):NativeCustomNextRepWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomNextRepWrite.Rejected(
                NativeCustomNextRepRefusal.NOT_AUTHORIZED)
        return acceptedFutureRepTargets.accept(owner.ownerKey,workoutId,inputs,
            nextAbsoluteWeek,nextWeekday,slotId,readiness,movementRestricted,
            maximumAuthorizedIncrease,expectedFingerprint,
            expectedHistoryRevision,expectedConsentRevision,shownSetKeys,
            shownNextMinimumReps,athleteConfirmed,repForecastPolicy)
    }

    /**
     * Display an EXACT, original authored workout for explicit athlete
     * reset of a prepared but NOT issued rep/load coaching amendment.
     * A reset never edits a started workout or a completed training record.
     */
    fun previewResetPreparedFutureWorkout(
        workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs
    ):NativePreparedManualResetPreview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativePreparedManualResetPreview.Rejected(
                NativePreparedManualResetRefusal.NOT_AUTHORIZED)
        return customPreparedStore.previewManualReset(owner.ownerKey,workoutId,
            cycleId,absoluteWeek,weekday,inputs,activatedCycleSignoffs,
            futureActivationCommit,customIssuedStore,coachSettings)
    }

    /**
     * Atomic, revision-checked and non-replayable manual choice. A saved
     * old coach approval cannot resurrect after the athlete resets targets.
     */
    fun acceptResetPreparedFutureWorkout(
        workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,expectedPreparedFingerprint:String,
        expectedSettingsRevision:Long,athleteConfirmed:Boolean
    ):NativePreparedManualResetWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativePreparedManualResetWrite.Rejected(
                NativePreparedManualResetRefusal.NOT_AUTHORIZED)
        return customPreparedStore.acceptManualReset(owner.ownerKey,workoutId,
            cycleId,absoluteWeek,weekday,inputs,activatedCycleSignoffs,
            futureActivationCommit,customIssuedStore,coachSettings,
            expectedPreparedFingerprint,expectedSettingsRevision,
            athleteConfirmed)
    }

    fun prepareActivatedFutureWorkout(
        workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs,
        acceptedFutureWeekSlotId:String?=null
    ):NativeCustomPrepareResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomPrepareResult.Rejected(
                NativeCustomPrepareRefusal.NOT_AUTHORIZED)
        return customPreparedStore.prepareActivated(owner.ownerKey,workoutId,
            cycleId,absoluteWeek,weekday,inputs,activatedCycleSignoffs,
            futureActivationCommit,acceptedFutureRepTargets,acceptedFutureLoads,
            futureWeekLoadHandoff,acceptedFutureWeekSlotId)
    }

    /**
     * REAL Kotlin custom journal issuance for the activated block only.
     * The owner-fenced issuer verifies the new signoff, exact immutable
     * preparation, source, original history enrollment and ID collision.
     * Other issuers remain behind the staged activation barrier.
     */
    fun issueActivatedFutureWorkout(
        workoutId:String,cycleId:String,absoluteWeek:Int,weekday:Int,
        inputs:CustomCoachAuditInputs
    ):NativeCustomJournalResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_AUTHORIZED)
        return customIssuedStore.issueActivated(owner.ownerKey,workoutId,
            cycleId,absoluteWeek,weekday,inputs,activatedCycleSignoffs,
            futureActivationCommit,acceptedFutureRepTargets,acceptedFutureLoads,
            futureWeekLoadHandoff)
    }

    fun abortFutureBlockActivationBeforeSourceChange(
        approvalRevision:Long,fingerprint:String,athleteConfirmed:Boolean
    ):NativeFutureCommitReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureCommitReview.Rejected(
                NativeFutureCommitRefusal.NOT_AUTHORIZED)
        return futureActivationCommit.abortBeforeSourceApplied(
            owner.ownerKey,approvalRevision,fingerprint,athleteConfirmed)
    }

    fun readAcceptedFutureBlockContext(
        cycleId:String,blockId:String
    ):NativeFutureBlockContextRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeFutureBlockContextRead.Rejected(
                NativeFutureBlockContextRefusal.NOT_AUTHORIZED)
        return futureBlockContexts.read(owner.ownerKey,cycleId,blockId)
    }

    fun readAdaptiveCyclePermissions(cycleId:String):NativeAdaptivePermissionRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeAdaptivePermissionRead.Rejected(
                NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
        return adaptiveCyclePermissions.read(owner.ownerKey,cycleId)
    }

    fun confirmAdaptiveCyclePermissions(
        cycleId:String,expectedPermissionRevision:Long,
        allowedKinds:Set<CustomCycleChangeKind>,athleteConfirmed:Boolean
    ):NativeAdaptivePermissionWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeAdaptivePermissionWrite.Rejected(
                NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
        return adaptiveCyclePermissions.confirm(
            owner.ownerKey,cycleId,expectedPermissionRevision,
            allowedKinds,athleteConfirmed)
    }

    fun revokeAdaptiveCyclePermissions(
        cycleId:String,expectedPermissionRevision:Long
    ):NativeAdaptivePermissionWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeAdaptivePermissionWrite.Rejected(
                NativeAdaptivePermissionRefusal.NOT_AUTHORIZED)
        return adaptiveCyclePermissions.revoke(
            owner.ownerKey,cycleId,expectedPermissionRevision)
    }

    /**
     * This authenticated candidate host is still NOT active navigation.
     * Calls are off-main-thread. No caller supplies an owner or grant.
     */
    private fun adaptiveChoice(
        cycleId:String,targetBlockId:String,optionId:String,
        expectedDraftRevision:Long,expectedSourceRevision:Long,
        proposedCycle:CustomTrainingCycle,inputs:CustomCoachAuditInputs,
        permissions:CustomCycleCoachPermissions,
        expectedPermissionRevision:Long,
        prospective:ProspectiveBlockCoachingContext?=null
    ):NativeAdaptiveCycleDraftCandidate? {
        val owner=localAthlete.signedInIdentity() ?:return null
        if(proposedCycle.athleteId!=owner.athleteId)return null
        return NativeAdaptiveCycleDraftCandidate(
            owner.ownerKey,cycleId,targetBlockId,optionId,
            expectedDraftRevision,expectedSourceRevision,proposedCycle,
            inputs,permissions,expectedPermissionRevision,prospective)
    }

    fun previewReviewedAdaptiveCycleDraft(
        cycleId:String,targetBlockId:String,optionId:String,
        expectedDraftRevision:Long,expectedSourceRevision:Long,
        proposedCycle:CustomTrainingCycle,inputs:CustomCoachAuditInputs,
        permissions:CustomCycleCoachPermissions,
        expectedPermissionRevision:Long,
        prospective:ProspectiveBlockCoachingContext?=null
    ):NativeAdaptiveCycleDraftPreview {
        val choice=adaptiveChoice(cycleId,targetBlockId,optionId,
            expectedDraftRevision,expectedSourceRevision,proposedCycle,
            inputs,permissions,expectedPermissionRevision,prospective)
            ?:return NativeAdaptiveCycleDraftPreview.Rejected(
                NativeAdaptiveCycleDraftRefusal.NOT_AUTHORIZED)
        return runCatching {
            revisionFence.withOwnerTransaction(choice.ownerKey) {
                if(availableCoachAssistance()?.coachingSuggestionsEnabled!=true)
                    NativeAdaptiveCycleDraftPreview.Rejected(
                        NativeAdaptiveCycleDraftRefusal.ASSISTANCE_DISABLED)
                else adaptiveCycleDrafts.preview(choice)
            }
        }.getOrElse {NativeAdaptiveCycleDraftPreview.Rejected(
            NativeAdaptiveCycleDraftRefusal.STORAGE_UNAVAILABLE)}
    }

    fun acceptReviewedAdaptiveCycleDraft(
        cycleId:String,targetBlockId:String,optionId:String,
        expectedDraftRevision:Long,expectedSourceRevision:Long,
        proposedCycle:CustomTrainingCycle,inputs:CustomCoachAuditInputs,
        permissions:CustomCycleCoachPermissions,
        expectedPermissionRevision:Long,
        reviewedFingerprint:String,athleteConfirmed:Boolean,
        prospective:ProspectiveBlockCoachingContext?=null
    ):NativeAdaptiveCycleDraftDecision {
        val choice=adaptiveChoice(cycleId,targetBlockId,optionId,
            expectedDraftRevision,expectedSourceRevision,proposedCycle,
            inputs,permissions,expectedPermissionRevision,prospective)
            ?:return NativeAdaptiveCycleDraftDecision.Rejected(
                NativeAdaptiveCycleDraftRefusal.NOT_AUTHORIZED)
        return runCatching {
            revisionFence.withOwnerTransaction(choice.ownerKey) {
                if(availableCoachAssistance()?.coachingSuggestionsEnabled!=true)
                    NativeAdaptiveCycleDraftDecision.Rejected(
                        NativeAdaptiveCycleDraftRefusal.ASSISTANCE_DISABLED)
                else adaptiveCycleDrafts.accept(choice,
                    reviewedFingerprint,athleteConfirmed)
            }
        }.getOrElse {NativeAdaptiveCycleDraftDecision.Rejected(
            NativeAdaptiveCycleDraftRefusal.STORAGE_UNAVAILABLE)}
    }

    fun readCustomCycleConsent(
        cycleId:String,inputs:CustomCoachAuditInputs?=null
    ):NativeCustomSignoffRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomSignoffRead.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
        return customCycleSignoffs.read(owner.ownerKey,cycleId,inputs)
    }

    fun recoverSavedCustomCycleReview(cycleId:String):NativeCustomSavedCoachReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomSavedCoachReview.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
        return customCycleSignoffs.recoverReviewedInputs(owner.ownerKey,cycleId)
    }

    fun revokeCustomCycleConsent(
        cycleId:String,expectedConsentRevision:Long
    ):NativeCustomSignoffWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomSignoffWrite.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
        return customCycleSignoffs.revoke(
            owner.ownerKey,cycleId,expectedConsentRevision)
    }

    /** Read-only, owner-fenced program discovery for the native picker. */
    fun listOwnedCustomCycleDrafts():NativeCustomDraftListResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomDraftListResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED)
        return customDraftStore.listOwned(owner.ownerKey)
    }

    fun readCustomAuthoringSource():NativeAuthoringSourceRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeAuthoringSourceRead.Rejected(
                NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
        return authoringSourceStore.read(owner.ownerKey)
    }

    fun publishCustomAuthoringSource(
        setupDraft:AthleteSetupDraft,
        curatedCatalog:List<CatalogMovement>,
        expectedRevision:Long
    ):NativeAuthoringSourceWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
        val reviewed=CoachAthleteSetupDomain.review(setupDraft)
        if (reviewed.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            reviewed.reviewed?.athleteProfileId!=owner.athleteId)
            return NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.INVALID_SOURCE)
        val initial=NativeCustomAuthoringSnapshot(
            owner.ownerKey,owner.athleteId,expectedRevision+1,
            reviewed,curatedCatalog)
        // Never publish a metadata-only legacy exercise list as an
        // author-reviewed movement catalog with fabricated muscle credits.
        if(NativeKotlinGymCatalogEditingDomain.replaceCuratedCatalog(
                initial,initial.revision,curatedCatalog)
            !is NativeGymCatalogEdit.Proposed)
            return NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.INVALID_SOURCE)
        return authoringSourceStore.publish(
            owner.ownerKey,expectedRevision,initial)
    }

    /**
     * The native gym editor changes exactly one inventory fact at a time.
     * Read + source CAS together prevent silently overwriting another edit.
     * An invalid athlete, gym or catalog proposal never touches the disk.
     */
    private fun editCustomAuthoringSource(
        expectedRevision:Long,
        operation:(NativeCustomAuthoringSnapshot)->NativeGymCatalogEdit
    ):NativeAuthoringSourceWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
        if(expectedRevision < 1)
            return NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.STALE_REVISION)
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                if(localAthlete.signedInIdentity()!=owner)
                    return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
                val existing=authoringSourceStore.read(owner.ownerKey)
                val current=(existing as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        if(existing is NativeAuthoringSourceRead.Rejected &&
                            existing.reason==NativeAuthoringSourceRefusal.CORRUPT_RECORD)
                            NativeAuthoringSourceRefusal.CORRUPT_RECORD
                        else NativeAuthoringSourceRefusal.STALE_REVISION)
                if(current.revision!=expectedRevision ||
                    current.athleteId!=owner.athleteId)
                    return@withOwnerTransaction NativeAuthoringSourceWrite.Rejected(
                        NativeAuthoringSourceRefusal.STALE_REVISION)
                when(val edited=operation(current)) {
                    is NativeGymCatalogEdit.Rejected ->
                        NativeAuthoringSourceWrite.Rejected(
                            NativeAuthoringSourceRefusal.INVALID_SOURCE)
                    is NativeGymCatalogEdit.Proposed ->
                        authoringSourceStore.publish(
                            owner.ownerKey,expectedRevision,edited.source)
                }
            }
        }.getOrElse {
            NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.STORAGE_UNAVAILABLE)
        }
    }

    fun upsertNativeGymStock(
        expectedRevision:Long,equipmentId:String,stock:VerifiedLoadStock
    ):NativeAuthoringSourceWrite = editCustomAuthoringSource(expectedRevision) {
        NativeKotlinGymCatalogEditingDomain.upsertStock(
            it,expectedRevision,equipmentId,stock)
    }

    fun removeNativeGymStock(
        expectedRevision:Long,equipmentId:String
    ):NativeAuthoringSourceWrite = editCustomAuthoringSource(expectedRevision) {
        NativeKotlinGymCatalogEditingDomain.removeStock(
            it,expectedRevision,equipmentId)
    }

    fun updateNativeGymFixtures(
        expectedRevision:Long,facilities:Set<String>,attachments:Set<String>
    ):NativeAuthoringSourceWrite = editCustomAuthoringSource(expectedRevision) {
        NativeKotlinGymCatalogEditingDomain.setFacilitiesAndAttachments(
            it,expectedRevision,facilities,attachments)
    }

    /** Catalog metadata requires a human-reviewed provisional credit source. */
    fun publishNativeCuratedCatalog(
        expectedRevision:Long,catalog:List<CatalogMovement>
    ):NativeAuthoringSourceWrite = editCustomAuthoringSource(expectedRevision) {
        NativeKotlinGymCatalogEditingDomain.replaceCuratedCatalog(
            it,expectedRevision,catalog)
    }

    fun revokeCustomAuthoringSource(
        expectedRevision:Long
    ):NativeAuthoringSourceWrite {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeAuthoringSourceWrite.Rejected(
                NativeAuthoringSourceRefusal.NOT_AUTHORIZED)
        return authoringSourceStore.revoke(owner.ownerKey,expectedRevision)
    }

    /**
     * Save one new authored cycle revision only if the SAME reviewed gym and
     * catalog source revision remains on disk under the owner issuance fence.
     * The outer owner lock spans source re-read and draft CAS commit.
     */
    fun saveReviewedCustomCycleDraft(
        draft:CustomTrainingCycle,
        expectedDraftRevision:Long,
        expectedSourceRevision:Long
    ):NativeCustomDraftWriteResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED)
        return customAuthoringCoordinator.saveVerifiedExerciseEdit(
            owner.ownerKey,draft,expectedDraftRevision,expectedSourceRevision)
    }

    /**
     * Athlete-owned custom-program drafts: save/reopen/erase independent of
     * workout approval. This is a candidate native API, NOT yet called by
     * the production Compose screens. No draft is an issuance grant.
     * The store re-authenticates the owner and enforces revision CAS.
     */
    /**
     * Explicit create-only first-program DRAFT handoff, not approval.
     * Replays the entire candidate from current owner-verified setup and gym
     * under a revision fence. Refuses any difference from the shown review.
     * A chosen cycle ID can never silently overwrite an older custom cycle.
     */
    fun saveReviewedFirstProgramDraft(
        input:FirstProgramStartRequest,
        shown:FirstProgramStartReview,
        expectedSourceRevision:Long,
        cycleId:String,
        title:String,
        weeks:Int,
        confirmed:Boolean
    ):NativeCustomDraftWriteResult {
        if(!confirmed)return NativeCustomDraftWriteResult.Rejected(
            NativeCustomDraftRefusal.INVALID_DRAFT)
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED)
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                if(localAthlete.signedInIdentity()!=owner)
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.NOT_AUTHORIZED)
                val source=(authoringSourceStore.read(owner.ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.STALE_REVISION)
                if(source.revision!=expectedSourceRevision ||
                    source.athleteId!=owner.athleteId ||
                    input.setup!=source.setup || input.catalog!=source.catalog)
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.STALE_REVISION)
                val replay=runCatching {
                    CoachFirstProgramStartDomain.review(input.copy(
                        setup=source.setup,catalog=source.catalog))
                }.getOrNull() ?:return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                    NativeCustomDraftRefusal.INVALID_DRAFT)
                if(replay!=shown ||
                    replay.status!=FirstProgramStartStatus.PRESCRIBED_DRAFT_FOR_REVIEW)
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.STALE_REVISION)
                val candidate=CoachFirstProgramDraftConversionDomain.propose(
                    replay,source.setup,source.catalog,cycleId,title,weeks)
                val cycle=candidate.cycle?.takeIf {
                    candidate.status==FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT
                } ?:return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                    NativeCustomDraftRefusal.INVALID_DRAFT)
                val seed=NativeFirstProgramSeed(cycleId,title,weeks,
                    source.revision,input.policyVersion,
                    input.phase,input.startingLoads,
                    input.selectedBodyweightExerciseIds,input.extraFocuses,
                    input.nonSetSecondsPerDay)
                // Write immutable calibration FIRST under the same owner
                // lock. If interrupted, an orphan cannot issue or be listed
                // as a program; the draft only appears after this succeeds.
                val stored=firstProgramSeedStore.create(owner.ownerKey,seed)
                if(stored !is NativeFirstProgramSeedWrite.Committed)
                    return@withOwnerTransaction NativeCustomDraftWriteResult.Rejected(
                        NativeCustomDraftRefusal.STORAGE_UNAVAILABLE)
                customAuthoringCoordinator.saveVerifiedExerciseEdit(
                    owner.ownerKey,cycle,expectedDraftRevision=0,
                    expectedSourceRevision=expectedSourceRevision)
            }
        }.getOrElse {
            NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Cold-recover a first-program's original generated coaching assumptions
     * only if the CURRENT saved athlete, gym and EXACT untouched cycle
     * reproduce the same finished draft. A missing sidecar marks a normal
     * custom program, not a generated program. Never grants signoff.
     */
    fun recoverSavedFirstProgramReview(
        cycleId:String
    ):NativeRecoveredFirstProgramReview {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeRecoveredFirstProgramReview.Rejected(
                "The native athlete is not active.")
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                val stored=firstProgramSeedStore.read(owner.ownerKey,cycleId)
                if(stored is NativeFirstProgramSeedRead.Missing)
                    return@withOwnerTransaction NativeRecoveredFirstProgramReview.NotGenerated
                val seed=(stored as? NativeFirstProgramSeedRead.Found)?.seed
                    ?:return@withOwnerTransaction NativeRecoveredFirstProgramReview.Rejected(
                        "Saved first-program coaching inputs cannot be verified.")
                val source=(authoringSourceStore.read(owner.ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                val draft=(customDraftStore.read(owner.ownerKey,cycleId)
                    as? NativeCustomDraftReadResult.Found)?.draft
                if(source==null || draft==null ||
                    source.athleteId!=owner.athleteId ||
                    draft.athleteId!=owner.athleteId ||
                    source.revision!=seed.sourceRevision ||
                    draft.revision!=1L ||
                    !CoachFirstProgramStartDomain.supportsPolicy(seed.policyVersion))
                    return@withOwnerTransaction NativeRecoveredFirstProgramReview.Rejected(
                        "The saved gym, program revision or generator version changed. Review the program manually.")
                val replay=runCatching {
                    CoachFirstProgramStartDomain.review(
                        FirstProgramStartRequest(
                            source.setup,source.catalog,seed.startingLoads,
                            seed.bodyweightExercises,seed.phase,
                            seed.extraFocuses,seed.nonSetSeconds,seed.policyVersion))
                }.getOrNull()
                val candidate=replay?.let {
                    CoachFirstProgramDraftConversionDomain.propose(
                        it,source.setup,source.catalog,cycleId,seed.title,
                        seed.blockWeeks)
                }
                if(candidate?.status!=FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT ||
                    candidate.cycle!=draft)
                    return@withOwnerTransaction NativeRecoveredFirstProgramReview.Rejected(
                        "Saved training draft differs from its original review. Nothing was approved.")
                val inputs=CoachFirstProgramDraftConversionDomain
                    .policyForInitialFullCycleReview(
                        checkNotNull(replay),source.setup)
                    ?:return@withOwnerTransaction NativeRecoveredFirstProgramReview.Rejected(
                        "Original generated coaching policy is not available.")
                val audit=CustomCycleCoachAuditDomain.review(
                    draft,source.setup,source.catalog,inputs)
                if(audit.status!=CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ||
                    audit.issues.isNotEmpty())
                    return@withOwnerTransaction NativeRecoveredFirstProgramReview.Rejected(
                        "The saved first program did not pass a new full-cycle audit.")
                if(localAthlete.signedInIdentity()!=owner)
                    return@withOwnerTransaction NativeRecoveredFirstProgramReview.Rejected(
                        "The active athlete changed.")
                NativeRecoveredFirstProgramReview.Ready(inputs)
            }
        }.getOrElse {
            NativeRecoveredFirstProgramReview.Rejected(
                "First-program review could not be recovered. No workout was approved.")
        }
    }

    fun createBlankCustomCycleDraft(
        cycleId:String,
        goal:AthleteTrainingGoal,
        title:String,
        firstBlockId:String,
        firstBlockLabel:String,
        firstBlockWeeks:Int,
        phase:TrainingPhase,
        split:CustomSplitIdentity
    ):NativeCustomDraftWriteResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED)
        val candidate=CustomCycleBuilderDomain.blank(
            cycleId,owner.athleteId,goal,title,firstBlockId,
            firstBlockWeeks,phase,split,firstBlockLabel)
            ?:return NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.INVALID_DRAFT)
        return customDraftStore.save(owner.ownerKey,cycleId,0,candidate)
    }

    fun readCustomCycleDraft(cycleId:String):NativeCustomDraftReadResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomDraftReadResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED)
        return customDraftStore.read(owner.ownerKey,cycleId)
    }

    fun saveCustomCycleDraft(
        draft:CustomTrainingCycle, expectedRevision:Long
    ):NativeCustomDraftWriteResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED)
        return customDraftStore.save(owner.ownerKey,draft.cycleId,
            expectedRevision,draft)
    }

    fun deleteCustomCycleDraft(
        cycleId:String, expectedRevision:Long
    ):NativeCustomDraftWriteResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomDraftWriteResult.Rejected(
                NativeCustomDraftRefusal.NOT_AUTHORIZED)
        return customDraftStore.delete(owner.ownerKey,cycleId,expectedRevision)
    }

    /**
     * Isolated future native program-review handoff. The caller must supply
     * an approved, CURRENT Kotlin plan and policies derived from verified
     * athlete/gym/catalog revisions. Here we independently re-stage the
     * full week before any commit and bind the private local owner.
     *
     * R&D ONLY until those upstream revision owners are integrated.
     */
    fun publishReviewedProgram(
        request:KotlinWorkoutSnapshotRequest,
        expectedApprovalRevision:Long,
        storageReceiptId:String
    ):NativeApprovalWriteResult {
        val current=localAthlete.signedInIdentity()
            ?:return NativeApprovalWriteResult.Rejected(
                NativeApprovalWriteRefusal.NOT_AUTHORIZED)
        if(request.source.athleteId!=current.athleteId ||
            request.grant.approvedSource!=request.source ||
            request.source!=request.currentSource ||
            expectedApprovalRevision<0)
            return NativeApprovalWriteResult.Rejected(
                NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
        return runCatching {
            revisionFence.withOwnerTransaction(current.ownerKey) {
                if(localAthlete.signedInIdentity()!=current)
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.NOT_AUTHORIZED)
                val saved=(authoringSourceStore.read(current.ownerKey)
                    as? NativeAuthoringSourceRead.Found)?.source
                    ?:return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
                val approval=NativeKotlinWorkoutApproval(
                    current.ownerKey,current.athleteId,request.source,
                    request.currentSource,request.grant,request.approvedProgram,
                    request.policies,storageReceiptId,expectedApprovalRevision+1)
                // Authoring source is the current gym/catalog authority.
                // Do not publish a plan carrying removed weights, stale
                // attachments, changed exercise credits or conflicting days.
                if(NativeKotlinApprovedGymCoherenceDomain.inspect(approval,saved)
                    !=NativeApprovedGymCoherenceResult.Matched)
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
                val staged=runCatching {
                    KotlinWorkoutSnapshotDomain.stage(request)
                }.getOrNull()
                if(staged !is KotlinWorkoutSnapshotResult.Staged)
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
                approvalStore.publish(
                    current.ownerKey,expectedApprovalRevision,approval)
            }
        }.getOrElse {
            NativeApprovalWriteResult.Rejected(
                NativeApprovalWriteRefusal.STORAGE_UNAVAILABLE)
        }
    }

    /**
     * Read-only owner-verified generated workout selection for isolated
     * native Train. Not an issuance decision or custom-cycle fallback.
     * Off-main-thread only: the private approval file is reopened under
     * the same owner fence as grants, journaling and sign-out.
     */
    fun currentlyApprovedKotlinWorkoutId():String? {
        val owner=localAthlete.signedInIdentity() ?:return null
        val pending=approvalStore.currentOwnedApproval(owner.ownerKey)
            ?.takeIf { it.athleteId==owner.athleteId }
            ?:return null
        return gymCoherentAuthority.currentApprovedWorkout(
            owner.ownerKey,pending.grant.workoutId)?.grant?.workoutId
    }

    /**
     * The screen receives only a read-only, revision-bound budget preview.
     * Reopen the currently approved gym/program under the owner fence. This
     * does not grant an issued workout or change the accepted plan.
     */
    fun reviewCurrentKotlinShortSession(
        workoutId:String,minutes:Int
    ):NativeKotlinTimeLimitChoiceReview? {
        val owner=localAthlete.signedInIdentity() ?:return null
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                if(localAthlete.signedInIdentity()!=owner)
                    return@withOwnerTransaction null
                val approval=gymCoherentAuthority.currentApprovedWorkout(
                    owner.ownerKey,workoutId)
                    ?:return@withOwnerTransaction null
                if(approval.athleteId!=owner.athleteId ||
                    approval.approvedSource.athleteId!=owner.athleteId)
                    return@withOwnerTransaction null
                val existing=coordinator.open(owner.ownerKey,workoutId)
                if(existing is NativeJournalFileResult.Rejected &&
                    existing.reason!=NativeJournalFileRefusal.NOT_ISSUED)
                    return@withOwnerTransaction null
                val proposal=KotlinShorterSessionReviewDomain.review(
                    KotlinWorkoutSnapshotRequest(
                        approval.approvedSource,approval.currentSource,
                        approval.grant,approval.program,approval.policies),minutes)
                NativeKotlinTimeLimitChoiceReview(
                    workoutId,approval.storageRevision,
                    approval.shortSessionLimitMinutes,proposal,
                    existing is NativeJournalFileResult.Durable)
            }
        }.getOrNull()
    }

    /**
     * Explicit user acceptance or Undo-to-Full BEFORE first issuance.
     * Never trust a preview as the current program: re-open the complete
     * approved plan and current gym, require the exact approval revision,
     * reject an already-issued journal, re-stage the budget and atomically
     * publish a NEW accepted revision under the same owner transaction.
     */
    fun acceptCurrentKotlinShortSession(
        workoutId:String,expectedApprovalRevision:Long,
        minutes:Int?,receiptId:String
    ):NativeApprovalWriteResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeApprovalWriteResult.Rejected(
                NativeApprovalWriteRefusal.NOT_AUTHORIZED)
        return runCatching {
            revisionFence.withOwnerTransaction(owner.ownerKey) {
                if(localAthlete.signedInIdentity()!=owner)
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.NOT_AUTHORIZED)
                val approval=gymCoherentAuthority.currentApprovedWorkout(
                    owner.ownerKey,workoutId)
                    ?:return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.NOT_AUTHORIZED)
                if(approval.athleteId!=owner.athleteId)
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.NOT_AUTHORIZED)
                if(expectedApprovalRevision!=approval.storageRevision ||
                    approval.storageRevision>=9_007_199_254_740_990L)
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.STALE_REVISION)
                if(!Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$").matches(receiptId))
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
                if(minutes!=null) {
                    val preview=KotlinShorterSessionReviewDomain.review(
                        KotlinWorkoutSnapshotRequest(
                            approval.approvedSource,approval.currentSource,
                            approval.grant,approval.program,approval.policies),minutes)
                    if(preview.status !in setOf(
                            KotlinShortSessionStatus.ALREADY_FITS,
                            KotlinShortSessionStatus.READY_FOR_ATHLETE_REVIEW))
                        return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                            NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
                }
                // Must be NOT_ISSUED, never treat corruption, storage failure
                // or an existing finished/unsubmitted journal as disposable.
                val existing=coordinator.open(owner.ownerKey,workoutId)
                if(existing!=NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.NOT_ISSUED))
                    return@withOwnerTransaction NativeApprovalWriteResult.Rejected(
                        NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
                approvalStore.publish(
                    owner.ownerKey,expectedApprovalRevision,
                    approval.copy(
                        storageReceiptId=receiptId,
                        storageRevision=expectedApprovalRevision+1,
                        shortSessionLimitMinutes=minutes)
                )
            }
        }.getOrElse {
            NativeApprovalWriteResult.Rejected(
                NativeApprovalWriteRefusal.STORAGE_UNAVAILABLE)
        }
    }

    fun issueWorkout(workoutId:String):NativeKotlinIssueResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeKotlinIssueResult.Rejected(
                NativeKotlinIssueRefusal.NOT_AUTHORIZED)
        return trackedWorkout.issue(owner.ownerKey,owner.athleteId,workoutId)
    }


    /**
     * Premium NEXT-SET coaching using only a reopened native journal and the
     * CURRENT fully coherent owner-verified gym. It never changes a target.
     * This also fails safely when the workout's original gym is no longer
     * the approved source: past sets can be resumed, but no unverified
     * physical weight suggestion is generated.
     */
    fun reviewLiveIssuedNextSet(
        workoutId:String,setKey:String,
        readiness:CoachReadiness,movementRestricted:Boolean,
        maximumReviewStep:java.math.BigDecimal
    ):LiveSetCoachResult? {
        val signed=localAthlete.signedInIdentity()?:return null
        return runCatching {
            revisionFence.withOwnerTransaction(signed.ownerKey) {
                if(localAthlete.signedInIdentity()!=signed)
                    return@withOwnerTransaction null
                val assistance=(coachSettings.read(signed.ownerKey)
                    as? NativeCoachSettingsRead.Current)?.settings?.policy
                    ?:return@withOwnerTransaction null
                val stored=coordinator.open(signed.ownerKey,workoutId)
                    as? NativeJournalFileResult.Durable
                    ?:return@withOwnerTransaction null
                val journal=stored.journal
                if(journal.snapshot.source.athleteId!=signed.athleteId)
                    return@withOwnerTransaction null
                val selected=journal.snapshot.targets.singleOrNull{
                    it.setKey==setKey
                }?:return@withOwnerTransaction null
                val current=gymCoherentAuthority.currentApprovedWorkout(
                    signed.ownerKey,workoutId)
                val stock=if(current?.approvedSource==journal.snapshot.source)
                    (current.program.exercises.singleOrNull{
                        it.exerciseId==selected.exerciseId
                    }?.equipment as? ComposerEquipment.Verified)?.inventory
                else null
                KotlinJournalLiveSetCoachDomain.review(
                    journal,setKey,stock,readiness,movementRestricted,
                    maximumReviewStep,assistance)
            }
        }.getOrNull()
    }

    fun reviewLiveCustomNextSet(
        workoutId:String,setKey:String,
        readiness:CoachReadiness,movementRestricted:Boolean,
        maximumReviewStep:java.math.BigDecimal
    ):LiveSetCoachResult? {
        val owner=localAthlete.signedInIdentity() ?:return null
        return customLiveSetCoach.review(owner.ownerKey,workoutId,setKey,
            readiness,movementRestricted,maximumReviewStep)
    }

    fun recoverWorkout(workoutId:String):NativeJournalFileResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_AUTHORIZED)
        return trackedWorkout.open(owner.ownerKey,owner.athleteId,workoutId)
    }

    fun recordWorkoutOperation(
        workoutId:String,operation:KotlinJournalOperation
    ):NativeJournalFileResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_AUTHORIZED)
        return trackedWorkout.append(
            owner.ownerKey,owner.athleteId,workoutId,operation)
    }

    /**
     * Read-only Composer replacement options from the current independently
     * approved gym/plan. These are suggestions, NOT a write entitlement.
     * The selected target is fully reverified when the athlete confirms.
     */
    fun reviewComposerWorkoutSwapChoices(
        workoutId:String,previousExerciseId:String,
        search:String=""
    ):List<KotlinComposerSwapChoice> {
        val owner=localAthlete.signedInIdentity() ?:return emptyList()
        val saved=trackedWorkout.open(
            owner.ownerKey,owner.athleteId,workoutId)
            as? NativeJournalFileResult.Durable ?:return emptyList()
        val journal=saved.journal
        if(journal.finishedByEventId!=null ||
            journal.snapshot.targets.none{it.exerciseId==previousExerciseId} ||
            journal.snapshot.targets.filter{
                it.exerciseId==previousExerciseId
            }.any{it.setKey in journal.effectiveSets})
            return emptyList()
        val approval=gymCoherentAuthority.currentApprovedWorkout(
            owner.ownerKey,workoutId) ?:return emptyList()
        if(approval.approvedSource!=journal.originalSnapshot.source ||
            approval.athleteId!=owner.athleteId)
            return emptyList()
        val original=approval.program.exercises.singleOrNull {
            it.exerciseId==previousExerciseId
        }?:return emptyList()
        val focus=approval.program.days.getOrNull(
            journal.snapshot.dayIndex)?.focus ?:return emptyList()
        return KotlinComposerSwapRankingDomain.rank(
            original,approval.program.exercises,
            journal.snapshot.targets.map{it.exerciseId}.toSet(),
            focus,search)
    }

    /**
     * Read-only plate viewer proof for an already issued generated workout.
     * A source-matched, owner-approved program AND exact exercise inventory
     * are required on every review. The target need not equal the entered
     * load, because this displays plates for the athlete's edited value.
     * Nothing is written to the journal or approved training program.
     */
    fun reviewComposerPlateLayout(
        workoutId:String,exerciseId:String,
        entered:BigDecimal,unit:LoadUnit
    ):KotlinVerifiedPlateLayout? {
        val owner=localAthlete.signedInIdentity() ?:return null
        val saved=trackedWorkout.open(
            owner.ownerKey,owner.athleteId,workoutId)
            as? NativeJournalFileResult.Durable ?:return null
        val journal=saved.journal
        if(journal.snapshot.targets.none{
            it.exerciseId==exerciseId &&
                (it.load as? DraftSetLoad.Verified)?.basis==
                    LoadBasis.BARBELL_TOTAL
        })return null
        val approval=gymCoherentAuthority.currentApprovedWorkout(
            owner.ownerKey,workoutId) ?:return null
        if(approval.approvedSource!=journal.originalSnapshot.source ||
            approval.athleteId!=owner.athleteId)
            return null
        val sourceExercise=approval.program.exercises.singleOrNull{
            it.exerciseId==exerciseId
        } ?:return null
        val equipment=sourceExercise.equipment as?
            ComposerEquipment.Verified ?:return null
        val stock=equipment.inventory as?
            VerifiedLoadStock.Barbell ?:return null
        if(equipment.unit!=unit || stock.unit!=unit ||
            stock.basis!=LoadBasis.BARBELL_TOTAL)
            return null
        return KotlinVerifiedPlateLayoutDomain.inspect(stock,entered,unit)
    }

    fun swapIssuedComposerExercise(
        workoutId:String,eventId:String,expectedRevision:Long,
        previousExerciseId:String,replacementExerciseId:String,
        intents:List<KotlinComposerSwapSetIntent>
    ):NativeJournalFileResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_AUTHORIZED)
        return trackedWorkout.swapExercise(
            owner.ownerKey,owner.athleteId,workoutId,
            eventId,expectedRevision,previousExerciseId,
            replacementExerciseId,intents)
    }

    fun undoIssuedComposerExerciseSwap(
        workoutId:String,eventId:String,expectedRevision:Long,
        swapEventId:String
    ):NativeJournalFileResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_AUTHORIZED)
        return trackedWorkout.undoSwap(
            owner.ownerKey,owner.athleteId,workoutId,
            eventId,expectedRevision,swapEventId)
    }

    /**
     * Read-only access to the independent Kotlin history index. Unenrolled,
     * pending or incomplete manifests are rejected, never replaced by empty
     * history. This does not publish PRs to the JS runtime.
     */
    fun reviewNativeHistory():NativeKotlinHistoryReadResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeKotlinHistoryReadResult.Rejected(
                NativeKotlinHistoryReadRefusal.NOT_AUTHORIZED)
        val authority=NativeKotlinHistoryManifestFileAuthority(historyStore) {
                ownerKey,athleteId ->
            val current=localAthlete.signedInIdentity()
            current!=null && current.ownerKey==ownerKey &&
                current.athleteId==athleteId
        }
        return NativeKotlinGlobalHistoryReadCoordinator(
            journalStore,correctionStore,approvalStore,authority,revisionFence
        ).read(owner.ownerKey,owner.athleteId)
    }

    /**
     * Source-only saved-history edit. Never bypasses global manifest
     * preparation/finalization or assumes target RIR is performed RIR.
     */
    fun correctSavedKotlinWorkout(
        workoutId:String,operation:KotlinSavedSetCorrection
    ):NativeSavedCorrectionFileResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.NOT_AUTHORIZED)
        return trackedWorkout.correct(
            owner.ownerKey,owner.athleteId,workoutId,operation)
    }

    fun revokeCurrentApproval(expectedRevision:Long):NativeApprovalWriteResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeApprovalWriteResult.Rejected(
                NativeApprovalWriteRefusal.NOT_AUTHORIZED)
        return approvalStore.revokeCurrentGrant(owner.ownerKey,expectedRevision)
    }
}
