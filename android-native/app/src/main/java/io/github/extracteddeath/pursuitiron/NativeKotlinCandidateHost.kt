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
    private val journalStore=KotlinNativeJournalFileStore(root)
    private val correctionStore=KotlinNativeSavedCorrectionFileStore(root,journalStore)
    private val historyStore=NativeKotlinHistoryManifestFileStore(root)
    private val coordinator=NativeKotlinWorkoutCoordinator(
        journalStore,approvalStore,revisionFence)
    private val historyTransactions=NativeKotlinHistoryTransactionCoordinator(
        historyStore,journalStore,correctionStore,approvalStore,revisionFence)
    private val trackedWorkout=NativeKotlinTrackedWorkoutGateway(
        coordinator,historyTransactions,historyStore,correctionStore,
        approvalStore,revisionFence)

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
        return authoringSourceStore.publish(owner.ownerKey,expectedRevision,
            NativeCustomAuthoringSnapshot(owner.ownerKey,owner.athleteId,
                expectedRevision+1,reviewed,curatedCatalog))
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
        val staged=runCatching{
            KotlinWorkoutSnapshotDomain.stage(request)
        }.getOrNull()
        if(staged !is KotlinWorkoutSnapshotResult.Staged)
            return NativeApprovalWriteResult.Rejected(
                NativeApprovalWriteRefusal.MALFORMED_APPROVAL)
        return approvalStore.publish(current.ownerKey,expectedApprovalRevision,
            NativeKotlinWorkoutApproval(
                current.ownerKey,current.athleteId,request.source,
                request.currentSource,request.grant,request.approvedProgram,
                request.policies,storageReceiptId,expectedApprovalRevision+1))
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
