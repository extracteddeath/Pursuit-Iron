package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Human-authored inputs for a custom-cycle audit, never a prescriptive model.
 * Missing numbers remain missing. In particular, zero dose, zero session
 * overhead and continuity fractions are never invented on behalf of an
 * athlete or coach.
 */
data class CustomMuscleDoseEntry(
    val mev:String,
    val mav:String,
    val mrv:String,
    val minimumDirectDays:String
)

data class CustomPhasePolicyEntry(
    val phase:TrainingPhase,
    val policyId:String,
    val revision:String,
    val rationale:String,
    val baseline:Map<TrainingMuscle,CustomMuscleDoseEntry>,
    val selected:Map<TrainingMuscle,CustomMuscleDoseEntry>,
    val minimumPurposeDays:Map<ComposerSessionPurpose,String>,
    val primaryLiftMinimumDays:Map<String,String> = emptyMap()
)

data class CustomCoachPolicyAuthoringRequest(
    val phases:List<CustomPhasePolicyEntry>,
    /** Explicit seconds including setup, transitions and cooldown. */
    val nonSetSecondsByWeekday:Map<Int,String>,
    val totalDropFraction:String = "",
    val directDropFraction:String = "",
    val totalRiseFraction:String = "",
    val directRiseFraction:String = ""
)

sealed interface CustomCoachPolicyAuthoringResult {
    data class Ready(val inputs:CustomCoachAuditInputs):
        CustomCoachPolicyAuthoringResult
    data class Blocked(val missingOrInvalid:List<String>):
        CustomCoachPolicyAuthoringResult
}

object CustomCoachPolicyAuthoringDomain {
    private val validId=Regex("^[A-Za-z0-9][A-Za-z0-9_:-]{0,63}$")
    private val zero=BigDecimal.ZERO
    private val one=BigDecimal.ONE
    private val cap=BigDecimal("200")
    private fun fraction(value:String):BigDecimal? =
        runCatching{BigDecimal(value.trim())}.getOrNull()?.takeIf{
            it>=zero && it<=one && it.stripTrailingZeros().scale()<=3
        }
    private fun number(value:String):BigDecimal? =
        runCatching{BigDecimal(value.trim())}.getOrNull()?.takeIf{
            it>=zero && it<=cap && it.stripTrailingZeros().scale()<=3
        }
    private fun whole(value:String,min:Int,max:Int):Int? =
        value.trim().toIntOrNull()?.takeIf{it in min..max}

