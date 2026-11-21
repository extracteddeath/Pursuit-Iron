package io.github.extracteddeath.pursuitiron.nativecoachstore

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/** An approved block date is a non-issuing local-date projection, not a
 * journal observation. Cover an entire training week spanning a DST change. */
class NativeApprovedBlockWeekProjectionTest {
    private val approval=NativeActivatedBlockCalendar(
        ownerKey="owner-1",athleteId="athlete-1",
        cycleId="cycle-1",blockId="build",
        sourceRevision=3,sourceSha256="a".repeat(64),
        draftRevision=4,draftSha256="b".repeat(64),
        firstAbsoluteWeek=5,
        firstWeekMonday=LocalDate.of(2026,3,2),
        timezoneId="America/New_York",
        scheduledWeekdays=setOf(0,2,4),
        acceptedAtEpochMillis=1_800_000_000_000L,
        approvalFingerprint="c".repeat(64)
    )

    @Test fun eachScheduledDayHasOneLocalDateAcrossSpringClockChange() {
        val first=approval.scheduledWeekdays.sorted().map { weekday ->
            NativeActivatedBlockScheduledDayDomain.project(
                approval,2,5,weekday)
                as NativeActivatedBlockScheduledDayReview.Scheduled
        }
        assertEquals(listOf("2026-03-02","2026-03-04","2026-03-06"),
            first.map{it.scheduledLocalDate.toString()})
        val second=approval.scheduledWeekdays.sorted().map { weekday ->
            NativeActivatedBlockScheduledDayDomain.project(
                approval,2,6,weekday)
                as NativeActivatedBlockScheduledDayReview.Scheduled
        }
        assertEquals(listOf("2026-03-09","2026-03-11","2026-03-13"),
            second.map{it.scheduledLocalDate.toString()})
        assertEquals(listOf(0,2,4),second.map{it.weekday})
        second.forEach {
            assertEquals("America/New_York",it.timezoneId)
            assertFalse(it.mayIssueWorkout)
            assertFalse(it.mayCompleteWorkout)
            assertFalse(it.mayMarkMissed)
            assertFalse(it.mayReschedule)
            assertFalse(it.mayChangeNumbers)
        }
    }

    @Test fun unscheduledDaysAndEarlierOrLaterBlocksHaveNoDates() {
        val cases=listOf(
            Triple(5,1,NativeActivatedBlockScheduledDayRefusal.UNSCHEDULED_WEEKDAY),
            Triple(4,0,NativeActivatedBlockScheduledDayRefusal.OUTSIDE_APPROVED_BLOCK),
            Triple(7,0,NativeActivatedBlockScheduledDayRefusal.OUTSIDE_APPROVED_BLOCK),
            Triple(5,-1,NativeActivatedBlockScheduledDayRefusal.UNSCHEDULED_WEEKDAY),
            Triple(5,7,NativeActivatedBlockScheduledDayRefusal.UNSCHEDULED_WEEKDAY)
        )
        cases.forEach { (week,day,reason) ->
            assertEquals(NativeActivatedBlockScheduledDayReview.Rejected(reason),
                NativeActivatedBlockScheduledDayDomain.project(
                    approval,2,week,day))
        }
        assertTrue(NativeActivatedBlockScheduledDayDomain.project(
            approval,0,5,0) is NativeActivatedBlockScheduledDayReview.Rejected)
        assertTrue(NativeActivatedBlockScheduledDayDomain.project(
            approval,53,5,0) is NativeActivatedBlockScheduledDayReview.Rejected)
    }
}
