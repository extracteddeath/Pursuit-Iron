package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Full-cycle COACHING REVIEW for athlete-authored programs, not the
 * general ComposerRequest path. Every block week is resolved independently;
 * overrides, amplifiers, rest, exact physical loads and stable slot IDs
 * survive unchanged.
 *
 * A complete structural review NEVER implies a coach policy exists.
 * No generic MEV/MAV/MRV, non-set transition time, exercise frequency,
 * loading increment, strength day, or set target is fabricated.
 *
 * Sign-off, revision-bound approvals and workout issuance require separate
 * native owner and persistence transactions. This pure review does none.
 */
enum class CustomCoachIssueKind {
    SETUP_UNRESOLVED, ATHLETE_MISMATCH, INVALID_CUSTOM_CYCLE,
    CATALOG_UNVERIFIED, ATHLETE_SCHEDULE_CONFLICT,
    EXCLUDED_EXERCISE, BODYWEIGHT_DISABLED, SUPERSET_DISALLOWED,
    BARBELL_LIMIT, EXERCISE_ROLE_CONFLICT, REVIEWED_POLICY_MISSING,
    GOAL_POLICY_UNRESOLVED, NON_SET_TIME_UNDECLARED,
    SESSION_HARD_LIMIT, BELOW_WEEKLY_MEV, ABOVE_WEEKLY_MRV,
    INSUFFICIENT_DIRECT_FREQUENCY, PRIMARY_SKILL_FREQUENCY,
    CONTINUITY_POLICY_MISSING, CONTINUITY_POLICY_INVALID,
    UNJUSTIFIED_BLOCK_VOLUME_DROP,
    DIRECT_MUSCLE_DROP, REVIEW_CAP_EXCEEDED
}

data class CustomCoachIssue(
    val kind: CustomCoachIssueKind,
    /** Stable block/week/day/slot path for editing. */
    val location: String,
    val explanation: String
)

data class CustomCoachWeekMeasurement(
    val absoluteWeek: Int,
    val blockId: String,
    val phase: TrainingPhase,
    val blockPurpose: CustomBlockPurpose,
    val workingSets: Int,
    val directSets: Map<TrainingMuscle, BigDecimal>,
    val totalSetCredits: Map<TrainingMuscle, BigDecimal>,
    val directDays: Map<TrainingMuscle, Int>,
    /** Actual scheduled work/rest + authored amplifier follow-ups. */
    val knownSecondsByDay: Map<Int, Long>,
    /** Null if the athlete's non-set work time was never declared. */
    val estimatedSecondsByDay: Map<Int, Long?>
)

enum class CustomCoachAuditStatus {
    INVALID_SOURCE, COACHING_POLICY_REQUIRED, UNRESOLVED,
    READY_FOR_ATHLETE_REVIEW
}

data class CustomCoachAudit(
    val status: CustomCoachAuditStatus,
    val issues: List<CustomCoachIssue>,
    val structuralReview: CustomCycleReview?,
    val weeks: List<CustomCoachWeekMeasurement>,
    val sourceAthleteRevision: String?,
    val auditedCycleRevision: Long,
    val coachingPolicyRevisions: Map<TrainingPhase, String>
) {
    val mayIssueWorkout: Boolean get() = false
    val mayPublishApprovedProgram: Boolean get() = false
    val mayAlterWorkoutHistory: Boolean get() = false
}

/**
 * Athlete-specific, explicitly reviewed ceiling on a NON-recovery training
 * block's week-to-week volume decline. Not a universal MEV/MRV threshold.
 * Missing this policy leaves continuity unproven, not approved for cutover.
 */
data class CustomCoachVolumeContinuityPolicy(
    val maximumTotalWorkingSetDropFraction: BigDecimal,
    val maximumDirectMuscleDropFraction: BigDecimal
)

/** Explicitly supplied coach policy, transitions and non-set time. */
data class CustomCoachAuditInputs(
    val policiesByPhase: Map<TrainingPhase, AuthoredGoalProgramPolicy> =
        emptyMap(),
    val nonSetSecondsByWeekday: Map<Int, Int> = emptyMap(),
    val continuityPolicy: CustomCoachVolumeContinuityPolicy? = null
)

