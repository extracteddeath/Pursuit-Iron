package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class MeasuredWorkoutFocusDomainTest {
    private fun parent():MeasuredWorkoutPlan {
        val ids=listOf("plank","superman","side-plank","plank")
        return MeasuredWorkoutPlan("focus-workout","gym-1",1,
            ids.mapIndexed{i,id->
                MeasuredExerciseSetPlan("focus-workout","set-${i+1}",
                    id,"gym-1",1,"floor",30,
                    if(id=="side-plank")
                        setOf(MeasuredSide.LEFT,MeasuredSide.RIGHT)
                    else setOf(MeasuredSide.BOTH),emptySet())
            })
    }
    private fun actual(plan:MeasuredExerciseSetPlan)=
        MeasuredExerciseActual(plan.physicalSetupId,
            plan.sides.sortedBy{it.ordinal}.map{
                MeasuredSideResult(it,34)})

    private fun record(
        before:MeasuredWorkoutJournal,setKey:String,event:String,
        correction:String?=null,outcome:MeasuredSetOutcome=MeasuredSetOutcome.COMPLETED
    ):MeasuredWorkoutJournal {
        val plan=before.approvedPlan.sets.first{it.setKey==setKey}
        val write=MeasuredWorkoutOperation.Record(
            event,before.revision,setKey,outcome,
            if(outcome==MeasuredSetOutcome.SKIPPED)null else actual(plan),
            correctsEventId=correction)
        val candidate=MeasuredWorkoutJournalDomain.reduce(before,write)
        assertTrue(candidate.toString(),
            candidate is MeasuredWorkoutDecision.Proposed)
        return (candidate as MeasuredWorkoutDecision.Proposed).journal
    }

    @Test fun resumedFocusChoosesFirstIncompleteAndRespectsExplicitSetSelection() {
        val start=checkNotNull(MeasuredWorkoutJournalDomain.begin(parent()))
        val first=MeasuredWorkoutFocusDomain.review(start,null)
        assertEquals("set-1",first?.selectedSetKey)
        assertEquals(4,first?.totalSets)
        assertEquals(4,first?.remainingSets)
        assertEquals("set-2",first?.nextUnrecordedKey)
        val afterFirst=record(start,"set-1","record-one")
        val resumed=MeasuredWorkoutFocusDomain.review(afterFirst,null)
        assertEquals("set-2",resumed?.selectedSetKey)
        assertEquals(1,resumed?.recordedSets)
        val selectedOld=MeasuredWorkoutFocusDomain.review(afterFirst,"set-1")
        assertTrue(checkNotNull(selectedOld).selectedRecorded)
        assertEquals("set-2",selectedOld.nextUnrecordedKey)
        val unknown=MeasuredWorkoutFocusDomain.review(afterFirst,"forged-set")
        assertEquals("set-2",unknown?.selectedSetKey)
        assertEquals("superman",unknown?.selectedExerciseId)
    }

    @Test fun durableRecordAdvancesOnlyWhenAnUnrecordedSetIsTrulyCommitted() {
        val start=checkNotNull(MeasuredWorkoutJournalDomain.begin(parent()))
        val one=record(start,"set-1","record-one")
        assertEquals("set-2",MeasuredWorkoutFocusDomain.afterDurableRecord(
            start,one,"set-1"))
        val four=record(one,"set-4","record-four")
        assertEquals("set-2",MeasuredWorkoutFocusDomain.afterDurableRecord(
            one,four,"set-4"))
        val three=record(four,"set-3","skip-three",
            outcome=MeasuredSetOutcome.SKIPPED)
        assertEquals("set-2",MeasuredWorkoutFocusDomain.afterDurableRecord(
            four,three,"set-3"))
        val two=record(three,"set-2","record-two")
        assertEquals("set-2",MeasuredWorkoutFocusDomain.afterDurableRecord(
            three,two,"set-2"))
        assertTrue(checkNotNull(MeasuredWorkoutFocusDomain.review(two,null))
            .readyToFinish)
        assertEquals(0,MeasuredWorkoutFocusDomain.review(two,null)
            ?.remainingSets)
        assertNull(MeasuredWorkoutFocusDomain.review(two,null)
            ?.nextUnrecordedKey)
        assertTrue(two.canProposeFinish)
        assertFalse(two.mayPublishHistory)
    }

    @Test fun correctionAndForgedReceiptsNeverJumpToTheWrongSet() {
        val start=checkNotNull(MeasuredWorkoutJournalDomain.begin(parent()))
        val one=record(start,"set-1","record-one")
        val corrected=record(one,"set-1","correction-one","record-one")
        assertEquals("set-1",MeasuredWorkoutFocusDomain.afterDurableRecord(
            one,corrected,"set-1"))
        assertNull(MeasuredWorkoutFocusDomain.afterDurableRecord(
            one,corrected,"set-2"))
        assertNull(MeasuredWorkoutFocusDomain.afterDurableRecord(
            one,one,"set-1"))
        val other=parent().copy(workoutId="foreign")
        assertNull(MeasuredWorkoutFocusDomain.afterDurableRecord(one,
            corrected.copy(approvedPlan=other),"set-1"))
        assertNull(MeasuredWorkoutFocusDomain.afterDurableRecord(one,
            corrected.copy(revision=corrected.revision+1),"set-1"))
        assertNull(MeasuredWorkoutFocusDomain.afterDurableRecord(one,
            corrected.copy(receipts=corrected.receipts.reversed()),"set-1"))
    }
}
