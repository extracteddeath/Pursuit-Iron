package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * A candidate bridge between evidence-based coaching and a fully reviewed,
 * exact-load program. It NEVER saves or issues this proposed program.
 *
 * Current ComposerExerciseOption loading is program-wide, not per visit.
 * A change here affects EVERY visit of this exact exercise. The affected
 * calendar days are exposed for explicit review, never hidden in one set.
 * A future native owner must authenticate every historical journal, its
 * corrections, the athlete and current revisions, collect explicit athlete
 * acceptance, then issue a NEW program revision/grant through native storage.
 * This DTO is not a substitute for that transaction.
 */
enum class CoachNextLoadReviewStatus {
    STALE_ATHLETE_OR_PROGRAM,
    STALE_HISTORY,
    RESTRICTION_OR_LOW_READINESS,
    EXERCISE_NOT_IN_PROGRAM,
    NO_COMPARABLE_PLANNED_VISIT,
    ONLY_STRAIGHT_SETS_SUPPORTED,
    LOAD_BASIS_UNVERIFIED,
    HISTORY_DOES_NOT_MATCH_PLANNED_LOAD,
    MORE_EVIDENCE_REQUIRED,
    NO_SAFE_CHANGE,
    FULL_WEEK_REAUDIT_FAILED,
    READY_FOR_ATHLETE_REVIEW
}

enum class CoachNextLoadChangeKind {
    EARNED_INCREMENT,
    RETURN_TO_DEMONSTRATED_LOAD
}

data class CoachNextProgramLoadRequest(
    val approvedProgram: ComposerRequest,
    val source: KotlinTrainingSourceRevision,
    val currentSource: KotlinTrainingSourceRevision,
    val historyRevision: Long,
    val currentHistoryRevision: Long,
    /** Two MOST RECENT exercise appearances; caller must prove full history. */
    val previous: ProgressionWorkoutEvidence,
    val latest: ProgressionWorkoutEvidence,
    val exerciseId: String,
    val policies: List<ExercisePrescriptionPolicy>,
    val maximumAuthorizedIncrease: BigDecimal,
    val readiness: CoachReadiness,
    val movementRestricted: Boolean
)

data class CoachNextProgramLoadReview(
    val status: CoachNextLoadReviewStatus,
    val kind: CoachNextLoadChangeKind?,
    val originalLoad: BigDecimal?,
    val candidateLoad: BigDecimal?,
    val affectedDayIndices: List<Int>,
    val candidateProgram: ComposerRequest?,
    val explanation: String
) {
    init {
        require(explanation.isNotBlank())
        require((status == CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW) ==
            (kind != null && originalLoad != null && candidateLoad != null &&
                candidateProgram != null && affectedDayIndices.isNotEmpty()))
    }
    val maySaveProgram: Boolean get() = false
    val mayIssueWorkout: Boolean get() = false
    val mayModifyHistory: Boolean get() = false
}

object CoachNextProgramLoadReviewDomain {
    private val zero = BigDecimal.ZERO
    private val maximumStep = BigDecimal("100000")
    private fun normalized(v: BigDecimal) =
        BigDecimal(v.stripTrailingZeros().toPlainString())
    private fun same(a: BigDecimal, b: BigDecimal) = a.compareTo(b) == 0

