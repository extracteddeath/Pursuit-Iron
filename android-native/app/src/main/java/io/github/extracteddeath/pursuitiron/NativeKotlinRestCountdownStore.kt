package io.github.extracteddeath.pursuitiron

import android.content.Context
import android.content.SharedPreferences
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.security.MessageDigest
import kotlin.math.abs

/**
 * The countdown is only a presentation of actual elapsed time. It never
 * records completed sets or writes to coach/prescription/history storage.
 * A clock rollback, device reboot, corruption or incorrect owner scope makes
 * a saved timer disappear instead of fabricating remaining rest.
 */
internal data class NativeRestCountdownCheckpoint(
    val startedElapsed:Long,
    val startedWall:Long,
    val deadlineElapsed:Long,
    val activeSeconds:Int
)

internal object NativeRestCountdownPolicy {
    const val MAX_SECONDS=7200
    private const val CLOCK_TOLERANCE_MILLIS=60_000L

    fun valid(checkpoint:NativeRestCountdownCheckpoint,elapsed:Long,wall:Long):Boolean {
        if(checkpoint.startedElapsed<=0L || checkpoint.startedWall<=0L ||
            elapsed<checkpoint.startedElapsed || wall<checkpoint.startedWall ||
            checkpoint.activeSeconds !in 1..MAX_SECONDS)return false
        val duration=checkpoint.deadlineElapsed-checkpoint.startedElapsed
        if(duration<=0L || duration>MAX_SECONDS*1000L)return false
        val elapsedAge=elapsed-checkpoint.startedElapsed
        val wallAge=wall-checkpoint.startedWall
        // Never resurrect an ancient 'Rest complete' from a past session.
        if(elapsedAge>MAX_SECONDS*1000L+CLOCK_TOLERANCE_MILLIS)return false
        return abs(wallAge-elapsedAge)<=CLOCK_TOLERANCE_MILLIS
    }

    /** Null is stale/invalid, zero is completed. Times are rounded UP. */
    fun remaining(checkpoint:NativeRestCountdownCheckpoint,elapsed:Long,wall:Long):Long? {
        if(!valid(checkpoint,elapsed,wall))return null
        return ((checkpoint.deadlineElapsed-elapsed).coerceAtLeast(0L)+999L)/1000L
    }

    fun begin(seconds:Int,elapsed:Long,wall:Long):NativeRestCountdownCheckpoint? {
        if(seconds !in 1..MAX_SECONDS || elapsed<=0L || wall<=0L)return null
        return NativeRestCountdownCheckpoint(elapsed,wall,
            elapsed+seconds*1000L,seconds)
    }

    /**
     * Adjust remaining real time, not the authored prescription. Preserve the
     * original clock anchor and keep a bounded progress denominator. Reduction
     * to zero clears the countdown. No extra rest is ever silently added.
     */
    fun adjust(
        checkpoint:NativeRestCountdownCheckpoint,
        changeSeconds:Int,elapsed:Long,wall:Long
    ):NativeRestCountdownCheckpoint? {
        if(remaining(checkpoint,elapsed,wall)==null)return null
        val remainingMillis=(checkpoint.deadlineElapsed-elapsed).coerceAtLeast(0L)
        if(remainingMillis==0L)return null
        val next=(remainingMillis+changeSeconds*1000L)
            .coerceIn(0L,MAX_SECONDS*1000L)
        if(next==0L)return null
        val nextDeadline=elapsed+next
        val total=((nextDeadline-checkpoint.startedElapsed)+999L)/1000L
        if(total !in 1..MAX_SECONDS)return null
        return checkpoint.copy(deadlineElapsed=nextDeadline,activeSeconds=total.toInt())
    }

    fun encode(checkpoint:NativeRestCountdownCheckpoint):String =
        listOf("1",checkpoint.startedElapsed,checkpoint.startedWall,
            checkpoint.deadlineElapsed,checkpoint.activeSeconds).joinToString("|")

    fun decode(raw:String?):NativeRestCountdownCheckpoint? {
        if(raw==null || raw.length>120)return null
        val fields=raw.split('|')
        if(fields.size!=5 || fields[0]!="1")return null
        val elapsed=fields[1].toLongOrNull() ?:return null
        val wall=fields[2].toLongOrNull() ?:return null
        val deadline=fields[3].toLongOrNull() ?:return null
        val seconds=fields[4].toIntOrNull() ?:return null
        val result=NativeRestCountdownCheckpoint(elapsed,wall,deadline,seconds)
        // A decoded timestamp has to be structurally valid before restoring.
        if(elapsed<=0L || wall<=0L || seconds !in 1..MAX_SECONDS ||
            deadline<=elapsed || deadline-elapsed>MAX_SECONDS*1000L)return null
        return result
    }
}

internal class NativeKotlinRestCountdownStore(context:Context) {
    private val preferences:SharedPreferences=context.applicationContext
        .getSharedPreferences("kotlin-workout-rest-v1",Context.MODE_PRIVATE)

    fun scopedKey(athleteId:String,workoutId:String):String? {
        if(athleteId.isBlank() || workoutId.isBlank())return null
        val parts=listOf("v1",athleteId,workoutId)
        val bytes=parts.joinToString(""){"${it.length}:$it"}
            .toByteArray(Charsets.UTF_8)
        val digest=MessageDigest.getInstance("SHA-256").digest(bytes)
        return "rest:"+digest.joinToString(""){"%02x".format(it)}
    }

    fun read(key:String,elapsed:Long,wall:Long):NativeRestCountdownCheckpoint? {
        return runCatching {
            val checkpoint=NativeRestCountdownPolicy.decode(
                preferences.getString(key,null))
            if(checkpoint!=null &&
                NativeRestCountdownPolicy.remaining(checkpoint,elapsed,wall)!=null)
                checkpoint
            else {
                preferences.edit().remove(key).apply()
                null
            }
        }.getOrNull()
    }

    fun save(key:String?,checkpoint:NativeRestCountdownCheckpoint?) {
        if(key==null)return
        runCatching {
            val edit=preferences.edit()
            if(checkpoint==null)edit.remove(key)
            else edit.putString(key,NativeRestCountdownPolicy.encode(checkpoint))
            edit.apply()
        }
    }
}

@Composable
internal fun rememberNativeRestCountdownStore():NativeKotlinRestCountdownStore {
    val app=LocalContext.current.applicationContext
    return remember(app){NativeKotlinRestCountdownStore(app)}
}
