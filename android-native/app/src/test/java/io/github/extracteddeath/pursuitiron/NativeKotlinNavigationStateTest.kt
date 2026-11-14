package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

class NativeKotlinNavigationStateTest {
    private val primary=listOf(KotlinCandidateDestination.PROGRAM,
        KotlinCandidateDestination.TRAIN,KotlinCandidateDestination.HISTORY,
        KotlinCandidateDestination.PROGRESS,KotlinCandidateDestination.COACH)
    private fun root(destination:KotlinCandidateDestination)=NativeKotlinNavigationState.start(destination)

    @Test fun primaryTabsHaveNoSyntheticBackDestination() {
        for(origin in primary) {
            val state=root(origin)
            assertEquals(origin,state.selectedPrimary)
            assertFalse(state.canGoBack)
            assertSame(state,state.back())
        }
    }

    @Test fun profileSettingsCycleReturnsToExistingRouteThenItsRealOrigin() {
        val coach=root(KotlinCandidateDestination.COACH)
        val settings=coach.open(KotlinCandidateDestination.SETTINGS)
        val returned=settings.open(KotlinCandidateDestination.PROFILE)
            .open(KotlinCandidateDestination.SETTINGS)
        assertEquals(settings.snapshot(),returned.snapshot())
        assertEquals(coach.snapshot(),returned.back().snapshot())
        assertFalse(returned.back().canGoBack)
    }

    @Test fun reverseProfileSettingsCycleAlsoTerminatesAtItsOrigin() {
        val original=root(KotlinCandidateDestination.HISTORY)
        var state=original.open(KotlinCandidateDestination.PROFILE)
        repeat(50) {state=state.open(KotlinCandidateDestination.SETTINGS)
            .open(KotlinCandidateDestination.PROFILE)}
        assertEquals(original.open(KotlinCandidateDestination.PROFILE).snapshot(),state.snapshot())
        assertEquals(original.snapshot(),state.back().snapshot())
    }

    @Test fun settingsAndGymInheritActualPrimaryContextFromEveryTab() {
        for(origin in primary) {
            val original=root(origin)
            val profile=original.open(KotlinCandidateDestination.PROFILE)
            for(destination in listOf(KotlinCandidateDestination.SETTINGS,KotlinCandidateDestination.GYM)) {
                val child=profile.open(destination)
                assertEquals(origin,child.selectedPrimary)
                assertEquals(profile.snapshot(),child.back().snapshot())
                assertEquals(original.snapshot(),child.back().back().snapshot())
            }
        }
    }

    @Test fun firstTimeSetupDoesNotPretendToBeCoachOrPlan() {
        val setup=NativeKotlinNavigationState.start()
        val gym=setup.open(KotlinCandidateDestination.GYM)
        assertNull(setup.selectedPrimary)
        assertNull(gym.selectedPrimary)
        assertEquals(setup.snapshot(),gym.back().snapshot())
    }

    @Test fun programToolsHavePlanAsHomeAndRetainActualBackOrigin() {
        val coach=root(KotlinCandidateDestination.COACH)
        for(tool in listOf(KotlinCandidateDestination.STARTER,KotlinCandidateDestination.FUTURE)) {
            val detail=coach.open(tool)
            assertEquals(KotlinCandidateDestination.PROGRAM,detail.selectedPrimary)
            assertEquals(KotlinCandidateDestination.PROGRAM,
                detail.open(KotlinCandidateDestination.SETTINGS).selectedPrimary)
            assertEquals(coach.snapshot(),detail.back().snapshot())
        }
    }

    @Test fun nestedHistoryTrendReturnsWithItsExactExerciseRequestIntact() {
        val history=root(KotlinCandidateDestination.HISTORY)
        val trend=history.openProgressTrend("machine_press_b")
        val returned=trend.open(KotlinCandidateDestination.PROFILE)
            .open(KotlinCandidateDestination.SETTINGS).back().back()
        assertEquals(trend.snapshot(),returned.snapshot())
        assertEquals(NativeKotlinRouteDetail.PROGRESS_TREND,returned.current.detail)
        assertEquals("machine_press_b",returned.current.exerciseId)
        assertEquals(history.snapshot(),returned.back().snapshot())
    }

