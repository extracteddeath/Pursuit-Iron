package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Source-exact non-repetition measurements. This authority is deliberately
 * separate from DraftSetLoad + RepBand and the old numeric-load x reps journal.
 *
 * These 14 definitions are CANDIDATES only until owner-authenticated gym,
 * issuance, on-disk journal and Compose entry are connected and accepted.
 * In particular: elapsed seconds != repetitions, centimeters != repetitions,
 * band stretch != a verified constant external weight, and an added plate
 * is not the athlete's bodyweight.
 */
enum class MeasuredExerciseKind { HOLD_SECONDS, CARRY_CENTIMETERS, BANDED_REPETITIONS }
enum class MeasuredLoadKind { UNWEIGHTED, VERIFIED_ADDED_LOAD, VERIFIED_PER_HAND, BANDED_BODYWEIGHT }
enum class MeasuredSideRule { BOTH, LEFT_AND_RIGHT, EXPLICIT_SINGLE_SIDE }
enum class MeasuredSide { BOTH, LEFT, RIGHT }

data class MeasuredExerciseDefinition(
    val exerciseId:String,
    val displayName:String,
    val kind:MeasuredExerciseKind,
    val loadKind:MeasuredLoadKind,
    val sideRule:MeasuredSideRule,
    val requiredFixtures:Set<String>,
    /** External mass convention, never the whole body/system load. */
    val expectedBasis:LoadBasis?=null
)

/**
 * Every name is the exact active-web built-in ID. Legacy practicalReps on
 * these timed/carry cases are NOT used as a duration/distance prescription.
 * Actual set targets require an athlete/coach authored quantity.
 */
object MeasuredExerciseCatalogDomain {
    private val entries=listOf(
        MeasuredExerciseDefinition("superman","Superman Hold",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.UNWEIGHTED,
            MeasuredSideRule.BOTH,emptySet()),
        MeasuredExerciseDefinition("plank","Plank",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.UNWEIGHTED,
            MeasuredSideRule.BOTH,emptySet()),
        MeasuredExerciseDefinition("side-plank","Side Plank",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.UNWEIGHTED,
            MeasuredSideRule.LEFT_AND_RIGHT,emptySet()),
        // Legacy source tags dumbbells, but plate pinching needs its own
        // explicitly verified physical plate setup, not a dumbbell alias.
        MeasuredExerciseDefinition("plate-pinch","Plate Pinch Hold",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE,setOf("pinchable-plates"),
            LoadBasis.EXERCISE_OVERRIDE),
        MeasuredExerciseDefinition("dead-hang","Dead Hang",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.UNWEIGHTED,
            MeasuredSideRule.BOTH,setOf("pull-up-bar")),
        MeasuredExerciseDefinition("hollow-hold","Hollow Body Hold",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.UNWEIGHTED,
            MeasuredSideRule.BOTH,emptySet()),
        MeasuredExerciseDefinition("copenhagen","Copenhagen Plank",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.UNWEIGHTED,
            MeasuredSideRule.LEFT_AND_RIGHT,setOf("stable-copenhagen-support")),
        MeasuredExerciseDefinition("wall-sit","Wall Sit",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.UNWEIGHTED,
            MeasuredSideRule.BOTH,setOf("stable-wall")),
        MeasuredExerciseDefinition("l-sit","L-Sit Hold",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.UNWEIGHTED,
            MeasuredSideRule.BOTH,setOf("confirmed-l-sit-support")),
        MeasuredExerciseDefinition("weighted-plank","Weighted Plank",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredSideRule.BOTH,setOf("secure-plank-load-placement"),
            LoadBasis.EXERCISE_OVERRIDE),
        MeasuredExerciseDefinition("towel-hang","Towel Dead Hang",
            MeasuredExerciseKind.HOLD_SECONDS,MeasuredLoadKind.UNWEIGHTED,
            MeasuredSideRule.BOTH,setOf("pull-up-bar","secured-hang-towel")),
        MeasuredExerciseDefinition("farmers","Farmer's Carry",
            MeasuredExerciseKind.CARRY_CENTIMETERS,MeasuredLoadKind.VERIFIED_PER_HAND,
            MeasuredSideRule.BOTH,emptySet(),LoadBasis.DUMBBELL_PER_HAND),
        MeasuredExerciseDefinition("suitcase-carry","Suitcase Carry",
            MeasuredExerciseKind.CARRY_CENTIMETERS,MeasuredLoadKind.VERIFIED_PER_HAND,
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE,emptySet(),LoadBasis.DUMBBELL_PER_HAND),
        MeasuredExerciseDefinition("band-pushup","Banded Push-Up",
            MeasuredExerciseKind.BANDED_REPETITIONS,MeasuredLoadKind.BANDED_BODYWEIGHT,
            MeasuredSideRule.BOTH,setOf("verified-band-pushup-setup"))
    )
    val all:List<MeasuredExerciseDefinition> =
        Collections.unmodifiableList(entries)
    private val byId=entries.associateBy { it.exerciseId }
    fun find(exerciseId:String):MeasuredExerciseDefinition?=byId[exerciseId]
}

