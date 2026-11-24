package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/** A proposed recovery week must be preparable on the exact owner-reviewed
 * gym roster even when the athlete elects to keep every retained weight.
 * Pure read-only preflight; never prepares or issues the workouts.
 */
object NativeKotlinRecoveryWorkoutPreflight {
    fun review(ownerKey:String,cycle:CustomTrainingCycle,
        setup:AthleteSetupReview,catalog:List<CatalogMovement>,
        sourceRevision:Long,absoluteWeek:Int,blockId:String,
        roster:NativeMachineRosterSnapshot?):String? {
        val block=cycle.blocks.singleOrNull{it.blockId==blockId}
            ?:return "The proposed recovery block is unavailable."
        if(block.purpose!=CustomBlockPurpose.RECOVERY || block.weeks!=1 ||
            block.days.isEmpty())
            return "The proposed recovery calendar is invalid."
        for(day in block.days) {
            val snapshot=(CustomWorkoutSnapshotDomain.preview(
                cycle,setup,catalog,sourceRevision,absoluteWeek,day.weekday)
                as? CustomWorkoutSnapshotResult.Preview)?.snapshot
                ?:return "Recovery day ${day.label} could not be prepared from the reviewed source."
            if(snapshot.blockId!=blockId || snapshot.absoluteWeek!=absoluteWeek ||
                snapshot.blockPurpose!=CustomBlockPurpose.RECOVERY)
                return "The recovery day does not match the reviewed week."
            val binding=NativeKotlinCustomMachineBindingPolicy.review(
                ownerKey,snapshot,catalog,roster)
            if(binding is NativeCustomMachineBindingReview.Rejected)
                return "Review the bound machine and retained weights for ${day.label}: ${binding.reason}."
        }
        return null
    }
}
