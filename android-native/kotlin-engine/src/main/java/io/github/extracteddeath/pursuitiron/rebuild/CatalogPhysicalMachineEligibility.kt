package io.github.extracteddeath.pursuitiron.rebuild

/**
 * Reviewed physical-machine eligibility shared by Gym, Plan, Train and every
 * owner-fenced machine roster/workout writer.
 *
 * A machine with LoadBasis.EXERCISE_OVERRIDE is still a physical machine:
 * leg-extension, chest-press, and many other machine-specific totals must not
 * silently lose Machine A/B switching just because their load is calibrated
 * using the exact exercise rather than a generic machine stack.
 *
 * Never decide from an English name, generic EXERCISE_OVERRIDE, or the suffix
 * "machine". The exact source-linked exercise/family/variant/implement tuple
 * below is explicit reviewer provenance; a changed source record fails shut.
 * Unreviewed custom records cannot borrow this whitelist by reusing an ID.
 *
 * Separate gym/station history and attainable machine-specific load remain
 * governed by KotlinMachineAlternativesDomain and the owner-fenced writers.
 */
object CatalogPhysicalMachineEligibility {
    private data class ReviewedStation(
        val family:String,
        val variant:String,
        val implement:String
    )

    // 42 active canonical and one historical shell machine identity, reviewed against
    // StarterExerciseTranche. Physical wrist levers/rollers and a loose plate
    // use the same EXERCISE_OVERRIDE basis but are deliberately EXCLUDED.
    private val machineOverrides=mapOf(
        "leg_extension" to ReviewedStation("knee_extension","seated-machine","leg-extension-machine"),
        "seated_leg_curl" to ReviewedStation("knee_flexion","seated-machine","seated-leg-curl-machine"),
        "machine_press" to ReviewedStation("machine_chest_press","chest-press","chest-press-machine"),
        "leg_press" to ReviewedStation("leg_press","machine","leg-press-machine"),
        "hack_squat" to ReviewedStation("squat","hack-machine","hack-squat-machine"),
        "lat-pulldown" to ReviewedStation("vertical_pull","machine-lat","lat-pulldown-machine"),
        "rear_delt_fly" to ReviewedStation("rear_delt_fly","reverse-pec-deck","reverse-pec-deck-machine"),
        "machine_ohp" to ReviewedStation("overhead_press","shoulder-machine","shoulder-press-machine"),
        "preacher_curl" to ReviewedStation("elbow_flexion","preacher-machine","preacher-curl-machine"),
        "standing_calf" to ReviewedStation("calf_raise","standing-machine","standing-calf-machine"),
        "pec-deck" to ReviewedStation("chest_adduction","pec-deck-machine","pec-deck-machine"),
        "lying-curl" to ReviewedStation("knee_flexion","lying-machine","lying-leg-curl-machine"),
        "seated-calf" to ReviewedStation("calf_raise","seated-machine","seated-calf-machine"),
        "machine-row" to ReviewedStation("machine_row","seated-machine","machine-row-station"),
        "incline-machine-press" to ReviewedStation("bench_press","incline-machine","incline-chest-press-station"),
        "machine-lat-raise" to ReviewedStation("lateral_raise","selectorized-machine","lateral-raise-machine"),
        "machine-curl" to ReviewedStation("elbow_flexion","selectorized-curl-machine","curl-machine"),
        "machine-ext" to ReviewedStation("elbow_extension","triceps-extension-machine","triceps-extension-machine"),
        "belt-squat" to ReviewedStation("squat","belt-machine","belt-squat-machine"),
        "machine-hip-thrust" to ReviewedStation("hip_extension","selectorized-hip-thrust","hip-thrust-machine"),
        "machine-crunch" to ReviewedStation("trunk_flexion","crunch-machine","crunch-machine"),
        "single-leg-press" to ReviewedStation("leg_press","single-leg-press-machine","leg-press-machine"),
        "standing-curl" to ReviewedStation("knee_flexion","standing-leg-curl-machine","standing-leg-curl-machine"),
        "pendulum-squat" to ReviewedStation("squat","pendulum-squat-machine","pendulum-squat-machine"),
        "v-squat" to ReviewedStation("squat","v-squat-machine","v-squat-machine"),
        "reverse-hyper" to ReviewedStation("hip_extension","reverse-hyper-station","reverse-hyper-machine"),
        "leg-press-calf" to ReviewedStation("calf_raise","leg-press-calf-press","leg-press-machine"),
        "donkey-calf" to ReviewedStation("calf_raise","donkey-calf-machine","donkey-calf-machine"),
        "machine-dip" to ReviewedStation("chest_dip","loaded-chest-dip-machine","chest-dip-machine"),
        "wide-machine-row" to ReviewedStation("machine_row","wide-grip-machine","wide-row-machine"),
        "neutral-machine-row" to ReviewedStation("machine_row","neutral-grip-machine","neutral-row-machine"),
        "t-bar-chest-supported" to ReviewedStation("machine_row","chest-supported-t-bar","chest-t-bar-row-station"),
        "machine-pullover" to ReviewedStation("shoulder_extension","machine-lat-pullover","pullover-machine"),
        "glute-kickback-machine" to ReviewedStation("hip_extension","dedicated-kickback-machine","glute-kickback-machine"),
        "hack-calf" to ReviewedStation("calf_raise","hack-machine-calf","hack-calf-machine"),
        "single-lying-curl" to ReviewedStation("knee_flexion","single-leg-lying-machine","single-leg-lying-curl-machine"),
        "single-leg-press-calf" to ReviewedStation("calf_raise","single-leg-press-machine","leg-press-machine"),
        "viking-press" to ReviewedStation("overhead_press","viking-machine","viking-press-machine"),
        "hip-abduction" to ReviewedStation("hip_abduction","machine-seated","hip-abduction-machine"),
        "adduction-machine" to ReviewedStation("hip_adduction","machine-seated","hip-adduction-machine"),
        "machine-shrug" to ReviewedStation("scapular_elevation","machine","shrug-machine"),
        "kelso-shrug" to ReviewedStation("scapular_retraction","supported-machine-kelso","kelso-shrug-machine"),
        "chest-supported-machine-row" to ReviewedStation("chest_supported_row","supported-machine","chest-supported-row-machine")
    )

