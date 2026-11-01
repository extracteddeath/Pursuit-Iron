package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

data class NativeProgramProfile(
    val name: String="My program", val goal: String="both", val experience: String="intermediate",
    val unit: String="lb",val split: String="full_body",val days: Int=3,val session: String="s60",
    val equipment: List<String> = listOf("barbell","rack","bench","dumbbell","cable","machine",
        "smith","pullup","dip","legpress","hacksquat","legext","legcurl","calfmachine"),
    val allowBodyweight: Boolean=true,val allowSupersets: Boolean=true,val barbellCap: Int?=3
)
data class NativeProgramSplit(val id: String,val label: String,val days: List<Int>)
data class NativeProgramOptions(val splits: List<NativeProgramSplit>,val equipment: List<String>)
class NativeProgramProposal internal constructor(
    val creationId: String,val setupJson: String,val snapshot: String,val program: ReadOnlyProgramSummary,
    val profile: NativeProgramProfile
)
class NativeSavedProgram internal constructor(
    val creationId: String,val profile: NativeProgramProfile,val program: ReadOnlyProgramSummary
)
interface NativeProgramCreationEngine {
    suspend fun programOptions(): NativeProgramOptions?
    suspend fun prepareProgram(setupJson: String): NativeProgramProposal?
    suspend fun inspectProgram(exactSnapshot: String): NativeSavedProgram?
}

/** Onboarding schemas and transport only; split validity and generation stay in JS. */
object NativeProgramSetupCodec {
    private val wire=WorkoutStoreRuntimeCodec
    fun profileJson(p: NativeProgramProfile)=JSONObject()
        .put("name",p.name).put("goal",p.goal).put("experience",p.experience).put("unit",p.unit)
        .put("split",p.split).put("days",p.days).put("session",p.session)
        .put("equipment",JSONArray(p.equipment.sorted())).put("allowBodyweight",p.allowBodyweight)
        .put("allowSupersets",p.allowSupersets).put("barbellCap",p.barbellCap ?: JSONObject.NULL).toString()
    fun readProfile(raw: String): NativeProgramProfile {
        wire.bounded(raw,16384);val p=JSONObject(raw)
        wire.exact(p,"name","goal","experience","unit","split","days","session","equipment",
            "allowBodyweight","allowSupersets","barbellCap")
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
        return NativeProgramProfile(name,goal,experience,unit,split,days.toInt(),session,equipment,
            p.getBoolean("allowBodyweight"),p.getBoolean("allowSupersets"),cap)
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
        wire.exact(value,"source","readOnly","splits","equipment")
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
        return NativeProgramOptions(splits,equipment)
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
        wire.exact(value,"source","readOnly","creationId","setupJson","snapshotSha256","program")
        require(value.get("source")=="modules/engine-api.js:parseStoredData" && value.get("readOnly")==true &&
            value.get("snapshotSha256")==wire.sha(snapshot))
        val s=JSONObject(wire.text(value,"setupJson"));require(value.get("creationId")==s.get("creationId"))
        val p=readProfile(s.getJSONObject("profile").toString())
        return NativeSavedProgram(s.getString("creationId"),p,summary(value.getJSONObject("program"),snapshot,s.getString("programId")))
    }
}
