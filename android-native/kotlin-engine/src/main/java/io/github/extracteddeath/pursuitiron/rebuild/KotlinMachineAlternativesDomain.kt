package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Physical station identity is separate from logical exercise identity.
 * Muscle-dose, cycle slot and exercise selection remain attached to exerciseId.
 * Load trend/PR/Last/progression are gym + machine + approved day scoped.
 */
data class KotlinPhysicalMachine(
    val id:String,
    val label:String,
    val active:Boolean=true,
    val unit:LoadUnit?=null,
    val increment:BigDecimal?=null,
    val description:String?=null,
    /** Explicitly confirmed physical stack limits, NOT inferred from gym stock. */
    val minimum:BigDecimal?=null,
    val maximum:BigDecimal?=null
)
data class KotlinGymMachineAlternatives(
    val gymId:String,
    val exerciseId:String,
    val defaultMachineId:String?,
    val machines:List<KotlinPhysicalMachine>
)
data class KotlinResolvedMachine(
    val id:String,
    val label:String,
    val active:Boolean,
    val legacy:Boolean=false,
    val missing:Boolean=false
)
/** A single completed set is an observation, not a converted cross-machine estimate. */
data class KotlinMachineSetFact(
    val gymId:String,
    val exerciseId:String,
    val machineInstanceId:String?,
    val dayIndex:Int,
    val workoutId:String,
    val setIndex:Int,
    val load:BigDecimal,
    val reps:Int,
    val unit:LoadUnit
)
object KotlinMachineAlternativesDomain {
    const val LEGACY_MACHINE_ID="__legacy_machine__"
    private val id=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    /** Fail closed rather than using another gym's default for the same lift. */
    fun forGymExercise(
        configurations:List<KotlinGymMachineAlternatives>,gymId:String,exerciseId:String
    ):KotlinGymMachineAlternatives? = configurations.singleOrNull {
        it.gymId==gymId && it.exerciseId==exerciseId
    }?.takeIf { valid(it) }

    fun normalizeMachineId(value:String?)=value ?: LEGACY_MACHINE_ID
    fun validInstanceId(value:String?)=value!=null && id.matches(value)

    /**
     * Existing inactive gyms must survive an edit to the currently reviewed gym.
     * A UI-provided roster cannot rename, delete or smuggle in records for
     * another gym, even when the revision is current.
     */
    fun preservesOtherGyms(
        previous:List<KotlinGymMachineAlternatives>,
        proposed:List<KotlinGymMachineAlternatives>,
        editableGymId:String
    ):Boolean {
        if(!id.matches(editableGymId) ||
            previous.any { !valid(it) } || proposed.any { !valid(it) } ||
            previous.map { it.gymId to it.exerciseId }.distinct().size!=previous.size ||
            proposed.map { it.gymId to it.exerciseId }.distinct().size!=proposed.size)
            return false
        fun nonEditable(configs:List<KotlinGymMachineAlternatives>) =
            configs.filter { it.gymId!=editableGymId }
                .associateBy { it.gymId to it.exerciseId }
        return nonEditable(previous)==nonEditable(proposed)
    }

    fun valid(config:KotlinGymMachineAlternatives):Boolean =
        id.matches(config.gymId) && id.matches(config.exerciseId) &&
        config.machines.size<=32 &&
        config.machines.map {it.id}.distinct().size==config.machines.size &&
        config.machines.all {machine ->
            id.matches(machine.id) && machine.label.trim().length in 1..80 &&
                (machine.description==null || machine.description.length<=256) &&
                (machine.increment==null ||
                    (machine.increment>BigDecimal.ZERO &&
                        machine.increment.stripTrailingZeros().scale()<=3 &&
                        machine.increment<=BigDecimal("100000"))) &&
                (if(machine.minimum==null && machine.maximum==null)true
                 else machine.minimum!=null && machine.maximum!=null &&
                    machine.increment!=null && machine.unit!=null &&
                    machine.minimum>=BigDecimal.ZERO &&
                    machine.maximum>=machine.minimum &&
                    machine.maximum<=BigDecimal("100000") &&
                    machine.minimum.stripTrailingZeros().scale()<=3 &&
                    machine.maximum.stripTrailingZeros().scale()<=3 &&
                    (machine.maximum-machine.minimum) <=
                        machine.increment.multiply(BigDecimal("99999")) &&
                    // An advertised maximum must be an attainable stack value.
                    // Otherwise "max 215, step 10 from 20" silently stops at 210.
                    (machine.maximum-machine.minimum).remainder(
                        machine.increment).compareTo(BigDecimal.ZERO)==0)
        } && (config.defaultMachineId==null || config.machines.any{
            it.id==config.defaultMachineId && it.active
        })

