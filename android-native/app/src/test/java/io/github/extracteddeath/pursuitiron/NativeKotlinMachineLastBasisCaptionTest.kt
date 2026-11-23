package io.github.extracteddeath.pursuitiron

import io.github.extracteddeath.pursuitiron.rebuild.*
import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

/** The live machine chooser is read-only: it must describe the last real
 * physical weight convention without borrowing a machine default or a new
 * program's current load. */
class NativeKotlinMachineLastBasisCaptionTest {
    private val key=WorkoutReferenceKey("assist-working-1")
    private fun last(basis:LoadBasis?,machine:String="station_A")=
        WorkoutLastReferenceResult.Ready(emptyMap(),mapOf(
            WorkoutMachineReferenceKey(key,machine) to WorkoutLastSetReference(
                "finished_7",7L,KotlinJournalSetOutcome.COMPLETED,
                KotlinPerformedSet(BigDecimal("70.00"),LoadUnit.LB,10,1,
                    machineInstanceId=machine),basis
            )))
    @Test fun assistanceCounterweightIsExplicitlyMarkedAsEasierWhenHigher() {
        val caption=workoutStationLastCaption(
            last(LoadBasis.ASSISTANCE_COUNTERWEIGHT),listOf(key),"station_A")
        assertEquals(
            "Last on this machine: 70 lb · assistance counterweight (more = easier) × 10 · 1 RIR",
            caption)
        assertFalse(caption.contains("machine stack"))
    }
    @Test fun ordinaryMachineStackUsesItsActualPhysicalBasis() {
        val caption=workoutStationLastCaption(
            last(LoadBasis.MACHINE_STACK),listOf(key),"station_A")
        assertEquals(
            "Last on this machine: 70 lb · machine stack × 10 · 1 RIR",
            caption)
    }
    @Test fun unrecordedBasisIsDisclosedRatherThanAssumed() {
        val caption=workoutStationLastCaption(last(null),listOf(key),"station_A")
        assertEquals(
            "Last on this machine: 70 lb · load basis not recorded × 10 · 1 RIR",
            caption)
        assertFalse(caption.contains("counterweight"))
        assertFalse(caption.contains("bodyweight"))
    }
    @Test fun otherStationAndLaterFailureDoNotBorrowAStaleSuccessfulSet() {
        val caption=workoutStationLastCaption(
            last(LoadBasis.MACHINE_STACK),listOf(key),"station_B")
        assertEquals("No comparable previous set on this machine",caption)
        val failure=WorkoutLastReferenceResult.Ready(emptyMap(),mapOf(
            WorkoutMachineReferenceKey(key,"station_A") to WorkoutLastSetReference(
                "finished_8",8L,KotlinJournalSetOutcome.FAILED_ATTEMPT,
                KotlinPerformedSet(BigDecimal("70"),LoadUnit.LB,0,null,
                    machineInstanceId="station_A"),LoadBasis.MACHINE_STACK)))
        assertEquals("Last visit: failed attempt · no load copied",
            workoutStationLastCaption(failure,listOf(key),"station_A"))
    }
}
