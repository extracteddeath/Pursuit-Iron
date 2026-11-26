package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import java.util.NavigableSet
import java.util.TreeSet

/**
 * Independent Kotlin training engine: physical loadability, not a progression
 * prescription. Never call this to silently round a lifter's logged value.
 *
 * No canonical-JS dependency, Android Context, storage or UI access.
 */
enum class LoadUnit { LB, KG }

enum class LoadBasis {
    BARBELL_TOTAL, DUMBBELL_PER_HAND, CABLE_STACK, MACHINE_STACK,
    SMITH_TOTAL, EXERCISE_OVERRIDE,
    /** Mass of ONE bell, including a two-handed swing or a matched-pair row. */
    KETTLEBELL_EACH,
    /** Plates added at the loaded end; excludes the bar and pivot/base mass. */
    LANDMINE_ADDED_PLATES,
    /** Counterweight displayed by a verified assisted pull-up/dip machine.
     * More is MORE assistance, never a stronger working lift. Append only
     * to preserve historical enum ordinal / serialized value compatibility. */
    ASSISTANCE_COUNTERWEIGHT;

    /** A larger displayed number is harder only for external resistance.
     * A counterweight offsets body mass; no unit-level conversion is implied. */
    val higherSettingIncreasesDifficulty:Boolean
        get()=this!=ASSISTANCE_COUNTERWEIGHT

    /** Positive = harder; negative = easier; zero = same numeric setting.
     * Compare only within the SAME independently verified implement and unit.
     * This is direction, not authorization, calibration or load validation. */
    fun compareDifficulty(candidate:BigDecimal,baseline:BigDecimal):Int =
        if(higherSettingIncreasesDifficulty)candidate.compareTo(baseline)
        else baseline.compareTo(candidate)

    /** Physical free bars count even when the logged convention excludes bar mass. */
    val countsTowardBarbellLimit:Boolean
        get()=this==BARBELL_TOTAL || this==LANDMINE_ADDED_PLATES

    /** Zero ADDED mass still leaves a real bar; other conventions stay unchanged. */
    val permitsZeroAddedLoad:Boolean
        get()=this==LANDMINE_ADDED_PLATES
}

sealed interface VerifiedLoadStock {
    val unit: LoadUnit
    val basis: LoadBasis

    /** Shared arithmetic only; each concrete implement keeps its load basis. */
    sealed interface PairedBar : VerifiedLoadStock {
        val barWeight: BigDecimal
        val pairedPlates: Map<BigDecimal, Int>
    }

    /** Exact, explicitly confirmed implement or stack values. */
    data class Totals(
        override val unit: LoadUnit,
        override val basis: LoadBasis,
        val loads: List<BigDecimal>
    ) : VerifiedLoadStock {
        init { require(basis != LoadBasis.BARBELL_TOTAL) }
    }

    /** Bar weight plus exactly symmetric pairs. Counts are pairs, not plates. */
    data class Barbell(
        override val unit: LoadUnit,
        override val barWeight: BigDecimal,
        override val pairedPlates: Map<BigDecimal, Int>
    ) : PairedBar {
        override val basis = LoadBasis.BARBELL_TOTAL
    }

    /**
     * Confirmed empty-bar resistance plus symmetric physical plate pairs.
     * Counterbalanced resistance may be zero. It is never inferred from a
     * free bar's mass or from an old list of Smith totals, and does not claim
     * force equivalence to another Smith machine or a free-weight exercise.
     */
    data class Smith(
        override val unit: LoadUnit,
        override val barWeight: BigDecimal,
        override val pairedPlates: Map<BigDecimal, Int>
    ) : PairedBar {
        override val basis = LoadBasis.SMITH_TOTAL
    }

    /** Confirmed machine/cable increments, never estimated from unit alone. */
    data class Stack(
        override val unit: LoadUnit,
        override val basis: LoadBasis,
        val minimum: BigDecimal,
        val increment: BigDecimal,
        val maximum: BigDecimal
    ) : VerifiedLoadStock {
        init {
            require(basis in setOf(LoadBasis.CABLE_STACK, LoadBasis.MACHINE_STACK,
                LoadBasis.EXERCISE_OVERRIDE,
                LoadBasis.ASSISTANCE_COUNTERWEIGHT))
        }
    }
}

enum class LoadUnavailableReason {
    NO_CONFIRMED_STOCK, UNIT_MISMATCH, INVALID_INPUT, INVALID_STOCK
}

sealed interface LoadInspection {
    data class Unavailable(val reason: LoadUnavailableReason) : LoadInspection

    /**
     * Informational only. 'atOrBelow' may be null if even the lightest
     * verified load is heavier than entered. Never imply it was applied.
     */
    data class Confirmed(
        val unit: LoadUnit,
        val basis: LoadBasis,
        val entered: BigDecimal,
        val exact: Boolean,
        val atOrBelow: BigDecimal?,
        val nextAbove: BigDecimal?,
        val nextBelow: BigDecimal?
    ) : LoadInspection {
        val nextHarder:BigDecimal?
            get()=if(basis.higherSettingIncreasesDifficulty)nextAbove else nextBelow
        val nextEasier:BigDecimal?
            get()=if(basis.higherSettingIncreasesDifficulty)nextBelow else nextAbove
    }
}

object EquipmentLoadDomain {
    private val zero = BigDecimal.ZERO
    private val two = BigDecimal("2")
    private const val MAX_DISTINCT_PLATES = 12
    private const val MAX_PAIRS_PER_SIZE = 24
    private const val MAX_ATTAINABLE_TOTALS = 100_000

