package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * G2 candidate search: assemble different split/day-count calendar blueprints
 * from athlete-offered FUTURE weekdays and independently authored policies.
 * No unreviewed volume rules, weights, exercises or profile permissions are
 * invented. Every candidate passes the existing blueprint -> complete week
 * -> full cycle review path and is never itself an accepted program.
 */
data class AdaptiveCalendarPolicyOffer(
    val policy:AuthoredGoalProgramPolicy,
    val choices:List<CatalogExerciseChoice>,
    val prescriptions:List<ExercisePrescriptionPolicy>,
    val extraFocuses:List<ComposerFocus> =emptyList()
)
data class AdaptiveCalendarSearchRequest(
    val blueprintTemplate:AdaptiveScheduleBlueprintRequest,
    val availableFutureWeekdays:Set<Int>,
    val nonSetSecondsByWeekday:Map<Int,Int>,
    val policyOffers:List<AdaptiveCalendarPolicyOffer>
)
data class AdaptiveCalendarSearchReview(
    val result:AdaptiveBlockGenerationReview,
    val calendarsConsidered:Int,
    val blueprintsGenerated:Int,
    val searchTruncated:Boolean
) {
    val maySaveProgram:Boolean get()=false
    val mayIssueWorkout:Boolean get()=false
}
object AdaptiveScheduleCalendarSearchDomain {
    private const val MAX_BLUEPRINTS=7
    private const val MAX_POLICIES=12
    private const val MAX_CALENDARS_PER_POLICY=3

    fun search(request:AdaptiveCalendarSearchRequest):AdaptiveCalendarSearchReview {
        require(request.availableFutureWeekdays.size in 3..7 &&
            request.availableFutureWeekdays.all {it in 0..6})
        require(request.nonSetSecondsByWeekday.keys==
            request.availableFutureWeekdays &&
            request.nonSetSecondsByWeekday.values.all {it in 0..21600})
        require(request.policyOffers.size<=MAX_POLICIES)
        require(request.blueprintTemplate.blueprints.isEmpty())
        val template=request.blueprintTemplate
        val original=template.base.athleteSetup.reviewed
        val target=template.base.approvedCycle.blocks.singleOrNull {
            it.blockId==template.base.targetBlockId
        }
        val candidates=mutableListOf<AdaptiveScheduleBlueprint>()
        var considered=0
        var truncated=false
        if(original!=null && target!=null &&
            template.base.athleteSetup.mayReviewProgramCandidate &&
            original.athleteProfileId==template.base.approvedCycle.athleteId &&
            original.goal==template.base.approvedCycle.goal) {
            val calendars=request.policyOffers.map {offer ->
                val p=offer.policy
                if(p.phase!=target.phase || p.trainingDays !in 3..6 ||
                    p.goal!=original.goal ||
                    p.experience!=original.experience ||
                    p.athleteId!=original.athleteProfileId ||
                    p.athleteRevision!=original.profileRevision ||
                    !validFocusDistribution(p.split,p.trainingDays,
                        offer.extraFocuses))emptyList<Set<Int>>()
                else combinations(request.availableFutureWeekdays,
                    p.trainingDays).filter {days ->
                        days.containsAll(original.lockedTrainingDays) &&
                        days.containsAll(original.hardTrainingDays) &&
                        days.containsAll(original.longTrainingDays)
                    }.sortedWith(compareBy<Set<Int>>(
                        {adjacentDays(it)}, {it.sorted().joinToString("")}
                    )).take(MAX_CALENDARS_PER_POLICY)
            }
            // Interleave policies so the first split cannot monopolize the
            // bounded search, even if it has many feasible calendars.
            for(index in 0 until MAX_CALENDARS_PER_POLICY) {
                for(offerIndex in request.policyOffers.indices) {
                    val days=calendars[offerIndex].getOrNull(index) ?:continue
                    if(candidates.size>=MAX_BLUEPRINTS) {
                        truncated=true
                        break
                    }
                    considered++
                    val offer=request.policyOffers[offerIndex]
                    val setup=reviewedSchedule(original,offer.policy.split,days)
                    if(!setup.mayReviewProgramCandidate)continue
                    val overhead=request.nonSetSecondsByWeekday.filterKeys {
                        it in days
                    }
                    val prospective=setup.reviewed!=original ||
                        template.base.coaching.policiesByPhase[
                            offer.policy.phase]!=offer.policy
                    candidates+=AdaptiveScheduleBlueprint(
                        "auto_"+offerIndex+"_"+index,offer.choices,
                        offer.prescriptions,
                        if(prospective)ProspectiveBlockCoachingContext(
                            setup,offer.policy,overhead) else null,
                        offer.extraFocuses)
                }
                if(truncated)break
            }
        }
        val reviewed=AdaptiveBlockOptionGenerationDomain.reviewBlueprints(
            template.copy(blueprints=Collections.unmodifiableList(candidates)))
        return AdaptiveCalendarSearchReview(reviewed,considered,
            candidates.size,truncated)
    }

