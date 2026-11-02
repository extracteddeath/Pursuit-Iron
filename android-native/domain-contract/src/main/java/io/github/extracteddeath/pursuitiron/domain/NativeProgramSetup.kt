package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

data class NativeProgramProfile(
    val name: String="My program", val goal: String="both", val experience: String="intermediate",
    val unit: String="lb",val split: String="full_body",val days: Int=3,val session: String="s60",
    val equipment: List<String> = listOf("barbell","rack","bench","dumbbell","cable","machine",
        "smith","pullup","dip","legpress","hacksquat","legext","legcurl","calfmachine"),
    val allowBodyweight: Boolean=true,val allowSupersets: Boolean=true,val barbellCap: Int?=3,
    val focus: Map<String,Int> = emptyMap(),val reduced: List<String> = emptyList(),
    val loadingInventory: String? = null
)
data class NativeProgramSplit(val id: String,val label: String,val days: List<Int>)
data class NativeProgramMuscle(val id: String,val label: String)
data class NativeProgramOptions(
    val splits: List<NativeProgramSplit>,
    val equipment: List<String>,
    val muscles: List<NativeProgramMuscle> = emptyList()
)
class NativeProgramProposal internal constructor(
    val creationId: String,val setupJson: String,val snapshot: String,val program: ReadOnlyProgramSummary,
    val profile: NativeProgramProfile
)
data class NativeStackCandidate(val id: String,val name: String,val mode: String)
/** Original canonical source slot, not a movement ID or reordered display index. */
data class NativeProtectedProgramSlot(
    val dayId: String,val sourceIndex: Int,val reason: String
)
class NativeSavedProgram internal constructor(
    val creationId: String,val profile: NativeProgramProfile,val program: ReadOnlyProgramSummary,
    val loadingInventory: String? = null,
    val loadingCandidates: List<NativeStackCandidate> = emptyList(),
    val protectedSlots: List<NativeProtectedProgramSlot> = emptyList()
)
interface NativeProgramCreationEngine {
    suspend fun programOptions(): NativeProgramOptions?
    suspend fun prepareProgram(setupJson: String): NativeProgramProposal?
    suspend fun inspectProgram(exactSnapshot: String): NativeSavedProgram?
}

/** Onboarding schemas and transport only; split validity and generation stay in JS. */
object NativeProgramSetupCodec {
    private val wire=WorkoutStoreRuntimeCodec
    private val baseFields=arrayOf("name","goal","experience","unit","split",
        "days","session","equipment","allowBodyweight","allowSupersets","barbellCap")
    private val allowedMuscles=setOf("chest","back","shoulders","biceps",
        "triceps","quads","hamstrings","glutes","calves","abs","traps","forearms",
        "adductors","abductors","lower_back","neck")
    fun profileJson(p: NativeProgramProfile)=JSONObject()
        .put("name",p.name).put("goal",p.goal).put("experience",p.experience).put("unit",p.unit)
        .put("split",p.split).put("days",p.days).put("session",p.session)
        .put("equipment",JSONArray(p.equipment.sorted())).put("allowBodyweight",p.allowBodyweight)
        .put("allowSupersets",p.allowSupersets).put("barbellCap",p.barbellCap ?: JSONObject.NULL)
        .put("focus",JSONObject().apply { p.focus.toSortedMap().forEach { (key,level) -> put(key,level) } })
        .put("reduced",JSONArray(p.reduced.sorted())).apply {
            p.loadingInventory?.let { put("loadingInventory",JSONObject(NativeGymInventoryCodec.validate(it,p.unit))) }
        }.toString()
    fun readProfile(raw: String): NativeProgramProfile {
        wire.bounded(raw,16384);val p=JSONObject(raw)
        // Old v1 saved native programs omitted priorities; preserve exact restore.
        val hasPriorities=p.has("focus") || p.has("reduced")
        val hasInventory=p.has("loadingInventory")
        if(hasInventory)wire.exact(p,*baseFields,"focus","reduced","loadingInventory")
        else if(hasPriorities)wire.exact(p,*baseFields,"focus","reduced")
        else wire.exact(p,*baseFields)
        val name=wire.text(p,"name");val goal=wire.text(p,"goal");val experience=wire.text(p,"experience")
        val unit=wire.text(p,"unit");val split=wire.text(p,"split");val days=wire.integer(p,"days")
        val session=wire.text(p,"session")
        require(name.isNotBlank() && name.length<=60 && !name.any { it.code<32 || it.code in 127..159 } &&
            goal in listOf("both","strength","hypertrophy") && experience in listOf("beginner","intermediate","advanced") &&
            unit in listOf("lb","kg") && split in listOf("full_body","upper_lower","ppl","ulppl") &&
            days in 3..6 && session in listOf("s60","s90","s120p"))
        val list=p.getJSONArray("equipment");require(list.length() in 1..17)
        val equipment=(0 until list.length()).map { val v=list.get(it);require(v is String && v.matches(Regex("^[a-z][a-z0-9_-]{0,39}$")));v }
        require(equipment.toSet().size==equipment.size && p.get("allowBodyweight") is Boolean && p.get("allowSupersets") is Boolean)
        val cap=if(p.isNull("barbellCap")) null else wire.integer(p,"barbellCap").also { require(it in 0..6) }.toInt()
        val focus=if(hasPriorities) {
            val obj=p.getJSONObject("focus")
            require(obj.length()<=3)
            obj.keys().asSequence().associateWith { key ->
                require(key in allowedMuscles)
                val level=wire.integer(obj,key)
                require(level in 1..2)
                level.toInt()
            }
        } else emptyMap()
        val reduced=if(hasPriorities) {
            val rows=p.getJSONArray("reduced")
            require(rows.length()<=3)
            (0 until rows.length()).map { i ->
                val key=rows.get(i)
                require(key is String && key in allowedMuscles && key !in focus)
                key
            }.also { require(it.size==it.distinct().size) }
        } else emptyList()
        return NativeProgramProfile(name,goal,experience,unit,split,days.toInt(),session,equipment,
            p.getBoolean("allowBodyweight"),p.getBoolean("allowSupersets"),cap,focus,reduced,
            if(hasInventory)NativeGymInventoryCodec.validate(p.getJSONObject("loadingInventory").toString(),unit) else null)
    }
    fun setupJson(profile: NativeProgramProfile,creationId: String,programId: String,createdAt: Long,seed: Int): String {
        val p=readProfile(profileJson(profile))
        require(creationId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")) &&
            programId.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")) && creationId!=programId &&
            createdAt in 1..8_640_000_000_000_000L && seed in 1 until Int.MAX_VALUE)
        return JSONObject().put("creationId",creationId).put("programId",programId).put("createdAt",createdAt)
            .put("seed",seed).put("profile",JSONObject(profileJson(p))).toString()
    }
    fun encodeOptions(id: String)=wire.encode(id,"nativeProgramOptions",JSONObject())
    fun encodeProgram(id: String,setupJson: String)=wire.encode(id,"prepareNativeProgram",JSONObject().put("setupJson",setupJson))
    fun encodeInspect(id: String,snapshot: String)=wire.encode(id,"inspectNativeProgram",JSONObject().put("rawSnapshot",snapshot))