    // BigDecimal compares numeric values ignoring scale, but Kotlin/JUnit's
    // equality does not. Canonical outputs avoid 115 vs 115.0 mismatches,
    // including scientific-notation values like 1E+3.
    private fun normalized(value: BigDecimal): BigDecimal =
        BigDecimal(value.stripTrailingZeros().toPlainString())

    // Physical numeric bounds belong to the one set-number authority.
    // Stock feasibility, exact neighbors and implement mechanics stay here.
    private fun validLoad(value: BigDecimal): Boolean =
        KotlinSetNumberAuthorityDomain.validPhysicalLoadValue(value)

    private fun validStep(value: BigDecimal): Boolean =
        value > zero && validLoad(value)

    /** A picker must expose at least one issuable working setting, not merely
     * a syntactically valid zero-only inventory. Same physical inspection
     * and prescribed-number authority for repetition and measured sessions. */
    fun hasPrescribableLoad(stock:VerifiedLoadStock,unit:LoadUnit):Boolean {
        val inspection=inspect(stock,zero,unit) as? LoadInspection.Confirmed
            ?:return false
        return inspection.basis==stock.basis &&
            ((inspection.exact &&
                KotlinSetNumberAuthorityDomain.validPrescribedLoad(zero,stock.basis)) ||
                inspection.nextAbove?.let {
                    KotlinSetNumberAuthorityDomain.validPrescribedLoad(it,stock.basis)
                }==true)
    }

    /** Explicitly no unit inference and no unknown-stock fallback. */
    fun inspect(
        stock: VerifiedLoadStock?,
        entered: BigDecimal,
        enteredUnit: LoadUnit
    ): LoadInspection {
        if (!validLoad(entered)) return LoadInspection.Unavailable(LoadUnavailableReason.INVALID_INPUT)
        if (stock == null) return LoadInspection.Unavailable(LoadUnavailableReason.NO_CONFIRMED_STOCK)
        if (stock.unit != enteredUnit) return LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH)

        if (stock is VerifiedLoadStock.Stack) return inspectStack(stock, normalized(entered))

        // Fail closed on impossible or attacker-sized inventory. A mutable
        // producer map/list is revalidated at each read before any result.
        val attainable = runCatching { attainable(stock) }.getOrElse {
            return LoadInspection.Unavailable(LoadUnavailableReason.INVALID_STOCK)
        }
        return LoadInspection.Confirmed(
            unit = enteredUnit, basis = stock.basis, entered = normalized(entered),
            exact = attainable.contains(entered),
            atOrBelow = attainable.floor(entered)?.let(::normalized),
            nextAbove = attainable.higher(entered)?.let(::normalized),
            nextBelow = attainable.lower(entered)?.let(::normalized)
        )
    }

    private fun inspectStack(stock: VerifiedLoadStock.Stack, entered: BigDecimal): LoadInspection {
        val invalid = LoadInspection.Unavailable(LoadUnavailableReason.INVALID_STOCK)
        if (!validLoad(stock.minimum) || !validLoad(stock.maximum) ||
            !validStep(stock.increment) || stock.maximum < stock.minimum) return invalid

        val minimum = normalized(stock.minimum)
        val maximum = normalized(stock.maximum)
        val step = normalized(stock.increment)
        val lastIndex = (maximum - minimum).divideToIntegralValue(step)
        if (lastIndex >= BigDecimal(MAX_ATTAINABLE_TOTALS)) return invalid

        // The declared ceiling need not be reachable by whole selector steps.
        val lower = if (entered < minimum) null else {
            val index = (entered - minimum).divideToIntegralValue(step).min(lastIndex)
            normalized(minimum + index * step)
        }
        val upper = if (lower == null) minimum else
            (lower + step).takeIf { it <= maximum }?.let(::normalized)
        val exact = lower != null && lower.compareTo(entered) == 0
        val below = if (exact) lower?.subtract(step)?.takeIf { it >= minimum } else lower
        return LoadInspection.Confirmed(stock.unit, stock.basis, entered,
            exact, lower, upper, below?.let(::normalized))
    }

    private fun attainable(stock: VerifiedLoadStock): NavigableSet<BigDecimal> = when(stock) {
        is VerifiedLoadStock.Totals -> {
            require(stock.loads.size in 1..MAX_ATTAINABLE_TOTALS &&
                stock.loads.all(::validLoad))
            if(stock.basis==LoadBasis.KETTLEBELL_EACH)
                require(stock.loads.all{it>zero})
            require(stock.loads.map { it.stripTrailingZeros() }.distinct().size ==
                stock.loads.size)
            TreeSet<BigDecimal>().apply { addAll(stock.loads) }
        }
        is VerifiedLoadStock.Stack -> error("Stack brackets use selector arithmetic")
        is VerifiedLoadStock.PairedBar -> {
            require(validLoad(stock.barWeight) && stock.pairedPlates.size in 1..MAX_DISTINCT_PLATES)
            val entries = stock.pairedPlates.entries.toList()
            require(entries.all { validStep(it.key) && it.value in 1..MAX_PAIRS_PER_SIZE })
            require(entries.map { it.key.stripTrailingZeros() }.distinct().size == entries.size)
            var sides = mutableSetOf(zero)
            for ((plate, pairs) in entries) {
                val nextSides = HashSet<BigDecimal>()
                for (existing in sides) {
                    for (n in 0..pairs) {
                        nextSides.add(existing.add(plate.multiply(BigDecimal(n))))
                        require(nextSides.size <= MAX_ATTAINABLE_TOTALS)
                    }
                }
                sides = nextSides
            }
            TreeSet<BigDecimal>().apply {
                for (side in sides) {
                    val total = stock.barWeight.add(side.multiply(two))
                    require(validLoad(total))
                    add(total)
                }
            }
        }
    }
}