object CustomCycleCoachAuditDomain {
    private val zero = BigDecimal.ZERO
    private const val MAX_REVIEW_WEEKS = 104
    private const val MAX_REPORT_ISSUES = 256

    fun review(
        cycle: CustomTrainingCycle,
        setup: AthleteSetupReview,
        catalog: List<CatalogMovement>,
        inputs: CustomCoachAuditInputs = CustomCoachAuditInputs()
    ): CustomCoachAudit {
        val issues = mutableListOf<CustomCoachIssue>()
        fun add(kind: CustomCoachIssueKind, where: String, why: String) {
            if (issues.size < MAX_REPORT_ISSUES)
                issues += CustomCoachIssue(kind, where, why)
        }
        val profile = setup.reviewed?.takeIf {
            setup.status == AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW
        }
        if (profile == null) {
            add(CustomCoachIssueKind.SETUP_UNRESOLVED, "setup",
                "Finish reviewed native onboarding and verify actual gym stock.")
            return CustomCoachAudit(CustomCoachAuditStatus.INVALID_SOURCE,
                issues.toList(), null, emptyList(), null, cycle.revision,
                emptyMap())
        }
        if (profile.athleteProfileId != cycle.athleteId ||
            profile.goal != cycle.goal) {
            add(CustomCoachIssueKind.ATHLETE_MISMATCH, "cycle.athlete",
                "Cycle athlete or goal differs from the confirmed profile.")
        }
        val options = CustomExerciseAuthoringDomain.picker(setup, catalog)
        // A partially malformed or unreviewed catalog cannot silently pass
        // by returning an empty subset of candidate exercise options.
        if (catalog.isEmpty() || catalog.size > 256 ||
            catalog.map { it.exerciseId }.distinct().size != catalog.size ||
            catalog.map { it.familyId to it.variantId }.distinct().size != catalog.size ||
            catalog.any { m ->
                m.creditStatus != CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
                    m.creditReviewReference.isNullOrBlank() ||
                    m.credits.isEmpty() ||
                    m.credits.map { it.muscle }.distinct().size!=m.credits.size ||
                    m.credits.any { it.fractionalSet <= zero ||
                        it.fractionalSet>BigDecimal.ONE }
            }) {
            add(CustomCoachIssueKind.CATALOG_UNVERIFIED, "catalog",
                "The current reviewed catalog cannot be authenticated as a valid native selection set.")
        }
        // Entire structural review: EVERY week override, set and drop-load
        // must be attainable. This step never suggests replacements.
        val structural = CustomTrainingCycleDomain.review(
            cycle, options.map { it.availability })
        if (structural.verifiedDraft == null) {
            structural.issues.take(40).forEach {
                add(CustomCoachIssueKind.INVALID_CUSTOM_CYCLE,
                    it.location, it.explanation)
            }
            if (structural.issues.size > 40)
                add(CustomCoachIssueKind.REVIEW_CAP_EXCEEDED,"cycle",
                    "Additional authored structure problems require a new audit.")
        }
        val indices = catalog.associateBy { it.exerciseId }
        if (cycle.blocks.any { it.days.map { day -> day.weekday }.toSet() !=
                profile.trainingWeekdays }) {
            add(CustomCoachIssueKind.ATHLETE_SCHEDULE_CONFLICT,"blocks",
                "A block's authored training days differ from the athlete's confirmed weekly schedule. Update the confirmed schedule or this block.")
        }
        for (block in cycle.blocks) for (day in block.days) {
            val location = "block.${block.blockId}.day.${day.weekday}"
            if (!profile.allowSupersets && day.slots.any {
                    it.supersetGroup != null
                })
                add(CustomCoachIssueKind.SUPERSET_DISALLOWED,location,
                    "Supersets are disabled in the athlete's setup.")
            var barbellCount = 0
            for (slot in day.slots) {
                val at="$location.slot.${slot.slotId}"
                if (slot.exerciseId in profile.excludedExerciseIds)
                    add(CustomCoachIssueKind.EXCLUDED_EXERCISE,at,
                        "The athlete has explicitly excluded this movement.")
                val catalogEntry=indices[slot.exerciseId]
                if (catalogEntry != null) {
                    if (catalogEntry.role != slot.role)
                        add(CustomCoachIssueKind.EXERCISE_ROLE_CONFLICT,at,
                            "An exercise role differs from its reviewed movement definition.")
                    if (catalogEntry.equipmentId == null &&
                        !profile.allowBodyweight)
                        add(CustomCoachIssueKind.BODYWEIGHT_DISABLED,at,
                            "The athlete disabled bodyweight movements.")
                    if (catalogEntry.expectedLoadBasis == LoadBasis.BARBELL_TOTAL)
                        barbellCount++
                }
            }
            if (barbellCount > profile.maxBarbellExercisesPerDay)
                add(CustomCoachIssueKind.BARBELL_LIMIT,location,
                    "Too many different barbell exercises for the confirmed per-day limit.")
        }

        val allPhases=cycle.blocks.map {it.phase}.toSet()
        val policyIssue = allPhases.any {
            it !in inputs.policiesByPhase
        }
        if (policyIssue) for (phase in allPhases) {
            if (phase !in inputs.policiesByPhase)
                add(CustomCoachIssueKind.REVIEWED_POLICY_MISSING,
                    "policy.${phase.name}",
                    "No versioned goal/experience/phase-specific coaching policy was supplied. Weekly MEV/MAV/MRV and primary skill frequency cannot be assumed.")
        }
        val policyChecks = mutableMapOf<TrainingPhase, GoalPolicyReview>()
        for (block in cycle.blocks) {
            val policy=inputs.policiesByPhase[block.phase] ?:continue
            val intents=block.days.map { day ->
                AthleteCalendarIntent(day.weekday,day.purpose,
                    day.slots.firstOrNull {
                        it.role == ComposerRole.PRIMARY && it.pinned
                    }?.exerciseId)
            }
            val checked=CoachGoalProgramPolicyDomain.review(
                profile,block.phase,policy.selectedBounds,intents,policy)
            policyChecks[block.phase]=checked
            checked.issues.take(25).forEach {
                add(CustomCoachIssueKind.GOAL_POLICY_UNRESOLVED,
                    "block.${block.blockId}.${it.field}",it.explanation)
            }
        }
        if (inputs.nonSetSecondsByWeekday.keys != profile.trainingWeekdays ||
            inputs.nonSetSecondsByWeekday.values.any { it !in 0..21600 }) {
            add(CustomCoachIssueKind.NON_SET_TIME_UNDECLARED,"time",
                "Explicit setup, transition and cooldown seconds are needed for each training day; known set duration alone is not total session duration.")
        }
        val measurements=mutableListOf<CustomCoachWeekMeasurement>()
        if (structural.verifiedDraft != null &&
            structural.totalWeeks in 1..MAX_REVIEW_WEEKS) {
            for (weekNumber in 1..structural.totalWeeks) {
                val week=CustomTrainingCycleDomain.week(structural,weekNumber)
                    ?:continue
                val byMuscle=TrainingMuscle.entries.associateWith{zero}.toMutableMap()
                val direct=TrainingMuscle.entries.associateWith{zero}.toMutableMap()
                val directDayCount=TrainingMuscle.entries.associateWith{0}.toMutableMap()
                var working=0
                val known=linkedMapOf<Int,Long>()
                val estimated=linkedMapOf<Int,Long?>()
                for (day in week.days) {
                    var seconds=0L
                    val touched=mutableSetOf<TrainingMuscle>()
                    for (slot in day.slots) {
                        val workingSets=slot.sets.count {it.type==DraftSetType.WORKING}
                        working+=workingSets
                        for (credit in indices[slot.exerciseId]?.credits.orEmpty()) {
                            val volume=credit.fractionalSet
                                .multiply(BigDecimal(workingSets))
                            byMuscle[credit.muscle]=checkNotNull(byMuscle[credit.muscle])
                                .add(volume)
                            if (credit.role == MuscleCreditRole.DIRECT &&
                                workingSets>0) {
                                direct[credit.muscle]=checkNotNull(direct[credit.muscle])
                                    .add(volume)
                                touched += credit.muscle
                            }
                        }
                        for (set in slot.sets) {
                            seconds+=set.activeSeconds.toLong()+set.restSeconds
                            val amp=set.amplifier
                            if (amp!=null) {
                                // Follow-up working time is not a declared
                                // duration: never invent its seconds.
                                seconds+=amp.followUpRestSeconds
                            }
                        }
                    }
                    touched.forEach {
                        directDayCount[it]=checkNotNull(directDayCount[it])+1
                    }
                    known[day.weekday]=seconds
                    val overhead=inputs.nonSetSecondsByWeekday[day.weekday]
                    estimated[day.weekday]=overhead?.let {seconds+it}
                    val hard=profile.explicitHardMaximumMinutes
                    if (hard!=null && estimated[day.weekday]!=null &&
                        checkNotNull(estimated[day.weekday])>hard*60L)
                        add(CustomCoachIssueKind.SESSION_HARD_LIMIT,
                            "week.$weekNumber.day.${day.weekday}",
                            "The authored session exceeds the athlete's confirmed hard time limit.")
                }
                measurements += CustomCoachWeekMeasurement(
                    weekNumber,week.blockId,week.phase,week.purpose,working,
                    Collections.unmodifiableMap(direct),
                    Collections.unmodifiableMap(byMuscle),
                    Collections.unmodifiableMap(directDayCount),
                    Collections.unmodifiableMap(known),
                    Collections.unmodifiableMap(estimated))
                val policy=inputs.policiesByPhase[week.phase]
                if (policy!=null && week.purpose==CustomBlockPurpose.TRAINING &&
                    policyChecks[week.phase]?.status == GoalPolicyStatus.MATCHED_FOR_REVIEW) {
                    for (bound in policy.selectedBounds) {
                        val dose=byMuscle[bound.muscle] ?:zero
                        if (dose < bound.mev)
                            add(CustomCoachIssueKind.BELOW_WEEKLY_MEV,
                                "week.$weekNumber.${bound.muscle.name}",
                                "Authored weekly muscle-credit volume is below the phase-specific coached MEV.")
                        if (dose > bound.mrv)
                            add(CustomCoachIssueKind.ABOVE_WEEKLY_MRV,
                                "week.$weekNumber.${bound.muscle.name}",
                                "Authored weekly muscle-credit volume exceeds the reviewed MRV.")
                        if (checkNotNull(directDayCount[bound.muscle]) <
                            bound.minimumDirectDays)
                            add(CustomCoachIssueKind.INSUFFICIENT_DIRECT_FREQUENCY,
                                "week.$weekNumber.${bound.muscle.name}",
                                "This muscle has fewer direct training days than the policy requires.")
                    }
                    for ((exerciseId,days) in policy.primaryLiftMinimumDays) {
                        val actual=week.days.count { day ->
                            day.slots.any {
                                it.exerciseId == exerciseId &&
                                    it.role == ComposerRole.PRIMARY &&
                                    it.sets.any {set->
                                        set.type==DraftSetType.WORKING
                                    }
                            }
                        }
                        if(actual<days)
                            add(CustomCoachIssueKind.PRIMARY_SKILL_FREQUENCY,
                                "week.$weekNumber.primary.$exerciseId",
                                "This athlete-authored week does not reach the coached primary lift frequency.")
                    }
                }
            }
        }
        // Compare EVERY consecutive pair of actual resolved training
        // weeks, including authored week overrides inside a block. MEV by
        // itself does not show that a 5-to-3 set drop was planned or safe.
        // Separate RECOVERY/PERFORMANCE_TEST purposes preserve explicitly
        // intended deloads rather than disguising TRAINING reductions.
        val trainingPairs=measurements.zipWithNext().filter {
            (earlier,later) ->
            earlier.blockPurpose == CustomBlockPurpose.TRAINING &&
                later.blockPurpose == CustomBlockPurpose.TRAINING
        }
        val requiresContinuity=trainingPairs.any { (earlier,later) ->
            earlier.blockId != later.blockId ||
                earlier.workingSets > later.workingSets ||
                TrainingMuscle.entries.any { muscle ->
                    (earlier.directSets[muscle] ?: zero) >
                        (later.directSets[muscle] ?: zero)
                }
        }
        val missingContinuity=requiresContinuity &&
            inputs.continuityPolicy == null
        if (missingContinuity)
            add(CustomCoachIssueKind.CONTINUITY_POLICY_MISSING,
                "continuity",
                "Training-phase transitions and within-block weekly dose reductions need an explicitly reviewed total/direct-muscle continuity policy; meeting separate MEVs is not permission to erase work.")
        val continuity = inputs.continuityPolicy
        if (continuity != null) {
            val totalLimit = continuity.maximumTotalWorkingSetDropFraction
            val directLimit = continuity.maximumDirectMuscleDropFraction
            val valid = listOf(totalLimit, directLimit).all {
                it >= zero && it <= BigDecimal.ONE &&
                    it.stripTrailingZeros().scale() <= 3
            }
            if (!valid) {
                add(CustomCoachIssueKind.CONTINUITY_POLICY_INVALID,
                    "continuity",
                    "Specify reviewed volume-drop ceilings between zero and one.")
            } else {
                for ((earlier, later) in trainingPairs) {
                    if (earlier.workingSets > later.workingSets &&
                        earlier.workingSets > 0) {
                        val decline = BigDecimal(earlier.workingSets - later.workingSets)
                        val allowed = BigDecimal(earlier.workingSets).multiply(totalLimit)
                        if (decline > allowed)
                            add(CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
                                "week.${later.absoluteWeek}.block.${later.blockId}",
                                "Consecutive training weeks drop from ${earlier.workingSets} to ${later.workingSets} working sets (week ${earlier.absoluteWeek} to ${later.absoluteWeek}), exceeding the explicitly reviewed reduction limit.")
                    }
                    for (muscle in TrainingMuscle.entries) {
                        val before = earlier.directSets[muscle] ?: zero
                        val after = later.directSets[muscle] ?: zero
                        if (before > zero && after < before &&
                            before.subtract(after) > before.multiply(directLimit))
                            add(CustomCoachIssueKind.DIRECT_MUSCLE_DROP,
                                "week.${later.absoluteWeek}.${muscle.name}",
                                "Direct work for this muscle falls beyond the reviewed transition limit.")
                    }
                }
            }
        }

        // Unknown amplifier working-time makes a complete exact session
        // time audit impossible; do not treat under-estimated minutes as ready.
        if (structural.verifiedDraft!=null &&
            cycle.blocks.any {block->block.days.any{day->day.slots.any{slot->
                slot.sets.any{it.amplifier!=null}
            }}}) {
            add(CustomCoachIssueKind.NON_SET_TIME_UNDECLARED,"amplifiers",
                "Advanced-set follow-up work duration must be explicitly specified before a hard total-time audit can pass.")
        }
        val status=when {
            profile.athleteProfileId!=cycle.athleteId ||
                profile.goal!=cycle.goal ||
                structural.verifiedDraft==null ->
                CustomCoachAuditStatus.INVALID_SOURCE
            issues.isEmpty()->CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW
            policyIssue || missingContinuity ->
                CustomCoachAuditStatus.COACHING_POLICY_REQUIRED
            else->CustomCoachAuditStatus.UNRESOLVED
        }
        return CustomCoachAudit(status,
            Collections.unmodifiableList(issues.toList()),
            structural,Collections.unmodifiableList(measurements),
            profile.profileRevision,cycle.revision,
            inputs.policiesByPhase.mapValues {it.value.revision})
    }
}
