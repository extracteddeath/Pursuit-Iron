package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test

class NativeKotlinRestCountdownPolicyTest {
    private val elapsed=100_000L
    private val wall=1_800_000_000_000L
    private fun started(seconds:Int=120)=
        requireNotNull(NativeRestCountdownPolicy.begin(seconds,elapsed,wall))

    @Test fun normalCountdownRecoversAfterWorkoutMinimizeAndColdReopen() {
        val original=started(120)
        val reopened=NativeRestCountdownPolicy.decode(
            NativeRestCountdownPolicy.encode(original))
        assertEquals(original,reopened)
        assertEquals(120L,NativeRestCountdownPolicy.remaining(
            checkNotNull(reopened),elapsed,wall))
        assertEquals(45L,NativeRestCountdownPolicy.remaining(
            reopened,elapsed+75_001L,wall+75_001L))
        assertEquals(0L,NativeRestCountdownPolicy.remaining(
            reopened,elapsed+120_001L,wall+120_001L))
    }

    @Test fun quickAdjustChangesRemainingRestWithoutChangingAuthorisedTargets() {
        val original=started(90)
        val plus=NativeRestCountdownPolicy.adjust(
            original,30,elapsed+15_000L,wall+15_000L)
        assertNotNull(plus)
        assertEquals(105L,NativeRestCountdownPolicy.remaining(
            checkNotNull(plus),elapsed+15_000L,wall+15_000L))
        assertEquals(90,original.activeSeconds)
        assertEquals(120,plus.activeSeconds)
        val minus=NativeRestCountdownPolicy.adjust(
            plus,-30,elapsed+15_000L,wall+15_000L)
        assertEquals(75L,NativeRestCountdownPolicy.remaining(
            checkNotNull(minus),elapsed+15_000L,wall+15_000L))
        assertEquals(90,minus.activeSeconds)
        assertNull(NativeRestCountdownPolicy.adjust(
            original,-30,elapsed+65_000L,wall+65_000L))
    }

    @Test fun extendingNearTheLimitPreservesTheCountdownAndOriginalClockAnchor() {
        val limit=NativeRestCountdownPolicy.MAX_SECONDS
        val original=started(limit-10)
        val later=elapsed+15_500L
        val laterWall=wall+15_500L
        val capped=requireNotNull(NativeRestCountdownPolicy.adjust(original,30,later,laterWall))
        assertEquals(started(limit),capped)
        assertEquals(capped,NativeRestCountdownPolicy.adjust(capped,30,later,laterWall))
        assertEquals(started(limit-30),NativeRestCountdownPolicy.adjust(capped,-30,later,laterWall))
        assertNull(NativeRestCountdownPolicy.adjust(capped,30,elapsed+limit*1000L,wall+limit*1000L))
    }

    @Test fun clockChangesRebootsAndHistoricalRestAlwaysFailClosed() {
        val original=started()
        assertNull(NativeRestCountdownPolicy.remaining(original,
            elapsed-1,wall+1_000L)) // device restart / monotonic reset
        assertNull(NativeRestCountdownPolicy.remaining(original,
            elapsed+10_000L,wall-1L)) // wall rollback
        assertNull(NativeRestCountdownPolicy.remaining(original,
            elapsed+20_000L,wall+120_000L)) // clock jump
        assertNull(NativeRestCountdownPolicy.remaining(original,
            elapsed+NativeRestCountdownPolicy.MAX_SECONDS*1000L+61_000L,
            wall+NativeRestCountdownPolicy.MAX_SECONDS*1000L+61_000L))
        assertNull(NativeRestCountdownPolicy.adjust(
            original,30,elapsed-1L,wall+5_000L))
    }

    @Test fun rejectsCorruptAndImpossiblePersistedSnapshots() {
        assertNull(NativeRestCountdownPolicy.decode(null))
        assertNull(NativeRestCountdownPolicy.decode(""))
        assertNull(NativeRestCountdownPolicy.decode("2|1|1|2000|2"))
        assertNull(NativeRestCountdownPolicy.decode("1|x|1|2000|2"))
        assertNull(NativeRestCountdownPolicy.decode("1|1|2|1|60"))
        assertNull(NativeRestCountdownPolicy.decode("1|1|2|1000|0"))
        assertNull(NativeRestCountdownPolicy.decode("1|1|2|1000|7201"))
        assertNull(NativeRestCountdownPolicy.decode("1|1|2|9000000|50"))
        assertNull(NativeRestCountdownPolicy.decode("a".repeat(121)))
        assertNull(NativeRestCountdownPolicy.begin(0,elapsed,wall))
        assertNull(NativeRestCountdownPolicy.begin(7201,elapsed,wall))
    }

    @Test fun progressIsBoundedToOnePresentationSession() {
        val active=started(3600)
        val almost=NativeRestCountdownPolicy.adjust(
            active,30,elapsed+15_000L,wall+15_000L)
        assertNotNull(almost)
        assertEquals(3630,almost?.activeSeconds)
        // Paused old app data cannot magically become an endlessly long timer.
        assertFalse(NativeRestCountdownPolicy.valid(
            active,elapsed+10_000_000L,wall+10_000_000L))
    }
}
