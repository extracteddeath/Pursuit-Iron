package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.*

/**
 * The immutable issued warmup prescription must agree with the approved day
 * for ALL history used to review new working-set rep/weight targets.
 *
 * Actual warmup performance may be skipped or corrected independently and
 * NEVER earns working-set dose/progression. This comparison uses only issued
 * IDs, loads, reps and effort; it cannot authorize a workout or history edit.
 */
internal object NativeKotlinWarmupEvidenceGate {
    fun matchesIssued(
        approved:KotlinStagedWorkout,
        history:KotlinWorkoutHistoryEvidence,
        exerciseId:String
    ):Boolean {
        val expected=approved.targets.filter {
            it.exerciseId==exerciseId && it.type==DraftSetType.WARMUP
        }.sortedBy {it.ordinal}
        val recorded=history.sets.filter {
            it.exerciseId==exerciseId && it.type==DraftSetType.WARMUP
        }.sortedBy {it.originalOrdinal}
        if(expected.size!=recorded.size)return false
        return expected.indices.all {i->
            val target=expected[i]
            val fact=recorded[i]
            val issued=fact.issuedLoad as? DraftSetLoad.Verified
            val approvedLoad=target.load as? DraftSetLoad.Verified
            fact.setKey==target.setKey &&
                fact.originalOrdinal==target.ordinal &&
                fact.issuedReps==target.reps &&
                fact.issuedRir==target.targetRir &&
                issued!=null && approvedLoad!=null &&
                issued.unit==approvedLoad.unit &&
                issued.basis==approvedLoad.basis &&
                issued.value.compareTo(approvedLoad.value)==0
        }
    }
}