    fun inspect(
        cycle:CustomTrainingCycle,
        setup:AthleteSetupReview,
        draft:CustomCoachPolicyAuthoringRequest
    ):CustomCoachPolicyAuthoringResult {
        val failures=mutableListOf<String>()
        fun bad(message:String) { if(failures.size<128)failures+=message }
        val athlete=setup.reviewed?.takeIf {
            setup.status==AthleteSetupStatus.READY_FOR_PROGRAM_REVIEW &&
                it.athleteProfileId==cycle.athleteId &&
                it.goal==cycle.goal
        } ?:return CustomCoachPolicyAuthoringResult.Blocked(
            listOf("A current reviewed athlete profile matching this cycle is required."))
        val days=athlete.trainingWeekdays
        if(days.isEmpty() || days.size>7)
            bad("Current training weekdays are not verified.")
        val overhead=linkedMapOf<Int,Int>()
        if(draft.nonSetSecondsByWeekday.keys!=days)
            bad("Declare session overhead for every scheduled training weekday, and no others.")
        days.sorted().forEach {day->
            val amount=whole(draft.nonSetSecondsByWeekday[day].orEmpty(),0,21600)
            if(amount==null)bad("Day ${day+1}: enter 0–21600 actual non-set seconds.")
            else overhead[day]=amount
        }
        val phases=cycle.blocks.map{it.phase}.toSet()
        if(draft.phases.size!=phases.size ||
            draft.phases.map{it.phase}.toSet()!=phases)
            bad("Supply exactly one complete policy per phase in this custom cycle.")
        val results=linkedMapOf<TrainingPhase,AuthoredGoalProgramPolicy>()
        for(entry in draft.phases) {
            val prefix=entry.phase.name
            if(entry.phase in results) {
                bad("$prefix: duplicate phase policy.")
                continue
            }
            if(!validId.matches(entry.policyId) ||
                !validId.matches(entry.revision) ||
                entry.rationale.isBlank() ||
                entry.rationale.length>400)
                bad("$prefix: enter a stable policy ID, revision and explanation.")
            fun parseBounds(
                values:Map<TrainingMuscle,CustomMuscleDoseEntry>,
                role:String
            ):List<DeclaredMuscleDoseBounds>? {
                if(!TrainingMuscleCoverage.isComplete(values.keys)) {
                    bad("$prefix $role: declare a complete muscle target scope, including explicitly zero-dose muscles.")
                    return null
                }
                val parsed=mutableListOf<DeclaredMuscleDoseBounds>()
                for(muscle in values.keys.sortedBy{it.ordinal}) {
                    val entry=values[muscle] ?:continue
                    val mev=number(entry.mev)
                    val mav=number(entry.mav)
                    val mrv=number(entry.mrv)
                    val minimum=whole(entry.minimumDirectDays,0,days.size)
                    if(mev==null||mav==null||mrv==null||minimum==null||
                        mev>mav||mav>mrv) {
                        bad("$prefix $role ${muscle.name}: MEV ≤ MAV ≤ MRV (0–200), direct days 0–${days.size}; all values explicitly authored.")
                    } else parsed+=DeclaredMuscleDoseBounds(
                        muscle,mev,mav,mrv,minimum)
                }
                return if(parsed.size==values.size)parsed else null
            }
            val baseline=parseBounds(entry.baseline,"baseline")
            val selected=parseBounds(entry.selected,"selected")
            val purposes=linkedMapOf<ComposerSessionPurpose,Int>()
            val scheduled=cycle.blocks.filter{it.phase==entry.phase}
                .flatMap{it.days}.groupingBy{it.purpose}.eachCount()
            if(entry.minimumPurposeDays.isEmpty())
                bad("$prefix: state the minimum number of sessions for each coached purpose.")
            entry.minimumPurposeDays.forEach {(purpose,value)->
                val count=whole(value,1,days.size)
                if(purpose==ComposerSessionPurpose.UNSPECIFIED ||
                    count==null || (scheduled[purpose]?:0)<count)
                    bad("$prefix: ${purpose.name} must have an attainable authored day minimum.")
                else purposes[purpose]=count
            }
            val core=if(entry.phase==TrainingPhase.HYPERTROPHY)
                ComposerSessionPurpose.HYPERTROPHY
            else ComposerSessionPurpose.STRENGTH_SKILL
            if((purposes[core]?:0)<1)
                bad("$prefix: explicitly require at least one ${core.name} day.")
            val lifts=linkedMapOf<String,Int>()
            entry.primaryLiftMinimumDays.forEach {(id,value)->
                val count=whole(value,1,days.size)
                if(!validId.matches(id)||count==null)
                    bad("$prefix: main lift ${id.take(40)} needs a valid stable ID and reviewed direct frequency.")
                else lifts[id]=count
            }
            if(baseline!=null && selected!=null &&
                entry.phase !in results)
                results[entry.phase]=AuthoredGoalProgramPolicy(
                    entry.policyId,entry.revision,entry.rationale,
                    athlete.athleteProfileId,athlete.profileRevision,
                    athlete.goal,athlete.experience,athlete.split,entry.phase,
                    days.size,baseline,selected,purposes,lifts)
        }
        val fractions=listOf(draft.totalDropFraction,draft.directDropFraction,
            draft.totalRiseFraction,draft.directRiseFraction)
        val continuity=when {
            fractions.all{it.isBlank()} -> null
            draft.totalDropFraction.isBlank() ||
                draft.directDropFraction.isBlank() -> {
                bad("Continuity: provide both total and direct-muscle drop ceilings.")
                null
            }
            draft.totalRiseFraction.isBlank()!=
                draft.directRiseFraction.isBlank() -> {
                bad("Continuity: both total and direct-muscle rise ceilings must be supplied together.")
                null
            }
            else -> {
                val parsed=fractions.map{if(it.isBlank())null else fraction(it)}
                if(parsed[0]==null || parsed[1]==null ||
                    (fractions[2].isNotBlank() && parsed[2]==null) ||
                    (fractions[3].isNotBlank() && parsed[3]==null)) {
                    bad("Continuity: fractions must be explicit decimals between 0 and 1 (up to three places).")
                    null
                } else CustomCoachVolumeContinuityPolicy(
                    checkNotNull(parsed[0]),checkNotNull(parsed[1]),
                    parsed[2],parsed[3])
            }
        }
        if(failures.isNotEmpty())
            return CustomCoachPolicyAuthoringResult.Blocked(
                Collections.unmodifiableList(failures))
        return CustomCoachPolicyAuthoringResult.Ready(
            CustomCoachAuditInputs(Collections.unmodifiableMap(results),
                Collections.unmodifiableMap(overhead),continuity))
    }
}
