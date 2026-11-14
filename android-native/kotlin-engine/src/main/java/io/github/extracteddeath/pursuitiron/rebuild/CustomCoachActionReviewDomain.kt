package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Explanation and athlete-reviewable alternatives for an existing VERIFIED
 * authored program. This is NOT another progression system and never makes
 * up MEV/MAV/MRV, fatigue tolerance, daily volume or a new prescription.
 *
 * The full-cycle coach audit alone decides whether a confirmed dose, time,
 * frequency or transition policy was violated. Changes without a confirmed
 * policy are described as factual comparisons, never called unsafe.
 */
enum class CustomCoachActionKind {
    EDIT_AUTHORED_WEEK, REVIEW_TRAINING_DAYS,
    REVIEW_COACH_POLICY, DECLARE_SESSION_TIME,
    INSPECT_EXERCISE_STOCK
}
data class CustomCoachActionOption(
    val kind:CustomCoachActionKind,
    val title:String,
    val explanation:String
)
enum class CustomCoachFindingStatus {
    COACH_POLICY_BLOCKER, CONFIRMED_POLICY_VIOLATION, OBSERVED_CHANGE
}
data class CustomCoachActionFinding(
    val status:CustomCoachFindingStatus,
    val location:String,
    val title:String,
    val explanation:String,
    val absoluteWeek:Int?,
    val blockId:String?,
    val comparison:String?,
    val options:List<CustomCoachActionOption>
)
data class CustomCoachActionReview(
    val cycleRevision:Long,
    val findings:List<CustomCoachActionFinding>,
    /** No UI tap may grant a mutation, approve a policy or issue a workout. */
    val mayApplyChange:Boolean = false,
    val mayApprovePolicy:Boolean = false,
    val mayIssueWorkout:Boolean = false
)
enum class CustomCoachActionRefusal {
    SOURCE_UNVERIFIED, STRUCTURE_UNVERIFIED, EVIDENCE_UNVERIFIED
}
sealed interface CustomCoachActionReviewResult {
    data class Reviewed(val review:CustomCoachActionReview):
        CustomCoachActionReviewResult
    data class Rejected(val reason:CustomCoachActionRefusal):
        CustomCoachActionReviewResult
}

object CustomCoachActionReviewDomain {
    private const val MAX_FINDINGS=96
    private val weekPath=Regex("^week\\.(\\d+)(?:\\..*)?$")
    private val structuralBlockers=setOf(
        CustomCoachIssueKind.SETUP_UNRESOLVED,
        CustomCoachIssueKind.ATHLETE_MISMATCH,
        CustomCoachIssueKind.INVALID_CUSTOM_CYCLE,
        CustomCoachIssueKind.CATALOG_UNVERIFIED,
        CustomCoachIssueKind.ATHLETE_SCHEDULE_CONFLICT,
        CustomCoachIssueKind.EXCLUDED_EXERCISE,
        CustomCoachIssueKind.BODYWEIGHT_DISABLED,
        CustomCoachIssueKind.SUPERSET_DISALLOWED,
        CustomCoachIssueKind.BARBELL_LIMIT,
        CustomCoachIssueKind.EXERCISE_ROLE_CONFLICT,
        CustomCoachIssueKind.PROSPECTIVE_CONTEXT_INVALID,
        CustomCoachIssueKind.REVIEW_CAP_EXCEEDED
    )
    private val verifiedViolations=setOf(
        CustomCoachIssueKind.SESSION_HARD_LIMIT,
        CustomCoachIssueKind.BELOW_WEEKLY_MEV,
        CustomCoachIssueKind.ABOVE_WEEKLY_MRV,
        CustomCoachIssueKind.INSUFFICIENT_DIRECT_FREQUENCY,
        CustomCoachIssueKind.PRIMARY_SKILL_FREQUENCY,
        CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
        CustomCoachIssueKind.UNJUSTIFIED_TRAINING_VOLUME_SPIKE,
        CustomCoachIssueKind.DIRECT_MUSCLE_DROP,
        CustomCoachIssueKind.DIRECT_MUSCLE_SPIKE
    )

