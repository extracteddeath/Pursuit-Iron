package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import java.util.Collections

/**
 * Read-only presentation of exactly ONE athlete-accepted future-week load.
 *
 * The caller MUST obtain Current from the durable consent store's
 * readCurrent() (which re-runs the owner-fenced full-cycle/history/permission
 * audit). This function deliberately cannot persist, prepare or issue a workout.
 * A future issuer integration needs a separate, versioned approval-provenance
 * contract rather than reusing the existing per-workout load fingerprint.
 */
internal sealed interface NativeFutureWeekLoadProjection {
    data class Ready(
        val original:CustomWorkoutDaySnapshot,
        val preview:CustomWorkoutDaySnapshot,
        val fingerprint:String,
        val changedSlotId:String
    ):NativeFutureWeekLoadProjection {
        val mayIssueWorkout:Boolean get()=false
        val mayWriteTrainingCycle:Boolean get()=false
        val mayChangeHistory:Boolean get()=false
    }
    data class Rejected(val reason:NativeFutureWeekLoadProjectionRefusal):
        NativeFutureWeekLoadProjection
}
internal enum class NativeFutureWeekLoadProjectionRefusal {
    CONSENT_NOT_CURRENT, WRONG_SESSION, SLOT_MISSING,
    PRESCRIPTION_CHANGED, UNVERIFIED_EQUIPMENT
}

internal object NativeFutureWeekLoadSessionProjection {
    private fun same(a:BigDecimal,b:BigDecimal)=a.compareTo(b)==0
    private fun normalized(v:BigDecimal)=
        BigDecimal(v.stripTrailingZeros().toPlainString())

    fun preview(
        source:CustomWorkoutDaySnapshot,
        consent:NativeFutureWeekConsentRead,
        physical:VerifiedLoadStock?
    ):NativeFutureWeekLoadProjection {
        fun no(r:NativeFutureWeekLoadProjectionRefusal)=
            NativeFutureWeekLoadProjection.Rejected(r)
        val item=(consent as? NativeFutureWeekConsentRead.Current)?.receipt
            ?:return no(NativeFutureWeekLoadProjectionRefusal.CONSENT_NOT_CURRENT)
        val stamp=source.stamp
        if(stamp.athleteId!=item.athleteId ||
            stamp.cycleId!=item.cycleId ||
            stamp.cycleRevision!=item.draftRevision ||
            stamp.gymCatalogSourceRevision!=item.sourceRevision ||
            source.blockId!=item.blockId ||
            source.absoluteWeek!=item.absoluteWeek ||
            source.weekday!=item.weekday ||
            source.blockPurpose!=CustomBlockPurpose.TRAINING ||
            source.weekInBlock<1)
            return no(NativeFutureWeekLoadProjectionRefusal.WRONG_SESSION)
        val slot=source.slots.singleOrNull{it.slotId==item.slotId}
            ?:return no(NativeFutureWeekLoadProjectionRefusal.SLOT_MISSING)
        // The earning path was deliberately limited to uniform straight
        // working sets. Warmups, drop sets, individual backoffs and partial
        // set changes need a different full-review policy.
        val sets=slot.sets
        val loads=sets.mapNotNull{it.prescription.load as? DraftSetLoad.Verified}
        if(sets.isEmpty()||sets.size>32||loads.size!=sets.size||
            sets.map{it.setKey}.distinct().size!=sets.size||
            sets.any{it.slotId!=item.slotId||
                it.prescription.type!=DraftSetType.WORKING||
                it.prescription.targetRir==null||
                it.prescription.amplifier!=null||
                it.prescription.reps!=sets.first().prescription.reps||
                it.prescription.targetRir!=sets.first().prescription.targetRir}||
            loads.any{it.unit!=loads.first().unit||
                it.basis!=loads.first().basis||
                !same(it.value,item.previousLoad)})
            return no(NativeFutureWeekLoadProjectionRefusal.PRESCRIPTION_CHANGED)
        if(item.previousLoad<=BigDecimal.ZERO ||
            item.proposedLoad<=item.previousLoad ||
            item.maximumAuthorizedIncrease<=BigDecimal.ZERO ||
            item.proposedLoad.subtract(item.previousLoad)>
                item.maximumAuthorizedIncrease ||
            physical==null||
            physical.unit!=loads.first().unit||
            physical.basis!=loads.first().basis)
            return no(NativeFutureWeekLoadProjectionRefusal.UNVERIFIED_EQUIPMENT)
        val inspection=EquipmentLoadDomain.inspect(
            physical,item.previousLoad,physical.unit)
        val replacement=EquipmentLoadDomain.inspect(
            physical,item.proposedLoad,physical.unit)
        if(inspection !is LoadInspection.Confirmed||
            !inspection.exact||inspection.nextAbove==null||
            !same(inspection.nextAbove,item.proposedLoad)||
            replacement !is LoadInspection.Confirmed||
            !replacement.exact)
            return no(NativeFutureWeekLoadProjectionRefusal.UNVERIFIED_EQUIPMENT)
        val amendedSlot=slot.copy(sets=Collections.unmodifiableList(
            sets.map{set->
                val old=set.prescription.load as DraftSetLoad.Verified
                set.copy(prescription=set.prescription.copy(load=old.copy(
                    value=normalized(item.proposedLoad))))
            }))
        val amended=source.copy(slots=Collections.unmodifiableList(
            source.slots.map{if(it.slotId==slot.slotId)amendedSlot else it}))
        return NativeFutureWeekLoadProjection.Ready(
            source,amended,item.reviewFingerprint,slot.slotId)
    }
}
