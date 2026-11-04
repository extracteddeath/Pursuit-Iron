package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * A read-only bridge from a FINISHED, replay-verified Kotlin workout journal to
 * factual history and a conservative progression review.
 *
 * The native store must independently authenticate its persisted snapshot,
 * receipt stream and athlete ownership BEFORE invoking this projector. This
 * object neither commits history nor issues/changes future targets. It cannot
 * turn a proposed reducer result into a durable completion acknowledgment.
 */
data class KotlinHistoricalSetFact(
    val exerciseId: String,
    val setKey: String,
    val type: DraftSetType,
    val originalOrdinal: Int,
    val issuedLoad: DraftSetLoad,
    val issuedReps: RepBand,
    val issuedRir: Int?,
    val outcome: KotlinJournalSetOutcome,
    val actual: KotlinPerformedSet?,
    val effectiveEventId: String,
    /** The receipt being corrected, if the effective event was a correction. */
    val correctsEventId: String?
)

enum class KotlinHistoryProgressionGate {
    COMPARABLE_FOR_REVIEW,
    NO_WORKING_SETS,
    TOO_MANY_WORKING_SETS,
    DUPLICATE_OR_GAPPED_ORDINALS,
    UNWEIGHTED_BODYWEIGHT,
    MIXED_LOAD_BASIS,
    MIXED_REP_BANDS
}

data class KotlinExerciseHistoryEvidence(
    val exerciseId: String,
    val workingSets: List<KotlinHistoricalSetFact>,
    val progressionGate: KotlinHistoryProgressionGate,
    val progressionReview: ProgressionEvidence?,
    /** Factual, comparable session input for the separate longitudinal coach. */
    val progressionWorkout: ProgressionWorkoutEvidence?
) {
    init {
        require((progressionGate == KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW) ==
            (progressionReview != null && progressionWorkout != null))
    }
}

data class KotlinWorkoutHistoryEvidence(
    val source: KotlinTrainingSourceRevision,
    val workoutId: String,
    val finishEventId: String,
    val journalRevision: Long,
    /** Exact original issued order, including warmups and corrected facts. */
    val sets: List<KotlinHistoricalSetFact>,
    val exerciseReviews: List<KotlinExerciseHistoryEvidence>
) {
    /** A projection is never proof of a storage transaction or an issued plan. */
    val canPublishHistory: Boolean get() = false
    val canApplyProgression: Boolean get() = false
}

enum class KotlinHistoryProjectionRefusal {
    NOT_FINISHED, INVALID_JOURNAL
}

sealed interface KotlinHistoryProjectionResult {
    data class Rejected(val reason: KotlinHistoryProjectionRefusal) :
        KotlinHistoryProjectionResult
    data class Candidate(val evidence: KotlinWorkoutHistoryEvidence) :
        KotlinHistoryProjectionResult
}

object KotlinWorkoutHistoryEvidenceDomain {
    /**
     * Only replay-derived, effective receipt values may contribute to facts.
     * Older correction receipts stay in the journal audit, never double-counted
     * as a second set. No target RIR can become an observed actual RIR.
     */
    fun project(journal: KotlinWorkoutJournalState): KotlinHistoryProjectionResult {
        val finishId = journal.finishedByEventId
            ?: return KotlinHistoryProjectionResult.Rejected(
                KotlinHistoryProjectionRefusal.NOT_FINISHED)
        val verified = KotlinWorkoutJournalReplayDomain.verifyStoredState(journal)
        if (verified !is KotlinJournalReplayResult.Recovered)
            return KotlinHistoryProjectionResult.Rejected(
                KotlinHistoryProjectionRefusal.INVALID_JOURNAL)
        val facts = journal.snapshot.targets.map { target ->
            val receipt = checkNotNull(verified.journal.effectiveSets[target.setKey])
            val record = receipt.operation as KotlinJournalOperation.RecordSet
            KotlinHistoricalSetFact(
                target.exerciseId, target.setKey, target.type, target.ordinal,
                target.load, target.reps, target.targetRir, record.outcome,
                record.performed, record.eventId, record.correctsEventId
            )
        }
        val grouped = linkedMapOf<String, MutableList<KotlinHistoricalSetFact>>()
        for (fact in facts) {
            grouped.getOrPut(fact.exerciseId) { mutableListOf() }
            if (fact.type == DraftSetType.WORKING)
                grouped.getValue(fact.exerciseId).add(fact)
        }
        val reviews = grouped.map { (exerciseId, working) ->
            val gate = when {
                working.isEmpty() -> KotlinHistoryProgressionGate.NO_WORKING_SETS
                working.size > 32 -> KotlinHistoryProgressionGate.TOO_MANY_WORKING_SETS
                working.map { it.originalOrdinal }.sorted() !=
                    (0 until working.size).toList() ->
                    KotlinHistoryProgressionGate.DUPLICATE_OR_GAPPED_ORDINALS
                working.any { it.issuedLoad !is DraftSetLoad.Verified } ->
                    KotlinHistoryProgressionGate.UNWEIGHTED_BODYWEIGHT
                working.map {
                    val load = it.issuedLoad as DraftSetLoad.Verified
                    load.unit to load.basis
                }.distinct().size != 1 -> KotlinHistoryProgressionGate.MIXED_LOAD_BASIS
                working.map { it.issuedReps }.distinct().size != 1 ->
                    KotlinHistoryProgressionGate.MIXED_REP_BANDS
                else -> KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW
            }
            val workout = if (gate == KotlinHistoryProgressionGate.COMPARABLE_FOR_REVIEW) {
                val ordered = working.sortedBy { it.originalOrdinal }
                val unit = (ordered.first().issuedLoad as DraftSetLoad.Verified).unit
                ProgressionWorkoutEvidence(
                    journal.snapshot.workoutId, exerciseId, unit,
                    ordered.first().issuedReps, ordered.size,
                    Collections.unmodifiableList(ordered.map { fact ->
                        val issued = fact.issuedLoad as DraftSetLoad.Verified
                        WorkingSetFact(
                            fact.originalOrdinal,
                            when (fact.outcome) {
                                KotlinJournalSetOutcome.COMPLETED -> SetOutcome.COMPLETED
                                KotlinJournalSetOutcome.SKIPPED -> SetOutcome.SKIPPED
                                KotlinJournalSetOutcome.FAILED_ATTEMPT -> SetOutcome.FAILED_ATTEMPT
                            },
                            fact.actual?.load, fact.actual?.reps, fact.actual?.actualRir,
                            issued.value, fact.issuedRir
                        )
                    })
                )
            } else null
            val progression = workout?.let {
                DoubleProgressionEvidenceDomain.assess(
                    it.targetReps, it.expectedWorkingSets, it.observations)
            }
            KotlinExerciseHistoryEvidence(exerciseId,
                Collections.unmodifiableList(working.toList()), gate, progression, workout)
        }
        return KotlinHistoryProjectionResult.Candidate(KotlinWorkoutHistoryEvidence(
            journal.snapshot.source, journal.snapshot.workoutId, finishId,
            journal.revision, Collections.unmodifiableList(facts),
            Collections.unmodifiableList(reviews)
        ))
    }
}