    fun review(audit:CustomCoachAudit):CustomCoachActionReviewResult {
        fun reject(reason:CustomCoachActionRefusal)=
            CustomCoachActionReviewResult.Rejected(reason)
        val structural=audit.structuralReview?.verifiedDraft
            ?:return reject(CustomCoachActionRefusal.STRUCTURE_UNVERIFIED)
        if(audit.sourceAthleteRevision.isNullOrBlank() ||
            audit.auditedCycleRevision!=structural.revision ||
            audit.issues.any{it.kind in structuralBlockers})
            return reject(CustomCoachActionRefusal.SOURCE_UNVERIFIED)
        // Never trust a partial/caller-provided measurement range. This
        // repeats the established full-cycle evidence consistency checks.
        val measured=(CustomCoachPolicyEvidenceDomain.review(audit)
            as? CustomCoachPolicyEvidenceReview.Reviewed)?.evidence
            ?:return reject(CustomCoachActionRefusal.EVIDENCE_UNVERIFIED)
        val weeks=audit.weeks.associateBy{it.absoluteWeek}
        val findings=mutableListOf<CustomCoachActionFinding>()
        fun option(kind:CustomCoachActionKind,title:String,explanation:String)=
            CustomCoachActionOption(kind,title,explanation)
        fun add(finding:CustomCoachActionFinding) {
            if(findings.size<MAX_FINDINGS)findings+=finding
        }
        val orderedIssues=audit.issues.sortedWith(
            compareBy<CustomCoachIssue> {
                when {
                    it.kind in verifiedViolations -> 0
                    it.kind==CustomCoachIssueKind.REVIEWED_POLICY_MISSING ||
                        it.kind==CustomCoachIssueKind.GOAL_POLICY_UNRESOLVED ||
                        it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_MISSING ||
                        it.kind==CustomCoachIssueKind.CONTINUITY_POLICY_INVALID -> 1
                    else -> 2
                }
            }.thenBy{it.location}.thenBy{it.kind.name})
        for(issue in orderedIssues) {
            if(issue.kind in structuralBlockers)continue
            val week=weekPath.matchEntire(issue.location)
                ?.groupValues?.get(1)?.toIntOrNull()
                ?.takeIf{it in weeks}
            val blockId=week?.let{weeks[it]?.blockId}
                ?:structural.blocks.firstOrNull {
                    issue.location.contains("block.${it.blockId}")
                }?.blockId
            val measures=week?.let{weeks[it]}
            val kind=issue.kind
            val status=if(kind in verifiedViolations)
                CustomCoachFindingStatus.CONFIRMED_POLICY_VIOLATION
            else CustomCoachFindingStatus.COACH_POLICY_BLOCKER
            val (title,options)=when(kind) {
                CustomCoachIssueKind.BELOW_WEEKLY_MEV ->
                    "Below the reviewed weekly volume minimum" to listOf(
                        option(CustomCoachActionKind.EDIT_AUTHORED_WEEK,
                            "Review this week's existing exercise sets",
                            "Inspect the affected muscle's direct and indirect set credits, then edit the authored week if a reviewed training prescription supports it."),
                        option(CustomCoachActionKind.REVIEW_COACH_POLICY,
                            "Verify the approved muscle-dose policy",
                            "Confirm the phase-specific lower bound against athlete context. Never lower it just to make an inadequate program pass."))
                CustomCoachIssueKind.ABOVE_WEEKLY_MRV ->
                    "Above the reviewed weekly volume ceiling" to listOf(
                        option(CustomCoachActionKind.EDIT_AUTHORED_WEEK,
                            "Review overload and duplicate exposures",
                            "Inspect the authored working sets and overrides before making an explicit athlete-approved correction."),
                        option(CustomCoachActionKind.REVIEW_TRAINING_DAYS,
                            "Review volume distribution",
                            "Compare the scheduled days and direct-muscle exposure without automatically deleting exercises."))
                CustomCoachIssueKind.INSUFFICIENT_DIRECT_FREQUENCY,
                CustomCoachIssueKind.PRIMARY_SKILL_FREQUENCY ->
                    "Below the reviewed training frequency" to listOf(
                        option(CustomCoachActionKind.REVIEW_TRAINING_DAYS,
                            "Inspect the existing weekly schedule",
                            "Check the affected muscle or primary lift across actual training days. Any changed frequency needs a new full-cycle review."))
                CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
                CustomCoachIssueKind.DIRECT_MUSCLE_DROP ->
                    "Decline exceeds the reviewed continuity policy" to listOf(
                        option(CustomCoachActionKind.EDIT_AUTHORED_WEEK,
                            "Inspect the next training week's sets",
                            "Compare both TRAINING weeks, their overrides and direct-muscle credits. Preserve custom exercises unless a specific reviewed edit is accepted."),
                        option(CustomCoachActionKind.REVIEW_COACH_POLICY,
                            "Review the phase-transition rationale",
                            "A strength or peak phase may prioritize different work, but the approved continuity ceiling still applies."))
                CustomCoachIssueKind.UNJUSTIFIED_TRAINING_VOLUME_SPIKE,
                CustomCoachIssueKind.DIRECT_MUSCLE_SPIKE ->
                    "Increase exceeds the reviewed continuity policy" to listOf(
                        option(CustomCoachActionKind.EDIT_AUTHORED_WEEK,
                            "Inspect the workload increase",
                            "Compare working sets, overrides and muscle credits across the two training weeks before manually adjusting a week."))
                CustomCoachIssueKind.SESSION_HARD_LIMIT ->
                    "Workout exceeds the athlete's hard time limit" to listOf(
                        option(CustomCoachActionKind.EDIT_AUTHORED_WEEK,
                            "Inspect total session work and rest",
                            "Review exact set timing and confirmed overhead before deciding whether to shorten or redistribute the session."),
                        option(CustomCoachActionKind.DECLARE_SESSION_TIME,
                            "Verify non-set duration",
                            "Check measured station changes, setup and cooldown. Do not omit real time to force an approval."))
                CustomCoachIssueKind.NON_SET_TIME_UNDECLARED ->
                    "Session duration evidence is incomplete" to listOf(
                        option(CustomCoachActionKind.DECLARE_SESSION_TIME,
                            "Provide actual non-set duration",
                            "Declare real setup, transitions and cooldown; unresolved amplifier work durations cannot be guessed."))
                CustomCoachIssueKind.REVIEWED_POLICY_MISSING,
                CustomCoachIssueKind.GOAL_POLICY_UNRESOLVED ->
                    "A complete phase coaching policy is required" to listOf(
                        option(CustomCoachActionKind.REVIEW_COACH_POLICY,
                            "Review phase-specific MEV, MAV, MRV and frequency",
                            "Use actual athlete and exercise evidence, not the number of sets already scheduled, to choose and justify the complete muscle target scope."))
                CustomCoachIssueKind.CONTINUITY_POLICY_MISSING,
                CustomCoachIssueKind.CONTINUITY_POLICY_INVALID ->
                    "A reviewed week-to-week continuity policy is required" to listOf(
                        option(CustomCoachActionKind.REVIEW_COACH_POLICY,
                            "Review ramp and decline limits",
                            "Compare adjacent TRAINING weeks across phase and recovery boundaries; enter athlete-specific ceilings rather than using an invented default."))
                else -> "Review the unresolved coaching constraint" to listOf(
                    option(CustomCoachActionKind.REVIEW_COACH_POLICY,
                        "Inspect the exact audit finding",
                        "Resolve the cited source constraint and rerun the full native cycle review."))
            }
            val compare=measures?.let{
                "Week ${it.absoluteWeek} · ${it.workingSets} authored working sets · ${it.phase.name.lowercase()}."
            }
            add(CustomCoachActionFinding(
                status,issue.location,title,issue.explanation,
                week,blockId,compare,
                Collections.unmodifiableList(options)))
        }

        // Pure observations: when no explicit athlete-specific continuity
        // policy is present a change is NOT a confirmed violation.
        // Do not repeat an already-confirmed ramp/drop finding as another
        // allegedly independent coaching problem.
        val continuityViolations=setOf(
            CustomCoachIssueKind.UNJUSTIFIED_BLOCK_VOLUME_DROP,
            CustomCoachIssueKind.UNJUSTIFIED_TRAINING_VOLUME_SPIKE)
        for(change in measured.transitions) {
            if(change.workingSetsBefore==change.workingSetsAfter)continue
            if(audit.issues.any {
                    it.kind in continuityViolations &&
                        weekPath.matchEntire(it.location)?.groupValues?.get(1)
                            ?.toIntOrNull()==change.toWeek
                })continue
            add(CustomCoachActionFinding(
                CustomCoachFindingStatus.OBSERVED_CHANGE,
                "week.${change.toWeek}.training-workload",
                "Training-week workload changed",
                "The authored program schedules ${change.workingSetsBefore} working sets in training week ${change.fromWeek} and ${change.workingSetsAfter} in training week ${change.toWeek}. This comparison alone is not proof of an unsafe or beneficial change; review the athlete-specific continuity decision.",
                change.toWeek,weeks[change.toWeek]?.blockId,
                "Week ${change.fromWeek} → ${change.toWeek} · ${change.fromPhase.name.lowercase()} → ${change.toPhase.name.lowercase()}",
                listOf(
                    option(CustomCoachActionKind.EDIT_AUTHORED_WEEK,
                        "Compare the original workouts and overrides",
                        "Inspect which authored slots and working sets changed without modifying them."),
                    option(CustomCoachActionKind.REVIEW_COACH_POLICY,
                        "Evaluate whether the change fits the athlete",
                        "Use an explicitly reviewed continuity ceiling and a full-cycle re-audit before approving an adjustment."))))
        }
        return CustomCoachActionReviewResult.Reviewed(
            CustomCoachActionReview(audit.auditedCycleRevision,
                Collections.unmodifiableList(findings)))
    }
}
