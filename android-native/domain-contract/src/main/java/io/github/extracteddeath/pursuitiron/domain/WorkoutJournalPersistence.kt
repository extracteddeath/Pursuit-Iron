package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONArray
import org.json.JSONObject
import java.nio.charset.StandardCharsets
import java.security.MessageDigest

/**
 * The ONLY bytes boundary for the private, in-progress workout journal.
 * Implementations must commit atomically (all old bytes OR all new bytes),
 * never truncate the prior committed state on failure. One process-scoped
 * owner must serialize all operations; no cross-process locking is provided.
 */
interface WorkoutJournalBytes {
    fun read(): ByteArray?
    fun commitAtomically(bytes: ByteArray)
}

/**
 * Crash-recoverable PRE-HISTORY journal. This is not canonical v13 history,
 * a personal workout generator, or evidence of a certified workout. Callers
 * must acquire verified canonical session identity and slots separately.
 *
 * All mutations reread the owned journal under a single owner lock and only
 * return a new state after atomic persistence. A crash after commit but
 * before acknowledgement is resolved by retrying the exact operation ID.
 */
class WorkoutJournalPersistence(private val bytes: WorkoutJournalBytes) {
    companion object {
        const val MAX_JOURNAL_BYTES = 256 * 1024
        private const val FORMAT = 2
    }

    private val guard = Any()
    private val hex = "0123456789abcdef"
    private val integer = Regex("^(0|[1-9][0-9]*)$")

    private fun sha(source: ByteArray): String {
        val digest = MessageDigest.getInstance("SHA-256").digest(source)
        return buildString(64) {
            for (b in digest) {
                val n = b.toInt() and 255
                append(hex[n ushr 4])
                append(hex[n and 15])
            }
        }
    }

    private fun exact(json: JSONObject, vararg keys: String) {
        require(json.keys().asSequence().toSet() == keys.toSet()) {
            "Unexpected journal field"
        }
    }

    private fun number(json: JSONObject, key: String): Long {
        val v = json.get(key)
        require(v is Number)
        val string = v.toString()
        require(integer.matches(string))
        return requireNotNull(string.toLongOrNull())
    }

    private fun text(json: JSONObject, key: String): String {
        val value = json.get(key)
        require(value is String && value.isNotEmpty())
        return value
    }

    private fun refJson(ref: ReadOnlyProgramReference) = JSONObject()
        .put("programId",ref.programId).put("programUpdatedAt",ref.programUpdatedAt)
        .put("storeSavedAt",ref.storeSavedAt).put("storeVersion",ref.storeVersion)
        .put("snapshotSha256",ref.snapshotSha256)

    private fun keyJson(key: JournalSessionKey) = JSONObject()
        .put("reference",refJson(key.programRevision))
        .put("workoutId",key.workoutId)
        .put("dayId",key.dayId).put("weekIndex",key.weekIndex)
        .put("unit",key.unit).put("startedAt",key.startedAt)

    private fun readKey(json: JSONObject): JournalSessionKey {
        exact(json,"reference","workoutId","dayId","weekIndex","unit","startedAt")
        val r = json.getJSONObject("reference")
        exact(r,"programId","programUpdatedAt","storeSavedAt","storeVersion","snapshotSha256")
        val version = number(r,"storeVersion")
        require(version <= Int.MAX_VALUE)
        val week = number(json,"weekIndex")
        require(week <= Int.MAX_VALUE)
        return JournalSessionKey(
            ReadOnlyProgramReference(
                text(r,"programId"),number(r,"programUpdatedAt"),
                number(r,"storeSavedAt"),version.toInt(),text(r,"snapshotSha256")
            ),
            text(json,"workoutId"),text(json,"dayId"),week.toInt(),
            text(json,"unit"),number(json,"startedAt")
        )
    }

    private fun slotJson(s: JournalSetSlot) = JSONObject()
        .put("exerciseIndex",s.exerciseIndex).put("setIndex",s.setIndex)
        .put("exerciseId",s.exerciseId)

    private fun readSlot(json: JSONObject): JournalSetSlot {
        exact(json,"exerciseIndex","setIndex","exerciseId")
        val ex = number(json,"exerciseIndex")
        val set = number(json,"setIndex")
        require(ex <= Int.MAX_VALUE && set <= Int.MAX_VALUE)
        return JournalSetSlot(ex.toInt(),set.toInt(),text(json,"exerciseId"))
    }