    fun decodeOptions(raw: String,id: String): NativeProgramOptions? {
        val value=wire.result(raw,id) ?: return null
        wire.exact(value,"source","readOnly","splits","equipment","muscles")
        require(value.get("source")=="modules/engine-api.js:splitSupportsTrainingDays" && value.get("readOnly")==true)
        val rows=value.getJSONArray("splits");require(rows.length()==4)
        val splits=(0 until rows.length()).map { i ->
            val r=rows.getJSONObject(i);wire.exact(r,"id","label","days")
            val days=r.getJSONArray("days");require(days.length() in 1..4)
            val choices=(0 until days.length()).map { val n=days.get(it);val count=n.toString().toIntOrNull();require(n is Number && count!=null && count in 3..6);count }
            require(choices.toSet().size==choices.size)
            NativeProgramSplit(wire.text(r,"id"),wire.text(r,"label"),choices)
        }
        require(splits.map { it.id }.toSet()==setOf("full_body","upper_lower","ppl","ulppl"))
        val eq=value.getJSONArray("equipment");require(eq.length() in 1..24)
        val equipment=(0 until eq.length()).map { val v=eq.get(it);require(v is String && v.length in 1..40);v }
        require(equipment.toSet().size==equipment.size)
        val muscleRows=value.getJSONArray("muscles")
        require(muscleRows.length()==allowedMuscles.size)
        val muscles=(0 until muscleRows.length()).map { i ->
            val item=muscleRows.getJSONObject(i)
            wire.exact(item,"id","label")
            val key=wire.text(item,"id")
            val label=wire.text(item,"label")
            require(key in allowedMuscles && label.isNotBlank() && label.length<=36)
            NativeProgramMuscle(key,label)
        }
        require(muscles.map { it.id }.toSet()==allowedMuscles)
        return NativeProgramOptions(splits,equipment,muscles)
    }
    private fun summary(raw: JSONObject,snapshot: String,programId: String): ReadOnlyProgramSummary {
        val r=raw.getJSONObject("reference")
        val ref=ReadOnlyProgramReference(r.getString("programId"),r.getLong("programUpdatedAt"),r.getLong("storeSavedAt"),
            r.getInt("storeVersion"),r.getString("snapshotSha256"))
        require(ref.programId==programId && ref.snapshotSha256==wire.sha(snapshot))
        return ReadOnlyProgramTransportCodec.decode(raw.toString(),ref)
    }
    fun decodeProposal(raw: String,id: String,setupJson: String): NativeProgramProposal? {
        val value=wire.result(raw,id) ?: return null
        wire.exact(value,"source","preparedOnly","approvedForWorkoutExecution","creationId","setupSha256","snapshot","snapshotSha256","program")
        val s=JSONObject(setupJson);val p=readProfile(s.getJSONObject("profile").toString())
        val snapshot=wire.text(value,"snapshot");wire.bounded(snapshot,wire.MAX_SNAPSHOT_BYTES)
        require(value.get("source")=="modules/engine-api.js:generateNextProgramForShell" && value.get("preparedOnly")==true &&
            value.get("approvedForWorkoutExecution")==false && value.get("creationId")==s.get("creationId") &&
            value.get("setupSha256")==wire.sha(setupJson) && value.get("snapshotSha256")==wire.sha(snapshot))
        val program=summary(value.getJSONObject("program"),snapshot,s.getString("programId"))
        require(program.name==p.name.trim() && program.days.size==p.days && program.goal==p.goal && program.weeks==6 &&
            program.reference.programUpdatedAt==s.getLong("createdAt") && program.reference.storeSavedAt==s.getLong("createdAt"))
        return NativeProgramProposal(s.getString("creationId"),setupJson,snapshot,program,p)
    }
    fun decodeSaved(raw: String,id: String,snapshot: String): NativeSavedProgram? {
        val value=wire.result(raw,id) ?: return null
        if(value.has("protectedSlots"))wire.exact(value,"source","readOnly","creationId",
            "setupJson","snapshotSha256","program","loadingInventory","loadingCandidates","protectedSlots")
        else if(value.has("loadingCandidates"))wire.exact(value,"source","readOnly","creationId",
            "setupJson","snapshotSha256","program","loadingInventory","loadingCandidates")
        else if(value.has("loadingInventory"))wire.exact(value,"source","readOnly","creationId",
            "setupJson","snapshotSha256","program","loadingInventory")
        else wire.exact(value,"source","readOnly","creationId","setupJson","snapshotSha256","program")
        require(value.get("source")=="modules/engine-api.js:parseStoredData" && value.get("readOnly")==true &&
            value.get("snapshotSha256")==wire.sha(snapshot))
        val s=JSONObject(wire.text(value,"setupJson"));require(value.get("creationId")==s.get("creationId"))
        val p=readProfile(s.getJSONObject("profile").toString())
        val program=summary(value.getJSONObject("program"),snapshot,s.getString("programId"))
        val ownedIds=program.days.flatMap { it.exerciseIds }.toSet()
        val candidates=if(value.has("loadingCandidates")) {
            val rows=value.getJSONArray("loadingCandidates");require(rows.length()<=210)
            val parsed=(0 until rows.length()).map { index ->
                val item=rows.getJSONObject(index)
                wire.exact(item,"id","name","mode")
                val key=wire.text(item,"id")
                val name=wire.text(item,"name")
                val mode=wire.text(item,"mode")
                require(key in ownedIds && key.matches(Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,79}$")) &&
                    name.isNotBlank() && name.length<=120 &&
                    !name.any { it.code<32 || it.code in 127..159 } &&
                    mode in listOf("cable_stack","machine_stack","external_load"))
                NativeStackCandidate(key,name,mode)
            }
            require(parsed.map { it.id }.distinct().size==parsed.size)
            parsed
        } else emptyList()
        val inventory=if(!value.has("loadingInventory") || value.isNull("loadingInventory"))null
            else NativeGymInventoryCodec.validate(value.getJSONObject("loadingInventory").toString(),
                p.unit,candidates.map { it.id }.toSet(),
                candidates.filter { it.mode=="external_load" }.map { it.id }.toSet())
        val protected=if(value.has("protectedSlots")) {
            val rows=value.getJSONArray("protectedSlots")
            require(rows.length()<=210)
            val original=program.days.associate { it.id to it.exerciseIds.size }
            val seen=mutableSetOf<Pair<String,Int>>()
            (0 until rows.length()).map { index ->
                val slot=rows.getJSONObject(index)
                wire.exact(slot,"dayId","sourceIndex","reason")
                val dayId=wire.text(slot,"dayId")
                val position=wire.integer(slot,"sourceIndex")
                val reason=wire.text(slot,"reason")
                require(position>=0 && position<(original[dayId] ?: 0).toLong() &&
                    reason in setOf("primary","paired"))
                val from=position.toInt()
                require(seen.add(dayId to from))
                NativeProtectedProgramSlot(dayId,from,reason)
            }
        } else emptyList()
        return NativeSavedProgram(s.getString("creationId"),p,program,inventory,candidates,protected)
    }
}
