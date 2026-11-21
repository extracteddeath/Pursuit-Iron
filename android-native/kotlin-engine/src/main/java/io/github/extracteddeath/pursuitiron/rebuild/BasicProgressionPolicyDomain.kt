package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.Collections

/**
 * Pure, opt-in progression FOUNDATION for weighted, ordinary working sets.
 *
 * Logging is always authoritative. A recommendation never alters a completed
 * set, an issued prescription, or a future program. Coaching is a separate
 * consumer, not another writer of these weights. No readiness, estimated RIR,
 * fatigue or predicted reps participate in these baseline decisions.
 *
 * The native owner MUST construct both scopes from authenticated history and
 * the approved future day, not from editable screen labels. Warmups, backoffs,
 * supersets with unusual load rules, bodyweight and amplifier follow-ups are
 * not eligible inputs. Their existing specialist paths remain authoritative.
 */
enum class BasicProgressionStrategy {
    MANUAL_LOGGING,
    STRICT_DOUBLE_PROGRESSION,
    PER_SET_DYNAMIC_PROGRESSION
}

/** A physical machine, program day and block have independent load histories. */
data class BasicProgressionScope(
    val athleteId: String,
    val programId: String,
    val gymId: String,
    val blockId: String,
    val trainingDayId: String,
    val exerciseId: String,
    val machineInstanceId: String?,
    val unit: LoadUnit,
    val basis: LoadBasis
) {
    fun valid(): Boolean =
        athleteId.isNotBlank() && programId.isNotBlank() &&
        gymId.isNotBlank() &&
        blockId.isNotBlank() && trainingDayId.isNotBlank() &&
        exerciseId.isNotBlank() &&
        (machineInstanceId == null ||
            KotlinMachineAlternativesDomain.validInstanceId(machineInstanceId))
}

data class BasicProgressionRequest(
    val strategy: BasicProgressionStrategy,
    val nextWorkoutId: String,
    val nextScope: BasicProgressionScope,
    val previousScope: BasicProgressionScope?,
    val previous: ProgressionWorkoutEvidence?,
    val repBand: RepBand,
    /** Current immutable, approved weights, not values from an editable UI. */
    val issuedWorkingLoads: List<BigDecimal>,
    val verifiedStock: VerifiedLoadStock?,
    val maximumAuthorizedIncrement: BigDecimal
)

enum class BasicProgressionRefusal {
    INVALID_SOURCE_OR_LOAD,
    HISTORY_NOT_COMPARABLE,
    INCOMPLETE_OR_UNVERIFIED_SETS,
    BASELINE_DIFFERS_FROM_HISTORY,
    UNSUPPORTED_STRAIGHT_SET_SCHEME,
    STOCK_NOT_VERIFIED,
    UNSUPPORTED_ASSISTANCE_DIRECTION
}

enum class BasicProgressionDecision {
    MANUAL_REPEAT,
    BUILD_REPS,
    EARNED_INCREMENT,
    MAXIMUM_LOAD_REACHED,
    AUTHORIZED_INCREMENT_TOO_SMALL
}

sealed interface BasicProgressionResult {
    data class Refused(val reason: BasicProgressionRefusal): BasicProgressionResult
    data class Candidate(
        val strategy: BasicProgressionStrategy,
        val proposedWorkingLoads: List<BigDecimal>,
        /** Zero-based set ordinals whose *future* load may be raised. */
        val increasedOrdinals: Set<Int>,
        val decision: BasicProgressionDecision,
        val evidenceWorkoutId: String?
    ): BasicProgressionResult {
        val mayModifyLoggedSets: Boolean get() = false
        val mayModifyIssuedWorkout: Boolean get() = false
        val maySaveFutureProgram: Boolean get() = false
    }
}

/**
 * One deterministic evaluator for BOTH basic progression strategies.
 * The only authority for a numeric increase is KotlinFutureLoadNumberAuthorityDomain,
 * using the exact next attainable load of the recorded implement.
 */
object BasicProgressionPolicyDomain {
    private val zero = BigDecimal.ZERO

