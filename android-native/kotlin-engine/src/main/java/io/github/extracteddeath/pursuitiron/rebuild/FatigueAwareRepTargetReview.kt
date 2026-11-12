package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Read-only, per-set rep-target coaching while an athlete builds reps at the
 * SAME physically confirmed loading. This is explicitly NOT an issued target,
 * auto-progression, or a substitute for longitudinal program approval.
 *
 * The current policy estimates each ordinal at its own intended goal RIR.
 * This is a transparent starting estimate, not a calibrated fatigue model.
 * No universal one-set or one-rep cap is imposed. Earlier extra work may
 * change later capacity; the result explicitly declares that uncertainty.
 *
 * Safeguards:
 * - Never copy first-set capacity to every later set.
 * - Goal RIR may be assumed for progression; actual effort stays unreported.
 * - A set closer to failure than intended needs fewer reps or a load review.
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
    /** Historical v1 receipts only; never the default for a new review. */
    ONE_SET_REP_INCREASE_FOR_REVIEW,
    TARGET_RIR_ALIGNED_REPS_FOR_REVIEW,
    EFFORT_TARGET_MISSING,
    EFFORT_REQUIRES_LOAD_REVIEW,
    REP_CEILING_OVERSHOT_REQUIRES_LOAD_REVIEW
}

enum class AdaptiveRepPolicyVersion { LEGACY_SINGLE_SET_V1, TARGET_RIR_V2 }

enum class RepForecastLimitation {
    SINGLE_EXPOSURE,
    RIR_ESTIMATE_UNCALIBRATED,
    ACTUAL_REST_AND_PRIOR_EXERCISE_WORK_UNAVAILABLE,
    CHRONIC_FATIGUE_NOT_ESTIMATED,
    EARLIER_SET_WORK_CHANGED
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
    val observedSetLoads: List<BigDecimal>? = null,
    val assumedGoalRirSetCount: Int = 0,
    val forecastLimitations: Set<RepForecastLimitation> = emptySet()
) {
    init {
        val legacy=reason==FatigueRepReviewReason.ONE_SET_REP_INCREASE_FOR_REVIEW
        val ready=legacy || reason==FatigueRepReviewReason.TARGET_RIR_ALIGNED_REPS_FOR_REVIEW
        require((candidateRepTargets==null)==(proposedOrdinal==null))
        require(ready==(candidateRepTargets!=null && proposedOrdinal!=null))
        if(ready) {
            val base=checkNotNull(observedReps)
            val proposed=checkNotNull(candidateRepTargets)
            val changed=checkNotNull(proposedOrdinal)
            require(proposed.size==base.size && changed in base.indices)
            require(proposed.all {it in 1..100} && proposed[changed]!=base[changed])
            require(proposed.indices.first {proposed[it]!=base[it]}==changed)
            if(legacy) require(proposed.indices.all { i ->
                proposed[i]==base[i]+if(i==changed)1 else 0
            })
        }
    }
    /** Original ordinals; proposedOrdinal remains the first change for old receipts. */
    val proposedOrdinals: Set<Int> get() = candidateRepTargets?.indices
        ?.filter {candidateRepTargets[it]!=checkNotNull(observedReps)[it]}?.toSet().orEmpty()
    val canAutoApply: Boolean get()=false
    val mayIssueWorkout: Boolean get()=false
}

/**
 * Exact unweighted training evidence. There is NO numeric load or load unit,
 * and the host must authenticate the originally issued bodyweight exercise.
 */
data class BodyweightRepTargetEvidence(
    val workoutId:String,
    val exerciseId:String,
    val targetReps:RepBand,
    val expectedWorkingSets:Int,
    val observations:List<WorkingSetFact>
)

object FatigueAwareRepTargetReviewDomain {
    private data class RepFacts(
        val targetReps:RepBand,
        val expectedWorkingSets:Int,
        val observations:List<WorkingSetFact>,
        /** Null only for authenticated, explicitly unloaded bodyweight. */
        val physicalUnit:LoadUnit?
    )

    private val zero=BigDecimal.ZERO

