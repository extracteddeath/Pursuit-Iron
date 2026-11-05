package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * G2 composition owner. Converts multiple independently authored and
 * athlete-context-bound goal/phase prescriptions through the EXISTING
 * week generator, then submits only complete blocks to the authoritative
 * full-cycle evaluator. There is no competing composer, loading engine,
 * approval writer or workout issuer.
 *
 * A changed weekly availability has not been approved merely because a
 * possible future day count was suggested. Until a versioned future
 * schedule authority exists, such options remain unreviewed.
 */
data class AdaptiveBlockGenerationRequest(
    val approvedCycle:CustomTrainingCycle,
    val targetBlockId:String,
    val athleteSetup:AthleteSetupReview,
    val catalog:List<CatalogMovement>,
    val coaching:CustomCoachAuditInputs,
    val permissions:CustomCycleCoachPermissions=CustomCycleCoachPermissions(),
    val alternatives:List<AdaptiveBlockGenerationAlternative>,
    val includeKeepCurrent:Boolean=true
)
data class AdaptiveBlockGenerationAlternative(
    val optionId:String,
    val generation:AthleteProgramGenerationRequest,
    val prescriptions:List<ExercisePrescriptionPolicy>
)
enum class AdaptiveBlockGenerationStatus {
    SOURCE_UNRESOLVED, OPTIONS_EVALUATED, NO_FEASIBLE_OPTION
}
enum class AdaptiveBlockGenerationRefusal {
    DIFFERENT_ATHLETE_OR_GYM,
    FUTURE_SCHEDULE_REQUIRES_APPROVAL,
    MISSING_PHASE_POLICY
}
data class AdaptiveBlockGenerationOutcome(
    val optionId:String,
    val generated:GeneratedAdaptiveBlockResult?,
    val refusal:AdaptiveBlockGenerationRefusal?
) {
    val eligibleForCycleAudit:Boolean get()=
        refusal==null &&
        generated?.status==GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW
}
data class AdaptiveBlockGenerationReview(
    val status:AdaptiveBlockGenerationStatus,
    val outcomes:List<AdaptiveBlockGenerationOutcome>,
    val comparison:AdaptiveCycleComparison,
    val recommendedOptionId:String?
) {
    val mayApplyAutomatically:Boolean get()=false
    val maySaveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}
object AdaptiveBlockOptionGenerationDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val BASELINE="keep_current"
    private const val MAX_GENERATED=7

    fun review(request:AdaptiveBlockGenerationRequest):
        AdaptiveBlockGenerationReview {
        require(request.alternatives.size<=MAX_GENERATED)
        require(id.matches(request.targetBlockId))
        require(request.alternatives.all {id.matches(it.optionId) &&
            it.optionId!=BASELINE})
        require(request.alternatives.map {it.optionId}.distinct().size==
            request.alternatives.size)
        val source=request.athleteSetup.reviewed
        val allowed=source!=null && request.athleteSetup.mayReviewProgramCandidate
        val sameSource=allowed && source!!.athleteProfileId==
            request.approvedCycle.athleteId &&
            source.goal==request.approvedCycle.goal
        val assessments=mutableListOf<AdaptiveBlockGenerationOutcome>()
        val candidates=mutableListOf<AdaptiveCycleOption>()
        // Always compare keeping a fully audited cycle when requested.
        // A valid baseline gets the same policy and full-cycle checks as
        // every changed block. No guess about a new training frequency.
        if(request.includeKeepCurrent)
            candidates+=AdaptiveCycleOption(BASELINE,
                request.approvedCycle.copy(
                    revision=request.approvedCycle.revision+1))
        for(option in request.alternatives) {
            val setup=option.generation.setup.reviewed
            val refusal=when {
                !sameSource || setup==null ||
                    !option.generation.setup.mayReviewProgramCandidate ||
                    setup.athleteProfileId!=source!!.athleteProfileId ||
                    setup.profileRevision!=source.profileRevision ||
                    setup.goal!=source.goal ||
                    setup.gym!=source.gym ||
                    option.generation.catalog!=request.catalog ||
                    setup.excludedExerciseIds!=source.excludedExerciseIds ||
                    setup.allowBodyweight!=source.allowBodyweight ||
                    setup.allowSupersets!=source.allowSupersets ||
                    setup.maxBarbellExercisesPerDay!=
                        source.maxBarbellExercisesPerDay ||
                    setup.allowTricepsOnLower!=source.allowTricepsOnLower ->
                    AdaptiveBlockGenerationRefusal.DIFFERENT_ATHLETE_OR_GYM
                setup.trainingWeekdays!=source.trainingWeekdays ||
                    setup.split!=source.split ||
                    setup.lockedTrainingDays!=source.lockedTrainingDays ||
                    setup.hardTrainingDays!=source.hardTrainingDays ||
                    setup.longTrainingDays!=source.longTrainingDays ||
                    setup.declaredRestDays!=source.declaredRestDays ->
                    AdaptiveBlockGenerationRefusal.FUTURE_SCHEDULE_REQUIRES_APPROVAL
                request.coaching.policiesByPhase[option.generation.phase]!=
                    option.generation.policy ->
                    AdaptiveBlockGenerationRefusal.MISSING_PHASE_POLICY
                else -> null
            }
            val generated=if(refusal!=null)null else
                GeneratedAdaptiveBlockDomain.propose(
                    GeneratedAdaptiveBlockRequest(request.approvedCycle,
                        request.targetBlockId,option.optionId,
                        option.generation,option.prescriptions))
            if(generated?.status==GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW)
                candidates+=checkNotNull(generated.proposedOption)
            assessments+=AdaptiveBlockGenerationOutcome(
                option.optionId,generated,refusal)
        }
        val comparison=AdaptiveCycleOptionReviewDomain.review(
            AdaptiveCycleOptionRequest(request.approvedCycle,
                request.targetBlockId,request.athleteSetup,
                request.catalog,request.coaching,request.permissions,
                Collections.unmodifiableList(candidates)))
        val status=when(comparison.status) {
            AdaptiveCycleComparisonStatus.SOURCE_UNRESOLVED ->
                AdaptiveBlockGenerationStatus.SOURCE_UNRESOLVED
            AdaptiveCycleComparisonStatus.NO_FEASIBLE_ALTERNATIVE ->
                AdaptiveBlockGenerationStatus.NO_FEASIBLE_OPTION
            AdaptiveCycleComparisonStatus.OPTIONS_COMPARED ->
                AdaptiveBlockGenerationStatus.OPTIONS_EVALUATED
        }
        return AdaptiveBlockGenerationReview(
            status,Collections.unmodifiableList(assessments),
            comparison,comparison.recommendedOptionId)
    }
}
