package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
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

/** Shared Compose editor for both native workout journals. */
internal class NativeKotlinPendingWorkoutDraftEditor(
    private val loadState:MutableState<String>,
    private val repsState:MutableState<String>,
    private val rirState:MutableState<String>,
    private val recoveredState:MutableState<Boolean>,
    private val identity:NativePendingWorkoutDraftKey,
    private val baseline:NativePendingWorkoutDraft,
    private val committed:Boolean,
    private val store:NativeKotlinPendingWorkoutDraftStore
) {
    val load:String get()=loadState.value
    val reps:String get()=repsState.value
    val rir:String get()=rirState.value
    val recovered:Boolean get()=recoveredState.value && !committed

    /** Only an athlete UI edit or an explicit Leveling/Last tap calls this. */
    fun update(load:String=this.load,reps:String=this.reps,rir:String=this.rir) {
        loadState.value=load
        repsState.value=reps
        rirState.value=rir
        recoveredState.value=false
        if(!committed)store.save(identity,NativePendingWorkoutDraft(load,reps,rir))
    }

    /** Discard never clears a saved journal receipt or changes a prescription. */
    fun discard() {
        store.discard(identity)
        loadState.value=baseline.load
        repsState.value=baseline.reps
        rirState.value=baseline.rir
        recoveredState.value=false
    }
}

@Composable
internal fun rememberNativePendingWorkoutDraftEditor(
    identity:NativePendingWorkoutDraftKey,
    committed:Boolean,
    receiptRevision:String?,
    baseline:NativePendingWorkoutDraft
):NativeKotlinPendingWorkoutDraftEditor {
    val store=rememberNativePendingWorkoutDraftStore()
    // The only persisted source is the exact authenticated workout identity;
    // on a saved receipt, ignore the private draft even before cleanup runs.
    val recovered=remember(identity.value,receiptRevision,committed) {
        if(committed)null else store.read(identity)
    }
    val load=rememberSaveable(identity.value,receiptRevision) {
        mutableStateOf(recovered?.load ?: baseline.load)
    }
    val reps=rememberSaveable(identity.value,receiptRevision) {
        mutableStateOf(recovered?.reps ?: baseline.reps)
    }
    val rir=rememberSaveable(identity.value,receiptRevision) {
        mutableStateOf(recovered?.rir ?: baseline.rir)
    }
    val recoveredLabel=rememberSaveable(identity.value,receiptRevision) {
        mutableStateOf(recovered!=null)
    }
    LaunchedEffect(identity.value,committed,receiptRevision) {
        if(committed)store.discard(identity)
    }
    return NativeKotlinPendingWorkoutDraftEditor(
        load,reps,rir,recoveredLabel,identity,baseline,committed,store)
}
