package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject

/**
 * Redacted, informational result only. Neither a consent receipt nor bytes
 * eligible for import into CanonicalWorkoutStorePersistence.
 */
data class NativeBackupMergeCounts(
    val saved: Int, val history: Int, val cycles: Int, val custom: Int, val gyms: Int
)
data class NativeBackupMergePreview(
    val currentSha256: String,
    val incomingSha256: String,
    val hypotheticalMergedSha256: String,
    val hypotheticalMergedBytes: Int,
    val current: NativeBackupMergeCounts,
    val incoming: NativeBackupMergeCounts,
    val hypothetical: NativeBackupMergeCounts,
    val conflicts: Int,
    val historyDropped: Int,
    val flags: List<String>
) {
    val requiresIntervention: Boolean get() = flags.isNotEmpty()
}

/** Strict decoder for the canonical JS dry-run. No candidate store is returned. */
object NativeBackupMergePreviewCodec {
    private const val METHOD = "previewBackupMerge"
    private const val STORE_LIMIT = 1024 * 1024
    private val hash = Regex("^[0-9a-f]{64}$")
    private val collectionNames = arrayOf("saved","history","cycles","custom","gyms")

    fun encode(id: String, currentSnapshot: String, incomingSnapshot: String): String {
        WorkoutStoreRuntimeCodec.bounded(currentSnapshot,STORE_LIMIT)
        WorkoutStoreRuntimeCodec.bounded(incomingSnapshot,STORE_LIMIT)
        return WorkoutStoreRuntimeCodec.encode(id,METHOD,JSONObject()
            .put("currentSnapshot",currentSnapshot)
            .put("incomingSnapshot",incomingSnapshot))
    }

    fun decode(raw: String, id: String, currentSnapshot: String, incomingSnapshot: String): NativeBackupMergePreview? {
        WorkoutStoreRuntimeCodec.bounded(currentSnapshot,STORE_LIMIT)
        WorkoutStoreRuntimeCodec.bounded(incomingSnapshot,STORE_LIMIT)
        val result = WorkoutStoreRuntimeCodec.result(raw,id) ?: return null
        WorkoutStoreRuntimeCodec.exact(result,
            "source","validation","readOnly","imported","approvedForImport",
            "approvedForWorkoutExecution","requiresExplicitApproval","storeVersion",
            "currentSha256","incomingSha256","hypotheticalMergedSha256",
            "hypotheticalMergedBytes","counts","conflicts","historyDropped","flags")
        require(result.get("source") == "modules/engine-api.js:mergeStores" &&
            result.get("validation") == "canonical-v13-merge-preview" &&
            result.get("readOnly") == true && result.get("imported") == false &&
            result.get("approvedForImport") == false &&
            result.get("approvedForWorkoutExecution") == false &&
            result.get("requiresExplicitApproval") == true &&
            WorkoutStoreRuntimeCodec.integer(result,"storeVersion") == 13L)

        val currentHash = WorkoutStoreRuntimeCodec.text(result,"currentSha256")
        val incomingHash = WorkoutStoreRuntimeCodec.text(result,"incomingSha256")
        val candidateHash = WorkoutStoreRuntimeCodec.text(result,"hypotheticalMergedSha256")
        require(currentHash == WorkoutStoreRuntimeCodec.sha(currentSnapshot) &&
            incomingHash == WorkoutStoreRuntimeCodec.sha(incomingSnapshot) &&
            hash.matches(candidateHash))

        val hypotheticalBytes = boundedInt(result,"hypotheticalMergedBytes",16 * 1024 * 1024)
        require(hypotheticalBytes > 0)
        val collisions = boundedInt(result,"conflicts",100000)
        val lost = boundedInt(result,"historyDropped",100000)
        val counts = result.getJSONObject("counts")
        WorkoutStoreRuntimeCodec.exact(counts,"current","incoming","hypothetical")
        val original = decodeCounts(counts.getJSONObject("current"))
        val selected = decodeCounts(counts.getJSONObject("incoming"))
        val hypothetical = decodeCounts(counts.getJSONObject("hypothetical"))

        val rawFlags = result.getJSONArray("flags")
        require(rawFlags.length() <= 3)
        val flags = (0 until rawFlags.length()).map { index ->
            val value = rawFlags.get(index)
            require(value is String)
            value
        }
        val expected = buildList {
            if (lost > 0) add("HISTORY_CAP_LOSS")
            if (hypotheticalBytes > STORE_LIMIT) add("NATIVE_STORE_CAPACITY")
            if (collisions > 0) add("RECORD_CONFLICTS_REQUIRE_REVIEW")
        }
        require(flags == expected)
        return NativeBackupMergePreview(currentHash,incomingHash,candidateHash,
            hypotheticalBytes,original,selected,hypothetical,collisions,lost,flags)
    }

    private fun boundedInt(value: JSONObject,key: String,limit: Int): Int {
        val number = WorkoutStoreRuntimeCodec.integer(value,key)
        require(number in 0L..limit.toLong())
        return number.toInt()
    }
    private fun decodeCounts(value: JSONObject): NativeBackupMergeCounts {
        WorkoutStoreRuntimeCodec.exact(value,*collectionNames)
        return NativeBackupMergeCounts(
            boundedInt(value,"saved",100000),boundedInt(value,"history",100000),
            boundedInt(value,"cycles",100000),boundedInt(value,"custom",100000),
            boundedInt(value,"gyms",100000)
        )
    }
}
