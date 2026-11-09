package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * Only supported integration entrypoint for the ISOLATED native Kotlin
 * candidate's issue/open/record/Finish/correct lifecycle.
 *
 * Unlike a direct journal append, Finish goes through the redo-backed
 * globally revisioned history transaction. Every caller-visible operation
 * is fenced to the same owner and athlete, and no unfinished or incomplete
 * history ownership is silently treated as a fresh empty account.
 *
 * This is NOT live JS/Compose product authority. Enrollment requires an
 * independently proven complete native history, not a UI toggle.
 */
class NativeKotlinTrackedWorkoutGateway(
    private val workout: NativeKotlinWorkoutCoordinator,
    private val history: NativeKotlinHistoryTransactionCoordinator,
    private val ledger: NativeKotlinHistoryManifestFileStore,
    private val corrections: KotlinNativeSavedCorrectionFileStore,
    private val ownerAuthority: NativeKotlinWorkoutAuthority,
    private val ownerFence: NativeKotlinApprovalRevisionFence
) {
    private val valid = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    /** Fenced read of the authenticated, complete, non-pending manifest. */
    private fun ready(owner: String, athlete: String): Boolean {
        val current = ledger.open(owner, athlete)
        if (current !is NativeHistoryLedgerResult.Durable ||
            current.ledger.ownerKey != owner ||
            current.ledger.athleteId != athlete ||
            !current.ledger.readyForHistoryReads)
            return false
        return true
    }

    private fun authorized(
        owner: String, athlete: String, journal: KotlinWorkoutJournalState
    ): Boolean = journal.snapshot.source.athleteId == athlete &&
        runCatching {
            ownerAuthority.canAccessIssuedWorkout(owner, journal.snapshot.source)
        }.getOrDefault(false)

    private fun unavailable() = NativeJournalFileResult.Rejected(
        NativeJournalFileRefusal.HISTORY_NOT_READY
    )

    private fun verifyHistory(
        owner: String, athlete: String
    ): Boolean {
        // This recovers unfinished redo intents instead of treating a
        // pending manifest as a missing/empty account.
        val recovered = history.recover(owner, athlete)
        return recovered is NativeHistoryTransactionResult.Durable &&
            ready(owner, athlete)
    }

    /**
     * Do not issue a brand-new Kotlin workout against an untracked legacy
     * history. The account must explicitly complete migration/enrollment.
     */
    fun issue(
        owner: String, athlete: String, workoutId: String
    ): NativeKotlinIssueResult = fenced(owner, athlete,
        NativeKotlinIssueResult.Rejected(NativeKotlinIssueRefusal.HISTORY_NOT_READY)) {
        if (!verifyHistory(owner, athlete))
            NativeKotlinIssueResult.Rejected(NativeKotlinIssueRefusal.HISTORY_NOT_READY)
        else {
            // The authenticated caller's athlete must match the currently
            // stored approval BEFORE the issuer can write a journal. Checking
            // only the returned journal would reject too late.
            val approved = runCatching {
                ownerAuthority.currentApprovedWorkout(owner, workoutId)
            }.getOrNull()
            if (approved == null || approved.ownerKey != owner ||
                approved.athleteId != athlete ||
                approved.approvedSource.athleteId != athlete ||
                approved.currentSource.athleteId != athlete ||
                approved.grant.workoutId != workoutId)
                NativeKotlinIssueResult.Rejected(NativeKotlinIssueRefusal.NOT_AUTHORIZED)
            else {
                val issued = workout.issue(owner, workoutId)
                if (issued is NativeKotlinIssueResult.Committed &&
                    !authorized(owner, athlete, issued.journal))
                    NativeKotlinIssueResult.Rejected(NativeKotlinIssueRefusal.NOT_AUTHORIZED)
                else issued
            }
        }
    }

    /** Reads never issue, change, or publish a workout or a history cache. */
    fun open(
        owner: String, athlete: String, workoutId: String
    ): NativeJournalFileResult = fenced(owner, athlete, unavailable()) {
        if (!verifyHistory(owner, athlete)) unavailable()
        else {
            val opened = workout.open(owner, workoutId)
            if (opened is NativeJournalFileResult.Durable &&
                !authorized(owner, athlete, opened.journal))
                NativeJournalFileResult.Rejected(NativeJournalFileRefusal.NOT_AUTHORIZED)
            else opened
        }
    }

    /**
     * The native candidate no longer has any direct-Finish shortcut.
     * Even a lost-ack Finish must verify global revisioned history first.
     */
    fun append(
        owner: String, athlete: String, workoutId: String,
        operation: KotlinJournalOperation
    ): NativeJournalFileResult = fenced(owner, athlete, unavailable()) {
        if (!verifyHistory(owner, athlete)) return@fenced unavailable()
        when (operation) {
            is KotlinJournalOperation.SwapExercise,
            is KotlinJournalOperation.UndoSwap ->
                NativeJournalFileResult.Rejected(
                    NativeJournalFileRefusal.SWAP_SOURCE_UNAVAILABLE)
            is KotlinJournalOperation.RecordSet -> {
                val existing = workout.open(owner, workoutId)
                if (existing !is NativeJournalFileResult.Durable)
                    return@fenced existing
                if (!authorized(owner, athlete, existing.journal))
                    return@fenced NativeJournalFileResult.Rejected(
                        NativeJournalFileRefusal.NOT_AUTHORIZED)
                workout.append(owner, workoutId, operation)
            }
            is KotlinJournalOperation.Finish -> {
                val committed = history.complete(owner, athlete, workoutId, operation)
                if (committed !is NativeHistoryTransactionResult.Durable)
                    return@fenced unavailable()
                val recorded = workout.open(owner, workoutId)
                if (recorded !is NativeJournalFileResult.Durable)
                    return@fenced unavailable()
                if (!authorized(owner, athlete, recorded.journal) ||
                    recorded.journal.finishedByEventId != operation.eventId ||
                    committed.manifest.completions.none {
                        it.workoutId == workoutId &&
                            it.finishEventId == operation.eventId &&
                            it.originalJournalRevision == recorded.journal.revision
                    }) return@fenced unavailable()
                NativeJournalFileResult.Durable(
                    recorded.journal, committed.replayed)
            }
        }
    }

    /**
     * Replace an unstarted Composer exercise only after verified History,
     * current athlete access, and full source-based target review.
     */
    fun swapExercise(
        owner:String,athlete:String,workoutId:String,
        eventId:String,expectedRevision:Long,
        previousExerciseId:String,replacementExerciseId:String,
        intents:List<KotlinComposerSwapSetIntent>
    ):NativeJournalFileResult=fenced(owner,athlete,unavailable()) {
        if(!verifyHistory(owner,athlete))return@fenced unavailable()
        val opened=workout.open(owner,workoutId)
        if(opened !is NativeJournalFileResult.Durable)
            return@fenced opened
        if(!authorized(owner,athlete,opened.journal))
            return@fenced NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_AUTHORIZED)
        workout.swapExercise(owner,workoutId,eventId,expectedRevision,
            previousExerciseId,replacementExerciseId,intents)
    }

    fun undoSwap(
        owner:String,athlete:String,workoutId:String,
        eventId:String,expectedRevision:Long,swapEventId:String
    ):NativeJournalFileResult=fenced(owner,athlete,unavailable()) {
        if(!verifyHistory(owner,athlete))return@fenced unavailable()
        val opened=workout.open(owner,workoutId)
        if(opened !is NativeJournalFileResult.Durable)
            return@fenced opened
        if(!authorized(owner,athlete,opened.journal))
            return@fenced NativeJournalFileResult.Rejected(
                NativeJournalFileRefusal.NOT_AUTHORIZED)
        workout.undoExerciseSwap(owner,workoutId,eventId,expectedRevision,
            swapEventId)
    }

    /**
     * Saved-history editing cannot call the bare correction file writer.
     * Only a fully manifest-committed correction becomes a durable success.
     */
    fun correct(
        owner: String, athlete: String, workoutId: String,
        operation: KotlinSavedSetCorrection
    ): NativeSavedCorrectionFileResult = fenced(owner, athlete,
        NativeSavedCorrectionFileResult.Rejected(
            NativeSavedCorrectionRefusal.HISTORY_NOT_READY)) {
        if (!verifyHistory(owner, athlete))
            return@fenced NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.HISTORY_NOT_READY)
        val committed = history.correct(owner, athlete, workoutId, operation)
        if (committed !is NativeHistoryTransactionResult.Durable)
            return@fenced NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.HISTORY_NOT_READY)
        val reopened = corrections.openStored(owner, workoutId) { o, source ->
            source.athleteId == athlete && runCatching {
                ownerAuthority.canAccessIssuedWorkout(o, source)
            }.getOrDefault(false)
        }
        if (reopened !is NativeSavedCorrectionFileResult.Opened ||
            committed.manifest.completions.none {
                it.workoutId == workoutId &&
                    it.savedCorrectionRevision == reopened.state.correctionRevision
            })
            return@fenced NativeSavedCorrectionFileResult.Rejected(
                NativeSavedCorrectionRefusal.HISTORY_NOT_READY)
        NativeSavedCorrectionFileResult.Durable(
            reopened.state, committed.replayed)
    }

    private fun <T> fenced(
        owner: String, athlete: String, unavailable: T, action: () -> T
    ): T {
        if (!valid.matches(owner) || !valid.matches(athlete))
            return unavailable
        return runCatching {
            ownerFence.withOwnerTransaction(owner, action)
        }.getOrElse { unavailable }
    }
}
