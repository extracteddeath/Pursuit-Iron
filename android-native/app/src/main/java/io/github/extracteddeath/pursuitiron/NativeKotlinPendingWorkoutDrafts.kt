package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.platform.LocalContext
import io.github.extracteddeath.pursuitiron.rebuild.*
import java.security.MessageDigest
import java.util.Base64

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
                "verified",load.value.stripTrailingZeros().toPlainString(),load.unit.name,load.basis.name)
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

/** Versioned, bounded, untrusted UI text codec; safe on JVM and Android. */
internal object NativePendingWorkoutDraftCodec {
    private const val MAX_FIELD=64
    private const val MAX_RECORD=512

    /** A malformed surrogate or UTF-8 byte sequence must never be silently
     * replaced while restoring the exact text an athlete had typed. */
    private fun utf8RoundTrips(value:String):Boolean =
        String(value.toByteArray(Charsets.UTF_8),Charsets.UTF_8)==value

    fun encode(draft:NativePendingWorkoutDraft):String? {
        val parts=listOf(draft.load,draft.reps,draft.rir)
        if(parts.any{it.length>MAX_FIELD || !utf8RoundTrips(it)})return null
        val encoded="v1:"+parts.joinToString(":"){
            Base64.getEncoder().encodeToString(it.toByteArray(Charsets.UTF_8))
        }
        // Save must never report success for a record decode will reject.
        return encoded.takeIf{it.length<=MAX_RECORD}
    }
    fun decode(raw:String?):NativePendingWorkoutDraft? {
        if(raw==null || raw.length>MAX_RECORD)return null
        val parts=raw.split(':')
        if(parts.size!=4 || parts.first()!="v1")return null
        val decoded=parts.drop(1).map {field->
            runCatching {
                val bytes=Base64.getDecoder().decode(field)
                if(Base64.getEncoder().encodeToString(bytes)!=field)return null
                String(bytes,Charsets.UTF_8).takeIf{
                    it.length<=MAX_FIELD &&
                        it.toByteArray(Charsets.UTF_8).contentEquals(bytes)
                }
            }.getOrNull() ?:return null
        }
        return NativePendingWorkoutDraft(decoded[0],decoded[1],decoded[2])
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
            NativePendingWorkoutDraftCodec.decode(
                preferences.getString(key.value,null))
        }.getOrNull()

    fun save(key:NativePendingWorkoutDraftKey,draft:NativePendingWorkoutDraft):Boolean {
        val encoded=NativePendingWorkoutDraftCodec.encode(draft) ?:return false
        return runCatching {
            preferences.edit().putString(key.value,encoded).apply()
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
    // A newly committed receipt outranks previously typed, unsubmitted
    // fields immediately, even if a caller's revision token has not changed.
    val load=rememberSaveable(identity.value,receiptRevision,committed) {
        mutableStateOf(recovered?.load ?: baseline.load)
    }
    val reps=rememberSaveable(identity.value,receiptRevision,committed) {
        mutableStateOf(recovered?.reps ?: baseline.reps)
    }
    val rir=rememberSaveable(identity.value,receiptRevision,committed) {
        mutableStateOf(recovered?.rir ?: baseline.rir)
    }
    val recoveredLabel=rememberSaveable(identity.value,receiptRevision,committed) {
        mutableStateOf(recovered!=null)
    }
    LaunchedEffect(identity.value,committed,receiptRevision) {
        if(committed)store.discard(identity)
    }
    return NativeKotlinPendingWorkoutDraftEditor(
        load,reps,rir,recoveredLabel,identity,baseline,committed,store)
}
