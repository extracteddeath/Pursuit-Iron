package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Explicit opt-in source-catalog shortlist for the provisional week composer.
 *
 * A supplied CatalogExerciseChoice is the ONLY source of a working load,
 * exposure duration, rest and authored sets. This selector never guesses
 * those facts or replaces a pinned visit. Estimated set capacity is an
 * optimistic screening bound, NOT actual delivered training stimulus.
 */
data class AthleteExerciseSelectionRequest(
    val setup:AthleteSetupReview,
    val policy:AuthoredGoalProgramPolicy?,
    val catalog:List<CatalogMovement>,
    val offered:List<CatalogExerciseChoice>,
    val usableFocus:Set<ComposerFocus>,
    val preferredPatternsPerDay:Int,
    val maximumOptions:Int=32
)
enum class AthleteSelectionStatus {
    INPUT_UNRESOLVED, COVERAGE_UNRESOLVED, SHORTLIST_FOR_REVIEW
}
enum class AthleteSelectionProblem {
    PROFILE_OR_POLICY, INVALID_CATALOG, UNAVAILABLE_REQUIRED_EXERCISE,
    TOO_MANY_REQUIRED_EXERCISES, MISSING_DOSE_COVERAGE,
    MISSING_DIRECT_DAYS, MISSING_MOVEMENT_VARIETY, NO_ELIGIBLE_OPTIONS
}
data class AthleteSelectionIssue(
    val kind:AthleteSelectionProblem,
    val field:String,
    val reason:String
)
data class RejectedCatalogChoice(
    val exerciseId:String,
    val explanation:String
)
data class AthleteExerciseSelectionReview(
    val status:AthleteSelectionStatus,
    val choices:List<CatalogExerciseChoice>,
    val excluded:List<RejectedCatalogChoice>,
    val issues:List<AthleteSelectionIssue>
) {
    val mayIssueWorkout:Boolean get()=false
    val mayOverwriteCustomProgram:Boolean get()=false
}
object CoachAthleteExerciseSelectionDomain {
    private val zero=BigDecimal.ZERO
    private val maxDose=BigDecimal("200")

    private fun validPolicy(
        athlete:ReviewedAthleteSetup,policy:AuthoredGoalProgramPolicy?
    ):Boolean {
        if(policy==null)return false
        if(policy.athleteId!=athlete.athleteProfileId ||
            policy.athleteRevision!=athlete.profileRevision ||
            policy.goal!=athlete.goal || policy.experience!=athlete.experience ||
            policy.split!=athlete.split ||
            policy.trainingDays!=athlete.trainingWeekdays.size)
            return false
        val doses=policy.selectedBounds
        return TrainingMuscleCoverage.isComplete(doses.map{it.muscle}) &&
            doses.all {
                it.mev>=zero && it.mev<=it.mav && it.mav<=it.mrv &&
                    it.mrv<=maxDose &&
                    it.minimumDirectDays in 0..athlete.trainingWeekdays.size
            }
    }

    /** Validate real catalog and loading evidence, not exercise display names. */
    private fun excludedReason(
        athlete:ReviewedAthleteSetup,
        spec:CatalogMovement?,
        choice:CatalogExerciseChoice,
        focus:Set<ComposerFocus>,coveredMuscles:Set<TrainingMuscle>
    ):String? {
        if(spec==null)return "This stable movement ID is absent from the catalog."
        if(spec.credits.any{it.muscle !in coveredMuscles})
            return "Review explicit targets for every additional muscle before selecting this exercise."
        if(spec.exerciseId in athlete.excludedExerciseIds)
            return "The athlete explicitly excluded this exercise."
        if(spec.creditStatus!=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
            spec.creditReviewReference.isNullOrBlank() ||
            spec.credits.isEmpty() ||
            spec.credits.map {it.muscle}.distinct().size!=spec.credits.size ||
            spec.credits.any {
                it.fractionalSet<=zero || it.fractionalSet>BigDecimal.ONE
            })
            return "The muscle-credit map is not source-reviewed."
        if(spec.permittedFocus.intersect(focus).isEmpty())
            return "The movement has no compatible confirmed split-day focus."
        if(!athlete.allowTricepsOnLower &&
            spec.permittedFocus.intersect(focus).all {
                it in setOf(ComposerFocus.LEGS,ComposerFocus.LOWER)
            } && spec.credits.any {
                it.muscle==TrainingMuscle.TRICEPS &&
                    it.role==MuscleCreditRole.DIRECT
            })
            return "Direct triceps are disabled on every compatible lower day."
        if(!athlete.gym.availableFacilities.containsAll(spec.requiredFacilityIds))
            return "A required bench, rack or fixture has not been confirmed."
        if(spec.attachmentId!=null &&
            spec.attachmentId !in athlete.gym.availableAttachments)
            return "The selected attachment is not available in this gym."
        if(choice.minimumSetsPerVisit !in 1..8 ||
            choice.maximumSetsPerVisit !in choice.minimumSetsPerVisit..12 ||
            choice.maximumVisits !in 1..athlete.trainingWeekdays.size ||
            choice.requiredVisits !in 0..choice.maximumVisits ||
            choice.minimumCalendarDayGap !in 1..6 ||
            choice.activeSecondsPerSet !in 1..3600 ||
            choice.restSecondsPerSet !in 0..3600 ||
            choice.priority !in 1..10)
            return "The chosen dose, visit, recovery or time inputs are invalid."
        if(spec.equipmentId==null) {
            if(spec.expectedLoadBasis!=null || choice.selectedLoad!=null ||
                !athlete.allowBodyweight)
                return "Unweighted bodyweight status or load is not confirmed."
            return null
        }
        val stock=athlete.gym.confirmedStock[spec.equipmentId]
            ?:return "The exact implement is not confirmed."
        if(stock.basis!=spec.expectedLoadBasis ||
            stock.unit!=athlete.gym.unit)
            return "This movement uses a different implement weight basis or unit."
        if(stock.basis==LoadBasis.BARBELL_TOTAL &&
            athlete.maxBarbellExercisesPerDay==0)
            return "The athlete permits zero barbell exercises per day."
        val load=choice.selectedLoad
            ?:return "A valid planned implement load has not been provided."
        val inspection=EquipmentLoadDomain.inspect(stock,load,stock.unit)
        if(inspection !is LoadInspection.Confirmed || !inspection.exact)
            return "The selected working load is physically unavailable."
        return null
    }

