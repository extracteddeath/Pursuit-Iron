package io.github.extracteddeath.pursuitiron

/**
 * Local, display-only training participation. This is an app gamification
 * measure, NOT athlete experience, a strength standard, a coaching reward or
 * evidence for load/rep progression. The caller supplies counts exclusively
 * from the complete owner-verified, corrected Kotlin history.
 */
internal enum class NativeProfileMilestoneUnit { SESSIONS, WORKING_SETS }

internal data class NativeProfileMilestone(
    val id: String,
    val title: String,
    val description: String,
    val unit: NativeProfileMilestoneUnit,
    val required: Int
) {
    fun current(progress: NativeProfileActivityProgress): Int = when (unit) {
        NativeProfileMilestoneUnit.SESSIONS -> progress.sessions
        NativeProfileMilestoneUnit.WORKING_SETS -> progress.completedWorkingSets
    }
    fun earned(progress: NativeProfileActivityProgress): Boolean =
        current(progress) >= required
}

internal data class NativeProfileActivityProgress(
    val sessions: Int,
    val completedWorkingSets: Int,
    val totalXp: Long,
    val level: Int,
    val xpInLevel: Long,
    val xpForNextLevel: Long?,
    val milestones: List<NativeProfileMilestone>
) {
    val levelFraction: Float get() =
        xpForNextLevel?.let { (xpInLevel.toDouble() / it.toDouble()).toFloat().coerceIn(0f, 1f) }
            ?: 1f
    val earnedMilestones: Int get() = milestones.count { it.earned(this) }
}

/**
 * Transparent, fixed rules; no random scores, inferred dates, RIR, PRs or
 * rewards for warmups / custom amplifier follow-ups / skipped or failed sets.
 *
 * Each VERIFIED finished workout = 100 XP.
 * Each COMPLETED main working set = 10 XP.
 * Level transitions cost 500, 750, 1000 ... XP, capped at level 100.
 * Milestones are cumulative and recomputed after corrections, never stored.
 */
internal object NativeKotlinProfileMilestones {
    const val XP_PER_COMPLETED_SESSION = 100L
    const val XP_PER_COMPLETED_WORKING_SET = 10L
    const val MAX_LEVEL = 100

    val milestones: List<NativeProfileMilestone> = listOf(
        NativeProfileMilestone("first-session", "First workout", "Complete your first workout",
            NativeProfileMilestoneUnit.SESSIONS, 1),
        NativeProfileMilestone("ten-sessions", "Showing up", "Complete 10 workouts",
            NativeProfileMilestoneUnit.SESSIONS, 10),
        NativeProfileMilestone("first-50-sets", "Building momentum",
            "Complete 50 working sets", NativeProfileMilestoneUnit.WORKING_SETS, 50),
        NativeProfileMilestone("twenty-five-sessions", "Committed",
            "Complete 25 workouts", NativeProfileMilestoneUnit.SESSIONS, 25),
        NativeProfileMilestone("two-fifty-sets", "Putting in the work",
            "Complete 250 working sets", NativeProfileMilestoneUnit.WORKING_SETS, 250),
        NativeProfileMilestone("fifty-sessions", "Consistent training",
            "Complete 50 workouts", NativeProfileMilestoneUnit.SESSIONS, 50),
        NativeProfileMilestone("thousand-sets", "Long-term effort",
            "Complete 1,000 working sets", NativeProfileMilestoneUnit.WORKING_SETS, 1000),
        NativeProfileMilestone("hundred-sessions", "Century of sessions",
            "Complete 100 workouts", NativeProfileMilestoneUnit.SESSIONS, 100)
    )

    fun fromVerifiedCounts(completedSessions: Int, completedMainWorkingSets: Int):
        NativeProfileActivityProgress {
        require(completedSessions >= 0 && completedMainWorkingSets >= 0)
        val xp = completedSessions.toLong() * XP_PER_COMPLETED_SESSION +
            completedMainWorkingSets.toLong() * XP_PER_COMPLETED_WORKING_SET
        var remaining = xp
        var level = 1
        while (level < MAX_LEVEL) {
            val next = 500L + (level - 1L) * 250L
            if (remaining < next) break
            remaining -= next
            level++
        }
        return NativeProfileActivityProgress(
            completedSessions, completedMainWorkingSets, xp, level, remaining,
            if (level == MAX_LEVEL) null else 500L + (level - 1L) * 250L,
            milestones
        )
    }
}
