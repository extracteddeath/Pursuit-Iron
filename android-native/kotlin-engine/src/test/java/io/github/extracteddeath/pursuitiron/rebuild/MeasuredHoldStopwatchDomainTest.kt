package io.github.extracteddeath.pursuitiron.rebuild

import org.junit.Assert.*
import org.junit.Test

class MeasuredHoldStopwatchDomainTest {
    private val boot=1_800_000_000_000L
    private fun start(t:Long=20_000L)=
        checkNotNull(MeasuredHoldStopwatchDomain.start(t,boot+t))

    @Test fun stoppedHoldRequiresExplicitEntryAndNeverRoundsUp() {
        val running=start()
        assertTrue(running.running)
        assertNull(MeasuredHoldStopwatchDomain.usableSeconds(running))
        assertEquals("00:00.0",
            MeasuredHoldStopwatchDomain.display(running,20_000L,boot+20_000))
        assertEquals("00:15.9",
            MeasuredHoldStopwatchDomain.display(running,35_999L,boot+35_999))
        val stopped=checkNotNull(MeasuredHoldStopwatchDomain.stop(
            running,35_999L,boot+35_999))
        assertFalse(stopped.running)
        assertEquals(15,MeasuredHoldStopwatchDomain.usableSeconds(stopped))
        assertEquals("00:15.9",MeasuredHoldStopwatchDomain.display(
            stopped,300_000L,boot+300_000))
        assertNull(MeasuredHoldStopwatchDomain.stop(
            stopped,500_000L,boot+500_000))
    }

    @Test fun zeroSecondsCannotBeRecordedAsOneOrSavedAutomatically() {
        assertNull(MeasuredHoldStopwatchDomain.usableSeconds(
            MeasuredHoldStopwatchState()))
        val stopped=MeasuredHoldStopwatchDomain.stop(
            start(),20_999L,boot+20_999)
        assertNotNull(stopped)
        assertNull(MeasuredHoldStopwatchDomain.usableSeconds(
            checkNotNull(stopped)))
        assertEquals(1,MeasuredHoldStopwatchDomain.usableSeconds(
            checkNotNull(MeasuredHoldStopwatchDomain.stop(
                start(),21_000L,boot+21_000))))
    }

    @Test fun savedTimerCanResumeAfterRotationButCannotInventTimeAfterReboot() {
        val running=start(50_000L)
        assertEquals("00:30.0",MeasuredHoldStopwatchDomain.display(
            running,80_000L,boot+80_000))
        assertNull(MeasuredHoldStopwatchDomain.stop(
            running,100L,boot+100L))
        assertNull(MeasuredHoldStopwatchDomain.display(
            running,100L,boot+100L))
        assertNull(MeasuredHoldStopwatchDomain.stop(
            running,80_000L,boot+80_000L+61_000L))
        assertNull(MeasuredHoldStopwatchDomain.display(
            running,80_000L,boot+80_000L+61_000L))
        assertEquals("00:30.0",MeasuredHoldStopwatchDomain.display(
            running,80_000L,boot+80_000L+10_000L))
        assertNull(MeasuredHoldStopwatchDomain.start(-10L,boot))
        assertNull(MeasuredHoldStopwatchDomain.start(200L,100L))
    }

    @Test fun oneHourIsFinalAllowedMeasuredSecondAndLongerIsRejected() {
        val exact=checkNotNull(MeasuredHoldStopwatchDomain.stop(
            start(),3_620_999L,boot+3_620_999L))
        assertEquals(3600,MeasuredHoldStopwatchDomain.usableSeconds(exact))
        assertNull(MeasuredHoldStopwatchDomain.stop(
            start(),3_621_000L,boot+3_621_000L))
        assertNull(MeasuredHoldStopwatchDomain.usableSeconds(
            MeasuredHoldStopwatchState(stoppedElapsedMs=3_601_000L)))
    }

    @Test fun corruptOrMixedRunningAndStoppedTimerFailsClosed() {
        val missing=MeasuredHoldStopwatchState(
            startedMonotonicMs=10_000L)
        assertNull(MeasuredHoldStopwatchDomain.elapsedMs(
            MeasuredHoldStopwatchState(startedMonotonicMs=10_000L,
                approximateBootEpochMs=-1L),
            11_000L,boot+11_000L))
        assertNull(MeasuredHoldStopwatchDomain.elapsedMs(
            MeasuredHoldStopwatchState(startedMonotonicMs=10_000L,
                approximateBootEpochMs=Long.MAX_VALUE),
            11_000L,boot+11_000L))
        assertNull(MeasuredHoldStopwatchDomain.elapsedMs(
            missing,11_000L,boot+11_000L))
        val twoModes=start().copy(stoppedElapsedMs=1000L)
        assertNull(MeasuredHoldStopwatchDomain.elapsedMs(
            twoModes,25_000L,boot+25_000L))
        assertNull(MeasuredHoldStopwatchDomain.usableSeconds(twoModes))
        assertNull(MeasuredHoldStopwatchDomain.display(
            MeasuredHoldStopwatchState(stoppedElapsedMs=-1L),
            100L,boot+100L))
    }

    @Test fun restoredLongCannotWrapIntoAnApparentlyValidHold() {
        // Both positive and negative overflow used to narrow to 30 seconds.
        val wrap=4_294_967_296L*1000L
        for(elapsed in listOf(wrap+30_000L,-wrap+30_000L,
            Long.MAX_VALUE,Long.MIN_VALUE,-1L,3_601_000L)) {
            val state=MeasuredHoldStopwatchState(stoppedElapsedMs=elapsed)
            assertNull("elapsed=$elapsed",MeasuredHoldStopwatchDomain.display(
                state,100L,boot+100L))
            assertNull("elapsed=$elapsed",MeasuredHoldStopwatchDomain.usableSeconds(state))
        }
        for((elapsed,seconds) in listOf(1_000L to 1,
            30_999L to 30,3_600_999L to 3600)) {
            val state=MeasuredHoldStopwatchState(stoppedElapsedMs=elapsed)
            assertNotNull(MeasuredHoldStopwatchDomain.display(state,100L,boot+100L))
            assertEquals(seconds,MeasuredHoldStopwatchDomain.usableSeconds(state))
        }
    }
}
