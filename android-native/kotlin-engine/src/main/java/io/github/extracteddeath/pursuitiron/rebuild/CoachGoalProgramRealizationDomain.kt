package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Checks the REAL emitted candidate week, rather than goal labels or just
 * authored thresholds. Not a scientific endorsement or an issuing engine.
 */
enum class GoalRealizationStatus { UNRESOLVED, DELIVERED_FOR_REVIEW }
enum class GoalRealizationIssueKind {
    NO_WEEK, DOSE_UNMET, EMPTY_OR_INVALID_SESSION, PURPOSE_MISMATCH,
    AUTHORED_LIFT_MISSING, LIFT_FREQUENCY_MISSING, LIFT_FREQUENCY_UNMET,
    LIFT_NOT_PRIMARY
}
data class GoalRealizationIssue(
    val kind:GoalRealizationIssueKind,
    val field:String,
    val explanation:String
)
data class GoalPrimaryFrequency(
    val exerciseId:String,
    val requiredDays:Int,
    val deliveredDays:Int,
    val actualDayIndices:Set<Int>
)
data class GoalRealizationReview(
    val status:GoalRealizationStatus,
    val issues:List<GoalRealizationIssue>,
    val primaryFrequencies:List<GoalPrimaryFrequency>
) {
    val maySaveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}

object CoachGoalProgramRealizationDomain {
    fun review(
        athlete:ReviewedAthleteSetup,
        phase:TrainingPhase,
        intents:List<AthleteCalendarIntent>,
        policy:AuthoredGoalProgramPolicy,
        proposed:CatalogWeekReview?
    ):GoalRealizationReview {
        val issues=mutableListOf<GoalRealizationIssue>()
        fun add(kind:GoalRealizationIssueKind,field:String,reason:String) {
            issues+=GoalRealizationIssue(kind,field,reason)
        }
        fun report(frequencies:List<GoalPrimaryFrequency> =emptyList())=
            GoalRealizationReview(
                if(issues.isEmpty())GoalRealizationStatus.DELIVERED_FOR_REVIEW
                    else GoalRealizationStatus.UNRESOLVED,
                Collections.unmodifiableList(issues.toList()),
                Collections.unmodifiableList(frequencies.toList()))
        val draft=proposed?.draft
        val template=proposed?.composedRequest
        if(draft==null || template==null) {
            add(GoalRealizationIssueKind.NO_WEEK,"week",
                "No composed, equipment-verified week exists for this policy.")
            return report()
        }
        for(finding in draft.audit.muscles.values)
            if(finding.gapToMev.signum()>0 ||
                finding.excessAboveMrv.signum()>0 ||
                finding.missingDirectDays>0)
                add(GoalRealizationIssueKind.DOSE_UNMET,
                    "muscle."+finding.muscle.name,
                    "The actual working sets or direct-day frequency miss the authored muscle targets.")
        if(draft.audit.daysWithoutWorkingSets.isNotEmpty() ||
            draft.audit.daysOutsidePreferredWindow.isNotEmpty() ||
            draft.audit.sessions.any {
                it.finding==TimeWindowFinding.ABOVE_USER_HARD_LIMIT
            } || draft.audit.requiresExtensionDosePolicy ||
            draft.daysBelowPatternPreference.isNotEmpty()) {
            add(GoalRealizationIssueKind.EMPTY_OR_INVALID_SESSION,"sessions",
                "One or more days is empty, outside preferred duration or movement variety, violates a hard stop or uses unreviewed extension sets.")
        }
        if(draft.missingRequiredVisits.isNotEmpty() ||
            draft.daysWithUnmetPrimary.isNotEmpty())
            add(GoalRealizationIssueKind.AUTHORED_LIFT_MISSING,
                "requiredVisits","The candidate omitted authored pinned visits or required main lifts.")

        val weekdays=template.days.withIndex().associate {
            it.value.calendarDay to it.index
        }
        val intentByDay=intents.associateBy {it.weekday}
        if(intents.size!=athlete.trainingWeekdays.size ||
            intentByDay.keys!=athlete.trainingWeekdays)
            add(GoalRealizationIssueKind.PURPOSE_MISMATCH,"weekdays",
                "The planned session purposes do not cover every selected day exactly once.")
        for((weekday,index) in weekdays) {
            val intent=intentByDay[weekday]
            val session=draft.sessions.getOrNull(index)
            if(intent==null || session?.purpose!=intent.purpose)
                add(GoalRealizationIssueKind.PURPOSE_MISMATCH,
                    "day."+weekday,"The emitted session purpose differs from its authored intent.")
            if(intent?.requiredPrimaryExerciseId!=null &&
                session?.orderedVisits?.none {
                    it.exerciseId==intent.requiredPrimaryExerciseId
                }!=false)
                add(GoalRealizationIssueKind.AUTHORED_LIFT_MISSING,
                    "day."+weekday+".primary",
                    "The named primary exercise did not appear on its authored day.")
        }

        val options=template.exercises.associateBy {it.exerciseId}
        val counted=mutableListOf<GoalPrimaryFrequency>()
        if(phase in setOf(TrainingPhase.STRENGTH,TrainingPhase.PEAK) &&
            policy.primaryLiftMinimumDays.isEmpty())
            add(GoalRealizationIssueKind.LIFT_FREQUENCY_MISSING,
                "primaryFrequency",
                "Strength/peak requires an explicit authored primary-lift frequency.")
        for((id,minimum) in policy.primaryLiftMinimumDays.toSortedMap()) {
            if(options[id]?.role!=ComposerRole.PRIMARY)
                add(GoalRealizationIssueKind.LIFT_NOT_PRIMARY,"primary."+id,
                    "The named exercise is absent or not a reviewed primary lift.")
            val actualDays=draft.visits.filter {it.exerciseId==id}
                .map {it.dayIndex}.toSet()
            counted+=GoalPrimaryFrequency(id,minimum,actualDays.size,
                Collections.unmodifiableSet(actualDays))
            if(actualDays.size<minimum)
                add(GoalRealizationIssueKind.LIFT_FREQUENCY_UNMET,
                    "primary."+id,
                    "The produced week misses the coach's primary-lift days.")
        }
        if(phase in setOf(TrainingPhase.STRENGTH,TrainingPhase.PEAK))
            for(intent in intents.filter {
                it.purpose==ComposerSessionPurpose.STRENGTH_SKILL
            }) {
                val lift=intent.requiredPrimaryExerciseId
                if(lift==null || options[lift]?.role!=ComposerRole.PRIMARY)
                    add(GoalRealizationIssueKind.LIFT_NOT_PRIMARY,
                        "day."+intent.weekday+".skill",
                        "Each skill day needs an actual named primary lift.")
                else if(lift !in policy.primaryLiftMinimumDays)
                    add(GoalRealizationIssueKind.LIFT_FREQUENCY_MISSING,
                        "primary."+lift,
                        "The named skill lift has no authored weekly frequency.")
            }
        return report(counted)
    }
}
