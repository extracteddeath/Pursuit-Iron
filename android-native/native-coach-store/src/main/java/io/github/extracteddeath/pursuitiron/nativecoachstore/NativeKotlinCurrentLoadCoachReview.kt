package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.Collections

/**
 * Read-only history-to-coach bridge. The SOURCE of this input must be the
 * full, owner-fenced NativeKotlinMixedHistoryReadCoordinator.readFenced
 * result, not a UI-selected pair of workouts or cached Last values.
 *
 * The native coordinator holds the same owner transaction fence while
 * reading the current approved program and the exhaustive history ledger.
 * This domain makes no claims that its publicly constructible inputs
 * themselves authenticate disk provenance.
 */
enum class NativeCurrentLoadReviewRefusal {
    INVALID_REQUEST,
    SOURCE_NOT_CURRENT,
    HISTORY_NOT_CURRENT,
    INVALID_HISTORY,
    NOT_ENOUGH_EXERCISE_HISTORY,
    CUSTOM_OR_UNWEIGHTED_INTERVENING_VISIT,
    INCOMPARABLE_WORKOUT,
    HISTORICAL_SOURCE_CHANGED,
    PURPOSE_CHANGED
}

sealed interface NativeCurrentLoadReviewResult {
    data class Rejected(val reason: NativeCurrentLoadReviewRefusal) :
        NativeCurrentLoadReviewResult
    data class Reviewed(
        val historyRevision: Long,
        val comparedWorkoutIds: List<String>,
        val coaching: CoachNextProgramLoadReview
    ) : NativeCurrentLoadReviewResult {
        /** The coaching result is a proposal, not a durable program change. */
        val mayApply: Boolean get() = false
        val mayIssueWorkout: Boolean get() = false
    }
}

object NativeKotlinCurrentLoadCoachReview {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_REV = 9_007_199_254_740_991L

