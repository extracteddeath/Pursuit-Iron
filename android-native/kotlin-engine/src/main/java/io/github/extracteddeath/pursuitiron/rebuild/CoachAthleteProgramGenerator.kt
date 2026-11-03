package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections
import kotlin.math.abs

/**
 * First source-backed generation pass: builds the week FOCUS and day PURPOSE
 * placements and schedules named priority lifts from an explicitly authored
 * policy. It delegates exercise/sets to the independently audited catalog
 * composer. It does not invent MEV/MAV/MRV, working loads or primary frequency.
 */
data class AthleteProgramGenerationRequest(
    val setup:AthleteSetupReview,
    val phase:TrainingPhase,
    val policy:AuthoredGoalProgramPolicy?,
    val catalog:List<CatalogMovement>,
    val choices:List<CatalogExerciseChoice>,
    val nonSetSecondsByWeekday:Map<Int,Int>,
    val maximumExercisesPerDay:Int,
    val preferredPatternsPerDay:Int,
    /**
     * Explicit distribution for unpaired split days. E.g. UL for 5 days
     * needs one UPPER or LOWER choice; ULPPL for 6 needs one extra focus.
     * Empty for 4-day UL, 3/6-day PPL, 5-day ULPPL and Full Body.
     */
    val extraFocuses:List<ComposerFocus> = emptyList()
)
enum class AthleteGenerationStatus {
    INPUTS_UNRESOLVED, PLACEMENTS_UNRESOLVED, GENERATED_FOR_REVIEW
}
enum class AthleteGenerationIssueKind {
    SETUP_UNRESOLVED, POLICY_UNRESOLVED, FOCUS_DISTRIBUTION_INVALID,
    PRIMARY_EXERCISE_UNAVAILABLE, SCHEDULE_CONFLICT, COMPOSED_WEEK_UNRESOLVED
}
data class AthleteGenerationIssue(
    val field:String,
    val kind:AthleteGenerationIssueKind,
    val explanation:String
)
data class AthleteProgramGenerationReview(
    val status:AthleteGenerationStatus,
    val issues:List<AthleteGenerationIssue>,
    val focusByWeekday:Map<Int,ComposerFocus>,
    val intents:List<AthleteCalendarIntent>,
    val candidate:AthleteCatalogCandidateReview?
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
}
object CoachAthleteProgramGenerator {
    private const val MAX_ASSIGNMENTS=16000

    private fun focusCounts(split:ComposerSplit,days:Int,
        extra:List<ComposerFocus>):Map<ComposerFocus,Int>? {
        val counts=linkedMapOf<ComposerFocus,Int>()
        when(split) {
            ComposerSplit.FULL_BODY -> counts[ComposerFocus.FULL_BODY]=days
            ComposerSplit.UPPER_LOWER -> {
                counts[ComposerFocus.UPPER]=days/2
                counts[ComposerFocus.LOWER]=days/2
            }
            ComposerSplit.PUSH_PULL_LEGS -> {
                for(focus in listOf(ComposerFocus.PUSH,ComposerFocus.PULL,
                    ComposerFocus.LEGS))counts[focus]=days/3
            }
            ComposerSplit.ULPPL -> {
                for(focus in listOf(ComposerFocus.UPPER,ComposerFocus.LOWER,
                    ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS))
                    counts[focus]=1
            }
        }
        val outstanding=days-counts.values.sum()
        if(extra.size!=outstanding ||
            extra.any {it !in counts.keys})return null
        for(value in extra)counts[value]=counts.getValue(value)+1
        return counts
    }

    private fun <T:Any> arrangements(counts:Map<T,Int>):List<List<T>> {
        val options=counts.keys.toList()
        val remaining=counts.toMutableMap()
        val result=mutableListOf<List<T>>()
        fun rec(path:MutableList<T>) {
            if(path.size==counts.values.sum()) {
                result+=path.toList()
                return
            }
            for(item in options)if(remaining.getValue(item)>0) {
                remaining[item]=remaining.getValue(item)-1
                path+=item
                rec(path)
                path.removeAt(path.lastIndex)
                remaining[item]=remaining.getValue(item)+1
            }
        }
        rec(mutableListOf())
        return result
    }

