package io.github.extracteddeath.pursuitiron.rebuild

/** One authored working set per participating movement. Shorter members
 * drop out of later rounds without losing, duplicating or inventing a set. */
data class CustomWorkoutSupersetRound(
    val groupId:String,val number:Int,val total:Int,val setKeys:List<String>
)

data class CustomWorkoutExecutionStep(
    val slotIndex:Int,val set:CustomWorkoutSetSnapshot,
    val round:CustomWorkoutSupersetRound?=null
)

/** Shared execution projection for next-visit, Focus and round rest. The
 * issued snapshot, original targets and append-only receipts never change.
 * Supersets warm up each member first, then alternate authored working sets.
 * A triggered amplifier remains with its main before moving to a partner. */
object CustomWorkoutExecutionDomain {
    fun orderedSets(snapshot:CustomWorkoutDaySnapshot):List<CustomWorkoutExecutionStep> = buildList {
        val visited=mutableSetOf<Int>()
        for((index,slot) in snapshot.slots.withIndex()) {
            if(index in visited)continue
            val members=slot.supersetGroup?.let {group->
                snapshot.slots.withIndex().filter{it.value.supersetGroup==group}
            }.orEmpty()
            // A malformed legacy/synthetic group is never silently treated
            // as a valid pair. Its sets retain ordinary authored order.
            if(members.size !in 2..3) {
                visited.add(index)
                slot.sets.forEach{add(CustomWorkoutExecutionStep(index,it))}
                continue
            }
            visited.addAll(members.map{it.index})
            members.forEach{member->member.value.sets.filter{
                it.prescription.type==DraftSetType.WARMUP
            }.forEach{add(CustomWorkoutExecutionStep(member.index,it))}}
            val working=members.associate{member->member.index to member.value.sets.filter{
                it.prescription.type==DraftSetType.WORKING
            }}
            val rounds=working.values.maxOfOrNull{it.size} ?:0
            for(number in 0 until rounds) {
                val steps=members.mapNotNull {member->
                    working.getValue(member.index).getOrNull(number)?.let {
                        CustomWorkoutExecutionStep(member.index,it)
                    }
                }
                val round=CustomWorkoutSupersetRound(checkNotNull(slot.supersetGroup),
                    number+1,rounds,steps.map{it.set.setKey})
                steps.forEach{add(it.copy(round=round))}
            }
        }
    }

    /** Read-only follow-up projection used by both the engine and the
     * separate Compose app module. Saved mini-sets and the next permitted
     * stage are shown; future unperformed stages are not observations.
     * Never mutates the issued prescription or journal. */
    fun followUpVisits(
        journal:CustomWorkoutJournalDraft,
        set:CustomWorkoutSetSnapshot
    ):List<CustomWorkoutVisitKey> {
        val main=journal.effectiveVisits[CustomWorkoutVisitKey(
            set.setKey,CustomWorkoutVisitPart.MAIN)]?.operation
            as? CustomWorkoutJournalOperation.Record
        val amp=set.prescription.amplifier ?:return emptyList()
        if(main?.outcome!=KotlinJournalSetOutcome.COMPLETED)return emptyList()
        if(amp.followUpRounds !in 1..5)return emptyList()
        val visits=mutableListOf<CustomWorkoutVisitKey>()
        val observedFollowUpReps=mutableListOf<Int>()
        for(stage in 1..amp.followUpRounds) {
            val key=CustomWorkoutVisitKey(set.setKey,
                CustomWorkoutVisitPart.FOLLOW_UP,stage)
            visits+=key
            val recorded=journal.effectiveVisits[key]?.operation
                as? CustomWorkoutJournalOperation.Record ?:break
            if(recorded.outcome!=KotlinJournalSetOutcome.COMPLETED)break
            if(amp.followUpRounds>1 &&
                (amp.kind==CustomAmplifierKind.MYO_REPS ||
                 amp.kind==CustomAmplifierKind.REST_PAUSE ||
                 amp.kind==CustomAmplifierKind.CLUSTER)) {
                // These protocols require the EXACT activation load and
                // station. Keep any unexpected observation as factual history
                // but never unlock more same-load work from changed equipment.
                val activation=main.performed ?:break
                val observed=recorded.performed ?:break
                if(observed.load!=activation.load ||
                    observed.unit!=activation.unit ||
                    observed.machineInstanceId!=activation.machineInstanceId)break
                observedFollowUpReps+=observed.reps
                val continueTraining=when(amp.kind) {
                    CustomAmplifierKind.MYO_REPS ->
                        AdvancedAmplifierProtocolDomain.nextMyoRepStep(
                            amp.followUpReps,observedFollowUpReps,
                            amp.followUpRounds)==MyoRepNextStep.KEEP_GOING
                    CustomAmplifierKind.REST_PAUSE ->
                        AdvancedAmplifierProtocolDomain.continueRestPause(
                            amp.followUpReps,observedFollowUpReps,
                            amp.followUpRounds)
                    CustomAmplifierKind.CLUSTER ->
                        AdvancedAmplifierProtocolDomain.continueCluster(
                            amp.followUpReps,observedFollowUpReps,
                            amp.followUpRounds)
                    else -> false
                }
                if(!continueTraining)break
            }
        }
        return visits
    }

    internal fun unresolvedVisit(journal:CustomWorkoutJournalDraft,
        set:CustomWorkoutSetSnapshot):CustomWorkoutVisitKey? {
        val main=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN)
        val recorded=journal.effectiveVisits[main]?.operation
            as? CustomWorkoutJournalOperation.Record ?:return main
        if(recorded.outcome==KotlinJournalSetOutcome.COMPLETED)
            return followUpVisits(journal,set).firstOrNull {
                it !in journal.effectiveVisits
            }
        return null
    }

    fun nextUnresolvedVisit(journal:CustomWorkoutJournalDraft):CustomWorkoutVisitKey? {
        if(journal.finishedByEventId!=null)return null
        val steps=orderedSets(journal.snapshot)
        // An athlete can record out of order in All sets. Complete the most
        // recently triggered amplifier before another movement, also after
        // cold replay; changing Focus cannot erase its required observation.
        val follow=steps.mapNotNull{step->
            unresolvedVisit(journal,step.set)?.takeIf{it.part==CustomWorkoutVisitPart.FOLLOW_UP}
        }.maxByOrNull{visit->journal.effectiveVisits.getValue(
            CustomWorkoutVisitKey(visit.setKey,CustomWorkoutVisitPart.MAIN)).appliedRevision}
        return follow ?:steps.firstNotNullOfOrNull{unresolvedVisit(journal,it.set)}
    }

    fun roundIsResolved(journal:CustomWorkoutJournalDraft,round:CustomWorkoutSupersetRound):Boolean {
        val sets=journal.snapshot.slots.flatMap{it.sets}.associateBy{it.setKey}
        return round.setKeys.isNotEmpty() && round.setKeys.all {key->
            sets[key]?.let{unresolvedVisit(journal,it)==null}==true
        }
    }
}
