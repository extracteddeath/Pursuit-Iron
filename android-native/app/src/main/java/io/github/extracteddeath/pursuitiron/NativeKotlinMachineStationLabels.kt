package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.nativecoachstore.NativeMachineRosterSnapshot

/**
 * Display-only aliases for verified history. A private gym or machine ID is
 * never a human-readable name. Exact IDs still scope history and comparisons;
 * only the chip/card label is transformed here.
 */
internal object NativeKotlinMachineStationLabels {
    fun label(
        exerciseId:String,
        gymId:String?,
        machineId:String?,
        knownStations:Collection<Pair<String?,String?>>,
        roster:NativeMachineRosterSnapshot?
    ):String {
        val knownGyms=knownStations.mapNotNull{it.first?.takeIf(String::isNotBlank)}
            .distinct().sorted()
        val gym=if(gymId.isNullOrBlank()) "Gym not recorded"
            else "Gym ${knownGyms.indexOf(gymId).takeIf{it>=0}?.plus(1) ?: 1}"
        if(machineId.isNullOrBlank())
            return "$gym · Earlier records (machine unspecified)"

        // Only the saved, matching gym+exercise+machine may supply a friendly
        // label. Never borrow the default machine label for an old observation.
        val savedName=roster?.configurations?.singleOrNull{
            it.gymId==gymId && it.exerciseId==exerciseId
        }?.machines?.singleOrNull{it.id==machineId}?.label?.trim()
            ?.takeIf{it.isNotBlank()}
        val knownMachines=knownStations.filter{it.first==gymId}
            .mapNotNull{it.second?.takeIf(String::isNotBlank)}
            .distinct().sorted()
        val ordinal=knownMachines.indexOf(machineId).takeIf{it>=0}?.plus(1) ?: 1
        return "$gym · ${savedName ?: "Machine $ordinal (name unavailable)"}"
    }
}
