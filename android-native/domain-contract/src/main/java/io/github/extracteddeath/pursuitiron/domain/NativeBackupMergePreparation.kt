package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject

/**
 * Private, ephemeral candidate. This type is deliberately NOT a storage
 * receipt or import authorization, and must never enter Compose state.
 */
class NativeBackupMergePreparation internal constructor(
    val candidateSnapshot: String,
    val currentSha256: String,
    val incomingSha256: String,
    val candidateSha256: String,
    val candidateBytes: Int
)

/**
 * Phase two of backup validation: check a canonical candidate returned by
 * the trusted JS worker against both full source bytes and a specific
 * previously displayed dry-run preview. NO publication method is exposed.
 */
object NativeBackupMergePreparationCodec {
    private const val LIMIT=1024*1024
    private val hash=Regex("^[0-9a-f]{64}$")
    fun encode(id: String,currentSnapshot: String,incomingSnapshot: String,expectedCandidateSha256: String): String {
        WorkoutStoreRuntimeCodec.bounded(currentSnapshot,LIMIT)
        WorkoutStoreRuntimeCodec.bounded(incomingSnapshot,LIMIT)
        require(hash.matches(expectedCandidateSha256))
        return WorkoutStoreRuntimeCodec.encode(id,"prepareBackupMerge",JSONObject()
            .put("currentSnapshot",currentSnapshot)
            .put("incomingSnapshot",incomingSnapshot)
            .put("expectedCandidateSha256",expectedCandidateSha256))
    }

    fun decode(raw: String,id: String,currentSnapshot: String,incomingSnapshot: String,
        expectedCandidateSha256: String): NativeBackupMergePreparation? {
        WorkoutStoreRuntimeCodec.bounded(currentSnapshot,LIMIT)
        WorkoutStoreRuntimeCodec.bounded(incomingSnapshot,LIMIT)
        require(hash.matches(expectedCandidateSha256))
        val result=WorkoutStoreRuntimeCodec.result(raw,id) ?: return null
        WorkoutStoreRuntimeCodec.exact(result,
            "source","validation","preparedOnly","persisted","approvedForImport",
            "approvedForWorkoutExecution","requiresAtomicUndo","storeVersion",
            "currentSha256","incomingSha256","candidateSnapshotSha256",
            "candidateSnapshotBytes","candidateSnapshot")
        require(result.get("source")=="modules/engine-api.js:mergeStores" &&
            result.get("validation")=="canonical-v13-merge-preparation" &&
            result.get("preparedOnly")==true && result.get("persisted")==false &&
            result.get("approvedForImport")==false &&
            result.get("approvedForWorkoutExecution")==false &&
            result.get("requiresAtomicUndo")==true &&
            WorkoutStoreRuntimeCodec.integer(result,"storeVersion")==13L)
        val currentHash=WorkoutStoreRuntimeCodec.text(result,"currentSha256")
        val incomingHash=WorkoutStoreRuntimeCodec.text(result,"incomingSha256")
        val candidateHash=WorkoutStoreRuntimeCodec.text(result,"candidateSnapshotSha256")
        require(currentHash==WorkoutStoreRuntimeCodec.sha(currentSnapshot) &&
            incomingHash==WorkoutStoreRuntimeCodec.sha(incomingSnapshot) &&
            candidateHash==expectedCandidateSha256)
        val candidate=WorkoutStoreRuntimeCodec.text(result,"candidateSnapshot")
        WorkoutStoreRuntimeCodec.bounded(candidate,LIMIT)
        val byteCount=WorkoutStoreRuntimeCodec.integer(result,"candidateSnapshotBytes")
        require(byteCount in 1..LIMIT.toLong() &&
            candidate.toByteArray(Charsets.UTF_8).size.toLong()==byteCount &&
            WorkoutStoreRuntimeCodec.sha(candidate)==candidateHash)
        // Structural minimum only; the trusted canonical JS parser is the
        // schema authority. Do NOT reinterpret a merged workout in Kotlin.
        val body=JSONObject(candidate)
        require(body.get("v") is Number && body.getInt("v")==13 &&
            body.get("saved") is org.json.JSONArray &&
            body.get("history") is org.json.JSONArray &&
            body.get("tombs") is JSONObject)
        return NativeBackupMergePreparation(candidate,currentHash,incomingHash,
            candidateHash,byteCount.toInt())
    }
}
