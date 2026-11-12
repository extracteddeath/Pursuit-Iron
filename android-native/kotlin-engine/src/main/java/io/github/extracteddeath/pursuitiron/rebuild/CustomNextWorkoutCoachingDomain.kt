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
    TARGET_RIR_V2,
    /** Opt-in longitudinal guard; V1 and V2 saved receipts retain old replay. */
    TARGET_RIR_TREND_V3
}

data class CustomNextWorkoutCoachingRequest(
    val next:CustomWorkoutDaySnapshot,
    val slotId:String,
    val completed:List<CustomCoachingHistoricalSession>,
    val verifiedStock:VerifiedLoadStock?,
    val maximumAuthorizedIncrease:BigDecimal,
    val readiness:CoachReadiness,
    val movementRestricted:Boolean,
    val repForecastPolicy:CustomRepForecastPolicy=CustomRepForecastPolicy.LEGACY_STEP_V1,
    /** Physical machine selected in the verified gym; null means historical legacy only. */
    val requestedMachineInstanceId:String?=null
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
    HOLD_MARKED_SAME_DAY_CAPACITY_DROP,
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
    val forecastLimitations:Set<RepForecastLimitation> = emptySet(),
    /** Confirmed drops measured only from two exact same-slot workouts with
     * independently reported RIR. Not a fatigue diagnosis or new consent. */
    val capacityDropsFromPreviousSession:List<Int> = emptyList()
) {
    val mayIssueWorkout:Boolean get()=false
    val mayEditProgram:Boolean get()=false
    val mayChangeHistory:Boolean get()=false
}

/** Never bypass a failed set, an intervening day, a changed source, or RIR. */
object CustomNextWorkoutCoachingDomain {
    private fun CustomRepForecastPolicy.usesTargetRir()=
        this!=CustomRepForecastPolicy.LEGACY_STEP_V1

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

    /**
     * This is a PERFORMANCE regression guard, not a chronic-fatigue model.
     * Two completed exposures do not identify its cause. Require two or more
     * working sets with authentic independently REPORTED effort, unchanged
     * loading, exact exercise/day/source and matching warmup prescriptions.
     * If any input is missing, do not fabricate a decline.
     */
    private fun comparableReportedCapacityChanges(
        previous:CustomCoachingHistoricalSession,
        latest:CustomCoachingHistoricalSession,
        latestSlot:CustomWorkoutSlotSnapshot,
        latestFacts:List<CustomHistoricalVisitFact>
    ):List<Int>? {
        val previousSlot=previous.snapshot.slots.singleOrNull{
            it.slotId==latestSlot.slotId &&
                it.exerciseId==latestSlot.exerciseId
        } ?:return null
        val older=previousSlot.sets.filter{
            it.prescription.type==DraftSetType.WORKING
        }
        val newer=latestSlot.sets.filter{
            it.prescription.type==DraftSetType.WORKING
        }
        if(newer.size<2 || older.size!=newer.size ||
            previous.snapshot.stamp!=latest.snapshot.stamp ||
            previous.snapshot.blockId!=latest.snapshot.blockId ||
            previous.snapshot.weekday!=latest.snapshot.weekday ||
            previous.snapshot.phase!=latest.snapshot.phase ||
            previous.snapshot.sessionPurpose!=latest.snapshot.sessionPurpose ||
            previous.snapshot.split!=latest.snapshot.split ||
            !sameWarmups(previousSlot,latestSlot) ||
            !authenticWarmups(previous,previousSlot) ||
            older.zip(newer).any { (a,b)->
                a.ordinalInSlot!=b.ordinalInSlot ||
                    a.prescription!=b.prescription
            }) return null
        val before=previous.evidence.visits.filter{
            it.slotId==latestSlot.slotId &&
                it.part==CustomWorkoutVisitPart.MAIN &&
                it.setType==DraftSetType.WORKING
        }
        if(!matchingReceipts(before,older) ||
            !matchingReceipts(latestFacts,newer))
            return null
        fun matchesActualLoad(
            fact:CustomHistoricalVisitFact,
            set:CustomWorkoutSetSnapshot
        ):Boolean {
            val actual=fact.actual ?:return false
            return when(val prescribed=set.prescription.load) {
                DraftSetLoad.ExplicitBodyweight ->
                    actual.load==null && actual.unit==null
                is DraftSetLoad.Verified ->
                    actual.load!=null && actual.unit==prescribed.unit &&
                        same(actual.load,prescribed.value)
            }
        }
        val differences=older.indices.map {i->
            val a=before.single{it.setKey==older[i].setKey}
            val b=latestFacts.single{it.setKey==newer[i].setKey}
            val olderRir=a.actual?.actualRir
            val newerRir=b.actual?.actualRir
            if(a.outcome!=KotlinJournalSetOutcome.COMPLETED ||
                b.outcome!=KotlinJournalSetOutcome.COMPLETED ||
                olderRir==null || newerRir==null ||
                !matchesActualLoad(a,older[i]) ||
                !matchesActualLoad(b,newer[i])) return null
            // Same goal/load: comparing reps plus reported reps remaining
            // estimates relative same-set capacity without claiming the
            // older RIR was perfectly measured.
            val beforeCapacity=checkNotNull(a.actual).reps+olderRir
            val nowCapacity=checkNotNull(b.actual).reps+newerRir
            beforeCapacity-nowCapacity
        }
        return differences
    }

