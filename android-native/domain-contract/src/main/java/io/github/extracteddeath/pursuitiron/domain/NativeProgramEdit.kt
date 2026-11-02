package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

interface NativeProgramEditEngine {
    suspend fun prepareProgramEdit(snapshot: String,editJson: String): NativeProgramEditPreparation?
}

class NativeProgramEditPreparation internal constructor(
    val candidate: String,val alreadyPresent: Boolean,val program: ReadOnlyProgramSummary
)

/** Revision, request and retained-store identity checks. No Kotlin training rules. */
object NativeProgramEditCodec {
    private val wire=WorkoutStoreRuntimeCodec
    fun editJson(draft: NativeProgramEditDraft,operationId: String,editedAt: Long): String {
        require(draft.isChanged && operationId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")))
        val r=draft.reference
        require(editedAt>r.programUpdatedAt && editedAt>r.storeSavedAt && editedAt<=8_640_000_000_000_000L)
        val reference=JSONObject().put("programId",r.programId).put("programUpdatedAt",r.programUpdatedAt)
            .put("storeSavedAt",r.storeSavedAt).put("storeVersion",r.storeVersion).put("snapshotSha256",r.snapshotSha256)
        return JSONObject().put("format",1).put("operationId",operationId).put("reference",reference)
            .put("editedAt",editedAt).put("name",draft.name).put("days",JSONArray().apply {
                draft.days.forEach { d->put(JSONObject().put("id",d.id).put("label",d.label)
                    .put("slots",JSONArray(draft.originalSlots(d.id)))) }
            }).toString().also { wire.bounded(it,32768) }
    }

    /** SavedState-only draft. This contains neither an operation nor save approval. */
    fun workspaceJson(draft: NativeProgramEditDraft): String=JSONObject()
        .put("snapshotSha256",draft.reference.snapshotSha256).put("programId",draft.reference.programId)
        .put("name",draft.name).put("days",JSONArray().apply {
            draft.days.forEach { d->put(JSONObject().put("id",d.id).put("label",d.label)
                .put("slots",JSONArray(draft.originalSlots(d.id)))) }
        }).toString()

    fun restoreWorkspace(verified: ReadOnlyProgramPresentation.Structure,raw: String): NativeProgramEditDraft? =
        runCatching {
            wire.bounded(raw,32768)
            val v=JSONObject(raw);wire.exact(v,"snapshotSha256","programId","name","days")
            val original=NativeProgramEditDraft.from(verified)
            require(v.get("snapshotSha256")==original.reference.snapshotSha256 && v.get("programId")==original.reference.programId)
            var draft=checkNotNull(original.renameProgram(wire.text(v,"name")))
            val rows=v.getJSONArray("days");require(rows.length()==original.days.size)
            val seen=mutableSetOf<String>()
            for(index in 0 until rows.length()) {
                val row=rows.getJSONObject(index);wire.exact(row,"id","label","slots")
                val id=wire.text(row,"id");require(seen.add(id))
                var position=draft.days.indexOfFirst { it.id==id };require(position>=0)
                while(position>index) {draft=checkNotNull(draft.moveDay(position,-1));position--}
                draft=checkNotNull(draft.renameDay(id,wire.text(row,"label")))
                val slots=row.getJSONArray("slots")
                val expected=original.originalSlots(id)
                val desired=(0 until slots.length()).map {
                    wire.integer(JSONObject().put("n",slots.get(it)),"n").also { require(it in 0..29) }.toInt()
                }
                require(desired.sorted()==expected)
                for(target in desired.indices) {
                    var current=draft.originalSlots(id).indexOf(desired[target]);require(current>=target)
                    while(current>target) {draft=checkNotNull(draft.moveExercise(id,current,-1));current--}
                }
            }
            draft
        }.getOrNull()

    fun encode(id: String,snapshot: String,editJson: String)=wire.encode(id,"prepareNativeProgramEdit",
        JSONObject().put("currentSnapshot",snapshot).put("editJson",editJson))

    fun decode(raw: String,id: String,snapshot: String,editJson: String): NativeProgramEditPreparation? {
        val value=wire.result(raw,id) ?: return null
        wire.exact(value,"source","preparedOnly","approvedForWorkoutExecution","operationId","requestSha256",
            "sourceSnapshotSha256","candidateSnapshot","candidateSnapshotSha256","alreadyPresent","program")
        val request=JSONObject(editJson);val ref=request.getJSONObject("reference")
        val programId=wire.text(ref,"programId")
        val candidate=wire.text(value,"candidateSnapshot");wire.bounded(candidate,wire.MAX_SNAPSHOT_BYTES)
        require(value.get("source")=="modules/engine-api.js:programSavePlan" && value.get("preparedOnly")==true &&
            value.get("approvedForWorkoutExecution")==false && value.get("operationId")==request.get("operationId") &&
            value.get("requestSha256")==wire.sha(editJson) && value.get("sourceSnapshotSha256")==wire.sha(snapshot) &&
            value.get("candidateSnapshotSha256")==wire.sha(candidate) && value.get("alreadyPresent") is Boolean)
        val already=value.getBoolean("alreadyPresent")
        val c=JSONObject(candidate);val original=JSONObject(snapshot)
        val audit=c.getJSONObject("nativeProgramEdit")
        wire.exact(audit,"format","operationId","requestSha256","sourceSnapshotSha256","programId","editedAt")
        require(wire.integer(audit,"format")==1L && audit.get("operationId")==request.get("operationId") &&
            audit.get("requestSha256")==wire.sha(editJson) && audit.get("sourceSnapshotSha256")==ref.get("snapshotSha256") &&
            audit.get("programId")==programId && audit.get("editedAt")==request.get("editedAt"))
        if(already)require(candidate==snapshot)
        else {
            require(ref.get("snapshotSha256")==wire.sha(snapshot) && c.get("savedAt")==request.get("editedAt"))
            // History, PR/performance caches, cycles, other programs, settings,
            // deletion clocks and onboarding provenance must all survive.
            val ignored=setOf("saved","savedAt","nativeProgramEdit")
            require(original.keys().asSequence().toSet()-ignored==c.keys().asSequence().toSet()-ignored)
            original.keys().asSequence().filter { it !in ignored }.forEach { key->
                require(equivalent(original.get(key),c.get(key)))
            }
            val before=original.getJSONArray("saved");val after=c.getJSONArray("saved")
            require(before.length()==after.length())
            var matching=0
            for(i in 0 until before.length()) {
                val a=before.getJSONObject(i);val b=after.getJSONObject(i)
                if(a.get("id")==programId) {
                    matching++
                    require(b.get("id")==programId && b.get("updatedAt")==request.get("editedAt"))
                    val editable=setOf("name","updatedAt","days","edited","overrides","nextWeekPrescriptions","progStyle",
                        "slotBias","autoBias","rounds","weekOff","pairs","ss")
                    require(a.keys().asSequence().toSet()-editable==b.keys().asSequence().toSet()-editable)
                    a.keys().asSequence().filter { it !in editable }.forEach { key->
                        require(equivalent(a.get(key),b.get(key)))
                    }
                    val originalDays=a.getJSONArray("days");val editedDays=b.getJSONArray("days")
                    val requestedDays=request.getJSONArray("days")
                    require(originalDays.length()==editedDays.length() && editedDays.length()==requestedDays.length())
                    val ids=mutableSetOf<String>()
                    for(dayIndex in 0 until editedDays.length()) {
                        val day=editedDays.getJSONObject(dayIndex);val row=requestedDays.getJSONObject(dayIndex)
                        val dayId=wire.text(row,"id");require(ids.add(dayId) && day.get("id")==dayId)
                        val old=(0 until originalDays.length()).map { originalDays.getJSONObject(it) }
                            .single { it.get("id")==dayId }
                        val dayFields=setOf("label","exercises","primaryIndex")
                        require(old.keys().asSequence().toSet()-dayFields==day.keys().asSequence().toSet()-dayFields)
                        old.keys().asSequence().filter { it !in dayFields }.forEach { key->
                            require(equivalent(old.get(key),day.get(key)))
                        }
                        val slots=row.getJSONArray("slots");val priorExercises=old.getJSONArray("exercises")
                        val exercises=day.getJSONArray("exercises")
                        require(slots.length()==priorExercises.length() && exercises.length()==slots.length())
                        val from=(0 until slots.length()).map { slots.getInt(it) }
                        require(from.sorted()==(0 until slots.length()).toList())
                        from.forEachIndexed { to,source->require(exercises.get(to)==priorExercises.get(source)) }
                        require(day.get("label")==row.get("label"))
                        if(old.has("primaryIndex"))require(day.getInt("primaryIndex")==from.indexOf(old.getInt("primaryIndex")))
                        else require(!day.has("primaryIndex"))
                    }

                } else require(equivalent(a,b))
            }
            require(matching==1)
        }
        val summary=value.getJSONObject("program");val r=summary.getJSONObject("reference")
        val expected=ReadOnlyProgramReference(programId,wire.integer(r,"programUpdatedAt"),
            wire.integer(r,"storeSavedAt"),13,wire.sha(candidate))
        val program=ReadOnlyProgramTransportCodec.decode(summary.toString(),expected)
        require(program.name==request.get("name"))
        val days=request.getJSONArray("days")
        require(program.days.size==days.length())
        program.days.forEachIndexed { i,d->
            val row=days.getJSONObject(i)
            require(d.id==row.get("id") && d.label==row.get("label"))
        }
        return NativeProgramEditPreparation(candidate,already,program)
    }

    private fun equivalent(a: Any,b: Any,depth: Int=0): Boolean {
        require(depth<=64)
        return when {
            a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet() &&
                a.keys().asSequence().all { equivalent(a.get(it),b.get(it),depth+1) }
            a is JSONArray && b is JSONArray -> a.length()==b.length() &&
                (0 until a.length()).all { equivalent(a.get(it),b.get(it),depth+1) }
            a is Number && b is Number -> a.toString().toBigDecimal().compareTo(b.toString().toBigDecimal())==0
            else -> a==b
        }
    }
}