    fun inspect(
        approval: NativeKotlinWorkoutApproval,
        timeline: NativeKotlinMixedHistoryTimeline,
        exerciseId: String,
        expectedHistoryRevision: Long,
        maximumAuthorizedIncrease: BigDecimal,
        readiness: CoachReadiness,
        movementRestricted: Boolean
    ): NativeCurrentLoadReviewResult {
        fun refuse(reason: NativeCurrentLoadReviewRefusal) =
            NativeCurrentLoadReviewResult.Rejected(reason)

        if (!id.matches(exerciseId) ||
            !id.matches(approval.athleteId) ||
            expectedHistoryRevision !in 1..MAX_REV ||
            maximumAuthorizedIncrease <= BigDecimal.ZERO ||
            maximumAuthorizedIncrease > BigDecimal("100000") ||
            maximumAuthorizedIncrease.stripTrailingZeros().scale() > 3)
            return refuse(NativeCurrentLoadReviewRefusal.INVALID_REQUEST)

        val source = approval.approvedSource
        if (approval.athleteId != source.athleteId ||
            approval.currentSource != source ||
            approval.grant.approvedSource != source ||
            approval.storageRevision < 1 ||
            source.programRevision < 1 ||
            source.gymRevision < 1 ||
            source.athleteRevision < 1)
            return refuse(NativeCurrentLoadReviewRefusal.SOURCE_NOT_CURRENT)

        if (timeline.athleteId != source.athleteId ||
            timeline.manifestRevision != expectedHistoryRevision)
            return refuse(NativeCurrentLoadReviewRefusal.HISTORY_NOT_CURRENT)
        if (timeline.completions.size > KotlinHistoryCapacityPolicy.MAX_MANIFEST_COMPLETIONS ||
            timeline.completions.map { it.manifest.completionOrder } !=
                (1..timeline.completions.size).map { it.toLong() } ||
            timeline.completions.map { it.manifest.workoutId }.distinct().size !=
                timeline.completions.size ||
            timeline.completions.map { it.manifest.storedReceiptId }.distinct().size !=
                timeline.completions.size)
            return refuse(NativeCurrentLoadReviewRefusal.INVALID_HISTORY)

        // The actual two LATEST exercise appearances must be considered.
        // An intervening custom visit, skipped/failed work, or newer exercise
        // with no comparable working sets may never be bypassed in search of
        // two older, successful sessions.
        val appearances = timeline.completions.filter { completion ->
            when(completion) {
                is NativeKotlinMixedHistoryCompletion.Composer ->
                    completion.evidence.sets.any { it.exerciseId == exerciseId }
                is NativeKotlinMixedHistoryCompletion.Custom ->
                    completion.evidence.visits.any { it.exerciseId == exerciseId }
            }
        }
        if (appearances.size < 2)
            return refuse(NativeCurrentLoadReviewRefusal.NOT_ENOUGH_EXERCISE_HISTORY)
        val lastTwo = appearances.takeLast(2)
        if (lastTwo.any { it !is NativeKotlinMixedHistoryCompletion.Composer })
            return refuse(NativeCurrentLoadReviewRefusal.CUSTOM_OR_UNWEIGHTED_INTERVENING_VISIT)
        val previous = lastTwo[0] as NativeKotlinMixedHistoryCompletion.Composer
        val latest = lastTwo[1] as NativeKotlinMixedHistoryCompletion.Composer
        if (previous.evidence.workoutId != previous.manifest.workoutId ||
            latest.evidence.workoutId != latest.manifest.workoutId ||
            previous.evidence.journalRevision != previous.manifest.originalJournalRevision ||
            latest.evidence.journalRevision != latest.manifest.originalJournalRevision ||
            previous.evidence.finishEventId != previous.manifest.finishEventId ||
            latest.evidence.finishEventId != latest.manifest.finishEventId)
            return refuse(NativeCurrentLoadReviewRefusal.INVALID_HISTORY)
        if (previous.evidence.source != source || latest.evidence.source != source)
            return refuse(NativeCurrentLoadReviewRefusal.HISTORICAL_SOURCE_CHANGED)
        if (previous.purpose == ComposerSessionPurpose.UNSPECIFIED ||
            previous.purpose != latest.purpose)
            return refuse(NativeCurrentLoadReviewRefusal.PURPOSE_CHANGED)
        val older = previous.evidence.exerciseReviews.singleOrNull {
            it.exerciseId == exerciseId
        }
        val newer = latest.evidence.exerciseReviews.singleOrNull {
            it.exerciseId == exerciseId
        }
        if (older == null || newer == null)
            return refuse(NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT)
        val matchingEquipment = approval.program.exercises.singleOrNull {
            it.exerciseId == exerciseId
        }?.equipment as? ComposerEquipment.Verified
            ?:return refuse(NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT)
        val stock=matchingEquipment.inventory
            ?:return refuse(NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT)
        val policy=approval.policies.singleOrNull {
            it.exerciseId==exerciseId && it.purpose==latest.purpose
        } ?:return refuse(NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT)
        // Do not reinterpret authored top/backoff or warmup prescriptions as
        // individualized STRAIGHT sets. Only the approved rep envelope is
        // normalized, never a single historical issued target or actual fact.
        if(policy.scheme!=SetLoadScheme.STRAIGHT ||
            policy.warmups.isNotEmpty() ||
            listOf(previous,latest).any { session ->
                session.evidence.sets.any {
                    it.exerciseId==exerciseId && it.type==DraftSetType.WARMUP
                }
            })
            return refuse(NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT)
        fun normalized(
            session:NativeKotlinMixedHistoryCompletion.Composer,
            exercise:KotlinExerciseHistoryEvidence
        ):ProgressionWorkoutEvidence? {
            // Recovery from an overloaded attempt MUST compare the prior
            // independently demonstrated lighter load, not assume both
            // sessions used today's programmed heavy load. The normalizer
            // still requires one uniform ISSUED load per historical visit,
            // exact current stock, the right unit/basis and full set order.
            val issued=exercise.workingSets.firstOrNull()?.issuedLoad
                as? DraftSetLoad.Verified ?:return null
            val compared=KotlinLongitudinalRepEvidenceDomain.review(
                exercise,session.evidence.workoutId,policy.workingReps,
                exercise.workingSets.size,issued.value,
                stock,matchingEquipment.unit,stock.basis,policy.targetRir)
            return (compared as? LongitudinalRepEvidenceResult.Comparable)
                ?.session
        }
        val olderFacts=normalized(previous,older)
            ?:return refuse(NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT)
        val newerFacts=normalized(latest,newer)
            ?:return refuse(NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT)
        if (olderFacts.targetReps!=newerFacts.targetReps ||
            olderFacts.expectedWorkingSets!=newerFacts.expectedWorkingSets ||
            olderFacts.unit!=newerFacts.unit)
            return refuse(NativeCurrentLoadReviewRefusal.INCOMPARABLE_WORKOUT)

        // Retain all approved exercise variants, policies and pinned choices.
        // The reviewed candidate can NEVER write until a distinct native
        // approval transaction rechecks current history and source revisions.
        val reviewed = CoachNextProgramLoadReviewDomain.review(
            CoachNextProgramLoadRequest(
                approval.program,source,approval.currentSource,
                timeline.manifestRevision,expectedHistoryRevision,
                olderFacts,newerFacts,exerciseId,approval.policies,
                maximumAuthorizedIncrease,readiness,movementRestricted
            )
        )
        return NativeCurrentLoadReviewResult.Reviewed(
            timeline.manifestRevision,
            Collections.unmodifiableList(lastTwo.map { it.manifest.workoutId }),
            reviewed
        )
    }
}
