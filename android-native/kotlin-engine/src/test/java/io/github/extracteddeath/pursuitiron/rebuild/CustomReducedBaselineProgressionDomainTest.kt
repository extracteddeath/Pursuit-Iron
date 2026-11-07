package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomReducedBaselineProgressionDomainTest {
    private fun n(v:Int)=BigDecimal(v)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf(10,20,25,30,35,40).map(::n))
    private fun snapshot(week:Int,loads:List<Int> = listOf(20,20),
        day:Int=0,goal:Int?=3):CustomWorkoutDaySnapshot {
        val slot=CustomWorkoutSlotSnapshot("bench","chest_press","chest","machine",
            ComposerRole.PRIMARY,null,null,true,"Keep steady",
            loads.mapIndexed{i,v->CustomWorkoutSetSnapshot("cycle:w$week:d$day:bench:s${i+1}",
                "bench",i+1,CustomAuthoredSet(DraftSetType.WORKING,
                    DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n(v)),
                    RepBand(5,8),goal,50,120))})
        return CustomWorkoutDaySnapshot(CustomWorkoutSourceStamp("athlete","profile-1",
            "cycle",2,"gym",1),week,week,"build",TrainingPhase.STRENGTH,
            CustomBlockPurpose.TRAINING,CustomSplitIdentity.Freeform("ULPPL"),day,
            "Upper",ComposerSessionPurpose.STRENGTH_SKILL,listOf(slot),340,120)
    }
    private fun finished(s:CustomWorkoutDaySnapshot=snapshot(2),reps:Int=8,
        rir:Int?=null,outcome:KotlinJournalSetOutcome=KotlinJournalSetOutcome.COMPLETED,
        order:Long=s.absoluteWeek.toLong()):CustomCoachingHistoricalSession {
        val facts=s.slots.single().sets.map{set->
            val prescribed=set.prescription
            val load=prescribed.load as DraftSetLoad.Verified
            CustomHistoricalVisitFact(set.slotId,s.slots.single().exerciseId,set.setKey,
                CustomWorkoutVisitPart.MAIN,prescribed.type,load,prescribed.reps,
                prescribed.targetRir,null,outcome,
                if(outcome==KotlinJournalSetOutcome.SKIPPED)null else KotlinPerformedSet(
                    load.value,load.unit,if(outcome==KotlinJournalSetOutcome.FAILED_ATTEMPT)0 else reps,rir),
                "observed-${s.absoluteWeek}-${set.ordinalInSlot}")
        }
        return CustomCoachingHistoricalSession(order,s,CustomWorkoutHistoricalEvidence(
            "athlete","workout-${s.absoluteWeek}-${s.weekday}","cycle",2,
            "finish-${s.absoluteWeek}",facts.size.toLong()+1,facts))
    }
    private fun review(next:CustomWorkoutDaySnapshot=snapshot(3,listOf(30,30)),
        history:List<CustomCoachingHistoricalSession> = listOf(finished(snapshot(1)),finished()),
        physical:VerifiedLoadStock?=stock,limit:BigDecimal=n(5),
        readiness:CoachReadiness=CoachReadiness.NORMAL,restricted:Boolean=false)=
        CustomReducedBaselineProgressionDomain.review(CustomNextWorkoutCoachingRequest(
            next,"bench",history,physical,limit,readiness,restricted))
    private fun denied(r:CustomReducedBaselineProgressionReview) {
        assertNotEquals(CustomReducedBaselineProgressionStatus.READY_FOR_REVIEW,r.status)
        assertNull(r.amendment);assertTrue(r.sets.isEmpty())
    }

    @Test fun twoCeilingWorkoutsEarnOneExactStepFromTheIssuedBaseline() {
        val next=snapshot(3,listOf(30,30));val history=listOf(finished(snapshot(1)),finished())
        val before=history.toString();val result=review(next,history)
        assertEquals(CustomReducedBaselineProgressionStatus.READY_FOR_REVIEW,result.status)
        assertEquals(listOf("workout-1-0","workout-2-0"),result.progression!!.comparedWorkoutIds)
        assertEquals(listOf(n(20),n(20)),result.sets.map{it.baselineLoad})
        assertEquals(listOf(n(25),n(25)),result.sets.map{it.proposedLoad})
        assertEquals(listOf(n(30),n(30)),result.sets.map{it.authoredLoad})
        val amended=result.amendment!!.amended
        assertEquals(next,amended.copy(slots=next.slots))
        assertEquals(next.slots.single(),amended.slots.single().copy(sets=next.slots.single().sets))
        amended.slots.single().sets.zip(next.slots.single().sets).forEach{(a,b)->
            assertEquals(b,a.copy(prescription=a.prescription.copy(load=b.prescription.load)))
        }
        assertEquals(before,history.toString())
        assertFalse(result.mayIssueWorkout);assertFalse(result.mayEditCycle);assertFalse(result.mayModifyHistory)
        assertTrue(runCatching{(result.sets as MutableList<CustomReducedBaselineLoadSet>).clear()}.isFailure)
    }
    @Test fun theEarnedStepCanExactlyReachTheAuthoredWeightWithoutBecomingAManualReturn() {
        val next=snapshot(3,listOf(25,25));val r=review(next)
        assertEquals(CustomReducedBaselineProgressionStatus.READY_FOR_REVIEW,r.status)
        assertEquals(next,r.amendment!!.amended)
        assertEquals(CustomNextWorkoutCoachingStatus.READY_FOR_LOAD_REVIEW,r.progression!!.status)
        assertEquals(listOf(n(20),n(20)),r.baseline.sets.map{it.proposedLoad})
    }
    @Test fun missingActualEffortUsesEachOriginalGoalAndLeavesActualHistoryNull() {
        val history=listOf(finished(snapshot(1)),finished())
        val r=review(history=history)
        assertEquals(4,r.progression!!.assumedGoalRirSetCount)
        assertTrue(history.flatMap{it.evidence.visits}.all{it.actual!!.actualRir==null})
        assertTrue(r.explanation.contains("goal",ignoreCase=true))
        denied(review(next=snapshot(3,listOf(30,30),goal=null),history=listOf(
            finished(snapshot(1,goal=null)),finished(snapshot(2,goal=null)))))
    }
    @Test fun actualNearFailureOverridesAnOptimisticOriginalGoalInEitherWorkout() {
        for(rir in listOf(0,1)) {
            denied(review(history=listOf(finished(snapshot(1),rir=rir),finished())))
            denied(review(history=listOf(finished(snapshot(1)),finished(rir=rir))))
        }
    }
    @Test fun bothMostRecentSameDayWorkoutsMustReachTheCeiling() {
        val priorMiss=review(history=listOf(finished(snapshot(1),reps=7),finished()))
        assertEquals(CustomReducedBaselineProgressionStatus.PROGRESSION_NOT_EARNED,priorMiss.status)
        denied(priorMiss)
        val lastMiss=review(history=listOf(finished(snapshot(1)),finished(reps=7)))
        assertEquals(CustomNextWorkoutCoachingStatus.BUILD_REPS,lastMiss.progression!!.status)
        denied(lastMiss)
    }
    @Test fun failedSkippedAndBelowRangeVisitsCannotBeCherryPickedAway() {
        for(outcome in listOf(KotlinJournalSetOutcome.SKIPPED,KotlinJournalSetOutcome.FAILED_ATTEMPT)) {
            denied(review(history=listOf(finished(snapshot(1),outcome=outcome),finished())))
            denied(review(history=listOf(finished(snapshot(1)),finished(outcome=outcome))))
        }
        denied(review(history=listOf(finished(snapshot(1)),finished(reps=4))))
    }
    @Test fun oneWorkoutOrAnInterveningMissingWeekCannotEarnAnIncrease() {
        assertEquals(CustomReducedBaselineProgressionStatus.TWO_ADJACENT_WORKOUTS_REQUIRED,
            review(history=listOf(finished())).status)
        denied(review(next=snapshot(4,listOf(30,30)),history=listOf(
            finished(snapshot(1)),finished(snapshot(3)))))
        denied(review(next=snapshot(4,listOf(30,30))))
    }
    @Test fun theOtherTrainingDayCannotSupplyTheSecondCeilingPerformance() {
        val wrongDay=finished(snapshot(1,day=2))
        denied(review(history=listOf(wrongDay,finished())))
        val otherDay=finished(snapshot(2,day=2),order=3)
        val correct=review(history=listOf(finished(snapshot(1)),finished(),otherDay))
        assertEquals(listOf("workout-1-0","workout-2-0"),correct.progression!!.comparedWorkoutIds)
    }
    @Test fun duplicateScheduledDaysAndOutOfOrderOrForeignHistoryRefuse() {
        val a=finished(snapshot(1));val b=finished()
        val duplicate=a.copy(completionOrder=2,evidence=a.evidence.copy(workoutId="duplicate"))
        denied(review(history=listOf(a,duplicate,b.copy(completionOrder=3))))
        denied(review(history=listOf(b,a)))
        denied(review(history=listOf(a.copy(evidence=a.evidence.copy(athleteId="foreign")),b)))
        denied(review(history=listOf(a,b,finished(snapshot(3)))))
    }
    @Test fun mixedOrDifferentIssuedLoadsCannotBecomeOneAllSetsIncrease() {
        denied(review(history=listOf(finished(snapshot(1,listOf(20,25))),finished(snapshot(2,listOf(20,25))))))
        denied(review(history=listOf(finished(snapshot(1,listOf(25,25))),finished())))
        val mismatched=finished().let{s->s.copy(evidence=s.evidence.copy(visits=s.evidence.visits.map{
            it.copy(actual=it.actual!!.copy(load=n(25)))
        }))}
        denied(review(history=listOf(finished(snapshot(1)),mismatched)))
    }
    @Test fun changedVariantAttachmentRestOrRepRecipeInvalidatesEarlierEvidence() {
        val original=snapshot(1);val slot=original.slots.single()
        val alternatives=listOf(slot.copy(variantId="different"),slot.copy(attachmentId="rope"),
            slot.copy(sets=slot.sets.map{it.copy(prescription=it.prescription.copy(restSeconds=60))}),
            slot.copy(sets=slot.sets.map{it.copy(prescription=it.prescription.copy(reps=RepBand(6,10)))}))
        alternatives.forEach{changed->denied(review(history=listOf(
            finished(original.copy(slots=listOf(changed))),finished())))}
        denied(review(next=snapshot(3,listOf(30,30)).let{
            it.copy(stamp=it.stamp.copy(gymCatalogSourceRevision=2))}))
    }
    @Test fun inventoryUserStepLimitAndReadinessStillControlTheEarnedOption() {
        denied(review(physical=null));denied(review(limit=n(4)))
        denied(review(limit=BigDecimal.ZERO));denied(review(readiness=CoachReadiness.LOW))
        denied(review(restricted=true));denied(review(next=snapshot(3,listOf(27,27))))
        assertEquals(CustomReducedBaselineProgressionStatus.CANDIDATE_EXCEEDS_AUTHORED_LOAD,
            review(next=snapshot(3,listOf(20,25))).status)
    }
}
