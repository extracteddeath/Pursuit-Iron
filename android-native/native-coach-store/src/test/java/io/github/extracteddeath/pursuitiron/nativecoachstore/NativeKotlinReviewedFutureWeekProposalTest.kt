package io.github.extracteddeath.pursuitiron.nativecoachstore

import io.github.extracteddeath.pursuitiron.rebuild.CustomCycleChangeKind
import org.junit.Assert.*
import org.junit.Test

class NativeKotlinReviewedFutureWeekProposalTest {
    private val owner=NativeKotlinReviewedFutureWeekProposal

    @Test fun permissionsMustBeDurableAndSpecificToTheActualAmendment() {
        val noRights=owner.weekPermissions(emptySet())
        assertFalse(noRights.adaptWeekOverrides)
        assertFalse(noRights.adaptReps)
        assertFalse(noRights.adaptLoads)

        val repRights=owner.weekPermissions(setOf(
            CustomCycleChangeKind.WEEK_OVERRIDES,
            CustomCycleChangeKind.REPS))
        assertTrue(repRights.adaptWeekOverrides)
        assertTrue(repRights.adaptReps)
        assertFalse(repRights.adaptLoads)
        assertFalse(repRights.adaptExercises)
        assertFalse(repRights.adaptSchedule)
        assertFalse(repRights.adaptBlocks)

        val wrongRight=owner.weekPermissions(setOf(
            CustomCycleChangeKind.REPS,
            CustomCycleChangeKind.EXERCISES))
        assertFalse(wrongRight.adaptWeekOverrides)
        assertTrue(wrongRight.adaptReps)

        val loadRights=owner.weekPermissions(setOf(
            CustomCycleChangeKind.WEEK_OVERRIDES,
            CustomCycleChangeKind.LOADS))
        assertTrue(loadRights.adaptLoads)
        assertFalse(loadRights.adaptReps)
    }

    @Test fun nextWeekUsesEntireSameCycleHistoryNotMostRecentSuccessfulLift() {
        assertEquals(0,owner.lastObservedWeek("cycle",emptyList()))
        assertEquals(0,owner.lastObservedWeek("cycle",
            listOf("different-cycle" to 9)))
        assertEquals(5,owner.lastObservedWeek("cycle",listOf(
            "cycle" to 2, "cycle" to 5, "different-cycle" to 22,
            "cycle" to 4)))
        // A completed later week cannot be hidden by the screen passing
        // only earlier successful sessions or an old cycle revision.
        assertEquals(7,owner.lastObservedWeek("cycle",
            listOf("cycle" to 7, "cycle" to 3)))
    }
}
