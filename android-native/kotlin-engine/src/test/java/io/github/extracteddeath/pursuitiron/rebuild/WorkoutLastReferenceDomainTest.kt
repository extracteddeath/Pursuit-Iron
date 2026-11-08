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
                val followLoad=amplifier.followUpLoad as? DraftSetLoad.Verified ?:target
                journal=(CustomWorkoutJournalDomain.reduce(journal,
                    CustomWorkoutJournalOperation.Record("follow-${set.setKey}",journal.revision,
                        CustomWorkoutVisitKey(set.setKey,CustomWorkoutVisitPart.FOLLOW_UP),
                        outcome,KotlinPerformedSet(followLoad.value,followLoad.unit,
                            amplifier.followUpReps,null),null))
                    as CustomWorkoutJournalDecision.Proposed).draft
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

    @Test fun unknownGeneratedDayCannotSupplyAReference() {
        assertTrue(sets(WorkoutLastReferenceDomain.composer(composerSnapshot(),
            listOf(composerSession(1,day=-1)))).isEmpty())
    }
}
