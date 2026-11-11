package io.github.extracteddeath.pursuitiron

import android.content.Context
import java.security.MessageDigest
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import io.github.extracteddeath.pursuitiron.rebuild.KotlinGymMachineAlternatives
import io.github.extracteddeath.pursuitiron.rebuild.KotlinMachineAlternativesDomain
import io.github.extracteddeath.pursuitiron.rebuild.WorkoutLastReferenceResult

/** Stable compact saveable per-exercise choice; IDs cannot contain separators. */
internal fun workoutMachineChoice(encoded:String,exerciseId:String,defaultId:String?):String? =
    encoded.split(";").firstOrNull {it.substringBefore("=")==exerciseId &&
        it.contains("=")}?.substringAfter("=")?.takeIf{it.isNotBlank()}?:defaultId

internal fun updateWorkoutMachineChoice(
    encoded:String,exerciseId:String,machineId:String
):String {
    val old=encoded.split(";").filter{
        it.contains("=")&&it.substringBefore("=")!=exerciseId
    }
    return (old+"$exerciseId=$machineId").joinToString(";")
}

/**
 * This is a display preference, never an authenticated workout set or
 * progression source. Scope it to the issued athlete, gym and workout.
 * Reload only choices still present and active in that exact gym; no
 * unverified cross-gym machine default may be borrowed after cold restart.
 */
internal object NativeWorkoutMachineChoicePrefs {
    private const val NAME="native-workout-machine-selections"
    private val segment=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,79}$")

    private fun key(athleteId:String,gymId:String,workoutId:String):String? {
        if(listOf(athleteId,gymId,workoutId).any { !segment.matches(it) })
            return null
        val bytes=MessageDigest.getInstance("SHA-256").digest(
            listOf(athleteId,gymId,workoutId).joinToString("|") {
                "${it.length}:$it"
            }.toByteArray(Charsets.UTF_8))
        return bytes.joinToString(""){"%02x".format(it)}
    }

    fun sanitize(
        encoded:String,gymId:String,configurations:List<KotlinGymMachineAlternatives>
    ):String {
        val choices=encoded.split(";")
        if(choices.size>256 || encoded.length>8192)return ""
        val seen=HashSet<String>()
        val kept=ArrayList<String>()
        for(choice in choices) {
            val separator=choice.indexOf('=')
            if(separator<=0 || separator==choice.lastIndex ||
                choice.indexOf('=',separator+1)!=-1)continue
            val exercise=choice.substring(0,separator)
            val machine=choice.substring(separator+1)
            if(!segment.matches(exercise) || !segment.matches(machine) ||
                !seen.add(exercise))continue
            val config=KotlinMachineAlternativesDomain.forGymExercise(
                configurations,gymId,exercise) ?:continue
            if(config.machines.none {it.id==machine && it.active})continue
            kept.add("$exercise=$machine")
        }
        return kept.joinToString(";")
    }

    fun load(
        context:Context,athleteId:String,gymId:String,workoutId:String,
        configurations:List<KotlinGymMachineAlternatives>
    ):String {
        val key=key(athleteId,gymId,workoutId)?:return ""
        val encoded=context.applicationContext.getSharedPreferences(NAME,Context.MODE_PRIVATE)
            .getString(key,"").orEmpty()
        return sanitize(encoded,gymId,configurations)
    }

    fun save(
        context:Context,athleteId:String,gymId:String,workoutId:String,
        encoded:String,configurations:List<KotlinGymMachineAlternatives>
    ):Boolean {
        val key=key(athleteId,gymId,workoutId)?:return false
        val safe=sanitize(encoded,gymId,configurations)
        return context.applicationContext.getSharedPreferences(NAME,Context.MODE_PRIVATE)
            .edit().putString(key,safe).commit()
    }
}

/** One shared Material 3 selector for both generated and custom workout journals. */
@Composable
internal fun NativeKotlinWorkoutMachineSelector(
    config:KotlinGymMachineAlternatives?,
    selectedId:String?,
    enabled:Boolean,
    onSelect:(String)->Unit
) {
    if(config==null||config.machines.isEmpty())return
    PursuitCard {
        Text("Physical machine",style=MaterialTheme.typography.titleMedium)
        Text("This exercise is shared, but each machine has its own weight history and progression.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant)
        Row(
            Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
            horizontalArrangement=Arrangement.spacedBy(8.dp)
        ) {
            config.machines.filter {it.active||it.id==selectedId}.forEach {machine->
                FilterChip(selected=machine.id==selectedId,
                    enabled=enabled&&machine.active,
                    onClick={onSelect(machine.id)},
                    label={Text(machine.label)})
            }
        }
        val chosen=config.machines.firstOrNull{it.id==selectedId}
        Text(
            if(chosen!=null)
                "Selected: ${chosen.label}. Enter the actual weight for this machine; no conversion from another machine is assumed."
            else "Choose a machine before logging this set.",
            style=MaterialTheme.typography.bodySmall,
            color=MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

/**
 * A selected physical machine cannot display another stack as "Last".
 * The authoritative reference domain has already authenticated same-gym,
 * same-day workout history. This enforces the additional machine boundary.
 */
internal fun physicalMachineLastReferences(
    previous:WorkoutLastReferenceResult?,
    selectedMachineId:String?
):WorkoutLastReferenceResult? =
    if(previous is WorkoutLastReferenceResult.Ready) {
        // Null is historical legacy only, not permission to borrow A or B.
        val requested=KotlinMachineAlternativesDomain.normalizeMachineId(selectedMachineId)
        val matching=if(previous.machineSets.isNotEmpty())
            previous.machineSets.filterKeys { it.machineId==requested }
                .mapKeys { it.key.key }
        else previous.sets.filterValues {
            KotlinMachineAlternativesDomain.normalizeMachineId(
                it.actual?.machineInstanceId)==requested
        }
        WorkoutLastReferenceResult.Ready(matching)
    } else previous
