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
    SMITH_TOTAL, EXERCISE_OVERRIDE
}

sealed interface VerifiedLoadStock {
    val unit: LoadUnit
    val basis: LoadBasis

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
        val barWeight: BigDecimal,
        val pairedPlates: Map<BigDecimal, Int>
    ) : VerifiedLoadStock {
        override val basis = LoadBasis.BARBELL_TOTAL
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
                LoadBasis.EXERCISE_OVERRIDE))
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
        val nextAbove: BigDecimal?
    ) : LoadInspection
}

object EquipmentLoadDomain {
    private val zero = BigDecimal.ZERO
    private val two = BigDecimal("2")
    private val maxLoad = BigDecimal("100000")
    private const val MAX_DISTINCT_PLATES = 12
    private const val MAX_PAIRS_PER_SIZE = 24
    private const val MAX_ATTAINABLE_TOTALS = 100_000

    // BigDecimal compares numeric values ignoring scale, but Kotlin/JUnit's
    // equality does not. Canonical outputs avoid 115 vs 115.0 mismatches,
    // including scientific-notation values like 1E+3.
    private fun normalized(value: BigDecimal): BigDecimal =
        BigDecimal(value.stripTrailingZeros().toPlainString())

    private fun validLoad(value: BigDecimal): Boolean =
        value >= zero && value <= maxLoad &&
            value.stripTrailingZeros().scale() <= 3

    private fun validStep(value: BigDecimal): Boolean =
        value > zero && value <= maxLoad &&
            value.stripTrailingZeros().scale() <= 3

    /** Explicitly no unit inference and no unknown-stock fallback. */
    fun inspect(
        stock: VerifiedLoadStock?,
        entered: BigDecimal,
        enteredUnit: LoadUnit
    ): LoadInspection {
        if (!validLoad(entered)) return LoadInspection.Unavailable(LoadUnavailableReason.INVALID_INPUT)
        if (stock == null) return LoadInspection.Unavailable(LoadUnavailableReason.NO_CONFIRMED_STOCK)
        if (stock.unit != enteredUnit) return LoadInspection.Unavailable(LoadUnavailableReason.UNIT_MISMATCH)

        // Fail closed on impossible or attacker-sized inventory. A mutable
        // producer map/list is revalidated at each read before any result.
        val attainable = runCatching { attainable(stock) }.getOrElse {
            return LoadInspection.Unavailable(LoadUnavailableReason.INVALID_STOCK)
        }
        return LoadInspection.Confirmed(
            unit = enteredUnit, basis = stock.basis, entered = normalized(entered),
            exact = attainable.contains(entered),
            atOrBelow = attainable.floor(entered)?.let(::normalized),
            nextAbove = attainable.higher(entered)?.let(::normalized)
        )
    }

    private fun attainable(stock: VerifiedLoadStock): NavigableSet<BigDecimal> = when(stock) {
        is VerifiedLoadStock.Totals -> {
            require(stock.loads.size in 1..MAX_ATTAINABLE_TOTALS &&
                stock.loads.all(::validLoad))
            require(stock.loads.map { it.stripTrailingZeros() }.distinct().size ==
                stock.loads.size)
            TreeSet<BigDecimal>().apply { addAll(stock.loads) }
        }
        is VerifiedLoadStock.Stack -> {
            require(validLoad(stock.minimum) && validLoad(stock.maximum) &&
                validStep(stock.increment) && stock.maximum >= stock.minimum)
            val range = stock.maximum.subtract(stock.minimum)
            val count = range.divideToIntegralValue(stock.increment).toInt()
            require(count in 0 until MAX_ATTAINABLE_TOTALS)
            TreeSet<BigDecimal>().apply {
                for (step in 0..count)
                    add(stock.minimum.add(stock.increment.multiply(BigDecimal(step))))
            }
        }
        is VerifiedLoadStock.Barbell -> {
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
