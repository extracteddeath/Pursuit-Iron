package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.Collections

data class NativeHistorySummary(val historyId: String,val programName: String,val dayLabel: String,
    val weekIndex: Int,val unit: String,val date: Long,val setsDone: Int,val totalSets: Int,
    val volume: Long,val detailsAvailable: Boolean)
data class NativeHistoryPage(val snapshotSha256: String,val offset: Int,val total: Int,
    val nextOffset: Int?,val entries: List<NativeHistorySummary>)

/** Bounded transport, not a Kotlin history/progression implementation. */
object NativeHistoryPageCodec {
    const val PAGE_SIZE=12
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private val hash=Regex("^[0-9a-f]{64}$")
    fun query(offset: Int=0,snapshotSha256: String?=null): String {
        require(offset in 0..1992 && offset%PAGE_SIZE==0 &&
            (snapshotSha256?.let { hash.matches(it) } ?: (offset==0)))
        return JSONObject().put("offset",offset).put("snapshotSha256",snapshotSha256 ?: JSONObject.NULL).toString()
    }
    fun encodePage(requestId: String,snapshot: String,query: String)=WorkoutStoreRuntimeCodec.encode(requestId,
        "inspectNativeHistoryPage",JSONObject().put("rawSnapshot",snapshot).put("queryJson",query))
    fun decodePage(raw: String,requestId: String,snapshot: String,queryJson: String): NativeHistoryPage? {
        WorkoutStoreRuntimeCodec.bounded(snapshot,WorkoutStoreRuntimeCodec.MAX_SNAPSHOT_BYTES)
        WorkoutStoreRuntimeCodec.bounded(queryJson,1024)
        val v=WorkoutStoreRuntimeCodec.result(raw,requestId) ?: return null
        WorkoutStoreRuntimeCodec.exact(v,"source","snapshotSha256","offset","total","nextOffset","entries")
        val query=JSONObject(queryJson);WorkoutStoreRuntimeCodec.exact(query,"offset","snapshotSha256")
        fun number(o: JSONObject,key: String,min: Long,max: Long)=WorkoutStoreRuntimeCodec.integer(o,key)
            .also { require(it in min..max) }
        fun text(o: JSONObject,key: String,max: Int=200)=WorkoutStoreRuntimeCodec.text(o,key)
            .also { require(it.length in 1..max) }
        val sha=text(v,"snapshotSha256",64)
        require(hash.matches(sha) && sha==WorkoutStoreRuntimeCodec.sha(snapshot) &&
            text(v,"source")=="modules/engine-api.js:parseStoredData")
        val offset=number(v,"offset",0,1992).toInt();val total=number(v,"total",0,2000).toInt()
        require(offset%PAGE_SIZE==0 && offset==number(query,"offset",0,1992).toInt() &&
            (offset==0 || offset<total) &&
            (query.isNull("snapshotSha256") && offset==0 || query.opt("snapshotSha256")==sha))
        val list=v.getJSONArray("entries")
        require(list.length()==minOf(PAGE_SIZE,(total-offset).coerceAtLeast(0)))
        val entries=(0 until list.length()).map { i ->
            val e=list.getJSONObject(i)
            WorkoutStoreRuntimeCodec.exact(e,"historyId","programName","dayLabel","weekIndex","unit","date",
                "setsDone","totalSets","volume","detailsAvailable")
            val historyId=text(e,"historyId",128);require(id.matches(historyId))
            val unit=text(e,"unit");require(unit in listOf("lb","kg") && e.get("detailsAvailable") is Boolean)
            val count=number(e,"totalSets",1,480).toInt()
            NativeHistorySummary(historyId,text(e,"programName"),text(e,"dayLabel"),number(e,"weekIndex",1,104).toInt(),
                unit,number(e,"date",1,8_640_000_000_000_000L),number(e,"setsDone",0,count.toLong()).toInt(),count,
                number(e,"volume",0,9_007_199_254_740_991L),e.getBoolean("detailsAvailable"))
        }
        require(entries.map { it.historyId }.toSet().size==entries.size && entries.zipWithNext().all { (a,b)->
            a.date>b.date || a.date==b.date && a.historyId<b.historyId })
        val expectedNext=(offset+entries.size).takeIf { it<total }
        val next=if(v.isNull("nextOffset"))null else number(v,"nextOffset",0,1992).toInt()
        require(next==expectedNext)
        return NativeHistoryPage(sha,offset,total,next,Collections.unmodifiableList(entries))
    }
}

/** Rechecks the owned revision after asynchronous engine reads. No store writes. */
class NativeHistoryReadPort(private val owner: CanonicalCurrentSnapshotOwner,private val engine: NativeHistoryEngine) {
    private suspend fun snapshot(): String? {
        val bytes=owner.readOwnedCurrentStoreBytes() ?: return null
        return try {
            require(bytes.size in 1..WorkoutStoreRuntimeCodec.MAX_SNAPSHOT_BYTES)
            Charsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString()
                .also { WorkoutStoreRuntimeCodec.bounded(it,WorkoutStoreRuntimeCodec.MAX_SNAPSHOT_BYTES) }
        } finally { bytes.fill(0) }
    }
    private suspend fun <T> read(expected: String?,project: suspend (String)->T?): T? {
        val source=snapshot() ?: return null
        if(expected!=null && expected!=WorkoutStoreRuntimeCodec.sha(source))return null
        val result=project(source) ?: return null
        return result.takeIf { snapshot()==source }
    }
    suspend fun page(offset: Int=0,expected: String?=null): NativeHistoryPage? = read(expected) { source ->
        engine.inspectHistoryPage(source,NativeHistoryPageCodec.query(offset,expected))?.also {
            require(it.snapshotSha256==WorkoutStoreRuntimeCodec.sha(source) && it.offset==offset)
        }
    }
    suspend fun workout(historyId: String,expected: String?=null): NativeWorkoutHistory? = read(expected) { source ->
        engine.inspectHistory(source,historyId)?.also {
            require(it.snapshotSha256==WorkoutStoreRuntimeCodec.sha(source) && it.historyId==historyId)
        }
    }
}
