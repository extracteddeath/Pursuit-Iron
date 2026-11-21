package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * One physical-stock selector used by both ordinary custom coaching and
 * basic progression. A roster configured for Machine A/B displaces generic
 * exercise stock; only the chosen physical station's separately verified
 * limits can authorize its next load. Missing physical calibration is null,
 * not a reason to borrow a different machine's inventory.
 */
internal data class NativeCustomSelectedStock(
    val machineInstanceId:String?,
    val stock:VerifiedLoadStock?
)

internal object NativeKotlinCustomSelectedStockDomain {
    fun resolve(
        source:NativeCustomAuthoringSnapshot,
        selected:CustomWorkoutSlotSnapshot,
        roster:NativeMachineRosterSnapshot?
    ):NativeCustomSelectedStock? {
        val gym=source.setup.reviewed?.gym ?:return null
        val movement=source.catalog.singleOrNull {
            it.exerciseId==selected.exerciseId &&
                it.familyId==selected.familyId &&
                it.variantId==selected.variantId
        }?:return null
        val matching=roster?.configurations.orEmpty().filter {
            it.gymId==gym.gymId && it.exerciseId==selected.exerciseId
        }
        if(matching.size>1)return null
        val config=matching.singleOrNull()
        val chosen=selected.machineInstanceId ?:config?.defaultMachineId
        val stock=if(config==null && selected.machineInstanceId!=null)
            null
        else if(config==null)
            movement.equipmentId?.let{gym.confirmedStock[it]}?.takeIf {
                it.basis==movement.expectedLoadBasis && it.unit==gym.unit
            }
        else chosen?.let {id->
            movement.expectedLoadBasis?.let{basis->
                KotlinMachineAlternativesDomain.confirmedStack(
                    config,id,basis)?.takeIf{it.unit==gym.unit}
            }
        }
        return NativeCustomSelectedStock(chosen,stock)
    }
}