    private fun spacingPenalty(calendar:List<Int>,categories:List<String>):Int {
        var penalty=0
        for(i in categories.indices)for(j in 0 until i) {
            val raw=abs(calendar[i]-calendar[j])
            val gap=minOf(raw,7-raw)
            if(categories[i]==categories[j] && gap<2)
                penalty+=100
            if(categories[i].startsWith("upper:") &&
                categories[j].startsWith("upper:") && gap<2)
                penalty+=25
        }
        return penalty
    }

    private fun validPrimary(
        id:String,
        athlete:ReviewedAthleteSetup,
        catalog:Map<String,CatalogMovement>,
        choices:Map<String,CatalogExerciseChoice>
    ):Boolean {
        val spec=catalog[id] ?:return false
        val choice=choices[id] ?:return false
        if(spec.role!=ComposerRole.PRIMARY ||
            spec.creditStatus!=CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL ||
            spec.creditReviewReference==null ||
            id in athlete.excludedExerciseIds ||
            !athlete.gym.availableFacilities.containsAll(spec.requiredFacilityIds) ||
            (spec.attachmentId!=null &&
                spec.attachmentId !in athlete.gym.availableAttachments))
            return false
        if(spec.equipmentId==null)
            return athlete.allowBodyweight &&
                spec.expectedLoadBasis==null && choice.selectedLoad==null
        val stock=athlete.gym.confirmedStock[spec.equipmentId] ?:return false
        if(stock.basis!=spec.expectedLoadBasis ||
            stock.unit!=athlete.gym.unit ||
            (stock.basis==LoadBasis.BARBELL_TOTAL &&
                athlete.maxBarbellExercisesPerDay==0))
            return false
        val load=choice.selectedLoad ?:return false
        val inspected=EquipmentLoadDomain.inspect(stock,load,stock.unit)
        return inspected is LoadInspection.Confirmed && inspected.exact
    }

    private fun placements(
        calendar:List<Int>,
        focus:List<ComposerFocus>,
        purpose:List<ComposerSessionPurpose>,
        policy:AuthoredGoalProgramPolicy,
        catalog:Map<String,CatalogMovement>,
        choices:Map<String,CatalogExerciseChoice>
    ):List<AthleteCalendarIntent>? {
        val liftIds=policy.primaryLiftMinimumDays.keys.sorted()
        val used=mutableMapOf<String,MutableList<Int>>()
        val assigned=mutableListOf<AthleteCalendarIntent>()
        fun gapOkay(id:String,day:Int):Boolean {
            val spec=catalog.getValue(id)
            val choice=choices.getValue(id)
            val group=spec.recoveryGroupId ?:choice.recoveryGroup
            for((other,dates) in used) {
                val otherSpec=catalog.getValue(other)
                val otherChoice=choices.getValue(other)
                val otherGroup=otherSpec.recoveryGroupId ?:otherChoice.recoveryGroup
                val required=when {
                    other==id -> maxOf(choice.minimumCalendarDayGap,
                        if(group!=null)maxOf(choice.minimumGroupCalendarDayGap,
                            spec.minimumRecoveryGroupDayGap)else 1)
                    group!=null && group==otherGroup ->
                        maxOf(choice.minimumGroupCalendarDayGap,
                            spec.minimumRecoveryGroupDayGap,
                            otherChoice.minimumGroupCalendarDayGap,
                            otherSpec.minimumRecoveryGroupDayGap)
                    else ->1
                }
                for(otherDay in dates) {
                    val raw=abs(day-otherDay)
                    if(minOf(raw,7-raw)<required)return false
                }
            }
            return true
        }
        fun solve(index:Int):Boolean {
            if(index==calendar.size)return liftIds.all {
                used[it].orEmpty().size>=policy.primaryLiftMinimumDays.getValue(it)
            }
            val day=calendar[index]
            val skill=purpose[index]==ComposerSessionPurpose.STRENGTH_SKILL
            val available=liftIds.filter {id ->
                focus[index] in catalog.getValue(id).permittedFocus &&
                    used[id].orEmpty().size<choices.getValue(id).maximumVisits &&
                    gapOkay(id,day)
            }.sortedWith(compareByDescending<String> {
                policy.primaryLiftMinimumDays.getValue(it)-used[it].orEmpty().size
            }.thenBy {it})
            val options:List<String?> = if(skill)available else
                listOf(null)+available
            for(id in options) {
                assigned+=AthleteCalendarIntent(day,purpose[index],id)
                if(id!=null)used.getOrPut(id){mutableListOf()}.add(day)
                val stillPossible=liftIds.all {lift ->
                    val missing=(policy.primaryLiftMinimumDays.getValue(lift)-
                        used[lift].orEmpty().size).coerceAtLeast(0)
                    missing<=calendar.size-index-1
                }
                if(stillPossible && solve(index+1))return true
                if(id!=null)used.getValue(id).removeAt(
                    used.getValue(id).lastIndex)
                assigned.removeAt(assigned.lastIndex)
            }
            return false
        }
        return if(solve(0))assigned.toList()else null
    }

