package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import java.math.BigDecimal

/** A non-writing loading suggestion for one actual engine-issued set. */
data class NativeEquipmentLoading(
    val workoutId:String,val slot:JournalSetSlot,val inputLoad:String,
    val unit:String,val mode:String,val family:String?,
    val confirmed:Boolean,val exact:Boolean,
    val previous:String?,val next:String?
)

object NativeEquipmentLoadingCodec {
    private val wire=WorkoutStoreRuntimeCodec
    private val load=Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")
    private val family=setOf("exercise_override","barbell_plates","dumbbells_per_hand",
        "cable_stack","machine_stack","smith_confirmed_totals")
    private val mode=setOf("unconfirmed","bodyweight","external_load",
        "barbell_total","dumbbell_per_hand","cable_stack","machine_stack","smith_total")
    fun matchesOrigin(source:String,plan:NativeWorkoutPlan):Boolean =
        wire.sha(source)==plan.session.programRevision.snapshotSha256

    fun encode(id:String,source:String,issued:String,slot:JournalSetSlot,input:String):String {
        val plan=NativeWorkoutCodec.readIssued(issued)
        require(matchesOrigin(source,plan) && load.matches(input) && slot in plan.slots)
        return wire.encode(id,"inspectNativeEquipmentLoad",JSONObject()
            .put("rawSnapshot",source).put("issuedWorkout",issued)
            .put("slot",JSONObject().put("exerciseIndex",slot.exerciseIndex)
                .put("setIndex",slot.setIndex).put("exerciseId",slot.exerciseId))
            .put("inputLoad",input))
    }

    fun decode(raw:String,id:String,source:String,issued:String,
        slot:JournalSetSlot,input:String):NativeEquipmentLoading? {
        val plan=NativeWorkoutCodec.readIssued(issued)
        require(matchesOrigin(source,plan) && slot in plan.slots && load.matches(input))
        val v=wire.result(raw,id) ?: return null
        wire.exact(v,"source","snapshotSha256","issuedWorkoutSha256","workoutId",
            "slot","unit","inputLoad","mode","family","confirmed","exact","previous","next",
            "readOnly","applied")
        require(wire.text(v,"source")=="modules/next-engine/loading.js:confirmed-inventory" &&
            wire.text(v,"snapshotSha256")==wire.sha(source) &&
            wire.text(v,"issuedWorkoutSha256")==wire.sha(issued) &&
            wire.text(v,"workoutId")==plan.session.workoutId &&
            wire.text(v,"unit")==plan.session.unit &&
            wire.text(v,"inputLoad")==input && v.get("readOnly")==true &&
            v.get("applied")==false && v.get("confirmed") is Boolean &&
            v.get("exact") is Boolean)
        val position=v.getJSONObject("slot")
        wire.exact(position,"exerciseIndex","setIndex","exerciseId")
        require(wire.integer(position,"exerciseIndex")==slot.exerciseIndex.toLong() &&
            wire.integer(position,"setIndex")==slot.setIndex.toLong() &&
            wire.text(position,"exerciseId")==slot.exerciseId)
        val modeValue=wire.text(v,"mode")
        require(modeValue in mode)
        val familyValue=if(v.isNull("family"))null else wire.text(v,"family")
        val verified=v.getBoolean("confirmed")
        val exact=v.getBoolean("exact")
        val before=readLoad(v,"previous")
        val after=readLoad(v,"next")
        if(!verified)require(familyValue==null && !exact && before==null && after==null)
        else require(familyValue in family)
        val value=BigDecimal(input)
        if(before!=null)require(BigDecimal(before)<value)
        if(after!=null)require(BigDecimal(after)>value)
        return NativeEquipmentLoading(plan.session.workoutId,slot,input,plan.session.unit,
            modeValue,familyValue,verified,exact,before,after)
    }
    private fun readLoad(v:JSONObject,key:String):String? {
        if(v.isNull(key))return null
        val item=v.get(key)
        require(item is Number)
        val value=BigDecimal(item.toString()).stripTrailingZeros().toPlainString()
        require(load.matches(value))
        return value
    }
}
