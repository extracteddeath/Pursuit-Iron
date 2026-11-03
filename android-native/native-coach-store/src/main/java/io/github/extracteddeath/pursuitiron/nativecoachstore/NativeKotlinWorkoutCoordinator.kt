package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Integration boundary for a future Android authenticated account/program
 * owner, NOT a replacement for its identity, permissions or SQL transaction.
 *
 * Implementations MUST load the current signed-in athlete and the athlete's
 * explicitly approved program and selection grant from trusted app storage.
 * Never implement this interface using a UI-provided boolean or untrusted
 * request body. Program approval and authentication are not yet wired to the
 * production Compose app.
 */
interface NativeKotlinWorkoutAuthority {
    /**
     * Return the complete CURRENT authoritative approval for this one workout.
     * Null includes signed-out, revoked, cross-account and stale approvals.
     */
    fun currentApprovedWorkout(ownerKey: String, workoutId: String): NativeKotlinWorkoutApproval?

    /**
     * Authorize access to an ALREADY issued workout. The original source
     * revision may no longer be current (a program might have been edited).
     * Preserve its frozen targets, but never grant cross-account access.
     */
    fun canAccessIssuedWorkout(ownerKey: String, source: KotlinTrainingSourceRevision): Boolean
}

/**
 * Trusted storage owns these fields. The app must authenticate the owner key
 * and athlete ID independently; they are NOT inferred from a JS program.
 */
data class NativeKotlinWorkoutApproval(
    val ownerKey: String,
    val athleteId: String,
    val approvedSource: KotlinTrainingSourceRevision,
    val currentSource: KotlinTrainingSourceRevision,
    val grant: KotlinWorkoutSelectionGrant,
    val program: ComposerRequest,
    val policies: List<ExercisePrescriptionPolicy>,
    val storageReceiptId: String,
    val storageRevision: Long
)

enum class NativeKotlinIssueRefusal {
    NOT_AUTHORIZED, APPROVAL_CHANGED, WORKOUT_UNRESOLVED,
    STORAGE_REJECTED
}

sealed interface NativeKotlinIssueResult {
    data class Committed(
        val journal: KotlinWorkoutJournalState,
        /** An identical issued workout already exists on private storage. */
        val replayed: Boolean
    ) : NativeKotlinIssueResult

    data class Rejected(
        val reason: NativeKotlinIssueRefusal,
        val snapshotReason: KotlinSnapshotRejection? = null,
        val storageReason: NativeJournalFileRefusal? = null
    ) : NativeKotlinIssueResult
}

/**
 * Only the Kotlin coach builds the actual prescribed snapshot. Only the
 * native journal store can issue it. The authority is read AGAIN inside the
 * storage lock and must still approve exactly the same athlete, source,
 * selected day, full plan and exact prescribed sets.
 *
 * No JS fallback, no UI-created issuance grant and no optimistic success.
 * Upstream app-owned source/approval writes still need a shared transaction or
 * explicit version fencing before this isolated module may ship.
 */
class NativeKotlinWorkoutCoordinator(
    private val store: KotlinNativeJournalFileStore,
    private val authority: NativeKotlinWorkoutAuthority
) {
    private fun stage(
        ownerKey: String,
        workoutId: String,
        approval: NativeKotlinWorkoutApproval?
    ): KotlinWorkoutSnapshotResult? {
        approval ?: return null
        if(approval.ownerKey!=ownerKey ||
            approval.athleteId!=approval.approvedSource.athleteId ||
            approval.grant.workoutId!=workoutId ||
            approval.grant.approvedSource!=approval.approvedSource ||
            approval.approvedSource!=approval.currentSource ||
            approval.storageRevision<1)
            return null
        return KotlinWorkoutSnapshotDomain.stage(
            KotlinWorkoutSnapshotRequest(
                approval.approvedSource,approval.currentSource,approval.grant,
                approval.program,approval.policies
            )
        )
    }

    fun issue(ownerKey: String, workoutId: String): NativeKotlinIssueResult {
        val initialApproval=runCatching {
            authority.currentApprovedWorkout(ownerKey,workoutId)
        }.getOrNull()
        val first=runCatching {
            stage(ownerKey,workoutId,initialApproval)
        }.getOrNull() ?: return NativeKotlinIssueResult.Rejected(
            NativeKotlinIssueRefusal.NOT_AUTHORIZED)
        if(first is KotlinWorkoutSnapshotResult.Rejected)
            return NativeKotlinIssueResult.Rejected(
                NativeKotlinIssueRefusal.WORKOUT_UNRESOLVED,first.reason)
        val snapshot=(first as KotlinWorkoutSnapshotResult.Staged).snapshot
        val approval=checkNotNull(initialApproval)
        val stored=KotlinStoredWorkoutIdentity(
            approval.storageReceiptId,snapshot.workoutId,
            snapshot.source,approval.storageRevision
        )
        var changed=false
        val written=store.issueAuthorized(ownerKey,snapshot,stored) {
            underLockOwner,underLockSource ->
            val second=runCatching {
                authority.currentApprovedWorkout(underLockOwner,workoutId)
            }.getOrNull()
            val rerun=runCatching {
                stage(underLockOwner,workoutId,second)
            }.getOrNull()
            val remainsSame=second!=null &&
                second.storageReceiptId==approval.storageReceiptId &&
                second.storageRevision==approval.storageRevision &&
                underLockSource==snapshot.source &&
                rerun is KotlinWorkoutSnapshotResult.Staged &&
                rerun.snapshot==snapshot
            if(!remainsSame)changed=true
            remainsSame
        }
        return when(written) {
            is NativeJournalFileResult.Durable ->
                NativeKotlinIssueResult.Committed(written.journal,written.replayed)
            is NativeJournalFileResult.Rejected -> NativeKotlinIssueResult.Rejected(
                if(changed)NativeKotlinIssueRefusal.APPROVAL_CHANGED
                else NativeKotlinIssueRefusal.STORAGE_REJECTED,
                storageReason=written.reason
            )
        }
    }

    /**
     * A past valid session may be resumed after a plan changes. The app owner
     * still authenticates the current user, and the original targets remain
     * exactly as issued and independently receipt-replayed from private disk.
     */
    fun open(ownerKey:String,workoutId:String):NativeJournalFileResult =
        store.openStored(ownerKey,workoutId) { owner,source ->
            runCatching {authority.canAccessIssuedWorkout(owner,source)}
                .getOrDefault(false)
        }

    fun append(
        ownerKey:String,workoutId:String,operation:KotlinJournalOperation
    ):NativeJournalFileResult =
        store.appendStored(ownerKey,workoutId,operation) { owner,source ->
            runCatching {authority.canAccessIssuedWorkout(owner,source)}
                .getOrDefault(false)
        }
}
