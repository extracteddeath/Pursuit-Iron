package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.time.Instant
import java.time.LocalDate
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinFirstBlockCalendarPreflightTest {
    private fun basis(weekdays:Set<Int> =setOf(0,2,4),
        timezone:String="America/New_York")=NativeFirstBlockCalendarBasis(
        "athlete-1","cycle-1","base",2,"a".repeat(64),
        7,"b".repeat(64),"c".repeat(64),timezone,weekdays.associateWith { "Session $it" })

    private val springBefore=Instant.parse("2026-03-07T16:00:00Z")
        .toEpochMilli()
    private val springAfter=Instant.parse("2026-03-09T16:00:00Z")
        .toEpochMilli()

    @Test fun firstBlockDatesAreLocalAndRemainStableOverSpringClockChange() {
        val start=NativeFirstBlockCalendarDomain.propose(
            basis(),springBefore,LocalDate.parse("2026-03-02"))
            as NativeFirstBlockCalendarPreview.Ready
        val second=NativeFirstBlockCalendarDomain.propose(
            basis(),springAfter,LocalDate.parse("2026-03-09"))
            as NativeFirstBlockCalendarPreview.Ready
        assertEquals(listOf("2026-03-02","2026-03-04","2026-03-06"),
            start.proposal.firstWeek.map{it.localDate.toString()})
        assertEquals(listOf("2026-03-09","2026-03-11","2026-03-13"),
            second.proposal.firstWeek.map{it.localDate.toString()})
        assertEquals(listOf(0,2,4),
            second.proposal.firstWeek.map{it.weekday})
        assertEquals(listOf("Session 0", "Session 2", "Session 4"),
            second.proposal.firstWeek.map { it.workoutName })
        assertTrue(second.proposal.firstWeek.all{it.absoluteWeek==1})
        assertEquals("America/New_York",second.proposal.timezoneId)
        assertFalse(second.proposal.isApproved)
        assertFalse(second.proposal.canIssueWorkout)
        assertFalse(second.proposal.canChangeTrainingNumbers)
        assertFalse(second.proposal.canMarkMissedOrCompleted)
        assertFalse(second.proposal.canInferStartFromHistory)
    }

    @Test fun chosenMondayAndVerifiedSourceIdentityBindPreviewFingerprint() {
        val original=(NativeFirstBlockCalendarDomain.propose(
            basis(),springBefore,LocalDate.parse("2026-03-09"))
            as NativeFirstBlockCalendarPreview.Ready).proposal
        assertEquals(original,(NativeFirstBlockCalendarDomain.propose(
            basis(),springBefore,LocalDate.parse("2026-03-09"))
            as NativeFirstBlockCalendarPreview.Ready).proposal)
        val moved=(NativeFirstBlockCalendarDomain.propose(
            basis(),springBefore,LocalDate.parse("2026-03-16"))
            as NativeFirstBlockCalendarPreview.Ready).proposal
        val changedSource=(NativeFirstBlockCalendarDomain.propose(
            basis().copy(sourceSha256="d".repeat(64)),
            springBefore,LocalDate.parse("2026-03-09"))
            as NativeFirstBlockCalendarPreview.Ready).proposal
        val changedConsent=(NativeFirstBlockCalendarDomain.propose(
            basis().copy(signoffFingerprint="e".repeat(64)),
            springBefore,LocalDate.parse("2026-03-09"))
            as NativeFirstBlockCalendarPreview.Ready).proposal
        val changedWeekdays=(NativeFirstBlockCalendarDomain.propose(
            basis(weekdays=setOf(0,1,4)),
            springBefore,LocalDate.parse("2026-03-09"))
            as NativeFirstBlockCalendarPreview.Ready).proposal
        assertEquals(64,original.fingerprint.length)
        listOf(moved,changedSource,changedConsent,changedWeekdays).forEach {
            assertNotEquals(original.fingerprint,it.fingerprint)
            assertFalse(it.isApproved)
            assertFalse(it.canIssueWorkout)
        }
    }

    @Test fun invalidClockMondayTimezoneAndRosterFailClosed() {
        fun rejected(time:Long=springBefore,
            date:LocalDate?=LocalDate.parse("2026-03-02"),
            input:NativeFirstBlockCalendarBasis=basis())=
            NativeFirstBlockCalendarDomain.propose(input,time,date)
        val invalidMonday=NativeFirstBlockCalendarPreview.Rejected(
            NativeFirstBlockCalendarRefusal.INVALID_LOCAL_MONDAY)
        assertEquals(invalidMonday,rejected(date=LocalDate.parse("2026-03-03")))
        assertEquals(invalidMonday,rejected(date=LocalDate.parse("2026-02-23")))
        assertEquals(invalidMonday,rejected(date=LocalDate.parse("2026-06-01")))
        val invalidClock=NativeFirstBlockCalendarPreview.Rejected(
            NativeFirstBlockCalendarRefusal.TIMEZONE_OR_CLOCK_UNAVAILABLE)
        assertEquals(invalidClock,rejected(time=0))
        assertEquals(invalidClock,rejected(input=basis(timezone="Mars/Unknown")))
        assertEquals(invalidClock,rejected(input=basis(weekdays=setOf(0,2))))
        assertEquals(invalidClock,rejected(input=basis(weekdays=setOf(0,2,7))))
    }

    @Test fun canBrowseEveryOfferedMondayInBothDirectionsAcrossDst() {
        fun preview(date:LocalDate?)=(NativeFirstBlockCalendarDomain.propose(
            basis(),springBefore,date) as NativeFirstBlockCalendarPreview.Ready).proposal
        val first=preview(null)
        assertNull(first.previousMonday)
        var current=first
        repeat(12) {
            val following=preview(checkNotNull(current.nextMonday))
            assertEquals(current.selectedMonday.plusWeeks(1),following.selectedMonday)
            assertEquals(current.selectedMonday,following.previousMonday)
            current=following
        }
        assertNull(current.nextMonday)
        repeat(12) { current=preview(checkNotNull(current.previousMonday)) }
        assertEquals(first,current)
    }

    @Test fun noSelectionDefaultsToCurrentMondayInAthletesTimezone() {
        val friday=NativeFirstBlockCalendarDomain.propose(
            basis(),springBefore,null) as NativeFirstBlockCalendarPreview.Ready
        val monday=NativeFirstBlockCalendarDomain.propose(
            basis(),springAfter,null) as NativeFirstBlockCalendarPreview.Ready
        assertEquals(LocalDate.parse("2026-03-02"),friday.proposal.selectedMonday)
        assertEquals(LocalDate.parse("2026-03-09"),monday.proposal.selectedMonday)
    }
}
