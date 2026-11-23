package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.time.Instant
import java.time.YearMonth
import java.time.LocalDate
import java.time.ZoneId
import java.util.Locale
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinHistoryDatesTest {
    private val source=KotlinTrainingSourceRevision("athlete-1",1,"program-1",1,"gym-1",1,"catalog-1","rules-1")
    private fun entry(order:Long,time:String?=null):NativeKotlinMixedHistoryCompletion.Composer {
        val manifest=KotlinHistoryManifestEntry(order,"workout-$order","stored-$order","finish-$order",2,0,
            time?.let{Instant.parse(it).toEpochMilli()})
        return NativeKotlinMixedHistoryCompletion.Composer(manifest,
            KotlinWorkoutHistoryEvidence(source,manifest.workoutId,manifest.finishEventId,2,emptyList(),emptyList()))
    }
    @Test fun oldSessionsRemainExplicitlyUndatedWithoutUsingTheirOrderAsATimestamp() {
        val sessions=listOf(entry(1),entry(2))
        val sections=nativeHistoryMonths(sessions,ZoneId.of("UTC"))
        assertEquals(1,sections.size)
        assertNull(sections.single().month)
        assertEquals("Date not recorded",sections.single().title(Locale.US))
        assertEquals(listOf(2L,1L),sections.single().sessions.map{it.manifest.completionOrder})
        assertNull(nativeHistoryDateLabel(sessions.first().manifest,ZoneId.of("UTC"),Locale.US))
    }
    @Test fun recordedInstantsAreGroupedInTheChosenLocalZoneAcrossMonthBoundaries() {
        val sessions=listOf(entry(1,"2026-11-01T00:30:00Z"),entry(2,"2026-11-01T06:30:00Z"))
        val local=nativeHistoryMonths(sessions,ZoneId.of("America/New_York"))
        assertEquals(listOf(YearMonth.of(2026,11),YearMonth.of(2026,10)),local.map{it.month})
        assertEquals(listOf(2L,1L),local.flatMap{it.sessions}.map{it.manifest.completionOrder})
        assertEquals(1,nativeHistoryMonths(sessions,ZoneId.of("UTC")).size)
        assertEquals(listOf(1L,2L),sessions.map{it.manifest.completionOrder})
    }
    @Test fun bothOccurrencesOfADaylightSavingHourRemainDistinctRecordedSessions() {
        val sessions=listOf(entry(1,"2026-11-01T05:30:00Z"),entry(2,"2026-11-01T06:30:00Z"))
        val section=nativeHistoryMonths(sessions,ZoneId.of("America/New_York")).single()
        assertEquals(YearMonth.of(2026,11),section.month)
        assertEquals(listOf("workout-2","workout-1"),section.sessions.map{it.manifest.workoutId})
    }
    @Test fun datedMonthsSortBeforeUnknownDatesAndReceiptOrderStaysStableWithinAMonth() {
        val sessions=listOf(entry(1,"2026-10-20T12:00:00Z"),entry(2,"2026-10-10T12:00:00Z"),entry(3),
            entry(4,"2026-11-02T12:00:00Z"))
        val sections=nativeHistoryMonths(sessions,ZoneId.of("UTC"))
        assertEquals(listOf("2026-11","2026-10","undated"),sections.map{it.key})
        assertEquals(listOf(2L,1L),sections[1].sessions.map{it.manifest.completionOrder})
        assertEquals(listOf(3L),sections.last().sessions.map{it.manifest.completionOrder})
    }
    @Test fun exerciseVisitHeadersUseRecordedLocalFinishTimesAndPreserveUndatedSessions() {
        val dated=entry(17,"2026-11-01T00:30:00Z").manifest
        val ny=nativeExerciseHistorySessionTitle(dated,ZoneId.of("America/New_York"),Locale.US)
        val utc=nativeExerciseHistorySessionTitle(dated,ZoneId.of("UTC"),Locale.US)
        assertTrue(ny.startsWith("Session 17 · "))
        assertTrue(ny.contains("Oct 31"))
        assertTrue(utc.contains("Nov 1"))
        assertEquals("Session 18 · Date not recorded",
            nativeExerciseHistorySessionTitle(entry(18).manifest,ZoneId.of("UTC"),Locale.US))
        assertNotEquals(ny,utc)
    }
    @Test fun aRemovedSavedDayFallsBackToVerifiedMonthNotAnEmptyHistory() {
        val zone=ZoneId.of("UTC")
        val sessions=listOf(entry(1,"2026-11-12T14:00:00Z"),
            entry(2,"2026-11-18T14:00:00Z"))
        val available=nativeHistoryMonths(sessions,zone)
        val valid=nativeHistoryDateSelection("2026-11","2026-11-12",available,zone)
        assertEquals(NativeKotlinHistoryDateSelection.Month(
            YearMonth.of(2026,11),LocalDate.of(2026,11,12)),valid)
        assertEquals(1,nativeHistorySessionsInSelection(sessions,valid,zone).size)
        val stale=nativeHistoryDateSelection("2026-11","2026-11-13",available,zone)
        assertEquals(NativeKotlinHistoryDateSelection.Month(YearMonth.of(2026,11)),stale)
        assertEquals(2,nativeHistorySessionsInSelection(sessions,stale,zone).size)
        assertEquals(NativeKotlinHistoryDateSelection.All,
            nativeHistoryDateSelection("2026-10","2026-10-15",available,zone))
    }

    @Test fun savedDayUsesActualLocalFinishDateIncludingMonthBoundaries() {
        val zone=ZoneId.of("America/New_York")
        val session=entry(3,"2026-11-01T00:30:00Z") // October 31 locally
        val available=nativeHistoryMonths(listOf(session),zone)
        assertEquals(NativeKotlinHistoryDateSelection.Month(
            YearMonth.of(2026,10),LocalDate.of(2026,10,31)),
            nativeHistoryDateSelection("2026-10","2026-10-31",available,zone))
        assertEquals(NativeKotlinHistoryDateSelection.Month(YearMonth.of(2026,10)),
            nativeHistoryDateSelection("2026-10","2026-10-30",available,zone))
        assertEquals(NativeKotlinHistoryDateSelection.Month(YearMonth.of(2026,10)),
            nativeHistoryDateSelection("2026-10","2026-11-01",available,zone))
    }

    @Test fun displayUsesTheRequestedLocaleAndEverySessionRemainsReachable() {
        val sessions=(1L..512L).map{entry(it,"2026-11-14T17:30:00Z")}
        val section=nativeHistoryMonths(sessions,ZoneId.of("UTC")).single()
        assertEquals(512,section.sessions.size)
        assertEquals("November 2026",section.title(Locale.US))
        assertTrue(section.title(Locale.FRANCE).startsWith("novembre"))
        assertNotNull(nativeHistoryDateLabel(sessions.first().manifest,ZoneId.of("UTC"),Locale.US))
    }
}
