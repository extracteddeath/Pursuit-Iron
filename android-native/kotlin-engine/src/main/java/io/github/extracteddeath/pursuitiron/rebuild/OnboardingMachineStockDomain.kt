package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Strict, unit-agnostic draft codec for first-time equipment onboarding.
 *
 * Every row names ONE distinct, actual physical implement used by a reviewed
 * exercise. A/B stations for the SAME exercise remain owned by the machine
 * roster; they must never share observed weights through this input.
 * The form keeps its confirmed units outside this codec and clears all
 * provisional weights before changing units. No source loads are inferred.
 */
data class OnboardingPhysicalMachine(
    val equipmentId:String,
    val exactLoads:List<BigDecimal>
)

object OnboardingMachineStockDomain {
    const val MAX_MACHINES=24
    private val idPattern=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")
    private val reserved=setOf("straight-bar","dumbbell-rack","cable-station")
    private val max=BigDecimal("100000")

    /** The same physical increment is a single value even when typed 25 vs 25.0. */
    fun exactLoads(raw:String):List<BigDecimal>?=runCatching {
        val entries=raw.split(',').map{it.trim()}
        require(entries.size in 1..250 && entries.all{it.isNotEmpty()})
        val loads=entries.map{BigDecimal(it)}
        require(loads.all{
            it>=BigDecimal.ZERO && it<=max &&
                it.stripTrailingZeros().scale()<=3
        })
        val canonical=loads.map{
            BigDecimal(it.stripTrailingZeros().toPlainString())
        }
        require(canonical.toSet().size==canonical.size)
        Collections.unmodifiableList(canonical)
    }.getOrNull()

    /** No heuristic split, default weight, external label, or history conversion. */
    fun parse(raw:String):List<OnboardingPhysicalMachine>?=runCatching {
        if(raw.isBlank())return@runCatching emptyList()
        val entries=raw.split(';')
        require(entries.size in 1..MAX_MACHINES)
        val reviewed=entries.map{record->
            require(record.count{it=='='}==1)
            val parts=record.split('=',limit=2)
            val id=parts[0].trim()
            require(idPattern.matches(id) && id !in reserved)
            val loads=exactLoads(parts[1]) ?:error("Invalid physical loads")
            OnboardingPhysicalMachine(id,loads)
        }
        require(reviewed.map{it.equipmentId}.distinct().size==reviewed.size)
        Collections.unmodifiableList(reviewed)
    }.getOrNull()

    private fun encode(machines:List<OnboardingPhysicalMachine>):String=
        machines.joinToString(";"){row->
            row.equipmentId+"="+row.exactLoads.joinToString(","){it.toPlainString()}
        }

    /**
     * Register only an explicitly entered physical machine and its exact
     * selectable loads. Forbid existing straight bars, Smith, cable and DB
     * stock identities: an EXERCISE_OVERRIDE may never overwrite their basis.
     */
    fun add(raw:String,equipmentId:String,loads:String,
        nonMachineEquipmentIds:Set<String> = emptySet()):String? {
        val saved=parse(raw) ?:return null
        if(!idPattern.matches(equipmentId) || equipmentId in reserved ||
            equipmentId in nonMachineEquipmentIds ||
            saved.size>=MAX_MACHINES || saved.any{it.equipmentId==equipmentId})
            return null
        val parsedLoads=exactLoads(loads) ?:return null
        return encode(saved+OnboardingPhysicalMachine(equipmentId,parsedLoads))
    }

    fun remove(raw:String,equipmentId:String):String? {
        val saved=parse(raw) ?:return null
        if(saved.none{it.equipmentId==equipmentId})return null
        return encode(saved.filterNot{it.equipmentId==equipmentId})
    }

    fun verifiedStock(raw:String,unit:LoadUnit,
        nonMachineEquipmentIds:Set<String> = emptySet()):
        Map<String,VerifiedLoadStock>? {
        val machines=parse(raw) ?:return null
        if(machines.any{it.equipmentId in nonMachineEquipmentIds})return null
        return Collections.unmodifiableMap(machines.associate{row->
            row.equipmentId to VerifiedLoadStock.Totals(
                unit,LoadBasis.EXERCISE_OVERRIDE,row.exactLoads)
        })
    }
}
