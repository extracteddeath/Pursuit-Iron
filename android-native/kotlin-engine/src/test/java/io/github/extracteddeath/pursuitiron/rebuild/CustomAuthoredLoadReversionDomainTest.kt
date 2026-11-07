package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomAuthoredLoadReversionDomainTest {
    private fun n(value:Int)=BigDecimal(value)
    private fun snapshot(week:Int,load:Int,weekday:Int=0,rev:Long=2L,
        phase:TrainingPhase=TrainingPhase.HYPERTROPHY):CustomWorkoutDaySnapshot {
        val slot=CustomWorkoutSlotSnapshot("main","chest_press","chest","machine",
            ComposerRole.ACCESSORY,null,null,true,"",listOf(
                CustomWorkoutSetSnapshot("cycle:w$week:d$weekday:main:s1","main",1,
                    CustomAuthoredSet(DraftSetType.WORKING,
                        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n(load)),
                        RepBand(5,8),2,60,120))))
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","profile-1","cycle",3,"gym-1",rev),
            week,week,"build",phase,CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("ULPPL"),weekday,"Upper",
            ComposerSessionPurpose.HYPERTROPHY,listOf(slot),240,120)
    }
    private fun completed(s:CustomWorkoutDaySnapshot=snapshot(2,20),order:Long=1,
        failed:Boolean=false,rir:Int?=null,id:String="finished-${s.absoluteWeek}-${s.weekday}"
    ):CustomCoachingHistoricalSession {
        val set=s.slots.single().sets.single()
        val load=set.prescription.load as DraftSetLoad.Verified
        val receipt=CustomHistoricalVisitFact("main","chest_press",set.setKey,
            CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,load,
            set.prescription.reps,set.prescription.targetRir,null,
            if(failed)KotlinJournalSetOutcome.FAILED_ATTEMPT
                else KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(load.value,load.unit,if(failed)0 else 8,rir),
            "finish-${s.absoluteWeek}")
        return CustomCoachingHistoricalSession(order,s,
            CustomWorkoutHistoricalEvidence("athlete-1",id,"cycle",3,
                "finish-${s.absoluteWeek}",3,listOf(receipt)))
    }
    private fun review(next:CustomWorkoutDaySnapshot=snapshot(3,25),
        history:List<CustomCoachingHistoricalSession> = listOf(completed())
    )=CustomAuthoredLoadReversionDomain.review(
        CustomBlockBaselineRequest(next,"main",history,null))

    @Test fun detectsHigherAuthoredLoadWithoutGrantingAnIncrease() {
        val actual=review()
        assertEquals(CustomAuthoredLoadReversionStatus.CONFIRM_HIGHER_AUTHORED_LOAD,
            actual.status)
        assertEquals("finished-2-0",actual.previousWorkoutId)
        assertEquals(listOf(n(20)),actual.higherSets.map{it.lastIssuedLoad})
        assertEquals(listOf(n(25)),actual.higherSets.map{it.authoredLoad})
        assertTrue(actual.requiresAthleteReview)
        assertFalse(actual.mayApplyAutomatically)
        assertFalse(actual.mayIssueWorkout)
        assertFalse(actual.mayEditCycle)
        assertTrue(runCatching{(actual.higherSets as MutableList<CustomAuthoredLoadReversionSet>)
            .clear()}.isFailure)
    }

    @Test fun nearFailureAndFailedPreviousDayStillExposeReversion() {
        assertTrue(review(history=listOf(completed(rir=0))).requiresAthleteReview)
        assertTrue(review(history=listOf(completed(failed=true))).requiresAthleteReview)
        assertTrue(review(history=listOf(completed(rir=null))).requiresAthleteReview)
    }

    @Test fun noSilentPromotionFromOlderGoodSessionOrAnotherWeekday() {
        val older=completed(snapshot(1,20),order=1)
        val latest=completed(snapshot(2,30),order=2)
        assertEquals(CustomAuthoredLoadReversionStatus.NO_HIGHER_AUTHORED_LOAD,
            review(history=listOf(older,latest)).status)
        assertEquals(CustomAuthoredLoadReversionStatus.NO_IMMEDIATELY_PREVIOUS_DAY,
            review(next=snapshot(4,25)).status)
        val push=completed(snapshot(2,20,weekday=2),order=2)
        assertTrue(review(history=listOf(completed(),push)).requiresAthleteReview)
    }

    @Test fun ambiguousAndStaleHistoryCannotApproveDefaultWorkout() {
        val first=completed()
        val duplicate=completed(order=2,id="another-completion")
        assertEquals(CustomAuthoredLoadReversionStatus.INVALID_HISTORY,
            review(history=listOf(first,duplicate)).status)
        assertEquals(CustomAuthoredLoadReversionStatus.TARGET_ALREADY_TRAINED,
            review(history=listOf(completed(snapshot(3,20)))).status)
    }

    @Test fun sourceAndExerciseChangesAreNotMistakenForSameDayRecovery() {
        assertEquals(CustomAuthoredLoadReversionStatus.CHANGED_CONTEXT,
            review(next=snapshot(3,25,rev=3)).status)
        assertEquals(CustomAuthoredLoadReversionStatus.CHANGED_CONTEXT,
            review(next=snapshot(3,25,phase=TrainingPhase.STRENGTH)).status)
        assertEquals(CustomAuthoredLoadReversionStatus.NO_IMMEDIATELY_PREVIOUS_DAY,
            review(next=snapshot(3,25,weekday=2)).status)
    }
}
