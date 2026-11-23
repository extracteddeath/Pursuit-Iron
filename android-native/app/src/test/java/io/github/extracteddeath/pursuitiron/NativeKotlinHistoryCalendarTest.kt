package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.*
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.time.*
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinHistoryCalendarTest {
    private val zone=ZoneId.of("UTC")
    private val source=KotlinTrainingSourceRevision("athlete-1",1,"program-1",1,"gym-1",1,"catalog-1","rules-1")
    private fun entry(order:Long,time:String?=null,custom:Boolean=false):NativeKotlinMixedHistoryCompletion {
        val manifest=KotlinHistoryManifestEntry(order,"workout-$order","stored-$order","finish-$order",2,0,
            time?.let{Instant.parse(it).toEpochMilli()})
        return if(custom)NativeKotlinMixedHistoryCompletion.Custom(manifest,
            CustomWorkoutHistoricalEvidence("athlete-1",manifest.workoutId,"cycle-1",1,
                manifest.finishEventId,2,emptyList()))
        else NativeKotlinMixedHistoryCompletion.Composer(manifest,
            KotlinWorkoutHistoryEvidence(source,manifest.workoutId,manifest.finishEventId,2,emptyList(),emptyList()))
    }
    private fun ids(entries:List<NativeKotlinMixedHistoryCompletion>)=entries.map{it.manifest.workoutId}

    @Test fun sixWeekMonthKeepsEveryDayAndLocaleWeekStartTogether() {
        val month=YearMonth.of(2026,8)
        val sunday=nativeHistoryCalendar(emptyList(),month,zone,DayOfWeek.SUNDAY)
        assertEquals(6,sunday.weeks.size)
        assertEquals(listOf(null,null,null,null,null,null,month.atDay(1)),sunday.weeks.first())
        assertEquals(DayOfWeek.SUNDAY,sunday.weekdays.first())
        val monday=nativeHistoryCalendar(emptyList(),month,zone,DayOfWeek.MONDAY)
        assertEquals(5,monday.weeks.first().takeWhile{it==null}.size)
        assertEquals(DayOfWeek.SUNDAY,monday.weekdays.last())
    }
    @Test fun leapDayAndExactFourWeekMonthHaveNoExtraOrMissingCells() {
        val leap=nativeHistoryCalendar(emptyList(),YearMonth.of(2024,2),zone,DayOfWeek.MONDAY)
        assertEquals((1..29).toList(),leap.weeks.flatten().filterNotNull().map{it.dayOfMonth})
        val four=nativeHistoryCalendar(emptyList(),YearMonth.of(2021,2),zone,DayOfWeek.MONDAY)
        assertEquals(4,four.weeks.size)
        assertTrue(four.weeks.flatten().all{it!=null})
    }
    @Test fun allGregorianCalendarShapesPartitionDaysExactlyOnceWithSevenColumns() {
        for(year in 2000..2399)for(number in 1..12)for(first in listOf(DayOfWeek.MONDAY,DayOfWeek.SUNDAY)) {
            val month=YearMonth.of(year,number)
            val calendar=nativeHistoryCalendar(emptyList(),month,zone,first)
            assertTrue(calendar.weeks.size in 4..6)
            assertTrue(calendar.weeks.all{it.size==7})
            assertEquals((1..month.lengthOfMonth()).map(month::atDay),calendar.weeks.flatten().filterNotNull())
        }
    }
    @Test fun localCalendarNeverUsesUtcDayAsTheAthletesRecordedDay() {
        val sessions=listOf(entry(1,"2027-01-01T00:30:00Z"))
        val ny=ZoneId.of("America/New_York")
        assertEquals(LocalDate.of(2026,12,31),nativeHistoryRecordedDate(sessions.single().manifest,ny))
        assertEquals(setOf(LocalDate.of(2026,12,31)),
            nativeHistoryCalendar(sessions,YearMonth.of(2026,12),ny,DayOfWeek.SUNDAY).sessionsByDate.keys)
        assertTrue(nativeHistoryCalendar(sessions,YearMonth.of(2027,1),ny,DayOfWeek.SUNDAY).sessionsByDate.isEmpty())
    }
    @Test fun duplicateClockHourAndBothWorkoutOwnersRemainDistinctOnTheSameDay() {
        val sessions=listOf(entry(1,"2026-11-01T05:30:00Z"),entry(2,"2026-11-01T06:30:00Z",true))
        val calendar=nativeHistoryCalendar(sessions,YearMonth.of(2026,11),ZoneId.of("America/New_York"),DayOfWeek.SUNDAY)
        assertEquals(listOf("workout-2","workout-1"),ids(calendar.sessionsByDate.values.single()))
    }
    @Test fun unknownDatesStayOutsideTheCalendarButRemainInAllAndUndatedViews() {
        val sessions=listOf(entry(1),entry(2,"2026-11-14T17:00:00Z"),entry(3,custom=true))
        val calendar=nativeHistoryCalendar(sessions,YearMonth.of(2026,11),zone,DayOfWeek.MONDAY)
        assertEquals(listOf("workout-2"),ids(calendar.sessionsByDate.values.flatten()))
        assertEquals(sessions,nativeHistorySessionsInSelection(sessions,NativeKotlinHistoryDateSelection.All,zone))
        assertEquals(listOf("workout-1","workout-3"),ids(nativeHistorySessionsInSelection(sessions,
            NativeKotlinHistoryDateSelection.Undated,zone)))
    }
    @Test fun monthAndDaySelectionIntersectTheExactRecordedDatesWithoutChangingFacts() {
        val sessions=listOf(entry(1,"2026-10-31T18:00:00Z"),entry(2,"2026-11-14T17:00:00Z"),
            entry(3,"2026-11-14T20:00:00Z",true),entry(4,"2026-11-15T20:00:00Z"),entry(5))
        val before=sessions.toList()
        val month=YearMonth.of(2026,11)
        assertEquals(listOf("workout-2","workout-3","workout-4"),ids(nativeHistorySessionsInSelection(sessions,
            NativeKotlinHistoryDateSelection.Month(month),zone)))
        assertEquals(listOf("workout-2","workout-3"),ids(nativeHistorySessionsInSelection(sessions,
            NativeKotlinHistoryDateSelection.Month(month,month.atDay(14)),zone)))
        assertEquals(before,sessions)
    }
    @Test fun removedOrMalformedRestoredMonthFallsBackToAllCurrentRecords() {
        val available=nativeHistoryMonths(listOf(entry(1,"2026-11-14T17:00:00Z")),zone)
        for(key in listOf("not-a-month","2026-10","undated","2026-13",""))
            assertEquals(NativeKotlinHistoryDateSelection.All,nativeHistoryDateSelection(key,"2026-11-14",available,zone))
        assertEquals(NativeKotlinHistoryDateSelection.All,nativeHistoryDateSelection("2026-11",null,emptyList(),zone))
    }
    @Test fun malformedOrWrongMonthRestoredDayKeepsTheWholeSelectedMonthAccessible() {
        val month=YearMonth.of(2026,11)
        val available=nativeHistoryMonths(listOf(entry(1,"2026-11-14T17:00:00Z")),zone)
        for(day in listOf("invalid","2026-10-14","2026-11-31","2026-02-29"))
            assertEquals(NativeKotlinHistoryDateSelection.Month(month),nativeHistoryDateSelection(month.toString(),day,available,zone))
        assertEquals(NativeKotlinHistoryDateSelection.Month(month,month.atDay(14)),
            nativeHistoryDateSelection(month.toString(),"2026-11-14",available,zone))
    }
    @Test fun searchAndDateFiltersComposeWithoutRevealingAnUnmatchedWorkout() {
        val all=listOf(entry(1,"2026-11-14T17:00:00Z"),entry(2,"2026-11-14T18:00:00Z"),
            entry(3,"2026-11-15T17:00:00Z"))
        val matching=listOf(all[0],all[2])
        val selection=NativeKotlinHistoryDateSelection.Month(YearMonth.of(2026,11),LocalDate.of(2026,11,14))
        assertEquals(listOf("workout-1"),ids(nativeHistorySessionsInSelection(matching,selection,zone)))
        assertEquals(1,nativeHistoryCalendar(matching,selection.month,zone,DayOfWeek.MONDAY)
            .sessionsByDate[selection.day]!!.size)
    }
    @Test fun selectedDayCanBecomeEmptyAfterSearchWithoutSilentlyChangingTheSelection() {
        val month=YearMonth.of(2026,11)
        val available=nativeHistoryMonths(listOf(entry(1,"2026-11-14T17:00:00Z")),zone)
        val selected=nativeHistoryDateSelection("2026-11","2026-11-14",available,zone)
        assertEquals(NativeKotlinHistoryDateSelection.Month(month,month.atDay(14)),selected)
        assertTrue(nativeHistorySessionsInSelection(emptyList(),selected,zone).isEmpty())
    }
    @Test fun longHistoryKeepsEveryDatedAndUndatedSessionReachable() {
        val sessions=(1L..512L).map{entry(it,"2026-11-14T17:00:00Z",it%2==0L)}+entry(513)
        val calendar=nativeHistoryCalendar(sessions,YearMonth.of(2026,11),zone,DayOfWeek.MONDAY)
        assertEquals(512,calendar.sessionsByDate.values.single().size)
        val dateSelection=NativeKotlinHistoryDateSelection.Month(YearMonth.of(2026,11),LocalDate.of(2026,11,14))
        val allReachable=nativeHistorySessionsInSelection(sessions,dateSelection,zone)+
            nativeHistorySessionsInSelection(sessions,NativeKotlinHistoryDateSelection.Undated,zone)
        assertEquals(sessions.map{it.manifest.workoutId}.toSet(),allReachable.map{it.manifest.workoutId}.toSet())
    }
}
