package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.time.LocalDate
import java.time.ZoneId
import org.junit.Assert.*
import org.junit.Test

/** Boundary arithmetic never trusts a screen's "next phase" label. */
class NativeFutureBlockBoundaryDomainTest {
    private fun cycle()=CustomTrainingCycle(
        "cycle-1",2,"athlete-1",AthleteTrainingGoal.HYPERTROPHY,
        "Custom multiblock",listOf(
            CustomTrainingBlock("base","Base",TrainingPhase.HYPERTROPHY,
                CustomBlockPurpose.TRAINING,2,
                CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),
                listOf(0,2,4).map{day->CustomTrainingDay(
                    day,"Base $day",ComposerSessionPurpose.HYPERTROPHY,
                    emptyList())}),
            CustomTrainingBlock("build","Build",TrainingPhase.STRENGTH,
                CustomBlockPurpose.TRAINING,3,
                CustomSplitIdentity.Named(ComposerSplit.UPPER_LOWER),
                listOf(0,1,2,4).map{day->CustomTrainingDay(
                    day,"Build $day",ComposerSessionPurpose.STRENGTH_SKILL,
                    emptyList())})
        ))
    private val timezoneId="America/New_York"
    private fun at(week:Int,day:Int):Long=LocalDate.of(2026,11,2)
        .plusWeeks(week.toLong()-1L).plusDays(day.toLong())
        .atTime(12,0).atZone(ZoneId.of(timezoneId))
        .toInstant().toEpochMilli()
    private fun done(block:String,week:Int,day:Int,cycleId:String="cycle-1",
        allMainComplete:Boolean=true,
        completedAtEpochMillis:Long?=at(week,day)
    )=NativeVerifiedCustomBlockCompletion(
            cycleId,block,week,day,allMainComplete,completedAtEpochMillis)
    private fun checked(
        cycle:CustomTrainingCycle,
        targetBlockId:String,
        completions:List<NativeVerifiedCustomBlockCompletion>,
        manifestRevision:Long
    )=NativeFutureBlockBoundaryDomain.check(
        cycle,targetBlockId,completions,manifestRevision,timezoneId)
    private fun earlier()=(1..2).flatMap{week->
        listOf(0,2,4).map{day->done("base",week,day)}
    }
    @Test fun completePriorBlockAllowsReviewButNeverAuthorizesActivation() {
        val checked=checked(
            cycle(),"build",earlier(),7)
        assertEquals(NativeFutureActivationReview.BoundaryReviewed(
            "cycle-1","build",3,2,6,7),checked)
        val reviewed=checked as NativeFutureActivationReview.BoundaryReviewed
        assertFalse(reviewed.mayActivate)
        assertFalse(reviewed.mayIssueWorkout)
        assertFalse(reviewed.mayChangeSchedule)
    }
    @Test fun firstBlockIsNeverProspectiveActivationTarget() {
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.NOT_A_FUTURE_BLOCK),
            checked(cycle(),"base",earlier(),7))
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.NOT_A_FUTURE_BLOCK),
            checked(cycle(),"unknown",earlier(),7))
    }
    @Test fun incompletePriorSessionsDoNotClaimTrainingBoundary() {
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.INCOMPLETE_HISTORY),
            checked(
                cycle(),"build",earlier().dropLast(1),7))
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.INCOMPLETE_HISTORY),
            checked(
                cycle(),"build",emptyList(),7))
    }
    @Test fun finishedButSkippedFailedOrReducedWorkCannotCompletePriorBlock() {
        val full=earlier()
        val skipped=full.mapIndexed{index,receipt->
            if(index==2)receipt.copy(allIssuedMainWorkingSetsCompleted=false)
            else receipt
        }
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.INCOMPLETE_HISTORY),
            checked(cycle(),"build",skipped,7))
        val failedLast=full.dropLast(1)+done("base",2,4,
            allMainComplete=false)
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.INCOMPLETE_HISTORY),
            checked(
                cycle(),"build",failedLast,7))
        assertEquals(NativeFutureActivationReview.BoundaryReviewed(
            "cycle-1","build",3,2,6,7),
            checked(
                cycle(),"build",full,7))
    }

    @Test fun anyAlreadyCompletedFutureWorkoutBlocksRetroactiveActivation() {
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.TARGET_BLOCK_ALREADY_STARTED),
            checked(
                cycle(),"build",earlier()+done("build",3,0),7))
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.TARGET_BLOCK_ALREADY_STARTED),
            checked(
                cycle(),"build",earlier()+done("build",5,4),7))
    }
    @Test fun mismatchedBlockOrDuplicateCompletionCannotFillGap() {
        val wrong=earlier().dropLast(1)+done("build",2,4)
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.CONFLICTING_HISTORY),
            checked(
                cycle(),"build",wrong,7))
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.CONFLICTING_HISTORY),
            checked(
                cycle(),"build",earlier()+earlier().first(),7))
    }
    @Test fun unrelatedCycleCannotAdvanceTheSelectedCustomCycle() {
        val unrelated=earlier().map{it.copy(cycleId="another-cycle")}
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.INCOMPLETE_HISTORY),
            checked(
                cycle(),"build",unrelated,7))
        assertEquals(NativeFutureActivationReview.BoundaryReviewed(
            "cycle-1","build",3,2,6,7),
            checked(
                cycle(),"build",earlier()+unrelated,7))
    }

    @Test fun undatedHistoricalReceiptsCannotProveACompletedCalendarBlock() {
        val incomplete=earlier().mapIndexed{index,visit->
            if(index==3)visit.copy(completedAtEpochMillis=null) else visit
        }
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.CALENDAR_UNVERIFIED),
            checked(cycle(),"build",incomplete,7))
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.CALENDAR_UNVERIFIED),
            NativeFutureBlockBoundaryDomain.check(
                cycle(),"build",earlier(),7,"Missing/TimeZone"))
    }

    @Test fun changedWeekdayOrCalendarWeekCannotBorrowACompletedProgramWeek() {
        val changedWeekday=earlier().mapIndexed{index,visit->
            if(index==1)visit.copy(completedAtEpochMillis=at(1,3))
            else visit
        }
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.CALENDAR_CONFLICT),
            checked(cycle(),"build",changedWeekday,7))

        // A stored absolute-week-1 session cannot become week 2 merely
        // because an actual Monday finish occurred one week later.
        val shiftedWeek=earlier().mapIndexed{index,visit->
            if(index==0)visit.copy(completedAtEpochMillis=at(2,0))
            else visit
        }
        assertEquals(NativeFutureActivationReview.Rejected(
            NativeFutureActivationRefusal.CALENDAR_CONFLICT),
            checked(cycle(),"build",shiftedWeek,7))
        val crossMidnight=earlier().mapIndexed{index,visit->
            if(index==0)visit.copy(completedAtEpochMillis=
                java.time.Instant.parse("2026-11-02T00:30:00Z")
                    .toEpochMilli()) else visit
        }
        assertEquals("UTC Monday is still Sunday in the athlete timezone",
            NativeFutureActivationReview.Rejected(
                NativeFutureActivationRefusal.CALENDAR_CONFLICT),
            checked(cycle(),"build",crossMidnight,7))
    }

    @Test fun daylightSavingChangesDoNotAlterMondayWeekIdentity() {
        val march=earlier().map{visit->
            visit.copy(completedAtEpochMillis=
                LocalDate.of(2026,3,2)
                    .plusWeeks(visit.absoluteWeek.toLong()-1L)
                    .plusDays(visit.weekday.toLong())
                    .atTime(12,0).atZone(ZoneId.of(timezoneId))
                    .toInstant().toEpochMilli())
        }
        assertEquals(NativeFutureActivationReview.BoundaryReviewed(
            "cycle-1","build",3,2,6,7),
            checked(cycle(),"build",march,7))
    }

    @Test fun realFreeformSplitRetainsAthleteAuthoredWeekdays() {
        assertTrue(NativeFutureBlockBoundaryDomain.scheduleMatches(
            CustomSplitIdentity.Freeform("Chest and Arms"),listOf(0,1,3,5),
            ComposerSplit.UPPER_LOWER,setOf(0,1,3,5)))
        assertFalse(NativeFutureBlockBoundaryDomain.scheduleMatches(
            CustomSplitIdentity.Freeform("Chest and Arms"),listOf(0,1,3,5),
            ComposerSplit.UPPER_LOWER,setOf(0,1,2,5)))
        assertFalse(NativeFutureBlockBoundaryDomain.scheduleMatches(
            CustomSplitIdentity.Freeform("Chest and Arms"),listOf(0,1,3,3),
            ComposerSplit.UPPER_LOWER,setOf(0,1,3)))
        assertFalse(NativeFutureBlockBoundaryDomain.scheduleMatches(
            CustomSplitIdentity.Named(ComposerSplit.FULL_BODY),listOf(0,1,3,5),
            ComposerSplit.UPPER_LOWER,setOf(0,1,3,5)))
    }

}
