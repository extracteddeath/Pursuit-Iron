package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Read-only, per-set rep-target coaching while an athlete builds reps at the
 * SAME physically confirmed loading. This is explicitly NOT an issued target,
 * auto-progression, or a substitute for longitudinal program approval.
 *
 * Old-engine regression safeguards:
 * - Never increase all set targets merely because set 1 was strong.
 * - Never jump by 2-3 reps in one revision, even for a strong first set.
 * - Never infer actual effort from issued RIR.
 * - Never turn a near-failure last set into a mandate to do more next time.
 * - Never transfer a result between implement bases, units or edited loads.
 */
enum class FatigueRepReviewReason {
    INCOMPLETE_OR_FAILED_SESSION,
    BELOW_TARGET_FLOOR,
    UNSTABLE_OR_USER_EDITED_LOADS,
    CURRENT_LOAD_NOT_VERIFIED,
    ZERO_LOAD_NEEDS_SPECIFIC_POLICY,
    ACTUAL_EFFORT_NOT_REPORTED,
    NEAR_FAILURE_OR_RECOVERY_PRIORITY,
    ALREADY_AT_REP_CEILING,
    NO_DEMONSTRATED_EFFORT_HEADROOM,
    ONE_SET_REP_INCREASE_FOR_REVIEW
}

data class FatigueRepTargetReview(
    val reason: FatigueRepReviewReason,
    /** Actual completed reps in original ordinal order; never invented. */
    val observedReps: List<Int>?,
    /** Proposed per-set repetitions, NEVER a change to issued targets. */
    val candidateRepTargets: List<Int>?,
    val proposedOrdinal: Int?,
    val observedLoad: BigDecimal?,
    /** Exact original ordinal loads; essential when every set is different. */
    val observedSetLoads: List<BigDecimal>? = null
) {
    init {
        val ready=reason==FatigueRepReviewReason.ONE_SET_REP_INCREASE_FOR_REVIEW
        require(ready==(candidateRepTargets!=null && proposedOrdinal!=null))
        if(ready) {
            val base=checkNotNull(observedReps)
            val proposed=checkNotNull(candidateRepTargets)
            val changed=checkNotNull(proposedOrdinal)
            require(proposed.size==base.size && changed in base.indices)
            require(proposed.indices.all { i ->
                proposed[i]==base[i]+if(i==changed)1 else 0
            })
        }
    }
    val canAutoApply: Boolean get()=false
    val mayIssueWorkout: Boolean get()=false
}

object FatigueAwareRepTargetReviewDomain {
    private val zero=BigDecimal.ZERO

    /**
     * One load, one finished session, original set order. The caller must
     * separately authenticate full history, program source and actual session
     * purpose before accepting any proposal into a NEW approved program.
     */
    fun assess(
        session: ProgressionWorkoutEvidence,
        verifiedStock: VerifiedLoadStock?,
        approvedLoadBasis: LoadBasis,
        readiness: CoachReadiness,
        movementRestricted: Boolean,
        /** Only an authenticated native per-set prescription can enable
         * mixed-load coaching. Never infer this from a user-selected toggle. */
        verifiedPerSetLoading: Boolean = false
    ): FatigueRepTargetReview {
        val evidence=DoubleProgressionEvidenceDomain.assess(
            session.targetReps,session.expectedWorkingSets,session.observations)
        val ordered=session.observations.sortedBy {it.workingOrdinal}
        val complete=evidence.completedWorkingSets==session.expectedWorkingSets &&
            ordered.size==session.expectedWorkingSets
        val reps=if(complete) Collections.unmodifiableList(
            ordered.map {checkNotNull(it.actualReps)}) else null
        val setLoads=if(complete)ordered.map {
            BigDecimal(checkNotNull(it.actualLoad)
                .stripTrailingZeros().toPlainString())
        } else null
        val rawLoads=setLoads?.distinct().orEmpty()
        val load=if(rawLoads.size==1)rawLoads.single() else null
        fun result(reason:FatigueRepReviewReason,
            candidate:List<Int>?=null,ordinal:Int?=null)=
            FatigueRepTargetReview(reason,reps,
                candidate?.let {Collections.unmodifiableList(it)},ordinal,load,
                setLoads?.let{Collections.unmodifiableList(it)})

        if(!complete || ordered.any {it.outcome!=SetOutcome.COMPLETED})
            return result(FatigueRepReviewReason.INCOMPLETE_OR_FAILED_SESSION)
        if(evidence.belowFloorOrdinals.isNotEmpty())
            return result(FatigueRepReviewReason.BELOW_TARGET_FLOOR)
        // Mixed authored set loads can progress REPS independently; they
        // cannot claim a new uniform weight. Every ordinal must match the
        // actual immutable issued target and physically verified gym stock.
        // The legacy uniform-only path remains unchanged by default.
        if((evidence.mixedActualLoads && !verifiedPerSetLoading) ||
            setLoads==null || setLoads.size!=ordered.size ||
            ordered.any {it.issuedLoad==null ||
                it.issuedLoad.compareTo(it.actualLoad)!=0})
            return result(FatigueRepReviewReason.UNSTABLE_OR_USER_EDITED_LOADS)
        if(setLoads.any{it.compareTo(zero)==0})
            return result(FatigueRepReviewReason.ZERO_LOAD_NEEDS_SPECIFIC_POLICY)
        if(verifiedStock?.basis!=approvedLoadBasis)
            return result(FatigueRepReviewReason.CURRENT_LOAD_NOT_VERIFIED)
        if(setLoads.any{actual ->
                val inspected=EquipmentLoadDomain.inspect(
                    verifiedStock,actual,session.unit)
                inspected !is LoadInspection.Confirmed || !inspected.exact ||
                    inspected.basis!=approvedLoadBasis
            })
            return result(FatigueRepReviewReason.CURRENT_LOAD_NOT_VERIFIED)
        if(ordered.any {it.actualRir==null})
            return result(FatigueRepReviewReason.ACTUAL_EFFORT_NOT_REPORTED)
        if(movementRestricted || readiness==CoachReadiness.LOW ||
            ordered.any {checkNotNull(it.actualRir)<=1})
            return result(FatigueRepReviewReason.NEAR_FAILURE_OR_RECOVERY_PRIORITY)
        if(ordered.all {it.actualReps==session.targetReps.maximum})
            return result(FatigueRepReviewReason.ALREADY_AT_REP_CEILING)

        // Never tell every set to gain reps at once. Prefer the lowest
        // attained rep count with ACTUALLY reported >=3 RIR; this accounts
        // for the often greater fatigue later in a session without forcing
        // reps on an observed near-limit set. Ordinal resolves ties so the
        // decision is repeatable and stable after history reload.
        val eligible=ordered.filter {
            checkNotNull(it.actualReps)<session.targetReps.maximum &&
                checkNotNull(it.actualRir)>=3
        }
        val chosen=eligible.minWithOrNull(
            compareBy<WorkingSetFact> {checkNotNull(it.actualReps)}
                .thenBy {it.workingOrdinal})
            ?:return result(FatigueRepReviewReason.NO_DEMONSTRATED_EFFORT_HEADROOM)
        val ordinal=chosen.workingOrdinal
        val candidate=checkNotNull(reps).mapIndexed {i,n ->
            if(i==ordinal) n+1 else n
        }
        return result(FatigueRepReviewReason.ONE_SET_REP_INCREASE_FOR_REVIEW,
            candidate,ordinal)
    }
}
