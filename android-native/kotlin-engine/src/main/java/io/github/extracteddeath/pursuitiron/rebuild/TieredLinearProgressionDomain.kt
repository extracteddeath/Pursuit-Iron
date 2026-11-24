package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * GZCLP history-replayed tier progression. The caller must supply the complete
 * corrected ledger for the authenticated slot/station and signed protocol
 * snapshots. This is a read-only numeric candidate; accepting it requires the
 * same owner, source revision and future-workout fences as ordinary progression.
 */
data class TieredLinearState(
    val stage: Int,
    val load: BigDecimal
)
data class TieredLinearSetTarget(
    val ordinal: Int,
    val load: BigDecimal,
    val minimumReps: Int,
    val repOut: Boolean
)
data class TieredLinearExposure(
    val workoutId: String,
    val completionOrder: Long,
    val scope: BasicProgressionScope,
    val tier: PercentageTier,
    val issuedStage: Int,
    val issuedTargets: List<TieredLinearSetTarget>,
    val observations: List<WorkingSetFact>,
    /** Shortened, substituted, recovery or interrupted exposure is not a
     * normal tier attempt. Preserve it as history, not progression evidence. */
    val comparableTraining: Boolean,
    val sourceSlotId: String? = null
)
data class TieredLinearRequest(
    val nextWorkoutId: String,
    val scope: BasicProgressionScope,
    val tier: PercentageTier,
    val initialState: TieredLinearState,
    val initialStateConfirmed: Boolean,
    val completed: List<TieredLinearExposure>,
    val verifiedStock: VerifiedLoadStock?,
    val maximumAuthorizedIncrease: BigDecimal,
    val approvedSetBudget: Int,
    val recovery: Boolean = false,
    val sourceSlotId: String? = null
)
enum class TieredLinearRefusal {
    INVALID_SOURCE, INVALID_INITIAL_STATE, INVALID_LEDGER,
    NONCOMPARABLE_LATEST, INCONSISTENT_ISSUED_STATE,
    INVALID_ATTEMPT, UNVERIFIED_STOCK, NO_SAFE_RESET_OR_RECOVERY
}
enum class TieredLinearDecision {
    INITIAL, EARNED_INCREMENT, HOLD_AT_STOCK_OR_LIMIT, NEXT_STAGE, RESET_REVIEW
}
data class TieredLinearTransition(
    val workoutId: String,
    val previous: TieredLinearState,
    val proposed: TieredLinearState,
    val decision: TieredLinearDecision,
    val earnedLoadFact: KotlinFutureLoadFact? = null
)
sealed interface TieredLinearResult {
    data class Refused(val reason: TieredLinearRefusal): TieredLinearResult
    data class Candidate(
        val state: TieredLinearState,
        val targets: List<TieredLinearSetTarget>,
        val sourceSetCount: Int,
        val transitions: List<TieredLinearTransition>,
        val recovery: Boolean
    ): TieredLinearResult {
        val maySaveProgram: Boolean get() = false
        val mayIssueWorkout: Boolean get() = false
        val mayModifyHistory: Boolean get() = false
        val requiresReviewedReset: Boolean get() =
            transitions.any { it.decision==TieredLinearDecision.RESET_REVIEW }
    }
}

object TieredLinearProgressionDomain {
    private val resetFactor=BigDecimal("0.85")
    private val recoveryFactor=BigDecimal("0.60")
    private fun normalized(value:BigDecimal)=BigDecimal(value.stripTrailingZeros().toPlainString())

    private fun stageReps(tier:PercentageTier,stage:Int):Int =
        if(tier==PercentageTier.PRIMARY)listOf(3,2,1)[stage]
        else listOf(10,8,6)[stage]
    private fun stageCount(tier:PercentageTier,stage:Int):Int =
        if(tier==PercentageTier.PRIMARY)listOf(5,6,10)[stage] else 3

