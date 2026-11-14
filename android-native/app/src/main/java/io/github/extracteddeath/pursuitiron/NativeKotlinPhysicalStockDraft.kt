package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal

private val physicalStockId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")

/** Shared physical-stock input used by the gym editor. No load inference. */
internal enum class PhysicalStockEditorKind {
    EXACT_LOADS, VERIFIED_STACK, BARBELL_WITH_PAIRS
}

private fun exactLoadList(text:String):List<BigDecimal>? =
    runCatching {
        val values=text.split(",").map{BigDecimal(it.trim())}
        values.takeIf {it.isNotEmpty() &&
            values.size<=250 && values.all{it>BigDecimal.ZERO}}
    }.getOrNull()

private fun barbellPairs(text:String):Map<BigDecimal,Int>? =
    runCatching {
        val parts=text.split(",").map { raw ->
            val pair=raw.trim().split(Regex("[xX]"))
            require(pair.size==2)
            BigDecimal(pair[0].trim()) to pair[1].trim().toInt()
        }
        require(parts.isNotEmpty() && parts.size<=12 &&
            parts.map{it.first.stripTrailingZeros()}.distinct().size==parts.size)
        require(parts.all { it.first>BigDecimal.ZERO && it.second in 1..24 })
        parts.toMap()
    }.getOrNull()

/** Never adds an invented implement, load, bar weight or stack interval. */
internal fun parseVerifiedStock(
    unit:LoadUnit,kind:PhysicalStockEditorKind,basis:LoadBasis,
    weights:String,minimum:String,increment:String,maximum:String,
    barWeight:String,platePairs:String
):VerifiedLoadStock? = runCatching {
    when(kind) {
        PhysicalStockEditorKind.EXACT_LOADS -> {
            require(basis!=LoadBasis.BARBELL_TOTAL)
            VerifiedLoadStock.Totals(unit,basis,requireNotNull(exactLoadList(weights)))
        }
        PhysicalStockEditorKind.VERIFIED_STACK -> {
            require(basis in listOf(LoadBasis.CABLE_STACK,
                LoadBasis.MACHINE_STACK,LoadBasis.EXERCISE_OVERRIDE))
            VerifiedLoadStock.Stack(unit,basis,BigDecimal(minimum.trim()),
                BigDecimal(increment.trim()),BigDecimal(maximum.trim()))
        }
        PhysicalStockEditorKind.BARBELL_WITH_PAIRS -> {
            require(basis==LoadBasis.BARBELL_TOTAL)
            val mass=BigDecimal(barWeight.trim())
            require(mass>BigDecimal.ZERO && mass<=BigDecimal("100000") &&
                mass.stripTrailingZeros().scale()<=3)
            VerifiedLoadStock.Barbell(unit,mass,requireNotNull(barbellPairs(platePairs)))
        }
    }
}.getOrNull()

/** Reviewed exercise equipment names for user-visible gym stock selection. */
internal data class KotlinGymStockChoice(
    val equipmentId:String,
    val basis:LoadBasis,
    val exerciseNames:List<String>
) {
    val displayName:String get()=when(equipmentId) {
        "straight-bar" -> "Barbell and plates"
        "ez-curl-bar" -> "EZ curl bar and plates"
        "trap-hex-bar" -> "Trap/hex bar and plates"
        "dumbbell-rack" -> "Dumbbells"
        "cable-station" -> "Cable station"
        else -> equipmentId.replace('-', ' ').replace('_',' ')
            .replaceFirstChar{it.uppercase()}
    }
}

/** A name can suggest stock identity, but it can never confirm actual loads. */
internal fun kotlinGymStockChoices(
    catalog:List<CatalogMovement>
):List<KotlinGymStockChoice> =
    catalog.filter{it.equipmentId!=null && it.expectedLoadBasis!=null}
        .groupBy{checkNotNull(it.equipmentId)}
        .mapNotNull{(id,entries)->
            val bases=entries.mapNotNull{it.expectedLoadBasis}.distinct()
            if(bases.size!=1)null else KotlinGymStockChoice(id,bases.single(),
                entries.map{kotlinExerciseLabel(it.exerciseId)}.distinct().sorted())
        }.sortedWith(compareBy<KotlinGymStockChoice>{it.displayName.lowercase(java.util.Locale.ROOT)}
            .thenBy{it.equipmentId})


private fun exactPhysicalLoads(raw:String):List<BigDecimal> {
    val values=raw.split(",").map{BigDecimal(it.trim())}
    require(values.isNotEmpty() && values.size<=250 &&
        values.all{it>=BigDecimal.ZERO} &&
        values.map{it.stripTrailingZeros()}.distinct().size==values.size)
    return values
}
internal fun onboardingStock(
    unit:LoadUnit?,
    db:String,cable:String,barWeight:String,plates:String,
    machineId:String,machineLoads:String,
    savedMachines:String,nonMachineEquipmentIds:Set<String>,
    barEquipmentId:String="straight-bar"
):Map<String,VerifiedLoadStock>? {
    if(unit==null) return null
    return runCatching {
        val stock=linkedMapOf<String,VerifiedLoadStock>()
        if(db.isNotBlank())stock["dumbbell-rack"]=VerifiedLoadStock.Totals(
            unit,LoadBasis.DUMBBELL_PER_HAND,exactPhysicalLoads(db))
        if(cable.isNotBlank()) {
            val parts=cable.split(",").map{BigDecimal(it.trim())}
            require(parts.size==3)
            stock["cable-station"]=VerifiedLoadStock.Stack(
                unit,LoadBasis.CABLE_STACK,parts[0],parts[1],parts[2])
        }
        if(barWeight.isNotBlank() || plates.isNotBlank()) {
            require(barWeight.isNotBlank() && plates.isNotBlank())
            require(StarterExerciseTranche.movements().any{
                it.equipmentId==barEquipmentId && it.expectedLoadBasis==LoadBasis.BARBELL_TOTAL})
            stock[barEquipmentId]=checkNotNull(parseVerifiedStock(unit,
                PhysicalStockEditorKind.BARBELL_WITH_PAIRS,LoadBasis.BARBELL_TOTAL,
                "","","","",barWeight,plates))
        }
        // Every already reviewed physical machine keeps its own implement ID.
        // A machine may never shadow the barbell, cable, dumbbell or Smith
        // equipment identities already loaded by the source catalog.
        val machines=OnboardingMachineStockDomain.verifiedStock(
            savedMachines,unit,nonMachineEquipmentIds)
            ?:error("Invalid saved machine list")
        machines.forEach{(id,verified)->
            require(id !in stock)
            stock[id]=verified
        }
        if(machineId.isNotBlank() || machineLoads.isNotBlank()) {
            require(physicalStockId.matches(machineId) &&
                machineId !in nonMachineEquipmentIds &&
                machineId !in stock && machineLoads.isNotBlank())
            val trial=OnboardingMachineStockDomain.add(
                savedMachines,machineId,machineLoads,nonMachineEquipmentIds)
                ?:error("Machine ID or loads are not independently verifiable")
            val appended=OnboardingMachineStockDomain.verifiedStock(
                trial,unit,nonMachineEquipmentIds)
                ?:error("Unverifiable machine inventory")
            stock[machineId]=checkNotNull(appended[machineId])
        }
        stock.toMap()
    }.getOrNull()
}

