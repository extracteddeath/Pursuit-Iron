package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

data class NativeHistoryObservation(val slot: JournalSetSlot,val name: String,val kind: String,
    val retained: Boolean,val disposition: JournalSetDisposition,val values: JournalSetValues?)
data class NativeHistoryObservationChange(val slot: JournalSetSlot,val disposition: JournalSetDisposition,
    val values: JournalSetValues?)

/** Observation identity and transport checks. Canonical JS owns all summaries. */
object NativeHistoryObservationCodec {
    private val wire=WorkoutStoreRuntimeCodec
    private val identifier=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    private val load=Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")
    private fun slot(v: JSONObject): JournalSetSlot {
        wire.exact(v,"exerciseIndex","setIndex","exerciseId")
        val exercise=wire.integer(v,"exerciseIndex");val set=wire.integer(v,"setIndex");val id=wire.text(v,"exerciseId")
        require(exercise in 0..47 && set in 0..19 && id.length<=80 && identifier.matches(id))
        return JournalSetSlot(exercise.toInt(),set.toInt(),id)
    }
    private fun json(slot: JournalSetSlot)=JSONObject().put("exerciseIndex",slot.exerciseIndex)
        .put("setIndex",slot.setIndex).put("exerciseId",slot.exerciseId)
    private fun values(v: JSONObject,unit: String,minReps: Int=1): JournalSetValues {
        wire.exact(v,"load","unit","reps","rir")
        val weight=wire.text(v,"load");val reps=wire.integer(v,"reps")
        val rir=if(v.isNull("rir"))null else wire.integer(v,"rir").also { require(it in 0..10) }.toInt()
        require(load.matches(weight) && wire.text(v,"unit")==unit && reps in minReps..100)
        return JournalSetValues(weight,unit,reps.toInt(),rir)
    }
    private fun json(value: JournalSetValues?)=value?.let { JSONObject().put("load",it.load)
        .put("unit",it.unit).put("reps",it.reps).put("rir",it.rir ?: JSONObject.NULL) } ?: JSONObject.NULL

    internal fun readRows(list: JSONArray,unit: String,exercises: List<NativeHistoryExercise>,total: Int,
        done: Int): List<NativeHistoryObservation> {
        require(list.length()==total)
        val result=(0 until list.length()).map { i->
            val row=list.getJSONObject(i);wire.exact(row,"slot","name","kind","retained","disposition","values")
            val name=wire.text(row,"name");val kind=wire.text(row,"kind");val location=slot(row.getJSONObject("slot"))
            require(name.length in 1..200 && kind in listOf("working","myo","drop") && row.get("retained") is Boolean)
            val disposition=JournalSetDisposition.valueOf(wire.text(row,"disposition"))
            val value=if(row.isNull("values"))null else values(row.getJSONObject("values"),unit)
            require((disposition==JournalSetDisposition.SKIPPED)==(value==null))
            val retained=row.getBoolean("retained")
            require(!retained || disposition==JournalSetDisposition.COMPLETED)
            require(disposition!=JournalSetDisposition.COMPLETED || retained || value?.load?.toBigDecimal()?.signum()==0)
            NativeHistoryObservation(location,name,kind,retained,disposition,value)
        }
        require(result.count { it.disposition==JournalSetDisposition.COMPLETED }==done &&
            result.map { it.slot }.toSet().size==result.size && result.zipWithNext().all { (a,b)->
                a.slot.exerciseIndex*20+a.slot.setIndex<b.slot.exerciseIndex*20+b.slot.setIndex })
        result.groupBy { it.slot.exerciseIndex }.values.forEach { group->
            require(group.map { it.slot.setIndex }==group.indices.toList() && group.map { it.slot.exerciseId }.distinct().size==1 &&
                group.map { it.name }.distinct().size==1 && group.first().kind=="working")
            var extension=false
            group.forEach { row->if(row.kind!="working")extension=true else require(!extension) }
        }
        val retained=result.filter { it.retained }.associateBy { it.slot }
        require(retained.size==exercises.sumOf { it.sets.size })
        exercises.forEach { exercise->exercise.sets.forEach { set->
            val row=checkNotNull(retained[JournalSetSlot(set.exerciseIndex,set.ordinal-1,exercise.id)])
            require(row.kind==set.kind && row.name==exercise.name && row.values==JournalSetValues(set.load,unit,set.reps,set.rir))
        } }
        return result
    }