    /**
     * ONE current-policy estimate for a set's own load/ordinal and goal RIR.
     * A below-floor estimate is NOT promoted to the minimum prescribed reps:
     * the target/load/range needs a separate review instead. Unlike frozen
     * V1 receipt replay, all new generated and custom target-RIR forecasts
     * must use this same rule, without rewriting measured actuals.
     */
    internal fun estimateTargetRirReps(
        actualReps:Int,effectiveRir:Int,goalRir:Int,band:RepBand
    ):Int? {
        require(actualReps>=0 && effectiveRir in 0..10 && goalRir in 0..10)
        val estimated=actualReps+effectiveRir-goalRir
        return if(estimated<band.minimum)null else
            estimated.coerceAtMost(band.maximum)
    }

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
        verifiedPerSetLoading: Boolean = false,
        policyVersion: AdaptiveRepPolicyVersion = AdaptiveRepPolicyVersion.TARGET_RIR_V2,
        targetRirByOrdinal: List<Int?>? = null
    ): FatigueRepTargetReview = assessCore(
        RepFacts(session.targetReps,session.expectedWorkingSets,
            session.observations,session.unit),
        verifiedStock,approvedLoadBasis,readiness,movementRestricted,
        verifiedPerSetLoading,policyVersion,targetRirByOrdinal)

    /**
     * The SAME policy and effort estimator for exact unloaded bodyweight.
     * Never infer this from missing loads; only a caller holding the issued
     * ExplicitBodyweight prescription can construct this adapter input.
     */
    fun assessBodyweight(
        session:BodyweightRepTargetEvidence,
        readiness:CoachReadiness,
        movementRestricted:Boolean,
        targetRirByOrdinal:List<Int?>?=null
    ):FatigueRepTargetReview = assessCore(
        RepFacts(session.targetReps,session.expectedWorkingSets,
            session.observations,null),
        null,null,readiness,movementRestricted,
        false,AdaptiveRepPolicyVersion.TARGET_RIR_V2,targetRirByOrdinal)

    private fun assessCore(
        facts:RepFacts,
        verifiedStock:VerifiedLoadStock?,
        approvedLoadBasis:LoadBasis?,
        readiness:CoachReadiness,
        movementRestricted:Boolean,
        verifiedPerSetLoading:Boolean,
        policyVersion:AdaptiveRepPolicyVersion,
        targetRirByOrdinal:List<Int?>?
    ):FatigueRepTargetReview {
        val explicitlyUnloaded=facts.physicalUnit==null
        val evidence=DoubleProgressionEvidenceDomain.assess(
            facts.targetReps,facts.expectedWorkingSets,facts.observations,
            explicitlyUnloaded=explicitlyUnloaded)
        val ordered=facts.observations.sortedBy {it.workingOrdinal}
        val effort=ordered.associate {it.workingOrdinal to ProgressionEffortDomain.resolve(it)}
        val complete=evidence.completedWorkingSets==facts.expectedWorkingSets &&
            ordered.size==facts.expectedWorkingSets
        val reps=if(complete) Collections.unmodifiableList(
            ordered.map {checkNotNull(it.actualReps)}) else null
        val setLoads=if(complete && !explicitlyUnloaded)ordered.map {
            BigDecimal(checkNotNull(it.actualLoad)
                .stripTrailingZeros().toPlainString())
        } else null
        val rawLoads=setLoads?.distinct().orEmpty()
        val load=if(rawLoads.size==1)rawLoads.single() else null
        val baselineLimitations=if(policyVersion==AdaptiveRepPolicyVersion.TARGET_RIR_V2)
            setOf(RepForecastLimitation.SINGLE_EXPOSURE,
                RepForecastLimitation.RIR_ESTIMATE_UNCALIBRATED,
                RepForecastLimitation.ACTUAL_REST_AND_PRIOR_EXERCISE_WORK_UNAVAILABLE,
                RepForecastLimitation.CHRONIC_FATIGUE_NOT_ESTIMATED)
            else emptySet()
        fun result(reason:FatigueRepReviewReason,
            candidate:List<Int>?=null,ordinal:Int?=null,
            limitations:Set<RepForecastLimitation> = baselineLimitations)=
            FatigueRepTargetReview(reason,reps,
                candidate?.let {Collections.unmodifiableList(it)},ordinal,load,
                setLoads?.let{Collections.unmodifiableList(it)},effort.values.count {it.assumed},
                Collections.unmodifiableSet(limitations))

        if(!complete || ordered.any {it.outcome!=SetOutcome.COMPLETED})
            return result(FatigueRepReviewReason.INCOMPLETE_OR_FAILED_SESSION)
        if(evidence.belowFloorOrdinals.isNotEmpty())
            return result(FatigueRepReviewReason.BELOW_TARGET_FLOOR)
        // Mixed authored set loads can progress REPS independently; they
        // cannot claim a new uniform weight. Every ordinal must match the
        // actual immutable issued target and physically verified gym stock.
        // The legacy uniform-only path remains unchanged by default.
        if(explicitlyUnloaded) {
            // An absent recorded weight is valid ONLY under the authenticated
            // bodyweight source mode. A stray physical load must never be
            // silently discarded or interpreted as a zero-pound set.
            if(ordered.any{it.actualLoad!=null || it.issuedLoad!=null})
                return result(FatigueRepReviewReason.UNSTABLE_OR_USER_EDITED_LOADS)
        } else {
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
                        verifiedStock,actual,checkNotNull(facts.physicalUnit))
                    inspected !is LoadInspection.Confirmed || !inspected.exact ||
                        inspected.basis!=approvedLoadBasis
                })
                return result(FatigueRepReviewReason.CURRENT_LOAD_NOT_VERIFIED)
        }
        if(movementRestricted || readiness==CoachReadiness.LOW ||
            (policyVersion==AdaptiveRepPolicyVersion.LEGACY_SINGLE_SET_V1 &&
                effort.values.any {it.rir!=null && it.rir<=1}))
            return result(FatigueRepReviewReason.NEAR_FAILURE_OR_RECOVERY_PRIORITY)
        // Clipping a completed 16+ reps down to a prescribed 15-rep
        // ceiling must not masquerade as earned progression. Over-ceiling
        // work needs a load/range review. Preserve frozen v1 receipts.
        if(policyVersion==AdaptiveRepPolicyVersion.TARGET_RIR_V2 &&
            ordered.any {checkNotNull(it.actualReps)>facts.targetReps.maximum})
            return result(FatigueRepReviewReason.REP_CEILING_OVERSHOT_REQUIRES_LOAD_REVIEW)
        if(effort.values.any {it.rir==null})
            return result(FatigueRepReviewReason.ACTUAL_EFFORT_NOT_REPORTED)
        if(policyVersion==AdaptiveRepPolicyVersion.TARGET_RIR_V2) {
            require(targetRirByOrdinal==null ||
                (targetRirByOrdinal.size==ordered.size &&
                    targetRirByOrdinal.all {it==null || it in 0..10}))
            val goals=targetRirByOrdinal ?: ordered.map {it.issuedRir}
            if(goals.any {it==null})
                return result(FatigueRepReviewReason.EFFORT_TARGET_MISSING)
            // Reps + reported reserve estimates capacity at THIS observed
            // set position/load; subtract its own goal reserve. This is not
            // a universal dose-response equation or a success probability.
            // Assumed effort supplies zero extra headroom when the goal is unchanged.
            val estimates=ordered.mapIndexed {i,it ->
                estimateTargetRirReps(checkNotNull(it.actualReps),
                    checkNotNull(effort.getValue(it.workingOrdinal).rir),
                    checkNotNull(goals[i]),facts.targetReps)
            }
            if(estimates.any {it==null})
                return result(FatigueRepReviewReason.EFFORT_REQUIRES_LOAD_REVIEW)
            val candidate=estimates.map {checkNotNull(it)}
            val changed=candidate.indices.filter {candidate[it]!=checkNotNull(reps)[it]}
            if(changed.isEmpty()) return result(
                if(ordered.all {it.actualReps==facts.targetReps.maximum})
                    FatigueRepReviewReason.ALREADY_AT_REP_CEILING
                else FatigueRepReviewReason.NO_DEMONSTRATED_EFFORT_HEADROOM)
            val limitations=baselineLimitations.toMutableSet()
            if(changed.any {it<ordered.lastIndex})
                limitations+=RepForecastLimitation.EARLIER_SET_WORK_CHANGED
            return result(FatigueRepReviewReason.TARGET_RIR_ALIGNED_REPS_FOR_REVIEW,
                candidate,changed.first(),limitations)
        }

        if(ordered.all {it.actualReps==facts.targetReps.maximum})
            return result(FatigueRepReviewReason.ALREADY_AT_REP_CEILING)

        // Frozen v1 receipt replay, NOT the policy for new recommendations.
        val eligible=ordered.filter {
            checkNotNull(it.actualReps)<facts.targetReps.maximum &&
                checkNotNull(effort.getValue(it.workingOrdinal).rir)>=3
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
