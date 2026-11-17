package io.github.extracteddeath.pursuitiron.rebuild

import java.math.BigDecimal
import org.junit.Assert.*
import org.junit.Test

class WorkoutMachineLastPreviewDomainTest {
    private val first=WorkoutReferenceKey("press-working-1")
    private val second=WorkoutReferenceKey("press-working-2")
    private fun completed(machine:String,load:Int,reps:Int,order:Long)=
        WorkoutLastSetReference(
            "session-$order",order,KotlinJournalSetOutcome.COMPLETED,
            KotlinPerformedSet(BigDecimal(load),LoadUnit.LB,reps,null,
                machineInstanceId=machine),LoadBasis.MACHINE_STACK)
    private fun failed(machine:String,order:Long)=WorkoutLastSetReference(
        "session-$order",order,KotlinJournalSetOutcome.FAILED_ATTEMPT,
        KotlinPerformedSet(BigDecimal(160),LoadUnit.LB,0,0,
            machineInstanceId=machine),LoadBasis.MACHINE_STACK)
    private fun ready(vararg entries:Pair<WorkoutMachineReferenceKey,WorkoutLastSetReference>)=
        WorkoutLastReferenceResult.Ready(
            emptyMap(),linkedMapOf(*entries))

    @Test fun physicalAAndBAreNeverUsedAsEachOthersLast() {
        val history=ready(
            WorkoutMachineReferenceKey(first,"A") to completed("A",200,8,2),
            WorkoutMachineReferenceKey(first,"B") to completed("B",140,8,3))
        val a=WorkoutMachineLastPreviewDomain.review(history,listOf(first),"A")
        val b=WorkoutMachineLastPreviewDomain.review(history,listOf(first),"B")
        assertEquals(BigDecimal(200),(a as WorkoutMachineLastPreview.Completed).load)
        assertEquals(BigDecimal(140),(b as WorkoutMachineLastPreview.Completed).load)
        assertEquals(WorkoutMachineLastPreview.NoMatchingSet,
            WorkoutMachineLastPreviewDomain.review(history,listOf(first),"C"))
    }

    @Test fun latestFailureNeverResurrectsEarlierCompletedWork() {
        val history=ready(
            WorkoutMachineReferenceKey(first,"A") to completed("A",200,8,1),
            WorkoutMachineReferenceKey(second,"A") to failed("A",4))
        val newest=WorkoutMachineLastPreviewDomain.review(history,listOf(first,second),"A")
            as WorkoutMachineLastPreview.NotCompleted
        assertEquals(KotlinJournalSetOutcome.FAILED_ATTEMPT,newest.outcome)
        assertEquals(4L,newest.completionOrder)
        // A review of a single specific set does not borrow the second set.
        assertEquals(BigDecimal(200),(WorkoutMachineLastPreviewDomain.review(
            history,listOf(first),"A") as WorkoutMachineLastPreview.Completed).load)
    }

    @Test fun laterFailureInTheSameSessionIsNeverHiddenByFirstSetSuccess() {
        val history=ready(
            WorkoutMachineReferenceKey(first,"A") to completed("A",200,8,7),
            WorkoutMachineReferenceKey(second,"A") to failed("A",7),
            WorkoutMachineReferenceKey(first,"B") to completed("B",140,8,6))
        val result=WorkoutMachineLastPreviewDomain.review(
            history,listOf(first,second),"A")
        assertEquals(KotlinJournalSetOutcome.FAILED_ATTEMPT,
            (result as WorkoutMachineLastPreview.NotCompleted).outcome)
        assertEquals(BigDecimal(140),(WorkoutMachineLastPreviewDomain.review(
            history,listOf(first,second),"B") as WorkoutMachineLastPreview.Completed).load)
    }

    @Test fun anotherExerciseAndLegacySetsCannotSupplyStationEvidence() {
        val history=WorkoutLastReferenceResult.Ready(
            mapOf(first to completed("A",220,12,4)),
            mapOf(
                WorkoutMachineReferenceKey(WorkoutReferenceKey("other-exercise"),"A")
                    to completed("A",300,8,5),
                WorkoutMachineReferenceKey(first,
                    KotlinMachineAlternativesDomain.LEGACY_MACHINE_ID)
                    to completed("A",400,9,6)))
        assertEquals(WorkoutMachineLastPreview.NoMatchingSet,
            WorkoutMachineLastPreviewDomain.review(history,listOf(first),"A"))
        assertEquals(WorkoutMachineLastPreview.NoMatchingSet,
            WorkoutMachineLastPreviewDomain.review(history,listOf(first),
                KotlinMachineAlternativesDomain.LEGACY_MACHINE_ID))
    }

    @Test fun impossibleStationProvenanceOrAbsentHistoryFailClosed() {
        val inconsistent=ready(
            WorkoutMachineReferenceKey(first,"B") to completed("A",200,8,2))
        assertEquals(WorkoutMachineLastPreview.Unavailable,
            WorkoutMachineLastPreviewDomain.review(inconsistent,listOf(first),"B"))
        assertEquals(WorkoutMachineLastPreview.Unavailable,
            WorkoutMachineLastPreviewDomain.review(
                WorkoutLastReferenceResult.Unavailable,listOf(first),"A"))
        assertEquals(WorkoutMachineLastPreview.NoMatchingSet,
            WorkoutMachineLastPreviewDomain.review(
                WorkoutLastReferenceResult.Ready(emptyMap()),emptyList(),"A"))
    }
}
