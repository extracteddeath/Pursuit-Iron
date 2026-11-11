package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.util.Collections

/**
 * The signed custom slot owns a PHYSICAL station, not a new exercise.
 * Source review still owns overall program dose. This gate is performed
 * against the CURRENT, owner-verified gym roster before a custom workout
 * can be prepared; it never substitutes generic gym stack values for A/B.
 */
sealed interface NativeCustomMachineBindingReview {
    data class Verified(val machinesByExercise:Map<String,String>):
        NativeCustomMachineBindingReview
    data class Rejected(val reason:String):NativeCustomMachineBindingReview
}

object NativeKotlinCustomMachineBindingPolicy {
    fun review(
        ownerKey:String,
        snapshot:CustomWorkoutDaySnapshot,
        catalog:List<CatalogMovement>,
        roster:NativeMachineRosterSnapshot?
    ):NativeCustomMachineBindingReview {
        fun reject(reason:String)=NativeCustomMachineBindingReview.Rejected(reason)
        if(snapshot.slots.none{it.machineInstanceId!=null})
            return NativeCustomMachineBindingReview.Verified(emptyMap())
        if(roster==null || roster.ownerId!=ownerKey || roster.revision<1)
            return reject("MACHINE_ROSTER_UNAVAILABLE")
        val catalogById=catalog.associateBy{it.exerciseId}
        if(catalogById.size!=catalog.size)return reject("CATALOG_NOT_UNIQUE")
        val byExercise=linkedMapOf<String,String>()
        // The issued machine-choice sidecar is keyed by exercise; do not
        // permit one exercise to demand both A and B in the same workout.
        for(group in snapshot.slots.groupBy{it.exerciseId}.values) {
            val ids=group.map{it.machineInstanceId}.distinct()
            if(ids.size>1)return reject("CONFLICTING_MACHINE_CHOICES")
        }
        for(slot in snapshot.slots) {
            val machineId=slot.machineInstanceId ?:continue
            if(!KotlinMachineAlternativesDomain.validInstanceId(machineId))
                return reject("INVALID_MACHINE_ID")
            val basis=catalogById[slot.exerciseId]?.expectedLoadBasis
                ?:return reject("NO_PHYSICAL_MACHINE_BASIS")
            val config=KotlinMachineAlternativesDomain.forGymExercise(
                roster.configurations,snapshot.stamp.gymId,slot.exerciseId)
                ?:return reject("MACHINE_NOT_IN_GYM")
            if(config.machines.none{it.id==machineId})
                return reject("MACHINE_NOT_IN_GYM")
            val stock=KotlinMachineAlternativesDomain.confirmedStack(
                config,machineId,basis)
                ?:return reject("MACHINE_STOCK_NOT_CONFIRMED")
            // The actual A/B station must support EVERY authored weight
            // including warmups and amplifier follow-ups, not only top sets.
            val loads=slot.sets.flatMap{set->
                listOfNotNull(set.prescription.load,
                    set.prescription.amplifier?.followUpLoad)
            }
            if(loads.isEmpty() || loads.any{load->
                load !is DraftSetLoad.Verified ||
                    load.basis!=basis ||
                    (EquipmentLoadDomain.inspect(stock,load.value,load.unit)
                        as? LoadInspection.Confirmed)?.let{
                            it.exact && it.basis==basis
                        } != true
            }) return reject("LOAD_NOT_AVAILABLE_ON_MACHINE")
            byExercise[slot.exerciseId]=machineId
        }
        return NativeCustomMachineBindingReview.Verified(
            Collections.unmodifiableMap(byExercise))
    }
}