    private fun validFocusDistribution(split:ComposerSplit,count:Int,
        extra:List<ComposerFocus>):Boolean=when(split) {
        ComposerSplit.FULL_BODY ->count in 3..6 && extra.isEmpty()
        ComposerSplit.UPPER_LOWER ->count in 3..6 &&
            extra.size==count%2 &&
            extra.all {it==ComposerFocus.UPPER || it==ComposerFocus.LOWER}
        ComposerSplit.PUSH_PULL_LEGS ->count in 3..6 &&
            extra.size==count%3 && extra.all {it in setOf(
                ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)}
        ComposerSplit.ULPPL ->count in 5..6 && extra.size==count-5 &&
            extra.all {it in setOf(ComposerFocus.UPPER,ComposerFocus.LOWER,
                ComposerFocus.PUSH,ComposerFocus.PULL,ComposerFocus.LEGS)}
    }

    private fun combinations(available:Set<Int>,count:Int):List<Set<Int>> {
        val days=available.sorted()
        val found=mutableListOf<Set<Int>>()
        fun visit(start:Int,selected:MutableList<Int>) {
            if(selected.size==count) {
                found+=selected.toSet()
                return
            }
            for(i in start until days.size) {
                if(days.size-i<count-selected.size)break
                selected+=days[i]
                visit(i+1,selected)
                selected.removeAt(selected.lastIndex)
            }
        }
        if(count<=days.size)visit(0,mutableListOf())
        return found
    }

    private fun adjacentDays(selected:Set<Int>):Int {
        val days=selected.sorted()
        return days.indices.count {i->
            val next=if(i==days.lastIndex)days.first()+7 else days[i+1]
            next-days[i]==1
        }
    }

    private fun reviewedSchedule(
        source:ReviewedAthleteSetup,split:ComposerSplit,days:Set<Int>
    ):AthleteSetupReview=CoachAthleteSetupDomain.review(AthleteSetupDraft(
        profileRevision=source.profileRevision,
        athleteProfileId=source.athleteProfileId,
        goal=source.goal,experience=source.experience,
        split=split,trainingWeekdays=days,timezoneId=source.timezoneId,
        sessionTimeBucket=source.sessionTimeBucket,
        hardSessionLimit=source.hardSessionLimit,
        gym=GymProfileDraft(source.gym.gymId,source.gym.unit,
            source.gym.confirmedStock,source.gym.availableFacilities,
            source.gym.availableAttachments),
        allowBodyweight=source.allowBodyweight,
        allowSupersets=source.allowSupersets,
        allowTricepsOnLower=source.allowTricepsOnLower,
        maxBarbellExercisesPerDay=source.maxBarbellExercisesPerDay,
        focusedMuscles=source.focusedMuscles,
        reducedMuscles=source.reducedMuscles,
        excludedExerciseIds=source.excludedExerciseIds,
        lockedTrainingDays=source.lockedTrainingDays,
        hardTrainingDays=source.hardTrainingDays,
        longTrainingDays=source.longTrainingDays,
        declaredRestDays=(0..6).toSet()-days))
}