    fun review(request: CoachNextProgramLoadRequest): CoachNextProgramLoadReview {
        fun denied(status: CoachNextLoadReviewStatus, explanation: String) =
            CoachNextProgramLoadReview(
                status, null, null, null, emptyList(), null, explanation)
        if (request.source != request.currentSource ||
            request.source.athleteId.isBlank() ||
            request.source.programId.isBlank() ||
            request.source.programRevision < 1)
            return denied(CoachNextLoadReviewStatus.STALE_ATHLETE_OR_PROGRAM,
                "The athlete or program revision changed. Reopen the current approved plan.")
        if (request.historyRevision < 1 ||
            request.historyRevision != request.currentHistoryRevision)
            return denied(CoachNextLoadReviewStatus.STALE_HISTORY,
                "Workout history changed. Review the current saved observations first.")
        if (request.movementRestricted || request.readiness == CoachReadiness.LOW)
            return denied(CoachNextLoadReviewStatus.RESTRICTION_OR_LOW_READINESS,
                "Review recovery or the movement restriction before changing the load.")
        if (request.maximumAuthorizedIncrease <= zero ||
            request.maximumAuthorizedIncrease > maximumStep ||
            request.maximumAuthorizedIncrease.stripTrailingZeros().scale() > 3)
            return denied(CoachNextLoadReviewStatus.NO_SAFE_CHANGE,
                "The athlete's permitted increase is missing or invalid.")
        val option = request.approvedProgram.exercises.singleOrNull {
            it.exerciseId == request.exerciseId
        } ?: return denied(CoachNextLoadReviewStatus.EXERCISE_NOT_IN_PROGRAM,
            "This movement is not part of the currently selected program.")
        val equipment = option.equipment as? ComposerEquipment.Verified
            ?: return denied(CoachNextLoadReviewStatus.LOAD_BASIS_UNVERIFIED,
                "Bodyweight or assisted loading needs a separate exercise-specific policy.")
        val stock = equipment.inventory
        if (stock == null || stock.unit != equipment.unit)
            return denied(CoachNextLoadReviewStatus.LOAD_BASIS_UNVERIFIED,
                "The exact gym load and equipment basis have not been confirmed.")
        val currentInspection = EquipmentLoadDomain.inspect(
            stock, equipment.plannedLoad, equipment.unit)
        if (currentInspection !is LoadInspection.Confirmed || !currentInspection.exact)
            return denied(CoachNextLoadReviewStatus.LOAD_BASIS_UNVERIFIED,
                "The current prescribed weight cannot be built with verified equipment.")
        val plan = runCatching {
            CoachSessionPrescriptionDomain.draft(
                request.approvedProgram, request.policies)
        }.getOrNull()
        if (plan?.status != PrescriptionDraftStatus.COMPLETE_FOR_REVIEW)
            return denied(CoachNextLoadReviewStatus.NO_COMPARABLE_PLANNED_VISIT,
                "The original complete week must be resolved before changing a load.")
        val affected = plan.sessions.filter { session ->
            session.exercises.any { it.exerciseId == request.exerciseId }
        }.map { it.dayIndex }
        if (affected.isEmpty())
            return denied(CoachNextLoadReviewStatus.NO_COMPARABLE_PLANNED_VISIT,
                "No approved training day contains this exercise.")
        if (request.policies.filter { it.exerciseId == request.exerciseId }
                .any { it.scheme != SetLoadScheme.STRAIGHT ||
                    it.warmups.isNotEmpty() })
            return denied(CoachNextLoadReviewStatus.ONLY_STRAIGHT_SETS_SUPPORTED,
                "A custom warmup or top/backoff load needs an individual prescription review.")

        val prior = request.previous
        val latest = request.latest
        if (prior.workoutId.isBlank() || latest.workoutId.isBlank() ||
            prior.workoutId == latest.workoutId ||
            prior.exerciseId != request.exerciseId ||
            latest.exerciseId != request.exerciseId ||
            prior.unit != equipment.unit || latest.unit != equipment.unit ||
            prior.targetReps != latest.targetReps ||
            prior.expectedWorkingSets != latest.expectedWorkingSets)
            return denied(CoachNextLoadReviewStatus.MORE_EVIDENCE_REQUIRED,
                "The last two exercise appearances are not safely comparable.")
        val programBands = plan.sessions.flatMap { it.exercises }.filter {
            it.exerciseId == request.exerciseId
        }.flatMap { it.sets }.filter { it.type == DraftSetType.WORKING }
            .map { it.targetReps }.distinct()
        val programSetCounts = plan.sessions.flatMap { it.exercises }.filter {
            it.exerciseId == request.exerciseId
        }.map { ex -> ex.sets.count { it.type == DraftSetType.WORKING } }.distinct()
        if (programBands.size != 1 || programBands.single() != latest.targetReps ||
            programSetCounts.size != 1 ||
            programSetCounts.single() != latest.expectedWorkingSets)
            return denied(CoachNextLoadReviewStatus.MORE_EVIDENCE_REQUIRED,
                "The authored rep ranges or working-set counts changed; historical targets cannot be reused.")

        val increase = runCatching {
            ConservativeProgressionReview.assess(
                prior, latest, stock, request.maximumAuthorizedIncrease)
        }.getOrNull()
            ?: return denied(CoachNextLoadReviewStatus.MORE_EVIDENCE_REQUIRED,
                "Prior set receipts are invalid or incomplete.")
        val recovery = runCatching {
            OverloadRecoveryLoadReviewDomain.assess(
                prior, latest, stock, stock.basis)
        }.getOrNull()
            ?: return denied(CoachNextLoadReviewStatus.MORE_EVIDENCE_REQUIRED,
                "The previous load cannot be established from comparable observations.")
        val candidate: BigDecimal
        val kind: CoachNextLoadChangeKind
        if (increase.canOfferIncrease &&
            increase.currentObservedLoad != null &&
            same(equipment.plannedLoad, increase.currentObservedLoad)) {
            candidate = checkNotNull(increase.reviewCandidate)
            kind = CoachNextLoadChangeKind.EARNED_INCREMENT
        } else if (recovery.canOfferForReview &&
            recovery.heaviestAttemptedLoad != null &&
            same(equipment.plannedLoad, recovery.heaviestAttemptedLoad)) {
            candidate = checkNotNull(recovery.candidateReturnLoad)
            kind = CoachNextLoadChangeKind.RETURN_TO_DEMONSTRATED_LOAD
        } else {
            return denied(CoachNextLoadReviewStatus.HISTORY_DOES_NOT_MATCH_PLANNED_LOAD,
                "No earned change matches the current plan. Preserve the athlete's existing target.")
        }
        val attainable = EquipmentLoadDomain.inspect(stock, candidate, equipment.unit)
        if (attainable !is LoadInspection.Confirmed || !attainable.exact ||
            same(candidate, equipment.plannedLoad))
            return denied(CoachNextLoadReviewStatus.NO_SAFE_CHANGE,
                "The reviewed change is not an available, distinct physical load.")
        val modified = request.approvedProgram.copy(exercises =
            Collections.unmodifiableList(request.approvedProgram.exercises.map {
                if (it.exerciseId == request.exerciseId)
                    it.copy(equipment = equipment.copy(plannedLoad = normalized(candidate)))
                else it
            }))
        val checked = runCatching {
            CoachSessionPrescriptionDomain.draft(modified, request.policies)
        }.getOrNull()
        if (checked?.status != PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            checked.sessions.map { it.dayIndex } != plan.sessions.map { it.dayIndex } ||
            checked.sessions.map { it.exercises.map { ex -> ex.exerciseId } } !=
                plan.sessions.map { it.exercises.map { ex -> ex.exerciseId } })
            return denied(CoachNextLoadReviewStatus.FULL_WEEK_REAUDIT_FAILED,
                "The whole proposed week could not be independently verified. Keep the saved plan.")
        val actualAffected = checked.sessions.filter { session ->
            session.exercises.any { it.exerciseId == request.exerciseId }
        }.map { it.dayIndex }
        if (actualAffected != affected)
            return denied(CoachNextLoadReviewStatus.FULL_WEEK_REAUDIT_FAILED,
                "The exercise schedule changed during review.")

        return CoachNextProgramLoadReview(
            CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW, kind,
            normalized(equipment.plannedLoad), normalized(candidate),
            Collections.unmodifiableList(affected),
            modified,
            if (kind == CoachNextLoadChangeKind.EARNED_INCREMENT)
                "Two complete comparable workouts earned the next physically verified load. Review every affected training day before saving a new program revision."
            else "The overloaded session did not demonstrate the heaviest attempted weight across all sets. Review a return to the previously demonstrated load across the affected days."
        )
    }
}
