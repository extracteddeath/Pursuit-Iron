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

    @Test fun restCueWaitsForItsExactNewReceiptWithoutReplayingOldEvents() {
        val previous=NativeKotlinRestCue("previous",90,0)
        val latest=NativeKotlinRestCue("latest",0,0)
        // Receipt committed first, timer cue projected in a later frame.
        assertNull(NativeRestReceiptCuePolicy.newCue("previous","latest",null))
        assertNull(NativeRestReceiptCuePolicy.newCue("previous","latest",previous))
        assertEquals(latest,NativeRestReceiptCuePolicy.newCue("previous","latest",latest))
        // Once observed, recomposition, Activity recreation or a duplicate
        // projection must not restart or clear the already recovered timer.
        assertNull(NativeRestReceiptCuePolicy.newCue("latest","latest",latest))
        assertNull(NativeRestReceiptCuePolicy.newCue("previous",null,latest))
        // A zero-second immediate follow-up remains a real acknowledged cue.
        assertEquals(0,NativeRestReceiptCuePolicy.newCue(
            "previous","latest",latest)?.seconds)
    }

    @Test fun manualRestAcknowledgmentSupersedesDelayedCueButNotNextSet() {
        val cue=NativeKotlinRestCue("new",90,1)
        assertTrue(NativeRestReceiptCuePolicy.needsPendingClear("old",null,"new"))
        assertFalse(NativeRestReceiptCuePolicy.needsPendingClear("old","new","new"))
        assertEquals(cue,NativeRestReceiptCuePolicy.newCue("old","new",cue))
        // A manual restart or clear acknowledges "new" before the cue arrives.
        assertNull(NativeRestReceiptCuePolicy.newCue("new","new",cue))
        assertFalse(NativeRestReceiptCuePolicy.needsPendingClear("new","new","new"))
        val later=NativeKotlinRestCue("later",60,1)
        assertTrue(NativeRestReceiptCuePolicy.needsPendingClear("new","new","later"))
        assertEquals(later,NativeRestReceiptCuePolicy.newCue("new","later",later))
    }

    @Test fun checkpointsMustHaveConsistentDurationAndSafeDeadline() {
        assertNull(NativeRestCountdownPolicy.decode("1|1000|2000|61000|90"))
        assertNull(NativeRestCountdownPolicy.begin(90,Long.MAX_VALUE-1,wall))
        assertFalse(NativeRestCountdownPolicy.valid(
            started(90).copy(activeSeconds=120),elapsed,wall))
        val fractional=requireNotNull(NativeRestCountdownPolicy.adjust(
            started(90),30,elapsed+12_345L,wall+12_345L))
        assertEquals(fractional,NativeRestCountdownPolicy.decode(
            NativeRestCountdownPolicy.encode(fractional)))
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
