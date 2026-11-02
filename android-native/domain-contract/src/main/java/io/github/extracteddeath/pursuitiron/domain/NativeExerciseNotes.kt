package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.Collections

data class NativeSetupField(val key: String,val label: String,val orphan: Boolean)
data class NativePinnedNote(val text: String,val setup: Map<String,String>,val fields: List<NativeSetupField>)
data class JournalExerciseNote(val operationId: String,val exerciseId: String,val text: String,
    val setup: Map<String,String>,val updatedAt: Long,val replacesOperationId: String?=null)

/** Per-exercise facts shared by repeated exercise slots. Never training instructions or set credit. */
object NativeExerciseNotePolicy {
    const val MAX_EVENTS=128
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private val exercise=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")
    fun validKey(key: String): Boolean = key.length in 1..64 && JournalSessionNoteCodec.validText(key) &&
        key.none {it=='\n' || it=='\t'} && key !in setOf("__proto__","constructor","prototype")
    fun validSetup(setup: Map<String,String>): Boolean = setup.size<=32 && setup.all {(key,text)->
        validKey(key) && text.length<=200 && JournalSessionNoteCodec.validText(text)
    }
    fun valid(edit: JournalExerciseNote,session: JournalSessionKey): Boolean =
        id.matches(edit.operationId) && exercise.matches(edit.exerciseId) &&
            JournalSessionNoteCodec.validText(edit.text) && validSetup(edit.setup) &&
            edit.updatedAt in session.startedAt..8_640_000_000_000_000L &&
            (edit.replacesOperationId==null || id.matches(edit.replacesOperationId) && edit.replacesOperationId!=edit.operationId)

    internal fun append(session: JournalSessionKey,events: List<JournalExerciseNote>,edit: JournalExerciseNote): List<JournalExerciseNote>? {
        if(!valid(edit,session) || events.size>=MAX_EVENTS || events.any {it.operationId==edit.operationId})return null
        val prior=events.lastOrNull {it.exerciseId==edit.exerciseId}
        if(edit.replacesOperationId!=prior?.operationId || prior?.let {edit.updatedAt<=it.updatedAt}==true)return null
        val detached=edit.copy(setup=Collections.unmodifiableMap(LinkedHashMap(edit.setup)))
        return Collections.unmodifiableList(events+detached)
    }

    fun resolved(plan: NativeWorkoutPlan,events: List<JournalExerciseNote>,exerciseId: String): NativePinnedNote? {
        val context=plan.exercises.firstOrNull {it.id==exerciseId}?.pinnedNote ?: return null
        val edit=events.lastOrNull {it.exerciseId==exerciseId} ?: return context
        return context.copy(text=edit.text,setup=edit.setup)
    }

    fun matchesPlan(plan: NativeWorkoutPlan,events: List<JournalExerciseNote>): Boolean = events.isEmpty() ||
        plan.tracksWarmups && events.all {edit->
            val rows=plan.exercises.filter {it.id==edit.exerciseId}
            val context=rows.firstOrNull()?.pinnedNote
            valid(edit,plan.session) && context!=null && rows.all {it.pinnedNote==context} &&
                edit.setup.keys.all {key->context.fields.any {it.key==key}}
        }
}

object NativeExerciseNoteCodec {
    private val wire=WorkoutStoreRuntimeCodec
    private fun equivalent(a: Any, b: Any, depth: Int=0): Boolean {
        require(depth<=64)
        return when {
            a==JSONObject.NULL || b==JSONObject.NULL -> a==JSONObject.NULL && b==JSONObject.NULL
            a is JSONObject && b is JSONObject -> {
                val keys=a.keys().asSequence().toSet()
                keys==b.keys().asSequence().toSet() && keys.all { equivalent(a.get(it),b.get(it),depth+1) }
            }
            a is JSONArray && b is JSONArray -> a.length()==b.length() &&
                (0 until a.length()).all { equivalent(a.get(it),b.get(it),depth+1) }
            a is Number && b is Number -> java.math.BigDecimal(a.toString())
                .compareTo(java.math.BigDecimal(b.toString()))==0
            else -> a==b
        }
    }


