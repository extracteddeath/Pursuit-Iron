package io.github.extracteddeath.pursuitiron.rebuild

/** Display-only rest from an issued target and an acknowledged journal receipt.
 * No clock, training calculation, set write or persistence authority lives here.
 * Callers must first recover the journal through its native owner. */
data class KotlinWorkoutRestCue(
    val eventId:String,
    /** Zero ends a preceding countdown for an explicitly immediate follow-up. */
    val seconds:Int,
    val exerciseIndex:Int
)

data class KotlinWorkoutRestPresentation(
    val latestReceiptEventId:String?,
    val manualRestSeconds:Int,
    val automaticCue:KotlinWorkoutRestCue?,
    val hasRemainingWork:Boolean,
    val manualIsSuperset:Boolean=false
)

object KotlinWorkoutRestDomain {
    private fun seconds(value:Int?)=value?.takeIf{it in 1..3600} ?:0

    fun inspect(
        journal:KotlinWorkoutJournalState,
        selectedSetKey:String?
    ):KotlinWorkoutRestPresentation {
        // Note edits must not reset an active rest countdown. Use the most
        // recent non-note receipt, so the cue/event ID remains unchanged
        // after a session-note edit and corrections still suppress a cue.
        val receipt=journal.receipts.lastOrNull {
            it.operation !is KotlinJournalOperation.SessionNote
        }
        val remaining=journal.finishedByEventId==null && journal.unresolvedSetCount>0
        val selected=journal.snapshot.targets.firstOrNull{it.setKey==selectedSetKey}
        val operation=receipt?.operation as? KotlinJournalOperation.RecordSet
        val target=journal.snapshot.targets.firstOrNull{it.setKey==operation?.setKey}
        val cue=if(remaining && operation!=null && target!=null &&
            operation.outcome==KotlinJournalSetOutcome.COMPLETED &&
            operation.correctsEventId==null && // The last NON-NOTE receipt is still the most recent meaningful
            // workout event, even when note receipts followed it.
            receipt.appliedRevision<=journal.revision &&
            journal.effectiveSets[operation.setKey]==receipt && target.restSeconds in 0..3600) {
            val index=KotlinWorkoutPresentationDomain.occurrenceIndex(
                journal.snapshot.targets,target.setKey)
            KotlinWorkoutRestCue(operation.eventId,target.restSeconds,index)
        } else null
        return KotlinWorkoutRestPresentation(receipt?.operation?.eventId,
            if(remaining)seconds(selected?.restSeconds) else 0,cue,remaining)
    }

    fun inspect(
        journal:CustomWorkoutJournalDraft,
        selectedVisit:CustomWorkoutVisitKey?
    ):KotlinWorkoutRestPresentation {
        // Note edits must not reset an active rest countdown. Use the most
        // recent non-note receipt, so the cue/event ID remains unchanged
        // after a session-note edit and corrections still suppress a cue.
        val receipt=journal.receipts.lastOrNull {
            it.operation !is CustomWorkoutJournalOperation.SessionNote
        }
        val remaining=journal.finishedByEventId==null && journal.unresolvedVisitCount>0
        val selectedSlot=journal.snapshot.slots.firstOrNull {slot->
            slot.sets.any{it.setKey==selectedVisit?.setKey}
        }
        val selected=selectedSlot?.sets?.firstOrNull{it.setKey==selectedVisit?.setKey}
        val manual=if(selectedVisit?.part==CustomWorkoutVisitPart.FOLLOW_UP)
            selected?.prescription?.amplifier?.followUpRestSeconds
        else selected?.prescription?.restSeconds
        val operation=receipt?.operation as? CustomWorkoutJournalOperation.Record
        val index=journal.snapshot.slots.indexOfFirst {slot->
            slot.sets.any{it.setKey==operation?.visit?.setKey}
        }
        val slot=journal.snapshot.slots.getOrNull(index)
        val target=slot?.sets?.firstOrNull{it.setKey==operation?.visit?.setKey}
        val execution=CustomWorkoutExecutionDomain.orderedSets(journal.snapshot)
        val round=execution.firstOrNull{it.set.setKey==target?.setKey}?.round
        // A completed main precedes the amplifier's SHORT intra-set rest.
        // Its completed follow-up precedes the parent's ordinary between-set
        // rest. Never substitute one duration for the other or add them.
        val beforeFollowUp=operation!=null && target!=null &&
            CustomWorkoutExecutionDomain.unresolvedVisit(journal,target)
                ?.let { it.part==CustomWorkoutVisitPart.FOLLOW_UP &&
                    it.setKey==operation.visit.setKey } ==true
        val after=if(beforeFollowUp)
            target?.prescription?.amplifier?.followUpRestSeconds
        else if(round!=null && !CustomWorkoutExecutionDomain.roundIsResolved(journal,round))0
        else target?.prescription?.restSeconds
        val cue=if(remaining && operation!=null && target!=null &&
            operation.outcome==KotlinJournalSetOutcome.COMPLETED &&
            operation.correctsEventId==null && // The last NON-NOTE receipt is still the most recent meaningful
            // workout event, even when note receipts followed it.
            receipt.appliedRevision<=journal.revision &&
            journal.effectiveVisits[operation.visit]==receipt &&
            after!=null && after in 0..3600 &&
            // Invalid group metadata retains the previous manual-only policy.
            (slot.supersetGroup==null || round!=null ||
                target.prescription.type==DraftSetType.WARMUP)) {
            KotlinWorkoutRestCue(operation.eventId,checkNotNull(after),index)
        } else null
        // A zero cue clears the preceding pause between movements. Only the
        // completed visit that resolves the round starts its own authored
        // between-set rest. Skips/failures/corrections remain non-work cues.
        return KotlinWorkoutRestPresentation(receipt?.operation?.eventId,
            if(remaining)seconds(manual) else 0,cue,remaining,
            execution.firstOrNull{it.set.setKey==selected?.setKey}?.round!=null &&
                selectedVisit?.part!=CustomWorkoutVisitPart.FOLLOW_UP)
    }
}
