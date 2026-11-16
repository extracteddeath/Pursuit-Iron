package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

internal class NativeKotlinProfileLayoutTest {
    @Test fun defaultShowsAllSectionsInOneStableOrder() {
        val layout=NativeProfileLayout.decode(null)
        assertEquals(NativeProfileSection.values().toList(),layout.order)
        assertTrue(layout.hidden.isEmpty())
    }

    @Test fun sectionVisibilityDoesNotRemoveAnyNavigationIdentity() {
        val altered=NativeProfileLayout.DEFAULT.setVisible(NativeProfileSection.SHARE,false)
        assertTrue(NativeProfileSection.SHARE in altered.order)
        assertTrue(NativeProfileSection.SHARE in altered.hidden)
        assertEquals(NativeProfileLayout.DEFAULT,
            altered.setVisible(NativeProfileSection.SHARE,true))
    }

    @Test fun movePreservesMembershipAndHonorsBounds() {
        val moved=NativeProfileLayout.DEFAULT.move(NativeProfileSection.SHARE,-1)
        assertEquals(NativeProfileSection.SHARE,moved.order[1])
        assertEquals(NativeProfileLayout.DEFAULT.order.toSet(),moved.order.toSet())
        assertEquals(moved,moved.move(NativeProfileSection.TRAINING,-1))
        assertEquals(moved,moved.move(NativeProfileSection.TRAINING,2))
    }

    @Test fun encodingRoundTripsOrderAndHiddenState() {
        val changed=NativeProfileLayout.DEFAULT
            .move(NativeProfileSection.SHARE,-1)
            .setVisible(NativeProfileSection.EXERCISE_FAVORITES,false)
        assertEquals(changed,NativeProfileLayout.decode(changed.encode()))
    }

    @Test fun invalidOrFuturePreferencesFailToSafeDefault() {
        for(value in listOf("v2|", "wrong", "v1||",
                "v1|TRAINING,TRAINING,SHARE,STRENGTH_FAVORITES,EXERCISE_FAVORITES|",
                "v1|TRAINING,PROGRESS,SHARE,STRENGTH_FAVORITES,EXERCISE_FAVORITES|UNKNOWN",
                "v1|TRAINING,PROGRESS,SHARE,STRENGTH_FAVORITES,EXERCISE_FAVORITES|SHARE,SHARE"))
            assertEquals(value,NativeProfileLayout.DEFAULT,NativeProfileLayout.decode(value))
    }

    @Test fun reorderingDoesNotMutateDefaultOrPreviousDraft() {
        val first=NativeProfileLayout.DEFAULT
        val next=first.move(NativeProfileSection.PROGRESS,1)
        assertEquals(NativeProfileSection.PROGRESS,first.order[1])
        assertNotEquals(first,next)
    }
}
