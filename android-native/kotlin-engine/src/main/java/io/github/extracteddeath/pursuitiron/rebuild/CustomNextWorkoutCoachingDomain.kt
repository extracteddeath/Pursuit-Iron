package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Factual, day-specific custom-cycle coaching. A CUSTOM slot and its workout
 * weekday (not the shared exercise ID or broad session purpose) own their own
 * progression: Upper bench cannot advance Push bench.
 *
 * This pure domain does not authenticate history. Only the native owner may
 * construct the request from the complete, verified mixed-ledger read and
 * freshly consent-reviewed NEXT workout. No issued targets are mutated.
 */
data class CustomCoachingHistoricalSession(
    val completionOrder:Long,
    val snapshot:CustomWorkoutDaySnapshot,
    val evidence:CustomWorkoutHistoricalEvidence
)

/** Versioned opt-in: existing native custom approvals replay the exact V1 rule. */
enum class CustomRepForecastPolicy {
    LEGACY_STEP_V1,
    TARGET_RIR_V2
}

data class CustomNextWorkoutCoachingRequest(
    val next:CustomWorkoutDaySnapshot,
    val slotId:String,
    val completed:List<CustomCoachingHistoricalSession>,
    val verifiedStock:VerifiedLoadStock?,
    val maximumAuthorizedIncrease:BigDecimal,
    val readiness:CoachReadiness,
    val movementRestricted:Boolean,
    val repForecastPolicy:CustomRepForecastPolicy=CustomRepForecastPolicy.LEGACY_STEP_V1
)

enum class CustomNextWorkoutCoachingStatus {
    INVALID_HISTORY,
    NO_MATCHING_SESSION,
    TARGET_ALREADY_TRAINED,
    CHANGED_DAY_OR_EXERCISE,
    CHANGED_COACHING_SOURCE,
    CHANGED_PRESCRIPTION,
    RESTRICTED_OR_LOW_READINESS,
    MISSED_OR_FAILED_SETS,
    NEEDS_RECORDED_EFFORT,
    HOLD_NEAR_FAILURE,
    BUILD_REPS,
    READY_FOR_LOAD_REVIEW,
    HOLD_CURRENT_TARGETS
}

data class CustomNextSetCoaching(
    val setKey:String,
    val observedReps:Int,
    val proposedNextReps:Int,
    val actualRir:Int?,
    val changed:Boolean,
    val progressionRir:Int?=actualRir,
    val effortSource:ProgressionEffortSource=if(actualRir==null)
        ProgressionEffortSource.UNAVAILABLE else ProgressionEffortSource.REPORTED
)

data class CustomNextWorkoutCoachingReview(
    val status:CustomNextWorkoutCoachingStatus,
    val comparedWorkoutIds:List<String>,
    val latestWorkoutId:String?,
    val nextSetSuggestions:List<CustomNextSetCoaching>,
    val observedStraightSetLoad:BigDecimal?,
    val loadReviewCandidate:BigDecimal?,
    val explanation:String,
    val assumedGoalRirSetCount:Int=0,
    val repForecastPolicy:CustomRepForecastPolicy=CustomRepForecastPolicy.LEGACY_STEP_V1,
    val forecastLimitations:Set<RepForecastLimitation> = emptySet()
) {
    val mayIssueWorkout:Boolean get()=false
    val mayEditProgram:Boolean get()=false
    val mayChangeHistory:Boolean get()=false
}

/** Never bypass a failed set, an intervening day, a changed source, or RIR. */
object CustomNextWorkoutCoachingDomain {
    private val zero=BigDecimal.ZERO
    private val maximum=BigDecimal("100000")
    private fun same(a:BigDecimal,b:BigDecimal)=a.compareTo(b)==0
    private fun normalized(v:BigDecimal)=BigDecimal(v.stripTrailingZeros().toPlainString())

    private fun matchingReceipts(
        records:List<CustomHistoricalVisitFact>,
        authored:List<CustomWorkoutSetSnapshot>,
        type:DraftSetType=DraftSetType.WORKING
    ):Boolean {
        if(records.size!=authored.size ||
            records.map{it.setKey}.toSet()!=authored.map{it.setKey}.toSet())
            return false
        return authored.all {set->
            val fact=records.singleOrNull {it.setKey==set.setKey}
                ?:return@all false
            val prescribed=set.prescription
            fact.slotId==set.slotId &&
                fact.part==CustomWorkoutVisitPart.MAIN &&
                fact.setType==type &&
                fact.prescribedLoad==prescribed.load &&
                fact.prescribedReps==prescribed.reps &&
                fact.prescribedRir==prescribed.targetRir &&
                fact.amplifierKind==prescribed.amplifier?.kind &&
                fact.effectiveEventId.isNotBlank() &&
                CustomWorkoutJournalDomain.actualValid(
                    prescribed.load,fact.outcome,fact.actual)
        }
    }

