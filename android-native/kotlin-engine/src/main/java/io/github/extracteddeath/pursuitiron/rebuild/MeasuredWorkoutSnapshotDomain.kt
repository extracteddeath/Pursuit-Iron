package io.github.extracteddeath.pursuitiron.rebuild

import java.util.Collections

/**
 * Take ownership of measured prescriptions and observations at the shared
 * journal boundary. Kotlin read-only collection types alone do not prevent
 * an editor from mutating a list it passed in after approval or recording.
 * BigDecimal and the scalar identities are immutable; collection operands
 * need independent, unmodifiable copies before validation and retention.
 */
internal object MeasuredWorkoutSnapshotDomain {
    private fun <T> list(values:List<T>):List<T> =
        Collections.unmodifiableList(ArrayList(values))
    private fun <T> set(values:Set<T>):Set<T> =
        Collections.unmodifiableSet(LinkedHashSet(values))

    fun plan(input:MeasuredExerciseSetPlan):MeasuredExerciseSetPlan = input.copy(
        sides=set(input.sides),
        confirmedFixtures=set(input.confirmedFixtures),
        gymConfirmedBandIds=set(input.gymConfirmedBandIds),
        physicalStock=when(val stock=input.physicalStock) {
            is VerifiedLoadStock.Totals -> stock.copy(loads=list(stock.loads))
            is VerifiedLoadStock.Barbell -> stock.copy(pairedPlates=
                Collections.unmodifiableMap(LinkedHashMap(stock.pairedPlates)))
            is VerifiedLoadStock.Smith -> stock.copy(pairedPlates=
                Collections.unmodifiableMap(LinkedHashMap(stock.pairedPlates)))
            is VerifiedLoadStock.Stack -> stock
            null -> null
        })

    fun actual(input:MeasuredExerciseActual?):MeasuredExerciseActual? =
        input?.copy(sides=list(input.sides))

    fun operation(input:MeasuredJournalOperation):MeasuredJournalOperation =
        when(input) {
            is MeasuredJournalOperation.Record -> input.copy(actual=actual(input.actual))
            is MeasuredJournalOperation.Finish -> input
        }

    fun operation(input:MeasuredWorkoutOperation):MeasuredWorkoutOperation =
        when(input) {
            is MeasuredWorkoutOperation.Record -> input.copy(actual=actual(input.actual))
            is MeasuredWorkoutOperation.Finish -> input
        }
}
