package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * First bounded WHOLE-CYCLE proposal path, built exclusively from independently
 * verified first-program phase prescriptions. Unlike a week-one-only plan, each
 * proposed cycle is re-audited across ALL weeks and phase boundaries.
 *
 * This is not a novel prescription engine: it never invents loads, set counts,
 * exercise transfers, gym availability or a peaking event. Block-length choices
 * are labeled coaching conventions and are NOT claimed to outperform other
 * periodization strategies. A source with missing phase evidence fails closed.
 */
data class ReviewedWholeCycleRequest(
    val cycleId:String,
    val title:String,
    val setup:AthleteSetupReview,
    val catalog:List<CatalogMovement>,
    val phaseStarts:Map<TrainingPhase,FirstProgramStartReview>,
    val totalWeeks:Int,
    /** A deliberately confirmed performance event, not inferred from goal. */
    val performanceTestPlanned:Boolean=false,
    /** An explicitly reviewed limit, NEVER inferred from generic MEV/MRV. */
    val continuityPolicy:CustomCoachVolumeContinuityPolicy?=null
)

enum class ReviewedWholeCycleCandidateStatus {
    READY_FOR_ATHLETE_REVIEW, MISSING_VERIFIED_PHASE,
    FULL_CYCLE_POLICY_REQUIRED, FULL_CYCLE_AUDIT_FAILED
}

data class ReviewedWholeCycleCandidate(
    val optionId:String,
    val allocations:List<Pair<TrainingPhase,Int>>,
    val status:ReviewedWholeCycleCandidateStatus,
    /** Full actual measured weeks are attached when verification was possible. */
    val audit:CustomCoachAudit?,
    val proposedCycle:CustomTrainingCycle?,
    val explanation:String
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
}

enum class ReviewedWholeCycleStatus {
    INVALID_INPUT, OPTIONS_AUDITED, NO_REVIEWABLE_CYCLE
}

data class ReviewedWholeCycleResult(
    val status:ReviewedWholeCycleStatus,
    val candidates:List<ReviewedWholeCycleCandidate>,
    /** A first reviewable convention, NOT a demonstrated global optimum. */
    val initialReviewOptionId:String?,
    val explanation:String
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
    val mayApplyAutomatically:Boolean get()=false
}

object CoachReviewedWholeCycleDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_OPTIONS=8

    private fun allocation(
        goal:AthleteTrainingGoal,weeks:Int,event:Boolean
    ):List<List<Pair<TrainingPhase,Int>>> {
        val h=TrainingPhase.HYPERTROPHY
        val s=TrainingPhase.STRENGTH
        val p=TrainingPhase.PEAK
        fun pair(a:Int,b:Int) = listOf(h to a,s to b)
        fun strengthPeak(peak:Int)=listOf(s to (weeks-peak),p to peak)
        fun three(hypertrophy:Int,peak:Int)=listOf(h to hypertrophy,
            s to (weeks-hypertrophy-peak),p to peak)
        val offers=when(goal) {
            AthleteTrainingGoal.HYPERTROPHY ->
                if(event)emptyList() else listOf(listOf(h to weeks))
            AthleteTrainingGoal.STRENGTH -> if(event) {
                if(weeks<3)emptyList() else
                    listOf(strengthPeak(1),strengthPeak(2))
                        .filter {it.all {(_,count)->count>=1} &&
                            it.first().second>=2}
            } else {
                listOf(listOf(s to weeks)) +
                    (if(weeks>=6)listOf(pair(maxOf(2,weeks/3),
                        weeks-maxOf(2,weeks/3))) else emptyList())
            }
            AthleteTrainingGoal.POWERBUILDING -> if(event) {
                if(weeks<5)emptyList() else
                    listOf(three(maxOf(2,(weeks-1)/2),1),
                        three(maxOf(2,(weeks-2)/2),2),
                        three(maxOf(2,(weeks-1)*2/3),1))
                        .filter {it.all {(_,count)->count>=1} &&
                            it[0].second>=2 && it[1].second>=2}
            } else {
                if(weeks<4)emptyList() else
                    listOf(pair(maxOf(2,weeks/2),weeks-maxOf(2,weeks/2)),
                        pair(maxOf(2,(weeks*2)/3),
                            weeks-maxOf(2,(weeks*2)/3)),
                        pair(maxOf(2,weeks/3),weeks-maxOf(2,weeks/3)))
                        .filter {it.all {(_,count)->count>=2}}
            }
        }
        return offers.filter {it.sumOf {entry->entry.second}==weeks}
            .distinct().take(MAX_OPTIONS)
    }

    fun propose(request:ReviewedWholeCycleRequest):ReviewedWholeCycleResult {
        val athlete=request.setup.reviewed
        if(athlete==null || !request.setup.mayReviewProgramCandidate ||
            !id.matches(request.cycleId) ||
            request.title.isBlank() || request.title.length>120 ||
            request.totalWeeks !in 1..26 ||
            request.phaseStarts.size>TrainingPhase.entries.size ||
            request.catalog.isEmpty() || request.catalog.size>256)
            return ReviewedWholeCycleResult(
                ReviewedWholeCycleStatus.INVALID_INPUT,emptyList(),null,
                "Confirm a reviewed athlete, catalog, cycle ID and 1–26 weeks.")
        val layouts=allocation(athlete.goal,request.totalWeeks,
            request.performanceTestPlanned)
        if(layouts.isEmpty())
            return ReviewedWholeCycleResult(
                ReviewedWholeCycleStatus.NO_REVIEWABLE_CYCLE,emptyList(),null,
                "No suitable convention fits the goal and weeks. A peak is never forced without an explicitly confirmed performance event.")
        val candidates=layouts.map {layout->
            val optionId=layout.joinToString("__") {(phase,weeks)->
                phase.name.lowercase()+"_"+weeks
            }
            var refusal:String?=null
            val blocks=mutableListOf<CustomTrainingBlock>()
            val policies=linkedMapOf<TrainingPhase,AuthoredGoalProgramPolicy>()
            var expectedTiming:Map<Int,Int>?=null
            for((phase,weeks) in layout) {
                val starter=request.phaseStarts[phase]
                if(starter==null) {
                    refusal="The verified ${phase.name.lowercase()} phase starter is missing."
                    break
                }
                // Both methods re-check the SAME reviewed athlete and
                // original audited prescription. A raw catalog list or a
                // suggested weight is not authority to generate a template.
                val original=CoachFirstProgramDraftConversionDomain.propose(
                    starter,request.setup,request.catalog,
                    request.cycleId+"_"+phase.name.lowercase(),
                    request.title,weeks)
                val sourcePolicy=CoachFirstProgramDraftConversionDomain
                    .policyForInitialFullCycleReview(starter,request.setup)
                if(original.status!=FirstProgramDraftStatus.READY_FOR_SAVING_DRAFT ||
                    original.cycle==null || sourcePolicy==null ||
                    starter.policy?.phase!=phase) {
                    refusal="The ${phase.name.lowercase()} starter lacks a complete, matching reviewed prescription and time policy."
                    break
                }
                val times=sourcePolicy.nonSetSecondsByWeekday
                if(expectedTiming!=null && expectedTiming!=times) {
                    refusal="The phase starters disagree about verified non-set session time; choose one reviewed schedule."
                    break
                }
                expectedTiming=times
                val policy=sourcePolicy.policiesByPhase[phase]
                if(policy==null) {
                    refusal="This phase does not have its own reviewed dose and exercise policy."
                    break
                }
                policies[phase]=policy
                val block=original.cycle.blocks.single()
                blocks+=block.copy(
                    blockId="phase_"+phase.name.lowercase(),
                    label=phase.name.lowercase().replaceFirstChar {it.uppercase()},
                    weeks=weeks,
                    purpose=CustomBlockPurpose.TRAINING)
            }
            val allocations=Collections.unmodifiableList(layout.toList())
            if(refusal!=null) {
                ReviewedWholeCycleCandidate(optionId,allocations,
                    ReviewedWholeCycleCandidateStatus.MISSING_VERIFIED_PHASE,
                    null,null,checkNotNull(refusal))
            } else {
                val proposed=CustomTrainingCycle(
                    request.cycleId,1,athlete.athleteProfileId,athlete.goal,
                    request.title,Collections.unmodifiableList(blocks.toList()),
                    authorNote="Provisional audited phase templates. Block durations are reviewable coaching conventions, not proven optimal; no progression or test performance was inferred.")
                val coaching=CustomCoachAuditInputs(
                    policiesByPhase=Collections.unmodifiableMap(policies),
                    nonSetSecondsByWeekday=checkNotNull(expectedTiming),
                    continuityPolicy=request.continuityPolicy)
                val audit=CustomCycleCoachAuditDomain.review(
                    proposed,request.setup,request.catalog,coaching)
                val status=when(audit.status) {
                    CustomCoachAuditStatus.READY_FOR_ATHLETE_REVIEW ->
                        ReviewedWholeCycleCandidateStatus.READY_FOR_ATHLETE_REVIEW
                    CustomCoachAuditStatus.COACHING_POLICY_REQUIRED ->
                        ReviewedWholeCycleCandidateStatus.FULL_CYCLE_POLICY_REQUIRED
                    else->ReviewedWholeCycleCandidateStatus.FULL_CYCLE_AUDIT_FAILED
                }
                ReviewedWholeCycleCandidate(optionId,allocations,
                    status,audit,proposed,
                    if(status==ReviewedWholeCycleCandidateStatus.READY_FOR_ATHLETE_REVIEW)
                        "All ${audit.weeks.size} projected weeks passed the independent phase, equipment, muscle-dose, time, frequency and transition audit. Requires athlete approval and later adaptation."
                    else "Every projected week was audited; unresolved policy or constraints prevent approval: "+
                        audit.issues.take(3).joinToString("; "){it.explanation})
            }
        }
        val initial=candidates.firstOrNull {
            it.status==ReviewedWholeCycleCandidateStatus.READY_FOR_ATHLETE_REVIEW
        }
        return ReviewedWholeCycleResult(
            if(initial==null)ReviewedWholeCycleStatus.NO_REVIEWABLE_CYCLE
            else ReviewedWholeCycleStatus.OPTIONS_AUDITED,
            Collections.unmodifiableList(candidates),initial?.optionId,
            if(initial==null)
                "No complete cycle passed source and full-cycle coaching review. Do not issue a workout or silently discard required phases."
            else "Review the first independently feasible convention alongside alternatives. It has not been shown to be physiologically superior; no workout has been issued.")
    }
}
