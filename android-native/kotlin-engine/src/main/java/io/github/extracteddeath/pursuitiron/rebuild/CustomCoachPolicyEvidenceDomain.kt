package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.math.RoundingMode
import java.util.Collections

/**
 * Factual, READ-ONLY evidence from the SAME independently reviewed weekly
 * measurements used by the full custom-cycle coach audit. This is what the
 * athlete-authored program contains, NOT a prediction of adaptation and NOT
 * a physiological MEV, MAV, MRV or safe ramp percentage.
 *
 * Missing or unverified catalog/structure/source evidence fails closed. In
 * particular, empty/missing muscle credits may never be presented as zero.
 */
data class CustomObservedMuscleRange(
    val muscle:TrainingMuscle,
    val minimumWeeklyCredit:BigDecimal,
    val maximumWeeklyCredit:BigDecimal,
    val minimumWeeklyDirectCredit:BigDecimal,
    val maximumWeeklyDirectCredit:BigDecimal,
    val minimumDirectWeekdays:Int,
    val maximumDirectWeekdays:Int
)
data class CustomObservedPhaseWorkload(
    val phase:TrainingPhase,
    val trainingWeekCount:Int,
    val minimumWorkingSets:Int,
    val maximumWorkingSets:Int,
    val muscleRanges:Map<TrainingMuscle,CustomObservedMuscleRange>,
    /** Known authored set work and rest only. Excludes unreported non-set time. */
    val minimumKnownSecondsByDay:Map<Int,Long>,
    val maximumKnownSecondsByDay:Map<Int,Long>
)
data class CustomObservedTrainingTransition(
    val fromWeek:Int,
    val toWeek:Int,
    val fromPhase:TrainingPhase,
    val toPhase:TrainingPhase,
    val workingSetsBefore:Int,
    val workingSetsAfter:Int,
    /** Change relative to earlier week; null when no earlier sets. */
    val workingSetChangeFraction:BigDecimal?
)
data class CustomCoachPolicyEvidence(
    val auditedCycleRevision:Long,
    val phaseWorkloads:Map<TrainingPhase,CustomObservedPhaseWorkload>,
    val transitions:List<CustomObservedTrainingTransition>
) {
    val canSupplyDoseTargets:Boolean get()=false
    val canSupplyContinuityCeilings:Boolean get()=false
    val canIssueWorkout:Boolean get()=false
}
enum class CustomCoachEvidenceRefusal {
    UNVERIFIED_SOURCE, UNVERIFIED_STRUCTURE, UNVERIFIED_CATALOG,
    INCOMPLETE_WEEKLY_MEASUREMENTS, NO_TRAINING_WEEKS
}
sealed interface CustomCoachPolicyEvidenceReview {
    data class Reviewed(val evidence:CustomCoachPolicyEvidence):
        CustomCoachPolicyEvidenceReview
    data class Rejected(val reason:CustomCoachEvidenceRefusal):
        CustomCoachPolicyEvidenceReview
}

