package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * Read-only, owner-fenced "resume custom workout" inventory.
 *
 * Neither a prepared preview nor an issued workout with an uncommitted Finish
 * may appear as resumable. The complete native mixed history, every custom
 * preparation and issued journal are independently verified before exposing
 * metadata to the Kotlin candidate journey. This cannot issue a session,
 * record a set, or grant access to a journal.
 */
data class NativeCustomResumableWorkout(
    val workoutId:String,
    val cycleId:String,
    val dayLabel:String,
    val absoluteWeek:Int,
    val weekday:Int,
    val journalRevision:Long,
    val unresolvedVisitCount:Int
)

enum class NativeCustomResumeRefusal {
    NOT_AUTHORIZED, HISTORY_NOT_VERIFIED,
    WORKOUT_INVENTORY_UNVERIFIED, STORAGE_UNAVAILABLE
}
sealed interface NativeCustomResumeReview {
    data class Ready(
        val workouts:List<NativeCustomResumableWorkout>,
        val manifestRevision:Long
    ):NativeCustomResumeReview {
        val mayIssueWorkout:Boolean get()=false
        val mayAlterHistory:Boolean get()=false
    }
    data class Rejected(val reason:NativeCustomResumeRefusal):
        NativeCustomResumeReview
}

/** Sorting is by authored week/day, not an invented issuance timestamp. */
internal object NativeCustomResumeSelectionDomain {
    fun pendingVerified(
        verified:List<NativeCustomInventoryFact>
    ):List<NativeCustomInventoryFact> =
        verified.filter{it.issued && !it.completed}.sortedWith(
            compareByDescending<NativeCustomInventoryFact>{it.absoluteWeek}
                .thenByDescending{it.weekday}
                .thenBy{it.cycleId}
                .thenBy{it.workoutId})
}

class NativeKotlinCustomResumeReview(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val inventory:NativeKotlinCustomOutstandingWorkoutInventory,
    private val issued:NativeKotlinCustomWorkoutJournalFileStore
) {
    private val validId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    fun review(ownerKey:String):NativeCustomResumeReview {
        fun no(reason:NativeCustomResumeRefusal)=
            NativeCustomResumeReview.Rejected(reason)
        if(!validId.matches(ownerKey))
            return no(NativeCustomResumeRefusal.NOT_AUTHORIZED)
        return runCatching {fence.withOwnerTransaction(ownerKey) {
            val owner=identity.signedInIdentity()?.takeIf {
                it.ownerKey==ownerKey && validId.matches(it.athleteId)
            }?:return@withOwnerTransaction no(
                NativeCustomResumeRefusal.NOT_AUTHORIZED)
            val timeline=(history.read(ownerKey,owner.athleteId)
                as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
                ?:return@withOwnerTransaction no(
                    NativeCustomResumeRefusal.HISTORY_NOT_VERIFIED)
            if(timeline.athleteId!=owner.athleteId)
                return@withOwnerTransaction no(
                    NativeCustomResumeRefusal.HISTORY_NOT_VERIFIED)
            val completed=timeline.completions.filterIsInstance<
                NativeKotlinMixedHistoryCompletion.Custom>()
                .map{it.manifest.workoutId}.toSet()
            val census=(inventory.inspect(
                ownerKey,owner.athleteId,completed)
                as? NativeCustomInventoryReview.Reviewed)
                ?:return@withOwnerTransaction no(
                    NativeCustomResumeRefusal.WORKOUT_INVENTORY_UNVERIFIED)
            // Re-open all issued journals under the same OS lock. The census
            // already verified every prepared/issued/manifest relationship.
            val journals=(issued.inventory(ownerKey)
                as? NativeIssuedInventoryResult.Found)?.workouts
                ?:return@withOwnerTransaction no(
                    NativeCustomResumeRefusal.WORKOUT_INVENTORY_UNVERIFIED)
            val ids=census.sessions.filter{it.issued}.map{it.workoutId}.toSet()
            val byId=journals.associateBy{it.journal.workoutId}
            if(ids.size!=journals.size || byId.size!=journals.size ||
                byId.keys!=ids)
                return@withOwnerTransaction no(
                    NativeCustomResumeRefusal.WORKOUT_INVENTORY_UNVERIFIED)
            val result=ArrayList<NativeCustomResumableWorkout>()
            for(fact in NativeCustomResumeSelectionDomain.pendingVerified(
                census.sessions)) {
                val journal=byId[fact.workoutId]?.journal
                    ?:return@withOwnerTransaction no(
                        NativeCustomResumeRefusal.WORKOUT_INVENTORY_UNVERIFIED)
                val snapshot=journal.snapshot
                if(journal.finishedByEventId!=null ||
                    snapshot.stamp.athleteId!=owner.athleteId ||
                    snapshot.stamp.cycleId!=fact.cycleId ||
                    snapshot.absoluteWeek!=fact.absoluteWeek ||
                    snapshot.weekday!=fact.weekday ||
                    journal.revision<0L ||
                    journal.unresolvedVisitCount<0)
                    return@withOwnerTransaction no(
                        NativeCustomResumeRefusal.WORKOUT_INVENTORY_UNVERIFIED)
                result+=NativeCustomResumableWorkout(
                    fact.workoutId,fact.cycleId,snapshot.dayLabel,
                    fact.absoluteWeek,fact.weekday,journal.revision,
                    journal.unresolvedVisitCount)
            }
            if(identity.signedInIdentity()!=owner)
                return@withOwnerTransaction no(
                    NativeCustomResumeRefusal.NOT_AUTHORIZED)
            NativeCustomResumeReview.Ready(
                Collections.unmodifiableList(result),
                timeline.manifestRevision)
        }}.getOrElse {
            no(NativeCustomResumeRefusal.STORAGE_UNAVAILABLE)
        }
    }
}
