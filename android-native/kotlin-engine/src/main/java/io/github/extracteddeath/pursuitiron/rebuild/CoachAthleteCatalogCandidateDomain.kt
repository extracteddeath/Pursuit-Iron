package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Review-only bridge: confirmed setup facts come from the athlete snapshot;
 * phase, dose, focus and training purpose require separately authored policy.
 * Never issues a workout, saves a program or guesses training thresholds.
 */
data class AthleteCalendarIntent(
    val weekday:Int,
    val purpose:ComposerSessionPurpose,
    val requiredPrimaryExerciseId:String?=null
)
data class AthleteCatalogCandidateRequest(
    val setup:AthleteSetupReview,
    val phase:TrainingPhase,
    val focusByWeekday:Map<Int,ComposerFocus>,
    val bounds:List<DeclaredMuscleDoseBounds>,
    val nonSetSecondsByWeekday:Map<Int,Int>,
    val intents:List<AthleteCalendarIntent>,
    val catalog:List<CatalogMovement>,
    val choices:List<CatalogExerciseChoice>,
    val maximumExercisesPerDay:Int,
    val preferredPatternsPerDay:Int
)
enum class AthleteCandidateIssueKind {
    SETUP_UNRESOLVED, SCHEDULE_MISMATCH, POLICY_UNRESOLVED,
    EXCLUDED_EXERCISE, BODYWEIGHT_DISABLED, BARBELL_LIMIT,
    LOWER_DAY_RESTRICTION, CATALOG_UNRESOLVED
}
data class AthleteCandidateIssue(
    val field:String,
    val kind:AthleteCandidateIssueKind,
    val reason:String
)
enum class AthleteCandidateStatus {
    SETUP_UNRESOLVED, CONSTRAINTS_UNRESOLVED, CANDIDATE_FOR_REVIEW
}
data class AthleteCatalogCandidateReview(
    val status:AthleteCandidateStatus,
    val issues:List<AthleteCandidateIssue>,
    val catalogReview:CatalogWeekReview?
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
}