    // All source sets are same-load, same-rep; set-budget adaptation always
    // retains the final rep-out. Never count warmups or amplifier extensions.
    private fun targets(
        tier:PercentageTier,state:TieredLinearState,count:Int,recovery:Boolean=false
    ):List<TieredLinearSetTarget> = List(count){i->
        TieredLinearSetTarget(i,state.load,stageReps(tier,state.stage),
            !recovery && i==count-1)
    }

    fun review(request:TieredLinearRequest):TieredLinearResult {
        fun no(reason:TieredLinearRefusal)=TieredLinearResult.Refused(reason)
        if(request.nextWorkoutId.isBlank() || !request.scope.valid() ||
            request.approvedSetBudget !in 1..20 ||
            request.maximumAuthorizedIncrease<=BigDecimal.ZERO ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(request.maximumAuthorizedIncrease) ||
            request.scope.basis==LoadBasis.ASSISTANCE_COUNTERWEIGHT ||
            KotlinSetNumberAuthorityDomain.machineTagContradictsFreeBarbell(
                request.scope.basis,request.scope.machineInstanceId))
            return no(TieredLinearRefusal.INVALID_SOURCE)
        val stock=request.verifiedStock ?:return no(TieredLinearRefusal.UNVERIFIED_STOCK)
        if(stock.unit!=request.scope.unit || stock.basis!=request.scope.basis)
            return no(TieredLinearRefusal.UNVERIFIED_STOCK)
        fun exact(load:BigDecimal):LoadInspection.Confirmed? {
            if(!KotlinSetNumberAuthorityDomain.validPrescribedLoad(load,request.scope.basis) ||
                load<=BigDecimal.ZERO)return null
            return (EquipmentLoadDomain.inspect(stock,load,request.scope.unit)
                as? LoadInspection.Confirmed)?.takeIf{it.exact}
        }
        if(!request.initialStateConfirmed || request.initialState.stage !in 0..2 ||
            exact(request.initialState.load)==null)
            return no(TieredLinearRefusal.INVALID_INITIAL_STATE)
        val ledger=request.completed
        if(ledger.map{it.completionOrder}!=ledger.map{it.completionOrder}.distinct().sorted() ||
            ledger.any{it.completionOrder<=0 || it.workoutId.isBlank() ||
                it.workoutId==request.nextWorkoutId || !it.scope.valid()} ||
            ledger.map{it.workoutId}.distinct().size!=ledger.size)
            return no(TieredLinearRefusal.INVALID_LEDGER)

        var state=request.initialState.copy(load=normalized(request.initialState.load))
        val transitions=ArrayList<TieredLinearTransition>()
        // A different physical station/day/tier is not evidence for this slot.
        // A newer noncomparable session on this scope blocks fallback.
        val sameScope=ledger.filter{it.scope==request.scope && it.tier==request.tier}
        if(request.sourceSlotId!=null && (request.sourceSlotId.isBlank() ||
            sameScope.any{it.sourceSlotId.isNullOrBlank()}))
            return no(TieredLinearRefusal.INVALID_LEDGER)
        val comparable=if(request.sourceSlotId==null) sameScope
            else sameScope.filter{it.sourceSlotId==request.sourceSlotId}
        if(comparable.lastOrNull()?.comparableTraining==false)
            return no(TieredLinearRefusal.NONCOMPARABLE_LATEST)
        for(exposure in comparable) {
            if(!exposure.comparableTraining)continue
            val issued=exposure.issuedTargets
            val facts=exposure.observations.sortedBy{it.workingOrdinal}
            if(exposure.issuedStage!=state.stage || issued.size !in 1..20 ||
                issued.map{it.ordinal}!=issued.indices.toList() ||
                issued.any{it.load.compareTo(state.load)!=0 ||
                    it.minimumReps!=stageReps(request.tier,state.stage)} ||
                issued.map{it.repOut}!=issued.indices.map{it==issued.lastIndex})
                return no(TieredLinearRefusal.INCONSISTENT_ISSUED_STATE)
            if(facts.size!=issued.size || facts.map{it.workingOrdinal}!=issued.indices.toList() ||
                facts.any{fact ->
                    fact.issuedLoad?.compareTo(state.load)!=0 ||
                    fact.actualLoad?.compareTo(state.load)!=0 ||
                    (fact.actualRir!=null && fact.actualRir !in 0..10) ||
                    (fact.issuedRir!=null && fact.issuedRir !in 0..10) ||
                    when(fact.outcome) {
                        SetOutcome.COMPLETED -> fact.actualReps==null || fact.actualReps !in 1..100
                        SetOutcome.FAILED_ATTEMPT -> fact.actualReps!=0 || fact.actualRir!=null
                        SetOutcome.SKIPPED -> true
                    }
                })
                return no(TieredLinearRefusal.INVALID_ATTEMPT)
            val prior=state
            var proof:KotlinFutureLoadFact?=null
            val passed=facts.indices.all{i->
                facts[i].outcome==SetOutcome.COMPLETED &&
                    facts[i].actualReps!!>=issued[i].minimumReps
            }
            val decision:TieredLinearDecision
            if(passed) {
                val next=exact(state.load)?.nextHarder
                if(next==null) {
                    decision=TieredLinearDecision.HOLD_AT_STOCK_OR_LIMIT
                } else {
                    when(val reviewed=KotlinFutureLoadNumberAuthorityDomain.review(
                        state.load,next,request.scope.unit,request.scope.basis,stock,
                        request.maximumAuthorizedIncrease,KotlinFutureLoadSource.EARNED_NEXT_INCREMENT)) {
                        is KotlinFutureLoadReview.Accepted -> {
                            proof=reviewed.fact
                            state=state.copy(load=proof.proposedLoad)
                            decision=TieredLinearDecision.EARNED_INCREMENT
                        }
                        is KotlinFutureLoadReview.Rejected -> {
                            if(reviewed.reason!=KotlinFutureLoadRefusal.EXCEEDS_AUTHORIZED_INCREMENT)
                                return no(TieredLinearRefusal.UNVERIFIED_STOCK)
                            decision=TieredLinearDecision.HOLD_AT_STOCK_OR_LIMIT
                        }
                    }
                }
            } else if(state.stage<2) {
                state=state.copy(stage=state.stage+1)
                decision=TieredLinearDecision.NEXT_STAGE
            } else {
                // Explicit named protocol reset, not an ordinary earned-load
                // reduction. Require exact stock strictly below the old load;
                // owner must explicitly approve before any future issuance.
                val reset=BasicProgrammedPercentageLoadDomain.verifiedPhysicalLoad(
                    request.scope,stock,state.load.multiply(resetFactor))
                    as? ProgrammedPhysicalLoadReview.Confirmed
                    ?:return no(TieredLinearRefusal.NO_SAFE_RESET_OR_RECOVERY)
                if(reset.value<=BigDecimal.ZERO || reset.value>=state.load)
                    return no(TieredLinearRefusal.NO_SAFE_RESET_OR_RECOVERY)
                state=TieredLinearState(0,reset.value)
                decision=TieredLinearDecision.RESET_REVIEW
            }
            transitions+=TieredLinearTransition(exposure.workoutId,prior,state,decision,proof)
        }
        val issuedState=if(request.recovery) {
            val light=BasicProgrammedPercentageLoadDomain.verifiedPhysicalLoad(
                request.scope,stock,state.load.multiply(recoveryFactor))
                as? ProgrammedPhysicalLoadReview.Confirmed
                ?:return no(TieredLinearRefusal.NO_SAFE_RESET_OR_RECOVERY)
            if(light.value<=BigDecimal.ZERO || light.value>=state.load)
                return no(TieredLinearRefusal.NO_SAFE_RESET_OR_RECOVERY)
            state.copy(load=light.value)
        } else state
        return TieredLinearResult.Candidate(state,
            Collections.unmodifiableList(targets(request.tier,issuedState,
                request.approvedSetBudget,request.recovery)),
            stageCount(request.tier,state.stage),
            Collections.unmodifiableList(transitions.toList()),request.recovery)
    }
}
