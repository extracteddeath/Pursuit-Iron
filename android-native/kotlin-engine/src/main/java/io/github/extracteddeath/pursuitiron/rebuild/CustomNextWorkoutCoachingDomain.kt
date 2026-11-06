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

data class CustomNextWorkoutCoachingRequest(
    val next:CustomWorkoutDaySnapshot,
    val slotId:String,
    val completed:List<CustomCoachingHistoricalSession>,
    val verifiedStock:VerifiedLoadStock?,
    val maximumAuthorizedIncrease:BigDecimal,
    val readiness:CoachReadiness,
    val movementRestricted:Boolean
)

enum class CustomNextWorkoutCoachingStatus {
    INVALID_HISTORY,
    NO_MATCHING_SESSION,
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
    val actualRir:Int,
    val changed:Boolean
)

data class CustomNextWorkoutCoachingReview(
    val status:CustomNextWorkoutCoachingStatus,
    val comparedWorkoutIds:List<String>,
    val latestWorkoutId:String?,
    val nextSetSuggestions:List<CustomNextSetCoaching>,
    val observedStraightSetLoad:BigDecimal?,
    val loadReviewCandidate:BigDecimal?,
    val explanation:String
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

    fun review(request:CustomNextWorkoutCoachingRequest):CustomNextWorkoutCoachingReview {
        fun result(
            status:CustomNextWorkoutCoachingStatus,
            ids:List<String> = emptyList(),latest:String?=null,
            reps:List<CustomNextSetCoaching> = emptyList(),
            load:BigDecimal?=null,candidate:BigDecimal?=null,
            explanation:String=status.name
        )=CustomNextWorkoutCoachingReview(status,
            Collections.unmodifiableList(ids.toList()),latest,
            Collections.unmodifiableList(reps.toList()),load,candidate,
            explanation)
        if(request.maximumAuthorizedIncrease<=zero ||
            request.maximumAuthorizedIncrease>maximum ||
            request.maximumAuthorizedIncrease.stripTrailingZeros().scale()>3 ||
            request.completed.zipWithNext().any{
                it.first.completionOrder>=it.second.completionOrder
            } ||
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

        val facts=latest.evidence.visits.filter {
            it.slotId==target.slotId &&
                it.part==CustomWorkoutVisitPart.MAIN &&
                it.setType==DraftSetType.WORKING
        }
        if(facts.size!=nextSets.size ||
            facts.map{it.setKey}.toSet()!=oldSets.map{it.setKey}.toSet() ||
            facts.any{it.exerciseId!=target.exerciseId})
            return result(CustomNextWorkoutCoachingStatus.INVALID_HISTORY,
                ids,latest.evidence.workoutId,
                explanation="Logged working sets do not match the finished immutable workout.")

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
        if(facts.any{it.actual?.actualRir==null})
            return result(CustomNextWorkoutCoachingStatus.NEEDS_RECORDED_EFFORT,
                ids,latest.evidence.workoutId,
                explanation="Actual RIR was not recorded for every working set.")
        if(facts.any{it.actual!!.actualRir!!<=1})
            return result(CustomNextWorkoutCoachingStatus.HOLD_NEAR_FAILURE,
                ids,latest.evidence.workoutId,
                explanation="Near-failure effort does not justify adding reps or load.")

        // Recommend at most ONE additional rep per individual working set.
        // A mixed-loading top set never becomes an all-sets prescription.
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
            val next=if(sameIssued && actual.reps<=upper &&
                actual.actualRir!!>=2) (observed+1).coerceAtMost(upper)
                else observed
            CustomNextSetCoaching(set.setKey,actual.reps,next,
                actual.actualRir!!,next>observed)
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
            val sameSets=previousSets.size==nextSets.size &&
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
                if(priorFacts.size==nextSets.size &&
                    priorFacts.map{it.setKey}.toSet()==
                        previousSets.map{it.setKey}.toSet() &&
                    straight.size==nextSets.size &&
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
                explanation="Increase eligible sets by at most one rep; keep each set's own load.")
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
