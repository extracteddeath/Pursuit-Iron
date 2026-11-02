package io.github.extracteddeath.pursuitiron.domain

import org.json.JSONObject

/** A session-only factual note, separate from set observations and progression. */
data class JournalSessionNote(
    val operationId: String,val text: String,val updatedAt: Long,val replacesOperationId: String?=null
)

object JournalSessionNoteCodec {
    const val MAX_CHARS=2000
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_-]{0,127}$")
    fun validText(text: String): Boolean = text.length<=MAX_CHARS &&
        text.none { it.isISOControl() && it!='\n' && it!='\t' } &&
        runCatching { if(text.isNotEmpty())WorkoutStoreRuntimeCodec.bounded(text,8192);true }.getOrDefault(false)

    fun valid(note: JournalSessionNote,session: JournalSessionKey): Boolean =
        id.matches(note.operationId) && validText(note.text) &&
            note.updatedAt in session.startedAt..8_640_000_000_000_000L &&
            (note.replacesOperationId==null || id.matches(note.replacesOperationId) && note.replacesOperationId!=note.operationId)

    internal fun json(note: JournalSessionNote)=JSONObject().put("operationId",note.operationId)
        .put("text",note.text).put("updatedAt",note.updatedAt)
        .put("replacesOperationId",note.replacesOperationId ?: JSONObject.NULL)

    internal fun read(raw: JSONObject,session: JournalSessionKey): JournalSessionNote {
        WorkoutStoreRuntimeCodec.exact(raw,"operationId","text","updatedAt","replacesOperationId")
        val text=raw.get("text");require(text is String)
        return JournalSessionNote(WorkoutStoreRuntimeCodec.text(raw,"operationId"),text,
            WorkoutStoreRuntimeCodec.integer(raw,"updatedAt"),
            if(raw.isNull("replacesOperationId"))null else WorkoutStoreRuntimeCodec.text(raw,"replacesOperationId"))
            .also { require(valid(it,session)) }
    }
}