    private fun sameWarmups(
        previous:CustomWorkoutSlotSnapshot,
        next:CustomWorkoutSlotSnapshot
    ):Boolean {
        val old=previous.sets.filter{it.prescription.type==DraftSetType.WARMUP}
        val current=next.sets.filter{it.prescription.type==DraftSetType.WARMUP}
        return old.size==current.size &&
            old.zip(current).all { (before,after)->
                before.slotId==after.slotId &&
                    before.ordinalInSlot==after.ordinalInSlot &&
                    before.prescription==after.prescription
            }
    }

    private fun authenticWarmups(
        history:CustomCoachingHistoricalSession,
        slot:CustomWorkoutSlotSnapshot
    ):Boolean {
        val authored=slot.sets.filter{
            it.prescription.type==DraftSetType.WARMUP
        }
        val facts=history.evidence.visits.filter{
            it.slotId==slot.slotId &&
                it.part==CustomWorkoutVisitPart.MAIN &&
                it.setType==DraftSetType.WARMUP
        }
        return matchingReceipts(facts,authored,DraftSetType.WARMUP) &&
            facts.all{it.exerciseId==slot.exerciseId}
    }

    fun review(request:CustomNextWorkoutCoachingRequest):CustomNextWorkoutCoachingReview {
        var assumedForReview=0
        fun result(
            status:CustomNextWorkoutCoachingStatus,
            ids:List<String> = emptyList(),latest:String?=null,
            reps:List<CustomNextSetCoaching> = emptyList(),
            load:BigDecimal?=null,candidate:BigDecimal?=null,
            explanation:String=status.name
        )=CustomNextWorkoutCoachingReview(status,
            Collections.unmodifiableList(ids.toList()),latest,
            Collections.unmodifiableList(reps.toList()),load,candidate,
            explanation+ProgressionEffortDomain.assumptionNote(assumedForReview),
            assumedForReview,request.repForecastPolicy,
            if(request.repForecastPolicy==CustomRepForecastPolicy.TARGET_RIR_V2)
                buildSet {
                    add(RepForecastLimitation.SINGLE_EXPOSURE)
                    add(RepForecastLimitation.RIR_ESTIMATE_UNCALIBRATED)
                    add(RepForecastLimitation.ACTUAL_REST_AND_PRIOR_EXERCISE_WORK_UNAVAILABLE)
                    add(RepForecastLimitation.CHRONIC_FATIGUE_NOT_ESTIMATED)
                    if(reps.dropLast(1).any {it.changed})
                        add(RepForecastLimitation.EARLIER_SET_WORK_CHANGED)
                }
            else emptySet())
        if(request.maximumAuthorizedIncrease<=zero ||
            request.maximumAuthorizedIncrease>maximum ||
            request.maximumAuthorizedIncrease.stripTrailingZeros().scale()>3 ||
            request.completed.zipWithNext().any{
                it.first.completionOrder>=it.second.completionOrder
            } ||
            request.completed.map{it.evidence.workoutId}.distinct().size!=
                request.completed.size ||
            request.completed.any{
                it.completionOrder<1 ||
                    it.snapshot.stamp.athleteId!=request.next.stamp.athleteId ||
                    it.evidence.athleteId!=it.snapshot.stamp.athleteId ||
                    it.evidence.cycleId!=it.snapshot.stamp.cycleId ||
                    it.evidence.cycleRevision!=it.snapshot.stamp.cycleRevision ||
                    it.evidence.workoutId.isBlank() ||
                    it.evidence.journalRevision<1
            })
            return result(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
                explanation="The complete native history or authorized progression limit could not be verified.")
        val target=request.next.slots.singleOrNull{it.slotId==request.slotId}
            ?:return result(CustomNextWorkoutCoachingStatus.CHANGED_DAY_OR_EXERCISE,
                explanation="The current approved day does not contain this movement slot.")
        val nextSets=target.sets.filter{it.prescription.type==DraftSetType.WORKING}
        if(nextSets.isEmpty() || nextSets.size>32)
            return result(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,
                explanation="There is no comparable working-set prescription.")

        // The most recent two appearances of THIS DAY are selected from the
        // entire ledger, including missed or changed exercises. Never skip an
        // unfavorable occurrence or borrow strength from another weekday.
        val appearances=request.completed.filter {
            it.snapshot.stamp.cycleId==request.next.stamp.cycleId &&
                it.snapshot.blockId==request.next.blockId &&
                it.snapshot.weekday==request.next.weekday
        }
        // An older workout is not a "next" workout if this training day
        // has already been finished in that absolute week or a later one.
        // Do not cherry-pick earlier successful sessions and prescribe
        // progression into a historically completed calendar slot.
        // Different weekdays remain independent (Upper versus Push).
        if(appearances.any{
                it.snapshot.absoluteWeek>=request.next.absoluteWeek
            })
            return result(CustomNextWorkoutCoachingStatus.TARGET_ALREADY_TRAINED,
                appearances.takeLast(2).map{it.evidence.workoutId},
                appearances.last().evidence.workoutId,
                explanation="This training day has a completed workout in the requested week or a later week. Review the next untrained day.")
        if(appearances.isEmpty())
            return result(CustomNextWorkoutCoachingStatus.NO_MATCHING_SESSION,
                explanation="Build evidence on this training day before progressing its targets.")
        val latest=appearances.last()
        val ids=appearances.takeLast(2).map{it.evidence.workoutId}
        val oldSlot=latest.snapshot.slots.singleOrNull{it.slotId==target.slotId}
        if(oldSlot==null || oldSlot.exerciseId!=target.exerciseId ||
            latest.snapshot.sessionPurpose!=request.next.sessionPurpose ||
            latest.snapshot.phase!=request.next.phase)
            return result(CustomNextWorkoutCoachingStatus.CHANGED_DAY_OR_EXERCISE,
                ids,latest.evidence.workoutId,
                explanation="The latest day uses a different exercise, slot or training purpose.")

        fun sameSource(s:CustomWorkoutDaySnapshot)=
            s.stamp==request.next.stamp &&
                s.blockId==request.next.blockId &&
                s.phase==request.next.phase &&
                s.sessionPurpose==request.next.sessionPurpose &&
                s.split==request.next.split
        if(!sameSource(latest.snapshot))
            return result(CustomNextWorkoutCoachingStatus.CHANGED_COACHING_SOURCE,
                ids,latest.evidence.workoutId,
                explanation="The plan or gym changed; establish new comparable performance.")

        val oldSets=oldSlot.sets.filter{it.prescription.type==DraftSetType.WORKING}
        if(oldSets.size!=nextSets.size ||
            oldSets.zip(nextSets).any { (old,next) ->
                old.prescription.copy(reps=next.prescription.reps)!=
                    next.prescription ||
                    old.prescription.reps.maximum!=next.prescription.reps.maximum ||
                    old.prescription.reps.minimum<
                        next.prescription.reps.minimum ||
                    old.slotId!=next.slotId ||
                    old.ordinalInSlot!=next.ordinalInSlot ||
                    old.prescription.amplifier!=null
            })
            return result(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,
                ids,latest.evidence.workoutId,
                explanation="Sets, rep ranges, loads or amplifier changed. Do not reuse old progression.")

        // V2 treats warmups as authenticated context, not as working-set
        // performance. An absent/edited warmup or a changed warmup policy
        // cannot silently become evidence of more rep capacity. V1 receipts
        // retain their original comparison policy for exact replay.
        if(request.repForecastPolicy==CustomRepForecastPolicy.TARGET_RIR_V2) {
            if(!sameWarmups(oldSlot,target))
                return result(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,
                    ids,latest.evidence.workoutId,
                    explanation="The warmup prescription changed since the last session.")
            if(!authenticWarmups(latest,oldSlot))
                return result(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
                    ids,latest.evidence.workoutId,
                    explanation="The latest warmups do not match their issued saved workout.")
        }

        val facts=latest.evidence.visits.filter {
            it.slotId==target.slotId &&
                it.part==CustomWorkoutVisitPart.MAIN &&
                it.setType==DraftSetType.WORKING
        }
        if(!matchingReceipts(facts,oldSets) ||
            facts.any{it.exerciseId!=target.exerciseId})
            return result(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
                ids,latest.evidence.workoutId,
                explanation="Logged working sets do not match the finished immutable workout.")

        // A target-RIR estimate has no meaning without its original goal.
        // V1 receipt replay is unchanged; the new policy never invents
        // an intended effort when only an actual RIR happens to be logged.
        if(request.repForecastPolicy==CustomRepForecastPolicy.TARGET_RIR_V2 &&
            nextSets.any {it.prescription.targetRir==null})
            return result(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,
                ids,latest.evidence.workoutId,
                explanation="The custom prescription has no approved goal RIR for at least one working set.")
        if(request.movementRestricted || request.readiness==CoachReadiness.LOW)
            return result(CustomNextWorkoutCoachingStatus.RESTRICTED_OR_LOW_READINESS,
                ids,latest.evidence.workoutId,
                explanation="Prioritize recovery or the movement restriction; do not advance targets.")
        if(facts.any{it.outcome!=KotlinJournalSetOutcome.COMPLETED ||
                it.actual==null || it.actual.reps<
                    oldSets.single{set->set.setKey==it.setKey}
                        .prescription.reps.minimum})
            return result(CustomNextWorkoutCoachingStatus.MISSED_OR_FAILED_SETS,
                ids,latest.evidence.workoutId,
                explanation="A skipped, failed or below-range set needs review before progression.")
        val effort=facts.associate {it.setKey to
            ProgressionEffortDomain.forCompletedSet(it.actual!!.actualRir,it.prescribedRir)}
        assumedForReview=effort.values.count {it.assumed}
        if(effort.values.any{it.rir==null})
            return result(CustomNextWorkoutCoachingStatus.NEEDS_RECORDED_EFFORT,
                ids,latest.evidence.workoutId,
                explanation="Some working sets have neither actual RIR nor an original goal RIR.")
        if(effort.values.any{checkNotNull(it.rir)<=1})
            return result(CustomNextWorkoutCoachingStatus.HOLD_NEAR_FAILURE,
                ids,latest.evidence.workoutId,
                explanation="Reported or assumed-goal effort leaves too little reserve to add reps or load.")

        // V1 replay retains its exact one-rep-per-set behavior. New opt-in
        // V2 estimates each ordinal at its OWN intended RIR; no invented
        // universal one-rep ceiling and no heaviest-set load transfer.
        // Neither approach predicts capacity after extra earlier-set work.
        // Set keys include absolute week; join the last completed set to
        // next week's set by stable slot ordinal, never by different-week ID.
        val suggestions=nextSets.zip(oldSets).map {(set,previousSet)->
            val observation=facts.single{it.setKey==previousSet.setKey}
            val actual=checkNotNull(observation.actual)
            val upper=set.prescription.reps.maximum
            val lower=set.prescription.reps.minimum
            val issued=set.prescription.load
            val sameIssued=when(issued) {
                is DraftSetLoad.Verified ->
                    actual.load!=null && actual.unit==issued.unit &&
                        same(actual.load,issued.value)
                DraftSetLoad.ExplicitBodyweight ->
                    actual.load==null && actual.unit==null
            }
            val observed=actual.reps.coerceIn(lower,upper)
            val effortForSet=effort.getValue(previousSet.setKey)
            val next=if(!sameIssued || actual.reps>upper) observed
                else when(request.repForecastPolicy) {
                    CustomRepForecastPolicy.LEGACY_STEP_V1 ->
                        if(checkNotNull(effortForSet.rir)>=2)
                            (observed+1).coerceAtMost(upper) else observed
                    CustomRepForecastPolicy.TARGET_RIR_V2 -> {
                        val goal=checkNotNull(set.prescription.targetRir)
                        val capacityAtGoal=actual.reps+
                            checkNotNull(effortForSet.rir)-goal
                        // Do not turn a low-effort set into an invented
                        // below-range target; effort/working-load review is
                        // distinct from this rep-only amendment.
                        capacityAtGoal.coerceIn(lower,upper)
                    }
                }
            CustomNextSetCoaching(set.setKey,actual.reps,next,
                actual.actualRir,next>observed,effortForSet.rir,effortForSet.source)
        }
        val increased=suggestions.any{it.changed}

        // An additional LOAD is only considered after TWO comparable,
        // uniformly loaded, completed ceiling sessions. Verified stock and
        // bounded increments are mandatory; never invent 38.5 lb or copy
        // the heaviest set into all working sets.
        var candidate:BigDecimal?=null
        var observedLoad:BigDecimal?=null
        if(appearances.size>=2) {
            val previous=appearances[appearances.size-2]
            val previousSlot=previous.snapshot.slots.singleOrNull{
                it.slotId==target.slotId && it.exerciseId==target.exerciseId
            }
            val previousSets=previousSlot?.sets?.filter{
                it.prescription.type==DraftSetType.WORKING
            }.orEmpty()
            val matchingPriorWarmups=
                request.repForecastPolicy!=CustomRepForecastPolicy.TARGET_RIR_V2 ||
                    (previousSlot!=null && sameWarmups(previousSlot,target))
            val sameSets=matchingPriorWarmups &&
                previousSets.size==nextSets.size &&
                previousSets.zip(nextSets).all{
                    it.first.prescription.copy(
                        reps=it.second.prescription.reps)==
                        it.second.prescription &&
                        it.first.prescription.reps.maximum==
                        it.second.prescription.reps.maximum &&
                        it.first.prescription.reps.minimum>=
                        it.second.prescription.reps.minimum &&
                        it.first.ordinalInSlot==it.second.ordinalInSlot &&
                        it.first.prescription.amplifier==null
                }
            if(sameSource(previous.snapshot) && sameSets) {
                val priorFacts=previous.evidence.visits.filter {
                    it.slotId==target.slotId &&
                        it.part==CustomWorkoutVisitPart.MAIN &&
                        it.setType==DraftSetType.WORKING
                }
                val straight=nextSets.mapNotNull{it.prescription.load as? DraftSetLoad.Verified}
                val bands=nextSets.map{it.prescription.reps}.distinct()
                if((request.repForecastPolicy==CustomRepForecastPolicy.TARGET_RIR_V2 &&
                        !authenticWarmups(previous,checkNotNull(previousSlot))) ||
                    !matchingReceipts(priorFacts,previousSets))
                    return result(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
                        ids,latest.evidence.workoutId,
                        explanation="The earlier completed day no longer matches its immutable logged working-set prescription.")
                if(straight.size==nextSets.size &&
                    straight.map{it.value.stripTrailingZeros()}.distinct().size==1 &&
                    bands.size==1 && verifiedStockMatches(
                        request.verifiedStock,straight.first())) {
                    fun workout(
                        entry:CustomCoachingHistoricalSession,
                        records:List<CustomHistoricalVisitFact>,
                        authored:List<CustomWorkoutSetSnapshot>
                    )=ProgressionWorkoutEvidence(
                        entry.evidence.workoutId,target.exerciseId,
                        straight.first().unit,bands.first(),authored.size,
                        authored.mapIndexed{index,set->
                            val receipt=records.single{it.setKey==set.setKey}
                            val actual=receipt.actual
                            WorkingSetFact(index,when(receipt.outcome) {
                                KotlinJournalSetOutcome.COMPLETED -> SetOutcome.COMPLETED
                                KotlinJournalSetOutcome.SKIPPED -> SetOutcome.SKIPPED
                                KotlinJournalSetOutcome.FAILED_ATTEMPT -> SetOutcome.FAILED_ATTEMPT
                            },actual?.load,actual?.reps,actual?.actualRir,
                                (set.prescription.load as DraftSetLoad.Verified).value,
                                set.prescription.targetRir)
                        })
                    val reviewed=runCatching {
                        ConservativeProgressionReview.assess(
                            workout(previous,priorFacts,previousSets),
                            workout(latest,facts,oldSets),
                            request.verifiedStock,request.maximumAuthorizedIncrease)
                    }.getOrNull()
                    observedLoad=reviewed?.currentObservedLoad
                    candidate=reviewed?.reviewCandidate
                    if(reviewed!=null)assumedForReview=maxOf(assumedForReview,reviewed.assumedGoalRirSetCount)
                }
            }
        }
        return when {
            candidate!=null->result(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,
                ids,latest.evidence.workoutId,suggestions,observedLoad,
                normalized(checkNotNull(candidate)),
                "Two complete same-day workouts earned a verified load option. Athlete review is required.")
            increased->result(CustomNextWorkoutCoachingStatus.BUILD_REPS,
                ids,latest.evidence.workoutId,suggestions,observedLoad,
                explanation=if(request.repForecastPolicy==CustomRepForecastPolicy.TARGET_RIR_V2)
                    "Review each set's reps at its intended RIR while keeping its own load. Earlier harder sets, rest and longer-term fatigue can change later capacity."
                else "Increase eligible sets by at most one rep; keep each set's own load.")
            else->result(CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,
                ids,latest.evidence.workoutId,suggestions,observedLoad,
                explanation="Maintain the current approved load and rep range.")
        }
    }

    private fun verifiedStockMatches(
        stock:VerifiedLoadStock?,load:DraftSetLoad.Verified
    ):Boolean =stock?.unit==load.unit && stock.basis==load.basis &&
        EquipmentLoadDomain.inspect(stock,load.value,load.unit).let{
            it is LoadInspection.Confirmed && it.exact
        }
}
