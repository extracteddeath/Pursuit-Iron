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
    val maximumAuthorizedIncrease: BigDecimal
)

enum class KotlinTimelineReviewStatus {
    INVALID_REQUEST,
    STALE_HISTORY,
    INVALID_COMPLETION_ORDER,
    DUPLICATE_WORKOUT,
    WRONG_ATHLETE,
    UNVERIFIED_JOURNAL,
    NEEDS_TWO_RECORDED_WORKOUTS,
    LATEST_EXERCISE_NOT_COMPARABLE,
    PREVIOUS_EXERCISE_NOT_COMPARABLE,
    CHANGED_PROGRAM_OR_CATALOG,
    CHANGED_GYM_OR_COACH_RULES,
    CHANGED_SESSION_PURPOSE,
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
        if (appearances.size < 2)
            return rejected(KotlinTimelineReviewStatus.NEEDS_TWO_RECORDED_WORKOUTS,
                appearances.map { it.second.workoutId },
                appearances.lastOrNull()?.third?.workingSets?.size ?: 0)

        val (olderStored, olderHistory, older) = appearances[appearances.size - 2]
        val (latestStored, latestHistory, latest) = appearances.last()
        val ids = listOf(olderHistory.workoutId, latestHistory.workoutId)
        val count = latest.workingSets.size
        if (latest.progressionGate != KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW)
            return rejected(KotlinTimelineReviewStatus.LATEST_EXERCISE_NOT_COMPARABLE, ids, count)
        if (older.progressionGate != KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW)
            return rejected(KotlinTimelineReviewStatus.PREVIOUS_EXERCISE_NOT_COMPARABLE, ids, count)

        val previousSource = olderHistory.source
        val newestSource = latestHistory.source
        if (previousSource.programId != newestSource.programId ||
            previousSource.catalogRevision != newestSource.catalogRevision)
            return rejected(KotlinTimelineReviewStatus.CHANGED_PROGRAM_OR_CATALOG, ids, count)
        if (previousSource.gymId != newestSource.gymId ||
            previousSource.gymRevision != newestSource.gymRevision ||
            previousSource.coachingRulesRevision != newestSource.coachingRulesRevision)
            return rejected(KotlinTimelineReviewStatus.CHANGED_GYM_OR_COACH_RULES, ids, count)
        if (olderStored.journal.snapshot.purpose != latestStored.journal.snapshot.purpose)
            return rejected(KotlinTimelineReviewStatus.CHANGED_SESSION_PURPOSE, ids, count)

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
