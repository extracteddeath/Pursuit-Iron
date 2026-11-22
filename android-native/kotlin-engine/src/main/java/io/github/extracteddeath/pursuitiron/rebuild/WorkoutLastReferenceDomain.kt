package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/** Display-only facts. Neither a target nor an instruction to progress. */
data class WorkoutReferenceKey(val setKey:String,val followUp:Boolean=false,val stage:Int=1)
data class WorkoutLastSetReference(
    val workoutId:String,
    val completionOrder:Long,
    val outcome:KotlinJournalSetOutcome,
    val actual:KotlinPerformedSet?,
    val loadBasis:LoadBasis?
)
/** An exercise's set ordinal and physical station are separate references. */
data class WorkoutMachineReferenceKey(
    val key:WorkoutReferenceKey,
    val machineId:String
)
sealed interface WorkoutLastReferenceResult {
    data class Ready(
        val sets:Map<WorkoutReferenceKey,WorkoutLastSetReference>,
        /** Latest authentic observation for each physical machine and set slot. */
        val machineSets:Map<WorkoutMachineReferenceKey,WorkoutLastSetReference> = emptyMap()
    ):WorkoutLastReferenceResult
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
    private fun ready(
        sets:Map<WorkoutReferenceKey,WorkoutLastSetReference>,
        machineSets:Map<WorkoutMachineReferenceKey,WorkoutLastSetReference> = emptyMap()
    )=WorkoutLastReferenceResult.Ready(
        Collections.unmodifiableMap(LinkedHashMap(sets)),
        Collections.unmodifiableMap(LinkedHashMap(machineSets)))

    private fun ordered(orders:List<Long>,ids:List<String>)=
        orders.all{it>0} && orders.zipWithNext().all{it.first<it.second} &&
            ids.all{it.isNotBlank()} && ids.distinct().size==ids.size

    private fun compatible(a:DraftSetLoad,b:DraftSetLoad):Boolean=when {
        a==DraftSetLoad.ExplicitBodyweight -> b==DraftSetLoad.ExplicitBodyweight
        a is DraftSetLoad.Verified && b is DraftSetLoad.Verified ->
            a.unit==b.unit && a.basis==b.basis
        else -> false
    }

