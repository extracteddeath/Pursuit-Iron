package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.math.RoundingMode

/**
 * The UI's units are NOT journal storage units. Explicit typed input is
 * converted once into integer seconds, centimeters, or repetitions. Never
 * reinterpret the old web practicalReps field as a hold/distance prescription.
 */
enum class MeasuredEntryUnit {
    SECONDS, MINUTES_SECONDS, CENTIMETERS, METERS, FEET, REPETITIONS
}

data class MeasuredExerciseEntryInput(
    /** One user-submitted string for EACH approved side. */
    val quantityBySide:Map<MeasuredSide,String>,
    val unit:MeasuredEntryUnit,
    /** Actual external weight is always entered, NEVER prefilled from target. */
    val externalLoadText:String?=null,
    val externalLoadUnit:LoadUnit?=null,
    val physicalSetupId:String,
    val bandId:String?=null,
    /** Extra carry elapsed time is OPTIONAL, not distance or a rep count. */
    val carryElapsedSecondsText:String?=null
)

enum class MeasuredEntryRefusal {
    UNAPPROVED_PLAN, MISSING_OR_EXTRA_SIDE,
    WRONG_QUANTITY_UNIT, INVALID_QUANTITY,
    WRONG_PHYSICAL_SETUP, LOAD_ENTRY_REQUIRED,
    UNVERIFIED_LOAD, WRONG_OR_UNVERIFIED_BAND,
    INVALID_ELAPSED_TIME, INVALID_ACTUAL
}

sealed interface MeasuredExerciseEntryDecision {
    data class Accepted(
        val outcome:MeasuredSetOutcome,
        val actual:MeasuredExerciseActual?,
        /** Whole centimeters: feet may require a reported ±0.5 cm rounding.
         * Always render this information before recording the observation. */
        val approximateDistanceConversion:Boolean,
        val convertedCentimetersBySide:Map<MeasuredSide,Int>
    ):MeasuredExerciseEntryDecision {
        val isSaved:Boolean get()=false
        val mayPublishHistory:Boolean get()=false
    }
    data class Rejected(val reason:MeasuredEntryRefusal):MeasuredExerciseEntryDecision
}

object MeasuredExerciseEntryDomain {
    private val unsigned=Regex("^(?:0|[1-9][0-9]{0,6})$")
    private val decimal2=Regex("^(?:0|[1-9][0-9]{0,5})(?:\\.[0-9]{1,2})?$")
    private val decimalLoad=Regex("^(?:0|[1-9][0-9]{0,5})(?:\\.[0-9]{1,3})?$")
    private val minutesSeconds=Regex("^([0-9]{1,2}):([0-5][0-9])$")
    private val zero=BigDecimal.ZERO
    private val hundred=BigDecimal("100")
    private val cmPerFoot=BigDecimal("30.48")

    private fun seconds(raw:String,clock:Boolean):Int? {
        val text=raw.trim()
        if(clock) {
            val match=minutesSeconds.matchEntire(text) ?:return null
            val minutes=match.groupValues[1].toInt()
            val seconds=match.groupValues[2].toInt()
            return minutes*60+seconds
        }
        if(!unsigned.matches(text))return null
        return text.toIntOrNull()
    }

    private fun amount(
        kind:MeasuredExerciseKind,unit:MeasuredEntryUnit,text:String
    ):Pair<Int,Boolean>? {
        val raw=text.trim()
        if(kind==MeasuredExerciseKind.HOLD_SECONDS) {
            val time=when(unit) {
                MeasuredEntryUnit.SECONDS->seconds(raw,false)
                MeasuredEntryUnit.MINUTES_SECONDS->seconds(raw,true)
                else->null
            } ?:return null
            if(time !in 0..3600)return null
            return time to false
        }
        if(kind==MeasuredExerciseKind.BANDED_REPETITIONS) {
            if(unit!=MeasuredEntryUnit.REPETITIONS || !unsigned.matches(raw))
                return null
            val reps=raw.toIntOrNull() ?:return null
            if(reps !in 0..100)return null
            return reps to false
        }
        if(kind!=MeasuredExerciseKind.CARRY_CENTIMETERS)return null
        val length=when(unit) {
            MeasuredEntryUnit.CENTIMETERS -> {
                if(!unsigned.matches(raw))return null
                BigDecimal(raw)
            }
            MeasuredEntryUnit.METERS -> {
                if(!decimal2.matches(raw))return null
                BigDecimal(raw).multiply(hundred)
            }
            MeasuredEntryUnit.FEET -> {
                if(!decimal2.matches(raw))return null
                BigDecimal(raw).multiply(cmPerFoot)
            }
            else ->return null
        }
        if(length<zero || length>BigDecimal("1000000.5"))return null
        val exact=length.stripTrailingZeros().scale()<=0
        val centimeters=length.setScale(0,RoundingMode.HALF_UP)
            .toIntOrNullSafe() ?:return null
        if(centimeters !in 0..1_000_000 || centimeters==0 && length.signum()!=0)return null
        // Feet is the only supported presentation unit that may need
        // rounding. An explicit notice is required; target/storage stay cm.
        return centimeters to (unit==MeasuredEntryUnit.FEET && !exact)
    }

    private fun BigDecimal.toIntOrNullSafe():Int? =
        runCatching{intValueExact()}.getOrNull()

