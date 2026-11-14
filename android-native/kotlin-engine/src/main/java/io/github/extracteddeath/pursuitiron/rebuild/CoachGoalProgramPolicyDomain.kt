package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Audits an explicitly AUTHORED goal/experience/phase policy. No universal
 * training-volume presets, issuance, persistence or automatic approval.
 */
data class AuthoredGoalProgramPolicy(
    val policyId:String,
    val revision:String,
    val rationale:String,
    val athleteId:String,
    val athleteRevision:String,
    val goal:AthleteTrainingGoal,
    val experience:AthleteExperience,
    val split:ComposerSplit,
    val phase:TrainingPhase,
    val trainingDays:Int,
    val baselineBounds:List<DeclaredMuscleDoseBounds>,
    val selectedBounds:List<DeclaredMuscleDoseBounds>,
    val minimumPurposeDays:Map<ComposerSessionPurpose,Int>,
    /** Per-lift days required by a coach; never guessed from the goal. */
    val primaryLiftMinimumDays:Map<String,Int> = emptyMap()
)
enum class GoalPolicyIssueKind {
    POLICY_MISSING, INVALID_PROVENANCE, CONTEXT_MISMATCH, INVALID_DOSE,
    PRIORITY_NOT_APPLIED, DAY_PURPOSE_MISSING, PRIMARY_SKILL_MISSING,
    INVALID_LIFT_FREQUENCY
}
data class GoalPolicyIssue(
    val field:String,
    val kind:GoalPolicyIssueKind,
    val explanation:String
)
enum class GoalPolicyStatus { UNRESOLVED, MATCHED_FOR_REVIEW }
data class GoalPolicyReview(
    val status:GoalPolicyStatus,
    val issues:List<GoalPolicyIssue>
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
}

object CoachGoalProgramPolicyDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")
    private val zero=BigDecimal.ZERO
    private val cap=BigDecimal("200")

    private fun valid(b:List<DeclaredMuscleDoseBounds>,days:Int)=
        TrainingMuscleCoverage.isComplete(b.map{it.muscle}) &&
            b.all {
                it.mev>=zero && it.mev<=it.mav && it.mav<=it.mrv &&
                    it.mrv<=cap && it.minimumDirectDays in 0..days &&
                    listOf(it.mev,it.mav,it.mrv).all {
                        value->value.stripTrailingZeros().scale()<=3
                    }
            }
    private fun doseEqual(a:DeclaredMuscleDoseBounds,
        b:DeclaredMuscleDoseBounds):Boolean =
        a.mev.compareTo(b.mev)==0 && a.mav.compareTo(b.mav)==0 &&
            a.mrv.compareTo(b.mrv)==0 &&
            a.minimumDirectDays==b.minimumDirectDays

    fun review(
        athlete:ReviewedAthleteSetup,
        phase:TrainingPhase,
        proposedBounds:List<DeclaredMuscleDoseBounds>,
        intents:List<AthleteCalendarIntent>,
        policy:AuthoredGoalProgramPolicy?
    ):GoalPolicyReview {
        val issues=mutableListOf<GoalPolicyIssue>()
        fun fail(field:String,kind:GoalPolicyIssueKind,reason:String) {
            issues+=GoalPolicyIssue(field,kind,reason)
        }
        fun report()=GoalPolicyReview(
            if(issues.isEmpty())GoalPolicyStatus.MATCHED_FOR_REVIEW
            else GoalPolicyStatus.UNRESOLVED,
            Collections.unmodifiableList(issues.toList()))
        if(policy==null) {
            fail("goalPolicy",GoalPolicyIssueKind.POLICY_MISSING,
                "No goal- and experience-specific authored coaching policy was supplied.")
            return report()
        }
        if(!id.matches(policy.policyId) || !id.matches(policy.revision) ||
            policy.rationale.isBlank() || policy.rationale.length>400)
            fail("goalPolicy.reference",GoalPolicyIssueKind.INVALID_PROVENANCE,
                "The policy needs a stable version and a concise authored rationale.")
        if(policy.athleteId!=athlete.athleteProfileId ||
            policy.athleteRevision!=athlete.profileRevision ||
            policy.goal!=athlete.goal ||
            policy.experience!=athlete.experience ||
            policy.split!=athlete.split ||
            policy.phase!=phase ||
            policy.trainingDays!=athlete.trainingWeekdays.size)
            fail("goalPolicy.context",GoalPolicyIssueKind.CONTEXT_MISMATCH,
                "The policy targets a different athlete, profile revision, goal, experience, split, phase or frequency.")

        val count=athlete.trainingWeekdays.size
        if(!valid(policy.baselineBounds,count) ||
            !valid(policy.selectedBounds,count) ||
            !valid(proposedBounds,count) ||
            policy.baselineBounds.map{it.muscle}.toSet()!=
                policy.selectedBounds.map{it.muscle}.toSet() ||
            proposedBounds.map{it.muscle}.toSet()!=
                policy.selectedBounds.map{it.muscle}.toSet() ||
            !(policy.selectedBounds.map{it.muscle}.toSet()
                .containsAll(athlete.focusedMuscles+athlete.reducedMuscles)) ||
            policy.selectedBounds.associateBy {it.muscle}.any { (muscle,selected) ->
                !doseEqual(selected,proposedBounds.first {it.muscle==muscle})
            })
            fail("goalPolicy.dose",GoalPolicyIssueKind.INVALID_DOSE,
                "The complete muscle scope, dose and direct-frequency targets must match the reviewed policy, including every focused or reduced muscle.")
        else {
            val previous=policy.baselineBounds.associateBy {it.muscle}
            val selected=policy.selectedBounds.associateBy {it.muscle}
            for(muscle in athlete.focusedMuscles) {
                val before=previous.getValue(muscle)
                val after=selected.getValue(muscle)
                if(after.mev<before.mev || after.mav<before.mav ||
                    after.mrv<before.mrv ||
                    after.minimumDirectDays<before.minimumDirectDays ||
                    (after.mev.compareTo(before.mev)==0 &&
                        after.mav.compareTo(before.mav)==0 &&
                        after.minimumDirectDays==before.minimumDirectDays))
                    fail("goalPolicy.focus."+muscle.name,
                        GoalPolicyIssueKind.PRIORITY_NOT_APPLIED,
                        "The selected policy did not actually increase this muscle's planned dose or frequency.")
            }
            for(muscle in athlete.reducedMuscles) {
                val before=previous.getValue(muscle)
                val after=selected.getValue(muscle)
                if(after.mev>before.mev || after.mav>before.mav ||
                    after.mrv>before.mrv ||
                    after.minimumDirectDays>before.minimumDirectDays ||
                    (after.mev.compareTo(before.mev)==0 &&
                        after.mav.compareTo(before.mav)==0 &&
                        after.minimumDirectDays==before.minimumDirectDays))
                    fail("goalPolicy.reduced."+muscle.name,
                        GoalPolicyIssueKind.PRIORITY_NOT_APPLIED,
                        "The selected policy did not actually reduce this muscle's planned dose or frequency.")
            }
        }
        if(policy.primaryLiftMinimumDays.size>16 ||
            policy.primaryLiftMinimumDays.any { (lift,days) ->
                !id.matches(lift) || days !in 1..count
            })
            fail("goalPolicy.primaryFrequency",GoalPolicyIssueKind.INVALID_LIFT_FREQUENCY,
                "The policy needs valid stable primary exercise IDs and attainable weekday frequencies.")
        val counted=intents.groupingBy {it.purpose}.eachCount()
        if(policy.minimumPurposeDays.isEmpty() ||
            policy.minimumPurposeDays.any { (purpose,needed) ->
                purpose==ComposerSessionPurpose.UNSPECIFIED ||
                    needed !in 1..count || (counted[purpose] ?: 0)<needed
            })
            fail("goalPolicy.purposes",GoalPolicyIssueKind.DAY_PURPOSE_MISSING,
                "Explicitly authored training-day purposes have not all been scheduled.")
        val required=when(phase) {
            TrainingPhase.HYPERTROPHY -> ComposerSessionPurpose.HYPERTROPHY
            TrainingPhase.STRENGTH,TrainingPhase.PEAK ->
                ComposerSessionPurpose.STRENGTH_SKILL
        }
        if((policy.minimumPurposeDays[required] ?: 0)<1)
            fail("goalPolicy.phase",GoalPolicyIssueKind.DAY_PURPOSE_MISSING,
                "The selected phase lacks its stated core day purpose.")
        if(phase!=TrainingPhase.HYPERTROPHY && intents.none {
            it.purpose==ComposerSessionPurpose.STRENGTH_SKILL &&
                !it.requiredPrimaryExerciseId.isNullOrBlank()
        })
            fail("goalPolicy.primary",GoalPolicyIssueKind.PRIMARY_SKILL_MISSING,
                "A strength/peak phase requires a named main lift on a skill day.")
        return report()
    }
}
