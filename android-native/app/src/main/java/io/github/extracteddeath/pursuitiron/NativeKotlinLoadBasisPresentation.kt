package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.LoadBasis
import io.github.extracteddeath.pursuitiron.rebuild.DraftSetLoad
import io.github.extracteddeath.pursuitiron.rebuild.KotlinPerformedSet
import java.math.BigDecimal

/** Form/disclosure check only; exact stock and owner review are still required. */
internal fun kotlinWorkingLoadEntered(value:BigDecimal?,basis:LoadBasis?):Boolean =
    value!=null && basis!=null && (value.signum()>0 ||
        basis.permitsZeroAddedLoad && value.signum()==0)

/** Consistent physical units across set entry, review and History. */
internal fun kotlinLoadBasisLabel(basis:LoadBasis?):String=when(basis) {
    LoadBasis.DUMBBELL_PER_HAND -> "per hand"
    LoadBasis.KETTLEBELL_EACH -> "per kettlebell"
    LoadBasis.LANDMINE_ADDED_PLATES -> "landmine added plates"
    LoadBasis.BARBELL_TOTAL -> "total barbell weight"
    LoadBasis.SMITH_TOTAL -> "total Smith weight"
    LoadBasis.CABLE_STACK -> "cable stack"
    LoadBasis.MACHINE_STACK -> "machine stack"
    LoadBasis.ASSISTANCE_COUNTERWEIGHT -> "assistance counterweight (more = easier)"
    LoadBasis.EXERCISE_OVERRIDE -> "exercise load"
    null -> "bodyweight"
}

internal fun kotlinLoadBasisSuffix(basis:LoadBasis?):String=when(basis) {
    LoadBasis.DUMBBELL_PER_HAND -> " per hand"
    LoadBasis.KETTLEBELL_EACH -> " per kettlebell"
    LoadBasis.LANDMINE_ADDED_PLATES -> " added plates"
    else -> ""
}

/** Exact recorded convention, not a conversion to total lifted resistance. */
internal fun kotlinIssuedLoadLabel(load:DraftSetLoad):String=when(load) {
    DraftSetLoad.ExplicitBodyweight -> "Bodyweight"
    is DraftSetLoad.Verified ->
        "${load.value.stripTrailingZeros().toPlainString()} ${load.unit.name.lowercase()}"+
            " · ${kotlinLoadBasisLabel(load.basis)}"
}

/** An actual's value and unit always come from that observation. Never fill
 * a missing unit/load from the target. Its physical convention is disclosed
 * only when its unit agrees with the verified issued prescription. */
internal fun kotlinActualLoadLabel(
    actual:KotlinPerformedSet,issuedLoad:DraftSetLoad
):String {
    val value=actual.load ?:return if(issuedLoad==DraftSetLoad.ExplicitBodyweight &&
        actual.unit==null)"Bodyweight" else "Load not reported"
    val unit=actual.unit?.name?.lowercase() ?: "unit unreported"
    val basis=(issuedLoad as? DraftSetLoad.Verified)
        ?.takeIf{it.unit==actual.unit}?.basis
    return "${value.stripTrailingZeros().toPlainString()} $unit"+
        (basis?.let{" · ${kotlinLoadBasisLabel(it)}"} ?: "")
}