    fun parse(
        plan:MeasuredExerciseSetPlan,
        outcome:MeasuredSetOutcome,
        input:MeasuredExerciseEntryInput?
    ):MeasuredExerciseEntryDecision {
        fun denied(reason:MeasuredEntryRefusal)=
            MeasuredExerciseEntryDecision.Rejected(reason)
        val definition=MeasuredExerciseCatalogDomain.find(plan.exerciseId)
            ?:return denied(MeasuredEntryRefusal.UNAPPROVED_PLAN)
        if(MeasuredExerciseDomain.inspect(plan) !is MeasurementPlanResult.Candidate)
            return denied(MeasuredEntryRefusal.UNAPPROVED_PLAN)
        if(outcome==MeasuredSetOutcome.SKIPPED) {
            if(input!=null)return denied(MeasuredEntryRefusal.INVALID_ACTUAL)
            return MeasuredExerciseEntryDecision.Accepted(
                outcome,null,false,emptyMap())
        }
        val submitted=input ?:return denied(MeasuredEntryRefusal.INVALID_ACTUAL)
        if(submitted.physicalSetupId!=plan.physicalSetupId)
            return denied(MeasuredEntryRefusal.WRONG_PHYSICAL_SETUP)
        if(submitted.quantityBySide.keys!=plan.sides ||
            submitted.quantityBySide.size!=plan.sides.size)
            return denied(MeasuredEntryRefusal.MISSING_OR_EXTRA_SIDE)
        val permitted=when(definition.kind) {
            MeasuredExerciseKind.HOLD_SECONDS->setOf(
                MeasuredEntryUnit.SECONDS,MeasuredEntryUnit.MINUTES_SECONDS)
            MeasuredExerciseKind.CARRY_CENTIMETERS->setOf(
                MeasuredEntryUnit.METERS,MeasuredEntryUnit.CENTIMETERS,
                MeasuredEntryUnit.FEET)
            MeasuredExerciseKind.BANDED_REPETITIONS ->
                setOf(MeasuredEntryUnit.REPETITIONS)
        }
        if(submitted.unit !in permitted)
            return denied(MeasuredEntryRefusal.WRONG_QUANTITY_UNIT)
        val converted=linkedMapOf<MeasuredSide,Int>()
        var rounded=false
        for(side in plan.sides.sortedBy{it.ordinal}) {
            val raw=submitted.quantityBySide[side].orEmpty()
            val candidate=amount(definition.kind,submitted.unit,raw)
                ?:return denied(MeasuredEntryRefusal.INVALID_QUANTITY)
            val (quantity,approximate)=candidate
            if(outcome==MeasuredSetOutcome.FAILED_ATTEMPT && quantity!=0 ||
                outcome==MeasuredSetOutcome.COMPLETED && quantity==0)
                return denied(MeasuredEntryRefusal.INVALID_QUANTITY)
            converted[side]=quantity
            rounded=rounded || approximate
        }
        val carryTime=if(definition.kind==MeasuredExerciseKind.CARRY_CENTIMETERS) {
            val text=submitted.carryElapsedSecondsText?.trim()
            if(text.isNullOrEmpty())null else
                seconds(text,false)?.takeIf{it in 1..3600}
                    ?:return denied(MeasuredEntryRefusal.INVALID_ELAPSED_TIME)
        } else {
            if(!submitted.carryElapsedSecondsText.isNullOrBlank())
                return denied(MeasuredEntryRefusal.INVALID_ELAPSED_TIME)
            null
        }
        val mass:BigDecimal?
        val massUnit:LoadUnit?
        when(definition.loadKind) {
            MeasuredLoadKind.UNWEIGHTED -> {
                if(!submitted.externalLoadText.isNullOrBlank() ||
                    submitted.externalLoadUnit!=null || submitted.bandId!=null)
                    return denied(MeasuredEntryRefusal.UNVERIFIED_LOAD)
                mass=null;massUnit=null
            }
            MeasuredLoadKind.BANDED_BODYWEIGHT -> {
                if(!submitted.externalLoadText.isNullOrBlank() ||
                    submitted.externalLoadUnit!=null)
                    return denied(MeasuredEntryRefusal.UNVERIFIED_LOAD)
                if(submitted.bandId!=plan.bandId)
                    return denied(MeasuredEntryRefusal.WRONG_OR_UNVERIFIED_BAND)
                mass=null;massUnit=null
            }
            MeasuredLoadKind.VERIFIED_ADDED_LOAD,
            MeasuredLoadKind.VERIFIED_PER_HAND -> {
                val raw=submitted.externalLoadText?.trim().orEmpty()
                if(raw.isEmpty())
                    return denied(MeasuredEntryRefusal.LOAD_ENTRY_REQUIRED)
                if(!decimalLoad.matches(raw) || submitted.externalLoadUnit!=plan.unit ||
                    submitted.bandId!=null)
                    return denied(MeasuredEntryRefusal.UNVERIFIED_LOAD)
                mass=runCatching{BigDecimal(raw)}.getOrNull()
                    ?:return denied(MeasuredEntryRefusal.UNVERIFIED_LOAD)
                massUnit=submitted.externalLoadUnit
                val inspection=EquipmentLoadDomain.inspect(
                    plan.physicalStock,mass,checkNotNull(massUnit))
                if(inspection !is LoadInspection.Confirmed || !inspection.exact)
                    return denied(MeasuredEntryRefusal.UNVERIFIED_LOAD)
            }
        }
        val actual=MeasuredExerciseActual(
            plan.physicalSetupId,
            converted.map{(side,q)->MeasuredSideResult(side,q)},
            mass,massUnit,submitted.bandId,carryTime)
        if(!MeasuredExerciseDomain.validActual(plan,outcome,actual))
            return denied(MeasuredEntryRefusal.INVALID_ACTUAL)
        return MeasuredExerciseEntryDecision.Accepted(
            outcome,actual,rounded,converted)
    }
}
