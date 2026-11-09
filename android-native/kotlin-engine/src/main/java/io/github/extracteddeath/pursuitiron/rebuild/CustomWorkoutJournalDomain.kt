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

    /**
     * Explicit one-workout-only substitution of an UNSTARTED slot. Never
     * transfers performed work, targets, progression or program identity.
     * A durable authority must independently verify replacement catalog,
     * physical gym stock and newly authored sets before recording this event.
     */
    data class Swap(
        override val eventId:String,
        override val expectedRevision:Long,
        val previousSlotId:String,
        val replacement:CustomWorkoutSlotSnapshot
    ):CustomWorkoutJournalOperation

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
    val finishedByEventId: String?,
    /** Never changes: exact originally prepared/consent-approved workout. */
    val originalSnapshot:CustomWorkoutDaySnapshot=snapshot
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
    INVALID_ACTUAL, INCOMPLETE_WORKOUT, TOO_MANY_EVENTS,
    SWAP_ALREADY_STARTED, SWAP_PROTECTED, SWAP_IDENTITY_CONFLICT,
    SWAP_INVALID_TARGET
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

    /** Ordered presentation of the next required observation. An authored
     * follow-up becomes required only after its main is actually completed. */
    fun nextUnresolvedVisit(journal:CustomWorkoutJournalDraft):CustomWorkoutVisitKey? {
        if(journal.finishedByEventId!=null)return null
        for(slot in journal.snapshot.slots)for(set in slot.sets) {
            val main=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN)
            val recorded=journal.effectiveVisits[main]?.operation
                as? CustomWorkoutJournalOperation.Record ?:return main
            if(recorded.outcome==KotlinJournalSetOutcome.COMPLETED &&
                set.prescription.amplifier!=null) {
                val follow=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.FOLLOW_UP)
                if(follow !in journal.effectiveVisits)return follow
            }
        }
        return null
    }

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

    /** Shared physical/effort validation for active and saved custom observations. */
    internal fun actualValid(
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
        var nextSnapshot=prior.snapshot
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
            is CustomWorkoutJournalOperation.Swap -> {
                val previous=prior.snapshot.slots.firstOrNull {
                    it.slotId==operation.previousSlotId
                } ?:return CustomWorkoutJournalDecision.Rejected(
                    CustomWorkoutJournalRefusal.UNKNOWN_VISIT)
                val next=operation.replacement
                val previousKeys=previous.sets.map {it.setKey}.toSet()
                if(prior.receipts.any {r->
                    (r.operation as? CustomWorkoutJournalOperation.Record)
                        ?.visit?.setKey in previousKeys
                })return CustomWorkoutJournalDecision.Rejected(
                    CustomWorkoutJournalRefusal.SWAP_ALREADY_STARTED)
                if(previous.supersetGroup!=null || next.supersetGroup!=null)
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_PROTECTED)
                val usedSlotIds=prior.originalSnapshot.slots.map {it.slotId}+
                    prior.receipts.mapNotNull {
                        (it.operation as? CustomWorkoutJournalOperation.Swap)
                            ?.replacement?.slotId
                    }
                val usedSetKeys=prior.originalSnapshot.slots.flatMap{
                    it.sets.map {set->set.setKey}
                }+prior.receipts.flatMap{
                    (it.operation as? CustomWorkoutJournalOperation.Swap)
                        ?.replacement?.sets?.map {set->set.setKey}.orEmpty()
                }
                if(!id.matches(next.slotId) ||
                    !id.matches(next.exerciseId) ||
                    !id.matches(next.familyId) ||
                    !id.matches(next.variantId) ||
                    next.slotId in usedSlotIds ||
                    next.exerciseId==previous.exerciseId ||
                    next.sets.any {it.setKey in usedSetKeys})
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_IDENTITY_CONFLICT)
                val count=next.sets.size
                if(count !in 1..20 || next.coachNote.length>1000 ||
                    !next.pinned || next.sets.any {set->
                        set.slotId!=next.slotId ||
                        set.ordinalInSlot !in 1..count ||
                        set.setKey!=
                            "${prior.snapshot.stamp.cycleId}:w${prior.snapshot.absoluteWeek}:d${prior.snapshot.weekday}:${next.slotId}:s${set.ordinalInSlot}" ||
                        set.prescription.reps.minimum !in 1..100 ||
                        set.prescription.reps.maximum !in set.prescription.reps.minimum..100 ||
                        set.prescription.targetRir?.let{it !in 0..10}==true ||
                        set.prescription.activeSeconds !in 1..3600 ||
                        set.prescription.restSeconds !in 0..3600 ||
                        (set.prescription.type==DraftSetType.WARMUP &&
                            set.prescription.amplifier!=null)
                    } || next.sets.map{it.ordinalInSlot}.toSet().size!=count)
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_INVALID_TARGET)
                val changed=prior.snapshot.slots.map {slot->
                    if(slot.slotId==previous.slotId)next else slot
                }
                if(changed.sumOf{it.sets.size}>256)
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_INVALID_TARGET)
                val duration=changed.sumOf { slot ->
                    slot.sets.sumOf{set ->
                        set.prescription.activeSeconds.toLong()+
                        set.prescription.restSeconds.toLong()+
                        (set.prescription.amplifier?.followUpRestSeconds ?:0)
                    }
                }
                if(duration !in 1L..10_000_000L)
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_INVALID_TARGET)
                nextSnapshot=prior.snapshot.copy(
                    slots=Collections.unmodifiableList(changed),
                    knownSetAndRestSeconds=duration)
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
            snapshot=nextSnapshot,
            revision=prior.revision+1,
            receipts=Collections.unmodifiableList(prior.receipts+receipt),
            effectiveVisits=Collections.unmodifiableMap(nextEffective),
            finishedByEventId=finish)
        return CustomWorkoutJournalDecision.Proposed(updated,false)
    }
}
