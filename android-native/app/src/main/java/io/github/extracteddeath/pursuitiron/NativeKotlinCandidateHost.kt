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
    private val coordinator=NativeKotlinWorkoutCoordinator(
        KotlinNativeJournalFileStore(root),approvalStore,revisionFence)

    /** The Kotlin engine owns readiness validation, never the JS runtime. */
    fun reviewSetup(draft:AthleteSetupDraft):AthleteSetupReview =
        CoachAthleteSetupDomain.review(draft)

    /**
     * Native onboarding's explicit confirmation must precede local enrolment.
     * A review-only draft grants NO write access.
     */
    fun activateReviewedSetup(review:AthleteSetupReview):
        AppPrivateKotlinLocalAthlete.Enrollment {
        if(review.status!=AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW ||
            review.reviewed==null)
            return AppPrivateKotlinLocalAthlete.Enrollment.Rejected(
                "Resolve onboarding requirements before creating an athlete")
        return localAthlete.activateReviewed(review.reviewed)
    }

    /** Deliberately clears only in-memory activation; training files stay. */
    fun deactivateLocalAthlete() {
        localAthlete.deactivate()
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
        return coordinator.issue(owner.ownerKey,workoutId)
    }

    fun recoverWorkout(workoutId:String):NativeJournalFileResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_AUTHORIZED)
        return coordinator.open(owner.ownerKey,workoutId)
    }

    fun recordWorkoutOperation(
        workoutId:String,operation:KotlinJournalOperation
    ):NativeJournalFileResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_AUTHORIZED)
        return coordinator.append(owner.ownerKey,workoutId,operation)
    }

    fun revokeCurrentApproval(expectedRevision:Long):NativeApprovalWriteResult {
        val owner=localAthlete.signedInIdentity()
            ?:return NativeApprovalWriteResult.Rejected(
                NativeApprovalWriteRefusal.NOT_AUTHORIZED)
        return approvalStore.revokeCurrentGrant(owner.ownerKey,expectedRevision)
    }
}
