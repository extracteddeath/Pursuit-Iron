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

    internal fun unresolvedVisit(journal:CustomWorkoutJournalDraft,
        set:CustomWorkoutSetSnapshot):CustomWorkoutVisitKey? {
        val main=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN)
        val recorded=journal.effectiveVisits[main]?.operation
            as? CustomWorkoutJournalOperation.Record ?:return main
        if(recorded.outcome==KotlinJournalSetOutcome.COMPLETED && set.prescription.amplifier!=null) {
            val follow=CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.FOLLOW_UP)
            if(follow !in journal.effectiveVisits)return follow
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