/**
 * Exact physical equipment source for measured external mass. This only
 * names real, separately saved gym stock. A generic catalog exercise or
 * a similarly named machine is NEVER proof of a load or two matching bells.
 */
object MeasuredPhysicalStockSource {
    fun equipmentIdFor(exerciseId:String):String?=when(exerciseId) {
        "farmers","suitcase-carry" -> "dumbbell-rack"
        "plate-pinch","weighted-plank" -> "single-weight-plate"
        else -> null
    }
}

data class MeasuredExerciseSetPlan(
    val workoutId:String,
    val setKey:String,
    val exerciseId:String,
    val gymId:String,
    val gymRevision:Long,
    /** Exact physical configuration, not the logical movement family. */
    val physicalSetupId:String,
    val targetAmount:Int,
    val sides:Set<MeasuredSide>,
    val confirmedFixtures:Set<String>,
    val physicalStock:VerifiedLoadStock?=null,
    val intendedExternalLoad:BigDecimal?=null,
    val unit:LoadUnit?=null,
    /** Explicit proof of two identical dumbbells for a farmer's carry. */
    val matchedPairConfirmed:Boolean=false,
    /** Qualitative band setup; never an invented pound-equivalent load. */
    val bandId:String?=null,
    val gymConfirmedBandIds:Set<String> = emptySet()
) {
    val canIssueWorkout:Boolean get()=false
    val canWriteHistory:Boolean get()=false
}

data class MeasuredSideResult(val side:MeasuredSide,val amount:Int)

data class MeasuredExerciseActual(
    val physicalSetupId:String,
    val sides:List<MeasuredSideResult>,
    /** User-observed external mass; never silently copied from the target. */
    val actualExternalLoad:BigDecimal?=null,
    val unit:LoadUnit?=null,
    /** Same independently confirmed band configuration as the issued set. */
    val bandId:String?=null,
    /** Optional additional factual duration for carries, not their distance. */
    val elapsedSeconds:Int?=null
)

enum class MeasuredSetOutcome { COMPLETED, SKIPPED, FAILED_ATTEMPT }
enum class MeasurementRefusal {
    UNKNOWN_EXERCISE, INVALID_ID_OR_SOURCE, MISSING_FIXTURE,
    INCORRECT_SIDES, INVALID_QUANTITY, UNVERIFIED_STOCK,
    UNVERIFIED_BAND, WRONG_MEASUREMENT_OR_LOAD,
    STALE_REVISION, EVENT_COLLISION, ALREADY_RECORDED,
    INVALID_CORRECTION, FINISHED, INCOMPLETE
}