    @Test fun favoriteLinkReturnsToProfileThenItsPriorProgressOverview() {
        val progress=root(KotlinCandidateDestination.PROGRESS)
        val profile=progress.open(KotlinCandidateDestination.PROFILE)
        val favorite=profile.openProgressExercise("barbell_bench")
        assertEquals(NativeKotlinRouteDetail.PROGRESS_EXERCISE,favorite.current.detail)
        assertEquals(profile.snapshot(),favorite.back().snapshot())
        assertEquals(progress.snapshot(),favorite.back().back().snapshot())
        assertNull(favorite.back().back().current.exerciseId)
    }

    @Test fun nestedProgressLinkDoesNotOverwriteEarlierTrendContext() {
        val trend=root(KotlinCandidateDestination.HISTORY).openProgressTrend("one_arm_db_row")
        val favorite=trend.open(KotlinCandidateDestination.PROFILE)
            .openProgressExercise("barbell_bench")
        assertEquals(trend.snapshot(),favorite.back().back().snapshot())
    }

    @Test fun coachExerciseReturnsToHistoryAndSurvivesSecondaryScreens() {
        val history=root(KotlinCandidateDestination.HISTORY)
        val coach=history.openCoachExercise("db_curl")
        val returned=coach.open(KotlinCandidateDestination.SETTINGS).back()
        assertEquals(coach.snapshot(),returned.snapshot())
        assertEquals("db_curl",returned.current.exerciseId)
        assertEquals(history.snapshot(),returned.back().snapshot())
    }

    @Test fun ordinaryTabSelectionClearsContextualRequestsAndAncestors() {
        val detailed=root(KotlinCandidateDestination.HISTORY).openProgressTrend("hip-abduction")
            .open(KotlinCandidateDestination.PROFILE)
        for(tab in primary) {
            val ordinary=detailed.open(tab)
            assertEquals(root(tab).snapshot(),ordinary.snapshot())
            assertEquals(NativeKotlinRouteDetail.NONE,ordinary.current.detail)
            assertNull(ordinary.current.exerciseId)
            assertFalse(ordinary.canGoBack)
        }
    }

    @Test fun repeatedDetailDoesNotCreateDuplicateBackEntries() {
        val detail=root(KotlinCandidateDestination.HISTORY).openCoachExercise("db_curl")
        assertSame(detail,detail.openCoachExercise("db_curl"))
        assertEquals(detail.snapshot(),detail.open(KotlinCandidateDestination.PROFILE)
            .openCoachExercise("db_curl").snapshot())
    }

    @Test fun savedNavigationRestoresEveryOriginAndDetailWithoutLosingFields() {
        val original=root(KotlinCandidateDestination.HISTORY).openProgressTrend("press|seat:3")
            .open(KotlinCandidateDestination.PROFILE).open(KotlinCandidateDestination.GYM)
        var restored=checkNotNull(NativeKotlinNavigationState.restore(original.snapshot()))
        var expected=original
        while(true) {
            assertEquals(expected.snapshot(),restored.snapshot())
            assertEquals(expected.selectedPrimary,restored.selectedPrimary)
            if(!expected.canGoBack)break
            expected=expected.back();restored=restored.back()
        }
    }

    @Test fun malformedOrIncompatibleSavedRoutesRequireSafeReinitialization() {
        val snapshot=root(KotlinCandidateDestination.HISTORY).openProgressTrend("bench").snapshot()
        for(bad in listOf(emptyList(),snapshot.dropLast(1),
            listOf("future-version")+snapshot.drop(1),
            snapshot.toMutableList().apply{this[1]="REMOVED_SCREEN"},
            snapshot.toMutableList().apply{this[5]="UNKNOWN_DETAIL"},
            snapshot.toMutableList().apply{this[4]="SETTINGS"},
            snapshot.toMutableList().apply{this[6]=""},
            snapshot+listOf("PROGRESS","PROGRESS_TREND","bench"),
            snapshot+listOf("COACH","NONE","")))
            assertNull(bad.toString(),NativeKotlinNavigationState.restore(bad))
    }

    @Test fun invalidExerciseRequestsAreNotUsableRoutes() {
        assertTrue(runCatching{NativeKotlinRoute(KotlinCandidateDestination.SETTINGS,
            NativeKotlinRouteDetail.COACH_EXERCISE,"bench")}.isFailure)
        assertTrue(runCatching{NativeKotlinNavigationState.start().openProgressTrend(" ")}.isFailure)
        assertTrue(runCatching{NativeKotlinRoute(KotlinCandidateDestination.PROGRESS,
            exerciseId="bench")}.isFailure)
    }
}
