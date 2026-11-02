package io.github.extracteddeath.pursuitiron.domain

import java.util.Collections
import org.json.JSONObject

/** Warm-up positions are independent of working-set positions, including repeated lifts. */
data class JournalWarmupSlot(val exerciseIndex: Int,val warmupIndex: Int,val exerciseId: String)
data class NativeWarmupTarget(val slot: JournalWarmupSlot,val load: String,val reps: String)
data class JournalWarmupIntent(val operationId: String,val slot: JournalWarmupSlot,
    val disposition: JournalSetDisposition,val values: JournalSetValues?,val recordedAt: Long,
    val replacesOperationId: String?=null)

/** Factual warm-up receipts only. No warm-up prescriptions or training credit are computed here. */
object NativeWarmupPolicy {
    const val MAX_EVENTS=1024
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private val exercise=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")
    private val load=Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")
    fun validValues(value: JournalSetValues?,unit: String): Boolean = value!=null &&
        value.load?.let { load.matches(it) }==true && value.unit==unit && value.reps in 0..100 &&
        (value.reps>0 || value.load.toBigDecimal().signum()>0) && (value.rir==null || value.rir in 0..10)

    fun valid(intent: JournalWarmupIntent,session: JournalSessionKey): Boolean = valid(intent,session.unit,session.startedAt)
    internal fun valid(intent: JournalWarmupIntent,unit: String,startedAt: Long): Boolean =
        id.matches(intent.operationId) && intent.slot.exerciseIndex in 0..47 && intent.slot.warmupIndex in 0..19 &&
            exercise.matches(intent.slot.exerciseId) && intent.recordedAt in startedAt..8_640_000_000_000_000L &&
            (intent.replacesOperationId==null || id.matches(intent.replacesOperationId) && intent.replacesOperationId!=intent.operationId) &&
            if(intent.disposition==JournalSetDisposition.SKIPPED)intent.values==null else validValues(intent.values,unit)

    fun latest(events: List<JournalWarmupIntent>): List<JournalWarmupIntent> =
        Collections.unmodifiableList(events.associateBy { it.slot }.values.sortedWith(
            compareBy<JournalWarmupIntent> {it.slot.exerciseIndex}.thenBy {it.slot.warmupIndex}))

    internal fun append(session: JournalSessionKey,events: List<JournalWarmupIntent>,intent: JournalWarmupIntent): List<JournalWarmupIntent>? {
        if(!valid(intent,session) || events.size>=MAX_EVENTS || events.any {it.operationId==intent.operationId})return null
        val prior=events.lastOrNull {it.slot==intent.slot}
        if(intent.replacesOperationId!=prior?.operationId || prior?.let {intent.recordedAt<=it.recordedAt}==true)return null
        return Collections.unmodifiableList(events+intent)
    }

    /** Issued rows permit optional additional user-entered warm-ups, in consecutive positions. */
    fun matchesPlan(plan: NativeWorkoutPlan,events: List<JournalWarmupIntent>): Boolean {
        val seen=mutableSetOf<JournalWarmupSlot>()
        for(event in events) {
            if(!valid(event,plan.session))return false
            val row=plan.exercises.singleOrNull {it.index==event.slot.exerciseIndex && it.id==event.slot.exerciseId} ?: return false
            if(event.slot !in seen && event.slot.warmupIndex>=row.warmupTargets.size) {
                val added=seen.count {it.exerciseIndex==row.index && it.warmupIndex>=row.warmupTargets.size}
                if(event.slot.warmupIndex!=row.warmupTargets.size+added)return false
            }
            seen.add(event.slot)
        }
        return true
    }
}

object NativeWarmupCodec {
    private val wire=WorkoutStoreRuntimeCodec
    internal fun slotJson(s: JournalWarmupSlot)=JSONObject().put("exerciseIndex",s.exerciseIndex)
        .put("warmupIndex",s.warmupIndex).put("exerciseId",s.exerciseId)
    internal fun readSlot(v: JSONObject): JournalWarmupSlot {
        wire.exact(v,"exerciseIndex","warmupIndex","exerciseId")
        val exercise=wire.integer(v,"exerciseIndex");val warmup=wire.integer(v,"warmupIndex")
        require(exercise in 0..47 && warmup in 0..19)
        return JournalWarmupSlot(exercise.toInt(),warmup.toInt(),wire.text(v,"exerciseId"))
    }
    internal fun valuesJson(v: JournalSetValues?): Any=v?.let {JSONObject().put("load",it.load)
        .put("unit",it.unit).put("reps",it.reps).put("rir",it.rir ?: JSONObject.NULL)} ?: JSONObject.NULL
    internal fun readValues(v: Any,unit: String): JournalSetValues? {
        if(v==JSONObject.NULL)return null
        require(v is JSONObject);wire.exact(v,"load","unit","reps","rir")
        val reps=wire.integer(v,"reps");require(reps in 0..100)
        val rir=if(v.isNull("rir"))null else wire.integer(v,"rir").also {require(it in 0..10)}.toInt()
        return JournalSetValues(wire.text(v,"load"),wire.text(v,"unit"),reps.toInt(),rir)
            .also {require(NativeWarmupPolicy.validValues(it,unit))}
    }
    internal fun json(e: JournalWarmupIntent)=JSONObject().put("operationId",e.operationId)
        .put("slot",slotJson(e.slot)).put("disposition",e.disposition.name).put("values",valuesJson(e.values))
        .put("recordedAt",e.recordedAt).put("replacesOperationId",e.replacesOperationId ?: JSONObject.NULL)
    internal fun read(v: JSONObject,session: JournalSessionKey): JournalWarmupIntent = read(v,session.unit,session.startedAt)
    internal fun read(v: JSONObject,unit: String,startedAt: Long): JournalWarmupIntent {
        wire.exact(v,"operationId","slot","disposition","values","recordedAt","replacesOperationId")
        return JournalWarmupIntent(wire.text(v,"operationId"),readSlot(v.getJSONObject("slot")),
            JournalSetDisposition.valueOf(wire.text(v,"disposition")),readValues(v.get("values"),unit),
            wire.integer(v,"recordedAt"),if(v.isNull("replacesOperationId"))null else wire.text(v,"replacesOperationId"))
            .also {require(NativeWarmupPolicy.valid(it,unit,startedAt))}
    }
}
