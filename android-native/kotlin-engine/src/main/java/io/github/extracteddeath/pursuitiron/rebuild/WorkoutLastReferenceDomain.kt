package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** Display-only facts. Neither a target nor an instruction to progress. */
data class WorkoutReferenceKey(val setKey:String,val followUp:Boolean=false)
data class WorkoutLastSetReference(
    val workoutId:String,
    val completionOrder:Long,
    val outcome:KotlinJournalSetOutcome,
    val actual:KotlinPerformedSet?,
    val loadBasis:LoadBasis?
)
sealed interface WorkoutLastReferenceResult {
    data class Ready(val sets:Map<WorkoutReferenceKey,WorkoutLastSetReference>):
        WorkoutLastReferenceResult
    data object Unavailable:WorkoutLastReferenceResult
}
data class ComposerReferenceSession(
    val completionOrder:Long,
    val dayIndex:Int,
    val purpose:ComposerSessionPurpose,
    val evidence:KotlinWorkoutHistoryEvidence
)

/**
 * The native owner supplies the COMPLETE, corrected, authenticated history.
 * First select the latest same program DAY, then match sets within that day.
 * Never search older successes after a skip, failed attempt or substitution.
 * Warmups, working sets, duplicate exercise slots and amplifier visits remain
 * distinct. Missing actual RIR remains unknown; no goal is copied into Last.
 */
object WorkoutLastReferenceDomain {
    private fun ready(sets:Map<WorkoutReferenceKey,WorkoutLastSetReference>)=
        WorkoutLastReferenceResult.Ready(Collections.unmodifiableMap(LinkedHashMap(sets)))

    private fun ordered(orders:List<Long>,ids:List<String>)=
        orders.all{it>0} && orders.zipWithNext().all{it.first<it.second} &&
            ids.all{it.isNotBlank()} && ids.distinct().size==ids.size

    private fun compatible(a:DraftSetLoad,b:DraftSetLoad):Boolean=when {
        a==DraftSetLoad.ExplicitBodyweight -> b==DraftSetLoad.ExplicitBodyweight
        a is DraftSetLoad.Verified && b is DraftSetLoad.Verified ->
            a.unit==b.unit && a.basis==b.basis
        else -> false
    }

