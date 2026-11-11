package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.Collections

/** A factual view of a MAIN working set. It is not an issued target or a PR. */
data class NativeMixedWorkingSetFact(
    val setKey: String,
    /** Custom cycles may repeat the same movement in distinct authored slots. */
    val customSlotId: String?,
    val outcome: KotlinJournalSetOutcome,
    val issuedLoad: DraftSetLoad,
    val actual: KotlinPerformedSet?,
    val effectiveEventId: String
)

data class NativeMixedLastExercise(
    val exerciseId: String,
    val workoutId: String,
    val completionOrder: Long,
    val isCustom: Boolean,
    /** Empty means the latest appearance had no MAIN working sets. */
    val workingSets: List<NativeMixedWorkingSetFact>
)

data class NativeMixedPhysicalLoadKey(
    val exerciseId: String,
    val unit: LoadUnit,
    val basis: LoadBasis
)

/** A physical station is comparable only inside its authenticated issued gym. */
data class NativeMixedMachineLoadKey(
    val gymId:String,
    val exerciseId:String,
    val machineInstanceId:String,
    val unit:LoadUnit,
    val basis:LoadBasis
)

data class NativeMixedCompletedLoadFact(
    val key: NativeMixedPhysicalLoadKey,
    val actualLoad: BigDecimal,
    val reps: Int,
    val workoutId: String,
    val completionOrder: Long,
    val setKey: String,
    val customSlotId: String?,
    val effectiveEventId: String
)

/**
 * All metrics remain descriptive. An equipment-aware, longitudinal coaching
 * decision must separately validate the current owner, exercise semantics,
 * session purpose, prescription, actual RIR and *every* recent exposure.
 */
data class NativeKotlinMixedHistoryFacts(
    val athleteId: String,
    val manifestRevision: Long,
    val completedWorkoutIds: List<String>,
    val lastByExercise: Map<String, NativeMixedLastExercise>,
    val highestCompletedLoad: Map<NativeMixedPhysicalLoadKey, NativeMixedCompletedLoadFact>,
    val mostRepsAtLoad: List<NativeMixedCompletedLoadFact>,
    /** Separate actual single evidence; cannot be replaced by 5-rep record at same weight. */
    val bestLoggedSingleByLoad: Map<NativeMixedPhysicalLoadKey,NativeMixedCompletedLoadFact> = emptyMap(),
    /** Factual best completed weights, not certified PRs or interchangeable stacks. */
    val highestCompletedLoadByMachine:
        Map<NativeMixedMachineLoadKey,NativeMixedCompletedLoadFact> = emptyMap(),
    val bestLoggedSingleByMachine:
        Map<NativeMixedMachineLoadKey,NativeMixedCompletedLoadFact> = emptyMap()
) {
    val canPublishPr: Boolean get() = false
    val canIssueProgression: Boolean get() = false
    val canEditProgram: Boolean get() = false
}

enum class NativeMixedFactsRefusal {
    INVALID_TIMELINE, MANIFEST_MISMATCH, DUPLICATE_COMPLETION, CROSS_ATHLETE
}

sealed interface NativeMixedFactsResult {
    data class Review(val facts: NativeKotlinMixedHistoryFacts) : NativeMixedFactsResult
    data class Rejected(val reason: NativeMixedFactsRefusal) : NativeMixedFactsResult
}

/**
 * Only call with the complete owner-fenced, replay-verified mixed timeline.
 * This independent projection has no filesystem access or write authority;
 * a caller-created timeline is NOT proof of a completed workout.
 */
object NativeKotlinMixedHistoryFactsDomain {
    private val id = Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_REVISION = 9_007_199_254_740_991L

    private data class Visit(
        val exercise: String,
        val setKey: String,
        val customSlot: String?,
        val mainWorking: Boolean,
        val outcome: KotlinJournalSetOutcome,
        val issuedLoad: DraftSetLoad,
        val actual: KotlinPerformedSet?,
        val eventId: String
    )

    private fun normalize(load: BigDecimal) =
        BigDecimal(load.stripTrailingZeros().toPlainString())

