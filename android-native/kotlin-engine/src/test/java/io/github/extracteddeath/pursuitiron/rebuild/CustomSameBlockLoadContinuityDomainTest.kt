package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class CustomSameBlockLoadContinuityDomainTest {
    private fun n(x:Int)=BigDecimal(x)
    private val stock=VerifiedLoadStock.Totals(LoadUnit.LB,LoadBasis.MACHINE_STACK,
        listOf(20,25,30,35,40,45,50).map(::n))
    private fun snapshot(week:Int,loads:List<Int>,
        day:Int=0,block:String="build",gymRev:Long=2L,
        goal:Int?=2):CustomWorkoutDaySnapshot {
        val slot=CustomWorkoutSlotSnapshot("main","chest_press","chest","machine",
            ComposerRole.ACCESSORY,null,null,true,"",
            loads.mapIndexed{index,load->
                CustomWorkoutSetSnapshot("cycle:w$week:d$day:main:s${index+1}",
                    "main",index+1,CustomAuthoredSet(DraftSetType.WORKING,
                        DraftSetLoad.Verified(LoadUnit.LB,LoadBasis.MACHINE_STACK,n(load)),
                        RepBand(5,8),goal,60,120))
            })
        return CustomWorkoutDaySnapshot(
            CustomWorkoutSourceStamp("athlete-1","profile-1","cycle",3,"gym-1",gymRev),
            week,week,block,TrainingPhase.HYPERTROPHY,CustomBlockPurpose.TRAINING,
            CustomSplitIdentity.Freeform("ULPPL"),day,"Upper",
            ComposerSessionPurpose.HYPERTROPHY,listOf(slot),240,120)
    }
    private fun completed(snap:CustomWorkoutDaySnapshot=snapshot(2,listOf(25,30)),
        order:Long=1,actualRir:List<Int?> = listOf(null,2),
        reps:List<Int> = listOf(7,7),failed:Boolean=false
    ):CustomCoachingHistoricalSession {
        val visits=snap.slots.single().sets.mapIndexed{i,set->
            val issued=set.prescription.load as DraftSetLoad.Verified
            CustomHistoricalVisitFact(set.slotId,snap.slots.single().exerciseId,set.setKey,
                CustomWorkoutVisitPart.MAIN,DraftSetType.WORKING,
                issued,set.prescription.reps,set.prescription.targetRir,null,
                if(failed)KotlinJournalSetOutcome.FAILED_ATTEMPT else KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(issued.value,LoadUnit.LB,
                    if(failed)0 else reps[i],if(failed)null else actualRir[i]),
                "finish-${snap.absoluteWeek}-$i")
        }
        return CustomCoachingHistoricalSession(order,snap,
            CustomWorkoutHistoricalEvidence("athlete-1",
                "workout-${snap.absoluteWeek}-${snap.weekday}","cycle",3,
                "finish-${snap.absoluteWeek}",3,visits))
    }
    private fun review(
        next:CustomWorkoutDaySnapshot=snapshot(3,listOf(40,45)),
        history:List<CustomCoachingHistoricalSession> = listOf(completed()),
        readiness:CoachReadiness=CoachReadiness.NORMAL,
        restricted:Boolean=false,physical:VerifiedLoadStock?=stock
    )=CustomSameBlockLoadContinuityDomain.review(
        CustomBlockBaselineRequest(next,"main",history,physical),
        readiness,restricted)

    @Test fun lowerIssuedWeightsAreVisibleButNeverSilentlyIssued() {
        val result=review()
        assertEquals(CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT,result.status)
        assertEquals(listOf(n(25),n(30)),result.sets.map{it.proposedLoad})
        assertEquals(listOf(n(40),n(45)),result.sets.map{it.authoredLoad})
        assertEquals(listOf(n(25),n(30)),checkNotNull(result.proposedWorkout)
            .slots.single().sets.map{(it.prescription.load as DraftSetLoad.Verified).value})
        assertEquals("workout-2-0",result.previousWorkoutId)
        assertEquals(1,result.assumedGoalRirSetCount)
        assertNull(result.sets.first().previousActualRir)
        assertEquals(ProgressionEffortSource.ASSUMED_GOAL,result.sets.first().effortSource)
        assertTrue(result.changesAuthoredLoads)
        assertFalse(result.mayApplyAutomatically)
        assertFalse(result.mayIssueWorkout)
        assertFalse(result.mayEditCycle)
        assertFalse(result.mayModifyHistory)
        assertTrue(runCatching{(result.sets as MutableList<CustomSameBlockLoadSet>).clear()}.isFailure)
    }

    @Test fun missingEffortUsesOnlyThePriorIssuedGoalAndNeverInventsActuals() {
        val history=completed(actualRir=listOf(null,null))
        val result=review(history=listOf(history))
        assertEquals(CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT,result.status)
        assertEquals(2,result.assumedGoalRirSetCount)
        assertTrue(result.sets.all{it.previousActualRir==null})
        assertTrue(history.evidence.visits.all{it.actual?.actualRir==null})
        val unknown=completed(snap=snapshot(2,listOf(25,30),goal=null),
            actualRir=listOf(null,null))
        assertEquals(CustomSameBlockLoadStatus.EFFORT_UNAVAILABLE,
            review(history=listOf(unknown)).status)
        assertEquals(CustomSameBlockLoadStatus.NEAR_FAILURE,
            review(history=listOf(completed(actualRir=listOf(0,2)))).status)
    }

    @Test fun lowerWeightsStayPerSetAndNeverPromoteHighWorkingSets() {
        val next=snapshot(3,listOf(20,45))
        val result=review(next=next)
        assertEquals(CustomSameBlockLoadStatus.REVIEW_LOWER_REPEAT,result.status)
        assertEquals(listOf(n(20),n(30)),result.sets.map{it.proposedLoad})
        assertEquals(CustomSameBlockLoadStatus.NO_LOWER_VERIFIED_BASELINE,
            review(next=snapshot(3,listOf(20,25))).status)
    }

    @Test fun missingWeekAndMostRecentFailureCannotCherryPickOldSuccess() {
        assertEquals(CustomSameBlockLoadStatus.NO_IMMEDIATELY_PREVIOUS_DAY,
            review(next=snapshot(4,listOf(40,45))).status)
        val older=completed(snapshot(1,listOf(25,30)),order=1)
        val failed=completed(order=2,failed=true)
        assertEquals(CustomSameBlockLoadStatus.PREVIOUS_WORKOUT_INCOMPLETE,
            review(history=listOf(older,failed)).status)
        assertEquals(CustomSameBlockLoadStatus.TARGET_ALREADY_TRAINED,
            review(history=listOf(completed(snapshot(3,listOf(25,30))))).status)
    }

    @Test fun changedDaySourceAndPrescriptionCannotBorrowThePriorLoad() {
        assertEquals(CustomSameBlockLoadStatus.NO_IMMEDIATELY_PREVIOUS_DAY,
            review(next=snapshot(3,listOf(40,45),day=2)).status)
        assertEquals(CustomSameBlockLoadStatus.CHANGED_CONTEXT,
            review(next=snapshot(3,listOf(40,45),gymRev=3)).status)
        assertEquals(CustomSameBlockLoadStatus.CHANGED_CONTEXT,
            review(next=snapshot(3,listOf(40,45),block="strength")).status)
        val changed=snapshot(3,listOf(40,45)).let{s->
            s.copy(slots=s.slots.map{slot->slot.copy(sets=slot.sets.map{set->
                set.copy(prescription=set.prescription.copy(reps=RepBand(8,12)))
            })})}
        assertEquals(CustomSameBlockLoadStatus.CHANGED_RECIPE,review(next=changed).status)
    }

    @Test fun gymRestrictionsAndPhysicalInventoryRefuseUnapprovedLoads() {
        assertEquals(CustomSameBlockLoadStatus.RESTRICTED_OR_LOW_READINESS,
            review(readiness=CoachReadiness.LOW).status)
        assertEquals(CustomSameBlockLoadStatus.RESTRICTED_OR_LOW_READINESS,
            review(restricted=true).status)
        assertEquals(CustomSameBlockLoadStatus.EQUIPMENT_UNVERIFIED,
            review(physical=null).status)
        assertEquals(CustomSameBlockLoadStatus.EQUIPMENT_UNVERIFIED,
            review(next=snapshot(3,listOf(42,45))).status)
        assertEquals(CustomSameBlockLoadStatus.NOT_LATER_TRAINING_WEEK,
            review(next=snapshot(1,listOf(40,45))).status)
    }
}
