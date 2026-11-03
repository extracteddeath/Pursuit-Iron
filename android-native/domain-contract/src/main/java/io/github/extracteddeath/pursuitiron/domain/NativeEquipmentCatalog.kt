package io.github.extracteddeath.pursuitiron.domain

/** Presentation-only equipment groups; IDs are canonical catalog IDs.
 * This never grants equipment, changes saved IDs, or supplies loading facts.
 * The engine's programOptions() remains the authority for selectable IDs.
 */
data class NativeEquipmentChoice(val id: String, val label: String)
data class NativeEquipmentGroup(val title: String, val choices: List<NativeEquipmentChoice>)

object NativeEquipmentCatalog {
    val groups: List<NativeEquipmentGroup> = listOf(
        NativeEquipmentGroup("Free weights", listOf(
            NativeEquipmentChoice("dumbbell", "Dumbbells"),
            NativeEquipmentChoice("kettlebell", "Kettlebells"),
            NativeEquipmentChoice("bands", "Bands")
        )),
        NativeEquipmentGroup("Bars", listOf(
            NativeEquipmentChoice("barbell", "Barbell"),
            NativeEquipmentChoice("ezbar", "EZ curl bar"),
            NativeEquipmentChoice("trapbar", "Trap / hex bar"),
            NativeEquipmentChoice("safetybar", "Safety squat bar"),
            NativeEquipmentChoice("landmine", "Landmine")
        )),
        NativeEquipmentGroup("Benches & stations", listOf(
            NativeEquipmentChoice("bench", "Flat bench"),
            NativeEquipmentChoice("inclinebench", "Incline bench"),
            NativeEquipmentChoice("declinebench", "Decline bench"),
            NativeEquipmentChoice("preacher", "Preacher bench"),
            NativeEquipmentChoice("pullup", "Pull-up bar"),
            NativeEquipmentChoice("dip", "Dip station"),
            NativeEquipmentChoice("ghd", "Glute-ham developer"),
            NativeEquipmentChoice("reversehyper", "Reverse hyper")
        )),
        NativeEquipmentGroup("Cables & general machines", listOf(
            NativeEquipmentChoice("cable", "Cable machine"),
            NativeEquipmentChoice("smith", "Smith machine"),
            NativeEquipmentChoice("machine", "Selectorized machines"),
            NativeEquipmentChoice("assisted", "Assisted pull-up / dip")
        )),
        NativeEquipmentGroup("Leg machines", listOf(
            NativeEquipmentChoice("legpress", "Leg press"),
            NativeEquipmentChoice("hacksquat", "Hack squat"),
            NativeEquipmentChoice("beltsquat", "Belt squat"),
            NativeEquipmentChoice("legext", "Leg extension"),
            NativeEquipmentChoice("legcurl", "Leg curl"),
            NativeEquipmentChoice("calfmachine", "Calf machine")
        )),
        NativeEquipmentGroup("Hip & glute machines", listOf(
            NativeEquipmentChoice("hipthrustmachine", "Hip thrust machine"),
            NativeEquipmentChoice("kickback", "Glute kickback"),
            NativeEquipmentChoice("abduction", "Hip abduction"),
            NativeEquipmentChoice("adduction", "Hip adduction")
        )),
        NativeEquipmentGroup("Upper-body machines", listOf(
            NativeEquipmentChoice("machinepress", "Chest press"),
            NativeEquipmentChoice("pecdeck", "Pec deck"),
            NativeEquipmentChoice("machineshoulder", "Shoulder press"),
            NativeEquipmentChoice("machinelatraise", "Lateral raise"),
            NativeEquipmentChoice("machinerow", "Row"),
            NativeEquipmentChoice("machinepullover", "Pullover"),
            NativeEquipmentChoice("machineshrug", "Shrug")
        )),
        NativeEquipmentGroup("Arm & core machines", listOf(
            NativeEquipmentChoice("machinecurl", "Curl"),
            NativeEquipmentChoice("machineext", "Triceps extension"),
            NativeEquipmentChoice("machinecrunch", "Crunch")
        ))
    )

    val allIds: List<String> = groups.flatMap { group -> group.choices.map { it.id } }

    /** Keep the user's requested grouping, but show only IDs the engine offers. */
    fun supported(available: Collection<String>): List<NativeEquipmentGroup> {
        val known = available.toSet()
        return groups.mapNotNull { group ->
            val choices = group.choices.filter { it.id in known }
            if (choices.isEmpty()) null else group.copy(choices = choices)
        }
    }
}