    private fun text(raw: JSONObject,key: String): String = raw.get(key).also {require(it is String)} as String
    internal fun readSetup(raw: JSONObject): Map<String,String> = Collections.unmodifiableMap(
        raw.keys().asSequence().associateWith {text(raw,it)}.also {require(NativeExerciseNotePolicy.validSetup(it))})
    internal fun setupJson(setup: Map<String,String>)=JSONObject().apply {setup.forEach {(k,v)->put(k,v)}}
    internal fun readContext(raw: JSONObject): NativePinnedNote {
        wire.exact(raw,"text","setup","fields")
        val text=text(raw,"text");require(JournalSessionNoteCodec.validText(text))
        val setup=readSetup(raw.getJSONObject("setup"));val list=raw.getJSONArray("fields")
        require(list.length() in 0..32)
        val fields=(0 until list.length()).map {i->
            val field=list.getJSONObject(i);wire.exact(field,"key","label","orphan")
            val key=wire.text(field,"key");val label=wire.text(field,"label")
            require(NativeExerciseNotePolicy.validKey(key) && label.length<=100 &&
                JournalSessionNoteCodec.validText(label) && field.get("orphan") is Boolean)
            NativeSetupField(key,label,field.getBoolean("orphan"))
        }
        require(fields.map {it.key}.distinct().size==fields.size && setup.keys.all {k->fields.any {it.key==k}})
        return NativePinnedNote(text,setup,Collections.unmodifiableList(fields))
    }
    internal fun json(edit: JournalExerciseNote)=JSONObject().put("operationId",edit.operationId)
        .put("exerciseId",edit.exerciseId).put("text",edit.text).put("setup",setupJson(edit.setup))
        .put("updatedAt",edit.updatedAt).put("replacesOperationId",edit.replacesOperationId ?: JSONObject.NULL)
    internal fun read(raw: JSONObject,session: JournalSessionKey): JournalExerciseNote {
        wire.exact(raw,"operationId","exerciseId","text","setup","updatedAt","replacesOperationId")
        return JournalExerciseNote(wire.text(raw,"operationId"),wire.text(raw,"exerciseId"),text(raw,"text"),
            readSetup(raw.getJSONObject("setup")),wire.integer(raw,"updatedAt"),
            if(raw.isNull("replacesOperationId"))null else wire.text(raw,"replacesOperationId"))
            .also {require(NativeExerciseNotePolicy.valid(it,session))}
    }
    internal fun audit(events: List<JournalExerciseNote>)=JSONObject().put("format",1)
        .put("edits",JSONArray().apply {events.forEach {put(json(it))}})

    /** Exact pinned base facts, including unknown stored setup fields, must match the origin. */
    internal fun verifyOrigin(plan: NativeWorkoutPlan,origin: String) {
        val store=JSONObject(origin)
        for(row in plan.exercises)row.pinnedNote?.let {context->
            val notes=store.optJSONObject("exNotes");val setups=store.optJSONObject("exSetup")
            val original=notes?.opt(row.id) ?: ""
            val setup=setups?.opt(row.id) ?: JSONObject()
            require(original==context.text && setup is JSONObject && readSetup(setup)==context.setup)
        }
    }

    /** The JS candidate may change only the exact exercise entries authorized by the journal. */
    internal fun verifyPublication(plan: NativeWorkoutPlan,origin: String,candidate: String,entry: JSONObject,
        events: List<JournalExerciseNote>) {
        require(NativeExerciseNotePolicy.matchesPlan(plan,events))
        if(events.isEmpty())require(!entry.has("nativeExerciseNotes"))
        else require(equivalent(entry.getJSONObject("nativeExerciseNotes"),audit(events)))
        val before=JSONObject(origin);val after=JSONObject(candidate)
        for(key in listOf("exNotes","exSetup")) {
            val expected=JSONObject((before.optJSONObject(key) ?: JSONObject()).toString())
            for(edit in events)expected.put(edit.exerciseId,if(key=="exNotes")edit.text else setupJson(edit.setup))
            require(equivalent(after.optJSONObject(key) ?: JSONObject(),expected))
        }
    }
}
