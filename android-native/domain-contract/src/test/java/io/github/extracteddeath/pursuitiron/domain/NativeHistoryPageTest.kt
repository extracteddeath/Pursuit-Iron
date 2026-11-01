package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import kotlin.coroutines.*

class NativeHistoryPageTest {
    private val fixture by lazy { JSONObject(checkNotNull(javaClass.classLoader?.getResourceAsStream("finished-journal-golden.json"))
        .bufferedReader().use { it.readText() }) }
    private val source get()=fixture.getJSONObject("preparation").getString("candidateSnapshot")
    private fun response(): JSONObject {
        val e=fixture.getJSONObject("preparation").getJSONObject("historyEntry")
        val summary=JSONObject().put("historyId",e.getString("id")).put("programName",e.getString("programName"))
            .put("dayLabel",e.getString("dayLabel")).put("weekIndex",e.getInt("weekIndex")).put("unit",e.getString("unit"))
            .put("date",e.getLong("date")).put("setsDone",e.getInt("setsDone")).put("totalSets",e.getInt("totalSets"))
            .put("volume",e.getLong("volume")).put("detailsAvailable",false)
        return JSONObject().put("protocolVersion",1).put("id","page-test").put("ok",true).put("result",JSONObject()
            .put("source","modules/engine-api.js:parseStoredData").put("snapshotSha256",WorkoutStoreRuntimeCodec.sha(source))
            .put("offset",0).put("total",1).put("nextOffset",JSONObject.NULL).put("entries",JSONArray().put(summary)))
    }
    @Test fun nativeSummaryUsesTheExistingCanonicalGoldenEntryAndExactSnapshot() {
        val query=NativeHistoryPageCodec.query()
        assertEquals("inspectNativeHistoryPage",WorkoutStoreRuntimeCodec.checkedRequest(
            NativeHistoryPageCodec.encodePage("page-test",source,query)).getString("method"))
        val page=checkNotNull(NativeHistoryPageCodec.decodePage(response().toString(),"page-test",source,query))
        assertEquals(1,page.total);assertEquals(0,page.offset);assertNull(page.nextOffset)
        assertEquals("synthetic-workout",page.entries.single().historyId)
        assertEquals(2,page.entries.single().setsDone);assertFalse(page.entries.single().detailsAvailable)
        assertEquals(12,NativeHistoryPageCodec.PAGE_SIZE)
        assertThrows(UnsupportedOperationException::class.java) {
            (page.entries as MutableList<NativeHistorySummary>).clear()
        }
    }
    @Test fun pageDecoderRejectsWrongRevisionCursorShapeAndDuplicateRows() {
        val query=NativeHistoryPageCodec.query()
        val mutations=listOf<(JSONObject)->Unit>(
            { it.put("id","different-request") },
            { it.getJSONObject("result").put("snapshotSha256","f".repeat(64)) },
            { it.getJSONObject("result").put("offset",12) },
            { it.getJSONObject("result").put("nextOffset",12) },
            { it.getJSONObject("result").put("total",2) },
            { it.getJSONObject("result").put("approvedForWorkoutExecution",true) },
            { it.getJSONObject("result").getJSONArray("entries").getJSONObject(0).put("detailsAvailable","true") },
            { it.getJSONObject("result").getJSONArray("entries").getJSONObject(0).put("setsDone",999) },
            { val p=it.getJSONObject("result");p.put("total",2);val rows=p.getJSONArray("entries");rows.put(rows.getJSONObject(0)) }
        )
        for(mutate in mutations) {
            val bad=response();mutate(bad)
            assertTrue(runCatching { NativeHistoryPageCodec.decodePage(bad.toString(),"page-test",source,query) }.isFailure)
        }
        assertTrue(runCatching { NativeHistoryPageCodec.decodePage(response().toString(),"page-test",source,
            NativeHistoryPageCodec.query(0,"e".repeat(64))) }.isFailure)
        for(offset in listOf(-1,1,1993,2000))assertTrue(runCatching { NativeHistoryPageCodec.query(offset) }.isFailure)
        assertTrue(runCatching { NativeHistoryPageCodec.query(12) }.isFailure)
    }
    private fun <T> immediate(block: suspend ()->T): T {
        var result: Result<T>?=null
        block.startCoroutine(object: Continuation<T> {
            override val context=EmptyCoroutineContext
            override fun resumeWith(value: Result<T>) { result=value }
        })
        return checkNotNull(result).getOrThrow()
    }
    @Test fun readPortDropsAChangedStoreAndWipesItsDetachedBuffersWithoutWriting() {
        val copies=ArrayList<ByteArray>();var raw=source;var calls=0;var mutate=false;var forge=false
        val owner=object: CanonicalCurrentSnapshotOwner {
            override suspend fun readOwnedCurrentStoreBytes()=raw.toByteArray().also { copies+=it }
            override fun subscribeCommittedChanges(onChanged: ()->Unit)=AutoCloseable {}
        }
        val engine=object: NativeHistoryEngine {
            override suspend fun inspectHistoryPage(snapshot: String,queryJson: String): NativeHistoryPage {
                calls++
                val page=checkNotNull(NativeHistoryPageCodec.decodePage(response().toString(),"page-test",snapshot,queryJson))
                if(mutate)raw=snapshot+" "
                return if(forge)page.copy(snapshotSha256="f".repeat(64)) else page
            }
            override suspend fun inspectHistory(snapshot: String,historyId: String): NativeWorkoutHistory?=null
            override suspend fun prepareHistoryCorrection(snapshot: String,correctionJson: String): String?=
                throw AssertionError("Read port cannot write history")
        }
        val port=NativeHistoryReadPort(owner,engine)
        assertNull(immediate { port.page(0,"e".repeat(64)) });assertEquals(0,calls)
        assertNotNull(immediate { port.page() });assertEquals(1,calls)
        mutate=true
        assertNull(immediate { port.page() });assertEquals(2,calls)
        assertTrue(copies.all { bytes->bytes.all { it==0.toByte() } })
        raw=source;mutate=false;forge=true
        assertTrue(runCatching { immediate { port.page() } }.isFailure)
        assertEquals(source,raw)
    }
}
