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
    val activeSeconds:Int,
    /** Monotonic instant at which the athlete explicitly paused this timer. */
    val pausedAtElapsed:Long?=null
)

internal object NativeRestCountdownPolicy {
    const val MAX_SECONDS=7200
    private const val CLOCK_TOLERANCE_MILLIS=60_000L

    fun valid(checkpoint:NativeRestCountdownCheckpoint,elapsed:Long,wall:Long):Boolean {
        if(checkpoint.startedElapsed<=0L || checkpoint.startedWall<=0L ||
            elapsed<checkpoint.startedElapsed || wall<checkpoint.startedWall ||
            checkpoint.activeSeconds !in 1..MAX_SECONDS)return false
        if(checkpoint.deadlineElapsed<=checkpoint.startedElapsed)return false
        val duration=checkpoint.deadlineElapsed-checkpoint.startedElapsed
        if(duration>MAX_SECONDS*1000L ||
            checkpoint.activeSeconds.toLong()!=(duration+999L)/1000L)return false
        val paused=checkpoint.pausedAtElapsed
        if(paused!=null && (paused<checkpoint.startedElapsed ||
                paused>=checkpoint.deadlineElapsed || paused>elapsed))return false
        val elapsedAge=elapsed-checkpoint.startedElapsed
        val wallAge=wall-checkpoint.startedWall
        // Never resurrect an ancient 'Rest complete' from a past session.
        if(elapsedAge>MAX_SECONDS*1000L+CLOCK_TOLERANCE_MILLIS)return false
        return abs(wallAge-elapsedAge)<=CLOCK_TOLERANCE_MILLIS
    }

    /** Null is stale/invalid, zero is completed. Times are rounded UP. */
    fun remaining(checkpoint:NativeRestCountdownCheckpoint,elapsed:Long,wall:Long):Long? {
        if(!valid(checkpoint,elapsed,wall))return null
        val clock=checkpoint.pausedAtElapsed ?:elapsed
        return ((checkpoint.deadlineElapsed-clock).coerceAtLeast(0L)+999L)/1000L
    }

    fun begin(seconds:Int,elapsed:Long,wall:Long):NativeRestCountdownCheckpoint? {
        if(seconds !in 1..MAX_SECONDS || elapsed<=0L || wall<=0L ||
            elapsed>Long.MAX_VALUE-seconds*1000L)return null
        return NativeRestCountdownCheckpoint(elapsed,wall,
            elapsed+seconds*1000L,seconds)
    }

    /** Pause only a still-running, valid countdown; never resurrect expired rest. */
    fun pause(checkpoint:NativeRestCountdownCheckpoint,elapsed:Long,wall:Long)
        :NativeRestCountdownCheckpoint? {
        if(checkpoint.pausedAtElapsed!=null || (remaining(checkpoint,elapsed,wall) ?:0L)<=0L)
            return null
        return checkpoint.copy(pausedAtElapsed=elapsed)
    }

    /** Move both clock anchors by the exact paused interval, preserving
     * fractional seconds and the original active-time denominator. */
    fun resume(checkpoint:NativeRestCountdownCheckpoint,elapsed:Long,wall:Long)
        :NativeRestCountdownCheckpoint? {
        val paused=checkpoint.pausedAtElapsed ?:return null
        if(!valid(checkpoint,elapsed,wall))return null
        val pausedFor=elapsed-paused
        if(checkpoint.deadlineElapsed>Long.MAX_VALUE-pausedFor ||
            checkpoint.startedWall>Long.MAX_VALUE-pausedFor)return null
        val resumed=checkpoint.copy(
            startedElapsed=checkpoint.startedElapsed+pausedFor,
            startedWall=checkpoint.startedWall+pausedFor,
            deadlineElapsed=checkpoint.deadlineElapsed+pausedFor,
            pausedAtElapsed=null)
        return resumed.takeIf{valid(it,elapsed,wall)}
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
        val clock=checkpoint.pausedAtElapsed ?:elapsed
        val remainingMillis=(checkpoint.deadlineElapsed-clock).coerceAtLeast(0L)
        if(remainingMillis==0L)return null
        val elapsedSinceStart=clock-checkpoint.startedElapsed
        val availableMillis=MAX_SECONDS*1000L-elapsedSinceStart
        val next=(remainingMillis+changeSeconds*1000L).coerceIn(0L,availableMillis)
        if(next==0L)return null
        if(clock>Long.MAX_VALUE-next)return null
        val nextDeadline=clock+next
        val total=(elapsedSinceStart+next+999L)/1000L
        return checkpoint.copy(deadlineElapsed=nextDeadline,activeSeconds=total.toInt())
    }

