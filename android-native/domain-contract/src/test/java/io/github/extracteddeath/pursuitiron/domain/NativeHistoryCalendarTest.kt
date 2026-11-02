package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.ZoneOffset
import java.util.concurrent.CancellationException
import kotlin.coroutines.*

class NativeHistoryCalendarTest {
    private val query=NativeHistoryCalendarQuery(YearMonth.of(2026,11),ZoneId.of("America/New_York"),"Leg")
    private fun utc(day: Int)=LocalDate.of(2026,11,day).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
    private fun rows(count: Int=29)= (0 until count).map { i ->
        val date=if(i<2)Instant.parse(if(i==0)"2026-11-01T05:30:00Z" else "2026-11-01T06:30:00Z").toEpochMilli()
            else Instant.parse("2026-11-02T04:30:00Z").toEpochMilli()+i*60000L
        NativeHistorySummary("workout-$i","Leg program","Leg day",1,"kg",date,2,3,200,i%2==0)
    }.sortedWith(compareByDescending<NativeHistorySummary> { it.date }.thenBy { it.historyId })
    private fun <T> immediate(block: suspend ()->T): T {
        var outcome: Result<T>?=null
        block.startCoroutine(object: Continuation<T> {
            override val context=EmptyCoroutineContext
            override fun resumeWith(result: Result<T>) { outcome=result }
        })
        return checkNotNull(outcome).getOrThrow()
    }
    private class Rig(val query: NativeHistoryCalendarQuery,val rows: List<NativeHistorySummary>) {
        var raw="{}";val copies=ArrayList<ByteArray>();val offsets=ArrayList<Int>()
        var mutate: (Int,NativeHistoryPage)->NativeHistoryPage={_,page->page}
        val owner=object: CanonicalCurrentSnapshotOwner {
            override suspend fun readOwnedCurrentStoreBytes()=raw.toByteArray().also { copies+=it }
            override fun subscribeCommittedChanges(onChanged: ()->Unit)=AutoCloseable {}
        }
        val engine=object: NativeHistoryEngine {
            override suspend fun inspectHistoryPage(snapshot: String,queryJson: String): NativeHistoryPage {
                val request=JSONObject(queryJson);val offset=request.getInt("offset");offsets+=offset
                val sha=WorkoutStoreRuntimeCodec.sha(snapshot)
                assertEquals(sha,request.getString("snapshotSha256"))
                assertEquals(JSONObject(NativeHistoryPageCodec.query(filter=query.filter)).getJSONObject("filter").toString(),
                    request.getJSONObject("filter").toString())
                return mutate(offset,NativeHistoryPage(sha,offset,rows.size,
                    (offset+12).takeIf { it<rows.size },rows.drop(offset).take(12),query.filter,minOf(2000,rows.size+3)))
            }
            override suspend fun inspectHistory(snapshot: String,historyId: String): NativeWorkoutHistory?=null
            override suspend fun prepareHistoryCorrection(snapshot: String,correctionJson: String): String?=
                throw AssertionError("Calendar cannot write training data")
        }
        val reader=NativeHistoryReadPort(owner,engine)
    }
    @Test fun completeMonthIncludesEveryPageAndGroupsRepeatedClockHoursByTheLocalDay() {
        val source=rows().toMutableList();val rig=Rig(query,source)
        val calendar=checkNotNull(immediate { rig.reader.calendar(query) })
        assertEquals(listOf(0,12,24),rig.offsets);assertEquals(29,calendar.entries.size)
        assertEquals(32,calendar.unfilteredTotal);assertEquals(1,calendar.activeDays)
        // 04:30 UTC on November 2 is still November 1 on this 25-hour local day.
        assertEquals(source,calendar.workoutsOn(utc(1)));assertTrue(calendar.workoutsOn(utc(2)).isEmpty())
        assertTrue(calendar.entries.any { !it.detailsAvailable })
        source.clear();assertEquals(29,calendar.entries.size);assertEquals(29,calendar.workoutsOn(utc(1)).size)
        assertThrows(UnsupportedOperationException::class.java) { (calendar.entries as MutableList<NativeHistorySummary>).clear() }
        assertThrows(UnsupportedOperationException::class.java) { (calendar.workoutsOn(utc(1)) as MutableList<NativeHistorySummary>).clear() }
        assertTrue(rig.copies.all { bytes->bytes.all { it==0.toByte() } })
    }
    @Test fun monthBoundsUseLocalMidnightIncludingDstLeapYearsAndOppositeOffsets() {
        for((month,zone) in listOf(
            YearMonth.of(2026,3) to ZoneId.of("America/New_York"),
            YearMonth.of(2026,11) to ZoneId.of("America/New_York"),
            YearMonth.of(2024,2) to ZoneId.of("Pacific/Kiritimati"),
            YearMonth.of(2026,12) to ZoneId.of("Pacific/Pago_Pago"))) {
            val q=NativeHistoryCalendarQuery(month,zone)
            assertEquals(month.atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),q.filter.fromInclusive)
            assertEquals(month.plusMonths(1).atDay(1).atStartOfDay(zone).toInstant().toEpochMilli(),q.filter.untilExclusive)
            assertEquals(month,NativeHistoryCalendarQuery.pickerMonth(q.firstUtcDay))
        }
        assertEquals(30L*86400000+3600000,checkNotNull(query.filter.untilExclusive)-checkNotNull(query.filter.fromInclusive))
        assertTrue(runCatching { query.copy(month=YearMonth.of(2101,1)) }.isFailure)
        assertTrue(runCatching { query.copy(search="bad\nquery") }.isFailure)
        assertTrue(runCatching { NativeHistoryCalendarQuery.pickerMonth(query.firstUtcDay+1) }.isFailure)
    }
    @Test fun anyMissingOrForgedPageRejectsTheWholeMonthRatherThanShowingPartialDates() {
        val mutations=listOf<(NativeHistoryPage)->NativeHistoryPage>(
            { it.copy(snapshotSha256="f".repeat(64)) },{it.copy(total=30)},{it.copy(unfilteredTotal=40)},
            {it.copy(filter=query.filter.copy(search="other"))},{it.copy(offset=0)},{it.copy(nextOffset=null)},
            {it.copy(entries=it.entries.drop(1))},{it.copy(entries=it.entries.reversed())},
            {it.copy(entries=it.entries.mapIndexed { i,row->if(i==0)row.copy(date=checkNotNull(query.filter.untilExclusive)) else row })},
            {it.copy(entries=it.entries.mapIndexed { i,row->if(i==0)row.copy(historyId=rows()[0].historyId) else row })})
        for(change in mutations) {
            val rig=Rig(query,rows());rig.mutate={offset,page->if(offset==12)change(page) else page}
            assertTrue(runCatching { immediate { rig.reader.calendar(query) } }.isFailure)
            assertEquals(listOf(0,12),rig.offsets)
        }
    }
    @Test fun changedOwnedRevisionAndCancellationCannotPublishAStaleOrPartialCalendar() {
        val rig=Rig(query,rows())
        assertNull(immediate { rig.reader.calendar(query,"f".repeat(64)) });assertTrue(rig.offsets.isEmpty())
        rig.mutate={offset,page->if(offset==12)rig.raw="{ }";page}
        assertNull(immediate { rig.reader.calendar(query) });assertEquals(listOf(0,12,24),rig.offsets)
        rig.mutate={offset,page->if(offset==12)throw CancellationException("Cancelled calendar read");page}
        assertThrows(CancellationException::class.java) { immediate { rig.reader.calendar(query) } }
        assertTrue(rig.copies.all { bytes->bytes.all { it==0.toByte() } })
        assertEquals("{ }",rig.raw)
    }
    @Test fun emptyMonthsAndTheFullTwoThousandWorkoutLimitHaveExactCompleteCounts() {
        val empty=Rig(query,emptyList());val emptyCalendar=checkNotNull(immediate { empty.reader.calendar(query) })
        assertEquals(listOf(0),empty.offsets);assertEquals(0,emptyCalendar.activeDays);assertEquals(3,emptyCalendar.unfilteredTotal)
        val rig=Rig(query,rows(2000));val calendar=checkNotNull(immediate { rig.reader.calendar(query) })
        assertEquals(2000,calendar.entries.size);assertEquals(167,rig.offsets.size);assertEquals(1992,rig.offsets.last())
    }
}
