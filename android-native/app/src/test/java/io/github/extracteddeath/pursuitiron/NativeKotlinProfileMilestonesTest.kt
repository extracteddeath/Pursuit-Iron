package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

internal class NativeKotlinProfileMilestonesTest {
    private val domain = NativeKotlinProfileMilestones

    @Test fun emptyVerifiedHistoryIsLevelOneWithNoEarnedMilestones() {
        val p = domain.fromVerifiedCounts(0, 0)
        assertEquals(1, p.level)
        assertEquals(0L, p.totalXp)
        assertEquals(0L, p.xpInLevel)
        assertEquals(500L, p.xpForNextLevel)
        assertEquals(0f, p.levelFraction, 0.0001f)
        assertEquals(0, p.earnedMilestones)
        assertFalse(p.milestones.first().earned(p))
    }

    @Test fun finishedWorkoutsAndCompletedMainSetsAreCountedSeparately() {
        val p = domain.fromVerifiedCounts(1, 4)
        assertEquals(140L, p.totalXp)
        assertTrue(p.milestones.single { it.id == "first-session" }.earned(p))
        assertFalse(p.milestones.single { it.id == "first-50-sets" }.earned(p))
        assertEquals(1, p.earnedMilestones)
        assertEquals(140L, p.xpInLevel)
    }

    @Test fun levelBoundariesAndNextLevelXpAreDeterministic() {
        // Five completed sessions reach exactly 500 XP.
        val level2 = domain.fromVerifiedCounts(5, 0)
        assertEquals(2, level2.level)
        assertEquals(500L, level2.totalXp)
        assertEquals(0L, level2.xpInLevel)
        assertEquals(750L, level2.xpForNextLevel)
        assertEquals(0f, level2.levelFraction, 0.0001f)
        val level3 = domain.fromVerifiedCounts(12, 5)
        assertEquals(3, level3.level)
        assertEquals(1250L, level3.totalXp)
        assertEquals(0L, level3.xpInLevel)
        assertEquals(1000L, level3.xpForNextLevel)
        assertTrue(domain.fromVerifiedCounts(12, 4).level < level3.level)
    }

    @Test fun correctionRecomputesDownwardRatherThanRetainingFalseMilestones() {
        val before = domain.fromVerifiedCounts(10, 50)
        val corrected = domain.fromVerifiedCounts(9, 49)
        assertTrue(before.milestones.single { it.id == "ten-sessions" }.earned(before))
        assertFalse(corrected.milestones.single { it.id == "ten-sessions" }.earned(corrected))
        assertTrue(before.milestones.single { it.id == "first-50-sets" }.earned(before))
        assertFalse(corrected.milestones.single { it.id == "first-50-sets" }.earned(corrected))
        assertEquals(before.totalXp - 110L, corrected.totalXp)
        assertEquals(1, domain.fromVerifiedCounts(0, 0).level)
    }

    @Test fun thresholdsAndExtremeCountsStayFiniteAndNeverOverflowOrLoopWithoutBound() {
        val max = domain.fromVerifiedCounts(Int.MAX_VALUE, Int.MAX_VALUE)
        assertEquals(100, max.level)
        assertNull(max.xpForNextLevel)
        assertEquals(1f, max.levelFraction, 0f)
        assertTrue(max.totalXp > 0L)
        assertTrue(max.milestones.all { it.earned(max) })
        assertThrows(IllegalArgumentException::class.java) { domain.fromVerifiedCounts(-1, 0) }
        assertThrows(IllegalArgumentException::class.java) { domain.fromVerifiedCounts(0, -1) }
    }

    @Test fun milestoneIdsAreUniqueAndMilestonesAreStrictlyParticipationBased() {
        val all = domain.milestones
        assertEquals(all.size, all.map { it.id }.distinct().size)
        assertTrue(all.all { it.required > 0 })
        assertEquals(2, NativeProfileMilestoneUnit.entries.size)
        assertTrue(all.any { it.unit == NativeProfileMilestoneUnit.SESSIONS })
        assertTrue(all.any { it.unit == NativeProfileMilestoneUnit.WORKING_SETS })
    }
}
