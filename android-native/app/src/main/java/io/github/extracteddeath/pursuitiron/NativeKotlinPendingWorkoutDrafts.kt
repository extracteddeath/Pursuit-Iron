package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import io.github.extracteddeath.pursuitiron.rebuild.*
import org.json.JSONObject
import java.security.MessageDigest

/**
 * Private PRESENTATION drafts, not an issued prescription, journal, outcome,
 * approval, machine calibration or coaching input. Nothing here records a set.
 * The independently verified workout and its exact target must be opened
 * before a matching entry can be read.
 */
internal data class NativePendingWorkoutDraft(
    val load:String,val reps:String,val rir:String
)
internal data class NativePendingWorkoutDraftKey(val value:String)

internal object NativePendingWorkoutDraftIdentity {
    fun make(
        athleteId:String,workoutId:String,gymId:String,exerciseId:String,
        setKey:String,visitPart:String,machineId:String?,
        load:DraftSetLoad,reps:RepBand,targetRir:Int?
    ):NativePendingWorkoutDraftKey {
        val issuedLoad=when(load) {
            is DraftSetLoad.Verified -> listOf(
                "verified",load.value.toPlainString(),load.unit.name,load.basis.name)
            DraftSetLoad.ExplicitBodyweight -> listOf("bodyweight")
            else -> listOf("other",load.toString())
        }
        // Length prefixes distinguish arbitrary separator characters and
        // prevent exercise, machine and visit identifiers from aliasing.
        val fields=listOf("v1",athleteId,workoutId,gymId,exerciseId,
            setKey,visitPart,machineId ?: "<no machine>") +
            issuedLoad+listOf(reps.minimum.toString(),reps.maximum.toString(),
                targetRir?.toString() ?: "<no RIR>")
        val encoded=fields.joinToString(""){"${it.length}:$it"}
        val hash=MessageDigest.getInstance("SHA-256")
            .digest(encoded.toByteArray(Charsets.UTF_8))
            .joinToString(""){"%02x".format(it)}
        return NativePendingWorkoutDraftKey("input:$hash")
    }
}

/**
 * Bounded app-private SharedPreferences store. apply() does not block the
 * workout's typing/stepper frame with disk I/O; Activity lifecycle flushes
 * pending writes. If storage is unavailable, the ordinary live editor still
 * works. This intentionally does NOT participate in the native journal's
 * authoritative recovery path.
 */
internal class NativeKotlinPendingWorkoutDraftStore(context:Context) {
    private val preferences:SharedPreferences=context.applicationContext
        .getSharedPreferences("kotlin-unsaved-workout-entry-v1",Context.MODE_PRIVATE)

    fun read(key:NativePendingWorkoutDraftKey):NativePendingWorkoutDraft? =
        runCatching {
            val raw=preferences.getString(key.value,null)
                ?.takeIf{it.length<=512} ?:return@runCatching null
            val json=JSONObject(raw)
            if(json.optInt("v")!=1 || !json.has("load") ||
                !json.has("reps") || !json.has("rir"))
                return@runCatching null
            val fields=listOf(
                json.getString("load"),json.getString("reps"),json.getString("rir"))
            if(fields.any{it.length>64})return@runCatching null
            NativePendingWorkoutDraft(fields[0],fields[1],fields[2])
        }.getOrNull()

    fun save(key:NativePendingWorkoutDraftKey,draft:NativePendingWorkoutDraft):Boolean {
        if(listOf(draft.load,draft.reps,draft.rir).any{it.length>64})
            return false
        return runCatching {
            val json=JSONObject().put("v",1)
                .put("load",draft.load).put("reps",draft.reps).put("rir",draft.rir)
            preferences.edit().putString(key.value,json.toString()).apply()
            true
        }.getOrDefault(false)
    }

    fun discard(key:NativePendingWorkoutDraftKey) {
        runCatching{preferences.edit().remove(key.value).apply()}
    }
}

@Composable
internal fun rememberNativePendingWorkoutDraftStore():NativeKotlinPendingWorkoutDraftStore {
    val context=LocalContext.current.applicationContext
    return remember(context){NativeKotlinPendingWorkoutDraftStore(context)}
}
