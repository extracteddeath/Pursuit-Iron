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
    PER_SET_DYNAMIC_PROGRESSION,
    LINEAR_LOAD_PROGRESSION,
    REP_TARGET_PROGRESSION,
    /** Rep ladder earns a load step at the ceiling, then resets to its floor. */
    REP_LADDER_PROGRESSION,
    ASSISTANCE_DOUBLE_PROGRESSION
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
    val maximumAuthorizedIncrement: BigDecimal,
    /** Approved exact set targets, required for REP_TARGET_PROGRESSION only. */
    val issuedRepTargets: List<Int>? = null
)

enum class BasicProgressionRefusal {
    INVALID_SOURCE_OR_LOAD,
    HISTORY_NOT_COMPARABLE,
    INCOMPLETE_OR_UNVERIFIED_SETS,
    BASELINE_DIFFERS_FROM_HISTORY,
    UNSUPPORTED_STRAIGHT_SET_SCHEME,
    STOCK_NOT_VERIFIED,
    UNSUPPORTED_ASSISTANCE_DIRECTION,
    REP_TARGETS_NOT_VERIFIED
}

enum class BasicProgressionDecision {
    MANUAL_REPEAT,
    BUILD_REPS,
    EARNED_INCREMENT,
    MAXIMUM_LOAD_REACHED,
    AUTHORIZED_INCREMENT_TOO_SMALL,
    EARNED_REP_TARGET
}

sealed interface BasicProgressionResult {
    data class Refused(val reason: BasicProgressionRefusal): BasicProgressionResult
    data class Candidate(
        val strategy: BasicProgressionStrategy,
        val proposedWorkingLoads: List<BigDecimal>,
        /** Zero-based set ordinals whose *future* load may be raised. */
        val increasedOrdinals: Set<Int>,
        val decision: BasicProgressionDecision,
        val evidenceWorkoutId: String?,
        val proposedRepTargets: List<Int>? = null,
        val changedRepOrdinals: Set<Int> = emptySet(),
        /** Independent load decisions, in working-set order, for dynamic progression. */
        val dynamicSetDecisions: List<BasicProgressionDecision> = emptyList()
    ): BasicProgressionResult {
        val mayModifyLoggedSets: Boolean get() = false
        val mayModifyIssuedWorkout: Boolean get() = false
        val maySaveFutureProgram: Boolean get() = false
    }
}

