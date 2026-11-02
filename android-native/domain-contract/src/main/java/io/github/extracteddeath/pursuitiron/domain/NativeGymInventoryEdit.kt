package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

interface NativeGymInventoryEditEngine {
    suspend fun prepareGymInventoryEdit(snapshot: String, editJson: String): NativeGymInventoryEditPreparation?
}

class NativeGymInventoryEditPreparation internal constructor(
    val candidate: String, val alreadyPresent: Boolean, val program: ReadOnlyProgramSummary
)

/** A saved gym is only physical stock. Never recompute prescriptions in Kotlin. */
object NativeGymInventoryEditCodec {
    private val wire = WorkoutStoreRuntimeCodec

    fun editJson(reference: ReadOnlyProgramReference, unit: String,
        inventoryJson: String?, operationId: String, editedAt: Long): String {
        require(operationId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")) &&
            reference.storeVersion == 13 && editedAt > reference.storeSavedAt &&
            editedAt > reference.programUpdatedAt && editedAt <= 8_640_000_000_000_000L)
        val inventory = inventoryJson?.let { JSONObject(NativeGymInventoryCodec.validate(it,unit)) }
        val r = JSONObject().put("programId",reference.programId)
            .put("programUpdatedAt",reference.programUpdatedAt)
            .put("storeSavedAt",reference.storeSavedAt)
            .put("storeVersion",reference.storeVersion)
            .put("snapshotSha256",reference.snapshotSha256)
        return JSONObject().put("version",1).put("operationId",operationId)
            .put("reference",r).put("editedAt",editedAt)
            .put("loadingInventory",inventory ?: JSONObject.NULL).toString()
            .also { wire.bounded(it,16384) }
    }

    fun encode(id: String, snapshot: String, editJson: String) =
        wire.encode(id,"prepareNativeGymInventoryEdit",
            JSONObject().put("currentSnapshot",snapshot).put("editJson",editJson))

    fun decode(raw: String, id: String, snapshot: String, editJson: String): NativeGymInventoryEditPreparation? {
        val v = wire.result(raw,id) ?: return null
        wire.exact(v,"source","preparedOnly","approvedForWorkoutExecution","operationId","requestSha256",
            "sourceSnapshotSha256","candidateSnapshot","candidateSnapshotSha256","alreadyPresent","program")
        require(wire.text(v,"source")=="modules/engine-api.js:parseStoredData:native-gym-inventory" &&
            v.get("preparedOnly")==true && v.get("approvedForWorkoutExecution")==false &&
            v.get("alreadyPresent") is Boolean && wire.text(v,"sourceSnapshotSha256")==wire.sha(snapshot) &&
            wire.text(v,"requestSha256")==wire.sha(editJson))
        val request=JSONObject(editJson)
        wire.exact(request,"version","operationId","reference","editedAt","loadingInventory")
        val ref=request.getJSONObject("reference")
        wire.exact(ref,"programId","programUpdatedAt","storeSavedAt","storeVersion","snapshotSha256")
        require(wire.integer(request,"version")==1L &&
            v.get("operationId")==request.get("operationId"))
        val programId=wire.text(ref,"programId")
        val original=JSONObject(snapshot)
        val candidate=wire.text(v,"candidateSnapshot")
        wire.bounded(candidate,wire.MAX_SNAPSHOT_BYTES)
        require(wire.text(v,"candidateSnapshotSha256")==wire.sha(candidate))
        val changed=JSONObject(candidate)
        val audit=changed.getJSONObject("nativeGymInventoryEdit")
        wire.exact(audit,"version","operationId","requestSha256","sourceSnapshotSha256","programId","editedAt")
        require(wire.integer(audit,"version")==1L && audit.get("operationId")==request.get("operationId") &&
            audit.get("requestSha256")==wire.sha(editJson) &&
            audit.get("sourceSnapshotSha256")==ref.get("snapshotSha256") &&
            audit.get("programId")==programId && audit.get("editedAt")==request.get("editedAt"))
        val already=v.getBoolean("alreadyPresent")
        if(already)require(candidate==snapshot)
        else {
            require(ref.get("snapshotSha256")==wire.sha(snapshot) &&
                changed.get("savedAt")==request.get("editedAt"))
            val untouched=setOf("saved","savedAt","nativeGymInventoryEdit")
            require(keys(original)-untouched==keys(changed)-untouched)
            keys(original).filter { it !in untouched }.forEach {
                require(equivalent(original.get(it),changed.get(it)))
            }
            val oldRows=original.getJSONArray("saved")
            val newRows=changed.getJSONArray("saved")
            require(oldRows.length()==newRows.length())
            var matches=0
            repeat(oldRows.length()) { i ->
                val old=oldRows.getJSONObject(i)
                val next=newRows.getJSONObject(i)
                if(old.get("id")==programId) {
                    matches++
                    require(next.get("updatedAt")==request.get("editedAt"))
                    val ignored=setOf("updatedAt","config")
                    require(keys(old)-ignored==keys(next)-ignored)
                    (keys(old)-ignored).forEach { require(equivalent(old.get(it),next.get(it))) }
                    val oldConfig=old.getJSONObject("config")
                    val newConfig=next.getJSONObject("config")
                    val adjustable="loadingInventory"
                    require(keys(oldConfig)-adjustable==keys(newConfig)-adjustable)
                    (keys(oldConfig)-adjustable).forEach {
                        require(equivalent(oldConfig.get(it),newConfig.get(it)))
                    }
                    require(if(request.isNull("loadingInventory")) !newConfig.has(adjustable)
                        else newConfig.has(adjustable) &&
                            equivalent(request.getJSONObject(adjustable),newConfig.getJSONObject(adjustable)))
                    val unit=wire.text(oldConfig,"unit")
                    if(!request.isNull(adjustable))
                        NativeGymInventoryCodec.validate(newConfig.getJSONObject(adjustable).toString(),unit)
                } else require(equivalent(old,next))
            }
            require(matches==1)
        }
        val summary=v.getJSONObject("program")
        val r=summary.getJSONObject("reference")
        val expected=ReadOnlyProgramReference(programId,wire.integer(r,"programUpdatedAt"),
            wire.integer(r,"storeSavedAt"),13,wire.sha(candidate))
        val projected=ReadOnlyProgramTransportCodec.decode(summary.toString(),expected)
        require(projected.reference.programId==programId &&
            projected.reference.snapshotSha256==wire.sha(candidate))
        return NativeGymInventoryEditPreparation(candidate,already,projected)
    }

    private fun keys(o:JSONObject)=o.keys().asSequence().toSet()
    private fun equivalent(a:Any,b:Any,depth:Int=0):Boolean {
        require(depth<=64)
        return when {
            a is JSONObject && b is JSONObject -> keys(a)==keys(b) &&
                keys(a).all { equivalent(a.get(it),b.get(it),depth+1) }
            a is JSONArray && b is JSONArray -> a.length()==b.length() &&
                (0 until a.length()).all { equivalent(a.get(it),b.get(it),depth+1) }
            a is Number && b is Number -> a.toString().toBigDecimal()
                .compareTo(b.toString().toBigDecimal())==0
            else -> a==b
        }
    }
}