sealed interface MeasurementPlanResult {
    data class Candidate(val plan:MeasuredExerciseSetPlan):MeasurementPlanResult {
        val mayIssueWorkout:Boolean get()=false
    }
    data class Rejected(val reason:MeasurementRefusal):MeasurementPlanResult
}

object MeasuredExerciseDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val maxLoad=BigDecimal("100000")

    private fun numericMass(value:BigDecimal):Boolean =
        value>=BigDecimal.ZERO && value<=maxLoad &&
            value.stripTrailingZeros().scale()<=3

    private fun expectedSides(rule:MeasuredSideRule,sides:Set<MeasuredSide>):Boolean =
        when(rule) {
            MeasuredSideRule.BOTH -> sides==setOf(MeasuredSide.BOTH)
            MeasuredSideRule.LEFT_AND_RIGHT ->
                sides==setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
            MeasuredSideRule.EXPLICIT_SINGLE_SIDE ->
                sides==setOf(MeasuredSide.LEFT) || sides==setOf(MeasuredSide.RIGHT)
        }

    private fun quantityValid(kind:MeasuredExerciseKind,amount:Int):Boolean =
        when(kind) {
            MeasuredExerciseKind.HOLD_SECONDS -> amount in 1..3600
            // 1 cm to 10 km, stored as exact integer centimeters.
            MeasuredExerciseKind.CARRY_CENTIMETERS -> amount in 1..1_000_000
            MeasuredExerciseKind.BANDED_REPETITIONS -> amount in 1..100
        }

    /** Review only. Never accepts a raw old practicalReps placeholder as
     * evidence of seconds, centimeters or elastic force. */
    fun inspect(plan:MeasuredExerciseSetPlan):MeasurementPlanResult {
        val definition=MeasuredExerciseCatalogDomain.find(plan.exerciseId)
            ?:return MeasurementPlanResult.Rejected(MeasurementRefusal.UNKNOWN_EXERCISE)
        if(!listOf(plan.workoutId,plan.setKey,plan.exerciseId,plan.gymId,
                plan.physicalSetupId).all(id::matches) ||
            plan.gymRevision<1 || plan.confirmedFixtures.size>16 ||
            plan.gymConfirmedBandIds.size>32 ||
            (plan.confirmedFixtures+plan.gymConfirmedBandIds).any{!id.matches(it)})
            return MeasurementPlanResult.Rejected(MeasurementRefusal.INVALID_ID_OR_SOURCE)
        if(!plan.confirmedFixtures.containsAll(definition.requiredFixtures))
            return MeasurementPlanResult.Rejected(MeasurementRefusal.MISSING_FIXTURE)
        if(!expectedSides(definition.sideRule,plan.sides))
            return MeasurementPlanResult.Rejected(MeasurementRefusal.INCORRECT_SIDES)
        if(!quantityValid(definition.kind,plan.targetAmount))
            return MeasurementPlanResult.Rejected(MeasurementRefusal.INVALID_QUANTITY)
        when(definition.loadKind) {
            MeasuredLoadKind.UNWEIGHTED -> {
                if(plan.physicalStock!=null || plan.intendedExternalLoad!=null ||
                    plan.unit!=null || plan.bandId!=null || plan.matchedPairConfirmed)
                    return MeasurementPlanResult.Rejected(
                        MeasurementRefusal.WRONG_MEASUREMENT_OR_LOAD)
            }
            MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND -> {
                val stock=plan.physicalStock ?:return MeasurementPlanResult.Rejected(
                    MeasurementRefusal.UNVERIFIED_STOCK)
                val amount=plan.intendedExternalLoad ?:return MeasurementPlanResult.Rejected(
                    MeasurementRefusal.UNVERIFIED_STOCK)
                val unit=plan.unit ?:return MeasurementPlanResult.Rejected(
                    MeasurementRefusal.UNVERIFIED_STOCK)
                if(plan.bandId!=null || stock.basis!=definition.expectedBasis ||
                    (plan.matchedPairConfirmed &&
                        plan.exerciseId!="farmers") ||
                    (plan.exerciseId=="farmers" && !plan.matchedPairConfirmed) ||
                    !numericMass(amount))
                    return MeasurementPlanResult.Rejected(
                        MeasurementRefusal.UNVERIFIED_STOCK)
                val proof=EquipmentLoadDomain.inspect(stock,amount,unit)
                if(proof !is LoadInspection.Confirmed || !proof.exact ||
                    proof.basis!=definition.expectedBasis)
                    return MeasurementPlanResult.Rejected(
                        MeasurementRefusal.UNVERIFIED_STOCK)
            }
            MeasuredLoadKind.BANDED_BODYWEIGHT -> {
                if(plan.physicalStock!=null || plan.intendedExternalLoad!=null ||
                    plan.unit!=null || plan.matchedPairConfirmed)
                    return MeasurementPlanResult.Rejected(
                        MeasurementRefusal.WRONG_MEASUREMENT_OR_LOAD)
                if(plan.bandId==null || !id.matches(plan.bandId) ||
                    plan.bandId !in plan.gymConfirmedBandIds)
                    return MeasurementPlanResult.Rejected(MeasurementRefusal.UNVERIFIED_BAND)
            }
        }
        return MeasurementPlanResult.Candidate(plan)
    }

    fun validActual(
        plan:MeasuredExerciseSetPlan,
        outcome:MeasuredSetOutcome,
        actual:MeasuredExerciseActual?
    ):Boolean {
        val definition=MeasuredExerciseCatalogDomain.find(plan.exerciseId)
            ?:return false
        if(inspect(plan) !is MeasurementPlanResult.Candidate)return false
        if(outcome==MeasuredSetOutcome.SKIPPED)return actual==null
        val performed=actual ?:return false
        if(performed.physicalSetupId!=plan.physicalSetupId ||
            performed.sides.map{it.side}.toSet()!=plan.sides ||
            performed.sides.size!=plan.sides.size)return false
        val max=when(definition.kind) {
            MeasuredExerciseKind.HOLD_SECONDS -> 3600
            MeasuredExerciseKind.CARRY_CENTIMETERS -> 1_000_000
            MeasuredExerciseKind.BANDED_REPETITIONS -> 100
        }
        val min=if(outcome==MeasuredSetOutcome.FAILED_ATTEMPT)0 else 1
        if(performed.sides.any{it.amount !in min..max} ||
            (outcome==MeasuredSetOutcome.FAILED_ATTEMPT &&
                performed.sides.any{it.amount!=0}))return false
        if(definition.kind!=MeasuredExerciseKind.CARRY_CENTIMETERS &&
            performed.elapsedSeconds!=null)return false
        if(performed.elapsedSeconds!=null &&
            performed.elapsedSeconds !in 1..3600)return false
        when(definition.loadKind) {
            MeasuredLoadKind.UNWEIGHTED ->
                return performed.actualExternalLoad==null &&
                    performed.unit==null && performed.bandId==null
            MeasuredLoadKind.BANDED_BODYWEIGHT ->
                return performed.actualExternalLoad==null &&
                    performed.unit==null && performed.bandId==plan.bandId
            MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND -> {
                val actualLoad=performed.actualExternalLoad ?:return false
                val stock=plan.physicalStock ?:return false
                val unit=performed.unit ?:return false
                if(performed.bandId!=null || unit!=plan.unit ||
                    !numericMass(actualLoad))return false
                val proof=EquipmentLoadDomain.inspect(stock,actualLoad,unit)
                return proof is LoadInspection.Confirmed && proof.exact &&
                    proof.basis==definition.expectedBasis
            }
        }
    }
}

