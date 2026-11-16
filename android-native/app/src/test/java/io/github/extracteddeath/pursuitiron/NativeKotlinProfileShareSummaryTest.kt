package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

internal class NativeKotlinProfileShareSummaryTest {
    private val progress = NativeKotlinProfileMilestones.fromVerifiedCounts(10, 50)
    private fun share(name: String="Private User",
        favorites:List<String> = listOf("Incline press","Bench press"),
        includeName:Boolean=false,includeFavorites:Boolean=false,club:Boolean=false)=
        NativeKotlinProfileShareSummary.render(
            progress,name,favorites,includeName,includeFavorites,club)

    @Test fun defaultOutputSharesOnlyActivityAndNeverIdentity() {
        val actual=share("Sensitive Name",listOf("Secret exercise"))
        assertTrue(actual.contains("Completed workouts: 10"))
        assertTrue(actual.contains("Completed working sets: 50"))
        assertTrue(actual.contains("Activity level: "))
        assertTrue(actual.contains("Participation milestones: "))
        assertFalse(actual.contains("Sensitive"))
        assertFalse(actual.contains("Secret"))
        assertFalse(actual.contains("Favorite logged"))
        assertFalse(actual.contains("1,000 lb"))
    }

    @Test fun nameAndFavoritesNeedSeparateOptInsAndAreBounded() {
        val actual=share(name="  Mia  ",favorites=listOf("Bench","Squat","Row","Press","Curl","Deadlift"),
            includeName=true,includeFavorites=true)
        assertTrue(actual.contains("Name: Mia"))
        assertTrue(actual.contains("Bench, Squat, Row, Press, Curl"))
        assertFalse(actual.contains("Deadlift"))
        val withoutName=share(includeFavorites=true)
        assertFalse(withoutName.contains("Name:"))
        val withoutFavorites=share(includeName=true)
        assertFalse(withoutFavorites.contains("Favorite logged"))
    }

    @Test fun personalInputsAreSanitizedNotEscapedIntoExtraLines() {
        val actual=share(name="Mia\nOwner: admin",
            favorites=listOf("Row\nPrivate history","Row\nPrivate history","   ","\tBench"),
            includeName=true,includeFavorites=true)
        assertTrue(actual.contains("Name: Mia Owner: admin"))
        assertTrue(actual.contains("Row Private history, Bench"))
        assertFalse(actual.contains("Name: Mia\nOwner"))
        assertFalse(actual.contains("Row\nPrivate"))
    }

    @Test fun loggedSinglesMilestoneIsExplicitlySelfReportedNotAProjectedPr() {
        val actual=share(club=true)
        assertTrue(actual.contains("self-reported, not competition-certified"))
        assertFalse(actual.contains("estimated 1RM"))
        assertTrue(actual.contains("not a verified competition record"))
    }

    @Test fun correctionsProduceAnUpdatedSummaryWithNoPersistedBadge() {
        val old=share()
        val corrected=NativeKotlinProfileShareSummary.render(
            NativeKotlinProfileMilestones.fromVerifiedCounts(9,49),
            "Mia",emptyList(),false,false,false)
        assertTrue(old.contains("Completed workouts: 10"))
        assertTrue(corrected.contains("Completed workouts: 9"))
        assertTrue(corrected.contains("Completed working sets: 49"))
        assertFalse(corrected.contains("Name: Mia"))
    }

    @Test fun emptyFavoritesDoNotCreateAFalseFavoriteClaim() {
        assertFalse(share(favorites=emptyList(),includeFavorites=true)
            .contains("Favorite logged exercises"))
    }
}