    private fun valuesJson(value: JournalSetValues?): Any {
        if (value == null) return JSONObject.NULL
        return JSONObject()
            .put("load",value.load ?: JSONObject.NULL)
            .put("unit",value.unit ?: JSONObject.NULL)
            .put("reps",value.reps)
            .put("rir",value.rir ?: JSONObject.NULL)
    }

    private fun readValues(raw: Any): JournalSetValues? {
        if (raw == JSONObject.NULL) return null
        require(raw is JSONObject)
        exact(raw,"load","unit","reps","rir")
        val load = if (raw.isNull("load")) null else text(raw,"load")
        val unit = if (raw.isNull("unit")) null else text(raw,"unit")
        val rep = number(raw,"reps")
        require(rep <= Int.MAX_VALUE)
        val rir = if (raw.isNull("rir")) null else {
            val n = number(raw,"rir")
            require(n <= Int.MAX_VALUE)
            n.toInt()
        }
        return JournalSetValues(load,unit,rep.toInt(),rir)
    }

    private fun eventJson(entry: JournalSetCommit): JSONObject {
        val intent = entry.intent
        val receipt = entry.receipt
        return JSONObject()
            .put("operationId",intent.operationId)
            .put("slot",slotJson(intent.slot))
            .put("disposition",intent.disposition.name)
            .put("values",valuesJson(intent.values))
            .put("fromRevision",receipt.fromRevision)
            .put("toRevision",receipt.toRevision)
    }

    private fun finishJson(entry: JournalFinishCommit?): Any =
        if (entry == null) JSONObject.NULL else JSONObject()
            .put("operationId",entry.intent.operationId)
            .put("fromRevision",entry.receipt.fromRevision)
            .put("toRevision",entry.receipt.toRevision)
            .put("completedAt",entry.intent.completedAt)
            .put("durationMs",entry.intent.durationMs)

    private fun initialRevision(state: WorkoutJournalState): Long =
        state.revision - state.completed.size.toLong() - if (state.isFinished) 1L else 0L

    private fun payloadJson(state: WorkoutJournalState) = JSONObject()
            .put("format",FORMAT)
            .put("session",keyJson(state.session))
            .put("slots",JSONArray().apply { state.slots.forEach { put(slotJson(it)) } })
            .put("startRevision",initialRevision(state))
            .put("events",JSONArray().apply {
                state.completed.forEach { put(eventJson(it)) }
            })
            .put("finish",finishJson(state.finish))

    private fun encode(state: WorkoutJournalState): ByteArray {
        val payload = payloadJson(state).toString()
        val envelope = JSONObject()
            .put("format",FORMAT)
            .put("payload",payload)
            .put("sha256",sha(payload.toByteArray(StandardCharsets.UTF_8)))
            .toString().toByteArray(StandardCharsets.UTF_8)
        require(envelope.size in 1..MAX_JOURNAL_BYTES)
        return envelope
    }

    /**
     * Reject corruption, altered receipts, stale revisions and malformed
     * events instead of silently creating a new blank workout. A digest here
     * detects accidental corruption; it is NOT a signature or authenticity
     * proof for a user-selected/imported file.
     */
    private fun decode(source: ByteArray): WorkoutJournalState {
        require(source.size in 1..MAX_JOURNAL_BYTES)
        val envelope=JSONObject(String(source,StandardCharsets.UTF_8))
        exact(envelope,"format","payload","sha256")
        require(number(envelope,"format") == FORMAT.toLong())
        val payload = text(envelope,"payload")
        val digest = text(envelope,"sha256")
        val actual = sha(payload.toByteArray(StandardCharsets.UTF_8))
        require(digest.matches(Regex("^[0-9a-f]{64}$")) &&
            MessageDigest.isEqual(
                digest.toByteArray(StandardCharsets.US_ASCII),
                actual.toByteArray(StandardCharsets.US_ASCII)
            ))
        val data=JSONObject(payload)
        exact(data,"format","session","slots","startRevision","events","finish")
        require(number(data,"format") == FORMAT.toLong())
        val key=readKey(data.getJSONObject("session"))
        val slotsJson=data.getJSONArray("slots")
        require(slotsJson.length() in 1..480)
        val slots=(0 until slotsJson.length()).map { readSlot(slotsJson.getJSONObject(it)) }
        var state=requireNotNull(WorkoutJournalPolicy.open(key,slots,number(data,"startRevision")))
        val events=data.getJSONArray("events")
        require(events.length() <= slots.size)
        for (index in 0 until events.length()) {
            val raw=events.getJSONObject(index)
            exact(raw,"operationId","slot","disposition","values","fromRevision","toRevision")
            val op=text(raw,"operationId")
            val slot=readSlot(raw.getJSONObject("slot"))
            val disposition=JournalSetDisposition.valueOf(text(raw,"disposition"))
            val values=readValues(raw.get("values"))
            val intent=JournalSetIntent(op,slot,disposition,values)
            val receipt=JournalSetReceipt(key,op,slot,disposition,values,
                number(raw,"fromRevision"),number(raw,"toRevision"))
            state=requireNotNull(WorkoutJournalPolicy.afterPersistedSet(state,intent,receipt))
        }
        if (!data.isNull("finish")) {
            val finish=data.getJSONObject("finish")
            exact(finish,"operationId","fromRevision","toRevision","completedAt","durationMs")
            val op=text(finish,"operationId")
            val completedAt=number(finish,"completedAt")
            val durationMs=number(finish,"durationMs")
            state=requireNotNull(WorkoutJournalPolicy.afterPersistedFinish(
                state,JournalFinishIntent(op,completedAt,durationMs),
                JournalFinishReceipt(key,op,number(finish,"fromRevision"),
                    number(finish,"toRevision"),completedAt,durationMs)
            ))
        }
        return state
    }

