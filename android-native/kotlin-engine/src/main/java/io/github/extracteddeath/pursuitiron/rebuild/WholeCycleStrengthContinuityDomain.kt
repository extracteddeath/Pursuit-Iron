package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Prevents a future independently generated complete cycle from treating a
 * source-verified 8 -> 3 Strength day or half-dose transition as a finished
 * athlete-ready plan merely because the week-level dose audit passes.
 *
 * This is one read-only composition of the EXISTING strength roster and dose
 * boundary rules, not a new muscle-dose target or a second program generator.
 * Both comparisons use the true last hypertrophy training week (including
 * overrides), even across approved recovery/test blocks. Changed splits and
 * intentional Peak tapers stay with their own audits.
 */
enum class WholeCycleStrengthContinuityStatus {
    SOURCE_UNVERIFIED, CONTINUOUS, REQUIRES_ATHLETE_REVIEW
}

data class WholeCycleStrengthBoundaryFinding(
    val blockId:String,
    val roster:StrengthRosterBoundaryReview,
    val dose:StrengthDoseBoundaryReview
) {
    val affectedWeekdays:Set<Int> get()=
        (roster.affectedDays.map{it.weekday}+
            dose.affectedDays.map{it.weekday}).toSortedSet()
    val hasSevereRosterLoss:Boolean get()=
        roster.status==StrengthRosterBoundaryStatus.MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW
    val hasHalvedWorkingDose:Boolean get()=
        dose.status==StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW
}

data class WholeCycleStrengthContinuityReview(
    val status:WholeCycleStrengthContinuityStatus,
    val findings:List<WholeCycleStrengthBoundaryFinding>,
    val explanation:String
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
}

object WholeCycleStrengthContinuityDomain {
    fun review(structure:CustomCycleReview?):WholeCycleStrengthContinuityReview {
        fun refuse()=WholeCycleStrengthContinuityReview(
            WholeCycleStrengthContinuityStatus.SOURCE_UNVERIFIED,emptyList(),
            "The issued-cycle source cannot verify the actual strength phase boundary.")
        val cycle=structure?.verifiedDraft ?:return refuse()
        if(structure.status!=CustomCycleReviewStatus.READY_FOR_COACH_REVIEW)
            return refuse()
        val findings=mutableListOf<WholeCycleStrengthBoundaryFinding>()
        for(block in cycle.blocks) {
            if(block.phase!=TrainingPhase.STRENGTH ||
                block.purpose!=CustomBlockPurpose.TRAINING)continue
            val roster=StrengthRosterBoundaryGuardDomain.review(
                structure,block.blockId)
            val dose=StrengthDoseBoundaryGuardDomain.review(
                structure,block.blockId)
            // Never turn a partial or corrupt week into a "continuous" claim.
            if(roster.status==StrengthRosterBoundaryStatus.SOURCE_UNVERIFIED ||
                dose.status==StrengthDoseBoundaryStatus.SOURCE_UNVERIFIED)
                return refuse()
            if(roster.status==
                    StrengthRosterBoundaryStatus.MATERIAL_EXERCISE_LOSS_REQUIRES_REVIEW ||
                dose.status==
                    StrengthDoseBoundaryStatus.HALVED_WORKING_DOSE_REQUIRES_REVIEW)
                findings+=WholeCycleStrengthBoundaryFinding(
                    block.blockId,roster,dose)
        }
        val frozen=Collections.unmodifiableList(findings.toList())
        if(findings.isEmpty())
            return WholeCycleStrengthContinuityReview(
                WholeCycleStrengthContinuityStatus.CONTINUOUS,frozen,
                "No comparable source-verified hypertrophy-to-strength boundary has a severe exercise-roster or working-dose collapse.")
        val facts=findings.joinToString("; ") { finding ->
            val rosterLoss=finding.roster.affectedDays.joinToString(", ") {
                "day ${it.weekday}: ${it.beforeWorkingExercises} to ${it.afterWorkingExercises} exercises"
            }
            val doseLoss=finding.dose.affectedDays.joinToString(", ") {
                "day ${it.weekday}: ${it.beforeWorkingSets} to ${it.afterWorkingSets} working sets"
            }
            val total=if(finding.hasHalvedWorkingDose &&
                finding.dose.hypertrophyWorkingSets!=null &&
                finding.dose.strengthWorkingSets!=null)
                "week ${finding.dose.hypertrophyWorkingSets} to ${finding.dose.strengthWorkingSets} working sets"
                else ""
            listOf("Strength ${finding.blockId}",
                rosterLoss.takeIf{it.isNotEmpty()},
                doseLoss.takeIf{it.isNotEmpty()},
                total.takeIf{it.isNotEmpty()}).filterNotNull().joinToString(" · ")
        }
        return WholeCycleStrengthContinuityReview(
            WholeCycleStrengthContinuityStatus.REQUIRES_ATHLETE_REVIEW,
            frozen,"Strength transition needs explicit review: $facts. "+
                "No exercise, load, set or saved program was changed.")
    }
}
