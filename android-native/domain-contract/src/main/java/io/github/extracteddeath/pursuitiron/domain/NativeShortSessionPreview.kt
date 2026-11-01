package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import java.util.Collections

/** Exact, non-persistent canonical engine preview. Not a workout authorization. */
data class NativeShortSessionCut(
    val slot: Int,val name: String,val fromSets: Int,val toSets: Int,val amplifierRemoved: Boolean
)
data class NativeShortSessionPreview(
    val snapshotSha256: String,val programId: String,val dayId: String,val weekIndex: Int,
    val minutes: Int,val originalMinutes: Int,val estimatedMinutes: Int,
    val fits: Boolean,val changed: Boolean,val cuts: List<NativeShortSessionCut>
)
object NativeShortSessionPreviewCodec {
    private val wire=WorkoutStoreRuntimeCodec
    private val identifier=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    /** Optimistic UI is never a start authorization; recheck owned current bytes. */
    fun matchesSource(preview: NativeShortSessionPreview,snapshot: String,
        programId: String,minutes: Int): Boolean =
        preview.programId==programId && preview.minutes==minutes && preview.fits &&
            preview.changed && preview.snapshotSha256==wire.sha(snapshot)

    fun encode(id: String,snapshot: String,programId: String,minutes: Int): String {
        require(identifier.matches(programId) && minutes in listOf(20,30,45))
        return wire.encode(id,"previewNativeShorterSession",
            JSONObject().put("rawSnapshot",snapshot).put("programId",programId).put("minutes",minutes))
    }
    fun decode(raw: String,id: String,snapshot: String,programId: String,minutes: Int): NativeShortSessionPreview? {
        require(minutes in listOf(20,30,45) && identifier.matches(programId))
        wire.bounded(snapshot,wire.MAX_SNAPSHOT_BYTES)
        val value=wire.result(raw,id) ?: return null
        wire.exact(value,"source","snapshotSha256","programId","dayId","weekIndex",
            "minutes","originalMinutes","estimatedMinutes","fits","changed","cuts","readOnly","applied")
        require(wire.text(value,"source")=="modules/engine-api.js:planShorterSession" &&
            wire.text(value,"snapshotSha256")==wire.sha(snapshot) &&
            wire.text(value,"programId")==programId &&
            identifier.matches(wire.text(value,"dayId")) &&
            value.get("readOnly")==true && value.get("applied")==false &&
            value.get("fits") is Boolean && value.get("changed") is Boolean)
        fun count(v: JSONObject,k: String,min: Long,max: Long): Int =
            wire.integer(v,k).also { require(it in min..max) }.toInt()
        val week=count(value,"weekIndex",1,104)
        val chosen=count(value,"minutes",20,45)
        require(chosen==minutes && chosen in listOf(20,30,45))
        val full=count(value,"originalMinutes",0,10000)
        val estimated=count(value,"estimatedMinutes",0,10000)
        require(estimated<=full && value.getBoolean("fits")== (estimated<=chosen))
        val rawCuts=value.getJSONArray("cuts")
        require(rawCuts.length()<=48)
        val cuts=(0 until rawCuts.length()).map { i->
            val cut=rawCuts.getJSONObject(i)
            wire.exact(cut,"slot","name","fromSets","toSets","amplifierRemoved")
            val slot=count(cut,"slot",0,47)
            val name=wire.text(cut,"name")
            require(name.length in 1..200 && cut.get("amplifierRemoved") is Boolean)
            val from=count(cut,"fromSets",1,20);val to=count(cut,"toSets",0,from.toLong())
            require(from>to || cut.getBoolean("amplifierRemoved"))
            NativeShortSessionCut(slot,name,from,to,cut.getBoolean("amplifierRemoved"))
        }
        require(cuts.map { it.slot }.distinct().size==cuts.size &&
            value.getBoolean("changed")==cuts.isNotEmpty())
        return NativeShortSessionPreview(wire.text(value,"snapshotSha256"),programId,
            wire.text(value,"dayId"),week,minutes,full,estimated,
            value.getBoolean("fits"),value.getBoolean("changed"),
            Collections.unmodifiableList(cuts))
    }
}