/**
 * One deterministic evaluator for basic weighted straight-working-set strategies.
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
            decision: BasicProgressionDecision, workoutId: String? = null,
            repTargets: List<Int>? = null, changedReps: Set<Int> = emptySet(),
            dynamicDecisions: List<BasicProgressionDecision> = emptyList()) =
            BasicProgressionResult.Candidate(
                request.strategy,
                Collections.unmodifiableList(values.toList()),
                Collections.unmodifiableSet(changed.toSet()),decision,workoutId,
                repTargets?.let { Collections.unmodifiableList(it.toList()) },
                Collections.unmodifiableSet(changedReps.toSet()),
                Collections.unmodifiableList(dynamicDecisions.toList()))

        // Basic logging deliberately neither requires a history or a gym
        // inventory, nor invokes progression or adaptive coaching.
        if (request.strategy == BasicProgressionStrategy.MANUAL_LOGGING)
            return candidate(loads,emptySet(),BasicProgressionDecision.MANUAL_REPEAT)

        if (request.strategy != BasicProgressionStrategy.REP_TARGET_PROGRESSION &&
            (request.maximumAuthorizedIncrement <= zero ||
                !KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(
                    request.maximumAuthorizedIncrement)))
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

        val uniformStrategy = request.strategy in setOf(
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION,
            BasicProgressionStrategy.REP_LADDER_PROGRESSION,
            BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION)
        if (uniformStrategy && loads.any { it.compareTo(loads.first()) != 0 })
            return reject(BasicProgressionRefusal.UNSUPPORTED_STRAIGHT_SET_SCHEME)

        val assistance = request.nextScope.basis == LoadBasis.ASSISTANCE_COUNTERWEIGHT
        if (request.strategy != BasicProgressionStrategy.REP_TARGET_PROGRESSION &&
            assistance !=
            (request.strategy == BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION))
            return reject(BasicProgressionRefusal.UNSUPPORTED_ASSISTANCE_DIRECTION)

        // A rep-only policy does not need invented stock increments. Its
        // authenticated exact issued targets are independent of logged
        // achievement; no reported RIR or fatigue forecast is inferred.
        val ladder=request.strategy==BasicProgressionStrategy.REP_LADDER_PROGRESSION
        if (request.strategy == BasicProgressionStrategy.REP_TARGET_PROGRESSION ||
            ladder) {
            val targets = request.issuedRepTargets
                ?: return reject(BasicProgressionRefusal.REP_TARGETS_NOT_VERIFIED)
            val proposed = BasicRepTargetStepDomain.advance(
                targets,facts.map {it.actualReps!!},request.repBand)
                ?:return reject(BasicProgressionRefusal.REP_TARGETS_NOT_VERIFIED)
            val changed = targets.indices.filter { proposed[it] != targets[it] }.toSet()
            // Rep ladder remains at the same physical weight until EVERY
            // completed set reaches its rep ceiling. Rep-only NEVER changes
            // load, even after reaching the ceiling.
            if(!ladder || facts.any{it.actualReps!!<request.repBand.maximum})
                return candidate(loads, emptySet(),
                    if (changed.isEmpty()) BasicProgressionDecision.BUILD_REPS
                    else BasicProgressionDecision.EARNED_REP_TARGET,
                    previous.workoutId, proposed, changed)
        }

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

        val ceilingOrdinals = facts.indices.filter {
            facts[it].actualReps!! >= request.repBand.maximum
        }
        val floorOrdinals = facts.indices.filter {
            facts[it].actualReps!! >= request.repBand.minimum
        }
        val eligible = when(request.strategy) {
            BasicProgressionStrategy.STRICT_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.ASSISTANCE_DOUBLE_PROGRESSION,
            BasicProgressionStrategy.REP_LADDER_PROGRESSION ->
                if (ceilingOrdinals.size == facts.size) facts.indices.toList() else emptyList()
            BasicProgressionStrategy.LINEAR_LOAD_PROGRESSION ->
                if (floorOrdinals.size == facts.size) facts.indices.toList() else emptyList()
            BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION -> ceilingOrdinals
            BasicProgressionStrategy.MANUAL_LOGGING,
            BasicProgressionStrategy.REP_TARGET_PROGRESSION -> emptyList()
        }
        val dynamicDecisions = if (request.strategy == BasicProgressionStrategy.PER_SET_DYNAMIC_PROGRESSION)
            MutableList(loads.size) { BasicProgressionDecision.BUILD_REPS } else null
        if (eligible.isEmpty())
            return candidate(loads,emptySet(),BasicProgressionDecision.BUILD_REPS,
                previous.workoutId, dynamicDecisions = dynamicDecisions.orEmpty())

        // Uniform schemes are atomic: all sets change or none change.
        // Dynamic works on each stable set ordinal independently.
        val proposals = loads.toMutableList()
        val changed = linkedSetOf<Int>()
        var blockedByLimit = false
        var atMaximum = false
        for (ordinal in eligible) {
            val next = if (assistance) {
                // Lower counterweight means LESS assistance, not a load drop.
                inspected[ordinal].nextBelow
            } else inspected[ordinal].nextAbove
            if (next == null) {
                dynamicDecisions?.set(ordinal, BasicProgressionDecision.MAXIMUM_LOAD_REACHED)
                atMaximum = true
                if (uniformStrategy) break
                continue
            }
            val verified = KotlinFutureLoadNumberAuthorityDomain.review(
                issuedLoad=loads[ordinal],candidateLoad=next,
                unit=request.nextScope.unit,basis=request.nextScope.basis,
                stock=stock,maximumAuthorizedIncrease=request.maximumAuthorizedIncrement,
                source=if(assistance)KotlinFutureLoadSource.EARNED_ASSISTANCE_DECREMENT
                    else KotlinFutureLoadSource.EARNED_NEXT_INCREMENT)
            if (verified is KotlinFutureLoadReview.Rejected) {
                if (verified.reason == KotlinFutureLoadRefusal.NO_LOWER_ASSISTED_SETTING) {
                    atMaximum = true
                    break
                }
                if (verified.reason != KotlinFutureLoadRefusal.EXCEEDS_AUTHORIZED_INCREMENT)
                    return reject(BasicProgressionRefusal.STOCK_NOT_VERIFIED)
                dynamicDecisions?.set(ordinal, BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL)
                blockedByLimit = true
                if (uniformStrategy) break
                continue
            }
            val accepted = verified as KotlinFutureLoadReview.Accepted
            dynamicDecisions?.set(ordinal, BasicProgressionDecision.EARNED_INCREMENT)
            proposals[ordinal] = accepted.fact.proposedLoad
            changed.add(ordinal)
        }
        if (uniformStrategy && changed.size != facts.size)
            return candidate(loads,emptySet(),
                if(atMaximum)BasicProgressionDecision.MAXIMUM_LOAD_REACHED
                else BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL,
                previous.workoutId)

        val ladderReset=if(ladder && changed.size==facts.size)
            List(facts.size){request.repBand.minimum} else null
        return candidate(proposals,changed,
            when {
                changed.isNotEmpty() -> BasicProgressionDecision.EARNED_INCREMENT
                atMaximum -> BasicProgressionDecision.MAXIMUM_LOAD_REACHED
                blockedByLimit -> BasicProgressionDecision.AUTHORIZED_INCREMENT_TOO_SMALL
                else -> BasicProgressionDecision.BUILD_REPS
            },previous.workoutId,
            ladderReset, if(ladderReset==null)emptySet()
                else ladderReset.indices.filter{
                    ladderReset[it]!=request.issuedRepTargets!![it]
                }.toSet(), dynamicDecisions.orEmpty())
    }
}
