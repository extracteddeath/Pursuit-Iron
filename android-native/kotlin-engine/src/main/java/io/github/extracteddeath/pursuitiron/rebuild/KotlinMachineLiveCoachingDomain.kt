package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal

/**
 * Read-only proof that the next-set coach may use the exact selected machine's
 * physically confirmed resistance range. The caller MUST first authenticate
 * the athlete, immutable issued gym and the revisioned workout choice file.
 *
 * The selection itself is NOT performance evidence. A newly selected B does
 * not turn completed A observations into B observations, including corrected
 * visits and work logged before an exercise swap.
 *
 * Null intentionally means: the coach may discuss recovery, repetitions and
 * technique, but it must not propose a weight step from unverified stock.
 */
object KotlinMachineLiveCoachingDomain {
    private fun exactPhysicalLoad(
        stock:VerifiedLoadStock.Stack,load:BigDecimal?,unit:LoadUnit?
    ):Boolean {
        if(load==null || unit!=stock.unit)return false
        val proof=EquipmentLoadDomain.inspect(stock,load,unit)
        return proof is LoadInspection.Confirmed && proof.exact
    }

    private fun stock(
        config:KotlinGymMachineAlternatives?,
        selectedMachineId:String?,
        issued:DraftSetLoad
    ):VerifiedLoadStock.Stack? {
        if(!KotlinMachineAlternativesDomain.validInstanceId(selectedMachineId))
            return null
        val load=issued as? DraftSetLoad.Verified ?:return null
        val verified=config?.let {
            KotlinMachineAlternativesDomain.confirmedStack(it,
                checkNotNull(selectedMachineId),load.basis)
        } ?:return null
        return verified.takeIf {
            it.unit==load.unit && it.minimum>BigDecimal.ZERO &&
                exactPhysicalLoad(it,load.value,load.unit)
        }
    }

    fun confirmedForNext(
        journal:KotlinWorkoutJournalState,
        nextSetKey:String,
        selectedMachineId:String?,
        config:KotlinGymMachineAlternatives?
    ):VerifiedLoadStock.Stack? {
        if(journal.finishedByEventId!=null || config==null ||
            journal.originalSnapshot.source.gymId!=config.gymId ||
            journal.snapshot.source.gymId!=config.gymId)
            return null
        val next=journal.snapshot.targets.singleOrNull {
            it.setKey==nextSetKey && it.type==DraftSetType.WORKING &&
                it.exerciseId==config.exerciseId
        } ?:return null
        if(nextSetKey in journal.effectiveSets)return null
        val candidates=journal.snapshot.targets.filter {
            it.exerciseId==next.exerciseId && it.type==DraftSetType.WORKING
        }.sortedBy {it.ordinal}
        if(candidates.map{it.ordinal}.distinct().size!=candidates.size)return null
        val index=candidates.indexOfFirst{it.setKey==nextSetKey}
        if(index<1)return null
        val machineStock=stock(config,selectedMachineId,next.load)?:return null
        val allEarlierOnStation=candidates.take(index).all {target->
            val issued=target.load as? DraftSetLoad.Verified
            val recorded=journal.effectiveSets[target.setKey]?.operation
                as? KotlinJournalOperation.RecordSet
            val actual=recorded?.performed
            recorded?.outcome==KotlinJournalSetOutcome.COMPLETED &&
                issued?.unit==machineStock.unit && issued.basis==machineStock.basis &&
                actual?.machineInstanceId==selectedMachineId &&
                exactPhysicalLoad(machineStock,actual?.load,actual?.unit)
        }
        return machineStock.takeIf {allEarlierOnStation}
    }

    fun confirmedForNext(
        journal:CustomWorkoutJournalDraft,
        nextSetKey:String,
        selectedMachineId:String?,
        config:KotlinGymMachineAlternatives?
    ):VerifiedLoadStock.Stack? {
        if(journal.finishedByEventId!=null || config==null ||
            journal.originalSnapshot.stamp.gymId!=config.gymId ||
            journal.snapshot.stamp.gymId!=config.gymId)
            return null
        // The custom day can repeat an exercise in distinct slots. Only the
        // currently selected stable slot may supply prior fatigue evidence.
        val slot=journal.snapshot.slots.singleOrNull {
            it.sets.any {set->set.setKey==nextSetKey}
        } ?:return null
        if(slot.exerciseId!=config.exerciseId)return null
        val next=slot.sets.singleOrNull {
            it.setKey==nextSetKey &&
                it.prescription.type==DraftSetType.WORKING
        } ?:return null
        if(CustomWorkoutVisitKey(nextSetKey,CustomWorkoutVisitPart.MAIN)
            in journal.effectiveVisits)return null
        val working=slot.sets.filter {
            it.prescription.type==DraftSetType.WORKING
        }
        val index=working.indexOfFirst{it.setKey==nextSetKey}
        if(index<1)return null
        val machineStock=stock(config,selectedMachineId,next.prescription.load)
            ?:return null
        val allEarlierOnStation=working.take(index).all {set->
            val issued=set.prescription.load as? DraftSetLoad.Verified
            val recorded=journal.effectiveVisits[CustomWorkoutVisitKey(
                set.setKey,CustomWorkoutVisitPart.MAIN)]?.operation
                as? CustomWorkoutJournalOperation.Record
            val actual=recorded?.performed
            recorded?.outcome==KotlinJournalSetOutcome.COMPLETED &&
                issued?.unit==machineStock.unit && issued.basis==machineStock.basis &&
                actual?.machineInstanceId==selectedMachineId &&
                exactPhysicalLoad(machineStock,actual?.load,actual?.unit)
        }
        return machineStock.takeIf {allEarlierOnStation}
    }
}