    fun correctionJson(history: NativeWorkoutHistory,operationId: String,correctedAt: Long,
        changes: List<NativeHistoryObservationChange>): String {
        val rows=requireNotNull(history.editRows)
        require(identifier.matches(operationId) && correctedAt in 1..8_640_000_000_000_000L &&
            changes.size in 1..48 && changes.map { it.slot }.toSet().size==changes.size)
        val list=JSONArray()
        changes.forEach { change->
            require(rows.any { it.slot==change.slot })
            val value=json(change.values)
            if(change.disposition==JournalSetDisposition.SKIPPED)require(change.values==null)
            else values(value as JSONObject,history.unit)
            list.put(JSONObject().put("slot",json(change.slot)).put("disposition",change.disposition.name).put("values",value))
        }
        return JSONObject().put("format",2).put("operationId",operationId).put("snapshotSha256",history.snapshotSha256)
            .put("historyId",history.historyId).put("entrySha256",history.entrySha256).put("correctedAt",correctedAt)
            .put("changes",list).toString().also { wire.bounded(it,16384) }
    }

    private fun same(a: Any,b: Any,depth: Int=0): Boolean {
        require(depth<=64)
        return when {
            a==JSONObject.NULL || b==JSONObject.NULL -> a==JSONObject.NULL && b==JSONObject.NULL
            a is JSONObject && b is JSONObject -> a.keys().asSequence().toSet()==b.keys().asSequence().toSet() &&
                a.keys().asSequence().all { same(a.get(it),b.get(it),depth+1) }
            a is JSONArray && b is JSONArray -> a.length()==b.length() && (0 until a.length()).all { same(a.get(it),b.get(it),depth+1) }
            a is Number && b is Number -> a.toString().toBigDecimal().compareTo(b.toString().toBigDecimal())==0
            else -> a==b
        }
    }
    private fun entry(snapshot: String,id: String): JSONObject {
        val rows=JSONObject(snapshot).getJSONArray("history")
        return (0 until rows.length()).map { rows.getJSONObject(it) }.filter { it.getString("id")==id }.single()
    }
    private fun template(set: JSONObject)=JSONObject(set.toString()).apply {
        for(key in listOf("w","r","rir","rirReported","failedAttempt"))remove(key)
        optJSONObject("decisionTrace")?.remove("applied")
    }
    private fun templates(value: JSONObject,allowAttempts: Boolean=false): Map<JournalSetSlot,JSONObject> {
        val result=mutableMapOf<JournalSetSlot,JSONObject>()
        val rawObservations=value.getJSONObject("nativeSetObservations").getJSONArray("events")
        val observations=(0 until rawObservations.length()).map { rawObservations.getJSONObject(it) }.associateBy {
            wire.exact(it,"slot","disposition","values");slot(it.getJSONObject("slot")) }
        require(observations.size==rawObservations.length())
        val basis=value.getJSONObject("nativeHistoryBasis");wire.exact(basis,"format","rows")
        require(wire.integer(basis,"format")==1L)
        val rows=basis.getJSONArray("rows")
        require(rows.length() in 1..48)
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i);val omitted=row.getJSONArray("omitted")
            wire.exact(row,"exerciseIndex","exerciseId","setCount","performance","omitted")
            val exercise=wire.integer(row,"exerciseIndex");val count=wire.integer(row,"setCount")
            require(exercise in 0..47 && count in 1..20 && omitted.length()<=count)
            val metadata=row.getJSONObject("performance")
            require(listOf("weight","reps","date","sets").none { metadata.has(it) })
            for(j in 0 until omitted.length()) {
                val missing=omitted.getJSONObject(j);wire.exact(missing,"setIndex","template")
                val position=wire.integer(missing,"setIndex");require(position>=0 && position<count)
                val location=JournalSetSlot(exercise.toInt(),position.toInt(),
                    wire.text(row,"exerciseId"))
                val evidence=missing.getJSONObject("template")
                require(same(evidence,template(evidence)))
                val observation=checkNotNull(observations[location])
                require(observation.getString("disposition")=="SKIPPED" ||
                    values(observation.getJSONObject("values"),value.getString("unit")).load?.toBigDecimal()?.signum()==0)
                require(result.put(location,evidence)==null)
            }
        }
        val map=value.getJSONObject("nativeSetLocations");wire.exact(map,"format","sets")
        require(wire.integer(map,"format")==1L)
        val locations=map.getJSONArray("sets");require(locations.length() in 0..480)
        val used=mutableSetOf<Pair<String,Int>>()
        for(i in 0 until locations.length()) {
            val location=locations.getJSONObject(i);wire.exact(location,"exerciseId","setIndex","slot")
            val key=slot(location.getJSONObject("slot"));val id=wire.text(location,"exerciseId")
            val index=wire.integer(location,"setIndex");require(index in 0..479 && id==key.exerciseId && used.add(id to index.toInt()))
            val sets=value.getJSONObject("perf").getJSONObject(id).getJSONArray("sets")
            val set=sets.getJSONObject(index.toInt());val observation=checkNotNull(observations[key])
            require(observation.getString("disposition")=="COMPLETED")
            val actual=values(observation.getJSONObject("values"),value.getString("unit"),if(allowAttempts)0 else 1)
            val weight=set.get("w");require(weight is Number && weight.toString().toBigDecimal().compareTo(checkNotNull(actual.load).toBigDecimal())==0 &&
                wire.integer(set,"r")==actual.reps.toLong())
            require(!set.has("rirReported") || set.get("rirReported") is Boolean)
            if(set.optBoolean("rirReported",false))require(actual.rir!=null && wire.integer(set,"rir")==actual.rir.toLong())
            else require(actual.rir==null)
            require(result.put(key,template(set))==null)
        }
        val perf=value.getJSONObject("perf")
        require(used.size==perf.keys().asSequence().sumOf { perf.getJSONObject(it).getJSONArray("sets").length() } &&
            result.keys==observations.keys)
        return result
    }

    /** Check a new completion's missing targets against its original issued bytes. */
    internal fun verifyIssuedBasis(entry: JSONObject,plan: NativeWorkoutPlan) {
        val evidence=templates(entry,allowAttempts=true);require(evidence.keys==plan.slots.toSet())
        val rows=entry.getJSONObject("nativeHistoryBasis").getJSONArray("rows")
        val issued=JSONObject(plan.issuedJson).getJSONArray("rows")
        require(rows.length()==plan.exercises.size)
        for(i in 0 until rows.length()) {
            val row=rows.getJSONObject(i);val exercise=plan.exercises[i]
            require(wire.integer(row,"exerciseIndex")==exercise.index.toLong() && row.get("exerciseId")==exercise.id &&
                wire.integer(row,"setCount")==exercise.sets.size.toLong())
            val sourceSets=issued.getJSONObject(i).getJSONArray("sets")
            val work=(0 until sourceSets.length()).map { sourceSets.getJSONObject(it) }.filter { !it.getBoolean("warm") }
            for((index,set) in exercise.sets.withIndex()) {
                val target=work[index];val template=checkNotNull(evidence[set.slot])
                require(!template.has("warm") || template.get("warm") is Boolean)
                require(!template.optBoolean("warm",false) && (!template.has("sub") || template.get("sub") is Boolean))
                require(template.optBoolean("sub",false)==set.isExtension)
                if(set.isExtension)require(template.get("kind")==set.kind && !template.has("decisionTrace"))
                else require(wire.integer(template,"workingSetIndex")==index.toLong() &&
                    same(template.get("decisionTrace"),target.getJSONObject("target").get("decisionTrace")))
                require(template.get("pt")==target.getJSONObject("target").get("reps"))
            }
        }
    }

    /** Verify requested observations, preserved targets/audit and untouched store owners. */
    internal fun verifyPreparation(snapshot: String,candidate: String,request: JSONObject,already: Boolean) {
        require(wire.integer(request,"format")==2L)
        val id=wire.text(request,"historyId");val after=entry(candidate,id)
        val changes=request.getJSONArray("changes");require(changes.length() in 1..48)
        val actual=after.getJSONObject("nativeSetObservations").getJSONArray("events")
        val requested=(0 until changes.length()).map { changes.getJSONObject(it) }.associateBy { slot(it.getJSONObject("slot")) }
        require(requested.size==changes.length())
        requested.forEach { (key,change)->
            val match=(0 until actual.length()).map { actual.getJSONObject(it) }.single { slot(it.getJSONObject("slot"))==key }
            require(same(match.get("disposition"),change.get("disposition")) && same(match.get("values"),change.get("values")))
        }
        if(already)return
        val before=entry(snapshot,id)
        val prior=before.getJSONObject("nativeSetObservations").getJSONArray("events")
        require(prior.length()==actual.length())
        for(i in 0 until prior.length()) {
            val old=prior.getJSONObject(i);val next=actual.getJSONObject(i);val key=slot(old.getJSONObject("slot"))
            wire.exact(next,"slot","disposition","values")
            if(next.get("disposition")=="SKIPPED")require(next.isNull("values"))
            else { require(next.get("disposition")=="COMPLETED");values(next.getJSONObject("values"),after.getString("unit")) }
            require(same(old.get("slot"),next.get("slot")))
            if(key !in requested)require(same(old,next))
        }
        for(key in listOf("id","programId","programName","dayId","dayLabel","weekIndex","date","unit","totalSets",
            "durationMin","nativeCompletion","nativeSetRevisions","sessionLengthPlan","timeLimited","prescriptionEdited")) {
            require(before.has(key)==after.has(key));if(before.has(key))require(same(before.get(key),after.get(key)))
        }
        val oldBasis=before.getJSONObject("nativeHistoryBasis").getJSONArray("rows")
        val newBasis=after.getJSONObject("nativeHistoryBasis").getJSONArray("rows")
        require(oldBasis.length()==newBasis.length())
        for(i in 0 until oldBasis.length())for(key in listOf("exerciseIndex","exerciseId","setCount","performance"))
            require(same(oldBasis.getJSONObject(i).get(key),newBasis.getJSONObject(i).get(key)))
        val oldTemplates=templates(before);val newTemplates=templates(after)
        require(oldTemplates.keys==newTemplates.keys && oldTemplates.all { (key,value)->same(value,checkNotNull(newTemplates[key])) })
        if(before.has("nativeSetAdjustments")) {
            val old=before.getJSONObject("nativeSetAdjustments")
            val expected=JSONArray();val adjustments=old.getJSONArray("events")
            for(i in 0 until adjustments.length()) {
                val adjustment=adjustments.getJSONObject(i);val location=slot(adjustment.getJSONObject("slot"))
                val observation=(0 until actual.length()).map { actual.getJSONObject(it) }
                    .single { slot(it.getJSONObject("slot"))==location }
                if(observation.getString("disposition")=="SKIPPED")expected.put(adjustment)
            }
            require(same(after.get("nativeSetAdjustments"),JSONObject().put("format",1).put("events",expected)))
        } else require(!after.has("nativeSetAdjustments"))
        val audit=after.getJSONObject("nativeHistoryRevisions");wire.exact(audit,"format","events")
        require(wire.integer(audit,"format")==1L)
        val events=audit.getJSONArray("events")
        val oldAudit=before.optJSONObject("nativeHistoryRevisions")?.getJSONArray("events") ?: JSONArray()
        require(events.length()==oldAudit.length()+changes.length() && events.length()<=1024)
        for(i in 0 until oldAudit.length())require(same(events.get(i),oldAudit.get(i)))
        for(i in 0 until changes.length()) {
            val change=changes.getJSONObject(i);val key=slot(change.getJSONObject("slot"))
            val old=(0 until prior.length()).map { prior.getJSONObject(it) }.single { slot(it.getJSONObject("slot"))==key }
            val event=events.getJSONObject(oldAudit.length()+i)
            wire.exact(event,"operationId","slot","correctedAt","from","to")
            require(event.get("operationId")==request.get("operationId") && same(event.get("slot"),change.get("slot")) &&
                wire.integer(event,"correctedAt")==wire.integer(after,"updatedAt") &&
                same(event.get("from"),JSONObject().put("disposition",old.get("disposition")).put("values",old.get("values"))) &&
                same(event.get("to"),JSONObject().put("disposition",change.get("disposition")).put("values",change.get("values"))))
        }
        val original=JSONObject(snapshot);val next=JSONObject(candidate)
        require(original.keys().asSequence().toSet()==next.keys().asSequence().toSet())
        original.keys().asSequence().filter { it !in listOf("savedAt","history","perf") }.forEach { require(same(original.get(it),next.get(it))) }
        val oldHistory=original.getJSONArray("history");val newHistory=next.getJSONArray("history")
        require(oldHistory.length()==newHistory.length())
        for(i in 0 until oldHistory.length())if(oldHistory.getJSONObject(i).getString("id")!=id)
            require(same(oldHistory.get(i),newHistory.get(i)))
    }
}