    fun generate(input:AthleteProgramGenerationRequest):
        AthleteProgramGenerationReview {
        val issues=mutableListOf<AthleteGenerationIssue>()
        fun fail(field:String,kind:AthleteGenerationIssueKind,message:String) {
            issues+=AthleteGenerationIssue(field,kind,message)
        }
        fun result(status:AthleteGenerationStatus,
            focuses:Map<Int,ComposerFocus> =emptyMap(),
            intents:List<AthleteCalendarIntent> =emptyList(),
            candidate:AthleteCatalogCandidateReview?=null
        )=AthleteProgramGenerationReview(status,
            Collections.unmodifiableList(issues.toList()),
            Collections.unmodifiableMap(LinkedHashMap(focuses)),
            Collections.unmodifiableList(intents.toList()),candidate)

        val athlete=input.setup.reviewed?.takeIf {
            input.setup.mayReviewProgramCandidate
        }
        if(athlete==null) {
            fail("setup",AthleteGenerationIssueKind.SETUP_UNRESOLVED,
                "Confirm the athlete, gym and calendar before generating a plan.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED)
        }
        val policy=input.policy
        if(policy==null) {
            fail("policy",AthleteGenerationIssueKind.POLICY_UNRESOLVED,
                "Provide an athlete-specific, versioned goal and phase coaching policy.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED)
        }
        val weekdays=athlete.trainingWeekdays.sorted()
        val counts=focusCounts(athlete.split,weekdays.size,input.extraFocuses)
        if(counts==null) {
            fail("extraFocuses",
                AthleteGenerationIssueKind.FOCUS_DISTRIBUTION_INVALID,
                "Select each extra Upper/Lower, PPL or ULPPL day explicitly.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED)
        }
        val purposeCounts=policy.minimumPurposeDays
        if(purposeCounts.isEmpty() ||
            purposeCounts.keys.any {it==ComposerSessionPurpose.UNSPECIFIED} ||
            purposeCounts.values.any {it<=0} ||
            purposeCounts.values.sum()!=weekdays.size) {
            fail("policy.minimumPurposeDays",
                AthleteGenerationIssueKind.POLICY_UNRESOLVED,
                "State the exact whole-week distribution of training purposes; the generator does not invent leftover session roles.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED)
        }
        if(policy.primaryLiftMinimumDays.size>weekdays.size ||
            policy.primaryLiftMinimumDays.values.any {
                it !in 1..weekdays.size
            } || policy.primaryLiftMinimumDays.values.sum()>weekdays.size) {
            fail("policy.primaryLiftMinimumDays",
                AthleteGenerationIssueKind.POLICY_UNRESOLVED,
                "The named lift frequencies must fit into the week with one primary lift per day.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED)
        }
        if((input.phase==TrainingPhase.STRENGTH ||
            input.phase==TrainingPhase.PEAK) &&
            (policy.primaryLiftMinimumDays.isEmpty() ||
                policy.minimumPurposeDays[
                    ComposerSessionPurpose.STRENGTH_SKILL]==null)) {
            fail("policy.primaryLiftMinimumDays",
                AthleteGenerationIssueKind.POLICY_UNRESOLVED,
                "Strength and peak phases must name primary lifts and skill-day counts.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED)
        }
        val catalog=input.catalog.associateBy {it.exerciseId}
        val choices=input.choices.associateBy {it.exerciseId}
        val ids=policy.primaryLiftMinimumDays.keys
        if(catalog.size!=input.catalog.size ||
            choices.size!=input.choices.size ||
            ids.any {!validPrimary(it,athlete,catalog,choices)}) {
            fail("primaryLifts",
                AthleteGenerationIssueKind.PRIMARY_EXERCISE_UNAVAILABLE,
                "A named primary is not a reviewed, selected, physically loadable movement in this gym.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED)
        }
        val phaseCount=if(input.phase==TrainingPhase.HYPERTROPHY)
            ComposerSessionPurpose.HYPERTROPHY else
            ComposerSessionPurpose.STRENGTH_SKILL
        if((purposeCounts[phaseCount] ?: 0)<1) {
            fail("policy.minimumPurposeDays",
                AthleteGenerationIssueKind.POLICY_UNRESOLVED,
                "The policy lacks its phase-specific training purpose.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED)
        }
        val focuses=arrangements(counts).sortedWith(compareBy<List<ComposerFocus>> {
            val categories=it.map {focus -> when(focus) {
                ComposerFocus.UPPER,ComposerFocus.PUSH,
                ComposerFocus.PULL -> "upper:"+focus.name
                else -> focus.name
            }}
            spacingPenalty(weekdays,categories)
        }.thenBy {it.joinToString(",") {focus ->focus.name}})
        val purposes=arrangements(purposeCounts).sortedWith(
            compareBy<List<ComposerSessionPurpose>> {
                spacingPenalty(weekdays,it.map {p ->p.name})
            }.thenBy {it.joinToString(",") {p ->p.name}})
        var evaluated=0
        for(focus in focuses)for(purpose in purposes) {
            if(++evaluated>MAX_ASSIGNMENTS)break
            val plans=placements(weekdays,focus,purpose,policy,catalog,choices)
                ?:continue
            // Validate generated intentions against the exact authored
            // athlete policy. The phase, dose and provenance are not guessed.
            val policyReview=CoachGoalProgramPolicyDomain.review(
                athlete,input.phase,policy.selectedBounds,plans,policy)
            if(policyReview.status!=GoalPolicyStatus.MATCHED_FOR_REVIEW)
                continue
            val proposal=CoachAthleteCatalogCandidateDomain.review(
                AthleteCatalogCandidateRequest(
                    input.setup,input.phase,
                    weekdays.indices.associate {weekdays[it] to focus[it]},
                    policy.selectedBounds,input.nonSetSecondsByWeekday,plans,
                    input.catalog,input.choices,input.maximumExercisesPerDay,
                    input.preferredPatternsPerDay,policy))
            // A mechanical draft may be unresolved due to exercise, hard
            // time or dose constraints. Preserve it for human diagnosis.
            val actual=proposal.realization
            val resultStatus=if(proposal.status==
                    AthleteCandidateStatus.CANDIDATE_FOR_REVIEW &&
                actual?.status==GoalRealizationStatus.DELIVERED_FOR_REVIEW)
                AthleteGenerationStatus.GENERATED_FOR_REVIEW
            else AthleteGenerationStatus.PLACEMENTS_UNRESOLVED
            if(resultStatus!=AthleteGenerationStatus.GENERATED_FOR_REVIEW)
                fail("composedWeek",
                    AthleteGenerationIssueKind.COMPOSED_WEEK_UNRESOLVED,
                    "The scheduled candidate needs real coach review of volume, recovery, time, equipment or constraints.")
            return result(resultStatus,
                weekdays.indices.associate {weekdays[it] to focus[it]},
                plans,proposal)
        }
        fail("weekdays",AthleteGenerationIssueKind.SCHEDULE_CONFLICT,
            "No bounded split/day/primary-lift placement meets the confirmed calendar, movement eligibility and circular recovery rules.")
        return result(AthleteGenerationStatus.PLACEMENTS_UNRESOLVED)
    }
}
