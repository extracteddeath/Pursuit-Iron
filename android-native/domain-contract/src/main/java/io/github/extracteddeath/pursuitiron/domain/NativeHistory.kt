package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

interface NativeHistoryEngine {
    suspend fun inspectHistoryPage(snapshot: String,queryJson: String): NativeHistoryPage?
    suspend fun inspectHistory(snapshot: String,historyId: String): NativeWorkoutHistory?
    suspend fun prepareHistoryCorrection(snapshot: String,correctionJson: String): String?
}

data class NativeHistorySet(val setIndex: Int,val kind: String,val ordinal: Int,
    val load: String,val reps: Int,val rir: Int?,val exerciseIndex: Int)
data class NativeHistoryRecord(val load: Double,val reps: Int,val date: Long,val unit: String)
data class NativeHistoryExercise(val id: String,val name: String,val sets: List<NativeHistorySet>,
    val record: NativeHistoryRecord?)
data class NativeWorkoutHistory(val snapshotSha256: String,val entrySha256: String,val historyId: String,
    val dayLabel: String,val weekIndex: Int,val unit: String,val date: Long,val setsDone: Int,
    val totalSets: Int,val volume: Long,val exercises: List<NativeHistoryExercise>)
data class NativeHistoryChange(val exerciseId: String,val setIndex: Int,val load: String,val reps: Int,val rir: Int?)
data class NativeHistoryPreparation(val candidate: String,val alreadyPresent: Boolean,val history: NativeWorkoutHistory)

