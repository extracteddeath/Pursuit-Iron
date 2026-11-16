package io.github.extracteddeath.pursuitiron

/** Privacy-preserving user-authored beta reports. They are composed in memory,
 * never queued, uploaded or appended to the athlete's training records.
 * Android ACTION_SEND always requires a separate user-selected destination. */
internal enum class NativeBetaFeedbackKind {
    GENERAL, PROBLEM
}

internal data class NativeBetaFeedbackDraft(
    val kind:NativeBetaFeedbackKind,
    val message:String="",
    val workflow:String="",
    val steps:String="",
    val expected:String="",
    val actual:String=""
)

/** Content-only policy, safe to regression-test without Android or a gym.
 * No device identifiers, health notes, actual weights, journal contents,
 * diagnostics, profile owner or signed athlete ID may be synthesized. */
internal object NativeKotlinBetaFeedbackFormat {
    const val MAX_FIELD_CHARS=2000
    const val MAX_VERSION_CHARS=80

    private fun safe(text:String):String? {
        if(text.length>MAX_FIELD_CHARS || text.any {
            it=='\u0000' || (it.code<32 && it!='\n' && it!='\r' && it!='\t')
        })return null
        return text.trim().replace("\r\n","\n").replace('\r','\n')
    }

    fun canShare(draft:NativeBetaFeedbackDraft):Boolean {
        val fields=listOf(draft.message,draft.workflow,draft.steps,
            draft.expected,draft.actual).map(::safe)
        if(fields.any{it==null})return false
        return if(draft.kind==NativeBetaFeedbackKind.GENERAL)
            !fields[0].isNullOrBlank()
        else !fields[4].isNullOrBlank()
    }

    fun subject(kind:NativeBetaFeedbackKind):String=when(kind) {
        NativeBetaFeedbackKind.GENERAL -> "Pursuit Iron early beta feedback"
        NativeBetaFeedbackKind.PROBLEM -> "Pursuit Iron beta problem report"
    }

    fun format(draft:NativeBetaFeedbackDraft,installedVersion:String):String? {
        if(!canShare(draft))return null
        val version=installedVersion.trim().take(MAX_VERSION_CHARS)
            .filter{it.code in 32..126}
            .ifBlank{"unknown"}
        val body=StringBuilder(subject(draft.kind))
            .append("\nApp version: ").append(version)
        fun part(heading:String,value:String) {
            val cleaned=checkNotNull(safe(value))
            if(cleaned.isNotEmpty())body.append("\n\n")
                .append(heading).append("\n").append(cleaned)
        }
        when(draft.kind) {
            NativeBetaFeedbackKind.GENERAL -> part("My feedback:",draft.message)
            NativeBetaFeedbackKind.PROBLEM -> {
                part("Screen or workflow:",draft.workflow)
                part("Steps to reproduce:",draft.steps)
                part("Expected behavior:",draft.expected)
                part("Actual behavior:",draft.actual)
            }
        }
        return body.toString()
    }
}
