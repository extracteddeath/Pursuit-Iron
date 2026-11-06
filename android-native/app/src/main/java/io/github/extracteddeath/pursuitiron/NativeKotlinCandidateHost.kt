package io.github.extracteddeath.pursuitiron

import android.content.Context
import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*

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
internal class NativeKotlinCandidateHost(context:Context) {
    private val root=context.applicationContext.noBackupFilesDir
        .resolve("kotlin-coach-v1").toPath()
    private val localAthlete=AppPrivateKotlinLocalAthlete(
        context.applicationContext)
    private val revisionFence=NativeKotlinApprovalRevisionFence(root)
    private val approvalStore=NativeKotlinApprovalFileAuthority(
        root,revisionFence,localAthlete)
    private val customDraftStore=NativeCustomCycleDraftFileStore(
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
        adaptiveCyclePermissions,customCycleSignoffs,futureBlockContexts)
    private val customWorkoutPreflight=NativeKotlinCustomWorkoutPreflight(
        revisionFence,localAthlete,customDraftStore,authoringSourceStore,
        customCycleSignoffs)
    private val customPreparedStore=NativeKotlinCustomWorkoutPreparedStore(
        root,revisionFence,localAthlete,customWorkoutPreflight)
    private val journalStore=KotlinNativeJournalFileStore(root)
    private val correctionStore=KotlinNativeSavedCorrectionFileStore(root,journalStore)
    private val historyStore=NativeKotlinHistoryManifestFileStore(root)
    private val customIssuedStore=NativeKotlinCustomWorkoutJournalFileStore(
        root,revisionFence,localAthlete,customPreparedStore,
        customWorkoutPreflight,historyStore,journalStore)
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
    private val futureActivationPreflight=NativeKotlinFutureBlockActivationPreflight(
        revisionFence,localAthlete,customDraftStore,futureBlockContexts,
        mixedHistoryReader,customIssuedStore,outstandingCustomInventory)
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
    private val coordinator=NativeKotlinWorkoutCoordinator(
        journalStore,gymCoherentAuthority,revisionFence,
        NativeKotlinAcceptedRepSnapshotVerifier { owner,approval ->
            val signed=localAthlete.signedInIdentity()
            if(signed==null || signed.ownerKey!=owner ||
                signed.athleteId!=approval.athleteId)null
            else mixedHistoryReader.rederiveAcceptedRepSnapshot(
                owner,signed.athleteId,approvalStore,approval,
                authoringSourceStore)
        })
    private val historyTransactions=NativeKotlinHistoryTransactionCoordinator(
        historyStore,journalStore,correctionStore,approvalStore,revisionFence)
    private val trackedWorkout=NativeKotlinTrackedWorkoutGateway(
        coordinator,historyTransactions,historyStore,correctionStore,
        gymCoherentAuthority,revisionFence)

    /** A displayed local ID is not a session nor access to gym/history. */
    fun savedAthleteIdForExplicitResume():String? =
        localAthlete.savedAthleteIdForExplicitResume()

    /** The Kotlin engine owns readiness validation, never the JS runtime. */
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
            cycleId,absoluteWeek,weekday,inputs)
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
            absoluteWeek,weekday,inputs)
    }

    /** Past committed prescriptions remain independently cold-recoverable. */
    fun openIssuedCustomWorkout(workoutId:String):NativeCustomJournalResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomJournalResult.Rejected(
                NativeCustomJournalRefusal.NOT_AUTHORIZED)
        return customIssuedStore.open(owner.ownerKey,workoutId)
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
        return mixedHistoryReader.reviewCurrentNextSetTargets(
            owner.ownerKey,owner.athleteId,approvalStore,exerciseId,
            readiness,movementRestricted,authoringSourceStore)
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
        return mixedHistoryReader.acceptReviewedNextSetTargets(
            signed.ownerKey,signed.athleteId,approvalStore,intent,
            readiness,movementRestricted,authoringSourceStore)
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
        return mixedHistoryReader.acceptCurrentApprovedLoad(
            owner.ownerKey,owner.athleteId,approvalStore,intent,
            maximumAuthorizedIncrease,readiness,movementRestricted,
            authoringSourceStore)
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
        return mixedHistoryReader.reviewCurrentApprovedLoad(
            owner.ownerKey,owner.athleteId,approvalStore,exerciseId,
            maximumAuthorizedIncrease,readiness,movementRestricted,
            authoringSourceStore)
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
        return adaptiveCycleDrafts.preview(choice)
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
        return adaptiveCycleDrafts.accept(choice,
            reviewedFingerprint,athleteConfirmed)
    }

    fun readCustomCycleConsent(
        cycleId:String,inputs:CustomCoachAuditInputs?=null
    ):NativeCustomSignoffRead {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeCustomSignoffRead.Rejected(
                NativeCustomSignoffRefusal.NOT_AUTHORIZED)
        return customCycleSignoffs.read(owner.ownerKey,cycleId,inputs)
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

    fun issueWorkout(workoutId:String):NativeKotlinIssueResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeKotlinIssueResult.Rejected(
                NativeKotlinIssueRefusal.NOT_AUTHORIZED)
        return trackedWorkout.issue(owner.ownerKey,owner.athleteId,workoutId)
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