    private fun readOwned(): WorkoutJournalState? {
        val content=bytes.read() ?: return null
        try {
            return decode(content)
        } finally {
            content.fill(0)
        }
    }

    private fun save(state: WorkoutJournalState) {
        val content=encode(state)
        try {
            bytes.commitAtomically(content)
        } finally {
            content.fill(0)
        }
    }

    fun recover(): WorkoutJournalState? = synchronized(guard) { readOwned() }

    /**
     * Exact, detached handoff of a DURABLY finished private journal. This
     * does not publish history, clear recovery, or authorize a training UI.
     * Context and finish clocks come from disk, never from a retry's wall clock.
     */
    fun finishedHandoff(session: JournalSessionKey, expectedRevision: Long): String? =
        synchronized(guard) {
            val state=readOwned() ?: return@synchronized null
            if (state.session != session || state.revision != expectedRevision ||
                !state.isFinished) return@synchronized null
            payloadJson(state).toString()
        }

    /** Does not overwrite unresolved or finished sessions, even after crashes. */
    fun begin(
        session: JournalSessionKey, slots: List<JournalSetSlot>, revision: Long
    ): WorkoutJournalState? = synchronized(guard) {
        val existing=readOwned()
        if (existing != null) {
            return@synchronized existing.takeIf {
                it.session == session && it.slots == slots &&
                    initialRevision(it) == revision
            }
        }
        val proposed=WorkoutJournalPolicy.open(session,slots,revision)
            ?: return@synchronized null
        save(proposed)
        proposed
    }

    fun recordSet(
        session: JournalSessionKey, expectedRevision: Long,
        intent: JournalSetIntent
    ): WorkoutJournalState? = synchronized(guard) {
        val current=readOwned() ?: return@synchronized null
        if (current.session != session) return@synchronized null
        // Crash-after-commit retry: identical operation and payload can safely
        // recover a previously acknowledged write, even with an older CAS.
        val replay=current.completed.find { it.intent.operationId == intent.operationId }
        if (replay != null) return@synchronized if (replay.intent == intent &&
            replay.receipt.fromRevision == expectedRevision) current else null
        if (expectedRevision != current.revision) return@synchronized null
        val receipt=JournalSetReceipt(
            session,intent.operationId,intent.slot,intent.disposition,intent.values,
            current.revision,current.revision+1
        )
        val next=WorkoutJournalPolicy.afterPersistedSet(current,intent,receipt)
            ?: return@synchronized null
        save(next)
        next
    }

    fun finish(
        session: JournalSessionKey, expectedRevision: Long,
        intent: JournalFinishIntent
    ): WorkoutJournalState? = synchronized(guard) {
        val current=readOwned() ?: return@synchronized null
        if (current.session != session) return@synchronized null
        val replay=current.finish
        if (replay != null) return@synchronized if (replay.intent == intent &&
            replay.receipt.fromRevision == expectedRevision) current else null
        if (expectedRevision != current.revision) return@synchronized null
        val receipt=JournalFinishReceipt(session,intent.operationId,
            current.revision,current.revision+1,intent.completedAt,intent.durationMs)
        val next=WorkoutJournalPolicy.afterPersistedFinish(current,intent,receipt)
            ?: return@synchronized null
        save(next)
        next
    }
}
