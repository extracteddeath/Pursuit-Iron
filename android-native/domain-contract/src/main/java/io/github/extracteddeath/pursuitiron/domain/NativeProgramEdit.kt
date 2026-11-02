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
        return JSONObject().put("format",if(draft.replacement!=null) 3
            else if(draft.removedMovementCount>0) 2 else 1)
            .put("operationId",operationId).put("reference",reference)
            .put("editedAt",editedAt).put("name",draft.name).put("days",JSONArray().apply {
                draft.days.forEach { d->put(JSONObject().put("id",d.id).put("label",d.label)
                    .put("slots",JSONArray(draft.originalSlots(d.id)))) }
            }).apply {
                draft.replacement?.let { put("replacement",JSONObject().put("dayId",it.dayId)
                    .put("sourceSlot",it.originalSlot).put("exerciseId",it.exerciseId)) }
            }.toString().also { wire.bounded(it,32768) }
    }

    /** SavedState-only draft. This contains neither an operation nor save approval. */
    fun workspaceJson(draft: NativeProgramEditDraft): String=JSONObject()
        .put("snapshotSha256",draft.reference.snapshotSha256).put("programId",draft.reference.programId)
        .put("name",draft.name).put("days",JSONArray().apply {
            draft.days.forEach { d->put(JSONObject().put("id",d.id).put("label",d.label)
                .put("slots",JSONArray(draft.originalSlots(d.id)))) }
        }).apply {
            draft.replacement?.let { put("replacement",JSONObject().put("dayId",it.dayId)
                .put("sourceSlot",it.originalSlot).put("exerciseId",it.exerciseId)) }
        }.toString()

    fun restoreWorkspace(verified: ReadOnlyProgramPresentation.Structure,raw: String): NativeProgramEditDraft? =
        runCatching {
            wire.bounded(raw,32768)
            val v=JSONObject(raw)
            if(v.has("replacement"))wire.exact(v,"snapshotSha256","programId","name","days","replacement")
            else wire.exact(v,"snapshotSha256","programId","name","days")
            val original=NativeProgramEditDraft.from(verified)
            require(v.get("snapshotSha256")==original.reference.snapshotSha256 && v.get("programId")==original.reference.programId)
            if(v.has("replacement")) {
                val change=v.getJSONObject("replacement")
                wire.exact(change,"dayId","sourceSlot","exerciseId")
                require(v.get("name")==original.name)
                val rows=v.getJSONArray("days")
                require(rows.length()==original.days.size)
                for(i in original.days.indices) {
                    val row=rows.getJSONObject(i);wire.exact(row,"id","label","slots")
                    val day=original.days[i];val slots=row.getJSONArray("slots")
                    require(row.get("id")==day.id && row.get("label")==day.label &&
                        slots.length()==day.exerciseIds.size &&
                        (0 until slots.length()).all { slots.getInt(it)==it })
                }
                return@runCatching checkNotNull(original.substituteExercise(
                    wire.text(change,"dayId"),wire.integer(change,"sourceSlot").toInt(),
                    wire.text(change,"exerciseId")))
            }
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
                require(desired.isNotEmpty() && desired.size<=expected.size &&
                    desired.distinct().size==desired.size && desired.all { it in expected })
                // Restore the surviving source slots, even when two have the same ID.
                for(source in expected.asReversed()) if(source !in desired) {
                    val current=draft.originalSlots(id).indexOf(source)
                    draft=checkNotNull(draft.removeExercise(id,current))
                }
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
        val format=wire.integer(request,"format")
        require(format in 1L..3L)
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
                    if(format==3L) {
                        verifyReplacement(a,b,request)
                        continue
                    }
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
                        require(slots.length() in 1..priorExercises.length() && exercises.length()==slots.length())
                        val format=wire.integer(request,"format")
                        require(format==2L || (format==1L && slots.length()==priorExercises.length()))
                        val from=(0 until slots.length()).map { slots.getInt(it) }
                        require(from.sorted()==(0 until slots.length()).toList())
                        from.forEachIndexed { to,source->require(exercises.get(to)==priorExercises.get(source)) }
                        require(day.get("label")==row.get("label"))
                        if(old.has("primaryIndex")) {
                            val primary=from.indexOf(old.getInt("primaryIndex"))
                            require(primary>=0 && day.getInt("primaryIndex")==primary)
                        }
                        else require(!day.has("primaryIndex"))
                    }
                    // Verify every slot store independently: removed slot keys
                    // disappear and surviving slot keys retain their exact values.
                    val byDay=(0 until requestedDays.length()).associate { index->
                        val row=requestedDays.getJSONObject(index)
                        val slots=row.getJSONArray("slots")
                        wire.text(row,"id") to (0 until slots.length()).map { slots.getInt(it) }
                    }
                    val indexed=Regex("^([^:]+):([0-9]+)(:.*)?$")
                    for(field in listOf("overrides","nextWeekPrescriptions","progStyle",
                        "slotBias","autoBias","rounds","weekOff","pairs","ss")) {
                        if(!a.has(field)) {require(!b.has(field));continue}
                        val originalStore=a.getJSONObject(field)
                        val candidateStore=b.getJSONObject(field)
                        val rebuilt=JSONObject()
                        originalStore.keys().asSequence().forEach { key->
                            val parts=indexed.matchEntire(key)
                            val source=parts?.groupValues?.get(2)?.toIntOrNull()
                            val dayId=parts?.groupValues?.get(1)
                            val slots=dayId?.let { byDay[it] }
                            if(slots!=null && source!=null) {
                                val moved=slots.indexOf(source)
                                if(moved>=0) rebuilt.put("${dayId}:${moved}${parts.groupValues[3]}",originalStore.get(key))
                            } else rebuilt.put(key,originalStore.get(key))
                        }
                        require(equivalent(rebuilt,candidateStore))
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
        if(format==3L) {
            val change=request.getJSONObject("replacement")
            val day=program.days.single { it.id==wire.text(change,"dayId") }
            require(day.exerciseIds[wire.integer(change,"sourceSlot").toInt()]==change.get("exerciseId"))
        }
        return NativeProgramEditPreparation(candidate,already,program)
    }

    /** Independently reconstruct the one authorized identity change. */
    private fun verifyReplacement(before: JSONObject,after: JSONObject,request: JSONObject) {
        wire.exact(request,"format","operationId","reference","editedAt","name","days","replacement")
        val change=request.getJSONObject("replacement")
        wire.exact(change,"dayId","sourceSlot","exerciseId")
        val dayId=wire.text(change,"dayId")
        val index=wire.integer(change,"sourceSlot").toInt()
        require(index in 0..29)
        val target=wire.text(change,"exerciseId")
        require(target.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")) &&
            before.get("name")==request.get("name") && after.get("name")==before.get("name") &&
            after.get("edited")==true)
        val mutable=setOf("updatedAt","edited","days","overrides",
            "nextWeekPrescriptions","progStyle","slotBias","autoBias","rounds",
            "weekOff","pairs","ss")
        require(before.keys().asSequence().toSet()-mutable==after.keys().asSequence().toSet()-mutable)
        before.keys().asSequence().filter { it !in mutable }.forEach { key->
            require(equivalent(before.get(key),after.get(key)))
        }
        val originals=before.getJSONArray("days");val changed=after.getJSONArray("days")
        val requested=request.getJSONArray("days")
        require(originals.length()==changed.length() && originals.length()==requested.length())
        var replacements=0
        for(dayIndex in 0 until originals.length()) {
            val old=originals.getJSONObject(dayIndex)
            val new=changed.getJSONObject(dayIndex)
            val row=requested.getJSONObject(dayIndex)
            wire.exact(row,"id","label","slots")
            val id=wire.text(old,"id")
            val from=old.getJSONArray("exercises");val to=new.getJSONArray("exercises")
            val slots=row.getJSONArray("slots")
            require(old.get("id")==new.get("id") && row.get("id")==id &&
                old.get("label")==new.get("label") && row.get("label")==old.get("label") &&
                slots.length()==from.length() && from.length()==to.length() &&
                (0 until slots.length()).all { slots.getInt(it)==it })
            val fields=setOf("exercises")
            require(old.keys().asSequence().toSet()-fields==new.keys().asSequence().toSet()-fields)
            old.keys().asSequence().filter { it !in fields }.forEach { key->
                require(equivalent(old.get(key),new.get(key)))
            }
            for(slot in 0 until from.length()) {
                if(id==dayId && slot==index) {
                    replacements++
                    require(from.get(slot)!=target && to.get(slot)==target &&
                        (0 until from.length()).none { it!=slot && from.get(it)==target })
                } else require(equivalent(from.get(slot),to.get(slot)))
            }
        }
        require(replacements==1)
        val sourceKey=dayId+":"+index
        val prior=before.optJSONObject("overrides") ?: JSONObject()
        val now=after.getJSONObject("overrides")
        val newMeta=now.getJSONObject(sourceKey)
        wire.exact(newMeta,"nextEngine","legacyExerciseId","nextExerciseId")
        require(newMeta.get("nextEngine")==true && newMeta.get("legacyExerciseId")==target &&
            wire.text(newMeta,"nextExerciseId").isNotBlank())
        fun withoutOriginalSlot(map: JSONObject): JSONObject = JSONObject().also { kept->
            map.keys().asSequence().forEach { key->
                if(key!=sourceKey && !key.startsWith(sourceKey+":"))
                    kept.put(key,map.get(key))
            }
        }
        val priorOthers=withoutOriginalSlot(prior)
        val currentOthers=JSONObject(now.toString()).apply { remove(sourceKey) }
        require(equivalent(priorOthers,currentOthers))
        for(field in listOf("nextWeekPrescriptions","progStyle","slotBias","autoBias","rounds",
            "weekOff","pairs","ss")) {
            require(before.has(field)==after.has(field))
            if(before.has(field)) {
                val expected=withoutOriginalSlot(before.getJSONObject(field))
                require(equivalent(expected,after.getJSONObject(field)))
            }
        }
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
