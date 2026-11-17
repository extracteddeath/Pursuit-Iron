package io.github.extracteddeath.pursuitiron

import org.junit.Assert.*
import org.junit.Test
import java.math.BigDecimal
import java.time.Instant
import java.time.ZoneId
import java.util.Locale

class NativeKotlinTrendAxisPolicyTest {
    @Test fun calendarSpacingReflectsUnequalRecordedIntervals() {
        val day=86400000L
        val result=NativeKotlinTrendAxisPolicy.project(
            listOf(1760000000000L,1760000000000L+2*day,1760000000000L+10*day))
        assertTrue(result.calendarScaled)
        assertEquals(0.0f,result.fractions[0],0.001f)
        assertEquals(0.2f,result.fractions[1],0.001f)
        assertEquals(1.0f,result.fractions[2],0.001f)
    }

    @Test fun missingTiedOrContradictoryDatesNeverBecomeInventedCalendarSpace() {
        listOf(
            listOf(1000L,null,9000L),
            listOf(1000L,1000L,9000L),
            listOf(1000L,9000L,2000L)
        ).forEach {times->
            val result=NativeKotlinTrendAxisPolicy.project(times)
            assertFalse(result.calendarScaled)
            assertEquals(listOf(0f,0.5f,1f),result.fractions)
        }
    }

    @Test fun smallSampleAxisNeverDividesByZero() {
        assertEquals(emptyList<Float>(),
            NativeKotlinTrendAxisPolicy.project(emptyList()).fractions)
        assertEquals(listOf(0.5f),
            NativeKotlinTrendAxisPolicy.project(listOf(1234L)).fractions)
    }

    @Test fun exactObservedWeightsRetainQuarterPlatePrecisionAndSignedChanges() {
        assertEquals("102.25 kg",NativeKotlinTrendAxisPolicy.exactMetric(
            BigDecimal("102.2500"),"kg"))
        assertEquals("-2.25 lb",NativeKotlinTrendAxisPolicy.exactMetric(
            BigDecimal("-2.250"),"lb"))
        assertEquals("0 reps",NativeKotlinTrendAxisPolicy.exactMetric(
            BigDecimal.ZERO,"reps"))
    }

    @Test fun dateLabelsUseActualFinishClockOrExplicitlySayUndated() {
        val zone=ZoneId.of("UTC")
        val locale=Locale.US
        val recorded=NativeKotlinTrendAxisPolicy.sessionLabel(
            8,Instant.parse("2026-11-16T20:30:00Z").toEpochMilli(),zone,locale)
        assertTrue(recorded.contains("Nov"))
        assertTrue(recorded.contains("2026"))
        assertTrue(recorded.contains("8"))
        assertEquals("Session 9 · Date not recorded",
            NativeKotlinTrendAxisPolicy.sessionLabel(9,null,zone,locale))
    }
}
