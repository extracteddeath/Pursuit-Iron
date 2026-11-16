package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.KotlinGymMachineAlternatives

/**
 * Safe roster evolution against the CURRENT owner-reviewed physical catalog.
 *
 * A previously stored but no-longer-qualified machine group belongs to
 * historical physical performance identity. It must remain byte-equivalent
 * through unrelated, valid gym edits, but may not gain new stations, change
 * default, calibrate, reactivate or disappear through an ordinary roster save.
 *
 * Only the authenticated source writer may supply eligibleExerciseIds.
 * This function is a pure guard, not another writable machine authority.
 */
internal fun nativeMachineRosterSourceEligible(
    gymId:String,
    eligibleExerciseIds:Set<String>,
    previous:List<KotlinGymMachineAlternatives>,
    proposed:List<KotlinGymMachineAlternatives>
):Boolean {
    val historical=previous.filter {
        it.gymId==gymId && it.exerciseId !in eligibleExerciseIds
    }
    val after=proposed.filter {
        it.gymId==gymId && it.exerciseId !in eligibleExerciseIds
    }
    return historical==after
}