    fun project(timeline: NativeKotlinMixedHistoryTimeline): NativeMixedFactsResult {
        fun reject(reason: NativeMixedFactsRefusal) = NativeMixedFactsResult.Rejected(reason)
        if (!id.matches(timeline.athleteId) ||
            timeline.manifestRevision !in 1..MAX_REVISION ||
            timeline.completions.any { it.manifest.completionOrder !in 1..MAX_REVISION })
            return reject(NativeMixedFactsRefusal.INVALID_TIMELINE)
        if (timeline.completions.map { it.manifest.workoutId }.distinct().size !=
            timeline.completions.size ||
            timeline.completions.map { it.manifest.storedReceiptId }.distinct().size !=
            timeline.completions.size)
            return reject(NativeMixedFactsRefusal.DUPLICATE_COMPLETION)
        if (timeline.completions.map { it.manifest.completionOrder } !=
            (1..timeline.completions.size).map { it.toLong() })
            return reject(NativeMixedFactsRefusal.MANIFEST_MISMATCH)

        val last = linkedMapOf<String, NativeMixedLastExercise>()
        val highest = linkedMapOf<NativeMixedPhysicalLoadKey, NativeMixedCompletedLoadFact>()
        val repsAtLoad = linkedMapOf<Pair<NativeMixedPhysicalLoadKey, BigDecimal>,
            NativeMixedCompletedLoadFact>()
        val bestSingle = linkedMapOf<NativeMixedPhysicalLoadKey,NativeMixedCompletedLoadFact>()
        val highestByMachine=linkedMapOf<NativeMixedMachineLoadKey,
            NativeMixedCompletedLoadFact>()
        val singlesByMachine=linkedMapOf<NativeMixedMachineLoadKey,
            NativeMixedCompletedLoadFact>()
        for (completion in timeline.completions) {
            val manifest = completion.manifest
            val visits = when (completion) {
                is NativeKotlinMixedHistoryCompletion.Composer -> {
                    if (completion.evidence.source.athleteId != timeline.athleteId)
                        return reject(NativeMixedFactsRefusal.CROSS_ATHLETE)
                    if (completion.evidence.workoutId != manifest.workoutId ||
                        completion.evidence.finishEventId != manifest.finishEventId ||
                        completion.evidence.journalRevision != manifest.originalJournalRevision)
                        return reject(NativeMixedFactsRefusal.MANIFEST_MISMATCH)
                    completion.evidence.sets.map {
                        Visit(it.exerciseId, it.setKey, null,
                            it.type == DraftSetType.WORKING, it.outcome,
                            it.issuedLoad, it.actual, it.effectiveEventId)
                    }
                }
                is NativeKotlinMixedHistoryCompletion.Custom -> {
                    if (completion.evidence.athleteId != timeline.athleteId)
                        return reject(NativeMixedFactsRefusal.CROSS_ATHLETE)
                    if (completion.evidence.workoutId != manifest.workoutId ||
                        completion.evidence.finishedEventId != manifest.finishEventId ||
                        completion.evidence.journalRevision != manifest.originalJournalRevision)
                        return reject(NativeMixedFactsRefusal.MANIFEST_MISMATCH)
                    completion.evidence.visits.map {
                        Visit(it.exerciseId, it.setKey, it.slotId,
                            it.part == CustomWorkoutVisitPart.MAIN &&
                                it.setType == DraftSetType.WORKING,
                            it.outcome, it.prescribedLoad, it.actual,
                            it.effectiveEventId)
                    }
                }
            }
            val issuedGymId=when(completion) {
                is NativeKotlinMixedHistoryCompletion.Composer ->
                    completion.evidence.source.gymId
                is NativeKotlinMixedHistoryCompletion.Custom -> completion.gymId
            }
            val isCustom = completion is NativeKotlinMixedHistoryCompletion.Custom
            for (exercise in visits.map { it.exercise }.distinct()) {
                val working = visits.filter { it.exercise == exercise && it.mainWorking }
                    .map { NativeMixedWorkingSetFact(
                        it.setKey, it.customSlot, it.outcome, it.issuedLoad,
                        it.actual, it.eventId) }
                // A skipped/latest poor session must never be replaced by an
                // older successful session in Last.
                last[exercise] = NativeMixedLastExercise(exercise,
                    manifest.workoutId, manifest.completionOrder, isCustom,
                    Collections.unmodifiableList(working))
            }
            for (visit in visits) {
                if (!visit.mainWorking ||
                    visit.outcome != KotlinJournalSetOutcome.COMPLETED) continue
                val target = visit.issuedLoad as? DraftSetLoad.Verified ?: continue
                val actual = visit.actual ?: continue
                val load = actual.load ?: continue
                if (actual.unit != target.unit || actual.reps !in 1..100 ||
                    load <= BigDecimal.ZERO || load > BigDecimal("100000") ||
                    load.stripTrailingZeros().scale() > 3) continue
                val key = NativeMixedPhysicalLoadKey(
                    visit.exercise, target.unit, target.basis)
                val physical = normalize(load)
                val record = NativeMixedCompletedLoadFact(key, physical, actual.reps,
                    manifest.workoutId, manifest.completionOrder, visit.setKey,
                    visit.customSlot, visit.eventId)
                // A tagged physical machine is NOT a barbell total even
                // when a stale catalog/swap calls its loading basis
                // BARBELL_TOTAL. Keep the factual station record below, but
                // exclude it from general barbell 1RM, Strength Standards
                // and thousand-pound logged-single calculations.
                val machineId=actual.machineInstanceId
                if(target.basis!=LoadBasis.BARBELL_TOTAL || machineId==null) {
                    if (actual.reps == 1) {
                        val previous=bestSingle[key]
                        if(previous==null || physical>previous.actualLoad ||
                            (physical.compareTo(previous.actualLoad)==0 &&
                                manifest.completionOrder>previous.completionOrder))
                            bestSingle[key]=record
                    }
                    val old = highest[key]
                    if (old == null || physical > old.actualLoad ||
                        (physical.compareTo(old.actualLoad) == 0 && actual.reps >= old.reps))
                        highest[key] = record
                    val loadKey = key to physical
                    if (repsAtLoad[loadKey] == null ||
                        actual.reps >= checkNotNull(repsAtLoad[loadKey]).reps)
                        repsAtLoad[loadKey] = record
                }
                // Only the immutable issued gym and an actual, explicit
                // machine ID can authenticate a station-local best. Old
                // custom receipts without a verifiable gym remain legacy
                // facts; they must not become an invented A/B record.
                if(id.matches(issuedGymId) &&
                    KotlinMachineAlternativesDomain.validInstanceId(machineId)) {
                    val machineKey=NativeMixedMachineLoadKey(issuedGymId,
                        visit.exercise,checkNotNull(machineId),
                        target.unit,target.basis)
                    val oldMachine=highestByMachine[machineKey]
                    if(oldMachine==null || physical>oldMachine.actualLoad ||
                        (physical.compareTo(oldMachine.actualLoad)==0 &&
                            (actual.reps>oldMachine.reps ||
                            (actual.reps==oldMachine.reps &&
                                manifest.completionOrder>oldMachine.completionOrder))))
                        highestByMachine[machineKey]=record
                    if(actual.reps==1) {
                        val oldSingle=singlesByMachine[machineKey]
                        if(oldSingle==null || physical>oldSingle.actualLoad ||
                            (physical.compareTo(oldSingle.actualLoad)==0 &&
                                manifest.completionOrder>oldSingle.completionOrder))
                            singlesByMachine[machineKey]=record
                    }
                }
            }
        }
        return NativeMixedFactsResult.Review(NativeKotlinMixedHistoryFacts(
            timeline.athleteId, timeline.manifestRevision,
            Collections.unmodifiableList(timeline.completions.map {
                it.manifest.workoutId }),
            Collections.unmodifiableMap(last),
            Collections.unmodifiableMap(highest),
            Collections.unmodifiableList(repsAtLoad.values.toList()),
            Collections.unmodifiableMap(bestSingle),
            Collections.unmodifiableMap(highestByMachine),
            Collections.unmodifiableMap(singlesByMachine)
        ))
    }
}

/** Convenience over a complete, already verified owner-fenced read result. */
fun NativeKotlinMixedHistoryTimeline.reviewWorkingSetFacts(): NativeMixedFactsResult =
    NativeKotlinMixedHistoryFactsDomain.project(this)
