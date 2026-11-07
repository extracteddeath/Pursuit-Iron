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
    PURPOSE_SPECIFIC_LOADING_REQUIRES_SEPARATE_REVIEW,
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
    val movementRestricted: Boolean,
    /** Null keeps legacy program-wide behavior; explicit purpose changes only
     * that verified policy, never another session-purpose working load. */
    val requestedPurpose: ComposerSessionPurpose? = null,
    /** Null retains prior exercise/purpose policy; explicit index isolates
     * one scheduled session from another of the same purpose. */
    val requestedDayIndex: Int? = null
)

data class CoachNextProgramLoadReview(
    val status: CoachNextLoadReviewStatus,
    val kind: CoachNextLoadChangeKind?,
    val originalLoad: BigDecimal?,
    val candidateLoad: BigDecimal?,
    val affectedDayIndices: List<Int>,
    val candidateProgram: ComposerRequest?,
    val explanation: String,
    /** Entire verified policy list for the proposed revision, when scoped. */
    val candidatePolicies: List<ExercisePrescriptionPolicy>? = null
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
        val purpose=request.requestedPurpose
        if(purpose==ComposerSessionPurpose.UNSPECIFIED ||
            (request.requestedDayIndex!=null &&
                (purpose==null ||
                    request.requestedDayIndex !in request.approvedProgram.days.indices)))
            return denied(CoachNextLoadReviewStatus.NO_COMPARABLE_PLANNED_VISIT,
                "The requested training purpose or day slot is not valid.")
        val day=request.requestedDayIndex
        val specific=if(purpose==null)null else if(day==null)
            request.policies.singleOrNull {
                it.exerciseId==request.exerciseId && it.purpose==purpose &&
                    it.dayIndex==null
            } else CoachSessionPrescriptionDomain.selectedPolicy(
                request.policies,request.exerciseId,purpose,day)
        if(purpose!=null && specific==null)
            return denied(
                CoachNextLoadReviewStatus.PURPOSE_SPECIFIC_LOADING_REQUIRES_SEPARATE_REVIEW,
                "The selected purpose has no unique approved prescription.")
        // Other purposes may override their loads while this one still uses
        // the original exact verified default. Treat both as independent.
        val workingLoad=specific?.purposeWorkingLoad ?:equipment.plannedLoad
        val currentInspection = EquipmentLoadDomain.inspect(
            stock, workingLoad, equipment.unit)
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
            (purpose==null || session.purpose==purpose) &&
                (day==null || session.dayIndex==day) &&
                session.exercises.any { it.exerciseId == request.exerciseId }
        }.map { it.dayIndex }
        if (affected.isEmpty())
            return denied(CoachNextLoadReviewStatus.NO_COMPARABLE_PLANNED_VISIT,
                "No approved training day contains this exercise.")
        // A shared-purpose policy MUST NOT claim to change all matching
        // training days when any of them has a more specific day-scoped
        // prescription. The day override wins during issuance, so a broad
        // update could falsely report that this day received a new load.
        // Explicitly review that day instead; Upper and Push stay independent.
        if(purpose!=null && day==null && request.policies.any { policy ->
                policy.exerciseId==request.exerciseId &&
                    policy.purpose==purpose &&
                    policy.dayIndex!=null &&
                    policy.dayIndex in affected
            })
            return denied(
                CoachNextLoadReviewStatus.PURPOSE_SPECIFIC_LOADING_REQUIRES_SEPARATE_REVIEW,
                "One or more training days have their own approved prescription. Review each day-specific load separately; a shared-purpose change cannot override or misreport those targets.")
        if (purpose==null && request.policies.any {
                it.exerciseId == request.exerciseId && it.purposeWorkingLoad != null
            })
            return denied(
                CoachNextLoadReviewStatus.PURPOSE_SPECIFIC_LOADING_REQUIRES_SEPARATE_REVIEW,
                "This exercise uses purpose-specific working loads. Review each purpose and its saved history independently; a program-wide increase is unsafe.")
        if (request.policies.filter { it.exerciseId == request.exerciseId &&
                (purpose==null || it.purpose==purpose) &&
                (day==null || it==specific) }
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
        val relevant=plan.sessions.filter {
            (purpose==null || it.purpose==purpose) &&
                (day==null || it.dayIndex==day)
        }
        val programBands = relevant.flatMap { it.exercises }.filter {
            it.exerciseId == request.exerciseId
        }.flatMap { it.sets }.filter { it.type == DraftSetType.WORKING }
            .map { it.targetReps }.distinct()
        val programSetCounts = relevant.flatMap { it.exercises }.filter {
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
            same(workingLoad, increase.currentObservedLoad)) {
            candidate = checkNotNull(increase.reviewCandidate)
            kind = CoachNextLoadChangeKind.EARNED_INCREMENT
        } else if (recovery.canOfferForReview &&
            recovery.heaviestAttemptedLoad != null &&
            same(workingLoad, recovery.heaviestAttemptedLoad)) {
            candidate = checkNotNull(recovery.candidateReturnLoad)
            kind = CoachNextLoadChangeKind.RETURN_TO_DEMONSTRATED_LOAD
        } else {
            return denied(CoachNextLoadReviewStatus.HISTORY_DOES_NOT_MATCH_PLANNED_LOAD,
                "No earned change matches the current plan. Preserve the athlete's existing target.")
        }
        val attainable = EquipmentLoadDomain.inspect(stock, candidate, equipment.unit)
        if (attainable !is LoadInspection.Confirmed || !attainable.exact ||
            same(candidate, workingLoad))
            return denied(CoachNextLoadReviewStatus.NO_SAFE_CHANGE,
                "The reviewed change is not an available, distinct physical load.")
        val modified = if(purpose==null)
            request.approvedProgram.copy(exercises =
                Collections.unmodifiableList(request.approvedProgram.exercises.map {
                    if(it.exerciseId==request.exerciseId)
                        it.copy(equipment=equipment.copy(plannedLoad=normalized(candidate)))
                    else it
                })) else request.approvedProgram
        val modifiedPolicies=if(purpose==null)request.policies
            else if(day==null)Collections.unmodifiableList(request.policies.map {
                if(it.exerciseId==request.exerciseId && it.purpose==purpose &&
                    it.dayIndex==null)
                    it.copy(purposeWorkingLoad=normalized(candidate))
                else it
            }) else {
                val update=checkNotNull(specific).copy(
                    dayIndex=day,purposeWorkingLoad=normalized(candidate))
                Collections.unmodifiableList(
                    request.policies.filterNot {it.exerciseId==request.exerciseId &&
                        it.purpose==purpose && it.dayIndex==day}+update)
            }
        val checked = runCatching {
            CoachSessionPrescriptionDomain.draft(modified, modifiedPolicies)
        }.getOrNull()
        if (checked?.status != PrescriptionDraftStatus.COMPLETE_FOR_REVIEW ||
            checked.sessions.map { it.dayIndex } != plan.sessions.map { it.dayIndex } ||
            checked.sessions.map { it.exercises.map { ex -> ex.exerciseId } } !=
                plan.sessions.map { it.exercises.map { ex -> ex.exerciseId } })
            return denied(CoachNextLoadReviewStatus.FULL_WEEK_REAUDIT_FAILED,
                "The whole proposed week could not be independently verified. Keep the saved plan.")
        val actualAffected=checked.sessions.filter { session ->
            (purpose==null || session.purpose==purpose) &&
                (day==null || session.dayIndex==day) &&
                session.exercises.any {it.exerciseId==request.exerciseId}
        }.map {it.dayIndex}
        if (actualAffected != affected ||
            (purpose!=null && plan.sessions.zip(checked.sessions).any { (before,after) ->
                (before.purpose!=purpose ||
                    (day!=null && before.dayIndex!=day)) && before!=after
            }))
            return denied(CoachNextLoadReviewStatus.FULL_WEEK_REAUDIT_FAILED,
                "The exercise schedule changed during review.")

        val assumed=if(kind==CoachNextLoadChangeKind.EARNED_INCREMENT)
            increase.assumedGoalRirSetCount else recovery.assumedGoalRirSetCount
        return CoachNextProgramLoadReview(
            CoachNextLoadReviewStatus.READY_FOR_ATHLETE_REVIEW, kind,
            normalized(workingLoad), normalized(candidate),
            Collections.unmodifiableList(affected),
            modified,
            (if (kind == CoachNextLoadChangeKind.EARNED_INCREMENT)
                "Two complete comparable workouts earned the next physically verified load. Review every affected training day before saving a new program revision."
            else "The overloaded session did not demonstrate the heaviest attempted weight across all sets. Review a return to the previously demonstrated load across the affected days.")+
                ProgressionEffortDomain.assumptionNote(assumed),
            if(purpose==null)null else modifiedPolicies
        )
    }
}