    fun encode(checkpoint:NativeRestCountdownCheckpoint):String {
        val paused=checkpoint.pausedAtElapsed
        val base=listOf(if(paused==null)"1" else "2",checkpoint.startedElapsed,
            checkpoint.startedWall,checkpoint.deadlineElapsed,checkpoint.activeSeconds)
        return (if(paused==null)base else base+paused).joinToString("|")
    }

    fun decode(raw:String?):NativeRestCountdownCheckpoint? {
        if(raw==null || raw.length>120)return null
        val fields=raw.split('|')
        val pausedVersion=fields.size==6 && fields[0]=="2"
        if(!pausedVersion && (fields.size!=5 || fields[0]!="1"))return null
        val elapsed=fields[1].toLongOrNull() ?:return null
        val wall=fields[2].toLongOrNull() ?:return null
        val deadline=fields[3].toLongOrNull() ?:return null
        val seconds=fields[4].toIntOrNull() ?:return null
        val paused=if(pausedVersion)fields[5].toLongOrNull() ?:return null else null
        val result=NativeRestCountdownCheckpoint(elapsed,wall,deadline,seconds,paused)
        // A decoded timestamp has to be structurally valid before restoring.
        if(elapsed<=0L || wall<=0L || seconds !in 1..MAX_SECONDS ||
            deadline<=elapsed || deadline-elapsed>MAX_SECONDS*1000L ||
            seconds.toLong()!=(deadline-elapsed+999L)/1000L ||
            (paused!=null && (paused<elapsed || paused>=deadline)))return null
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

    /**
     * A recovered countdown must also belong to the latest independently
     * confirmed non-note journal event. If the last set changed while this
     * UI was away, an older manual timer is not allowed to impersonate it.
     * Hashing avoids storing an event ID in the presentation preference.
     */
    private fun receiptStamp(receiptEventId:String?):String {
        val event=receiptEventId ?: "<before first set>"
        val bytes=("receipt:"+event.length+":"+event).toByteArray(Charsets.UTF_8)
        return MessageDigest.getInstance("SHA-256")
            .digest(bytes).joinToString(""){"%02x".format(it)}
    }

    fun read(
        key:String,receiptEventId:String?,elapsed:Long,wall:Long
    ):NativeRestCountdownCheckpoint? {
        return runCatching {
            val raw=preferences.getString(key,null)
            val stamp=receiptStamp(receiptEventId)
            val scoped=raw?.takeIf{it.startsWith("$stamp:")}
                ?.substring(stamp.length+1)
            val checkpoint=NativeRestCountdownPolicy.decode(scoped)
            if(checkpoint!=null &&
                NativeRestCountdownPolicy.remaining(checkpoint,elapsed,wall)!=null)
                checkpoint
            else {
                // A changed workout receipt (or stale time) invalidates only
                // this UI timer, not the durable workout journal.
                preferences.edit().remove(key).apply()
                null
            }
        }.getOrNull()
    }

    fun save(
        key:String?,receiptEventId:String?,
        checkpoint:NativeRestCountdownCheckpoint?
    ) {
        if(key==null)return
        runCatching {
            val edit=preferences.edit()
            if(checkpoint==null)edit.remove(key)
            else edit.putString(key,receiptStamp(receiptEventId)+":"+
                NativeRestCountdownPolicy.encode(checkpoint))
            edit.apply()
        }
    }
}

@Composable
internal fun rememberNativeRestCountdownStore():NativeKotlinRestCountdownStore {
    val app=LocalContext.current.applicationContext
    return remember(app){NativeKotlinRestCountdownStore(app)}
}
