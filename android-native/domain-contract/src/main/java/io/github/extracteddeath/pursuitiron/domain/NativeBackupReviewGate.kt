package io.github.extracteddeath.pursuitiron.domain

/**
 * Process-only read-only backup review ownership.
 *
 * A preview/reselection must never outlive another selection, an owner
 * revision, a screen dismissal or process shutdown. In particular, a second
 * selection consumes its first ticket BEFORE opening the provider: a
 * failed/cancelled second read cannot be retried against an old review.
 *
 * Only source fingerprints and a redacted comparison are retained here.
 * There is intentionally no restore approval, journal, writer or URI.
 */
class NativeBackupReviewGate {
    private val lock = Any()
    private var epoch = 0L
    private var closed = false
    private var ticket: NativeBackupReviewTicket? = null
    private var pending: Reselection? = null

    class Reselection internal constructor(
        internal val epoch: Long,
        internal val ticket: NativeBackupReviewTicket
    )

    fun beginPreview(): Long = synchronized(lock) {
        check(!closed)
        epoch++
        ticket = null
        pending = null
        epoch
    }

    fun registerPreview(at: Long, review: NativeBackupReviewTicket): Boolean =
        synchronized(lock) {
            if (closed || at != epoch || pending != null || ticket != null) false
            else {
                ticket = review
                true
            }
        }

    fun claimReselection(): Reselection? = synchronized(lock) {
        if (closed) return@synchronized null
        val selected = ticket ?: return@synchronized null
        epoch++
        ticket = null
        Reselection(epoch, selected).also { pending = it }
    }

    fun currentRevision(): Long = synchronized(lock) {
        check(!closed)
        epoch
    }

    fun isCurrent(at: Long): Boolean = synchronized(lock) {
        !closed && at == epoch
    }

    // A failed asynchronous read must not revoke a *newer* review.
    fun abandonReselection(claimed: Reselection) = synchronized(lock) {
        if (pending === claimed) {
            pending = null
            epoch++
        }
    }

    fun completeReselection(
        claimed: Reselection,
        selected: UnverifiedBackupFingerprint,
        fresh: NativeBackupMergePreview
    ): Boolean = synchronized(lock) {
        val valid = !closed && pending === claimed &&
            claimed.epoch == epoch &&
            claimed.ticket.matchesReselection(selected, fresh)
        if (pending === claimed) {
            pending = null
            epoch++
        }
        valid
    }

    fun discard() = synchronized(lock) {
        epoch++
        ticket = null
        pending = null
    }

    fun close() = synchronized(lock) {
        closed = true
        epoch++
        ticket = null
        pending = null
    }
}
