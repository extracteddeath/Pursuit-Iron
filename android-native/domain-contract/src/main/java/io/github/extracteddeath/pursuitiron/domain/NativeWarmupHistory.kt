package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

data class NativeHistoryWarmup(val slot: JournalWarmupSlot,val name: String,val targetLoad: String?,
    val targetReps: String?,val disposition: JournalSetDisposition?,val values: JournalSetValues?)
data class NativeWarmupChange(val slot: JournalWarmupSlot,val disposition: JournalSetDisposition,val values: JournalSetValues?)

/** Warm-up identity, original-target retention and observation transport. No training calculations. */
object NativeWarmupHistoryCodec {
    private val wire=WorkoutStoreRuntimeCodec
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private fun entry(snapshot: String,id: String): JSONObject {
        val rows=JSONObject(snapshot).getJSONArray("history")
        return (0 until rows.length()).map {rows.getJSONObject(it)}.single {it.get("id")==id}
    }
    internal fun issuedFacts(plan: NativeWorkoutPlan,events: List<JournalWarmupIntent>): JSONObject {
        val rows=JSONObject(plan.issuedJson).getJSONArray("rows")
        return JSONObject().put("format",1).put("exercises",JSONArray().apply {
            for(i in 0 until rows.length()) {
                val row=rows.getJSONObject(i);val sets=row.getJSONArray("sets")
                put(JSONObject().put("exerciseIndex",row.get("slot")).put("exerciseId",row.get("id")).put("name",row.get("name"))
                    .put("targets",JSONArray().apply {for(j in 0 until sets.length())if(sets.getJSONObject(j).getBoolean("warm"))put(sets.get(j))}))
            }
        }).put("events",JSONArray().apply {events.forEach {put(NativeWarmupCodec.json(it))}})
    }