    private val assistedSources=mapOf(
        "assisted-dip" to ReviewedStation(
            "horizontal_press","assisted-counterweight-dip",
            "assisted-pullup-dip-counterweight"),
        "assisted-chinup" to ReviewedStation(
            "vertical_pull","assisted-counterweight-chinup",
            "assisted-pullup-dip-counterweight"),
        "assisted-pullup" to ReviewedStation(
            "vertical_pull","assisted-counterweight-pullup",
            "assisted-pullup-dip-counterweight")
    )
    fun eligible(movement:CatalogMovement):Boolean {
        if(movement.expectedLoadBasis==LoadBasis.ASSISTANCE_COUNTERWEIGHT) {
            val reviewed=assistedSources[movement.exerciseId] ?:return false
            return movement.equipmentId==reviewed.implement &&
                movement.familyId==reviewed.family &&
                movement.variantId==reviewed.variant &&
                movement.pattern==(if(movement.exerciseId=="assisted-dip")
                    ComposerPattern.HORIZONTAL_PRESS else ComposerPattern.VERTICAL_PULL) &&
                movement.role==ComposerRole.SECONDARY &&
                movement.sourcePracticalReps==RepBand(8,12) &&
                movement.requiredFacilityIds==setOf("assisted-pullup-dip") &&
                movement.creditStatus==
                    CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL &&
                movement.creditReviewReference=="engineering-provisional-v1"
        }
        // Preserve separately reviewed cable/machine stack behavior.
        if(movement.expectedLoadBasis in setOf(
                LoadBasis.CABLE_STACK,LoadBasis.MACHINE_STACK))
            return movement.equipmentId!=null
        if(movement.expectedLoadBasis!=LoadBasis.EXERCISE_OVERRIDE)
            return false
        val source=machineOverrides[movement.exerciseId]?:return false
        return movement.familyId==source.family &&
            movement.variantId==source.variant &&
            movement.equipmentId==source.implement &&
            movement.creditStatus==CatalogCreditStatus.AUTHOR_REVIEWED_PROVISIONAL &&
            movement.creditReviewReference=="engineering-provisional-v1"
    }

    /** Duplicate or conflicted current-source identities are ineligible. */
    fun eligibleExerciseIds(catalog:List<CatalogMovement>):Set<String> =
        catalog.groupBy{it.exerciseId}.filterValues {records->
            records.size==1 && eligible(records.single())
        }.keys
}