    private fun customBase(
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
                    val priorAmp=oldSet.prescription.amplifier
                    val oldStages=if(followUp) checkNotNull(priorAmp).followUpRounds else 1
                    val newStages=if(followUp) checkNotNull(amplifier).followUpRounds else 1
                    if(facts.any { it.stage !in 1..oldStages } ||
                        facts.distinctBy { it.stage }.size!=facts.size)
                        return WorkoutLastReferenceResult.Unavailable
                    for(fact in facts) {
                        val oldLoad=if(followUp) AdvancedAmplifierProtocolDomain.followUpLoadForStage(
                            checkNotNull(priorAmp),oldSet.prescription.load,fact.stage)
                            else oldSet.prescription.load
                        if(fact.exerciseId!=slot.exerciseId || fact.setType!=type ||
                            !KotlinSetNumberAuthorityDomain.sameIssuedTarget(fact.prescribedLoad,oldLoad) ||
                            followUp && fact.amplifierKind!=priorAmp?.kind ||
                            !CustomWorkoutJournalDomain.actualValid(oldLoad,fact.outcome,fact.actual))
                            return WorkoutLastReferenceResult.Unavailable
                        if(fact.stage>newStages)continue
                        val newLoad=if(followUp) AdvancedAmplifierProtocolDomain.followUpLoadForStage(
                            checkNotNull(amplifier),set.prescription.load,fact.stage)
                            else set.prescription.load
                        if(!compatible(oldLoad,newLoad))continue
                        result[WorkoutReferenceKey(set.setKey,followUp,fact.stage)]=WorkoutLastSetReference(
                            prior.evidence.workoutId,prior.completionOrder,fact.outcome,
                            fact.actual,(oldLoad as? DraftSetLoad.Verified)?.basis)
                    }
                }
            }
        }
        return ready(result)
    }

    private fun composerBase(
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

    /**
     * Preserve the existing latest-day safety gate for generic references,
     * then project a separate latest observed set for EACH physical machine.
     * A B session does not replace A's history; an A skip/failure remains
     * the latest A fact and never revives an older successful A attempt.
     */
    private fun appendMachineFacts(
        destination:MutableMap<WorkoutMachineReferenceKey,WorkoutLastSetReference>,
        source:Map<WorkoutReferenceKey,WorkoutLastSetReference>
    ) {
        // A later visit replaces the whole technique on that station. An early
        // stop or a shorter prescription cannot revive an older mini-set.
        val mainBySet=source.filterKeys { !it.followUp }.mapKeys { it.key.setKey }
        destination.keys.removeAll { previous ->
            val main=mainBySet[previous.key.setKey]
            previous.key.followUp && main!=null && (main.actual==null ||
                previous.machineId==KotlinMachineAlternativesDomain.normalizeMachineId(main.actual.machineInstanceId))
        }
        source.forEach { (key,fact) ->
            if(fact.outcome==KotlinJournalSetOutcome.SKIPPED && fact.actual==null) {
                // The station used for an unattempted set is unknowable.
                // Fail closed for this slot rather than promoting an older PR.
                destination.keys.removeAll { it.key==key }
            } else {
                val machine=KotlinMachineAlternativesDomain.normalizeMachineId(
                    fact.actual?.machineInstanceId)
                destination[WorkoutMachineReferenceKey(key,machine)]=fact
            }
        }
    }

    /**
     * The generic Last has already rejected a changed exercise or native
     * Swap slot. Its absence is NOT permission to erase an older, physically
     * independent Machine A reference after a verified Machine B substitute.
     *
     * Conversely, that substitute must invalidate the old set on its OWN
     * station, even though its new set keys cannot match the previous lift.
     * An unattributed skip/failed observation cannot prove a different
     * station, so it invalidates every station for the affected current slot.
     * This is a historical display projection only; never progression.
     */
    private fun invalidateChangedCustomStation(
        destination:MutableMap<WorkoutMachineReferenceKey,WorkoutLastSetReference>,
        source:CustomCoachingHistoricalSession,
        next:CustomWorkoutDaySnapshot
    ) {
        val previousDay=source.snapshot
        if(previousDay.stamp.gymId!=next.stamp.gymId ||
            previousDay.phase!=next.phase || previousDay.split!=next.split ||
            previousDay.sessionPurpose!=next.sessionPurpose ||
            previousDay.blockPurpose!=next.blockPurpose)return
        for(slot in next.slots) {
            val previous=previousDay.slots.singleOrNull{it.slotId==slot.slotId}
            if(previous!=null && previous.exerciseId==slot.exerciseId &&
                previous.familyId==slot.familyId &&
                previous.variantId==slot.variantId &&
                previous.attachmentId==slot.attachmentId &&
                previous.role==slot.role &&
                previous.supersetGroup==slot.supersetGroup)continue
            // If the slot ID vanished, native Swap has replaced it with
            // new keys. No arbitrary replacement-to-old-slot mapping exists
            // in a finished receipt, so conservatively inspect the whole
            // same-day main working record.
            // This is the SAME complete, issued-set verification used by
            // basic progression and V2/V3 coaching. Seeing one recorded B
            // set is not proof that missing/failed sets could not involve A.
            // A native Swap changes the slot and set keys; an ordinary
            // substitute may preserve them. Neither scenario gets to infer
            // station independence from the exercise label alone.
            val machines=if(previous==null)
                CustomVerifiedSwappedStationEvidenceDomain
                    .machinesWhenOriginalSlotAbsent(source,slot.slotId)
            else CustomVerifiedSwappedStationEvidenceDomain
                    .machinesForVerifiedSlot(source,slot.slotId)
            val currentKeys=slot.sets.map{it.setKey}.toSet()
            destination.keys.removeAll {
                it.key.setKey in currentKeys &&
                    (machines==null || it.machineId in machines)
            }
        }
    }

    fun custom(
        workoutId:String,next:CustomWorkoutDaySnapshot,
        completed:List<CustomCoachingHistoricalSession>
    ):WorkoutLastReferenceResult {
        val baseline=customBase(workoutId,next,completed)
        if(baseline !is WorkoutLastReferenceResult.Ready)return baseline
        val before=completed.singleOrNull { it.evidence.workoutId==workoutId }
            ?.completionOrder ?:Long.MAX_VALUE
        // Preserve the old strict latest-day boundary when the gym, phase or
        // training intent changed. Only a SAME-scope substituted movement on
        // a different verified station can preserve an earlier machine Last.
        val latest=completed.lastOrNull{
            it.completionOrder<before && it.evidence.workoutId!=workoutId &&
                it.snapshot.stamp.athleteId==next.stamp.athleteId &&
                it.snapshot.stamp.cycleId==next.stamp.cycleId &&
                it.snapshot.weekday==next.weekday &&
                it.snapshot.absoluteWeek<next.absoluteWeek
        }
        if(latest!=null && (latest.snapshot.stamp.gymId!=next.stamp.gymId ||
            latest.snapshot.phase!=next.phase ||
            latest.snapshot.split!=next.split ||
            latest.snapshot.sessionPurpose!=next.sessionPurpose ||
            latest.snapshot.blockPurpose!=next.blockPurpose))return baseline
        val machines=linkedMapOf<WorkoutMachineReferenceKey,WorkoutLastSetReference>()
        for(session in completed) {
            if(session.completionOrder>=before ||
                session.evidence.workoutId==workoutId ||
                session.snapshot.stamp.athleteId!=next.stamp.athleteId ||
                session.snapshot.stamp.cycleId!=next.stamp.cycleId ||
                session.snapshot.weekday!=next.weekday ||
                session.snapshot.absoluteWeek>=next.absoluteWeek)
                continue
            val observation=customBase(workoutId,next,listOf(session))
            if(observation !is WorkoutLastReferenceResult.Ready)
                return WorkoutLastReferenceResult.Unavailable
            invalidateChangedCustomStation(machines,session,next)
            appendMachineFacts(machines,observation.sets)
        }
        return ready(baseline.sets,machines)
    }

    /**
     * A generated workout SwapExercise replaces its exercise and set keys.
     * Keep older, verified A Last when all newer physical work was on B,
     * but invalidate stale A after an A substitution. No replacement-to-
     * original key mapping survives the generic generated history; missing
     * set keys therefore require examining the whole day's station facts.
     */
    private fun invalidateChangedComposerStation(
        destination:MutableMap<WorkoutMachineReferenceKey,WorkoutLastSetReference>,
        previous:ComposerReferenceSession,next:KotlinStagedWorkout
    ) {
        if(previous.purpose!=next.purpose ||
            previous.evidence.source.gymId!=next.source.gymId)return
        val missing=next.targets.filter { issued ->
            previous.evidence.sets.none { fact ->
                fact.exerciseId==issued.exerciseId &&
                    fact.type==issued.type &&
                    fact.originalOrdinal==issued.ordinal &&
                    compatible(fact.issuedLoad,issued.load)
            }
        }.map{it.setKey}.toSet()
        if(missing.isEmpty())return
        val work=previous.evidence.sets.filter{
            it.type==DraftSetType.WORKING
        }
        val uncertain=previous.evidence.prescriptionEditedForTime || work.isEmpty() || work.any { fact ->
            val issued=fact.issuedLoad as? DraftSetLoad.Verified
            val actual=fact.actual
            fact.outcome!=KotlinJournalSetOutcome.COMPLETED ||
                actual==null || issued==null || actual.load==null ||
                !KotlinMachineAlternativesDomain.validInstanceId(actual.machineInstanceId) ||
                !KotlinSetNumberAuthorityDomain.valid(
                    fact.issuedLoad,fact.outcome,actual)
        }
        val machines=work.mapNotNull {it.actual?.machineInstanceId}.toSet()
        destination.keys.removeAll {
            it.key.setKey in missing && (uncertain || it.machineId in machines)
        }
    }

    /**
     * One source-of-truth projection for BOTH workout set display and the
     * optional "Use Last" draft helper. Machine A/B may use ONLY explicitly
     * verified machineSets: an empty map can mean that a newer uncertain
     * substitution invalidated older A evidence. Generic sets are not a
     * substitute for that missing station proof.
     *
     * For an unconfigured legacy station ONLY, keep factual unattributed
     * records from the generic same-day reference when no machine-specific
     * map exists. A null machine choice never borrows A or B.
     */
    fun physicalStation(
        history:WorkoutLastReferenceResult?,
        selectedMachineId:String?
    ):WorkoutLastReferenceResult? {
        if(history !is WorkoutLastReferenceResult.Ready)return history
        val requested=KotlinMachineAlternativesDomain.normalizeMachineId(
            selectedMachineId)
        if(selectedMachineId!=null &&
            !KotlinMachineAlternativesDomain.validInstanceId(selectedMachineId))
            return ready(emptyMap())
        val facts=if(selectedMachineId==null && history.machineSets.isEmpty())
            history.sets.filterValues {
                it.actual?.machineInstanceId==null
            }
        else history.machineSets.filter { (key,value) ->
            key.machineId==requested &&
                (value.actual==null ||
                    KotlinMachineAlternativesDomain.normalizeMachineId(
                        value.actual.machineInstanceId)==requested)
        }.mapKeys { it.key.key }
        return ready(facts)
    }

    fun composer(
        next:KotlinStagedWorkout,completed:List<ComposerReferenceSession>
    ):WorkoutLastReferenceResult {
        val baseline=composerBase(next,completed)
        if(baseline !is WorkoutLastReferenceResult.Ready)return baseline
        val before=completed.singleOrNull { it.evidence.workoutId==next.workoutId }
            ?.completionOrder ?:Long.MAX_VALUE
        // A changed gym or purpose is not a same-source station swap.
        val latest=completed.lastOrNull{
            it.completionOrder<before &&
                it.evidence.workoutId!=next.workoutId &&
                it.evidence.source.athleteId==next.source.athleteId &&
                it.evidence.source.programId==next.source.programId &&
                it.dayIndex==next.dayIndex && it.dayIndex>=0
        }
        if(latest!=null && (latest.purpose!=next.purpose ||
            latest.evidence.source.gymId!=next.source.gymId))return baseline
        val machines=linkedMapOf<WorkoutMachineReferenceKey,WorkoutLastSetReference>()
        for(session in completed) {
            if(session.completionOrder>=before ||
                session.evidence.workoutId==next.workoutId ||
                session.evidence.source.athleteId!=next.source.athleteId ||
                session.evidence.source.programId!=next.source.programId ||
                session.dayIndex!=next.dayIndex || session.dayIndex<0)
                continue
            val observation=composerBase(next,listOf(session))
            if(observation !is WorkoutLastReferenceResult.Ready)
                return WorkoutLastReferenceResult.Unavailable
            invalidateChangedComposerStation(machines,session,next)
            appendMachineFacts(machines,observation.sets)
        }
        return ready(baseline.sets,machines)
    }
}
