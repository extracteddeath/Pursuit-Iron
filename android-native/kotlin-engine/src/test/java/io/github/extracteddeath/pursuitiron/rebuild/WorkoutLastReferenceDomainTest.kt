package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class WorkoutLastReferenceDomainTest {
    private fun load(value:Int,basis:LoadBasis=LoadBasis.BARBELL_TOTAL)=
        DraftSetLoad.Verified(LoadUnit.LB,basis,BigDecimal(value))
    private fun custom(week:Int=2,day:Int=0,exercise:String="barbell_bench",
        warmup:Boolean=true,slots:List<String> = listOf("bench"),
        basis:LoadBasis=LoadBasis.BARBELL_TOTAL):CustomWorkoutDaySnapshot {
        val recipe=listOfNotNull(
            if(warmup)CustomAuthoredSet(DraftSetType.WARMUP,load(45,basis),
                RepBand(5,5),null,30,60) else null,
            CustomAuthoredSet(DraftSetType.WORKING,load(135,basis),RepBand(5,8),2,30,120),
            CustomAuthoredSet(DraftSetType.WORKING,load(135,basis),RepBand(5,8),2,30,120))
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete","profile","cycle",1,"gym",1),
            week,week,"build",TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("ULPPL"),day,"Upper",
            ComposerSessionPurpose.HYPERTROPHY,slots.map {slot->
                CustomWorkoutSlotSnapshot(slot,exercise,"bench","barbell",
                    ComposerRole.PRIMARY,null,null,true,"",recipe.mapIndexed {i,p->
                        CustomWorkoutSetSnapshot("cycle:w$week:d$day:$slot:s${i+1}",slot,i+1,p)
                    })
            },420,120)
    }
    private fun session(snapshot:CustomWorkoutDaySnapshot=custom(),order:Long=1,
        id:String="prior-$order",outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED
    ):CustomCoachingHistoricalSession {
        var journal=CustomWorkoutJournalDomain.begin(id,snapshot,"a".repeat(64))
        snapshot.slots.forEach {slot->slot.sets.forEach {set->
            val target=set.prescription.load as DraftSetLoad.Verified
            val actual=when(outcome) {
                KotlinJournalSetOutcome.SKIPPED -> null
                KotlinJournalSetOutcome.FAILED_ATTEMPT ->
                    KotlinPerformedSet(target.value,target.unit,0,null)
                KotlinJournalSetOutcome.COMPLETED ->
                    KotlinPerformedSet(target.value,target.unit,5,null)
            }
            journal=(CustomWorkoutJournalDomain.reduce(journal,
                CustomWorkoutJournalOperation.Record("record-${set.setKey}",journal.revision,
                    CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                    outcome,actual,null)) as CustomWorkoutJournalDecision.Proposed).draft
            val amplifier=set.prescription.amplifier
            if(outcome==KotlinJournalSetOutcome.COMPLETED && amplifier!=null) {
                for(stage in 1..amplifier.followUpRounds) {
                    val followLoad=AdvancedAmplifierProtocolDomain.followUpLoadForStage(
                        amplifier,target,stage) as DraftSetLoad.Verified
                    journal=(CustomWorkoutJournalDomain.reduce(journal,
                        CustomWorkoutJournalOperation.Record("follow-$stage-${set.setKey}",journal.revision,
                            CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.FOLLOW_UP,stage),
                            outcome,KotlinPerformedSet(followLoad.value,followLoad.unit,
                                amplifier.followUpReps,null),null))
                        as CustomWorkoutJournalDecision.Proposed).draft
                }
            }
        }}
        journal=(CustomWorkoutJournalDomain.reduce(journal,
            CustomWorkoutJournalOperation.Finish("finish-$id",journal.revision))
            as CustomWorkoutJournalDecision.Proposed).draft
        val facts=(CustomWorkoutHistoryEvidenceDomain.project(journal)
            as CustomHistoryEvidenceResult.Candidate).evidence
        return CustomCoachingHistoricalSession(order,snapshot,facts)
    }
    private fun result(next:CustomWorkoutDaySnapshot=custom(3),
        sessions:List<CustomCoachingHistoricalSession> = listOf(session()),id:String="current")=
        WorkoutLastReferenceDomain.custom(id,next,sessions)
    private fun sets(result:WorkoutLastReferenceResult)=
        (result as WorkoutLastReferenceResult.Ready).sets

    @Test fun explicitMachineNeverBorrowsGenericLastWithoutStationProof() {
        val key=WorkoutReferenceKey("main-working-1")
        val fact=WorkoutLastSetReference("old-A",1,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("200"),LoadUnit.LB,8,2,"A"),
            LoadBasis.MACHINE_STACK)
        // machineSets can be intentionally empty after an unresolved newer
        // substitute. A generic A fact alone is not station-proof for Last
        // display, the one-tap draft fill, or a Leveling observation.
        val ambiguous=WorkoutLastReferenceResult.Ready(mapOf(key to fact))
        val selected=WorkoutLastReferenceDomain.physicalStation(ambiguous,"A")
            as WorkoutLastReferenceResult.Ready
        assertTrue(selected.sets.isEmpty())
        assertNull(WorkoutLastDraftDomain.reviewedActual(selected,key,
            load(200,LoadBasis.MACHINE_STACK)))
        assertTrue((WorkoutLastReferenceDomain.physicalStation(ambiguous,null)
            as WorkoutLastReferenceResult.Ready).sets.isEmpty())
        assertTrue((WorkoutLastReferenceDomain.physicalStation(ambiguous,"bad machine")
            as WorkoutLastReferenceResult.Ready).sets.isEmpty())
        assertNull(WorkoutLastReferenceDomain.physicalStation(null,"A"))
        assertEquals(WorkoutLastReferenceResult.Unavailable,
            WorkoutLastReferenceDomain.physicalStation(
                WorkoutLastReferenceResult.Unavailable,"A"))
    }

    @Test fun verifiedMachineLastKeepsOnlyItsStationAndItsAmplifierStages() {
        val key=WorkoutReferenceKey("working-1")
        val followUp=WorkoutReferenceKey("working-1",true,2)
        fun observed(id:String,order:Long,mass:Int)=
            WorkoutLastSetReference("visit-$id",order,
                KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal(mass),LoadUnit.LB,8,2,id),
                LoadBasis.MACHINE_STACK)
        val a=observed("A",1,200)
        val b=observed("B",2,140)
        val history=WorkoutLastReferenceResult.Ready(
            mapOf(key to b),mapOf(
                WorkoutMachineReferenceKey(key,"A") to a,
                WorkoutMachineReferenceKey(followUp,"A") to a,
                WorkoutMachineReferenceKey(key,"B") to b))
        val selectedA=WorkoutLastReferenceDomain.physicalStation(history,"A")
            as WorkoutLastReferenceResult.Ready
        assertEquals(mapOf(key to a,followUp to a),selectedA.sets)
        assertEquals(BigDecimal("200"),
            WorkoutLastDraftDomain.reviewedActual(
                selectedA,key,load(200,LoadBasis.MACHINE_STACK))?.load)
        val selectedB=WorkoutLastReferenceDomain.physicalStation(history,"B")
            as WorkoutLastReferenceResult.Ready
        assertEquals(mapOf(key to b),selectedB.sets)
        assertTrue((WorkoutLastReferenceDomain.physicalStation(history,null)
            as WorkoutLastReferenceResult.Ready).sets.isEmpty())

        // Never accept a caller's inconsistent A index over a factual B ID.
        val misindexed=WorkoutLastReferenceResult.Ready(emptyMap(),mapOf(
            WorkoutMachineReferenceKey(key,"A") to b))
        assertTrue((WorkoutLastReferenceDomain.physicalStation(misindexed,"A")
            as WorkoutLastReferenceResult.Ready).sets.isEmpty())
    }

    @Test fun unassignedLegacyLastKeepsOnlyUnassignedFacts() {
        val key=WorkoutReferenceKey("unassigned-1")
        val legacy=WorkoutLastSetReference("old-legacy",1,
            KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal("135"),LoadUnit.LB,6,null,null),
            LoadBasis.BARBELL_TOTAL)
        val a=legacy.copy(actual=legacy.actual?.copy(machineInstanceId="A"))
        val reference=WorkoutLastReferenceResult.Ready(
            mapOf(key to legacy,WorkoutReferenceKey("A") to a))
        val unassigned=WorkoutLastReferenceDomain.physicalStation(reference,null)
            as WorkoutLastReferenceResult.Ready
        assertEquals(mapOf(key to legacy),unassigned.sets)
        assertTrue((WorkoutLastReferenceDomain.physicalStation(reference,"A")
            as WorkoutLastReferenceResult.Ready).sets.isEmpty())
    }

    @Test fun exactSetsUseActualsAndKeepUnreportedRirUnknown() {
        val next=custom(3)
        val references=sets(result(next))
        assertEquals(3,references.size)
        val first=references.getValue(WorkoutReferenceKey(next.slots.single().sets[1].setKey))
        assertEquals(BigDecimal(135),first.actual?.load)
        assertEquals(5,first.actual?.reps)
        assertNull(first.actual?.actualRir)
        assertEquals(1L,first.completionOrder)
        assertTrue(runCatching{(references as MutableMap<*,*>).clear()}.isFailure)
    }

    @Test fun upperAndPushOwnSeparateReferencesEvenForTheSameExercise() {
        val upper=session(order=1)
        val push=session(custom(2,day=3),order=2)
        assertTrue(sets(result(sessions=listOf(upper,push))).values.all{it.workoutId==upper.evidence.workoutId})
    }

    @Test fun skipsAndFailedAttemptsNeverFallBackToAnOlderSuccess() {
        val older=session(custom(1),order=1)
        for(outcome in listOf(KotlinJournalSetOutcome.SKIPPED,KotlinJournalSetOutcome.FAILED_ATTEMPT)) {
            val latest=session(order=2,outcome=outcome)
            val references=sets(result(sessions=listOf(older,latest)))
            assertEquals(3,references.size)
            assertTrue(references.values.all{it.workoutId==latest.evidence.workoutId && it.outcome==outcome})
            if(outcome==KotlinJournalSetOutcome.SKIPPED)
                assertTrue(references.values.all{it.actual==null})
            else assertTrue(references.values.all{it.actual?.reps==0})
        }
    }

    @Test fun aSubstitutionOnTheLatestDayDoesNotReviveTheOlderExercise() {
        assertTrue(sets(result(sessions=listOf(session(custom(1),1),
            session(custom(exercise="paused_bench"),2)))).isEmpty())
    }

    @Test fun currentWorkoutSwapAndUndoNeedReferencesForTheCurrentSnapshot() {
        val next=custom(3)
        val history=listOf(session())
        val original=sets(result(next,history))
        assertEquals(3,original.size)
        // A live swap owns a fresh slot and set keys. References fetched
        // for this source are deliberately empty rather than borrowing the
        // original exercise's weights or earlier successful sets.
        val replacement=custom(3,exercise="paused_bench",slots=listOf("swap-bench"))
        assertTrue(sets(result(replacement,history)).isEmpty())
        // Undo restores the original source and its comparable Last facts.
        // Caching the empty replacement result would hide these valid facts.
        assertEquals(original,sets(result(next,history)))

        val generated=composerSnapshot()
        val before=sets(WorkoutLastReferenceDomain.composer(
            generated,listOf(composerSession(1))))
        assertEquals(1,before.size)
        val swapped=generated.copy(targets=generated.targets.map {
            it.copy(exerciseId="paused_bench",setKey="swap-working-1")
        })
        assertTrue(sets(WorkoutLastReferenceDomain.composer(
            swapped,listOf(composerSession(1)))).isEmpty())
        assertEquals(before,sets(WorkoutLastReferenceDomain.composer(
            generated,listOf(composerSession(1)))))
    }

    @Test fun warmupChangesDoNotShiftWorkingSetReferencePositions() {
        val next=custom(3,warmup=false)
        val references=sets(result(next))
        assertEquals(2,references.size)
        assertTrue(references.values.all{it.actual?.load==BigDecimal(135)})
    }

    @Test fun repeatedExercisesKeepDistinctStableSlots() {
        val prior=session(custom(slots=listOf("first","second")))
        val changed=prior.copy(evidence=prior.evidence.copy(visits=prior.evidence.visits.map {
            if(it.slotId=="second" && it.setType==DraftSetType.WORKING)
                it.copy(actual=it.actual?.copy(load=BigDecimal(125),reps=7)) else it
        }))
        val next=custom(3,slots=listOf("first","second"))
        val reference=sets(result(next,listOf(changed)))
        assertEquals(BigDecimal(135),reference[WorkoutReferenceKey(next.slots[0].sets[1].setKey)]?.actual?.load)
        assertEquals(BigDecimal(125),reference[WorkoutReferenceKey(next.slots[1].sets[1].setKey)]?.actual?.load)
    }

    @Test fun correctedEffectiveFactsReplaceTheOriginalActuals() {
        val prior=session()
        val corrected=prior.copy(evidence=prior.evidence.copy(visits=prior.evidence.visits.map {
            it.copy(actual=it.actual?.copy(reps=6,actualRir=3),effectiveEventId="correction-${it.setKey}")
        }))
        val reference=sets(result(sessions=listOf(corrected)))
        assertTrue(reference.values.all{it.actual?.reps==6 && it.actual?.actualRir==3})
    }

    @Test fun changedImplementAndChangedGymCannotBeCompared() {
        assertTrue(sets(result(custom(3,basis=LoadBasis.SMITH_TOTAL))).isEmpty())
        val next=custom(3)
        assertTrue(sets(result(next.copy(stamp=next.stamp.copy(gymId="other")))).isEmpty())
    }

    @Test fun amplifierFollowUpsHaveIndependentPreviousLoadsAndReps() {
        fun amplified(week:Int,kind:CustomAmplifierKind):CustomWorkoutDaySnapshot {
            val snap=custom(week,warmup=false)
            return snap.copy(slots=snap.slots.map {slot->slot.copy(sets=slot.sets.map {set->
                set.copy(prescription=set.prescription.copy(amplifier=CustomSetAmplifier(
                    kind,4,20,load(95))))
            })})
        }
        val previous=amplified(2,CustomAmplifierKind.DROP_SET)
        val next=amplified(3,CustomAmplifierKind.DROP_SET)
        val references=sets(result(next,listOf(session(previous))))
        val key=next.slots.single().sets.first().setKey
        assertEquals(BigDecimal(135),references[WorkoutReferenceKey(key)]?.actual?.load)
        assertEquals(BigDecimal(95),references[WorkoutReferenceKey(key,true)]?.actual?.load)
        assertEquals(4,references[WorkoutReferenceKey(key,true)]?.actual?.reps)
        val changed=sets(result(amplified(3,CustomAmplifierKind.MYO_REPS),listOf(session(previous))))
        assertTrue(changed.keys.none{it.followUp})
    }

    @Test fun doubleDropLastKeepsBothActualsAndDoesNotResurrectAnOlderSecondDrop() {
        fun snapshot(week:Int, rounds:Int):CustomWorkoutDaySnapshot {
            val snap=custom(week,warmup=false)
            val amp=CustomSetAmplifier(CustomAmplifierKind.DROP_SET,8,10,
                load(95),30,rounds,if(rounds==2)load(75) else null)
            return snap.copy(slots=snap.slots.map { slot ->
                slot.copy(sets=slot.sets.mapIndexed { index,set ->
                    if(index==0)set else set.copy(prescription=set.prescription.copy(amplifier=amp))
                })
            })
        }
        val prior=session(snapshot(1,2),1)
        val next=snapshot(3,2)
        val key=next.slots.single().sets.last().setKey
        val both=sets(result(next,listOf(prior)))
        assertEquals(BigDecimal(135),both.getValue(WorkoutReferenceKey(key)).actual?.load)
        assertEquals(BigDecimal(95),both.getValue(WorkoutReferenceKey(key,true,1)).actual?.load)
        assertEquals(BigDecimal(75),both.getValue(WorkoutReferenceKey(key,true,2)).actual?.load)
        val latest=result(next,listOf(prior,session(snapshot(2,1),2))) as WorkoutLastReferenceResult.Ready
        assertFalse(WorkoutReferenceKey(key,true,2) in latest.sets)
        assertTrue(latest.machineSets.keys.none { it.key==WorkoutReferenceKey(key,true,2) })
        val duplicate=prior.evidence.visits.last()
        assertEquals(WorkoutLastReferenceResult.Unavailable,result(next,listOf(prior.copy(
            evidence=prior.evidence.copy(visits=prior.evidence.visits+duplicate)))))
    }

    @Test fun myoMiniSetsRemainDistinctFromTheActivationAndOrdinaryWorkingSets() {
        fun myo(week:Int):CustomWorkoutDaySnapshot {
            val snap=custom(week,warmup=false)
            return snap.copy(slots=snap.slots.map { slot ->slot.copy(sets=slot.sets.mapIndexed { i,set ->
                if(i==0)set else set.copy(prescription=set.prescription.copy(amplifier=
                    CustomSetAmplifier(CustomAmplifierKind.MYO_REPS,4,20,followUpRounds=3)))
            }) })
        }
        val next=myo(3)
        val refs=sets(result(next,listOf(session(myo(2)))))
        val last=next.slots.single().sets.last().setKey
        assertEquals(5,refs.size)
        assertEquals(5,refs.getValue(WorkoutReferenceKey(last)).actual?.reps)
        for(stage in 1..3)
            assertEquals(4,refs.getValue(WorkoutReferenceKey(last,true,stage)).actual?.reps)
    }

    @Test fun viewingAnOldFinishedWorkoutNeverUsesAFutureCompletion() {
        val first=session(custom(1),1,"first")
        val current=session(custom(2),2,"current")
        val later=session(custom(3),3,"later")
        val references=sets(result(current.snapshot,listOf(first,current,later),"current"))
        assertTrue(references.values.all{it.workoutId=="first"})
    }

    @Test fun ambiguousOrMisorderedHistoryIsUnavailableRatherThanEmpty() {
        val first=session(custom(1),1)
        val second=session(order=2)
        assertEquals(WorkoutLastReferenceResult.Unavailable,result(sessions=listOf(second,first)))
        val duplicate=second.copy(evidence=second.evidence.copy(visits=
            second.evidence.visits+second.evidence.visits.first()))
        assertEquals(WorkoutLastReferenceResult.Unavailable,result(sessions=listOf(duplicate)))
        assertTrue(sets(result(sessions=emptyList())).isEmpty())
    }

    private val source=KotlinTrainingSourceRevision("athlete",1,"program",1,"gym",1,"catalog","rules")
    private fun composerSnapshot(id:String="current",day:Int=0)=KotlinStagedWorkout(
        source,"grant",id,day,day,ComposerSessionPurpose.HYPERTROPHY,45,
        listOf(KotlinWorkoutTarget("barbell_bench",ComposerRole.PRIMARY,
            ComposerSessionPurpose.HYPERTROPHY,"bench:work:0",DraftSetType.WORKING,
            0,load(135),RepBand(5,8),2,30,120)))
    private fun composerSession(order:Long,day:Int=0,id:String="old-$order",
        outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED)=
        ComposerReferenceSession(order,day,ComposerSessionPurpose.HYPERTROPHY,
            KotlinWorkoutHistoryEvidence(source,id,"finish-$id",2,
                listOf(KotlinHistoricalSetFact("barbell_bench","old:work:0",
                    DraftSetType.WORKING,0,load(135),RepBand(5,8),2,outcome,
                    if(outcome==KotlinJournalSetOutcome.SKIPPED)null else
                        KotlinPerformedSet(BigDecimal(130),LoadUnit.LB,6,null),
                    "corrected-$id",null)),emptyList()))

    @Test fun generatedDaysAreIndependentAndUseCorrectedActualLoads() {
        val result=sets(WorkoutLastReferenceDomain.composer(composerSnapshot(),
            listOf(composerSession(1),composerSession(2,day=3))))
        val last=result.values.single()
        assertEquals("old-1",last.workoutId)
        assertEquals(BigDecimal(130),last.actual?.load)
        assertNull(last.actual?.actualRir)
    }

    @Test fun generatedLatestSkipDoesNotBecomeAnOlderCompletedSet() {
        val last=sets(WorkoutLastReferenceDomain.composer(composerSnapshot(),
            listOf(composerSession(1),composerSession(2,outcome=KotlinJournalSetOutcome.SKIPPED))))
            .values.single()
        assertEquals("old-2",last.workoutId)
        assertEquals(KotlinJournalSetOutcome.SKIPPED,last.outcome)
        assertNull(last.actual)
    }

    @Test fun returningToMachineAAfterBRecoversAWithoutBorrowingB() {
        fun tagged(
            week:Int,order:Long,machine:String,weight:String,
            outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED
        ):CustomCoachingHistoricalSession {
            val original=session(custom(week),order,outcome=outcome)
            return original.copy(evidence=original.evidence.copy(visits=
                original.evidence.visits.map { visit ->
                    visit.copy(actual=visit.actual?.copy(
                        machineInstanceId=machine,load=BigDecimal(weight)))
                }))
        }
        val a=tagged(1,1,"A","200")
        val b=tagged(2,2,"B","140")
        val next=custom(3)
        val ref=WorkoutLastReferenceDomain.custom("current",next,listOf(a,b))
            as WorkoutLastReferenceResult.Ready
        val key=WorkoutReferenceKey(next.slots.single().sets[1].setKey)
        assertEquals("prior-2",ref.sets.getValue(key).workoutId)
        assertEquals("prior-1",ref.machineSets.getValue(
            WorkoutMachineReferenceKey(key,"A")).workoutId)
        assertEquals(BigDecimal("200"),ref.machineSets.getValue(
            WorkoutMachineReferenceKey(key,"A")).actual?.load)
        assertEquals(BigDecimal("140"),ref.machineSets.getValue(
            WorkoutMachineReferenceKey(key,"B")).actual?.load)
        assertFalse(ref.machineSets.containsKey(
            WorkoutMachineReferenceKey(key,KotlinMachineAlternativesDomain.LEGACY_MACHINE_ID)))

        // A's own latest failure blocks an older A success, even if B succeeded.
        val aFailed=tagged(3,3,"A","200",KotlinJournalSetOutcome.FAILED_ATTEMPT)
        val nextAfterFailure=custom(4)
        val failed=WorkoutLastReferenceDomain.custom("current",nextAfterFailure,
            listOf(a,b,aFailed)) as WorkoutLastReferenceResult.Ready
        val laterKey=WorkoutReferenceKey(nextAfterFailure.slots.single().sets[1].setKey)
        assertEquals(KotlinJournalSetOutcome.FAILED_ATTEMPT,
            failed.machineSets.getValue(WorkoutMachineReferenceKey(laterKey,"A")).outcome)
    }

    @Test fun realNativeSwapOnBDoesNotEraseAWhileSameStationSwapBlocksOldLast() {
        val original=session(custom(1),1).let {previous ->
            previous.copy(evidence=previous.evidence.copy(visits=
                previous.evidence.visits.map { fact ->
                    fact.copy(actual=fact.actual?.copy(
                        machineInstanceId="A",load=BigDecimal("200")))
                }))
        }
        fun swapped(machine:String?,skip:Boolean=false):
            CustomCoachingHistoricalSession {
            val source=custom(2)
            var journal=CustomWorkoutJournalDomain.begin(
                "swap-${machine ?: "unknown"}-${if(skip)"skip" else "done"}",
                source,"a".repeat(64))
            val previous=source.slots.single()
            val newSlotId="swapped-bench"
            val replacement=previous.copy(
                slotId=newSlotId,exerciseId="paused_bench",
                sets=previous.sets.map { set ->
                    set.copy(slotId=newSlotId,
                        setKey="cycle:w2:d0:${newSlotId}:s${set.ordinalInSlot}")
                })
            journal=(CustomWorkoutJournalDomain.reduce(journal,
                CustomWorkoutJournalOperation.Swap(
                    "swap-receipt",journal.revision,previous.slotId,replacement))
                as CustomWorkoutJournalDecision.Proposed).draft
            for(set in replacement.sets) {
                val target=set.prescription.load as DraftSetLoad.Verified
                journal=(CustomWorkoutJournalDomain.reduce(journal,
                    CustomWorkoutJournalOperation.Record(
                        "record-${set.ordinalInSlot}",journal.revision,
                        CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                        if(skip)KotlinJournalSetOutcome.SKIPPED
                        else KotlinJournalSetOutcome.COMPLETED,
                        if(skip)null else KotlinPerformedSet(
                            target.value,target.unit,5,null,machine)))
                    as CustomWorkoutJournalDecision.Proposed).draft
            }
            journal=(CustomWorkoutJournalDomain.reduce(journal,
                CustomWorkoutJournalOperation.Finish(
                    "finish-swap",journal.revision))
                as CustomWorkoutJournalDecision.Proposed).draft
            val evidence=(CustomWorkoutHistoryEvidenceDomain.project(journal)
                as CustomHistoryEvidenceResult.Candidate).evidence
            assertEquals(newSlotId,journal.snapshot.slots.single().slotId)
            assertTrue(evidence.visits.all{it.slotId==newSlotId})
            return CustomCoachingHistoricalSession(2,journal.snapshot,evidence)
        }

        val upcoming=custom(3)
        val key=WorkoutReferenceKey(upcoming.slots.single().sets[1].setKey)
        val machineA=WorkoutMachineReferenceKey(key,"A")
        val afterB=WorkoutLastReferenceDomain.custom(
            "current",upcoming,listOf(original,swapped("B")))
            as WorkoutLastReferenceResult.Ready
        // Generic Last still refuses the changed exercise. The A-specific
        // view alone recovers its valid, older station observation.
        assertTrue(afterB.sets.isEmpty())
        assertEquals("prior-1",afterB.machineSets.getValue(machineA).workoutId)
        assertEquals(BigDecimal("200"),afterB.machineSets.getValue(machineA).actual?.load)
        assertFalse(afterB.machineSets.keys.any{it.machineId=="B"})

        val afterA=WorkoutLastReferenceDomain.custom(
            "current",upcoming,listOf(original,swapped("A")))
            as WorkoutLastReferenceResult.Ready
        assertTrue(afterA.sets.isEmpty())
        assertTrue(afterA.machineSets.isEmpty())

        for(unknown in listOf(swapped(null),swapped("B",skip=true))) {
            val result=WorkoutLastReferenceDomain.custom(
                "current",upcoming,listOf(original,unknown))
                as WorkoutLastReferenceResult.Ready
            assertTrue(result.machineSets.isEmpty())
        }
    }

    @Test fun changedExerciseInTheSameSlotAlsoHonorsActualStationEvidence() {
        val old=session(custom(1),1).let {record ->
            record.copy(evidence=record.evidence.copy(visits=
                record.evidence.visits.map{fact ->
                    fact.copy(actual=fact.actual?.copy(machineInstanceId="A"))
                }))
        }
        fun substituted(machine:String)=session(custom(2,exercise="paused_bench"),2)
            .let {record ->record.copy(evidence=record.evidence.copy(visits=
                record.evidence.visits.map{fact ->
                    fact.copy(actual=fact.actual?.copy(machineInstanceId=machine))
                })) }
        val next=custom(3)
        val key=WorkoutMachineReferenceKey(
            WorkoutReferenceKey(next.slots.single().sets[1].setKey),"A")
        val b=WorkoutLastReferenceDomain.custom(
            "current",next,listOf(old,substituted("B")))
            as WorkoutLastReferenceResult.Ready
        assertTrue(b.sets.isEmpty())
        assertEquals("prior-1",b.machineSets.getValue(key).workoutId)
        val a=WorkoutLastReferenceDomain.custom(
            "current",next,listOf(old,substituted("A")))
            as WorkoutLastReferenceResult.Ready
        assertTrue(a.machineSets.isEmpty())
    }

    @Test fun incompleteRetainedSlotReplacementNeverRevivesOlderMachineALast() {
        // Last is display-only, but a fake recovery of old A performance
        // is still a misleading indication of the selected physical stack.
        fun at(session:CustomCoachingHistoricalSession,machine:String)=
            session.copy(evidence=session.evidence.copy(visits=
                session.evidence.visits.map { fact ->
                    fact.copy(actual=fact.actual?.copy(machineInstanceId=machine))
                }))
        val oldA=at(session(custom(1),1),"A")
        val completeB=at(session(custom(2,exercise="paused_bench"),2),"B")
        val next=custom(3)
        val key=WorkoutMachineReferenceKey(
            WorkoutReferenceKey(next.slots.single().sets[1].setKey),"A")
        fun last(s:CustomCoachingHistoricalSession)=
            WorkoutLastReferenceDomain.custom("current",next,listOf(oldA,s))
                as WorkoutLastReferenceResult.Ready
        assertEquals("prior-1",last(completeB).machineSets.getValue(key).workoutId)
        val original=completeB.evidence.visits
        val work=original.filter{it.setType==DraftSetType.WORKING}
        val bad=listOf(
            completeB.copy(evidence=completeB.evidence.copy(
                visits=original.dropLast(1))),
            completeB.copy(evidence=completeB.evidence.copy(
                visits=original.dropLast(1)+work.first())),
            completeB.copy(evidence=completeB.evidence.copy(
                visits=original.map{fact ->
                    if(fact.setKey==work.last().setKey)
                        fact.copy(prescribedReps=RepBand(6,8)) else fact
                })),
            completeB.copy(evidence=completeB.evidence.copy(
                visits=original.map{fact ->
                    if(fact.setKey==work.last().setKey)
                        fact.copy(outcome=KotlinJournalSetOutcome.SKIPPED,
                            actual=null) else fact
                })),
            completeB.copy(evidence=completeB.evidence.copy(
                visits=original.map{fact ->
                    if(fact.setKey==work.last().setKey)
                        fact.copy(stage=2) else fact
                }))
        )
        for (candidate in bad) {
            val seen=last(candidate)
            assertTrue("Unknown B visit must invalidate stale A Last: ${candidate.evidence.workoutId}",
                seen.machineSets.isEmpty())
            assertTrue(seen.sets.isEmpty())
        }
    }

    @Test fun partialRealSwapReplacementNeverKeepsOldStationLast() {
        val oldA=session(custom(1),1).let {s->
            s.copy(evidence=s.evidence.copy(visits=s.evidence.visits.map{
                it.copy(actual=it.actual?.copy(machineInstanceId="A"))
            }))
        }
        val issued=custom(2)
        var journal=CustomWorkoutJournalDomain.begin("real-swap",issued,"a".repeat(64))
        val original=issued.slots.single()
        val swappedId="swapped-bench"
        val replacement=original.copy(
            slotId=swappedId,exerciseId="paused_bench",
            sets=original.sets.map{set ->
                set.copy(slotId=swappedId,
                    setKey="cycle:w2:d0:${swappedId}:s${set.ordinalInSlot}")
            })
        journal=(CustomWorkoutJournalDomain.reduce(journal,
            CustomWorkoutJournalOperation.Swap(
                "swap-receipt",journal.revision,original.slotId,replacement))
            as CustomWorkoutJournalDecision.Proposed).draft
        for(set in replacement.sets) {
            val issuedLoad=set.prescription.load as DraftSetLoad.Verified
            journal=(CustomWorkoutJournalDomain.reduce(journal,
                CustomWorkoutJournalOperation.Record(
                    "set-${set.ordinalInSlot}",journal.revision,
                    CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.MAIN),
                    KotlinJournalSetOutcome.COMPLETED,
                    KotlinPerformedSet(issuedLoad.value,issuedLoad.unit,
                        6,null,"B")))
                as CustomWorkoutJournalDecision.Proposed).draft
        }
        journal=(CustomWorkoutJournalDomain.reduce(journal,
            CustomWorkoutJournalOperation.Finish("finish-real",journal.revision))
            as CustomWorkoutJournalDecision.Proposed).draft
        val verified=(CustomWorkoutHistoryEvidenceDomain.project(journal)
            as CustomHistoryEvidenceResult.Candidate).evidence
        val full=CustomCoachingHistoricalSession(2,journal.snapshot,verified)
        val next=custom(3)
        fun last(session:CustomCoachingHistoricalSession)=
            WorkoutLastReferenceDomain.custom("current",next,listOf(oldA,session))
                as WorkoutLastReferenceResult.Ready
        assertTrue(last(full).machineSets.keys.any{it.machineId=="A"})
        val truncated=full.copy(evidence=verified.copy(
            visits=verified.visits.dropLast(1)))
        val review=last(truncated)
        assertTrue(review.machineSets.isEmpty())
        assertTrue(review.sets.isEmpty())
    }

    @Test fun aMachineLabelOnUnloadedReplacementDoesNotAuthenticateWeightStationB() {
        val sourceA=session(custom(1),1).let {prior ->
            prior.copy(evidence=prior.evidence.copy(visits=
                prior.evidence.visits.map{fact ->
                    fact.copy(actual=fact.actual?.copy(machineInstanceId="A"))
                }))
        }
        // This is valid as a factual bodyweight observation, but a string B
        // alone is not proof that the unloaded substitute used a separately
        // calibrated weighted machine B. Do not revive older A Last.
        val unloaded=session(custom(2,exercise="paused_bench"),2).let{prior ->
            prior.copy(
                snapshot=prior.snapshot.copy(slots=prior.snapshot.slots.map{slot ->
                    slot.copy(sets=slot.sets.map{set ->
                        set.copy(prescription=set.prescription.copy(
                            load=DraftSetLoad.ExplicitBodyweight))
                    })
                }),
                evidence=prior.evidence.copy(visits=prior.evidence.visits.map{fact ->
                    fact.copy(prescribedLoad=DraftSetLoad.ExplicitBodyweight,
                        actual=fact.actual?.copy(load=null,unit=null,
                            machineInstanceId="B"))
                }))
        }
        assertTrue(unloaded.evidence.visits.all{
            KotlinSetNumberAuthorityDomain.valid(it.prescribedLoad,
                it.outcome,it.actual)
        })
        val result=WorkoutLastReferenceDomain.custom(
            "current",custom(3),listOf(sourceA,unloaded))
            as WorkoutLastReferenceResult.Ready
        assertTrue(result.sets.isEmpty())
        assertTrue(result.machineSets.isEmpty())
    }

    @Test fun anUnknownSkippedMachineBlocksStaleLastInsteadOfPromotingAnOldPr() {
        val first=session(custom(1),1)
        val tagged=first.copy(evidence=first.evidence.copy(visits=
            first.evidence.visits.map {
                it.copy(actual=it.actual?.copy(machineInstanceId="A"))
            }))
        // A skip has no actual machine ID, so the physical station is unknown.
        val skipped=session(custom(2),2,outcome=KotlinJournalSetOutcome.SKIPPED)
        val reference=WorkoutLastReferenceDomain.custom("current",custom(3),
            listOf(tagged,skipped)) as WorkoutLastReferenceResult.Ready
        assertTrue(reference.machineSets.isEmpty())
        assertTrue(reference.sets.values.all { it.outcome==KotlinJournalSetOutcome.SKIPPED })
    }

    @Test fun composerRestoresTheLastAExposureAfterANewerBSession() {
        fun tagged(order:Long,machine:String,weight:String)=
            composerSession(order).let { session ->
                session.copy(evidence=session.evidence.copy(sets=
                    session.evidence.sets.map { fact ->
                        fact.copy(actual=fact.actual?.copy(
                            load=BigDecimal(weight),machineInstanceId=machine))
                    }))
            }
        val a=tagged(1,"A","200")
        val b=tagged(2,"B","140")
        val reference=WorkoutLastReferenceDomain.composer(composerSnapshot(),
            listOf(a,b)) as WorkoutLastReferenceResult.Ready
        val key=WorkoutReferenceKey("bench:work:0")
        assertEquals("old-2",reference.sets.getValue(key).workoutId)
        assertEquals("old-1",reference.machineSets.getValue(
            WorkoutMachineReferenceKey(key,"A")).workoutId)
        assertEquals(BigDecimal("140"),reference.machineSets.getValue(
            WorkoutMachineReferenceKey(key,"B")).actual?.load)
        assertTrue(runCatching {
            (reference.machineSets as MutableMap<*,*>).clear()
        }.isFailure)
    }

    @Test fun generatedRealSwapOnBPreservesIndependentAOnly() {
        val next=composerSnapshot().let{staged ->
            staged.copy(targets=staged.targets.map{
                it.copy(load=load(135,LoadBasis.MACHINE_STACK))
            })
        }
        val first=composerSession(1).let{prior ->
            prior.copy(evidence=prior.evidence.copy(sets=
                prior.evidence.sets.map {fact ->
                    fact.copy(issuedLoad=load(135,LoadBasis.MACHINE_STACK),
                        actual=fact.actual?.copy(machineInstanceId="A"))
                }))
        }
        fun swapped(
            machine:String?,skipped:Boolean=false,
            changedGym:Boolean=false
        ):ComposerReferenceSession {
            val issued=next.copy(workoutId="swapped-workout",
                source=if(changedGym)source.copy(gymId="away") else source)
            var journal=KotlinWorkoutJournalDomain.begin(issued,
                KotlinStoredWorkoutIdentity(
                    "stored-swap","swapped-workout",issued.source,1))
            val swapId="swap-entry"
            val replacement=issued.targets.single().copy(
                exerciseId="paused_bench",
                setKey="swap:${swapId}:working:0")
            journal=(KotlinWorkoutJournalDomain.reduce(journal,
                KotlinJournalOperation.SwapExercise(
                    swapId,journal.revision,"barbell_bench",listOf(replacement)))
                as KotlinJournalDecision.Proposed).state
            journal=(KotlinWorkoutJournalDomain.reduce(journal,
                KotlinJournalOperation.RecordSet(
                    "record-new",journal.revision,replacement.setKey,
                    if(skipped)KotlinJournalSetOutcome.SKIPPED
                    else KotlinJournalSetOutcome.COMPLETED,
                    if(skipped)null else KotlinPerformedSet(
                        BigDecimal(135),LoadUnit.LB,6,null,machine)))
                as KotlinJournalDecision.Proposed).state
            journal=(KotlinWorkoutJournalDomain.reduce(journal,
                KotlinJournalOperation.Finish("finish-new",journal.revision))
                as KotlinJournalDecision.Proposed).state
            val history=(KotlinWorkoutHistoryEvidenceDomain.project(journal)
                as KotlinHistoryProjectionResult.Candidate).evidence
            assertEquals("paused_bench",history.sets.single().exerciseId)
            assertEquals(replacement.setKey,history.sets.single().setKey)
            return ComposerReferenceSession(2,0,
                ComposerSessionPurpose.HYPERTROPHY,history)
        }
        val key=WorkoutReferenceKey(next.targets.single().setKey)
        val machineA=WorkoutMachineReferenceKey(key,"A")
        val afterB=WorkoutLastReferenceDomain.composer(
            next,listOf(first,swapped("B")))
            as WorkoutLastReferenceResult.Ready
        assertTrue(afterB.sets.isEmpty())
        assertEquals("old-1",afterB.machineSets.getValue(machineA).workoutId)
        assertEquals(BigDecimal(130),afterB.machineSets.getValue(machineA).actual?.load)
        assertFalse(afterB.machineSets.keys.any{it.machineId=="B"})

        val afterA=WorkoutLastReferenceDomain.composer(
            next,listOf(first,swapped("A")))
            as WorkoutLastReferenceResult.Ready
        assertTrue(afterA.sets.isEmpty())
        assertTrue(afterA.machineSets.isEmpty())
        for(other in listOf(swapped(null),swapped("B",skipped=true),
            swapped("B",changedGym=true))) {
            val reviewed=WorkoutLastReferenceDomain.composer(
                next,listOf(first,other)) as WorkoutLastReferenceResult.Ready
            assertTrue(reviewed.sets.isEmpty())
            assertTrue(reviewed.machineSets.isEmpty())
        }
    }

    @Test fun unknownGeneratedDayCannotSupplyAReference() {
        assertTrue(sets(WorkoutLastReferenceDomain.composer(composerSnapshot(),
            listOf(composerSession(1,day=-1)))).isEmpty())
    }
}
