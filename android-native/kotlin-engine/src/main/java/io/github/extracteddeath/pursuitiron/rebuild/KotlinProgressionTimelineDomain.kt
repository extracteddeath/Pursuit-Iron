package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * A storage-order record of an already completed native Kotlin workout.
 * The caller is responsible for authenticating the COMPLETE historical read:
 * this DTO does not claim that a journal or completion ordinal came from disk.
 */
data class KotlinChronologicalWorkout(
    val completionOrder: Long,
    val journal: KotlinWorkoutJournalState,
    /** Optional separately verified, post-Finish correction journal. */
    val savedCorrections: KotlinSavedHistoryCorrectionState? = null
)

/**
 * The native owner must supply every retained completion in this read range,
 * in historical order (including poor/skipped sessions), and compare the
 * revision against the CURRENT owned history before showing/using the review.
 * Passing two hand-picked good sessions is not a complete historical read.
 */
data class KotlinProgressionTimelineRequest(
    val athleteId: String,
    val exerciseId: String,
    val historyRevision: Long,
    val currentHistoryRevision: Long,
    val completedWorkouts: List<KotlinChronologicalWorkout>,
    /** Current exact physical inventory, still subject to owner verification. */
    val currentVerifiedStock: VerifiedLoadStock?,
    val maximumAuthorizedIncrease: BigDecimal,
    /**
     * Explicit owner-verified purpose of the NEXT proposed training session.
     * Null preserves the strict legacy adjacent-appearance comparison.
     * A specified purpose compares the latest two occurrences WITHIN that
     * purpose from the complete replayed history, not two chosen successes.
     */
    val requestedPurpose: ComposerSessionPurpose? = null,
    /**
     * Owner-verified index of the NEXT approved training day. For example,
     * Upper and Push bench exposures may both be HYPERTROPHY but must
     * progress independently. This index is meaningful only within the
     * source's exact approved program revision. Null preserves the older
     * purpose-only diagnostic until the trusted owner supplies day identity.
     */
    val requestedDayIndex: Int? = null,
    /** Optional physical machine scope; never infer equal resistance from a shared lift. */
    val requestedMachineInstanceId: String? = null
)

enum class KotlinTimelineReviewStatus {
    INVALID_REQUEST,
    STALE_HISTORY,
    INVALID_COMPLETION_ORDER,
    INCOMPLETE_COMPLETION_WINDOW,
    DUPLICATE_WORKOUT,
    WRONG_ATHLETE,
    UNVERIFIED_JOURNAL,
    NEEDS_TWO_RECORDED_WORKOUTS,
    LATEST_EXERCISE_NOT_COMPARABLE,
    PREVIOUS_EXERCISE_NOT_COMPARABLE,
    CHANGED_PROGRAM_OR_CATALOG,
    CHANGED_ATHLETE_PROFILE,
    CHANGED_GYM_OR_COACH_RULES,
    CHANGED_SESSION_PURPOSE,
    CHANGED_TRAINING_DAY,
    MIXED_PHYSICAL_MACHINE, DIFFERENT_PHYSICAL_MACHINE,
    CHANGED_INTERVENING_SOURCE,
    CHANGED_LOAD_BASIS,
    CHANGED_SET_PRESCRIPTION,
    VERIFIED_REVIEW
}

data class KotlinProgressionTimelineReview(
    val status: KotlinTimelineReviewStatus,
    /** The latest two exercise appearances, never two cherry-picked successes. */
    val comparedWorkoutIds: List<String>,
    val latestFactCount: Int,
    val progression: IncreaseReview?,
    val latestFatigue: SessionFatigueEvidence?
) {
    init {
        require((status == KotlinTimelineReviewStatus.VERIFIED_REVIEW) ==
            (progression != null && latestFatigue != null))
    }
    /** All decisions remain read-only until separate coach and owner gates. */
    val canIssueTargets: Boolean get() = false
    val canChangeProgram: Boolean get() = false
}

object KotlinProgressionTimelineDomain {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val zero = BigDecimal.ZERO
    private val maxStep = BigDecimal("100000")
    private const val MAX_COMPLETIONS = KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS

    fun review(request: KotlinProgressionTimelineRequest): KotlinProgressionTimelineReview {
        fun rejected(
            reason: KotlinTimelineReviewStatus,
            ids: List<String> = emptyList(),
            count: Int = 0
        ) = KotlinProgressionTimelineReview(reason, ids, count, null, null)

        if (!id.matches(request.athleteId) || !id.matches(request.exerciseId) ||
            request.historyRevision < 1 || request.currentHistoryRevision < 1 ||
            request.completedWorkouts.size !in 1..MAX_COMPLETIONS ||
            request.requestedPurpose==ComposerSessionPurpose.UNSPECIFIED ||
            (request.requestedMachineInstanceId!=null &&
                request.requestedMachineInstanceId!=KotlinMachineAlternativesDomain.LEGACY_MACHINE_ID &&
                !KotlinMachineAlternativesDomain.validInstanceId(request.requestedMachineInstanceId)) ||
            (request.requestedDayIndex!=null &&
                request.requestedDayIndex !in 0..6) ||
            request.maximumAuthorizedIncrease <= zero ||
            request.maximumAuthorizedIncrease > maxStep ||
            request.maximumAuthorizedIncrease.stripTrailingZeros().scale() > 3)
            return rejected(KotlinTimelineReviewStatus.INVALID_REQUEST)

        if (request.historyRevision != request.currentHistoryRevision)
            return rejected(KotlinTimelineReviewStatus.STALE_HISTORY)

        val sessions = request.completedWorkouts
        if (sessions.any { it.completionOrder < 1 } ||
            sessions.zipWithNext().any { (first, next) ->
                first.completionOrder >= next.completionOrder
            })
            return rejected(KotlinTimelineReviewStatus.INVALID_COMPLETION_ORDER)
        // Complete read windows must have contiguous, storage-authenticated
        // completion ordinals. Otherwise an unfavorable or skipped workout
        // could be silently omitted despite increasing order.
        // The native owner MUST also authenticate the first/last window
        // boundaries; this DTO cannot prove a missing prefix or tail.
        if (sessions.zipWithNext().any { (first, next) ->
                next.completionOrder != first.completionOrder+1L
            })
            return rejected(KotlinTimelineReviewStatus.INCOMPLETE_COMPLETION_WINDOW)

        if (sessions.map { it.journal.snapshot.workoutId }.distinct().size != sessions.size ||
            sessions.map { it.journal.storedIdentity.storageReceiptId }.distinct().size != sessions.size)
            return rejected(KotlinTimelineReviewStatus.DUPLICATE_WORKOUT)

        if (sessions.any { it.journal.snapshot.source.athleteId != request.athleteId })
            return rejected(KotlinTimelineReviewStatus.WRONG_ATHLETE)

        val projected = ArrayList<Pair<KotlinChronologicalWorkout, KotlinWorkoutHistoryEvidence>>()
        for (session in sessions) {
            val edits = session.savedCorrections
            if (edits != null && edits.originalFinishedJournal != session.journal)
                return rejected(KotlinTimelineReviewStatus.UNVERIFIED_JOURNAL)
            val candidate = if (edits == null)
                KotlinWorkoutHistoryEvidenceDomain.project(session.journal)
            else KotlinWorkoutHistoryEvidenceDomain.projectSaved(edits)
            if (candidate !is KotlinHistoryProjectionResult.Candidate)
                return rejected(KotlinTimelineReviewStatus.UNVERIFIED_JOURNAL)
            projected.add(session to candidate.evidence)
        }

        // Include every occurrence of the exercise, even an unsuccessful
        // workout. In particular, do not go around yesterday's failed sets
        // to cherry-pick two older ceiling performances.
        val appearances = projected.mapNotNull { (stored, history) ->
            val exercise = history.exerciseReviews.singleOrNull {
                it.exerciseId == request.exerciseId
            } ?: return@mapNotNull null
            Triple(stored, history, exercise)
        }
        // Preserve a fully authenticated replay of EVERY completed workout,
        // including those from other purposes. Only AFTER validating history
        // do we partition coaching evidence by the explicitly requested
        // session purpose AND the approved day index, when supplied. A Push
        // bench must not earn an Upper bench increase even if their purposes
        // match. Intervening sessions remain authenticated in full.
        // Importantly, an intervening BAD workout of the requested purpose
        // stays in the comparison: never cherry-pick two earlier successes.
        val dayApplicable=appearances.filter { (stored,_,_) ->
            (request.requestedPurpose==null ||
                stored.journal.snapshot.purpose==request.requestedPurpose) &&
            (request.requestedDayIndex==null ||
                stored.journal.snapshot.dayIndex==request.requestedDayIndex)
        }
        // Read set-level physical IDs only AFTER validating the COMPLETE
        // stored journal. A mixed A/B workout is never one comparable load
        // exposure. Other same-machine days are retained, but never borrowed.
        // Machine identity is a corrected historical FACT, not a frozen
        // original-journal receipt. A post-Finish correction may move a set
        // from an incorrectly logged physical A to its true physical B.
        // Projected evidence has already been owner-verified and replayed.
        fun physicalMachines(history:KotlinWorkoutHistoryEvidence):Set<String> =
            history.sets.filter {
                it.exerciseId==request.exerciseId && it.type==DraftSetType.WORKING
            }.mapNotNull {it.actual}.map {
                KotlinMachineAlternativesDomain.normalizeMachineId(it.machineInstanceId)
            }.toSet()
        val newestMachineIds=dayApplicable.lastOrNull()?.second?.let(::physicalMachines)
            .orEmpty()
        if(newestMachineIds.size>1 && request.requestedMachineInstanceId==null)
            return rejected(KotlinTimelineReviewStatus.MIXED_PHYSICAL_MACHINE)
        val machineId=request.requestedMachineInstanceId
            ?:newestMachineIds.singleOrNull()
            ?:KotlinMachineAlternativesDomain.LEGACY_MACHINE_ID
        val applicable=dayApplicable.filter { (_,history,_) ->
            physicalMachines(history)==setOf(machineId)
        }
        if (applicable.size < 2)
            return rejected(KotlinTimelineReviewStatus.NEEDS_TWO_RECORDED_WORKOUTS,
                applicable.map { it.second.workoutId },
                applicable.lastOrNull()?.third?.workingSets?.size ?: 0)

        val (olderStored, olderHistory, older) = applicable[applicable.size - 2]
        val (latestStored, latestHistory, latest) = applicable.last()
        val ids = listOf(olderHistory.workoutId, latestHistory.workoutId)
        val count = latest.workingSets.size
        if (latest.progressionGate != KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW)
            return rejected(KotlinTimelineReviewStatus.LATEST_EXERCISE_NOT_COMPARABLE, ids, count)
        if (older.progressionGate != KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW)
            return rejected(KotlinTimelineReviewStatus.PREVIOUS_EXERCISE_NOT_COMPARABLE, ids, count)

        val previousSource = olderHistory.source
        val newestSource = latestHistory.source
        // Never credit an old achievement toward a changed program revision:
        // even an unchanged set count and rep band can conceal amended
        // rest, exercise semantics, training dose or cycle intent.
        if (previousSource.programId != newestSource.programId ||
            previousSource.programRevision != newestSource.programRevision ||
            previousSource.catalogRevision != newestSource.catalogRevision)
            return rejected(KotlinTimelineReviewStatus.CHANGED_PROGRAM_OR_CATALOG, ids, count)
        // Athlete setup revisions may change recovery/experience or
        // relevant coaching constraints without changing the exercise ID.
        if (previousSource.athleteRevision != newestSource.athleteRevision)
            return rejected(KotlinTimelineReviewStatus.CHANGED_ATHLETE_PROFILE,
                ids, count)
        if (previousSource.gymId != newestSource.gymId ||
            previousSource.gymRevision != newestSource.gymRevision ||
            previousSource.coachingRulesRevision != newestSource.coachingRulesRevision)
            return rejected(KotlinTimelineReviewStatus.CHANGED_GYM_OR_COACH_RULES, ids, count)
        if (olderStored.journal.snapshot.purpose != latestStored.journal.snapshot.purpose)
            return rejected(KotlinTimelineReviewStatus.CHANGED_SESSION_PURPOSE, ids, count)
        // Never compare Upper and Push simply because both share the same
        // broad HYPERTROPHY session purpose. When the owner has not supplied
        // a day index, refuse the cross-day inference instead of declaring
        // a false weight increase. Changing the weekday under one unchanged
        // program revision is also not comparable.
        if (olderStored.journal.snapshot.dayIndex !=
                latestStored.journal.snapshot.dayIndex ||
            olderStored.journal.snapshot.calendarDay !=
                latestStored.journal.snapshot.calendarDay)
            return rejected(KotlinTimelineReviewStatus.CHANGED_TRAINING_DAY, ids, count)

        // Filtering by purpose cannot conceal a program, gym, athlete or
        // coaching-rules revision that occurred BETWEEN the matched
        // workouts. A later "return" to an old source is not continuity.
        val between=appearances.dropWhile {
            it.second.workoutId!=olderHistory.workoutId
        }.takeWhile {it.second.workoutId!=latestHistory.workoutId}
        if(between.any {it.second.source!=newestSource})
            return rejected(KotlinTimelineReviewStatus.CHANGED_INTERVENING_SOURCE,
                ids,count)

        val priorLoad = older.workingSets.first().issuedLoad as DraftSetLoad.Verified
        val latestLoad = latest.workingSets.first().issuedLoad as DraftSetLoad.Verified
        if (priorLoad.basis != latestLoad.basis ||
            priorLoad.unit != latestLoad.unit ||
            (request.currentVerifiedStock != null &&
                (request.currentVerifiedStock.basis != latestLoad.basis ||
                    request.currentVerifiedStock.unit != latestLoad.unit)))
            return rejected(KotlinTimelineReviewStatus.CHANGED_LOAD_BASIS, ids, count)

        val oldEvidence = checkNotNull(older.progressionWorkout)
        val latestEvidence = checkNotNull(latest.progressionWorkout)
        if (oldEvidence.targetReps != latestEvidence.targetReps ||
            oldEvidence.expectedWorkingSets != latestEvidence.expectedWorkingSets)
            return rejected(KotlinTimelineReviewStatus.CHANGED_SET_PRESCRIPTION, ids, count)

        // These are read-only coach diagnostics. Even a successful load
        // candidate has no authority to replace the next issued targets.
        val progression = ConservativeProgressionReview.assess(
            oldEvidence, latestEvidence, request.currentVerifiedStock,
            request.maximumAuthorizedIncrease
        )
        val fatigue = SessionFatigueEvidenceDomain.inspect(
            latestEvidence.targetReps, latestEvidence.expectedWorkingSets,
            latestEvidence.observations
        )
        return KotlinProgressionTimelineReview(
            KotlinTimelineReviewStatus.VERIFIED_REVIEW, ids, count,
            progression, fatigue
        )
    }
}
