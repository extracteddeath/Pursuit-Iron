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
    /**
     * Actual physical loads are NUMBERS, not presentation strings. BigDecimal
     * equals() distinguishes 20, 20.0 and 20.00, but these are the same
     * physical load. Keep unit and Machine A/B exact; null is permitted only
     * when both observations are explicitly unweighted bodyweight.
     *
     * This is used for staged short-rest technique continuation and for
     * correcting an activation set after dependent follow-ups have been
     * logged. It never converts units or transfers weights between machines.
     */
    internal fun sameActualLoadAndStation(
        first:KotlinPerformedSet?,
        second:KotlinPerformedSet?
    ):Boolean {
        if(first==null || second==null)return first==null && second==null
        if(first.unit!=second.unit ||
            first.machineInstanceId!=second.machineInstanceId)return false
        val firstLoad=first.load
        val secondLoad=second.load
        if(firstLoad==null || secondLoad==null)
            return firstLoad==null && secondLoad==null &&
                first.unit==null && second.unit==null
        return firstLoad.compareTo(secondLoad)==0
    }

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
    ):List<CustomWorkoutVisitKey> = followUpVisits(set) { key ->
        journal.effectiveVisits[key]?.operation as? CustomWorkoutJournalOperation.Record
    }

    internal fun followUpVisits(
        set: CustomWorkoutSetSnapshot,
        recordFor: (CustomWorkoutVisitKey) -> CustomWorkoutJournalOperation.Record?
    ): List<CustomWorkoutVisitKey> {
        val main = recordFor(CustomWorkoutVisitKey(set.setKey, CustomWorkoutVisitPart.MAIN))
        val amp=set.prescription.amplifier ?:return emptyList()
        if(main?.outcome!=KotlinJournalSetOutcome.COMPLETED)return emptyList()
        if(amp.followUpRounds !in 1..5)return emptyList()
        val visits=mutableListOf<CustomWorkoutVisitKey>()
        val observedFollowUpReps=mutableListOf<Int>()
        for(stage in 1..amp.followUpRounds) {
            val key=CustomWorkoutVisitKey(set.setKey,
                CustomWorkoutVisitPart.FOLLOW_UP,stage)
            if(amp.kind==CustomAmplifierKind.DROP_SET) {
                // Apply to EVERY weight drop, including the older one-drop
                // recipe: an issued "reduction" may exceed what the athlete
                // really lifted. No new stage is offered in that situation.
                // An athlete's ACTUAL activation weight may differ from
                // the issued target. Never present an optional drop that
                // would increase or hold weight relative to the last
                // performed stage; a changed target is not consent.
                val previous=if(stage==1)null else
                    recordFor(CustomWorkoutVisitKey(
                        set.setKey,CustomWorkoutVisitPart.FOLLOW_UP,stage-1))?.performed
                // The SAME source-authority governs the editable prefill
                // and the actual permitted next stage. The second drop must
                // follow the recorded FIRST drop, not the activation.
                val entry=AdvancedAmplifierProtocolDomain.resolveFollowUpEntryLoad(
                    amp,set.prescription.load,main.performed,
                    main.performed?.machineInstanceId,stage,previous)
                if(entry !is AmplifierFollowUpEntryLoad.AuthoredStage)break
            }
            visits+=key
            val recorded=recordFor(key) ?:break
            if(recorded.outcome!=KotlinJournalSetOutcome.COMPLETED)break
            if(AdvancedAmplifierProtocolDomain.requiresActivationLoad(amp.kind) &&
                !AdvancedAmplifierProtocolDomain.validSameLoadFollowUp(
                    amp,set.prescription.load,main.performed,
                    recorded.performed,recorded.outcome))break
            if(amp.followUpRounds>1 &&
                amp.kind in setOf(CustomAmplifierKind.MYO_REPS,
                    CustomAmplifierKind.REST_PAUSE,CustomAmplifierKind.CLUSTER)) {
                observedFollowUpReps+=recorded.performed?.reps ?:break
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

    /** A correction may change reps or effort on an earlier stage only while
     * every later receipt still has its original physical anchor and is reachable. */
    internal fun preservesRecordedFollowUps(
        set: CustomWorkoutSetSnapshot,
        replacement: CustomWorkoutJournalOperation.Record,
        recorded: Collection<CustomWorkoutVisitKey>,
        finished: Boolean,
        recordFor: (CustomWorkoutVisitKey) -> CustomWorkoutJournalOperation.Record?
    ): Boolean {
        val visit = replacement.visit
        if (visit.part == CustomWorkoutVisitPart.FOLLOW_UP &&
            recorded.any { it.stage > visit.stage } &&
            !sameActualLoadAndStation(recordFor(visit)?.performed, replacement.performed)) return false
        val reachable = followUpVisits(set) { key ->
            if (key == visit) replacement else recordFor(key)
        }
        return reachable.containsAll(recorded) && (!finished || recorded.containsAll(reachable))
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
