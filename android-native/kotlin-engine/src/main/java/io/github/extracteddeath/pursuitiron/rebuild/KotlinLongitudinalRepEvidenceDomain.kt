package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * A FINISHED workout with individually accepted exact rep targets retains its
 * real immutable issued targets in history. For the NEXT coaching calculation
 * only, restore the CURRENT original practical rep envelope (e.g. 10–15).
 *
 * This MUST be called only after the owner verified complete replayed history,
 * unchanged program source/session purpose, physical gym, and the authoritative
 * straight-set policy. It does not edit history, change approved targets, or
 * issue a new workout.
 *
 * An authored mixed top/backoff scheme must NEVER be relabeled as a uniform
 * straight-set program. Therefore every historical rep band must either equal
 * the exact original envelope, OR be a SINGLE exact integer inside it; mixing
 * those two representations is refused.
 */
enum class LongitudinalRepEvidenceRefusal {
    NO_SETS_OR_INVALID_ORDER,
    CHANGED_EQUIPMENT_OR_ISSUED_LOAD,
    UNVERIFIED_PHYSICAL_STOCK,
    CHANGED_TARGET_EFFORT,
    UNRELATED_MIXED_REP_SCHEME
}

sealed interface LongitudinalRepEvidenceResult {
    data class Rejected(val reason:LongitudinalRepEvidenceRefusal):
        LongitudinalRepEvidenceResult
    data class Comparable(
        val session:ProgressionWorkoutEvidence,
        val exactIssuedReps:List<RepBand>,
        val normalizedPersonalizedTargets:Boolean
    ):LongitudinalRepEvidenceResult {
        /** Never provides permission to write existing journal targets. */
        val mayEditHistory:Boolean get()=false
        val mayIssueWorkout:Boolean get()=false
    }
}

object KotlinLongitudinalRepEvidenceDomain {
    fun review(
        historical:KotlinExerciseHistoryEvidence,
        completedWorkoutId:String,
        approvedReps:RepBand,
        expectedSets:Int,
        approvedLoad:BigDecimal,
        stock:VerifiedLoadStock?,
        approvedUnit:LoadUnit,
        approvedBasis:LoadBasis,
        approvedTargetRir:Int?,
        /** Only native re-staged, exact per-ordinal prescription may supply
         * different issued working loads. A UI flag is not sufficient. */
        approvedSetLoads:List<BigDecimal>?=null
    ):LongitudinalRepEvidenceResult {
        fun reject(reason:LongitudinalRepEvidenceRefusal)=
            LongitudinalRepEvidenceResult.Rejected(reason)
        val ordered=historical.workingSets.sortedBy {it.originalOrdinal}
        if(completedWorkoutId.isBlank() || expectedSets !in 1..32 ||
            ordered.size!=expectedSets ||
            ordered.map {it.originalOrdinal}!=(0 until expectedSets).toList() ||
            ordered.map {it.setKey}.distinct().size!=expectedSets ||
            ordered.any {it.type!=DraftSetType.WORKING ||
                it.exerciseId!=historical.exerciseId})
            return reject(LongitudinalRepEvidenceRefusal.NO_SETS_OR_INVALID_ORDER)
        val expected=approvedSetLoads ?:List(expectedSets){approvedLoad}
        if(expected.size!=expectedSets ||
            stock==null || stock.unit!=approvedUnit ||
            stock.basis!=approvedBasis ||
            expected.any {planned ->
                val checked=EquipmentLoadDomain.inspect(stock,planned,approvedUnit)
                checked !is LoadInspection.Confirmed ||
                    !checked.exact || checked.basis!=approvedBasis
            })
            return reject(LongitudinalRepEvidenceRefusal.UNVERIFIED_PHYSICAL_STOCK)
        if(ordered.any {set ->
            val load=set.issuedLoad as? DraftSetLoad.Verified
            load==null || load.unit!=approvedUnit ||
                load.basis!=approvedBasis ||
                load.value.compareTo(expected[set.originalOrdinal])!=0 ||
                (set.actual!=null && set.actual.unit!=approvedUnit)
        })
            return reject(LongitudinalRepEvidenceRefusal.CHANGED_EQUIPMENT_OR_ISSUED_LOAD)
        if(ordered.any { it.issuedRir!=approvedTargetRir })
            return reject(LongitudinalRepEvidenceRefusal.CHANGED_TARGET_EFFORT)
        val bands=ordered.map {it.issuedReps}
        val standard=bands.all {it==approvedReps}
        val personalized=bands.all {
            it.minimum==it.maximum &&
                it.minimum in approvedReps.minimum..approvedReps.maximum
        }
        if(!standard && !personalized)
            return reject(LongitudinalRepEvidenceRefusal.UNRELATED_MIXED_REP_SCHEME)

        val observations=ordered.map {
            val load=it.issuedLoad as DraftSetLoad.Verified
            WorkingSetFact(it.originalOrdinal,when(it.outcome) {
                KotlinJournalSetOutcome.COMPLETED -> SetOutcome.COMPLETED
                KotlinJournalSetOutcome.SKIPPED -> SetOutcome.SKIPPED
                KotlinJournalSetOutcome.FAILED_ATTEMPT -> SetOutcome.FAILED_ATTEMPT
            },it.actual?.load,it.actual?.reps,it.actual?.actualRir,
                load.value,it.issuedRir)
        }
        // Preserve original facts and records: only the coaching evidence
        // object's effective *range* is normalized to the approved program.
        val normalized=ProgressionWorkoutEvidence(
            completedWorkoutId,historical.exerciseId,approvedUnit,
            approvedReps,expectedSets,Collections.unmodifiableList(observations))
        return LongitudinalRepEvidenceResult.Comparable(
            normalized,Collections.unmodifiableList(bands),
            personalized && !standard)
    }
}
