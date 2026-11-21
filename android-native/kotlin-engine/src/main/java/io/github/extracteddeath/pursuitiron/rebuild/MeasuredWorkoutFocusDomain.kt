package io.github.extracteddeath.pursuitiron.rebuild

/**
 * A source-only view over an ALREADY issued measured workout journal.
 * Never fabricate a performed set, change a set target, or grant Finish.
 * The selected key is a UI hint; the authoritative set list always comes
 * from the immutable owner-verified plan.
 */
data class MeasuredWorkoutFocusSnapshot(
    val selectedSetKey:String,
    val selectedOrdinal:Int,
    val totalSets:Int,
    val recordedSets:Int,
    val selectedRecorded:Boolean,
    val nextUnrecordedKey:String?,
    val selectedExerciseId:String
) {
    val remainingSets:Int get()=totalSets-recordedSets
    val readyToFinish:Boolean get()=totalSets>0 && remainingSets==0
}

object MeasuredWorkoutFocusDomain {
    fun review(
        journal:MeasuredWorkoutJournal,requestedSetKey:String?
    ):MeasuredWorkoutFocusSnapshot? {
        if(!MeasuredWorkoutJournalDomain.verified(journal))return null
        val plans=journal.approvedPlan.sets
        if(plans.isEmpty() ||
            plans.map{it.setKey}.toSet().size!=plans.size ||
            plans.any{it.setKey !in journal.bySetKey})return null
        val missing=plans.filter{
            journal.bySetKey[it.setKey]?.effectiveRecord==null
        }
        val selected=plans.firstOrNull{it.setKey==requestedSetKey}
            ?:missing.firstOrNull() ?:plans.last()
        val position=plans.indexOfFirst{it.setKey==selected.setKey}
        val after=plans.drop(position+1).firstOrNull{
            journal.bySetKey[it.setKey]?.effectiveRecord==null}
        val next=after ?:missing.firstOrNull{
            it.setKey!=selected.setKey}
        return MeasuredWorkoutFocusSnapshot(
            selected.setKey,position+1,plans.size,
            plans.size-missing.size,
            journal.bySetKey[selected.setKey]?.effectiveRecord!=null,
            next?.setKey,selected.exerciseId)
    }

    /**
     * Move forward only AFTER a verified, durable new set observation.
     * Corrections retain the selected set for review. A failed/foreign
     * receipt, a different plan, or a changed global revision cannot
     * advance navigation, even if the UI callback fires.
     */
    fun afterDurableRecord(
        before:MeasuredWorkoutJournal,
        after:MeasuredWorkoutJournal,
        setKey:String
    ):String? {
        if(!MeasuredWorkoutJournalDomain.verified(before) ||
            !MeasuredWorkoutJournalDomain.verified(after))return null
        if(before.approvedPlan!=after.approvedPlan ||
            before.finishedByEventId!=null ||
            after.finishedByEventId!=null ||
            after.revision!=before.revision+1 ||
            after.receipts.size!=before.receipts.size+1 ||
            after.receipts.dropLast(1)!=before.receipts)return null
        val receipt=after.receipts.lastOrNull() ?:return null
        val write=receipt.operation as? MeasuredWorkoutOperation.Record
            ?:return null
        if(write.setKey!=setKey ||
            write.expectedRevision!=before.revision ||
            write.eventId in before.receipts.map{it.operation.eventId} ||
            after.bySetKey[setKey]?.effectiveRecord==null ||
            setKey !in before.bySetKey)return null
        // Correcting a saved observation must NOT jump away from the set.
        if(before.bySetKey[setKey]?.effectiveRecord!=null)return setKey
        val ordered=after.approvedPlan.sets
        val index=ordered.indexOfFirst{it.setKey==setKey}
        if(index<0)return null
        val next=ordered.drop(index+1).firstOrNull {
            after.bySetKey[it.setKey]?.effectiveRecord==null
        } ?:ordered.firstOrNull {
            after.bySetKey[it.setKey]?.effectiveRecord==null
        }
        return next?.setKey ?:setKey
    }
}