sealed interface MeasuredJournalOperation {
    val eventId:String
    val expectedRevision:Long
    data class Record(
        override val eventId:String,
        override val expectedRevision:Long,
        val outcome:MeasuredSetOutcome,
        val actual:MeasuredExerciseActual?,
        val correctsEventId:String?=null
    ):MeasuredJournalOperation
    data class Finish(
        override val eventId:String,
        override val expectedRevision:Long
    ):MeasuredJournalOperation
}
data class MeasuredJournalReceipt(val revision:Long,val operation:MeasuredJournalOperation)
data class MeasuredExerciseJournal(
    val immutablePlan:MeasuredExerciseSetPlan,
    val revision:Long,
    val receipts:List<MeasuredJournalReceipt>,
    val effectiveRecord:MeasuredJournalReceipt?,
    val finishedByEventId:String?
) {
    val persisted:Boolean get()=false
    val mayChangeTrainingProgram:Boolean get()=false
}
sealed interface MeasuredJournalDecision {
    data class Proposed(val journal:MeasuredExerciseJournal,val replayed:Boolean=false):
        MeasuredJournalDecision
    data class Rejected(val reason:MeasurementRefusal):MeasuredJournalDecision
}

/**
 * Single-set correction-aware proposed journal. An Android store must add
 * authenticated ownership, serialized v1 format, checksums and atomic CAS
 * before this can issue sets, save athlete history or be called beta-ready.
 */
