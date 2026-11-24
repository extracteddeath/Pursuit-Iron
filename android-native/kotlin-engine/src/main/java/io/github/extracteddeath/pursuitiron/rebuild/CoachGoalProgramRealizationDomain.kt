package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Checks the REAL emitted candidate week, rather than goal labels or just
 * authored thresholds. Not a scientific endorsement or an issuing engine.
 */
enum class GoalRealizationStatus { UNRESOLVED, DELIVERED_FOR_REVIEW }
enum class GoalRealizationIssueKind {
    NO_WEEK, UNVERIFIED_CANDIDATE, DOSE_UNMET, EMPTY_OR_INVALID_SESSION, PURPOSE_MISMATCH,
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
        // A numerically attractive week is not verified merely because its
        // muscle dose happens to pass. A caller may independently inspect
        // this realization review, so do not rely on its outer catalog owner
        // to have enforced the same source and calendar gates first.
        val weekdays=template.days.map {it.calendarDay}
        if(proposed.status!=CatalogWeekStatus.VERIFIED_CATALOG_DRAFT_FOR_REVIEW ||
            draft.status!=ComposerStatus.FEASIBLE_DRAFT_FOR_REVIEW ||
            template.phase!=phase || draft.week.phase!=phase ||
            draft.audit.phase!=phase ||
            draft.week.days!=template.days.size ||
            draft.week.windows!=template.days.map {it.window} ||
            weekdays.size!=athlete.trainingWeekdays.size ||
            weekdays.toSet()!=athlete.trainingWeekdays ||
            weekdays.distinct().size!=weekdays.size ||
            draft.sessions.map {it.dayIndex}!=template.days.indices.toList()) {
            add(GoalRealizationIssueKind.UNVERIFIED_CANDIDATE,"week.source",
                "The composed week is not a verified, complete candidate for the athlete's exact phase, schedule and session windows.")
        }
        // Independent source binding: a composer can satisfy its own
        // permissive bounds while violating the athlete's ACTUAL reviewed
        // goal/experience policy. Goal realization must never certify that.
        val policyReview=runCatching {
            CoachGoalProgramPolicyDomain.review(
                athlete,phase,template.bounds,intents,policy)
        }.getOrNull()
        val templateBounds=template.bounds.associateBy {it.muscle}
        val emittedBounds=draft.week.bounds.associateBy {it.muscle}
        val sameBounds=templateBounds.size==template.bounds.size &&
            emittedBounds.size==draft.week.bounds.size &&
            templateBounds.keys==emittedBounds.keys &&
            templateBounds.all { (muscle,required) ->
                val actual=emittedBounds[muscle]
                actual!=null &&
                    required.mev.compareTo(actual.mev)==0 &&
                    required.mav.compareTo(actual.mav)==0 &&
                    required.mrv.compareTo(actual.mrv)==0 &&
                    required.minimumDirectDays==actual.minimumDirectDays
            }
        val intendedByDay=template.days.mapIndexedNotNull { index,day ->
            intents.singleOrNull{it.weekday==day.calendarDay}?.let {
                ComposerSessionIntent(index,it.purpose,
                    it.requiredPrimaryExerciseId)
            }
        }
        val scopeMatches=policyReview?.status==
                GoalPolicyStatus.MATCHED_FOR_REVIEW &&
            sameBounds &&
            template.split==athlete.split &&
            template.maximumBarbellExercisesPerDay==
                athlete.maxBarbellExercisesPerDay &&
            template.allowDirectTricepsOnLower==
                athlete.allowTricepsOnLower &&
            intendedByDay.size==intents.size &&
            intendedByDay.toSet()==template.sessionIntents.toSet() &&
            template.sessionIntents.size==intendedByDay.size &&
            template.days.all {day ->
                day.window.preferredMinimumMinutes==
                    athlete.preferredMinimumMinutes &&
                    day.window.preferredMaximumMinutes==
                    athlete.preferredMaximumMinutes &&
                    day.window.userHardLimitMinutes==
                    athlete.explicitHardMaximumMinutes
            }
        if(!scopeMatches) {
            add(GoalRealizationIssueKind.UNVERIFIED_CANDIDATE,
                "week.policy",
                "The emitted week does not match the athlete's currently reviewed goal, phase, schedule, training limits and muscle-dose policy.")
        }
        // An already-approved gym may have been replaced. Do not claim a
        // real movement can be trained from a different gym's stock or
        // from bodyweight when the athlete explicitly disabled it.
        val optionById=template.exercises.associateBy {it.exerciseId}
        val stock=athlete.gym.confirmedStock.values
        if(draft.visits.any { visit ->
            val option=optionById[visit.exerciseId]
            option==null ||
                visit.exerciseId in athlete.excludedExerciseIds ||
                when(val equipment=option.equipment) {
                    ComposerEquipment.Bodyweight -> !athlete.allowBodyweight
                    is ComposerEquipment.Verified -> {
                        val inventory=equipment.inventory
                        inventory==null ||
                            stock.none {it==inventory} ||
                            equipment.unit!=athlete.gym.unit ||
                            // Real stock availability is not sufficient to
                            // authorize an issued program weight. Recheck
                            // the same basis-specific envelope as the native
                            // program and durable workout, even on a cached
                            // or externally supplied reviewed candidate.
                            !KotlinSetNumberAuthorityDomain.validPrescribedLoad(
                                equipment.plannedLoad,inventory.basis) ||
                            EquipmentLoadDomain.inspect(inventory,
                                equipment.plannedLoad,equipment.unit)
                                .let {it !is LoadInspection.Confirmed || !it.exact}
                    }
                }
        }) {
            add(GoalRealizationIssueKind.UNVERIFIED_CANDIDATE,
                "week.gym",
                "One or more delivered movements use excluded, unavailable or unverified gym equipment.")
        }
        // Recalculate the athlete-facing work from the actual proposed week.
        // A cached clean audit is NOT evidence after a caller edits the
        // exposures, session roster or set counts. Review stays read-only.
        val replayedAudit=runCatching {
            WeeklyDoseFeasibilityDomain.audit(draft.week)
        }.getOrNull()
        val sessionVisits=draft.sessions.flatMap {it.orderedVisits}
        val expectedWorkingKeys=draft.visits.flatMap { visit ->
            (0 until visit.sets).map {ordinal ->
                visit.exerciseId+":d"+visit.dayIndex+":s"+ordinal
            }
        }
        val actualWorkingKeys=draft.week.exposures.filter {
            it.kind==ExposureSetKind.WORKING
        }.map {it.setKey}
        if(replayedAudit==null || replayedAudit!=draft.audit ||
            sessionVisits.groupingBy {it}.eachCount()!=
                draft.visits.groupingBy {it}.eachCount() ||
            expectedWorkingKeys.sorted()!=actualWorkingKeys.sorted()) {
            add(GoalRealizationIssueKind.UNVERIFIED_CANDIDATE,"week.replay",
                "The actual prescribed working sets, session roster or current dose audit no longer match this reviewed candidate.")
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

        val weekdayToIndex=template.days.withIndex().associate {
            it.value.calendarDay to it.index
        }
        val intentByDay=intents.associateBy {it.weekday}
        if(intents.size!=athlete.trainingWeekdays.size ||
            intentByDay.keys!=athlete.trainingWeekdays)
            add(GoalRealizationIssueKind.PURPOSE_MISMATCH,"weekdays",
                "The planned session purposes do not cover every selected day exactly once.")
        for((weekday,index) in weekdayToIndex) {
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
