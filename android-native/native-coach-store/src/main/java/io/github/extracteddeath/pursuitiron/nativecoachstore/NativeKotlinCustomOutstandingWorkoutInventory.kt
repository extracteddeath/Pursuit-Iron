package io.github.extracteddeath.pursuitiron.nativecoachstore

import java.util.Collections

/**
 * Factual snapshot of prepared and issued custom sessions. All file entries
 * are enumerated and authenticated by their respective native stores, then
 * compared to the full native history manifest under the owner transaction.
 *
 * A prepared workout never counts as issued and an issued workout never counts
 * as finished just because its in-memory reducer says "finished". Only the
 * manifest-backed, fully verified history read establishes completion.
 */
enum class NativeCustomInventoryRefusal {
    NOT_AUTHORIZED, CORRUPT_OR_UNRECOGNIZED_FILE, TOO_MANY_FILES,
    ISSUED_WITHOUT_PREPARATION, ISSUED_CONTENT_MISMATCH,
    DUPLICATE_WORKOUT, UNVERIFIED_COMPLETION, STORAGE_UNAVAILABLE
}
internal sealed interface NativePreparedInventoryResult {
    data class Found(val workouts:List<NativeCustomPreparedWorkout>):
        NativePreparedInventoryResult
    data class Rejected(val reason:NativeCustomInventoryRefusal):
        NativePreparedInventoryResult
}
internal sealed interface NativeIssuedInventoryResult {
    data class Found(val workouts:List<NativeCustomIssuedJournal>):
        NativeIssuedInventoryResult
    data class Rejected(val reason:NativeCustomInventoryRefusal):
        NativeIssuedInventoryResult
}
internal data class NativeCustomInventoryFact(
    val workoutId:String,
    val cycleId:String,
    val absoluteWeek:Int,
    val weekday:Int,
    val issued:Boolean,
    val completed:Boolean
)
internal sealed interface NativeCustomInventoryReview {
    data class Reviewed(val sessions:List<NativeCustomInventoryFact>):
        NativeCustomInventoryReview {
        val outstanding:List<NativeCustomInventoryFact>
            get()=sessions.filter{!it.completed}
    }
    data class Rejected(val reason:NativeCustomInventoryRefusal):
        NativeCustomInventoryReview
}

/**
 * Pure reconciliation: no old consent flag, date, or UI workflow controls
 * completion. Every issue must have a matching preparation and every
 * completed custom manifest ID must match a stored, finished journal.
 */
internal object NativeKotlinCustomInventoryDomain {
    fun reconcile(
        ownerKey:String,
        athleteId:String,
        prepared:List<NativeCustomPreparedWorkout>,
        issued:List<NativeCustomIssuedJournal>,
        completedIds:Set<String>
    ):NativeCustomInventoryReview {
        fun reject(reason:NativeCustomInventoryRefusal)=
            NativeCustomInventoryReview.Rejected(reason)
        val p=prepared.associateBy{it.workoutId}
        val i=issued.associateBy{it.journal.workoutId}
        if(p.size!=prepared.size || i.size!=issued.size)
            return reject(NativeCustomInventoryRefusal.DUPLICATE_WORKOUT)
        if(prepared.any{it.ownerKey!=ownerKey || it.athleteId!=athleteId} ||
            issued.any{it.ownerKey!=ownerKey || it.athleteId!=athleteId})
            return reject(NativeCustomInventoryRefusal.NOT_AUTHORIZED)
        for(record in issued) {
            val preparedSession=p[record.journal.workoutId]
                ?:return reject(NativeCustomInventoryRefusal.ISSUED_WITHOUT_PREPARATION)
            if(record.consentRevision!=preparedSession.consentRevision ||
                record.journal.snapshot!=preparedSession.snapshot ||
                record.journal.preparedConsentFingerprint!=
                    preparedSession.consentFingerprint)
                return reject(NativeCustomInventoryRefusal.ISSUED_CONTENT_MISMATCH)
        }
        if(completedIds.any{it !in i})
            return reject(NativeCustomInventoryRefusal.UNVERIFIED_COMPLETION)
        if(issued.any{
                it.journal.workoutId in completedIds &&
                    it.journal.finishedByEventId==null
            } || issued.any{
                it.journal.workoutId !in completedIds &&
                    it.journal.finishedByEventId!=null
            })
            return reject(NativeCustomInventoryRefusal.UNVERIFIED_COMPLETION)
        val facts=prepared.map{w->
            val recorded=i[w.workoutId]
            NativeCustomInventoryFact(w.workoutId,w.snapshot.stamp.cycleId,
                w.snapshot.absoluteWeek,w.snapshot.weekday,
                recorded!=null,w.workoutId in completedIds)
        }.sortedWith(compareBy(NativeCustomInventoryFact::cycleId,
            NativeCustomInventoryFact::absoluteWeek,
            NativeCustomInventoryFact::weekday,
            NativeCustomInventoryFact::workoutId))
        return NativeCustomInventoryReview.Reviewed(
            Collections.unmodifiableList(facts))
    }
}

/** A full scan is deliberately conservative and never an activation grant. */
class NativeKotlinCustomOutstandingWorkoutInventory(
    private val prepared:NativeKotlinCustomWorkoutPreparedStore,
    private val issued:NativeKotlinCustomWorkoutJournalFileStore
) {
    internal fun inspect(
        ownerKey:String,athleteId:String,
        completedCustomWorkoutIds:Set<String>
    ):NativeCustomInventoryReview {
        val preparedRead=prepared.inventory(ownerKey)
        if(preparedRead !is NativePreparedInventoryResult.Found)
            return NativeCustomInventoryReview.Rejected(
                (preparedRead as NativePreparedInventoryResult.Rejected).reason)
        val issuedRead=issued.inventory(ownerKey)
        if(issuedRead !is NativeIssuedInventoryResult.Found)
            return NativeCustomInventoryReview.Rejected(
                (issuedRead as NativeIssuedInventoryResult.Rejected).reason)
        return NativeKotlinCustomInventoryDomain.reconcile(
            ownerKey,athleteId,preparedRead.workouts,issuedRead.workouts,
            completedCustomWorkoutIds)
    }
}