/** Transport checks only. Summaries, progression mirrors and records stay in canonical JS. */
object NativeHistoryCodec {
    private const val SOURCE="modules/engine-api.js:normalizeEditedHistoryEntry+perfAfterHistoryReplace+exRecords"
    private val identifier=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private val hash=Regex("^[0-9a-f]{64}$")
    private val load=Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")
    private fun number(v: JSONObject,key: String,min: Long,max: Long): Long =
        WorkoutStoreRuntimeCodec.integer(v,key).also { require(it in min..max) }
    private fun text(v: JSONObject,key: String,max: Int=200)=WorkoutStoreRuntimeCodec.text(v,key)
        .also { require(it.length in 1..max) }
    private fun sha(v: JSONObject,key: String)=text(v,key,64).also { require(hash.matches(it)) }
    private fun optionalRir(v: JSONObject): Int?=if(v.isNull("rir"))null else number(v,"rir",0,10).toInt()
    fun encodeInspect(id: String,snapshot: String,historyId: String)=WorkoutStoreRuntimeCodec.encode(id,
        "inspectNativeHistory",JSONObject().put("rawSnapshot",snapshot).put("historyId",historyId))
    fun encodeCorrection(id: String,snapshot: String,correctionJson: String)=WorkoutStoreRuntimeCodec.encode(id,
        "prepareNativeHistoryCorrection",JSONObject().put("currentSnapshot",snapshot).put("correctionJson",correctionJson))
    fun correctionJson(history: NativeWorkoutHistory,operationId: String,correctedAt: Long,
        changes: List<NativeHistoryChange>): String {
        require(identifier.matches(operationId) && correctedAt in 1..8_640_000_000_000_000L &&
            changes.size in 1..48 && changes.map { it.exerciseId to it.setIndex }.toSet().size==changes.size)
        val list=JSONArray()
        changes.forEach { change ->
            require(identifier.matches(change.exerciseId) && change.setIndex in 0..479 && load.matches(change.load) &&
                change.reps in 1..100 && (change.rir==null || change.rir in 0..10) &&
                history.exercises.any { ex -> ex.id==change.exerciseId && ex.sets.any { it.setIndex==change.setIndex } })
            list.put(JSONObject().put("exerciseId",change.exerciseId).put("setIndex",change.setIndex)
                .put("load",change.load).put("reps",change.reps).put("rir",change.rir ?: JSONObject.NULL))
        }
        return JSONObject().put("operationId",operationId).put("snapshotSha256",history.snapshotSha256)
            .put("historyId",history.historyId).put("entrySha256",history.entrySha256)
            .put("correctedAt",correctedAt).put("changes",list).toString()
            .also { WorkoutStoreRuntimeCodec.bounded(it,16384) }
    }
    private fun history(v: JSONObject,expectedSnapshot: String,expectedId: String): NativeWorkoutHistory {
        WorkoutStoreRuntimeCodec.exact(v,"source","snapshotSha256","entrySha256","historyId","dayLabel",
            "weekIndex","unit","date","setsDone","totalSets","volume","exercises")
        require(text(v,"source")==SOURCE && sha(v,"snapshotSha256")==WorkoutStoreRuntimeCodec.sha(expectedSnapshot) &&
            text(v,"historyId")==expectedId && identifier.matches(expectedId))
        val unit=text(v,"unit");require(unit in listOf("lb","kg"))
        val list=v.getJSONArray("exercises");require(list.length() in 0..48)
        val exercises=(0 until list.length()).map { i ->
            val ex=list.getJSONObject(i);WorkoutStoreRuntimeCodec.exact(ex,"id","name","sets","record")
            val exId=text(ex,"id",80);require(identifier.matches(exId))
            val rawSets=ex.getJSONArray("sets");require(rawSets.length() in 1..480)
            val sets=(0 until rawSets.length()).map { j ->
                val s=rawSets.getJSONObject(j)
                WorkoutStoreRuntimeCodec.exact(s,"setIndex","exerciseIndex","kind","ordinal","load","reps","rir")
                val kind=text(s,"kind");require(kind in listOf("working","myo","drop"))
                val weight=text(s,"load",9);require(load.matches(weight))
                NativeHistorySet(number(s,"setIndex",0,479).toInt(),kind,number(s,"ordinal",1,20).toInt(),
                    weight,number(s,"reps",1,100).toInt(),optionalRir(s),number(s,"exerciseIndex",0,47).toInt())
            }
            require(sets.map { it.setIndex }==sets.indices.toList() && sets.zipWithNext().all { (a,b)->
                a.exerciseIndex*20+a.ordinal<b.exerciseIndex*20+b.ordinal })
            val record=if(ex.isNull("record"))null else {
                val r=ex.getJSONObject("record");WorkoutStoreRuntimeCodec.exact(r,"w","r","date","unit")
                val w=r.get("w");require(w is Number && w.toDouble().isFinite() && w.toDouble()>0)
                val u=text(r,"unit");require(u in listOf("lb","kg"))
                NativeHistoryRecord(w.toDouble(),number(r,"r",1,100).toInt(),
                    number(r,"date",1,8_640_000_000_000_000L),u)
            }
            NativeHistoryExercise(exId,text(ex,"name"),sets,record)
        }
        require(exercises.map { it.id }.toSet().size==exercises.size && exercises.sumOf { it.sets.size }<=480)
        require(exercises.flatMap { ex->ex.sets.map { it.exerciseIndex to ex.id } }
            .groupBy({it.first},{it.second}).values.all { it.distinct().size==1 })
        val total=number(v,"totalSets",1,480).toInt()
        val done=number(v,"setsDone",0,total.toLong()).toInt()
        require(exercises.sumOf { it.sets.size }<=done)
        return NativeWorkoutHistory(sha(v,"snapshotSha256"),sha(v,"entrySha256"),expectedId,
            text(v,"dayLabel"),number(v,"weekIndex",1,104).toInt(),unit,number(v,"date",1,8_640_000_000_000_000L),
            done,total,number(v,"volume",0,9_007_199_254_740_991L),exercises)
    }
    fun decodeInspect(raw: String,id: String,snapshot: String,historyId: String): NativeWorkoutHistory? =
        WorkoutStoreRuntimeCodec.result(raw,id)?.let { history(it,snapshot,historyId) }
    fun readPreparation(raw: String,snapshot: String,correctionJson: String): NativeHistoryPreparation {
        WorkoutStoreRuntimeCodec.bounded(raw,WorkoutStoreRuntimeCodec.MAX_RESPONSE_BYTES)
        val v=JSONObject(raw)
        WorkoutStoreRuntimeCodec.exact(v,"source","expectedSnapshotSha256","inputCorrectionSha256",
            "candidateSnapshotSha256","candidateSnapshot","alreadyPresent","history")
        require(text(v,"source")==SOURCE && sha(v,"expectedSnapshotSha256")==WorkoutStoreRuntimeCodec.sha(snapshot) &&
            sha(v,"inputCorrectionSha256")==WorkoutStoreRuntimeCodec.sha(correctionJson) && v.get("alreadyPresent") is Boolean)
        val candidate=WorkoutStoreRuntimeCodec.text(v,"candidateSnapshot")
        WorkoutStoreRuntimeCodec.bounded(candidate,WorkoutStoreRuntimeCodec.MAX_SNAPSHOT_BYTES)
        require(sha(v,"candidateSnapshotSha256")==WorkoutStoreRuntimeCodec.sha(candidate))
        val already=v.getBoolean("alreadyPresent");if(already)require(candidate==snapshot)
        val request=JSONObject(correctionJson)
        val result=history(v.getJSONObject("history"),candidate,text(request,"historyId"))
        // Bind publication to the requested edit as well as its transport bytes.
        // This checks transaction identity, not canonical summaries or merge policy.
        val entries=JSONObject(candidate).getJSONArray("history")
        val matches=(0 until entries.length()).map { entries.getJSONObject(it) }
            .filter { it.opt("id")==result.historyId }
        require(matches.size==1)
        val correction=matches.single().getJSONObject("nativeCorrection")
        WorkoutStoreRuntimeCodec.exact(correction,"format","operationId","requestSha256","priorEntrySha256","correctedAt")
        require(number(correction,"format",1,1)==1L &&
            text(correction,"operationId")==text(request,"operationId") &&
            sha(correction,"priorEntrySha256")==sha(request,"entrySha256"))
        sha(correction,"requestSha256")
        return NativeHistoryPreparation(candidate,already,result)
    }
    fun decodeCorrection(raw: String,id: String,snapshot: String,correctionJson: String): String? {
        val result=WorkoutStoreRuntimeCodec.result(raw,id) ?: return null
        return result.toString().also { readPreparation(it,snapshot,correctionJson) }
    }
}
