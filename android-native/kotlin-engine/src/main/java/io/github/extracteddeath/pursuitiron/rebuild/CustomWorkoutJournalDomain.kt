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
    val part: CustomWorkoutVisitPart,
    /** 1 is byte-identical to legacy follow-ups. Used only for FOLLOW_UP. */
    val stage: Int = 1
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

    /**
     * Explicit rollback of exactly one durable swap receipt. It restores
     * the historically approved slot, never recreating targets from UI.
     * Only an untouched, still-active replacement can be undone.
     */
    data class UndoSwap(
        override val eventId:String,
        override val expectedRevision:Long,
        val swapEventId:String
    ):CustomWorkoutJournalOperation

    /** Athlete-written text. Empty text explicitly clears the session note.
     * Every edit is a revisioned, owner-fenced receipt; no draft is factual. */
    /** Explicit single-workout edit; no permanent cycle write. */
    data class AdjustRemaining(
        override val eventId:String,override val expectedRevision:Long,
        val request:MidSessionWorkingEdit
    ):CustomWorkoutJournalOperation
    data class UndoAdjustment(
        override val eventId:String,override val expectedRevision:Long,
        val adjustmentEventId:String
    ):CustomWorkoutJournalOperation

    data class SessionNote(
        override val eventId:String,
        override val expectedRevision:Long,
        val text:String
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
    val originalSnapshot:CustomWorkoutDaySnapshot=snapshot,
    val lastAdjustment:MidSessionUndo<CustomWorkoutDaySnapshot>?=null
) {
    /** A note never leaks into the next workout's issued targets or coach. */
    val sessionNote:String get()=receipts.asReversed().firstNotNullOfOrNull{
        (it.operation as? CustomWorkoutJournalOperation.SessionNote)?.text
    }.orEmpty()
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
                CustomWorkoutExecutionDomain.followUpVisits(this,set).any{
                    it !in effectiveVisits
                })unresolved++
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
    SWAP_INVALID_TARGET, SWAP_UNDO_UNAVAILABLE, INVALID_SESSION_NOTE, INVALID_ADJUSTMENT
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
    // At most 256 main sets, each with up to five separately logged stages.
    const val MAX_RECEIPTS = KotlinWorkoutJournalDomain.EDIT_LIMIT + 256 * 6 + 1
    const val MAX_SESSION_NOTE_CHARS=2000
    fun validSessionNote(text:String):Boolean =
        text.length<=MAX_SESSION_NOTE_CHARS && text.none {
            it=='\u0000' || (it.code<32 && it!='\n' && it!='\r' && it!='\t')
        }

    /** One execution policy also owns Focus order and superset round rest. */
    fun nextUnresolvedVisit(journal:CustomWorkoutJournalDraft):CustomWorkoutVisitKey? =
        CustomWorkoutExecutionDomain.nextUnresolvedVisit(journal)

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
    /** Same policy as generated and saved-history observations, not a fork. */
    internal fun actualValid(
        target:DraftSetLoad,
        outcome:KotlinJournalSetOutcome,
        performed:KotlinPerformedSet?
    ):Boolean=KotlinSetNumberAuthorityDomain.valid(target,outcome,performed)

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
        val firstObservation = operation is CustomWorkoutJournalOperation.Record &&
            operation.visit !in prior.effectiveVisits && operation.correctsEventId == null
        if(prior.receipts.size>=MAX_RECEIPTS ||
            prior.receipts.size>=KotlinWorkoutJournalDomain.EDIT_LIMIT && !firstObservation &&
                operation !is CustomWorkoutJournalOperation.Finish)
            return CustomWorkoutJournalDecision.Rejected(
                CustomWorkoutJournalRefusal.TOO_MANY_EVENTS)

        val nextEffective=LinkedHashMap(prior.effectiveVisits)
        var nextSnapshot=prior.snapshot
        var undo:MidSessionUndo<CustomWorkoutDaySnapshot>?=null
        val finish=when(operation) {
            is CustomWorkoutJournalOperation.AdjustRemaining -> {
                nextSnapshot=MidSessionWorkingEditDomain.customSnapshot(prior,operation.request)
                    ?:return CustomWorkoutJournalDecision.Rejected(CustomWorkoutJournalRefusal.INVALID_ADJUSTMENT)
                undo=MidSessionUndo(operation.eventId,prior.snapshot)
                null
            }
            is CustomWorkoutJournalOperation.UndoAdjustment -> {
                val last=prior.lastAdjustment?.takeIf{it.eventId==operation.adjustmentEventId}
                    ?:return CustomWorkoutJournalDecision.Rejected(CustomWorkoutJournalRefusal.INVALID_ADJUSTMENT)
                nextSnapshot=last.before
                null
            }
            is CustomWorkoutJournalOperation.Record -> {
                val target=prior.snapshot.slots.asSequence()
                    .flatMap{it.sets.asSequence()}
                    .firstOrNull{it.setKey==operation.visit.setKey}
                    ?:return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.UNKNOWN_VISIT)
                if(operation.visit.stage !in 1..5 ||
                    operation.visit.part==CustomWorkoutVisitPart.MAIN &&
                        operation.visit.stage!=1)
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.UNKNOWN_VISIT)
                val mainKey=CustomWorkoutVisitKey(
                    operation.visit.setKey,CustomWorkoutVisitPart.MAIN)
                val followerKey=CustomWorkoutVisitKey(
                    operation.visit.setKey,CustomWorkoutVisitPart.FOLLOW_UP)
                val main=(nextEffective[mainKey]?.operation
                    as? CustomWorkoutJournalOperation.Record)
                val plan=when(operation.visit.part) {
                    CustomWorkoutVisitPart.MAIN -> {
                        // A completed dependent drop, mini-set, pause,
                        // cluster or partial stays anchored to the ACTUAL
                        // activation's physical load and station. Comparing
                        // numeric BigDecimals avoids 20 vs 20.000 drift.
                        // Skipping the main remains possible only AFTER
                        // every dependent follow-up has been skipped.
                        if(main!=null &&
                            operation.outcome==KotlinJournalSetOutcome.COMPLETED &&
                            nextEffective.any { (key,receipt)->
                                key.setKey==operation.visit.setKey &&
                                    key.part==CustomWorkoutVisitPart.FOLLOW_UP &&
                                    (receipt.operation as?
                                        CustomWorkoutJournalOperation.Record)
                                        ?.outcome!=KotlinJournalSetOutcome.SKIPPED
                            } &&
                            !CustomWorkoutExecutionDomain.sameActualLoadAndStation(
                                main.performed,operation.performed))
                            return CustomWorkoutJournalDecision.Rejected(
                                CustomWorkoutJournalRefusal.INVALID_CORRECTION)
                        if(operation.outcome!=KotlinJournalSetOutcome.COMPLETED &&
                            nextEffective.any{ (key,receipt)->
                                key.setKey==operation.visit.setKey &&
                                key.part==CustomWorkoutVisitPart.FOLLOW_UP &&
                                (receipt.operation as? CustomWorkoutJournalOperation.Record)
                                    ?.outcome!=KotlinJournalSetOutcome.SKIPPED
                            })
                            return CustomWorkoutJournalDecision.Rejected(
                                CustomWorkoutJournalRefusal.INVALID_CORRECTION)
                        val recorded = nextEffective.keys.filter {
                            it.setKey == target.setKey && it.part == CustomWorkoutVisitPart.FOLLOW_UP
                        }
                        // Even a skipped drop must remain a reachable visit
                        // while the corrected activation is completed.
                        if (operation.outcome == KotlinJournalSetOutcome.COMPLETED &&
                            recorded.isNotEmpty() &&
                            !CustomWorkoutExecutionDomain.preservesRecordedFollowUps(
                                target, operation, recorded, finished = false) { key ->
                                nextEffective[key]?.operation as? CustomWorkoutJournalOperation.Record
                            }) return CustomWorkoutJournalDecision.Rejected(
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
                        if(operation.visit.stage>amp.followUpRounds)
                            return CustomWorkoutJournalDecision.Rejected(
                                CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED)
                        val oldStage=nextEffective[operation.visit]
                        if(oldStage==null &&
                            operation.visit !in CustomWorkoutExecutionDomain.followUpVisits(
                                prior,target))
                            return CustomWorkoutJournalDecision.Rejected(
                                CustomWorkoutJournalRefusal.FOLLOW_UP_NOT_AUTHORED)
                        if (oldStage != null) {
                            val recorded = nextEffective.keys.filter {
                                it.setKey == target.setKey && it.part == CustomWorkoutVisitPart.FOLLOW_UP
                            }
                            if (!CustomWorkoutExecutionDomain.preservesRecordedFollowUps(
                                    target, operation, recorded, finished = false) { key ->
                                    nextEffective[key]?.operation as? CustomWorkoutJournalOperation.Record
                                }) return CustomWorkoutJournalDecision.Rejected(
                                    CustomWorkoutJournalRefusal.INVALID_CORRECTION)
                        }
                        // Partial-range extension cannot be performed on a
                        // different physical station or load. This is a
                        // separate factual partial count, never a full-ROM
                        // regular working set or an invented load reduction.
                        if(AdvancedAmplifierProtocolDomain.requiresActivationLoad(
                                amp.kind) &&
                            operation.outcome!=KotlinJournalSetOutcome.SKIPPED &&
                            !AdvancedAmplifierProtocolDomain.validSameLoadFollowUp(
                                amp,target.prescription.load,main.performed,
                                operation.performed,operation.outcome))
                            return CustomWorkoutJournalDecision.Rejected(
                                CustomWorkoutJournalRefusal.INVALID_ACTUAL)
                        // The issued lower target never proves that even
                        // an ATTEMPTED drop was physically lower. This gate
                        // applies to failed and completed stages identically
                        // and preserves exact station and last-stage identity.
                        if(amp.kind==CustomAmplifierKind.DROP_SET &&
                            operation.outcome!=KotlinJournalSetOutcome.SKIPPED) {
                            val predecessor=if(operation.visit.stage==1)main
                                else (nextEffective[CustomWorkoutVisitKey(
                                    operation.visit.setKey,
                                    CustomWorkoutVisitPart.FOLLOW_UP,
                                    operation.visit.stage-1)]?.operation as?
                                        CustomWorkoutJournalOperation.Record)
                            if(predecessor?.outcome!=KotlinJournalSetOutcome.COMPLETED ||
                                !AdvancedAmplifierProtocolDomain.validDropStageObservation(
                                    amp,target.prescription.load,main.performed,
                                    if(operation.visit.stage==1)null
                                        else predecessor.performed,
                                    operation.performed,operation.visit.stage,
                                    operation.outcome))
                                return CustomWorkoutJournalDecision.Rejected(
                                    CustomWorkoutJournalRefusal.INVALID_ACTUAL)
                        }
                        AdvancedAmplifierProtocolDomain.followUpLoadForStage(
                            amp,target.prescription.load,operation.visit.stage)
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
                if(prior.receipts.any{
                    (it.operation as? CustomWorkoutJournalOperation.AdjustRemaining)?.request?.occurrenceId==operation.previousSlotId
                })return CustomWorkoutJournalDecision.Rejected(CustomWorkoutJournalRefusal.INVALID_ADJUSTMENT)
                val previous=prior.snapshot.slots.firstOrNull {
                    it.slotId==operation.previousSlotId
                } ?:return CustomWorkoutJournalDecision.Rejected(
                    CustomWorkoutJournalRefusal.UNKNOWN_VISIT)
                val next=operation.replacement
                val previousKeys=previous.sets.map {it.setKey}.toSet()
                if(prior.receipts.any {r->
                    (r.operation as? CustomWorkoutJournalOperation.Record)
                        ?.visit?.setKey?.let{it in previousKeys}==true
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
                        !KotlinSetNumberAuthorityDomain.validIssuedLoad(
                            set.prescription.load) ||
                        set.prescription.reps.minimum !in 1..100 ||
                        set.prescription.reps.maximum !in set.prescription.reps.minimum..100 ||
                        set.prescription.targetRir?.let{it !in 0..10}==true ||
                        set.prescription.activeSeconds !in 1..3600 ||
                        set.prescription.restSeconds !in 0..3600 ||
                        (set.prescription.type==DraftSetType.WARMUP &&
                            set.prescription.amplifier!=null) ||
                        (set.prescription.amplifier?.let{amp->
                            !AdvancedAmplifierProtocolDomain.validStagedFollowUps(amp) ||
                                amp.kind==CustomAmplifierKind.DROP_SET &&
                                !AdvancedAmplifierProtocolDomain.validDropSequence(
                                    set.prescription.load,amp)
                        }==true)
                    } || next.sets.map{it.ordinalInSlot}.toSet().size!=count)
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_INVALID_TARGET)
                val changed=prior.snapshot.slots.map {slot->
                    if(slot.slotId==previous.slotId)next else slot
                }
                if(changed.sumOf{it.sets.size}>256)
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_INVALID_TARGET)
                val duration=CustomWorkoutTimingDomain.totalSeconds(
                    changed.flatMap{slot->slot.sets.map{it.prescription}})
                    ?:return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_INVALID_TARGET)
                nextSnapshot=prior.snapshot.copy(
                    slots=Collections.unmodifiableList(changed),
                    knownSetAndRestSeconds=duration)
                null
            }
            is CustomWorkoutJournalOperation.UndoSwap -> {
                val swappedReceipt=prior.receipts.firstOrNull {receipt->
                    receipt.operation.eventId==operation.swapEventId
                }
                val swapped=swappedReceipt?.operation as?
                    CustomWorkoutJournalOperation.Swap
                    ?:return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_UNDO_UNAVAILABLE)
                val replacement=swapped.replacement
                val replacementIndex=prior.snapshot.slots.indexOfFirst {
                    it.slotId==replacement.slotId &&
                        it==replacement
                }
                if(replacementIndex<0 || prior.receipts.any {receipt->
                    (receipt.operation as? CustomWorkoutJournalOperation.UndoSwap)
                        ?.swapEventId==operation.swapEventId
                })return CustomWorkoutJournalDecision.Rejected(
                    CustomWorkoutJournalRefusal.SWAP_UNDO_UNAVAILABLE)
                val discardedKeys=replacement.sets.map{it.setKey}.toSet()
                if(prior.receipts.any {receipt->
                    (receipt.operation as? CustomWorkoutJournalOperation.Record)
                        ?.visit?.setKey?.let{it in discardedKeys}==true
                })return CustomWorkoutJournalDecision.Rejected(
                    CustomWorkoutJournalRefusal.SWAP_ALREADY_STARTED)
                // A previous exercise can be the original approved slot or
                // the exact replacement from an earlier swap. Never derive
                // it from the current gym, editor or a mutable program.
                val previous=prior.originalSnapshot.slots.firstOrNull{
                    it.slotId==swapped.previousSlotId
                } ?: prior.receipts.takeWhile {
                    it.operation.eventId!=operation.swapEventId
                }.mapNotNull{
                    (it.operation as? CustomWorkoutJournalOperation.Swap)
                        ?.replacement
                }.firstOrNull{it.slotId==swapped.previousSlotId}
                if(previous==null || prior.snapshot.slots.any {
                    it.slotId==previous.slotId
                })return CustomWorkoutJournalDecision.Rejected(
                    CustomWorkoutJournalRefusal.SWAP_UNDO_UNAVAILABLE)
                val changed=prior.snapshot.slots.toMutableList().also{
                    it[replacementIndex]=previous
                }
                val duration=CustomWorkoutTimingDomain.totalSeconds(
                    changed.flatMap{slot->slot.sets.map{it.prescription}})
                    ?:return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.SWAP_INVALID_TARGET)
                nextSnapshot=prior.snapshot.copy(
                    slots=Collections.unmodifiableList(changed),
                    knownSetAndRestSeconds=duration)
                null
            }
            is CustomWorkoutJournalOperation.SessionNote -> {
                if(!validSessionNote(operation.text))
                    return CustomWorkoutJournalDecision.Rejected(
                        CustomWorkoutJournalRefusal.INVALID_SESSION_NOTE)
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
            finishedByEventId=finish,lastAdjustment=undo)
        return CustomWorkoutJournalDecision.Proposed(updated,false)
    }
}