/** Consume audit measurements, never recompute or infer exercise credit. */
object CustomCoachPolicyEvidenceDomain {
    private val zero=BigDecimal.ZERO
    fun review(audit:CustomCoachAudit):CustomCoachPolicyEvidenceReview {
        fun no(reason:CustomCoachEvidenceRefusal)=
            CustomCoachPolicyEvidenceReview.Rejected(reason)
        if(audit.issues.any{it.kind==CustomCoachIssueKind.CATALOG_UNVERIFIED})
            return no(CustomCoachEvidenceRefusal.UNVERIFIED_CATALOG)
        val authored=audit.structuralReview?.verifiedDraft
            ?:return no(CustomCoachEvidenceRefusal.UNVERIFIED_STRUCTURE)
        if(audit.sourceAthleteRevision.isNullOrBlank() ||
            audit.auditedCycleRevision!=authored.revision ||
            audit.issues.any{it.kind in setOf(
                CustomCoachIssueKind.SETUP_UNRESOLVED,
                CustomCoachIssueKind.ATHLETE_MISMATCH,
                CustomCoachIssueKind.ATHLETE_SCHEDULE_CONFLICT,
                CustomCoachIssueKind.EXCLUDED_EXERCISE,
                CustomCoachIssueKind.BODYWEIGHT_DISABLED,
                CustomCoachIssueKind.SUPERSET_DISALLOWED,
                CustomCoachIssueKind.BARBELL_LIMIT,
                CustomCoachIssueKind.EXERCISE_ROLE_CONFLICT,
                CustomCoachIssueKind.PROSPECTIVE_CONTEXT_INVALID
            )})
            return no(CustomCoachEvidenceRefusal.UNVERIFIED_SOURCE)
        val weeks=audit.weeks.sortedBy{it.absoluteWeek}
        if(weeks.size!=authored.blocks.sumOf{it.weeks} ||
            weeks.size !in 1..104 ||
            weeks.map{it.absoluteWeek}!=(1..weeks.size).toList())
            return no(CustomCoachEvidenceRefusal.INCOMPLETE_WEEKLY_MEASUREMENTS)
        var nextWeek=1
        for(block in authored.blocks)repeat(block.weeks) {
            val week=weeks.getOrNull(nextWeek-1)
            if(week==null || week.blockId!=block.blockId ||
                week.phase!=block.phase || week.blockPurpose!=block.purpose)
                return no(CustomCoachEvidenceRefusal.INCOMPLETE_WEEKLY_MEASUREMENTS)
            nextWeek++
        }
        if(weeks.any { week ->
                week.workingSets<0 ||
                !TrainingMuscleCoverage.isComplete(week.directSets.keys) ||
                week.totalSetCredits.keys!=week.directSets.keys ||
                week.directDays.keys!=week.directSets.keys ||
                week.directSets.values.any{it<zero} ||
                week.totalSetCredits.values.any{it<zero} ||
                week.directDays.values.any{it !in 0..7} ||
                week.knownSecondsByDay.values.any{it<0} ||
                week.knownSecondsByDay.isEmpty()
            })
            return no(CustomCoachEvidenceRefusal.INCOMPLETE_WEEKLY_MEASUREMENTS)
        val training=weeks.filter{
            it.blockPurpose==CustomBlockPurpose.TRAINING
        }
        if(training.isEmpty())
            return no(CustomCoachEvidenceRefusal.NO_TRAINING_WEEKS)
        val byPhase=linkedMapOf<TrainingPhase,CustomObservedPhaseWorkload>()
        for((phase,group) in training.groupBy{it.phase}) {
            val muscles=linkedMapOf<TrainingMuscle,CustomObservedMuscleRange>()
            // A newly tracked muscle has no earlier zero-dose observation.
            // Report only coverage measured in EVERY week of this phase;
            // later policy authoring still requires explicit added targets.
            val shared=group.map{it.directSets.keys}.reduce{a,b->a.intersect(b)}
            for(muscle in shared.sortedBy{it.ordinal}) {
                val credits=group.map{it.totalSetCredits.getValue(muscle)}
                val direct=group.map{it.directSets.getValue(muscle)}
                val weekdays=group.map{it.directDays.getValue(muscle)}
                muscles[muscle]=CustomObservedMuscleRange(
                    muscle,credits.minOrNull()!!,credits.maxOrNull()!!,
                    direct.minOrNull()!!,direct.maxOrNull()!!,
                    weekdays.minOrNull()!!,weekdays.maxOrNull()!!)
            }
            val observedDays=group.flatMap{it.knownSecondsByDay.keys}
                .distinct().sorted()
            val minimum=linkedMapOf<Int,Long>()
            val maximum=linkedMapOf<Int,Long>()
            for(day in observedDays) {
                val values=group.mapNotNull{it.knownSecondsByDay[day]}
                // Never conceal a missing day in the comparison.
                if(values.size!=group.size)
                    return no(CustomCoachEvidenceRefusal.INCOMPLETE_WEEKLY_MEASUREMENTS)
                minimum[day]=values.minOrNull()!!
                maximum[day]=values.maxOrNull()!!
            }
            byPhase[phase]=CustomObservedPhaseWorkload(
                phase,group.size,group.minOf{it.workingSets},
                group.maxOf{it.workingSets},
                Collections.unmodifiableMap(muscles),
                Collections.unmodifiableMap(minimum),
                Collections.unmodifiableMap(maximum))
        }
        val transitions=training.zipWithNext().map{(before,after)->
            val change=if(before.workingSets==0)null else
                BigDecimal(after.workingSets-before.workingSets)
                    .divide(BigDecimal(before.workingSets),3,
                        RoundingMode.HALF_UP)
            CustomObservedTrainingTransition(
                before.absoluteWeek,after.absoluteWeek,
                before.phase,after.phase,
                before.workingSets,after.workingSets,change)
        }
        return CustomCoachPolicyEvidenceReview.Reviewed(
            CustomCoachPolicyEvidence(authored.revision,
                Collections.unmodifiableMap(byPhase),
                Collections.unmodifiableList(transitions)))
    }
}