    /**
     * Never borrow the exercise-level stock for a physical A/B machine.
     * Full minimum/increment/maximum must be provided and explicitly
     * confirmed for THIS machine. Otherwise rep advice may continue but an
     * automatic weight step is unavailable.
     */
    fun confirmedStack(
        config:KotlinGymMachineAlternatives,machineId:String,
        basis:LoadBasis
    ):VerifiedLoadStock.Stack? {
        if(!valid(config) || basis !in setOf(
            LoadBasis.MACHINE_STACK,LoadBasis.CABLE_STACK,LoadBasis.EXERCISE_OVERRIDE))
            return null
        val machine=config.machines.firstOrNull{it.id==machineId && it.active}
            ?:return null
        return if(machine.unit!=null && machine.minimum!=null &&
            machine.increment!=null && machine.maximum!=null)
                VerifiedLoadStock.Stack(machine.unit,basis,machine.minimum,
                    machine.increment,machine.maximum)
            else null
    }

    fun add(
        config:KotlinGymMachineAlternatives, machine:KotlinPhysicalMachine,
        makeDefault:Boolean=false
    ):KotlinGymMachineAlternatives {
        require(valid(config) && config.machines.none {it.id==machine.id})
        val next=config.copy(
            defaultMachineId=if(makeDefault || config.defaultMachineId==null)machine.id
                else config.defaultMachineId,
            machines=Collections.unmodifiableList(config.machines+machine))
        require(valid(next))
        return next
    }

    fun setDefault(
        config:KotlinGymMachineAlternatives, machineId:String
    ):KotlinGymMachineAlternatives {
        require(valid(config) && config.machines.any {it.id==machineId && it.active})
        return config.copy(defaultMachineId=machineId)
    }

    fun edit(
        config:KotlinGymMachineAlternatives, machine:KotlinPhysicalMachine
    ):KotlinGymMachineAlternatives {
        require(valid(config) && config.machines.any {it.id==machine.id})
        val next=config.copy(machines=Collections.unmodifiableList(
            config.machines.map {if(it.id==machine.id)machine else it}))
        require(valid(next))
        return next
    }

    /** A saved selection wins over a changed default, including deactivation. */
    fun resolve(
        config:KotlinGymMachineAlternatives, storedMachineId:String?=null,
        restoring:Boolean=false
    ):KotlinResolvedMachine {
        require(valid(config))
        if(storedMachineId==LEGACY_MACHINE_ID)
            return KotlinResolvedMachine(LEGACY_MACHINE_ID,"Legacy machine",false,legacy=true)
        if(storedMachineId!=null) {
            config.machines.firstOrNull {it.id==storedMachineId}?.let {
                return KotlinResolvedMachine(it.id,it.label,it.active)
            }
            require(restoring)
            return KotlinResolvedMachine(storedMachineId,"Former machine ($storedMachineId)",
                false,missing=true)
        }
        val preferred=config.machines.firstOrNull{
            it.id==config.defaultMachineId && it.active
        } ?:config.machines.firstOrNull {it.active}
        return preferred?.let {KotlinResolvedMachine(it.id,it.label,it.active)}
            ?:KotlinResolvedMachine(LEGACY_MACHINE_ID,"Legacy machine",true,legacy=true)
    }

    fun comparable(a:KotlinMachineSetFact,b:KotlinMachineSetFact):Boolean =
        a.gymId==b.gymId && a.exerciseId==b.exerciseId &&
            normalizeMachineId(a.machineInstanceId)==normalizeMachineId(b.machineInstanceId) &&
            a.dayIndex==b.dayIndex && a.unit==b.unit

    fun scopedHistory(
        facts:List<KotlinMachineSetFact>,gymId:String,exerciseId:String,
        machineInstanceId:String,dayIndex:Int?=null
    ):List<KotlinMachineSetFact> {
        require(id.matches(gymId) && id.matches(exerciseId) &&
            (machineInstanceId==LEGACY_MACHINE_ID || validInstanceId(machineInstanceId)) &&
            (dayIndex==null || dayIndex in 0..6))
        return Collections.unmodifiableList(facts.filter {
            it.gymId==gymId && it.exerciseId==exerciseId &&
                normalizeMachineId(it.machineInstanceId)==machineInstanceId &&
                (dayIndex==null || it.dayIndex==dayIndex)
        })
    }
}
