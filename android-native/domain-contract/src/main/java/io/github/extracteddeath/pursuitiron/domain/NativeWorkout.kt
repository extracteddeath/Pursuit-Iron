package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import java.util.Collections

/** Issued targets and UI projections; no training calculations live in Kotlin. */
data class NativeWorkoutSet(
    val slot: JournalSetSlot,val load: String,val reps: String,
    val targetReps: String,val targetRir: String?,val last: String?,
    val isExtension: Boolean=false,val kind: String?=null,val extensionRestSeconds: Int?=null
)
data class NativeWorkoutExercise(
    val index: Int,val id: String,val name: String,val restSeconds: Int,
    val superset: Boolean,val warmups: List<String>,val sets: List<NativeWorkoutSet>
)
class NativeWorkoutPlan internal constructor(
    val issuedJson: String,val session: JournalSessionKey,val programName: String,
    val dayLabel: String,val exercises: List<NativeWorkoutExercise>,
    val shortenedMinutes: Int?=null,val shortenedEstimatedMinutes: Int?=null
) {
    val slots: List<JournalSetSlot> = Collections.unmodifiableList(exercises.flatMap { row->row.sets.map { it.slot } })
}
data class NativeWorkoutProgress(val plan: NativeWorkoutPlan,val journal: WorkoutJournalState)

interface NativeWorkoutEngine {
    suspend fun inspectWorkoutMuscles(snapshot: String,issuedWorkout: String): NativeWorkoutMuscleReview?
    suspend fun previewShorterSession(snapshot: String,programId: String,minutes: Int): NativeShortSessionPreview?
    suspend fun prepareWorkout(snapshot: String,startJson: String): NativeWorkoutPlan?
    suspend fun prepareIssuedCompletion(origin: String,current: String,journal: String,issued: String): String?
}

