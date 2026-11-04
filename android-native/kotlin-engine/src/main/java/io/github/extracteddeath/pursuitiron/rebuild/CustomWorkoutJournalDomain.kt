package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Pure reducer for the complete, ATHLETE-AUTHORED custom workout.
 *
 * Main work and advanced-set follow-up are independent factual observations:
 * a prescribed DROP_SET/MYO_REPS/REST_PAUSE/CLUSTER follow-up is NEVER
 * automatically counted as done because the preceding set was completed.
 *
 * This domain proposes state only. A future native issuance and journal
 * authority must pin the exact, verified CustomWorkoutDaySnapshot to an
 * authenticated owner and commit every receipt with crash-safe CAS.
 * Never treat a prepared workout or this reducer's returned state as durable.
 */
enum class CustomWorkoutVisitPart { MAIN, FOLLOW_UP }

data class CustomWorkoutVisitKey(
    val setKey: String,
    val part: CustomWorkoutVisitPart
)

sealed interface CustomWorkoutJournalOperation {
    val eventId: String
    val expectedRevision: Long

    data class Record(
        override val eventId: String,
        override val expectedRevision: Long,
        val visit: CustomWorkoutVisitKey,
        val outcome: KotlinJournalSetOutcome,
        val performed: KotlinPerformedSet?,
        /** Only a CURRENT receipt's event ID authorizes a correction. */
        val correctsEventId: String? = null
    ): CustomWorkoutJournalOperation

    data class Finish(
        override val eventId: String,
        override val expectedRevision: Long
    ): CustomWorkoutJournalOperation
}

data class CustomWorkoutJournalReceipt(
    val appliedRevision: Long,
    val operation: CustomWorkoutJournalOperation
)

data class CustomWorkoutJournalDraft(
    val workoutId: String,
    val snapshot: CustomWorkoutDaySnapshot,
    val preparedConsentFingerprint: String,
    val revision: Long,
    val receipts: List<CustomWorkoutJournalReceipt>,
    val effectiveVisits: Map<CustomWorkoutVisitKey, CustomWorkoutJournalReceipt>,
    val finishedByEventId: String?
) {
    /** A skipped or failed main set makes follow-up non-applicable, never done. */
    val unresolvedVisitCount: Int get() {
        var unresolved=0
        for (slot in snapshot.slots) for (set in slot.sets) {
            val main=effectiveVisits[CustomWorkoutVisitKey(
                set.setKey,CustomWorkoutVisitPart.MAIN)]
            if(main==null) {
                unresolved++
                continue
            }
            val done=(main.operation as? CustomWorkoutJournalOperation.Record)
                ?.outcome==KotlinJournalSetOutcome.COMPLETED
            if(done && set.prescription.amplifier!=null &&
                CustomWorkoutVisitKey(set.setKey,
                    CustomWorkoutVisitPart.FOLLOW_UP) !in effectiveVisits)
                unresolved++
        }
        return unresolved
    }
    val canProposeFinish: Boolean get() =
        finishedByEventId==null && unresolvedVisitCount==0
    val persisted: Boolean get()=false
    val mayWriteHistory: Boolean get()=false
}

enum class CustomWorkoutJournalRefusal {
    INVALID_WORKOUT, INVALID_OPERATION, STALE_REVISION, FINISHED,
    EVENT_ID_COLLISION, UNKNOWN_VISIT, FOLLOW_UP_NOT_AUTHORED,
    MAIN_NOT_COMPLETED, ALREADY_RECORDED, INVALID_CORRECTION,
    INVALID_ACTUAL, INCOMPLETE_WORKOUT, TOO_MANY_EVENTS
}

sealed interface CustomWorkoutJournalDecision {
    data class Proposed(
        val draft: CustomWorkoutJournalDraft,
        val replayed: Boolean
    ):CustomWorkoutJournalDecision
    data class Rejected(val reason: CustomWorkoutJournalRefusal):
        CustomWorkoutJournalDecision
}

object CustomWorkoutJournalDomain {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private val sha256=Regex("^[a-f0-9]{64}$")
    private val maxWeight=BigDecimal("100000")
    private const val MAX_EVENTS=2048

    fun begin(
        workoutId:String,
        snapshot:CustomWorkoutDaySnapshot,
        preparedConsentFingerprint:String
    ):CustomWorkoutJournalDraft {
        require(id.matches(workoutId) && sha256.matches(preparedConsentFingerprint))
        val keys=snapshot.slots.flatMap{it.sets}.map{it.setKey}
        require(keys.isNotEmpty() && keys.size<=256 &&
            keys.distinct().size==keys.size)
        require(snapshot.stamp.cycleRevision>0 &&
            snapshot.stamp.gymCatalogSourceRevision>0)
        return CustomWorkoutJournalDraft(
            workoutId,snapshot,preparedConsentFingerprint,0L,
            emptyList(),emptyMap(),null)
    }

