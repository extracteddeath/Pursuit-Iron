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
    val prescriptions:List<ExercisePrescriptionPolicy>,
    /** Requires a separately reviewed future profile and block policy. */
    val futureBlockContext:ProspectiveBlockCoachingContext?=null
)
enum class AdaptiveBlockGenerationStatus {
    SOURCE_UNRESOLVED, OPTIONS_EVALUATED, NO_FEASIBLE_OPTION,
    PROSPECTIVE_OPTIONS_ONLY
}
enum class AdaptiveBlockGenerationRefusal {
    DIFFERENT_ATHLETE_OR_GYM,
    FUTURE_SCHEDULE_REQUIRES_APPROVAL,
    INVALID_PROSPECTIVE_CONTEXT, MISSING_PHASE_POLICY
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
/**
 * G2 blueprint: the athlete/reviewer supplies allowable future splits,
 * weekdays, phase policies and exercise prescriptions, not an already
 * assembled program-generation request. This is NOT authority to change
 * the currently approved schedule or to synthesize training thresholds.
 */
data class AdaptiveScheduleBlueprint(
    val optionId:String,
    val choices:List<CatalogExerciseChoice>,
    val prescriptions:List<ExercisePrescriptionPolicy>,
    val future:ProspectiveBlockCoachingContext?=null,
    val extraFocuses:List<ComposerFocus> =emptyList()
)
data class AdaptiveScheduleBlueprintRequest(
    val base:AdaptiveBlockGenerationRequest,
    /** Full current reviewed generator constraints, including density. */
    val generatorTemplate:AthleteProgramGenerationRequest,
    val blueprints:List<AdaptiveScheduleBlueprint>
)

object AdaptiveBlockOptionGenerationDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val BASELINE="keep_current"
    private const val MAX_GENERATED=7

    /**
     * Assemble distinct, bounded candidate weeks from verified current and
     * prospective contexts. Reuses the same generator, prescription
     * converter, cycle auditor and transition permissions as review().
     *
     * A prospective context is never silently treated as a committed
     * profile. Change of split alone is separate from day-count change.
     */
    fun reviewBlueprints(request:AdaptiveScheduleBlueprintRequest):
        AdaptiveBlockGenerationReview {
        val base=request.base
        val template=request.generatorTemplate
        require(base.alternatives.isEmpty()) {
            "Blueprint review owns its alternatives; do not merge two sources."
        }
        require(request.blueprints.size<=MAX_GENERATED)
        require(template.setup==base.athleteSetup &&
            template.catalog==base.catalog &&
            template.nonSetSecondsByWeekday==
                base.coaching.nonSetSecondsByWeekday) {
            "The generator template must use the current audited athlete and time."
        }
        val target=base.approvedCycle.blocks.singleOrNull {
            it.blockId==base.targetBlockId
        }
        require(target!=null && template.phase==target.phase &&
            template.policy==base.coaching.policiesByPhase[target.phase]) {
            "The template must match the existing target phase and policy."
        }
        val alternatives=request.blueprints.map {blueprint ->
            val future=blueprint.future
            val setup=future?.setup ?:base.athleteSetup
            val policy=future?.policy ?:template.policy
            val overhead=future?.nonSetSecondsByWeekday ?:
                template.nonSetSecondsByWeekday
            AdaptiveBlockGenerationAlternative(
                blueprint.optionId,
                template.copy(setup=setup,policy=policy,
                    choices=blueprint.choices,extraFocuses=blueprint.extraFocuses,
                    nonSetSecondsByWeekday=overhead),
                blueprint.prescriptions,future)
        }
        return review(base.copy(alternatives=alternatives))
    }

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
        val prospective=mutableMapOf<String,ProspectiveBlockCoachingContext>()
        // Always compare keeping a fully audited cycle when requested.
        // A valid baseline gets the same policy and full-cycle checks as
        // every changed block. No guess about a new training frequency.
        if(request.includeKeepCurrent)
            candidates+=AdaptiveCycleOption(BASELINE,
                request.approvedCycle.copy(
                    revision=request.approvedCycle.revision+1))
        for(option in request.alternatives) {
            val setup=option.generation.setup.reviewed
            val context=option.futureBlockContext
            val changedSchedule=source!=null && setup!=null && (
                setup.trainingWeekdays!=source.trainingWeekdays ||
                setup.split!=source.split ||
                setup.lockedTrainingDays!=source.lockedTrainingDays ||
                setup.hardTrainingDays!=source.hardTrainingDays ||
                setup.longTrainingDays!=source.longTrainingDays ||
                setup.declaredRestDays!=source.declaredRestDays)
            val refusal=when {
                !sameSource || setup==null ||
                    !option.generation.setup.mayReviewProgramCandidate ||
                    option.generation.catalog!=request.catalog ||
                    // Candidate scheduling is the only field we permit to
                    // differ at all. Do not bypass reviewed gym, injury,
                    // session time, priority, goal or other athlete facts.
                    setup.copy(split=source!!.split,
                        trainingWeekdays=source.trainingWeekdays,
                        lockedTrainingDays=source.lockedTrainingDays,
                        hardTrainingDays=source.hardTrainingDays,
                        longTrainingDays=source.longTrainingDays,
                        declaredRestDays=source.declaredRestDays)!=source ->
                    AdaptiveBlockGenerationRefusal.DIFFERENT_ATHLETE_OR_GYM
                changedSchedule && context==null ->
                    AdaptiveBlockGenerationRefusal.FUTURE_SCHEDULE_REQUIRES_APPROVAL
                context!=null && (
                    context.setup!=option.generation.setup ||
                    context.policy!=option.generation.policy ||
                    context.nonSetSecondsByWeekday!=
                        option.generation.nonSetSecondsByWeekday) ->
                    AdaptiveBlockGenerationRefusal.INVALID_PROSPECTIVE_CONTEXT
                context==null &&
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
            if(generated?.status==GeneratedAdaptiveBlockStatus.READY_FOR_CYCLE_REVIEW) {
                candidates+=checkNotNull(generated.proposedOption)
                if(context!=null)prospective[option.optionId]=context
            }
            assessments+=AdaptiveBlockGenerationOutcome(
                option.optionId,generated,refusal)
        }
        val comparison=AdaptiveCycleOptionReviewDomain.review(
            AdaptiveCycleOptionRequest(request.approvedCycle,
                request.targetBlockId,request.athleteSetup,
                request.catalog,request.coaching,request.permissions,
                Collections.unmodifiableList(candidates),
                Collections.unmodifiableMap(prospective)))
        val status=when(comparison.status) {
            AdaptiveCycleComparisonStatus.SOURCE_UNRESOLVED ->
                AdaptiveBlockGenerationStatus.SOURCE_UNRESOLVED
            AdaptiveCycleComparisonStatus.NO_FEASIBLE_ALTERNATIVE ->
                AdaptiveBlockGenerationStatus.NO_FEASIBLE_OPTION
            AdaptiveCycleComparisonStatus.PROSPECTIVE_OPTIONS_ONLY ->
                AdaptiveBlockGenerationStatus.PROSPECTIVE_OPTIONS_ONLY
            AdaptiveCycleComparisonStatus.OPTIONS_COMPARED ->
                AdaptiveBlockGenerationStatus.OPTIONS_EVALUATED
        }
        return AdaptiveBlockGenerationReview(
            status,Collections.unmodifiableList(assessments),
            comparison,comparison.recommendedOptionId)
    }
}