    fun review(input:AthleteExerciseSelectionRequest):
        AthleteExerciseSelectionReview {
        val issues=mutableListOf<AthleteSelectionIssue>()
        val excluded=mutableListOf<RejectedCatalogChoice>()
        val selected=LinkedHashMap<String,CatalogExerciseChoice>()
        fun issue(kind:AthleteSelectionProblem,field:String,reason:String) {
            issues+=AthleteSelectionIssue(kind,field,reason)
        }
        fun result(status:AthleteSelectionStatus)=
            AthleteExerciseSelectionReview(status,
                Collections.unmodifiableList(selected.values.toList()),
                Collections.unmodifiableList(excluded.toList()),
                Collections.unmodifiableList(issues.toList()))
        val athlete=input.setup.reviewed?.takeIf {
            input.setup.mayReviewProgramCandidate
        }
        if(athlete==null || !validPolicy(athlete,input.policy)) {
            issue(AthleteSelectionProblem.PROFILE_OR_POLICY,"setupOrPolicy",
                "The policy must match confirmed goal, experience, split, athlete revision and its complete muscle target scope.")
            return result(AthleteSelectionStatus.INPUT_UNRESOLVED)
        }
        val policy=checkNotNull(input.policy)
        if(input.catalog.size !in 1..CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES ||
            input.offered.size !in 1..CoachExerciseCatalogDomain.MAX_CATALOG_ENTRIES ||
            input.maximumOptions !in 1..32 ||
            input.preferredPatternsPerDay !in 1..4 ||
            input.usableFocus.isEmpty() ||
            input.catalog.map {it.exerciseId}.distinct().size!=input.catalog.size ||
            input.catalog.map {it.familyId to it.variantId}.distinct().size!=
                input.catalog.size ||
            input.offered.map {it.exerciseId}.distinct().size!=input.offered.size) {
            issue(AthleteSelectionProblem.INVALID_CATALOG,"catalog",
                "The offered, uniquely identified movement pool or selection budget is invalid.")
            return result(AthleteSelectionStatus.INPUT_UNRESOLVED)
        }
        val catalog=input.catalog.associateBy {it.exerciseId}
        val eligible=LinkedHashMap<String,CatalogExerciseChoice>()
        for(choice in input.offered.sortedBy {it.exerciseId}) {
            val reason=excludedReason(athlete,catalog[choice.exerciseId],
                choice,input.usableFocus,policy.selectedBounds.map{it.muscle}.toSet())
            if(reason==null)eligible[choice.exerciseId]=choice
            else excluded+=RejectedCatalogChoice(choice.exerciseId,reason)
        }
        val pinned=input.offered.filter {it.requiredVisits>0 }
            .map {it.exerciseId}.toSet()+
            policy.primaryLiftMinimumDays.keys
        val unavailable=pinned-eligible.keys
        if(unavailable.isNotEmpty()) {
            issue(AthleteSelectionProblem.UNAVAILABLE_REQUIRED_EXERCISE,
                "requiredVisits", "A pinned or priority lift has no verified, eligible offered variant: "+
                    unavailable.sorted().joinToString(", "))
            return result(AthleteSelectionStatus.INPUT_UNRESOLVED)
        }
        if(pinned.size>input.maximumOptions) {
            issue(AthleteSelectionProblem.TOO_MANY_REQUIRED_EXERCISES,
                "maximumOptions","The athlete has more pinned lifts than the selection budget.")
            return result(AthleteSelectionStatus.INPUT_UNRESOLVED)
        }
        for(id in pinned.sorted())selected[id]=eligible.getValue(id)
        val constraints=policy.selectedBounds.associateBy {it.muscle}
        fun capacity(muscle:TrainingMuscle):BigDecimal =
            selected.values.fold(zero) {sum,choice ->
                val spec=catalog.getValue(choice.exerciseId)
                val credit=spec.credits.firstOrNull {it.muscle==muscle }
                if(credit==null)sum else
                    sum.add(credit.fractionalSet.multiply(BigDecimal(
                        choice.maximumSetsPerVisit*choice.maximumVisits)))
            }
        fun directDays(muscle:TrainingMuscle):Int =
            selected.values.sumOf {choice ->
                val spec=catalog.getValue(choice.exerciseId)
                if(spec.credits.any {
                    it.muscle==muscle && it.role==MuscleCreditRole.DIRECT
                })choice.maximumVisits else 0
            }.coerceAtMost(athlete.trainingWeekdays.size)
        fun desiredGain(choice:CatalogExerciseChoice):BigDecimal {
            val spec=catalog.getValue(choice.exerciseId)
            return spec.credits.fold(zero) {gain,credit ->
                val b=constraints.getValue(credit.muscle)
                val missing=b.mev.subtract(capacity(credit.muscle)).max(zero)
                val added=credit.fractionalSet.multiply(BigDecimal(
                    choice.maximumSetsPerVisit*choice.maximumVisits))
                val directExtra=if(credit.role==MuscleCreditRole.DIRECT &&
                    directDays(credit.muscle)<b.minimumDirectDays)
                    BigDecimal("100") else zero
                gain.add(missing.min(added)).add(directExtra)
            }
        }
        // Deterministic bounded greedy set-cover only. The actual week
        // composer still proves training-day feasibility and hard limits.
        while(selected.size<input.maximumOptions) {
            val remaining=eligible.values.filter {
                it.exerciseId !in selected
            }.sortedWith(compareByDescending<CatalogExerciseChoice> {
                desiredGain(it)
            }.thenByDescending {it.priority}.thenBy {it.exerciseId})
            val best=remaining.firstOrNull() ?:break
            if(desiredGain(best)<=zero)break
            selected[best.exerciseId]=best
        }
        // Prefer actual pattern variety across the selected split focus
        // without manufacturing filler working sets to consume time.
        for(dayFocus in input.usableFocus.sortedBy {it.ordinal}) {
            while(selected.size<input.maximumOptions) {
                val patterns=selected.keys.mapNotNull {id->
                    catalog.getValue(id).takeIf {
                        dayFocus in it.permittedFocus
                    }?.pattern
                }.toSet()
                if(patterns.size>=input.preferredPatternsPerDay)break
                val alternative=eligible.values.filter {option ->
                    option.exerciseId !in selected &&
                        dayFocus in catalog.getValue(option.exerciseId).permittedFocus &&
                        catalog.getValue(option.exerciseId).pattern !in patterns
                }.sortedWith(compareByDescending<CatalogExerciseChoice> {
                    it.priority
                }.thenBy {it.exerciseId}).firstOrNull() ?:break
                selected[alternative.exerciseId]=alternative
            }
        }
        for((muscle,b) in constraints) {
            if(capacity(muscle)<b.mev)
                issue(AthleteSelectionProblem.MISSING_DOSE_COVERAGE,
                    "muscle."+muscle.name,
                    "Even optimistic selected catalog capacity cannot reach this authored muscle MEV.")
            if(directDays(muscle)<b.minimumDirectDays)
                issue(AthleteSelectionProblem.MISSING_DIRECT_DAYS,
                    "muscle."+muscle.name,
                    "The selected catalog cannot support the authored direct-day frequency.")
        }
        for(focus in input.usableFocus)if(selected.keys.mapNotNull {id->
            catalog.getValue(id).takeIf {focus in it.permittedFocus}?.pattern
        }.toSet().size<input.preferredPatternsPerDay)
            issue(AthleteSelectionProblem.MISSING_MOVEMENT_VARIETY,
                "focus."+focus.name,
                "The offered usable catalog lacks the selected split-day pattern variety.")
        if(selected.isEmpty())
            issue(AthleteSelectionProblem.NO_ELIGIBLE_OPTIONS,"choices",
                "There is no non-fabricated exercise candidate for this athlete.")
        return result(if(issues.isEmpty())
            AthleteSelectionStatus.SHORTLIST_FOR_REVIEW
            else AthleteSelectionStatus.COVERAGE_UNRESOLVED)
    }
}