    private fun actualValid(
        target:DraftSetLoad,
        outcome:KotlinJournalSetOutcome,
        performed:KotlinPerformedSet?
    ):Boolean {
        if(outcome==KotlinJournalSetOutcome.SKIPPED)return performed==null
        val actual=performed ?:return false
        if(actual.actualRir!=null && actual.actualRir !in 0..10)return false
        if(outcome==KotlinJournalSetOutcome.COMPLETED &&
            actual.reps !in 1..100)return false
        if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT &&
            (actual.reps!=0 || actual.actualRir!=null))return false
        return when(target) {
            DraftSetLoad.ExplicitBodyweight ->
                actual.load==null && actual.unit==null
            is DraftSetLoad.Verified ->
                actual.load!=null && actual.unit==target.unit &&
                    actual.load>=BigDecimal.ZERO &&
                    actual.load<=maxWeight &&
                    actual.load.stripTrailingZeros().scale()<=3
        }
    }

    fun reduce(
        prior:CustomWorkoutJournalDraft,
        operation:CustomWorkoutJournalOperation
    ):CustomWorkoutJournalDecision {
        if(!id.matches(operation.eventId) || prior.revision<0L)
            return CustomWorkoutJournalDecision.Rejected(
                CustomWorkoutJournalRefusal.INVALID_OPERATION)
        // An exact retry wins even if a client has an old journal revision.
        val earlier=prior.receipts.firstOrNull{
            it.operation.eventId==operation.eventId
        }
        if(earlier!=null)
            return if(earlier.operation==operation)
                CustomWorkoutJournalDecision.Proposed(prior,true)
            else CustomWorkoutJournalDecision.Rejected(
                CustomWorkoutJournalRefusal.EVENT_ID_COLLISION)
        if(operation.expectedRevision!=prior.revision)
            return CustomWorkoutJournalDecision.Rejected(
                CustomWorkoutJournalRefusal.STALE_REVISION)
        if(prior.finishedByEventId!=null)
            return CustomWorkoutJournalDecision.Rejected(
                CustomWorkoutJournalRefusal.FINISHED)
        if(prior.receipts.size>=MAX_EVENTS)
            return CustomWorkoutJournalDecision.Rejected(
                CustomWorkoutJournalRefusal.TOO_MANY_EVENTS)

        val nextEffective=LinkedHashMap(prior.effectiveVisits)
        val finish=when(operation) {
            is CustomWorkoutJournalOperation.Record -> {
                val target=prior.snapshot.slots.asSequence()
                    .flatMap{it.sets.asSequence()}
                    .firstOrNull{it.setKey==operation.visit.setKey}
                    ?:return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.UNKNOWN_VISIT)
                val mainKey=CustomWorkoutVisitKey(
                    operation.visit.setKey,CustomWorkoutVisitPart.MAIN)
                val followerKey=CustomWorkoutVisitKey(
                    operation.visit.setKey,CustomWorkoutVisitPart.FOLLOW_UP)
                val main=(nextEffective[mainKey]?.operation
                    as? CustomWorkoutJournalOperation.Record)
                val plan=when(operation.visit.part) {
                    CustomWorkoutVisitPart.MAIN -> {
                        if(operation.outcome!=KotlinJournalSetOutcome.COMPLETED &&
                            (nextEffective[followerKey]?.operation
                                as? CustomWorkoutJournalOperation.Record)
                                ?.outcome!=null &&
                            (nextEffective[followerKey]?.operation
                                as CustomWorkoutJournalOperation.Record)
                                .outcome!=KotlinJournalSetOutcome.SKIPPED)
                            return CustomWorkoutJournalDecision.Rejected(
                                CustomWorkoutJournalRefusal.INVALID_CORRECTION)
                        target.prescription.load
                    }
                    CustomWorkoutVisitPart.FOLLOW_UP -> {
                        val amp=target.prescription.amplifier
                            ?:return CustomWorkoutJournalDecision.Rejected(
                                CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED)
                        if(main?.outcome!=KotlinJournalSetOutcome.COMPLETED)
                            return CustomWorkoutJournalDecision.Rejected(
                                CustomWorkoutJournalRefusal.MAIN_NOT_COMPLETED)
                        amp.followUpLoad ?:target.prescription.load
                    }
                }
                if(!actualValid(plan,operation.outcome,operation.performed))
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.INVALID_ACTUAL)
                val old=nextEffective[operation.visit]
                if(old==null && operation.correctsEventId!=null ||
                    old!=null && operation.correctsEventId==null)
                    return CustomWorkoutJournalDecision.Rejected(
                        if(old==null)CustomWorkoutJournalRefusal.INVALID_CORRECTION
                        else CustomWorkoutJournalRefusal.ALREADY_RECORDED)
                if(old!=null &&
                    old.operation.eventId!=operation.correctsEventId)
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.INVALID_CORRECTION)
                null
            }
            is CustomWorkoutJournalOperation.Finish -> {
                if(prior.unresolvedVisitCount!=0)
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.INCOMPLETE_WORKOUT)
                operation.eventId
            }
        }
        val receipt=CustomWorkoutJournalReceipt(prior.revision+1,operation)
        if(operation is CustomWorkoutJournalOperation.Record)
            nextEffective[operation.visit]=receipt
        val updated=prior.copy(
            revision=prior.revision+1,
            receipts=Collections.unmodifiableList(prior.receipts+receipt),
            effectiveVisits=Collections.unmodifiableMap(nextEffective),
            finishedByEventId=finish)
        return CustomWorkoutJournalDecision.Proposed(updated,false)
    }
}