    fun custom(
        workoutId:String,next:CustomWorkoutDaySnapshot,
        completed:List<CustomCoachingHistoricalSession>
    ):WorkoutLastReferenceResult {
        if(!ordered(completed.map{it.completionOrder},completed.map{it.evidence.workoutId}) ||
            completed.any{
                it.evidence.athleteId!=it.snapshot.stamp.athleteId ||
                it.evidence.cycleId!=it.snapshot.stamp.cycleId ||
                it.evidence.cycleRevision!=it.snapshot.stamp.cycleRevision
            })return WorkoutLastReferenceResult.Unavailable
        val before=completed.singleOrNull{it.evidence.workoutId==workoutId}
            ?.completionOrder ?:Long.MAX_VALUE
        val prior=completed.lastOrNull{
            it.completionOrder<before && it.evidence.workoutId!=workoutId &&
                it.snapshot.stamp.athleteId==next.stamp.athleteId &&
                it.snapshot.stamp.cycleId==next.stamp.cycleId &&
                it.snapshot.weekday==next.weekday &&
                it.snapshot.absoluteWeek<next.absoluteWeek
        } ?:return ready(emptyMap())
        val old=prior.snapshot
        // Select the latest day BEFORE checking comparability; no cherry-pick.
        if(old.stamp.gymId!=next.stamp.gymId || old.phase!=next.phase ||
            old.split!=next.split || old.sessionPurpose!=next.sessionPurpose ||
            old.blockPurpose!=next.blockPurpose)return ready(emptyMap())
        val result=linkedMapOf<WorkoutReferenceKey,WorkoutLastSetReference>()
        for(slot in next.slots) {
            val previous=old.slots.singleOrNull{it.slotId==slot.slotId} ?:continue
            if(previous.exerciseId!=slot.exerciseId || previous.familyId!=slot.familyId ||
                previous.variantId!=slot.variantId || previous.attachmentId!=slot.attachmentId ||
                previous.role!=slot.role || previous.supersetGroup!=slot.supersetGroup)continue
            for(set in slot.sets) {
                val type=set.prescription.type
                val ordinal=slot.sets.filter{it.prescription.type==type}.indexOf(set)
                val oldSet=previous.sets.filter{it.prescription.type==type}
                    .getOrNull(ordinal) ?:continue
                if(!compatible(oldSet.prescription.load,set.prescription.load))continue
                for(part in CustomWorkoutVisitPart.entries) {
                    val followUp=part==CustomWorkoutVisitPart.FOLLOW_UP
                    val amplifier=set.prescription.amplifier
                    if(followUp && (amplifier==null ||
                            oldSet.prescription.amplifier?.kind!=amplifier.kind))continue
                    val facts=prior.evidence.visits.filter{
                        it.slotId==slot.slotId && it.setKey==oldSet.setKey && it.part==part
                    }
                    if(facts.size>1)return WorkoutLastReferenceResult.Unavailable
                    val fact=facts.singleOrNull() ?:continue
                    val oldLoad=if(followUp)oldSet.prescription.amplifier?.followUpLoad
                        ?:oldSet.prescription.load else oldSet.prescription.load
                    val newLoad=if(followUp)set.prescription.amplifier?.followUpLoad
                        ?:set.prescription.load else set.prescription.load
                    if(!compatible(oldLoad,newLoad))continue
                    if(fact.exerciseId!=slot.exerciseId || fact.setType!=type ||
                        fact.prescribedLoad!=oldLoad ||
                        !CustomWorkoutJournalDomain.actualValid(oldLoad,fact.outcome,fact.actual))
                        return WorkoutLastReferenceResult.Unavailable
                    result[WorkoutReferenceKey(set.setKey,followUp)]=WorkoutLastSetReference(
                        prior.evidence.workoutId,prior.completionOrder,fact.outcome,
                        fact.actual,(oldLoad as? DraftSetLoad.Verified)?.basis)
                }
            }
        }
        return ready(result)
    }

    fun composer(
        next:KotlinStagedWorkout,completed:List<ComposerReferenceSession>
    ):WorkoutLastReferenceResult {
        if(!ordered(completed.map{it.completionOrder},completed.map{it.evidence.workoutId}))
            return WorkoutLastReferenceResult.Unavailable
        val before=completed.singleOrNull{it.evidence.workoutId==next.workoutId}
            ?.completionOrder ?:Long.MAX_VALUE
        val prior=completed.lastOrNull{
            it.completionOrder<before && it.evidence.workoutId!=next.workoutId &&
                it.evidence.source.athleteId==next.source.athleteId &&
                it.evidence.source.programId==next.source.programId &&
                it.dayIndex>=0 && it.dayIndex==next.dayIndex
        } ?:return ready(emptyMap())
        if(prior.purpose!=next.purpose || prior.evidence.source.gymId!=next.source.gymId)
            return ready(emptyMap())
        val result=linkedMapOf<WorkoutReferenceKey,WorkoutLastSetReference>()
        for(set in next.targets) {
            val matches=prior.evidence.sets.filter{
                it.exerciseId==set.exerciseId && it.type==set.type &&
                    it.originalOrdinal==set.ordinal
            }
            if(matches.size>1)return WorkoutLastReferenceResult.Unavailable
            val fact=matches.singleOrNull() ?:continue
            if(!compatible(fact.issuedLoad,set.load))continue
            if(!CustomWorkoutJournalDomain.actualValid(fact.issuedLoad,fact.outcome,fact.actual))
                return WorkoutLastReferenceResult.Unavailable
            result[WorkoutReferenceKey(set.setKey)]=WorkoutLastSetReference(
                prior.evidence.workoutId,prior.completionOrder,fact.outcome,fact.actual,
                (fact.issuedLoad as? DraftSetLoad.Verified)?.basis)
        }
        return ready(result)
    }
}
