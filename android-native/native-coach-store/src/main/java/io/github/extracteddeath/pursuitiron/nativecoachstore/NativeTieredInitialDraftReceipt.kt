package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigDecimal

/** Explicit first tier reference, not authority for a later tier transition.
 * Persisted with the proposed draft in one revision-CAS transaction. */
data class NativeTieredInitialDraftReceipt(
    val selection:NativeTieredInitialSelection,
    val stamp:CustomWorkoutSourceStamp,
    val blockId:String,
    val weekInBlock:Int,
    val exerciseId:String,
    val machineInstanceId:String?,
    val unit:LoadUnit,
    val basis:LoadBasis,
    val consentRevision:Long,
    val historyRevision:Long,
    val machineRosterRevision:Long
) {
    val draftRevision:Long get()=stamp.cycleRevision+1
    val mayIssueWorkout:Boolean get()=false
    val maySignoff:Boolean get()=false
}

internal object NativeTieredInitialDraftReceiptCodec {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    private const val MAX_REV=9_007_199_254_740_991L

    /** Structural integrity only. Exact recipe, stock, source, history and
     * consent are rederived by the authenticated reviewer before acceptance. */
    const val MAX_RECEIPTS=128

    fun valid(r:NativeTieredInitialDraftReceipt,owner:String,cycle:CustomTrainingCycle):Boolean =
        validBatch(listOf(r),owner,cycle)

    /** All roots must bind one source revision, a stable slot and one
     * workout identity per scheduled day. The full candidate must contain
     * exactly these overrides: no unsigned recipe can hide in the batch. */
    fun validBatch(receipts:List<NativeTieredInitialDraftReceipt>,owner:String,cycle:CustomTrainingCycle):Boolean {
        if(receipts.size !in 1..MAX_RECEIPTS)return false
        val first=receipts.first()
        if(receipts.any{it.stamp!=first.stamp || it.consentRevision!=first.consentRevision ||
            it.historyRevision!=first.historyRevision || it.machineRosterRevision!=first.machineRosterRevision})
            return false
        val keys=receipts.map{CustomNamedPercentageWeekKey(it.blockId,it.weekInBlock,it.selection.weekday,it.selection.slotId)}
        val threads=receipts.map{Triple(it.blockId,it.selection.weekday,it.selection.slotId)}
        if(keys.distinct().size!=keys.size || threads.distinct().size!=threads.size)return false
        val positions=receipts.groupBy{it.selection.absoluteWeek to it.selection.weekday}
        if(positions.values.any{day->day.map{it.selection.nextWorkoutId}.distinct().size!=1} ||
            positions.values.map{it.first().selection.nextWorkoutId}.distinct().size!=positions.size)
            return false
        if(cycle.blocks.any{b->b.days.any{d->d.slots.any{slot->
            slot.sets.any{it.percentageIntent!=null}}}})return false
        val named=cycle.weekOverrides.flatMap{(blockId,edits)->
            edits.filter{e->e.sets.any{it.percentageIntent!=null}}.map{
                CustomNamedPercentageWeekKey(blockId,it.weekInBlock,it.weekday,it.slotId)
            }}
        return named.size==keys.size && named.toSet()==keys.toSet() &&
            receipts.all{validRoot(it,owner,cycle)}
    }

