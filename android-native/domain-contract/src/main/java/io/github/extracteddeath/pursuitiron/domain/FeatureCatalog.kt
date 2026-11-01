package io.github.extracteddeath.pursuitiron.domain

/**
 * Feature capabilities are stable acceptance targets, NOT React screen ports.
 * Displaying a capability never means its native implementation is complete.
 */
enum class FeatureGate {
    CANONICAL_ENGINE,
    VERIFIED_STORAGE,
    ANDROID_DEVICE
}

enum class FeatureArea(val label: String) {
    TRAINING("Training"),
    PROGRAMMING("Programming"),
    INSIGHTS("Insights"),
    PERSONALIZATION("Personalization")
}

data class FeatureCapability(
    val id: String,
    val title: String,
    val outcome: String,
    val area: FeatureArea,
    val gates: Set<FeatureGate>
)

object FeatureCatalog {
    val capabilities: List<FeatureCapability> = listOf(
        FeatureCapability("adaptive-progression", "Adaptive progression",
            "Use logged performance and RIR without rewriting user-entered loads.",
            FeatureArea.TRAINING, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE)),
        FeatureCapability("workout-logging", "Live workout",
            "Warm-ups, reps, weights, RIR, rest, notes, set edits and interruptions.",
            FeatureArea.TRAINING, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE, FeatureGate.ANDROID_DEVICE)),
        FeatureCapability("session-adjustments", "Adjust today's session",
            "Readiness, injury constraints and safe shorter-session choices.",
            FeatureArea.TRAINING, setOf(FeatureGate.CANONICAL_ENGINE)),
        FeatureCapability("workout-tools", "Workout tools",
            "Plate loading, substitutes, supersets, amplifiers, focus and history.",
            FeatureArea.TRAINING, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE)),
        FeatureCapability("program-builder", "Personalized program",
            "Goal, equipment, split, available days, emphasis and muscle volume.",
            FeatureArea.PROGRAMMING, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE)),
        FeatureCapability("custom-programs", "Custom programs",
            "Preserve authored movements and advanced settings across phases.",
            FeatureArea.PROGRAMMING, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE)),
        FeatureCapability("adaptive-cycles", "Adaptive training cycles",
            "Hypertrophy, strength, peak and authorized split/exercise changes.",
            FeatureArea.PROGRAMMING, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE)),
        FeatureCapability("exercise-discovery", "Exercise library",
            "Equipment-aware discovery, variants, attachments and swaps.",
            FeatureArea.PROGRAMMING, setOf(FeatureGate.CANONICAL_ENGINE)),
        FeatureCapability("history-and-prs", "Training history and PRs",
            "Correction-safe records, actual PRs and comparable last performance.",
            FeatureArea.INSIGHTS, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE)),
        FeatureCapability("volume-and-trends", "Strength and volume trends",
            "Regional/muscle volume, strength standards, consistency and charts.",
            FeatureArea.INSIGHTS, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE)),
        FeatureCapability("bodyweight", "Bodyweight and milestones",
            "Measurements, bodyweight and milestone progress without double-counts.",
            FeatureArea.INSIGHTS, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE)),
        FeatureCapability("backup-restore", "Backup and restore",
            "Non-destructive import previews, validated merges, Undo and export.",
            FeatureArea.PERSONALIZATION, setOf(FeatureGate.VERIFIED_STORAGE, FeatureGate.ANDROID_DEVICE)),
        FeatureCapability("gyms-and-equipment", "Gyms and equipment",
            "Available plates, machine increments, units and workout options.",
            FeatureArea.PERSONALIZATION, setOf(FeatureGate.CANONICAL_ENGINE, FeatureGate.VERIFIED_STORAGE)),
        FeatureCapability("profile", "Profile and preferences",
            "Personal training settings, presentation and progress identity.",
            FeatureArea.PERSONALIZATION, setOf(FeatureGate.VERIFIED_STORAGE))
    )

    init {
        check(capabilities.map { it.id }.distinct().size == capabilities.size) {
            "Feature IDs must be unique"
        }
    }
}
