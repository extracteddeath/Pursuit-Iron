package io.github.extracteddeath.pursuitiron.nativecoachstore

/**
 * A recovery schedule edit changes the cycle revision. Even an append-only
 * edit must not orphan an unfinished preparation or issued workout. Any
 * future mid-cycle insertion must additionally leave every observed week
 * before its first changed calendar position.
 */
internal object NativeRecoveryScheduleInventoryPolicy {
    fun permits(sessions:List<NativeCustomInventoryFact>,cycleId:String,
        firstChangedWeek:Int):Boolean =
        firstChangedWeek in 1..104 && sessions.none{
            it.cycleId==cycleId &&
                (!it.completed || it.absoluteWeek>=firstChangedWeek)
        }

    /** Existing date/activation approvals bind exact draft bytes. Until an
     * explicit reapproval transaction exists, never invalidate these records. */
    fun calendarPermits(cycleId:String,activationBarrier:Boolean,
        approvedCycles:Set<String>,calendarCycles:Set<String>):Boolean =
        !activationBarrier && cycleId !in approvedCycles && cycleId !in calendarCycles
}

sealed interface NativeRecoveryScheduleReview {
    data class Ready(val historyRevision:Long):NativeRecoveryScheduleReview
    data class Rejected(val explanation:String):NativeRecoveryScheduleReview
}

/** Read only. The caller MUST retain the same owner transaction from this
 * review through the final draft CAS. The history revision belongs in the
 * explicit preview receipt, so a correction or Finish forces another review.
 */
class NativeKotlinRecoveryScheduleFence(
    private val fence:NativeKotlinApprovalRevisionFence,
    private val identity:NativeKotlinCurrentIdentity,
    private val history:NativeKotlinMixedHistoryReadCoordinator,
    private val inventory:NativeKotlinCustomOutstandingWorkoutInventory,
    private val handoffs:NativeKotlinFutureActivationHandoffFileStore,
    private val commits:NativeKotlinFutureActivationCommitTransaction,
    private val approvals:NativeKotlinFutureActivationApprovalFileStore,
    private val calendars:NativeKotlinActivatedBlockCalendarFileStore
) {
    fun review(ownerKey:String,cycleId:String,firstChangedWeek:Int):
        NativeRecoveryScheduleReview {
        fun deny(reason:String)=NativeRecoveryScheduleReview.Rejected(reason)
        if(firstChangedWeek !in 1..104)
            return deny("The recovery week is outside the supported calendar.")
        return runCatching {
            fence.withOwnerTransaction(ownerKey) {
                val owner=identity.signedInIdentity()?.takeIf{it.ownerKey==ownerKey}
                    ?:return@withOwnerTransaction deny("The active athlete changed.")
                val timeline=(history.read(ownerKey,owner.athleteId)
                    as? NativeKotlinMixedHistoryReadResult.Review)?.timeline
                    ?:return@withOwnerTransaction deny("Verify native workout history before changing the schedule.")
                if(timeline.athleteId!=owner.athleteId)
                    return@withOwnerTransaction deny("Workout history belongs to another athlete.")
                val completed=timeline.completions
                    .filterIsInstance<NativeKotlinMixedHistoryCompletion.Custom>()
                    .map{it.manifest.workoutId}.toSet()
                val census=(inventory.inspect(ownerKey,owner.athleteId,completed)
                    as? NativeCustomInventoryReview.Reviewed)
                    ?:return@withOwnerTransaction deny("Prepared and issued workouts could not be verified.")
                if(!NativeRecoveryScheduleInventoryPolicy.permits(
                        census.sessions,cycleId,firstChangedWeek))
                    return@withOwnerTransaction deny(
                        "Resolve unfinished workouts first. A recovery edit cannot shift a recorded training week.")
                val handoff=handoffs.inventoryOwner(ownerKey)
                val commit=commits.inventoryOwner(ownerKey)
                    as? NativeFutureCommitInventory.Verified
                    ?:return@withOwnerTransaction deny("Activation records could not be verified.")
                val approved=approvals.inventoryOwner(ownerKey)
                    as? NativeFutureApprovalInventory.Verified
                    ?:return@withOwnerTransaction deny("Future block approvals could not be verified.")
                val dates=calendars.inventoryOwner(ownerKey)
                    as? NativeActivatedBlockCalendarInventory.Verified
                    ?:return@withOwnerTransaction deny("Calendar approvals could not be verified.")
                if(!NativeRecoveryScheduleInventoryPolicy.calendarPermits(
                        cycleId,handoff!=NativeFutureHandoffReview.Missing || commit.state!=null,
                        approved.records.filterNot{it.revoked}.map{it.cycleId}.toSet(),
                        dates.records.map{it.calendar.cycleId}.toSet()))
                    return@withOwnerTransaction deny(
                        "An existing activation or calendar approval must be resolved before changing this schedule.")
                if(identity.signedInIdentity()!=owner)
                    return@withOwnerTransaction deny("The active athlete changed.")
                NativeRecoveryScheduleReview.Ready(timeline.manifestRevision)
            }
        }.getOrElse{deny("The recovery schedule could not be verified.")}
    }
}