    private fun validRoot(r:NativeTieredInitialDraftReceipt,owner:String,cycle:CustomTrainingCycle):Boolean {
        val s=r.selection
        val p=r.stamp
        if(s.ownerKey!=owner || s.cycleId!=cycle.cycleId || p.cycleId!=cycle.cycleId ||
            p.athleteId!=cycle.athleteId || cycle.revision!=r.draftRevision ||
            p.cycleRevision !in 1 until MAX_REV ||
            listOf(p.gymCatalogSourceRevision,r.consentRevision,r.historyRevision).any{it !in 1..MAX_REV} ||
            r.machineRosterRevision !in 0..MAX_REV ||
            !listOf(owner,s.cycleId,p.athleteId,p.gymId,r.blockId,s.slotId,s.nextWorkoutId,r.exerciseId).all(id::matches) ||
            p.athleteProfileRevision.length !in 1..80 ||
            s.weekday !in 0..6 || s.absoluteWeek !in 1..104 ||
            s.initialLoad<=BigDecimal.ZERO || s.maximumAuthorizedIncrease<=BigDecimal.ZERO ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(s.initialLoad) ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(s.maximumAuthorizedIncrease) ||
            r.basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT ||
            KotlinSetNumberAuthorityDomain.machineTagContradictsFreeBarbell(r.basis,r.machineInstanceId))
            return false
        val block=cycle.blocks.singleOrNull{it.blockId==r.blockId}?:return false
        val preceding=cycle.blocks.takeWhile{it.blockId!=r.blockId}.sumOf{it.weeks}
        if(r.weekInBlock !in 1..block.weeks || preceding+r.weekInBlock!=s.absoluteWeek ||
            block.purpose!=CustomBlockPurpose.TRAINING)return false
        val slot=block.days.singleOrNull{it.weekday==s.weekday}?.slots
            ?.singleOrNull{it.slotId==s.slotId}?:return false
        if(slot.exerciseId!=r.exerciseId || slot.machineInstanceId!=r.machineInstanceId)return false
        val edit=cycle.weekOverrides[r.blockId].orEmpty().singleOrNull{
            it.weekInBlock==r.weekInBlock && it.weekday==s.weekday && it.slotId==s.slotId
        }?:return false
        val work=edit.sets.filter{it.type==DraftSetType.WORKING}
        if(work.isEmpty() || work.size!=slot.sets.count{it.type==DraftSetType.WORKING})return false
        return work.withIndex().all{(index,set)->
            val intent=set.percentageIntent?:return@all false
            val load=set.load as? DraftSetLoad.Verified?:return@all false
            intent.scheme==NamedPercentageScheme.GZCLP && intent.tier==s.tier &&
                intent.dayRole==PercentageDayRole.STANDARD && intent.tierStage==0 &&
                !intent.recovery && intent.repOut==(index==work.lastIndex) &&
                intent.referenceLoad.compareTo(s.initialLoad)==0 &&
                load.value.compareTo(s.initialLoad)==0 && load.unit==r.unit && load.basis==r.basis &&
                intent.weekInBlock==r.weekInBlock && intent.blockWeeks==block.weeks &&
                intent.validForSet(set)
        }
    }

    fun write(out:DataOutputStream,r:NativeTieredInitialDraftReceipt) {
        val s=r.selection;val p=r.stamp
        out.writeInt(1)
        listOf(s.ownerKey,s.cycleId,s.slotId,s.nextWorkoutId,s.tier.name,
            s.initialLoad.toPlainString(),s.maximumAuthorizedIncrease.toPlainString(),
            p.athleteId,p.athleteProfileRevision,p.gymId,r.blockId,r.exerciseId,
            r.unit.name,r.basis.name).forEach(out::writeUTF)
        out.writeBoolean(r.machineInstanceId!=null)
        r.machineInstanceId?.let(out::writeUTF)
        out.writeInt(s.absoluteWeek);out.writeInt(s.weekday);out.writeInt(r.weekInBlock)
        listOf(p.cycleRevision,p.gymCatalogSourceRevision,r.consentRevision,r.historyRevision,
            r.machineRosterRevision).forEach(out::writeLong)
    }

    fun read(input:DataInputStream):NativeTieredInitialDraftReceipt {
        require(input.readInt()==1)
        val f=List(14){input.readUTF()}
        val machine=if(input.readBoolean())input.readUTF() else null
        val absolute=input.readInt();val weekday=input.readInt();val week=input.readInt()
        val revisions=List(5){input.readLong()}
        return NativeTieredInitialDraftReceipt(NativeTieredInitialSelection(
            f[0],f[1],absolute,weekday,f[2],f[3],PercentageTier.valueOf(f[4]),
            BigDecimal(f[5]),BigDecimal(f[6])),
            CustomWorkoutSourceStamp(f[7],f[8],f[1],revisions[0],f[9],revisions[1]),
            f[10],week,f[11],machine,LoadUnit.valueOf(f[12]),LoadBasis.valueOf(f[13]),
            revisions[2],revisions[3],revisions[4])
    }
}