    fun review(request: BasicProgressionRequest): BasicProgressionResult {
        fun reject(reason: BasicProgressionRefusal) = BasicProgressionResult.Refused(reason)
        val loads = request.issuedWorkingLoads
        if (request.nextWorkoutId.isBlank() || !request.nextScope.valid() ||
            loads.size !in 1..32 || loads.any {
                !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(it) ||
                (it.signum() == 0 && !request.nextScope.basis.permitsZeroAddedLoad)
            })
            return reject(BasicProgressionRefusal.INVALID_SOURCE_OR_LOAD)

        fun candidate(values: List<BigDecimal>, changed: Set<Int>,
            decision: BasicProgressionDecision, workoutId: String? = null) =
            BasicProgressionResult.Candidate(
                request.strategy,
                Collections.unmodifiableList(values.toList()),
                Collections.unmodifiableSet(changed.toSet()),decision,workoutId)

        // Basic logging deliberately neither requires a history or a gym
        // inventory, nor invokes progression or adaptive coaching.
        if (request.strategy == BasicProgressionStrategy.MANUAL_LOGGING)
            return candidate(loads,emptySet(),BasicProgressionDecision.MANUAL_REPEAT)

        if (request.maximumAuthorizedIncrement <= zero ||
            !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(
                request.maximumAuthorizedIncrement))
            return reject(BasicProgressionRefusal.INVALID_SOURCE_OR_LOAD)

        val previous = request.previous
            ?: return reject(BasicProgressionRefusal.HISTORY_NOT_COMPARABLE)
        if (request.previousScope != request.nextScope ||
            previous.workoutId.isBlank() ||
            previous.workoutId == request.nextWorkoutId ||
            previous.exerciseId != request.nextScope.exerciseId ||
            previous.trainingDayId != request.nextScope.trainingDayId ||
            previous.unit != request.nextScope.unit ||
            previous.targetReps != request.repBand ||
            previous.expectedWorkingSets != loads.size)
            return reject(BasicProgressionRefusal.HISTORY_NOT_COMPARABLE)

        val facts = previous.observations.sortedBy { it.workingOrdinal }
        if (facts.size != loads.size ||
            facts.map { it.workingOrdinal } != loads.indices.toList() ||
            facts.any { it.outcome != SetOutcome.COMPLETED ||
                it.actualReps == null || it.actualReps !in 1..100 ||
                it.actualLoad == null || it.issuedLoad == null ||
                !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(it.actualLoad) ||
                !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(it.issuedLoad) ||
                (it.actualRir != null && it.actualRir !in 0..10)
            })
            return reject(BasicProgressionRefusal.INCOMPLETE_OR_UNVERIFIED_SETS)

        // Never silently turn a deliberate manually lowered load into a new
        // prescription, or borrow a previous set's heavy load for every set.
        if (facts.indices.any { i ->
                facts[i].issuedLoad!!.compareTo(facts[i].actualLoad!!) != 0 ||
                    loads[i].compareTo(facts[i].actualLoad!!) != 0
            })
            return reject(BasicProgressionRefusal.BASELINE_DIFFERS_FROM_HISTORY)

        if (request.strategy == BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION &&
            loads.any { it.compareTo(loads.first()) != 0 })
            return reject(BasicProgressionRefusal.UNSUPPORTED_STRAIGHT_SET_SCHEME)

        if (request.nextScope.basis == LoadBasis.ASSISTANCE_COUNTERWEIGHT)
            return reject(BasicProgressionRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION)

        val stock = request.verifiedStock
        if (stock == null || stock.unit != request.nextScope.unit ||
            stock.basis != request.nextScope.basis)
            return reject(BasicProgressionRefusal.STOCK_NOT_VERIFIED)
        val inspected = loads.map {
            EquipmentLoadDomain.inspect(stock,it,request.nextScope.unit)
                as? LoadInspection.Confirmed
                ?: return reject(BasicProgressionRefusal.STOCK_NOT_VERIFIED)
        }
        if (inspected.any { !it.exact })
            return reject(BasicProgressionRefusal.STOCK_NOT_VERIFIED)

        val qualified = facts.indices.filter {
            facts[it].actualReps!! >= request.repBand.maximum
        }
        val eligible = when(request.strategy) {
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION ->
                if (qualified.size == facts.size) facts.indices.toList() else emptyList()
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION -> qualified
            BasicProgressionStrategy.MANUAL_LOGGING -> emptyList()
        }
        if (eligible.isEmpty())
            return candidate(loads,emptySet(),BasicProgressionDecision.BUILD_REPS,
                previous.workoutId)

        // Atomic strict DP: either ALL straight working sets earn the same
        // verified step, or the whole load remains exactly unchanged.
        val proposals = loads.toMutableList()
        val changed = linkedSetOf<Int>()
        var blockedByLimit = false
        var atMaximum = false
        for (ordinal in eligible) {
            val next = inspected[ordinal].nextAbove
            if (next == null) {
                atMaximum = true
                if (request.strategy == BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION)
                    break
                continue
            }
            val verified = KotlinFutureLoadNumberAuthorityDomain.review(
                issuedLoad=loads[ordinal],candidateLoad=next,
                unit=request.nextScope.unit,basis=request.nextScope.basis,
                stock=stock,maximumAuthorizedIncrease=request.maximumAuthorizedIncrement,
                source=KotlinFutureLoadSource.EARNED_NEXT_INCREMENT)
            if (verified !is KotlinFutureLoadReview.Accepted) {
                blockedByLimit = true
                if (request.strategy == BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION)
                    break
                continue
            }
            proposals[ordinal] = verified.fact.proposedLoad
            changed.add(ordinal)
        }
        if (request.strategy == BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION &&
            changed.size != facts.size)
            return candidate(loads,emptySet(),
                if(atMaximum)BasicProgressionDecision.MAXIMUM_LOAD_REACHED
                else BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL,
                previous.workoutId)

        return candidate(proposals,changed,
            when {
                changed.isNotEmpty() -> BasicProgressionDecision.EARNED_INCREMENT
                atMaximum -> BasicProgressionDecision.MAXIMUM_LOAD_REACHED
                blockedByLimit -> BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL
                else -> BasicProgressionDecision.BUILD_REPS
            },previous.workoutId)
    }
}