/** Strict bounded transport + retained issue decoding; never regenerates targets. */
object NativeWorkoutCodec {
    const val MAX_ISSUED_BYTES=512*1024
    const val SOURCE="modules/engine-api.js:nextSessionCursor+calibratedAnchorPerf+sessionSuggestion+prescribeSets"
    private val wire=WorkoutStoreRuntimeCodec
    private val identifier=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    fun startJson(programId: String,workoutId: String,startedAt: Long,minutes: Int?=null): String {
        require(identifier.matches(programId) && identifier.matches(workoutId) &&
            programId!=workoutId && startedAt in 1..8_640_000_000_000_000L)
        require(minutes==null || minutes in listOf(20,30,45))
        val start=JSONObject().put("programId",programId).put("workoutId",workoutId).put("startedAt",startedAt)
        if(minutes!=null)start.put("minutes",minutes)
        return start.toString()
    }
    fun encodeWorkout(id: String,snapshot: String,startJson: String)=wire.encode(id,"prepareNativeWorkout",
        JSONObject().put("rawSnapshot",snapshot).put("startJson",startJson))
    fun encodeCompletion(id: String,origin: String,current: String,journal: String,issued: String)=
        wire.encode(id,"prepareIssuedWorkoutHistory",JSONObject().put("originSnapshot",origin)
            .put("currentSnapshot",current).put("finishedJournal",journal).put("issuedWorkout",issued))
    fun decodeWorkout(raw: String,id: String,snapshot: String,startJson: String): NativeWorkoutPlan? {
        val value=wire.result(raw,id) ?: return null
        val plan=readIssued(value.toString())
        val start=JSONObject(startJson)
        require(value.get("inputStartSha256")==wire.sha(startJson) &&
            plan.session.programRevision.snapshotSha256==wire.sha(snapshot) &&
            plan.session.programRevision.programId==wire.text(start,"programId") &&
            plan.session.workoutId==wire.text(start,"workoutId") && plan.session.startedAt==wire.integer(start,"startedAt") &&
            (plan.shortenedMinutes?.toLong()==if(start.has("minutes"))wire.integer(start,"minutes") else null))
        return plan
    }
    /** Only private checksummed pending storage calls this on recovery. */
    internal fun readIssued(raw: String): NativeWorkoutPlan {
        wire.bounded(raw,MAX_ISSUED_BYTES);val v=JSONObject(raw)
        val format=wire.integer(v,"format")
        require(format in 1L..2L)
        wire.exact(v,"format","source","preparedOnly","persisted","inputStartSha256","session","programName",
            "dayLabel",*(if(format==2L)arrayOf("sessionLengthPlan") else emptyArray()),"rows")
        require(v.get("source")==SOURCE && v.get("preparedOnly")==true &&
            v.get("persisted")==false && wire.text(v,"inputStartSha256").matches(Regex("^[0-9a-f]{64}$")))
        val s=v.getJSONObject("session");wire.exact(s,"reference","workoutId","dayId","weekIndex","unit","startedAt")
        val r=s.getJSONObject("reference");wire.exact(r,"programId","programUpdatedAt","storeSavedAt","storeVersion","snapshotSha256")
        require(wire.integer(r,"storeVersion")==13L && wire.integer(s,"weekIndex") in 1..104)
        val session=JournalSessionKey(ReadOnlyProgramReference(wire.text(r,"programId"),wire.integer(r,"programUpdatedAt"),
            wire.integer(r,"storeSavedAt"),13,wire.text(r,"snapshotSha256")),wire.text(s,"workoutId"),wire.text(s,"dayId"),
            wire.integer(s,"weekIndex").toInt(),wire.text(s,"unit"),wire.integer(s,"startedAt"))
        val short=if(format==2L)v.getJSONObject("sessionLengthPlan") else null
        val minutes=short?.let { wire.integer(it,"minutes") }
        val estimate=short?.let { wire.integer(it,"estimatedMinutes") }
        if(short!=null) {
            require(minutes in listOf(20L,30L,45L) && estimate!=null && estimate in 0L..10000L &&
                short.get("fits")==true && short.get("changed") is Boolean &&
                wire.text(short,"programId")==session.programRevision.programId &&
                wire.text(short,"dayId")==session.dayId &&
                wire.integer(short,"weekIndex")==session.weekIndex.toLong())
        }
        val rows=v.getJSONArray("rows");require(rows.length() in 1..48)
        val exercises=(0 until rows.length()).map { i->
            val row=rows.getJSONObject(i)
            wire.exact(row,"id","slot","name","restSeconds","superset","previous","sets")
            val index=wire.integer(row,"slot");val rest=wire.integer(row,"restSeconds")
            val exerciseId=wire.text(row,"id");val name=wire.text(row,"name")
            require(index in 0..47 && rest in 0..3600 && name.length in 1..200 && row.get("superset") is Boolean)
            val previous=row.getJSONArray("previous");require(previous.length() in 0..20)
            val positioned=previous.length()>0 && previous.getJSONObject(0).has("workingSetIndex")
            val previousByPosition=(0 until previous.length()).map { j->
                val value=previous.getJSONObject(j)
                wire.exact(value,"load","reps","unit",*(if(positioned)arrayOf("workingSetIndex") else emptyArray()))
                val rawPosition=if(positioned)wire.integer(value,"workingSetIndex") else j.toLong()
                val load=wire.text(value,"load");val reps=wire.integer(value,"reps");val unit=wire.text(value,"unit")
                require(rawPosition in 0..19 && load.matches(Regex("^(0|[1-9][0-9]{0,4})(\\.[0-9]{1,3})?$")) &&
                    reps in 0..100 && unit in listOf("lb","kg"))
                rawPosition.toInt() to "$load $unit × $reps"
            }.toMap()
            require(previousByPosition.size==previous.length())
            val sets=row.getJSONArray("sets");require(sets.length() in 1..40)
            val warmups=ArrayList<String>();val work=ArrayList<NativeWorkoutSet>()
            var mainOrdinal=0
            var reachedExtension=false
            for(j in 0 until sets.length()) {
                val set=sets.getJSONObject(j)
                require(set.get("warm") is Boolean && set.get("done")==false && set.get("auto")==true)
                val extension=set.optBoolean("sub",false)
                require(!set.has("sub") || set.get("sub") is Boolean)
                val load=wire.text(set,"weight");val reps=wire.text(set,"reps")
                require(load.length<=16 && reps.length<=16)
                if(set.getBoolean("warm")) {
                    require(!extension && !reachedExtension)
                    warmups+="$load ${session.unit} × $reps"
                } else {
                    val target=set.getJSONObject("target")
                    val kind=if(extension)wire.text(set,"kind") else null
                    val rest=if(extension)wire.integer(target,"rest").toInt() else null
                    if(extension) {
                        reachedExtension=true
                        require(kind in setOf("myo","drop") && rest in 1..60 &&
                            !target.has("decisionTrace") && set.get("prescribed")==true)
                    } else {
                        require(!reachedExtension)
                        val trace=target.getJSONObject("decisionTrace")
                        require(wire.integer(trace,"schemaVersion")==1L && trace.get("programId")==session.programRevision.programId &&
                            trace.get("dayId")==session.dayId && trace.get("exerciseId")==exerciseId &&
                            wire.integer(trace,"slot")==index && wire.integer(trace,"weekIndex")==session.weekIndex.toLong() &&
                            wire.integer(trace,"workingSetIndex")==mainOrdinal.toLong())
                    }
                    val targetReps=wire.text(target,"reps");val rir=if(target.isNull("rir"))null else wire.text(target,"rir")
                    require(targetReps.length in 1..40 && (rir==null || rir.length<=40))
                    val last=if(!extension)previousByPosition[mainOrdinal] else null
                    if(!extension)mainOrdinal++
                    work+=NativeWorkoutSet(JournalSetSlot(index.toInt(),work.size,exerciseId),load,reps,targetReps,rir,last,
                        extension,kind,rest)
                }
            }
            require(mainOrdinal in 1..20 && work.size in 1..20)
            NativeWorkoutExercise(index.toInt(),exerciseId,name,rest.toInt(),row.getBoolean("superset"),
                Collections.unmodifiableList(warmups),Collections.unmodifiableList(work))
        }
        val name=wire.text(v,"programName");val label=wire.text(v,"dayLabel")
        require(name.length in 1..200 && label.length in 1..200 && exercises.map { it.index }.distinct().size==exercises.size)
        val plan=NativeWorkoutPlan(raw,session,name,label,Collections.unmodifiableList(exercises),
            minutes?.toInt(),estimate?.toInt())
        requireNotNull(WorkoutJournalPolicy.open(session,plan.slots,0))
        return plan
    }
}
