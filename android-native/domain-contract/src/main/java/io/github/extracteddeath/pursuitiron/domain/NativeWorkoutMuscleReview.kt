package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject
import java.util.Collections

data class NativeWorkoutMuscleRow(
    val exerciseIndex: Int,val exerciseId: String,val name: String,
    val primaryPart: String,val involvedParts: List<String>
)
data class NativeWorkoutMuscleReview(
    val workoutId: String,val parts: List<String>,val rows: List<NativeWorkoutMuscleRow>
)

/** Only the canonical worker identifies involved muscles. No injury diagnosis. */
object NativeWorkoutMuscleReviewCodec {
    private val wire=WorkoutStoreRuntimeCodec
    private val part=Regex("^[a-z][a-z_]{0,39}$")
    fun matchesOrigin(source: String,plan: NativeWorkoutPlan): Boolean =
        wire.sha(source)==plan.session.programRevision.snapshotSha256

    fun encode(id: String,source: String,issued: String): String =
        wire.encode(id,"inspectNativeWorkoutMuscles",JSONObject()
            .put("rawSnapshot",source).put("issuedWorkout",issued))

    fun decode(raw: String,id: String,source: String,issued: String): NativeWorkoutMuscleReview? {
        val plan=NativeWorkoutCodec.readIssued(issued)
        require(matchesOrigin(source,plan))
        val value=wire.result(raw,id) ?: return null
        wire.exact(value,"source","snapshotSha256","issuedWorkoutSha256",
            "workoutId","parts","rows","readOnly","applied")
        require(wire.text(value,"source")=="modules/engine-api.js:EX_BY_ID+secondaryOf" &&
            wire.text(value,"snapshotSha256")==wire.sha(source) &&
            wire.text(value,"issuedWorkoutSha256")==wire.sha(issued) &&
            wire.text(value,"workoutId")==plan.session.workoutId &&
            value.get("readOnly")==true && value.get("applied")==false)
        val all=value.getJSONArray("parts");require(all.length() in 1..48)
        val parts=(0 until all.length()).map { i ->
            val name=all.get(i);require(name is String && part.matches(name));name
        }
        require(parts==parts.distinct().sorted())
        val rows=value.getJSONArray("rows")
        require(rows.length()==plan.exercises.size)
        val result=(0 until rows.length()).map { i ->
            val row=rows.getJSONObject(i)
            wire.exact(row,"exerciseIndex","exerciseId","name","primaryPart","involvedParts")
            val issuedRow=plan.exercises[i]
            require(wire.integer(row,"exerciseIndex")==issuedRow.index.toLong() &&
                wire.text(row,"exerciseId")==issuedRow.id &&
                wire.text(row,"name")==issuedRow.name)
            val primary=wire.text(row,"primaryPart")
            val involved=row.getJSONArray("involvedParts")
            require(involved.length() in 1..20)
            val credited=(0 until involved.length()).map { j ->
                val partName=involved.get(j)
                require(partName is String && part.matches(partName) && partName in parts)
                partName
            }
            require(primary in credited && credited==credited.distinct().sorted())
            NativeWorkoutMuscleRow(issuedRow.index,issuedRow.id,issuedRow.name,
                primary,Collections.unmodifiableList(credited))
        }
        require(result.flatMap { it.involvedParts }.distinct().sorted()==parts)
        return NativeWorkoutMuscleReview(plan.session.workoutId,
            Collections.unmodifiableList(parts),Collections.unmodifiableList(result))
    }
}