object MeasuredExerciseJournalDomain {
    private val eventId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    fun begin(plan:MeasuredExerciseSetPlan):MeasuredExerciseJournal? {
        val frozen=MeasuredWorkoutSnapshotDomain.plan(plan)
        return if(MeasuredExerciseDomain.inspect(frozen) is MeasurementPlanResult.Candidate)
            MeasuredExerciseJournal(frozen,0,emptyList(),null,null) else null
    }

    fun reduce(
        prior:MeasuredExerciseJournal,
        input:MeasuredJournalOperation
    ):MeasuredJournalDecision {
        val operation=MeasuredWorkoutSnapshotDomain.operation(input)
        if(!eventId.matches(operation.eventId))
            return MeasuredJournalDecision.Rejected(MeasurementRefusal.INVALID_ID_OR_SOURCE)
        val previous=prior.receipts.firstOrNull {
            it.operation.eventId==operation.eventId
        }
        if(previous!=null)return if(previous.operation==operation)
            MeasuredJournalDecision.Proposed(prior,true)
        else MeasuredJournalDecision.Rejected(MeasurementRefusal.EVENT_COLLISION)
        if(prior.revision!=operation.expectedRevision)
            return MeasuredJournalDecision.Rejected(MeasurementRefusal.STALE_REVISION)
        if(prior.finishedByEventId!=null)
            return MeasuredJournalDecision.Rejected(MeasurementRefusal.FINISHED)
        if(prior.receipts.size>=128)
            return MeasuredJournalDecision.Rejected(MeasurementRefusal.INVALID_ID_OR_SOURCE)
        when(operation) {
            is MeasuredJournalOperation.Record -> {
                if(!MeasuredExerciseDomain.validActual(
                        prior.immutablePlan,operation.outcome,operation.actual))
                    return MeasuredJournalDecision.Rejected(
                        MeasurementRefusal.WRONG_MEASUREMENT_OR_LOAD)
                val existing=prior.effectiveRecord
                if(existing==null && operation.correctsEventId!=null ||
                    existing!=null && operation.correctsEventId==null)
                    return MeasuredJournalDecision.Rejected(
                        if(existing==null)MeasurementRefusal.INVALID_CORRECTION
                        else MeasurementRefusal.ALREADY_RECORDED)
                if(existing!=null && operation.correctsEventId!=
                    existing.operation.eventId)
                    return MeasuredJournalDecision.Rejected(
                        MeasurementRefusal.INVALID_CORRECTION)
                val newReceipt=MeasuredJournalReceipt(prior.revision+1,operation)
                return MeasuredJournalDecision.Proposed(prior.copy(
                    revision=prior.revision+1,
                    receipts=Collections.unmodifiableList(prior.receipts+newReceipt),
                    effectiveRecord=newReceipt))
            }
            is MeasuredJournalOperation.Finish -> {
                if(prior.effectiveRecord==null)
                    return MeasuredJournalDecision.Rejected(MeasurementRefusal.INCOMPLETE)
                val receipt=MeasuredJournalReceipt(prior.revision+1,operation)
                return MeasuredJournalDecision.Proposed(prior.copy(
                    revision=prior.revision+1,
                    receipts=Collections.unmodifiableList(prior.receipts+receipt),
                    finishedByEventId=operation.eventId))
            }
        }
    }

