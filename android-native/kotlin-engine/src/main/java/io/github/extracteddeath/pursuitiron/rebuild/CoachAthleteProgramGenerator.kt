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
    val extraFocuses:List<ComposerFocus> = emptyList(),
    /**
     * Default preserves authored/custom exercise selections byte-for-byte.
     * Only explicit opt-in permits pruning unpinned offered catalog options.
     */
    val selectFromOfferedCatalog:Boolean=false,
    val maximumSelectedOptions:Int=32
)
enum class AthleteGenerationStatus {
    INPUTS_UNRESOLVED, PLACEMENTS_UNRESOLVED, GENERATED_FOR_REVIEW
}
enum class AthleteGenerationIssueKind {
    SETUP_UNRESOLVED, POLICY_UNRESOLVED, FOCUS_DISTRIBUTION_INVALID,
    PRIMARY_EXERCISE_UNAVAILABLE, SCHEDULE_CONFLICT, COMPOSED_WEEK_UNRESOLVED,
    EXERCISE_SELECTION_UNRESOLVED
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
    val candidate:AthleteCatalogCandidateReview?,
    val exerciseSelection:AthleteExerciseSelectionReview?=null,
    /** Bounded selection evidence; a feasible candidate is not a global
     * optimum or proof that athlete-specific cycle progression is finished. */
    val search:AthleteGenerationSearchEvidence=AthleteGenerationSearchEvidence()
) {
    val mayIssueWorkout:Boolean get()=false
    val maySaveProgram:Boolean get()=false
}
data class AthleteGenerationSearchEvidence(
    val placementsChecked:Int=0,
    val draftsAudited:Int=0,
    val feasibleWeeksCompared:Int=0,
    val searchBudgetReached:Boolean=false
)
object CoachAthleteProgramGenerator {
    private const val MAX_ASSIGNMENTS=16000
    /** Do not turn a bounded candidate review into an unbounded search. */
    private const val MAX_COMPOSED_CANDIDATES=32
    /** Search a handful of feasible weeks instead of returning the first. */
    private const val MAX_FEASIBLE_CANDIDATES=5

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

    private fun betterLong(a:List<Long>,b:List<Long>):Boolean {
        for(i in a.indices)if(a[i]!=b[i])return a[i]<b[i]
        return false
    }

    /**
     * Prefer actual repeated movement exposures spaced over the CIRCULAR
     * seven-day week. All declarations remain hard-checked by the composer:
     * this is a tie-breaking quality preference, never a silent new gap.
     *
     * A gap of one calendar day gets a larger penalty than a gap of two.
     * Only distinct training days count: one exercise's sets are not
     * independent appearances, and unrelated exercises remain independent.
     */
    internal fun repeatLiftSpacingPenalty(
        visits:List<ComposerVisit>,weekdays:List<Int>
    ):Long {
        var penalty=0L
        for(days in visits.groupBy {it.exerciseId}.values) {
            val appearances=days.map {visit ->
                weekdays[visit.dayIndex]
            }.distinct().sorted()
            if(appearances.size<2)continue
            for(index in appearances.indices) {
                val today=appearances[index]
                val next=if(index==appearances.lastIndex)
                    appearances.first()+7 else appearances[index+1]
                val gap=next-today
                penalty+=when(gap) {
                    1 -> 100L
                    2 -> 20L
                    else -> 0L
                }
            }
        }
        return penalty
    }

