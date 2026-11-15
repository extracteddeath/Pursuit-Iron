package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.LoadBasis

/** Consistent physical units across set entry, review and History. */
internal fun kotlinLoadBasisLabel(basis:LoadBasis?):String=when(basis) {
    LoadBasis.DUMBBELL_PER_HAND -> "per hand"
    LoadBasis.KETTLEBELL_EACH -> "per kettlebell"
    LoadBasis.BARBELL_TOTAL -> "total barbell weight"
    LoadBasis.SMITH_TOTAL -> "total Smith weight"
    LoadBasis.CABLE_STACK -> "cable stack"
    LoadBasis.MACHINE_STACK -> "machine stack"
    LoadBasis.EXERCISE_OVERRIDE -> "exercise load"
    null -> "bodyweight"
}

internal fun kotlinLoadBasisSuffix(basis:LoadBasis?):String=when(basis) {
    LoadBasis.DUMBBELL_PER_HAND -> " per hand"
    LoadBasis.KETTLEBELL_EACH -> " per kettlebell"
    else -> ""
}
