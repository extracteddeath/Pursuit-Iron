package io.github.extracteddeath.pursuitiron

/**
 * Build an explicit, athlete-reviewed TEXT share payload from display-only
 * history facts. Never serialize owner keys, athlete IDs, gym/machine IDs,
 * history dates, RIR, private notes, loads or account data.
 *
 * This is deliberately NOT a rendered image card or a certified PR.
 * Sharing is only invoked by the Android UI after a separate preview tap.
 */
internal object NativeKotlinProfileShareSummary {
    private const val MAX_FAVORITES = 5
    private val control = Regex("[\\p{Cc}\\p{Cf}]")

    private fun clean(value: String, maxChars: Int): String =
        value.replace(control, " ").trim()
            .replace(Regex("\\s+"), " ").take(maxChars).trim()

    fun render(
        progress: NativeProfileActivityProgress,
        displayName: String,
        verifiedFavoriteLabels: List<String>,
        includeName: Boolean,
        includeFavoriteExercises: Boolean,
        loggedSinglesClubReached: Boolean
    ): String {
        val result = mutableListOf<String>()
        result += "Pursuit Iron — training summary"
        if (includeName) clean(displayName, 32).takeIf { it.isNotBlank() }
            ?.let { result += "Name: $it" }
        result += "Activity level: ${progress.level} (${progress.totalXp} XP)"
        result += "Completed workouts: ${progress.sessions}"
        result += "Completed working sets: ${progress.completedWorkingSets}"
        result += "Participation milestones: ${progress.earnedMilestones} / ${progress.milestones.size}"
        if (loggedSinglesClubReached)
            result += "1,000 lb barbell logged-single milestone: self-reported, not competition-certified"
        if (includeFavoriteExercises) {
            val safe = verifiedFavoriteLabels.map { clean(it, 64) }
                .filter { it.isNotBlank() }.distinct().take(MAX_FAVORITES)
            if (safe.isNotEmpty())
                result += "Favorite logged exercises: " + safe.joinToString(", ")
        }
        result += "Historical activity only — not a verified competition record or workout recommendation."
        return result.joinToString("\n")
    }
}