    fun review(request:CustomNextWorkoutCoachingRequest):CustomNextWorkoutCoachingReview {
        var assumedForReview=0
        var trendDrops:List<Int> = emptyList()
        var hasComparableReportedTrend=false
        // Canonical source owns current-policy uncertainty and provenance.
        var sharedForecastLimitations:Set<RepForecastLimitation> = emptySet()
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
            if(request.repForecastPolicy.usesTargetRir())
                buildSet {
                    if(request.repForecastPolicy==CustomRepForecastPolicy.TARGET_RIR_V2 ||
                        !hasComparableReportedTrend)
                        add(RepForecastLimitation.SINGLE_EXPOSURE)
                    add(RepForecastLimitation.RIR_ESTIMATE_UNCALIBRATED)
                    add(RepForecastLimitation.ACTUAL_REST_AND_PRIOR_EXERCISE_WORK_UNAVAILABLE)
                    add(RepForecastLimitation.CHRONIC_FATIGUE_NOT_ESTIMATED)
                    if(reps.dropLast(1).any {it.changed})
                        add(RepForecastLimitation.EARLIER_SET_WORK_CHANGED)
                    addAll(sharedForecastLimitations)
                    // Only a genuine comparable prior V3 history can
                    // remove the one-session uncertainty flag.
                    if(request.repForecastPolicy==
                        CustomRepForecastPolicy.TARGET_RIR_TREND_V3 &&
                        hasComparableReportedTrend)
                        remove(RepForecastLimitation.SINGLE_EXPOSURE)
                }
            else emptySet(),
            Collections.unmodifiableList(trendDrops.toList()))
        if(request.maximumAuthorizedIncrease<=zero ||
            request.maximumAuthorizedIncrease>maximum ||
            request.maximumAuthorizedIncrease.stripTrailingZeros().scale()>3 ||
            (request.requestedMachineInstanceId!=null &&
                !KotlinMachineAlternativesDomain.validInstanceId(
                    request.requestedMachineInstanceId)) ||
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
        val dayAppearances=request.completed.filter {
            it.snapshot.stamp.cycleId==request.next.stamp.cycleId &&
                it.snapshot.blockId==request.next.blockId &&
                it.snapshot.weekday==request.next.weekday
        }
        // A physical station is a separate load-progression thread, not a
        // different exercise or dose. Never compare A to B (nor unlabelled
        // legacy to A) or silently borrow older evidence across a bad set.
        val targetMachine=request.requestedMachineInstanceId
            ?:KotlinMachineAlternativesDomain.LEGACY_MACHINE_ID
        fun machines(session:CustomCoachingHistoricalSession):Set<String> =
            session.evidence.visits.filter {
                it.slotId==target.slotId &&
                    it.part==CustomWorkoutVisitPart.MAIN &&
                    it.setType==DraftSetType.WORKING
            }.map {KotlinMachineAlternativesDomain.normalizeMachineId(
                it.actual?.machineInstanceId)}.toSet()
        val appearances=dayAppearances.filter {
            machines(it)==setOf(targetMachine)
        }
        // An older workout is not a "next" workout if this training day
        // has already been finished in that absolute week or a later one.
        // Do not cherry-pick earlier successful sessions and prescribe
        // progression into a historically completed calendar slot.
        // Different weekdays remain independent (Upper versus Push).
        if(dayAppearances.any{
                it.snapshot.absoluteWeek>=request.next.absoluteWeek
            })
            return result(CustomNextWorkoutCoachingStatus.TARGET_ALREADY_TRAINED,
                dayAppearances.takeLast(2).map{it.evidence.workoutId},
                dayAppearances.last().evidence.workoutId,
                explanation="This training day has a completed workout in the requested week or a later week. Review the next untrained day.")
        val latestDay=dayAppearances.lastOrNull()
        if(latestDay!=null && machines(latestDay).size!=1)
            return result(CustomNextWorkoutCoachingStatus.MISSED_OR_FAILED_SETS,
                dayAppearances.takeLast(2).map{it.evidence.workoutId},
                latestDay.evidence.workoutId,
                explanation="Mixed or unassigned physical-machine sets on the last training day cannot earn a new load. Review its factual history.")
        if(appearances.isEmpty())
            return result(CustomNextWorkoutCoachingStatus.NO_MATCHING_SESSION,
                explanation="No comparable performance on this physical machine and training day. Establish a separate A/B baseline.")
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
        if(request.repForecastPolicy.usesTargetRir()) {
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
        if(request.repForecastPolicy.usesTargetRir() &&
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

        if(request.repForecastPolicy==CustomRepForecastPolicy.TARGET_RIR_TREND_V3 &&
            appearances.size>=2) {
            val differences=comparableReportedCapacityChanges(
                appearances[appearances.lastIndex-1],latest,oldSlot,facts)
            if(differences!=null)hasComparableReportedTrend=true
            if(differences!=null && differences.all{it>=2}) {
                trendDrops=differences
                return result(
                    CustomNextWorkoutCoachingStatus.HOLD_MARKED_SAME_DAY_CAPACITY_DROP,
                    ids,latest.evidence.workoutId,
                    explanation="Every comparable working set lost at least two estimated reps of capacity since the previous same-weekday workout, using separately reported RIR and the same load. Hold this progression and review recovery, rest and the next session. Two sessions do not establish chronic fatigue.")
            }
        }

        // Current custom and generated sessions use the SAME complete
        // FatigueAwareRepTargetReviewDomain assessment. This adapter only
        // translates exact, owner-verified custom slot receipts to its
        // canonical working-set evidence. No second rep/effort rule lives here.
        // Historical V1 alone retains its frozen, previously accepted steps.
        val overshotRepCeiling=request.repForecastPolicy.usesTargetRir() &&
            facts.any {fact->
                val issued=oldSets.single{it.setKey==fact.setKey}
                checkNotNull(fact.actual).reps>issued.prescription.reps.maximum
            }
        val currentReview=if(request.repForecastPolicy.usesTargetRir()) {
            val bands=nextSets.map{it.prescription.reps}.distinct()
            val unloaded=nextSets.all{
                it.prescription.load==DraftSetLoad.ExplicitBodyweight
            } && oldSets.all{
                it.prescription.load==DraftSetLoad.ExplicitBodyweight
            }
            val loads=nextSets.map{it.prescription.load as? DraftSetLoad.Verified}
            val verified=loads.filterNotNull()
            if(bands.size!=1 || (!unloaded && (
                verified.size!=nextSets.size ||
                verified.map{it.unit}.distinct().size!=1 ||
                verified.map{it.basis}.distinct().size!=1)))
                return result(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,
                    ids,latest.evidence.workoutId,
                    explanation="This set scheme needs a consistent verified implement or explicit unloaded bodyweight identity and compatible rep ranges for the shared next-session review.")
            val observations=oldSets.mapIndexed{ordinal,set->
                val receipt=facts.single{it.setKey==set.setKey}
                val actual=receipt.actual
                WorkingSetFact(ordinal,when(receipt.outcome) {
                    KotlinJournalSetOutcome.COMPLETED->SetOutcome.COMPLETED
                    KotlinJournalSetOutcome.SKIPPED->SetOutcome.SKIPPED
                    KotlinJournalSetOutcome.FAILED_ATTEMPT->SetOutcome.FAILED_ATTEMPT
                },actual?.load,actual?.reps,actual?.actualRir,
                    (set.prescription.load as? DraftSetLoad.Verified)?.value,
                    set.prescription.targetRir)
            }
            runCatching {
                val goals=nextSets.map{it.prescription.targetRir}
                if(unloaded)
                    FatigueAwareRepTargetReviewDomain.assessBodyweight(
                        BodyweightRepTargetEvidence(
                            latest.evidence.workoutId,target.exerciseId,
                            bands.single(),oldSets.size,observations),
                        request.readiness,request.movementRestricted,
                        targetRirByOrdinal=goals)
                else FatigueAwareRepTargetReviewDomain.assess(
                    ProgressionWorkoutEvidence(latest.evidence.workoutId,
                        target.exerciseId,verified.first().unit,
                        bands.single(),oldSets.size,observations,
                        trainingDayId="${request.next.stamp.cycleId}:${request.next.weekday}:${target.slotId}"),
                    request.verifiedStock,verified.first().basis,
                    request.readiness,request.movementRestricted,
                    // Mixed loads are acceptable only because the already
                    // signed custom slot supplies each immutable load and
                    // the canonical policy verifies EVERY recorded increment.
                    verifiedPerSetLoading=true,
                    policyVersion=AdaptiveRepPolicyVersion.TARGET_RIR_V2,
                    targetRirByOrdinal=goals,
                    // A same-slot signed, unchanged-load REP preview can
                    // survive an unavailable current gym read. This NEVER
                    // earns a load step, bypasses contradictory stock, or
                    // applies to unverified physical Machine A/B stations.
                    unchangedIssuedLoadReviewOnly=
                        request.verifiedStock==null &&
                        request.requestedMachineInstanceId==null)
            }.getOrElse {
                return result(CustomNextWorkoutCoachingStatus.CHANGED_PRESCRIPTION,
                    ids,latest.evidence.workoutId,
                    explanation="The authentic working-set evidence could not be reviewed under the shared target-RIR policy.")
            }
        } else null

        sharedForecastLimitations=currentReview?.forecastLimitations.orEmpty()

        // All present and future V2/V3 rep outcomes come from the canonical
        // assessment, including load verification, user edits, rep ceilings,
        // missing RIR and per-set fatigue uncertainty. Only V1 uses a frozen
        // compatibility algorithm for previously accepted receipts.
        if(currentReview!=null) when(currentReview.reason) {
            FatigueRepReviewReason.EFFORT_REQUIRES_LOAD_REVIEW ->
                return result(CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,
                    ids,latest.evidence.workoutId,
                    explanation="At the prescribed goal RIR, at least one completed set is projected below its minimum rep target. Review that set's verified load, range or recovery before changing the future program. No reps were raised to an invented minimum.")
            FatigueRepReviewReason.TARGET_RIR_ALIGNED_REPS_FOR_REVIEW,
            FatigueRepReviewReason.NO_DEMONSTRATED_EFFORT_HEADROOM,
            FatigueRepReviewReason.ALREADY_AT_REP_CEILING,
            // An over-ceiling session can STILL earn an independent
            // verified load review from two compatible complete workouts.
            FatigueRepReviewReason.REP_CEILING_OVERSHOT_REQUIRES_LOAD_REVIEW -> Unit
            else ->return result(CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,
                ids,latest.evidence.workoutId,
                explanation="The shared target-RIR review rejected these working-set facts: ${currentReview.reason}. Do not invent a new rep or load target.")
        }

        val targets=currentReview?.candidateRepTargets
        val suggestions=nextSets.zip(oldSets).mapIndexed{ordinal,(set,previousSet)->
            val observation=facts.single{it.setKey==previousSet.setKey}
            val actual=checkNotNull(observation.actual)
            val observed=actual.reps.coerceIn(
                set.prescription.reps.minimum,set.prescription.reps.maximum)
            val effortForSet=effort.getValue(previousSet.setKey)
            val next=if(currentReview!=null) {
                targets?.get(ordinal) ?: observed
            } else {
                // Frozen V1: historical receipt replay only; no new V1
                // algorithm is added and no old approval is rewritten.
                val issued=set.prescription.load
                val sameIssued=when(issued) {
                    is DraftSetLoad.Verified ->
                        actual.load!=null && actual.unit==issued.unit &&
                            same(actual.load,issued.value)
                    DraftSetLoad.ExplicitBodyweight ->
                        actual.load==null && actual.unit==null
                }
                if(!sameIssued || actual.reps>set.prescription.reps.maximum)
                    observed
                else if(checkNotNull(effortForSet.rir)>=2)
                    (observed+1).coerceAtMost(set.prescription.reps.maximum)
                else observed
            }
            CustomNextSetCoaching(set.setKey,actual.reps,next,
                actual.actualRir,next>observed,effortForSet.rir,effortForSet.source)
        }
        val increased=currentReview?.reason==
            FatigueRepReviewReason.TARGET_RIR_ALIGNED_REPS_FOR_REVIEW ||
            (currentReview==null && suggestions.any{it.changed})

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
                !request.repForecastPolicy.usesTargetRir() ||
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
                if((request.repForecastPolicy.usesTargetRir() &&
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
                if(overshotRepCeiling)
                    "One or more sets exceeded their prescribed rep ceiling. Two comparable completed same-day workouts support a verified higher LOAD review, not a rep-only progression. Athlete approval is required."
                else "Two complete same-day workouts earned a verified load option. Athlete review is required.")
            overshotRepCeiling->result(
                CustomNextWorkoutCoachingStatus.HOLD_CURRENT_TARGETS,
                ids,latest.evidence.workoutId,load=observedLoad,
                explanation="A recorded working set exceeded its authored rep ceiling. Review the load and rep range; lowering that observed count back to the ceiling cannot be presented as an earned rep progression. No rep amendment was proposed.")
            increased->result(CustomNextWorkoutCoachingStatus.BUILD_REPS,
                ids,latest.evidence.workoutId,suggestions,observedLoad,
                explanation=if(request.repForecastPolicy.usesTargetRir())
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