    private data class Exercise(val index: Int,val id: String,val name: String,val targets: List<Pair<String,String>>)
    private fun project(entry: JSONObject): List<NativeHistoryWarmup> {
        val facts=entry.getJSONObject("nativeWarmups");wire.exact(facts,"format","exercises","events")
        require(wire.integer(facts,"format")==1L)
        val unit=wire.text(entry,"unit");require(unit in listOf("lb","kg"))
        val until=wire.integer(entry,if(entry.has("updatedAt"))"updatedAt" else "date")
        val array=facts.getJSONArray("exercises");require(array.length() in 1..48)
        val exercises=(0 until array.length()).map { i->
            val row=array.getJSONObject(i);wire.exact(row,"exerciseIndex","exerciseId","name","targets")
            val index=wire.integer(row,"exerciseIndex");val identifier=wire.text(row,"exerciseId");val name=wire.text(row,"name")
            require(index in 0..47 && id.matches(identifier) && identifier.length<=80 && name.length in 1..200)
            val targets=row.getJSONArray("targets");require(targets.length() in 0..20)
            Exercise(index.toInt(),identifier,name,(0 until targets.length()).map { j->
                val target=targets.getJSONObject(j)
                require(target.get("warm")==true && !target.optBoolean("sub",false))
                val load=wire.text(target,"weight");val reps=wire.text(target,"reps")
                require(load.length<=16 && reps.length<=16)
                load to reps
            })
        }
        require(exercises.map {it.index}.distinct().size==exercises.size)
        val events=facts.getJSONArray("events");require(events.length() in 0..NativeWarmupPolicy.MAX_EVENTS)
        val latest=linkedMapOf<JournalWarmupSlot,JournalWarmupIntent>();val operations=mutableSetOf<String>()
        entry.optJSONObject("nativeSessionNote")?.let {operations.add(wire.text(it,"operationId"))}
        for(i in 0 until events.length()) {
            val event=NativeWarmupCodec.read(events.getJSONObject(i),unit,1)
            require(operations.add(event.operationId) && event.recordedAt<=until)
            val ex=exercises.single {it.index==event.slot.exerciseIndex && it.id==event.slot.exerciseId}
            val prior=latest[event.slot]
            require(event.replacesOperationId==prior?.operationId && (prior==null || event.recordedAt>prior.recordedAt))
            if(prior==null && event.slot.warmupIndex>=ex.targets.size)require(event.slot.warmupIndex==ex.targets.size+
                latest.keys.count {it.exerciseIndex==ex.index && it.warmupIndex>=ex.targets.size})
            latest[event.slot]=event
        }
        return exercises.sortedBy {it.index}.flatMap {ex->
            val count=maxOf(ex.targets.size,latest.keys.filter {it.exerciseIndex==ex.index}.maxOfOrNull {it.warmupIndex+1} ?: 0)
            (0 until count).map {index->
                val slot=JournalWarmupSlot(ex.index,index,ex.id);val event=latest[slot];val target=ex.targets.getOrNull(index)
                NativeHistoryWarmup(slot,ex.name,target?.first,target?.second,event?.disposition,event?.values)
            }
        }
    }
    private fun json(row: NativeHistoryWarmup)=JSONObject().put("slot",NativeWarmupCodec.slotJson(row.slot))
        .put("name",row.name).put("targetLoad",row.targetLoad ?: JSONObject.NULL).put("targetReps",row.targetReps ?: JSONObject.NULL)
        .put("disposition",row.disposition?.name ?: JSONObject.NULL).put("values",NativeWarmupCodec.valuesJson(row.values))
    internal fun readResult(rows: JSONArray,snapshot: String,id: String): List<NativeHistoryWarmup> {
        val expected=project(entry(snapshot,id))
        require(same(rows,JSONArray().apply {expected.forEach {put(json(it))}}))
        return expected
    }
    fun correctionJson(history: NativeWorkoutHistory,operationId: String,correctedAt: Long,changes: List<NativeWarmupChange>): String {
        require(id.matches(operationId) && correctedAt in 1..8_640_000_000_000_000L && changes.size in 1..48 &&
            changes.map {it.slot}.distinct().size==changes.size)
        val rows=JSONArray()
        for(change in changes) {
            require(history.warmups.any {it.slot==change.slot})
            require(if(change.disposition==JournalSetDisposition.SKIPPED)change.values==null
                else NativeWarmupPolicy.validValues(change.values,history.unit))
            rows.put(JSONObject().put("slot",NativeWarmupCodec.slotJson(change.slot)).put("disposition",change.disposition.name)
                .put("values",NativeWarmupCodec.valuesJson(change.values)))
        }
        return JSONObject().put("format",3).put("operationId",operationId).put("snapshotSha256",history.snapshotSha256)
            .put("historyId",history.historyId).put("entrySha256",history.entrySha256).put("correctedAt",correctedAt)
            .put("changes",rows).toString().also {wire.bounded(it,16384)}
    }
    internal fun verifyPreparation(snapshot: String,candidate: String,request: JSONObject,already: Boolean) {
        require(wire.integer(request,"format")==3L)
        val before=JSONObject(snapshot);val after=JSONObject(candidate);val historyId=wire.text(request,"historyId")
        val original=entry(snapshot,historyId);val edited=entry(candidate,historyId)
        val changed=setOf("history","savedAt")
        require(before.keys().asSequence().toSet()==after.keys().asSequence().toSet())
        before.keys().asSequence().filter {it !in changed}.forEach {require(same(before.get(it),after.get(it)))}
        val a=before.getJSONArray("history");val b=after.getJSONArray("history");require(a.length()==b.length())
        for(i in 0 until a.length())if(a.getJSONObject(i).get("id")!=historyId)require(same(a.get(i),b.get(i)))
        val editable=setOf("nativeWarmups","updatedAt","nativeCorrection")
        require(original.keys().asSequence().toSet()-editable==edited.keys().asSequence().toSet()-editable)
        original.keys().asSequence().filter {it !in editable}.forEach {require(same(original.get(it),edited.get(it)))}
        require(wire.integer(edited,"updatedAt")>=wire.integer(request,"correctedAt") &&
            after.get("savedAt")==edited.get("updatedAt"))
        if(!already)require(request.get("snapshotSha256")==wire.sha(snapshot) &&
            wire.integer(edited,"updatedAt")>wire.integer(original,if(original.has("updatedAt"))"updatedAt" else "date"))
        val oldFacts=original.getJSONObject("nativeWarmups");val facts=edited.getJSONObject("nativeWarmups")
        require(same(oldFacts.getJSONArray("exercises"),facts.getJSONArray("exercises")))
        val changes=request.getJSONArray("changes");val events=facts.getJSONArray("events")
        val prior=if(already)JSONArray().apply {for(i in 0 until events.length()-changes.length())put(events.get(i))}
            else oldFacts.getJSONArray("events")
        require(events.length()==prior.length()+changes.length())
        for(i in 0 until prior.length())require(same(events.get(i),prior.get(i)))
        val latest=(0 until prior.length()).map {NativeWarmupCodec.read(prior.getJSONObject(it),edited.getString("unit"),1)}.associateBy {it.slot}
        val eligible=project(if(already)JSONObject(edited.toString()).put("nativeWarmups",JSONObject(facts.toString()).put("events",prior)) else original)
        val expected=JSONArray().apply {for(i in 0 until prior.length())put(prior.get(i))}
        for(i in 0 until changes.length()) {
            val change=changes.getJSONObject(i);wire.exact(change,"slot","disposition","values")
            val slot=NativeWarmupCodec.readSlot(change.getJSONObject("slot"));require(eligible.any {it.slot==slot})
            val operation="warmup-edit-"+wire.sha(wire.text(request,"operationId")+":"+i).take(40)
            expected.put(NativeWarmupCodec.json(JournalWarmupIntent(operation,slot,
                JournalSetDisposition.valueOf(wire.text(change,"disposition")),NativeWarmupCodec.readValues(change.get("values"),edited.getString("unit")),
                wire.integer(edited,"updatedAt"),latest[slot]?.operationId)))
        }
        require(same(expected,events));project(edited)
    }
    internal fun same(a: Any,b: Any,depth: Int=0): Boolean {
        require(depth<=64)
        return when {
            a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet() &&
                a.keys().asSequence().all {same(a.get(it),b.get(it),depth+1)}
            a is JSONArray && b is JSONArray -> a.length()==b.length() && (0 until a.length()).all {same(a.get(it),b.get(it),depth+1)}
            a is Number && b is Number -> a.toString().toBigDecimal().compareTo(b.toString().toBigDecimal())==0
            else -> a==b
        }
    }
}