object CoachAthleteCatalogCandidateDomain {
    fun review(request:AthleteCatalogCandidateRequest):AthleteCatalogCandidateReview {
        val issues=mutableListOf<AthleteCandidateIssue>()
        fun add(field:String,kind:AthleteCandidateIssueKind,reason:String) {
            issues+=AthleteCandidateIssue(field,kind,reason)
        }
        fun result(status:AthleteCandidateStatus,
            catalog:CatalogWeekReview?=null):AthleteCatalogCandidateReview =
            AthleteCatalogCandidateReview(status,
                Collections.unmodifiableList(issues.toList()),catalog)

        val profile=request.setup.reviewed?.takeIf {
            request.setup.mayReviewProgramCandidate
        }
        if(profile==null) {
            add("setup",AthleteCandidateIssueKind.SETUP_UNRESOLVED,
                "Confirm all athlete and equipment settings first.")
            return result(AthleteCandidateStatus.SETUP_UNRESOLVED)
        }
        val weekdays=profile.trainingWeekdays.sorted()
        if(request.focusByWeekday.keys!=profile.trainingWeekdays)
            add("focusByWeekday",AthleteCandidateIssueKind.SCHEDULE_MISMATCH,
                "Provide exactly one day focus for each confirmed training weekday.")
        val focus=request.focusByWeekday.values.toSet()
        val allowedFocus=when(profile.split) {
            ComposerSplit.FULL_BODY ->setOf(ComposerFocus.FULL_BODY)
            ComposerSplit.UPPER_LOWER ->setOf(
                ComposerFocus.UPPER,ComposerFocus.LOWER)
            ComposerSplit.PUSH_PULL_LEGS ->setOf(
                ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)
            ComposerSplit.ULPPL ->setOf(
                ComposerFocus.UPPER,ComposerFocus.LOWER,ComposerFocus.PUSH,
                ComposerFocus.PULL,ComposerFocus.LEGS)
        }
        if(focus!=allowedFocus)
            add("focusByWeekday",AthleteCandidateIssueKind.SCHEDULE_MISMATCH,
                "The proposed day focuses do not match the confirmed split.")
        if(request.nonSetSecondsByWeekday.keys!=profile.trainingWeekdays ||
            request.nonSetSecondsByWeekday.values.any {it !in 0..21600})
            add("nonSetSecondsByWeekday",AthleteCandidateIssueKind.POLICY_UNRESOLVED,
                "Declare real setup and transition time for every training day.")
        val intentDays=request.intents.map {it.weekday}
        if(intentDays.size!=weekdays.size ||
            intentDays.toSet()!=profile.trainingWeekdays ||
            intentDays.distinct().size!=intentDays.size)
            add("intents",AthleteCandidateIssueKind.POLICY_UNRESOLVED,
                "Every selected weekday needs one explicit training-day purpose.")
        if(request.bounds.size!=TrainingMuscle.entries.size ||
            request.bounds.map {it.muscle}.toSet()!=TrainingMuscle.entries.toSet())
            add("bounds",AthleteCandidateIssueKind.POLICY_UNRESOLVED,
                "All sixteen phase-specific muscle dose targets must be supplied.")
        if(request.maximumExercisesPerDay !in 1..12 ||
            request.preferredPatternsPerDay !in 1..4)
            add("preferences",AthleteCandidateIssueKind.POLICY_UNRESOLVED,
                "Explicit exercise and pattern limits are required.")

        val movementById=request.catalog.associateBy {it.exerciseId}
        for(choice in request.choices) {
            if(choice.exerciseId in profile.excludedExerciseIds)
                add("choices",AthleteCandidateIssueKind.EXCLUDED_EXERCISE,
                    "A selected exercise is explicitly excluded by the athlete.")
            val movement=movementById[choice.exerciseId] ?:continue
            if(!profile.allowBodyweight && movement.equipmentId==null)
                add("choices",AthleteCandidateIssueKind.BODYWEIGHT_DISABLED,
                    "The athlete disabled unweighted bodyweight movements.")
            if(profile.maxBarbellExercisesPerDay==0 &&
                movement.expectedLoadBasis==LoadBasis.BARBELL_TOTAL)
                add("choices",AthleteCandidateIssueKind.BARBELL_LIMIT,
                    "No barbell exercises are allowed by this gym profile.")
        }
        if(issues.isNotEmpty())return result(AthleteCandidateStatus.CONSTRAINTS_UNRESOLVED)

        val index=weekdays.withIndex().associate {it.value to it.index}
        val days=weekdays.map {day ->
            ComposerDay(day,request.focusByWeekday.getValue(day),
                DeclaredSessionWindow(
                    profile.preferredMinimumMinutes,
                    profile.preferredMaximumMinutes,
                    profile.explicitHardMaximumMinutes,
                    request.nonSetSecondsByWeekday.getValue(day)))
        }
        val intents=request.intents.map {
            ComposerSessionIntent(index.getValue(it.weekday),
                it.purpose,it.requiredPrimaryExerciseId)
        }
        val template=ComposerRequest(profile.split,request.phase,days,
            request.bounds,emptyList(),request.maximumExercisesPerDay,
            request.preferredPatternsPerDay,intents,
            maximumBarbellExercisesPerDay=profile.maxBarbellExercisesPerDay,
            allowDirectTricepsOnLower=profile.allowTricepsOnLower)
        val plan=runCatching {
            CoachExerciseCatalogDomain.review(
                CatalogWeekRequest(template,request.catalog,request.choices,
                    CatalogGym(profile.gym.confirmedStock,
                        profile.gym.availableAttachments,
                        profile.gym.availableFacilities)))
        }.getOrElse {
            add("catalogOrDosePolicy",AthleteCandidateIssueKind.CATALOG_UNRESOLVED,
                "Invalid exercise catalog, dose targets or authored time estimates.")
            return result(AthleteCandidateStatus.CONSTRAINTS_UNRESOLVED)
        }
        if(plan.status!=CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW)
            add("composedWeek",AthleteCandidateIssueKind.CATALOG_UNRESOLVED,
                "The catalog or the whole-week dose, recovery and time constraints remain unresolved.")

        for(session in plan.draft?.sessions.orEmpty()) {
            val day=days[session.dayIndex]
            val movements=session.orderedVisits.mapNotNull {
                movementById[it.exerciseId]
            }
            if(movements.count {
                it.expectedLoadBasis==LoadBasis.BARBELL_TOTAL
            }>profile.maxBarbellExercisesPerDay)
                add("day."+day.calendarDay+".barbell",
                    AthleteCandidateIssueKind.BARBELL_LIMIT,
                    "The proposed day exceeds the athlete's barbell limit.")
            if(day.focus in setOf(ComposerFocus.LOWER,ComposerFocus.LEGS) &&
                !profile.allowTricepsOnLower && movements.any {movement ->
                    movement.credits.any {credit ->
                        credit.muscle==TrainingMuscle.TRICEPS &&
                            credit.role==MuscleCreditRole.DIRECT
                    }
                })
                add("day."+day.calendarDay+".triceps",
                    AthleteCandidateIssueKind.LOWER_DAY_RESTRICTION,
                    "Direct triceps training on lower day is disabled.")
        }
        // Athletic barbell/day and direct-triceps/lower restrictions are
        // enforced during candidate selection and repair. These remaining
        // goal/volume/day-intensity settings still have no issuing policy;
        // report them instead of claiming a personalized program.
        if(profile.focusedMuscles.isNotEmpty() ||
            profile.reducedMuscles.isNotEmpty())
            add("musclePriorities",AthleteCandidateIssueKind.POLICY_UNRESOLVED,
                "A phase-specific priority-to-dose policy is still required.")
        if(profile.hardTrainingDays.isNotEmpty() ||
            profile.longTrainingDays.isNotEmpty())
            add("dayIntensity",AthleteCandidateIssueKind.POLICY_UNRESOLVED,
                "Explicit hard/long day intensity and time policies are still required.")
        return result(
            if(issues.isEmpty())AthleteCandidateStatus.CANDIDATE_FOR_REVIEW
            else AthleteCandidateStatus.CONSTRAINTS_UNRESOLVED,plan)
    }
}
