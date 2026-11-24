package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.io.DataInputStream
import java.io.DataOutputStream
import java.math.BigDecimal

/**
 * Historical evidence of explicit confirmation of ONE exact draft edit.
 * Atomically stored with the draft, never a live signoff or workout grant.
 * Future approval must authenticate this receipt against the current source,
 * actual stock, complete history and canonical full-cycle audit.
 */
data class NativeNamedPercentageDraftReceipt(
    val selection:NativeNamedPercentageSelection,
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
    val mayIssueWorkout:Boolean get()=false
    val maySignoff:Boolean get()=false
    val draftRevision:Long get()=stamp.cycleRevision+1
}

internal object NativeNamedPercentageDraftReceiptCodec {
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")
    const val MAX_RECEIPTS=3000
    private fun key(r:NativeNamedPercentageDraftReceipt)=CustomNamedPercentageWeekKey(
        r.blockId,r.weekInBlock,r.selection.weekday,r.selection.slotId)

    fun valid(r:NativeNamedPercentageDraftReceipt,owner:String,cycle:CustomTrainingCycle):Boolean =
        validBatch(listOf(r),owner,cycle)

    /** Every named override must have one receipt from the same exact
     * reviewed base transaction. Structural binding is not approval. */
    fun validBatch(receipts:List<NativeNamedPercentageDraftReceipt>,owner:String,
        cycle:CustomTrainingCycle):Boolean {
        if(receipts.size !in 1..MAX_RECEIPTS ||
            receipts.map(::key).distinct().size!=receipts.size ||
            cycle.blocks.any{b->b.days.any{d->d.slots.any{v->
                v.sets.any{it.percentageIntent!=null}}}})
            return false
        val first=receipts.first()
        if(receipts.any{it.stamp!=first.stamp || it.consentRevision!=first.consentRevision ||
            it.historyRevision!=first.historyRevision ||
            it.machineRosterRevision!=first.machineRosterRevision})
            return false
        val named=cycle.weekOverrides.flatMap{(blockId,edits)->
            edits.filter{e->e.sets.any{it.percentageIntent!=null}}.map{edit->
                CustomNamedPercentageWeekKey(blockId,edit.weekInBlock,edit.weekday,edit.slotId) to edit
            }}
        if(named.size!=receipts.size || named.map{it.first}.toSet()!=receipts.map(::key).toSet())
            return false
        val byKey=named.toMap()
        return receipts.all{validOne(it,owner,cycle,byKey.getValue(key(it)))}
    }

    private fun validOne(r:NativeNamedPercentageDraftReceipt,owner:String,
        cycle:CustomTrainingCycle,edit:CustomWeekSlotOverride):Boolean {
        val s=r.selection
        val p=r.stamp
        if(s.ownerKey!=owner || s.cycleId!=cycle.cycleId || p.cycleId!=cycle.cycleId ||
            p.athleteId!=cycle.athleteId || cycle.revision!=r.draftRevision ||
            p.cycleRevision !in 1..9_007_199_254_740_990L ||
            listOf(p.gymCatalogSourceRevision,r.consentRevision,r.historyRevision)
                .any{it !in 1..9_007_199_254_740_991L} ||
            r.machineRosterRevision !in 0..9_007_199_254_740_991L ||
            !listOf(owner,s.cycleId,p.athleteId,p.gymId,r.blockId,s.slotId,r.exerciseId).all(id::matches) ||
            p.athleteProfileRevision.length !in 1..80 ||
            s.weekday !in 0..6 || s.absoluteWeek !in 1..104 ||
            s.scheme==NamedPercentageScheme.GZCLP ||
            s.referenceKind!=NamedPercentageProtocolDomain.referenceKind(s.scheme) ||
            s.reference<=BigDecimal.ZERO ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(s.reference))
            return false
        val block=cycle.blocks.singleOrNull{it.blockId==r.blockId}?:return false
        val preceding=cycle.blocks.takeWhile{it.blockId!=r.blockId}.sumOf{it.weeks}
        if(r.weekInBlock !in 1..block.weeks || preceding+r.weekInBlock!=s.absoluteWeek)
            return false
        val slot=block.days.singleOrNull{it.weekday==s.weekday}?.slots
            ?.singleOrNull{it.slotId==s.slotId}?:return false
        if(slot.exerciseId!=r.exerciseId || slot.machineInstanceId!=r.machineInstanceId ||
            block.purpose!=CustomBlockPurpose.TRAINING)
            return false
        val work=edit.sets.filter{it.type==DraftSetType.WORKING}
        return work.isNotEmpty() && work.all{set->
            val intent=set.percentageIntent ?:return@all false
            val load=set.load as? DraftSetLoad.Verified ?:return@all false
            intent.scheme==s.scheme && intent.tier==s.tier && intent.dayRole==s.dayRole &&
                intent.referenceLoad.compareTo(s.reference)==0 &&
                intent.weekInBlock==r.weekInBlock && intent.blockWeeks==block.weeks &&
                load.unit==r.unit && load.basis==r.basis && intent.validForSet(set)
        }
    }

    fun write(out:DataOutputStream,r:NativeNamedPercentageDraftReceipt) {
        val s=r.selection
        val p=r.stamp
        out.writeInt(1)
        listOf(s.ownerKey,s.cycleId,s.slotId,s.scheme.name,s.tier.name,s.dayRole.name,
            s.referenceKind.name,s.reference.toPlainString(),p.athleteId,
            p.athleteProfileRevision,p.gymId,r.blockId,r.exerciseId,r.unit.name,r.basis.name)
            .forEach(out::writeUTF)
        out.writeBoolean(r.machineInstanceId!=null)
        r.machineInstanceId?.let(out::writeUTF)
        out.writeInt(s.absoluteWeek);out.writeInt(s.weekday);out.writeInt(r.weekInBlock)
        listOf(p.cycleRevision,p.gymCatalogSourceRevision,r.consentRevision,r.historyRevision,
            r.machineRosterRevision).forEach(out::writeLong)
    }

    fun read(input:DataInputStream):NativeNamedPercentageDraftReceipt {
        require(input.readInt()==1)
        val f=List(15){input.readUTF()}
        val machine=if(input.readBoolean())input.readUTF() else null
        val absolute=input.readInt();val weekday=input.readInt();val week=input.readInt()
        val revisions=List(5){input.readLong()}
        return NativeNamedPercentageDraftReceipt(NativeNamedPercentageSelection(
            f[0],f[1],absolute,weekday,f[2],NamedPercentageScheme.valueOf(f[3]),
            PercentageTier.valueOf(f[4]),PercentageDayRole.valueOf(f[5]),
            PercentageReferenceKind.valueOf(f[6]),BigDecimal(f[7])),
            CustomWorkoutSourceStamp(f[8],f[9],f[1],revisions[0],f[10],revisions[1]),
            f[11],week,f[12],machine,LoadUnit.valueOf(f[13]),LoadBasis.valueOf(f[14]),
            revisions[2],revisions[3],revisions[4])
    }
}