    /**
     * Rank ONLY audited feasible weeks; nothing here can waive MEV/MRV,
     * frequency, equipment, preferred session window or authored primary
     * requirements. We do not synthesize new exercises, reps or sets.
     */
    internal fun qualityScore(
        draft:ComposerDraft,
        focus:List<ComposerFocus>,
        purpose:List<ComposerSessionPurpose>,
        weekdays:List<Int>
    ):List<Long> {
        val recovery=spacingPenalty(weekdays,focus.map {f->
            if(f in setOf(ComposerFocus.UPPER,ComposerFocus.PUSH,
                    ComposerFocus.PULL))"upper:"+f.name else f.name
        }).toLong()
        val purposes=spacingPenalty(weekdays,purpose.map {it.name}).toLong()
        val repeatedLiftSpacing=repeatLiftSpacingPenalty(draft.visits,weekdays)
        val mavGap=draft.audit.muscles.values.sumOf {
            if(it.gapToMav.signum()>0)
                it.gapToMav.multiply(java.math.BigDecimal("1000")).toLong()
            else 0L
        }
        val request=draft.week
        val minutes=draft.audit.sessions.map {it.roundedMinutes}
        val variation=(minutes.maxOrNull() ?:0L)-(minutes.minOrNull() ?:0L)
        // A feasible 40–60 minute workout closer to 50 is preferred, but
        // a gap to authored MAV takes precedence over decorative duration.
        val sessionFit=draft.audit.sessions.sumOf {session->
            val bounds=request.windows[session.dayIndex]
            val preferred=if(bounds.preferredMaximumMinutes==null)
                bounds.preferredMinimumMinutes.toLong()
            else (bounds.preferredMinimumMinutes.toLong()+
                bounds.preferredMaximumMinutes.toLong())/2L
            abs(session.roundedMinutes-preferred)
        }
        return listOf(recovery,purposes,repeatedLiftSpacing,
            mavGap,sessionFit,variation)
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
        var evaluated=0
        var drafted=0
        var feasibleCompared=0
        var budgetReached=false
        fun result(status:AthleteGenerationStatus,
            focuses:Map<Int,ComposerFocus> =emptyMap(),
            intents:List<AthleteCalendarIntent> =emptyList(),
            candidate:AthleteCatalogCandidateReview?=null,
            selection:AthleteExerciseSelectionReview?=null
        )=AthleteProgramGenerationReview(status,
            Collections.unmodifiableList(issues.toList()),
            Collections.unmodifiableMap(LinkedHashMap(focuses)),
            Collections.unmodifiableList(intents.toList()),candidate,selection,
            AthleteGenerationSearchEvidence(evaluated,drafted,feasibleCompared,
                budgetReached))

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
        // Non-destructive choice mode is the default: custom authoring
        // remains authoritative until the athlete explicitly opts into
        // screening a larger, source-backed offer pool.
        val selection=if(input.selectFromOfferedCatalog)
            CoachAthleteExerciseSelectionDomain.review(
                AthleteExerciseSelectionRequest(input.setup,policy,
                    input.catalog,input.choices,counts.keys,
                    input.preferredPatternsPerDay,
                    input.maximumSelectedOptions))
        else null
        if(selection!=null &&
            selection.status!=AthleteSelectionStatus.SHORTLIST_FOR_REVIEW) {
            fail("exerciseSelection",
                AthleteGenerationIssueKind.EXERCISE_SELECTION_UNRESOLVED,
                "The offered catalog cannot reliably cover the author's direct-muscle and split-day requirements.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED,
                selection=selection)
        }
        val chosen=selection?.choices ?:input.choices
        val catalog=input.catalog.associateBy {it.exerciseId}
        val choices=chosen.associateBy {it.exerciseId}
        val ids=policy.primaryLiftMinimumDays.keys
        if(catalog.size!=input.catalog.size ||
            choices.size!=chosen.size ||
            ids.any {!validPrimary(it,athlete,catalog,choices)}) {
            fail("primaryLifts",
                AthleteGenerationIssueKind.PRIMARY_EXERCISE_UNAVAILABLE,
                "A named primary is not a reviewed, selected, physically loadable movement in this gym.")
            return result(AthleteGenerationStatus.INPUTS_UNRESOLVED,
                selection=selection)
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
        data class ViablePlacement(
            val focuses:Map<Int,ComposerFocus>,
            val intents:List<AthleteCalendarIntent>,
            val review:AthleteCatalogCandidateReview,
            val score:List<Int>
        )
        fun better(a:List<Int>,b:List<Int>):Boolean {
            for(i in a.indices)if(a[i]!=b[i])return a[i]<b[i]
            return false
        }
        var best:ViablePlacement?=null
        var strongest:ViablePlacement?=null
        var strongestScore:List<Long>?=null
        search@ for(focus in focuses)for(purpose in purposes) {
            if(evaluated>=MAX_ASSIGNMENTS ||
                drafted>=MAX_COMPOSED_CANDIDATES ||
                feasibleCompared>=MAX_FEASIBLE_CANDIDATES) {
                budgetReached=true
                break@search
            }
            evaluated++
            val plans=placements(weekdays,focus,purpose,policy,catalog,choices)
                ?:continue
            // Reject unverified phase-specific policy before composing sets.
            val policyReview=CoachGoalProgramPolicyDomain.review(
                athlete,input.phase,policy.selectedBounds,plans,policy)
            if(policyReview.status!=GoalPolicyStatus.MATCHED_FOR_REVIEW)
                continue
            drafted++
            val byDay=weekdays.indices.associate {weekdays[it] to focus[it]}
            val proposal=CoachAthleteCatalogCandidateDomain.review(
                AthleteCatalogCandidateRequest(
                    input.setup,input.phase,byDay,
                    policy.selectedBounds,input.nonSetSecondsByWeekday,plans,
                    input.catalog,chosen,input.maximumExercisesPerDay,
                    input.preferredPatternsPerDay,policy))
            val actual=proposal.realization
            if(proposal.status==AthleteCandidateStatus.CANDIDATE_FOR_REVIEW &&
                actual?.status==GoalRealizationStatus.DELIVERED_FOR_REVIEW) {
                feasibleCompared++
                // Lexicographic choice: preserve whole-week recovery spacing
                // before preferring closer-to-MAV doses and balanced time.
                // No extra volume is added for the sake of a longer workout.
                val week=checkNotNull(proposal.catalogReview?.draft)
                val score=qualityScore(week,focus,purpose,weekdays)
                if(strongestScore==null ||
                    betterLong(score,checkNotNull(strongestScore))) {
                    strongest=ViablePlacement(byDay,plans,proposal,emptyList())
                    strongestScore=score
                }
                continue
            }

            // Rank actual unresolved drafts by unmet authored promises
            // before soft time preferences. A locally incomplete first
            // arrangement must not hide another materially better option.
            val draft=proposal.catalogReview?.draft
            val score=listOf(
                if(draft==null)1 else 0,
                draft?.missingRequiredVisits?.values?.sum() ?: 1000,
                draft?.daysWithUnmetPrimary?.size ?: 1000,
                draft?.audit?.muscles?.values?.count {
                    it.gapToMev.signum()>0 || it.missingDirectDays>0 ||
                        it.excessAboveMrv.signum()>0
                } ?: 1000,
                draft?.audit?.daysWithoutWorkingSets?.size ?: 1000,
                draft?.audit?.daysOutsidePreferredWindow?.size ?: 1000,
                proposal.issues.size
            )
            if(best==null || better(score,checkNotNull(best).score))
                best=ViablePlacement(byDay,plans,proposal,score)
        }
        val selected=strongest
        if(selected!=null)
            return result(AthleteGenerationStatus.GENERATED_FOR_REVIEW,
                selected.focuses,selected.intents,selected.review,selection)
        val closest=best
        if(closest!=null) {
            fail("composedWeek",AthleteGenerationIssueKind.COMPOSED_WEEK_UNRESOLVED,
                "The closest checked week still needs coach review of muscle dose, recovery, exercise distribution, time or gym constraints.")
            return result(AthleteGenerationStatus.PLACEMENTS_UNRESOLVED,
                closest.focuses,closest.intents,closest.review,selection)
        }
        fail("weekdays",AthleteGenerationIssueKind.SCHEDULE_CONFLICT,
            "No bounded split/day/priority-lift placement meets the confirmed calendar, eligibility and circular recovery rules.")
        return result(AthleteGenerationStatus.PLACEMENTS_UNRESOLVED,
            selection=selection)
    }
}
