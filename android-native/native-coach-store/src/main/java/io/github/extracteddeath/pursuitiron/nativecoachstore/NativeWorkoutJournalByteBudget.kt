package io.github.extracteddeath.pursuitiron.nativecoachstore

/**
 * Reserve a conservative encoded-byte budget for explicit workout completion.
 * Separate from the existing reducer event-count bound: a small number of
 * long UTF-8 notes or repeated swaps can exhaust the file before that limit.
 *
 * Both journal formats bound event IDs to 80 ASCII characters and set keys
 * to at most 256 UTF-16 code units (modified UTF-8 needs at most three bytes
 * per unit). A skipped visit has no actual or predecessor, so 1 KiB covers
 * its entire receipt; a Finish uses at most 256 bytes including the repeated
 * event ID in the header. Skips do not introduce new machine, note, swap or
 * decimal fields. The custom staged format is already selected by its
 * snapshot before a follow-up can be skipped.
 *
 * Each unresolved generated set or custom main/current follow-up can be
 * resolved with one explicit skip. Reserving this capacity does NOT record
 * any skips, guarantee room for arbitrary new edits, or alter a workout.
 * Historical files remain readable; only a proposed new write is checked.
 */
internal object NativeWorkoutJournalByteBudget {
    private const val FINISH_BYTES=256
    private const val SKIP_BYTES=1024
    fun requireFits(encodedBytes:Int,maxBytes:Int,remaining:Int?) {
        require(encodedBytes>=0 && maxBytes>0)
        require(remaining==null || remaining in 0..256)
        val reserved=if(remaining==null)0L else
            FINISH_BYTES.toLong()+SKIP_BYTES.toLong()*remaining
        require(encodedBytes.toLong()+reserved<=maxBytes.toLong()) {
            "Journal must retain room for explicit remaining skips and Finish"
        }
    }
}