    fun replay(
        plan:MeasuredExerciseSetPlan,
        receipts:List<MeasuredJournalReceipt>
    ):MeasuredExerciseJournal? {
        var current=begin(plan) ?:return null
        if(receipts.size>128)return null
        for(receipt in receipts) {
            val next=reduce(current,receipt.operation) as? MeasuredJournalDecision.Proposed
                ?:return null
            if(next.replayed || next.journal.revision!=receipt.revision)return null
            current=next.journal
        }
        return current
    }

    /** Verify cached actual/correction/Finish pointers against the one receipt stream. */
    fun verified(journal:MeasuredExerciseJournal):Boolean =
        replay(journal.immutablePlan,journal.receipts)==journal
}

/** Read-only, comparable actual results only; never grant PRs or change load. */
enum class MeasuredComparison { HARDER_OR_LONGER, SAME, MIXED_OR_LOWER, NOT_COMPARABLE }
object MeasuredExerciseComparisonDomain {
    fun compare(
        before:MeasuredExerciseJournal,
        after:MeasuredExerciseJournal
    ):MeasuredComparison {
        if(!MeasuredExerciseJournalDomain.verified(before) ||
            !MeasuredExerciseJournalDomain.verified(after))
            return MeasuredComparison.NOT_COMPARABLE
        val a=before.immutablePlan
        val b=after.immutablePlan
        val first=(before.effectiveRecord?.operation as? MeasuredJournalOperation.Record)
            ?.takeIf{it.outcome==MeasuredSetOutcome.COMPLETED}?.actual
        val second=(after.effectiveRecord?.operation as? MeasuredJournalOperation.Record)
            ?.takeIf{it.outcome==MeasuredSetOutcome.COMPLETED}?.actual
        if(before.finishedByEventId==null || after.finishedByEventId==null ||
            first==null || second==null ||
            a.exerciseId!=b.exerciseId || a.gymId!=b.gymId ||
            a.physicalSetupId!=b.physicalSetupId || a.bandId!=b.bandId ||
            a.sides!=b.sides || a.unit!=b.unit ||
            first.unit!=second.unit || first.bandId!=second.bandId ||
            first.physicalSetupId!=second.physicalSetupId)
            return MeasuredComparison.NOT_COMPARABLE
        val earlierLoad=first.actualExternalLoad
        val laterLoad=second.actualExternalLoad
        if((earlierLoad==null)!=(laterLoad==null) ||
            (earlierLoad!=null && (laterLoad==null ||
                earlierLoad.compareTo(laterLoad)!=0)))
            return MeasuredComparison.NOT_COMPARABLE
        val old=first.sides.associate{it.side to it.amount}
        val current=second.sides.associate{it.side to it.amount}
        if(old.keys!=current.keys)return MeasuredComparison.NOT_COMPARABLE
        val differences=current.map{(side,amount)->amount-old.getValue(side)}
        return when {
            differences.all{it==0} -> MeasuredComparison.SAME
            differences.all{it>=0} && differences.any{it>0} ->
                MeasuredComparison.HARDER_OR_LONGER
            else -> MeasuredComparison.MIXED_OR_LOWER
        }
    }
}
