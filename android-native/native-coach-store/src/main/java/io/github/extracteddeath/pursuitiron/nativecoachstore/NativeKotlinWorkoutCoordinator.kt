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
/**
 * Durable acceptance of ONE history-derived per-set adjustment, bound to
 * immutable native evidence. This is NOT a substitute for live revalidation:
 * the issuer must recompute it from the complete corrected history, or refuse.
 */
data class NativeKotlinAcceptedRepTargets(
    val historicalWorkoutId: String,
    val historyRevision: Long,
    val exerciseId: String,
    val changedSetKey: String,
    val observedReps: List<Int>,
    val newRepTargets: List<Int>,
    val reviewedReadiness: CoachReadiness,
    /** Store the reviewed decision so repetition is never mislabeled as earned progress. */
    val decisionKind: AdaptiveRepDecisionKind =
        AdaptiveRepDecisionKind.EARNED_ONE_MORE_REP
)

data class NativeKotlinWorkoutApproval(
    val ownerKey: String,
    val athleteId: String,
    val approvedSource: KotlinTrainingSourceRevision,
    val currentSource: KotlinTrainingSourceRevision,
    val grant: KotlinWorkoutSelectionGrant,
    val program: ComposerRequest,
    val policies: List<ExercisePrescriptionPolicy>,
    val storageReceiptId: String,
    val storageRevision: Long,
    val acceptedRepTargets: NativeKotlinAcceptedRepTargets? = null
)

enum class NativeKotlinIssueRefusal {
    NOT_AUTHORIZED, APPROVAL_CHANGED, WORKOUT_UNRESOLVED,
    STORAGE_REJECTED, HISTORY_NOT_READY
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
 * Every app-owned source/approval mutation and sign-out MUST also use the
 * injected revision fence; otherwise this module cannot claim cross-store
 * atomicity. The production native account/approval provider is not wired.
 */
fun interface NativeKotlinAcceptedRepSnapshotVerifier {
    /** Must re-open FULL corrected history under the SAME owner fence. */
    fun rederive(ownerKey:String,approval:NativeKotlinWorkoutApproval):KotlinStagedWorkout?
}

class NativeKotlinWorkoutCoordinator(
    private val store: KotlinNativeJournalFileStore,
    private val authority: NativeKotlinWorkoutAuthority,
    private val revisionFence: NativeKotlinApprovalRevisionFence,
    private val repVerifier: NativeKotlinAcceptedRepSnapshotVerifier?=null,
    private val activationHandoff:NativeKotlinFutureActivationHandoffFileStore?=null
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
        val baseline=KotlinWorkoutSnapshotDomain.stage(
            KotlinWorkoutSnapshotRequest(
                approval.approvedSource,approval.currentSource,approval.grant,
                approval.program,approval.policies
            )
        )
        val proof=approval.acceptedRepTargets ?:return baseline
        val base=(baseline as? KotlinWorkoutSnapshotResult.Staged)?.snapshot
            ?:return baseline
        // Never issue generic ranges for an approved adaptive prescription
        // just because the optional history verifier is absent or failed.
        val rebuilt=repVerifier?.rederive(ownerKey,approval)
            ?:return KotlinWorkoutSnapshotResult.Rejected(
                KotlinSnapshotRejection.UNVERIFIED_TARGET)
        if(rebuilt.source!=base.source ||
            rebuilt.grantId!=base.grantId ||
            rebuilt.workoutId!=base.workoutId ||
            rebuilt.dayIndex!=base.dayIndex ||
            rebuilt.calendarDay!=base.calendarDay ||
            rebuilt.purpose!=base.purpose ||
            rebuilt.estimatedMinutes!=base.estimatedMinutes ||
            rebuilt.targets.size!=base.targets.size ||
            rebuilt.targets.map {it.setKey}!=base.targets.map {it.setKey})
            return KotlinWorkoutSnapshotResult.Rejected(
                KotlinSnapshotRejection.UNVERIFIED_TARGET)
        val changed=rebuilt.targets.zip(base.targets).filter {(next,old)->
            next!=old
        }
        val working=base.targets.filter {
            it.exerciseId==proof.exerciseId && it.type==DraftSetType.WORKING
        }.sortedBy {it.ordinal}
        if(working.size!=proof.newRepTargets.size ||
            proof.changedSetKey !in working.map {it.setKey} ||
            changed.size!=working.size ||
            changed.any {(next,old)->
                next.copy(reps=old.reps)!=old ||
                    old.exerciseId!=proof.exerciseId ||
                    old.type!=DraftSetType.WORKING
            } ||
            working.indices.any {i->
                val target=rebuilt.targets.singleOrNull {
                    it.setKey==working[i].setKey
                } ?:return@any true
                target.reps!=RepBand(proof.newRepTargets[i],proof.newRepTargets[i])
            })
            return KotlinWorkoutSnapshotResult.Rejected(
                KotlinSnapshotRejection.UNVERIFIED_TARGET)
        return KotlinWorkoutSnapshotResult.Staged(rebuilt)
    }

    /**
     * The entire approved-program review and atomic journal issuance happen
     * under the SAME owner revision fence used by native program/gym writers.
     * Without this lock, a writer can change approval after our second review
     * but before the workout file is atomically committed.
     */
    fun issue(ownerKey: String, workoutId: String): NativeKotlinIssueResult =
        runCatching {
            revisionFence.withOwnerTransaction(ownerKey) {
                issueFenced(ownerKey,workoutId)
            }
        }.getOrElse {
            NativeKotlinIssueResult.Rejected(NativeKotlinIssueRefusal.STORAGE_REJECTED,
                storageReason=NativeJournalFileRefusal.STORAGE_UNAVAILABLE)
        }

    private fun issueFenced(ownerKey: String, workoutId: String):
        NativeKotlinIssueResult {
        if(activationHandoff?.blocksNewIssuance(ownerKey)==true)
            return NativeKotlinIssueResult.Rejected(NativeKotlinIssueRefusal.NOT_AUTHORIZED)
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
            // Check the ENTIRE approved week and all policy records, not just
            // the selected day's re-staged targets. Editing a different day
            // is still a change in this approved-program transaction.
            val remainsSame=second==approval &&
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
        runCatching {
            revisionFence.withOwnerTransaction(ownerKey) {
                store.openStored(ownerKey,workoutId) { owner,source ->
                    runCatching {authority.canAccessIssuedWorkout(owner,source)}
                        .getOrDefault(false)
                }
            }
        }.getOrElse {
            NativeJournalFileResult.Rejected(NativeJournalFileRefusal.STORAGE_UNAVAILABLE)
        }

    fun append(
        ownerKey:String,workoutId:String,operation:KotlinJournalOperation
    ):NativeJournalFileResult =
        runCatching {
            revisionFence.withOwnerTransaction(ownerKey) {
                store.appendStored(ownerKey,workoutId,operation) { owner,source ->
                    runCatching {authority.canAccessIssuedWorkout(owner,source)}
                        .getOrDefault(false)
                }
            }
        }.getOrElse {
            NativeJournalFileResult.Rejected(NativeJournalFileRefusal.STORAGE_UNAVAILABLE)
        }
}
